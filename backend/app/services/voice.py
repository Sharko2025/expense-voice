"""POST /voice pipeline: idempotent claim → transcribe → parse → store."""
import enum
import tempfile
import time
import uuid
from dataclasses import dataclass, field
from datetime import datetime, timedelta
from pathlib import Path

from sqlalchemy import and_, func, or_, select, update
from sqlalchemy.dialects.postgresql import insert as pg_insert
from sqlalchemy.orm import Session

from app.models import Category, Transaction, VoiceEntry
from app.routers.categories import active_categories
from app.services.normalize import FALLBACK_CATEGORY, normalize
from app.services.parser import TransactionParser
from app.services.stt import Transcriber

# An entry stuck in received/transcribed longer than this (e.g. server restarted mid-request) may be reclaimed.
STALE_AFTER = timedelta(minutes=2)


class Outcome(enum.Enum):
    OK = "ok"  # transactions created (or replayed)
    EMPTY = "empty"  # parsed, but no transaction found in the speech
    BUSY = "busy"  # another request is processing this entry right now


@dataclass
class ProcessResult:
    outcome: Outcome
    entry: VoiceEntry | None = None
    transactions: list[Transaction] = field(default_factory=list)


@dataclass
class VoiceInput:
    entry_id: uuid.UUID
    recorded_at: datetime
    audio: bytes
    suffix: str
    latitude: float | None = None
    longitude: float | None = None


def process_voice(
    db: Session, data: VoiceInput, transcriber: Transcriber, parser: TransactionParser, started: float
) -> ProcessResult:
    if not _claim(db, data):
        entry = db.get(VoiceEntry, data.entry_id, populate_existing=True)
        if entry.status != "parsed":
            return ProcessResult(Outcome.BUSY, entry)
        return _replay(db, entry)

    entry = db.get(VoiceEntry, data.entry_id, populate_existing=True)
    stage = "stt"
    try:
        t0 = time.perf_counter()
        raw_text = _transcribe(transcriber, data)
        entry.stt_ms = _ms(t0)
        entry.raw_text = raw_text
        entry.status = "transcribed"
        db.commit()

        stage = "parse"
        categories = active_categories(db)
        t0 = time.perf_counter()
        raw = parser.parse(raw_text, [c.name for c in categories])
        entry.parse_ms = _ms(t0)

        stage = "store"
        mapping = {c.name: c.id for c in categories}
        mapping.setdefault(FALLBACK_CATEGORY, _fallback_category_id(db))
        transactions = []
        for tx in normalize(raw, mapping):
            row = Transaction(
                voice_entry_id=entry.id,
                occurred_at=data.recorded_at,
                item=tx.item,
                amount_vnd=tx.amount_vnd,
                category_id=tx.category_id,
                method=tx.method,
                merchant=tx.merchant,
                created_at=func.clock_timestamp(),  # keeps spoken order stable on replay
            )
            db.add(row)
            db.flush()
            transactions.append(row)
        entry.status = "parsed"
        entry.total_ms = _ms(started)
        db.commit()
    except Exception as e:
        _mark_failed(db, data.entry_id, f"{stage}: {type(e).__name__}: {e}"[:500], started)
        raise

    for row in transactions:
        db.refresh(row)
    return ProcessResult(Outcome.OK if transactions else Outcome.EMPTY, entry, transactions)


def _claim(db: Session, data: VoiceInput) -> bool:
    """True if this request owns processing of the entry (new, failed earlier, or stale)."""
    inserted = db.execute(
        pg_insert(VoiceEntry)
        .values(
            id=data.entry_id,
            recorded_at=data.recorded_at,
            status="received",
            latitude=data.latitude,
            longitude=data.longitude,
        )
        .on_conflict_do_nothing(index_elements=[VoiceEntry.id])
        .returning(VoiceEntry.id)
    ).scalar()
    if inserted is None:
        # Atomic: only one concurrent retry can flip the row back to "received".
        inserted = db.execute(
            update(VoiceEntry)
            .where(
                VoiceEntry.id == data.entry_id,
                or_(
                    VoiceEntry.status == "failed",
                    and_(
                        VoiceEntry.status.in_(["received", "transcribed"]),
                        VoiceEntry.received_at < func.now() - STALE_AFTER,
                    ),
                ),
            )
            .values(
                status="received",
                error=None,
                received_at=func.now(),
                raw_text=None,
                stt_ms=None,
                parse_ms=None,
                total_ms=None,
            )
            .returning(VoiceEntry.id)
        ).scalar()
    db.commit()
    return inserted is not None


def _replay(db: Session, entry: VoiceEntry) -> ProcessResult:
    all_rows = list(
        db.scalars(
            select(Transaction)
            .where(Transaction.voice_entry_id == entry.id)
            .order_by(Transaction.created_at, Transaction.id)
        )
    )
    if not all_rows:
        return ProcessResult(Outcome.EMPTY, entry)
    return ProcessResult(Outcome.OK, entry, [t for t in all_rows if t.deleted_at is None])


def _transcribe(transcriber: Transcriber, data: VoiceInput) -> str:
    tmp = tempfile.NamedTemporaryFile(suffix=data.suffix, delete=False)
    path = Path(tmp.name)
    try:
        with tmp:
            tmp.write(data.audio)
        return transcriber.transcribe(path)
    finally:
        path.unlink(missing_ok=True)  # never keep audio on disk


def _mark_failed(db: Session, entry_id: uuid.UUID, error: str, started: float) -> None:
    db.rollback()
    entry = db.get(VoiceEntry, entry_id, populate_existing=True)
    entry.status = "failed"
    entry.error = error
    entry.total_ms = _ms(started)
    db.commit()


def _fallback_category_id(db: Session) -> int:
    return db.scalar(select(Category.id).where(Category.name == FALLBACK_CATEGORY))


def _ms(since: float) -> int:
    return int((time.perf_counter() - since) * 1000)
