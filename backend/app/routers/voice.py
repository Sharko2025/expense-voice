import re
import time
import uuid
from datetime import date, datetime, time as dtime
from pathlib import Path

from fastapi import APIRouter, Depends, File, Form, HTTPException, Request, UploadFile
from fastapi.responses import JSONResponse
from sqlalchemy.orm import Session

from app.config import get_settings
from app.db import get_db
from app.schemas import TransactionOut, VoiceResult
from app.services.errors import AIServiceError
from app.services.parser import TransactionParser, get_parser
from app.services.stt import Transcriber, get_transcriber
from app.services.voice import Outcome, VoiceInput, process_voice
from app.timeutil import VN_TZ

router = APIRouter()


def _parse_recorded_at(value: str) -> datetime:
    try:
        dt = datetime.fromisoformat(value)
    except ValueError:
        raise HTTPException(422, "recorded_at không đúng định dạng ISO 8601")
    if dt.tzinfo is None:
        raise HTTPException(422, "recorded_at phải có múi giờ")
    return dt


def _occurred_at(recorded_at: datetime, occurred_on: str | None) -> datetime:
    """Recording for an earlier day (in-app mic on a past day) lands at noon that day, Vietnam time."""
    if not occurred_on:
        return recorded_at
    try:
        day = date.fromisoformat(occurred_on)
    except ValueError:
        raise HTTPException(422, "occurred_on không đúng định dạng YYYY-MM-DD")
    spoken_day = recorded_at.astimezone(VN_TZ).date()
    if day > spoken_day:
        raise HTTPException(422, "Không ghi được cho ngày tương lai")
    if day == spoken_day:
        return recorded_at  # noon today may still be ahead
    return datetime.combine(day, dtime(12, 0), VN_TZ)


def _audio_suffix(filename: str | None) -> str:
    suffix = Path(filename or "").suffix.lower()
    return suffix if re.fullmatch(r"\.[a-z0-9]{1,5}", suffix) else ".m4a"


@router.post("/voice", response_model=VoiceResult)
def post_voice(
    request: Request,
    file: UploadFile = File(...),
    entry_id: uuid.UUID = Form(...),
    recorded_at: str = Form(...),
    occurred_on: str | None = Form(None),
    lat: float | None = Form(None),
    lng: float | None = Form(None),
    db: Session = Depends(get_db),
    transcriber: Transcriber = Depends(get_transcriber),
    parser: TransactionParser = Depends(get_parser),
):
    started = time.perf_counter()
    request.state.entry_id = entry_id
    when = _parse_recorded_at(recorded_at)
    occurred_at = _occurred_at(when, occurred_on)

    limit = get_settings().max_upload_bytes
    audio = file.file.read(limit + 1)
    if not audio:
        raise HTTPException(422, "File ghi âm rỗng")
    if len(audio) > limit:
        raise HTTPException(422, "File ghi âm quá lớn")

    data = VoiceInput(entry_id, when, audio, _audio_suffix(file.filename), lat, lng, occurred_at)
    try:
        result = process_voice(db, data, transcriber, parser, started)
    except AIServiceError:
        raise HTTPException(502, "Lỗi xử lý giọng nói, sẽ thử lại")

    if result.outcome is Outcome.BUSY:
        raise HTTPException(409, "Đang xử lý, thử lại sau")
    if result.outcome is Outcome.EMPTY:
        return JSONResponse(status_code=422, content={"detail": "Không hiểu, nói lại nhé", "raw_text": result.entry.raw_text})
    return VoiceResult(
        entry_id=entry_id,
        raw_text=result.entry.raw_text,
        transactions=[TransactionOut.from_model(t) for t in result.transactions],
    )
