import uuid
from datetime import date

from fastapi import APIRouter, Depends, HTTPException, Query, Response
from sqlalchemy import func, select, update
from sqlalchemy.orm import Session

from app.db import get_db
from app.models import Category, Transaction, VoiceEntry
from app.schemas import TransactionOut, TransactionPatch
from app.timeutil import day_range, today_vn

router = APIRouter()


def _get_tx(db: Session, tx_id: uuid.UUID, include_deleted: bool = False) -> Transaction:
    tx = db.get(Transaction, tx_id)
    if tx is None or (tx.deleted_at is not None and not include_deleted):
        raise HTTPException(404, "Không tìm thấy giao dịch")
    return tx


@router.get("/transactions", response_model=list[TransactionOut])
def list_transactions(
    from_: date | None = Query(None, alias="from"),
    to: date | None = Query(None),
    db: Session = Depends(get_db),
):
    first = from_ or to or today_vn()
    last = to or first
    if last < first:
        raise HTTPException(422, "Khoảng ngày không hợp lệ")
    start, end = day_range(first, last)
    rows = db.scalars(
        select(Transaction)
        .where(Transaction.deleted_at.is_(None), Transaction.occurred_at >= start, Transaction.occurred_at < end)
        .order_by(Transaction.occurred_at.desc(), Transaction.created_at.desc(), Transaction.id)
    )
    return [TransactionOut.from_model(t) for t in rows]


@router.patch("/transactions/{tx_id}", response_model=TransactionOut)
def patch_transaction(tx_id: uuid.UUID, patch: TransactionPatch, db: Session = Depends(get_db)):
    tx = _get_tx(db, tx_id)
    changes = patch.model_dump(exclude_unset=True)
    if "category_id" in changes and changes["category_id"] is not None:
        if db.get(Category, changes["category_id"]) is None:
            raise HTTPException(422, "Danh mục không tồn tại")
    if "merchant" in changes:
        changes["merchant"] = (changes["merchant"] or "").strip() or None
    for key, value in changes.items():
        setattr(tx, key, value)
    tx.updated_at = func.now()
    db.commit()
    db.refresh(tx)
    return TransactionOut.from_model(tx)


@router.delete("/transactions/{tx_id}", status_code=204)
def delete_transaction(tx_id: uuid.UUID, db: Session = Depends(get_db)):
    tx = _get_tx(db, tx_id, include_deleted=True)
    if tx.deleted_at is None:
        tx.deleted_at = func.now()
        db.commit()
    return Response(status_code=204)


@router.post("/transactions/{tx_id}/restore", response_model=TransactionOut)
def restore_transaction(tx_id: uuid.UUID, db: Session = Depends(get_db)):
    tx = _get_tx(db, tx_id, include_deleted=True)
    tx.deleted_at = None
    db.commit()
    db.refresh(tx)
    return TransactionOut.from_model(tx)


@router.post("/voice/{entry_id}/undo", status_code=204)
def undo_voice_entry(entry_id: uuid.UUID, db: Session = Depends(get_db)):
    if db.get(VoiceEntry, entry_id) is None:
        raise HTTPException(404, "Không tìm thấy lần ghi âm")
    db.execute(
        update(Transaction)
        .where(Transaction.voice_entry_id == entry_id, Transaction.deleted_at.is_(None))
        .values(deleted_at=func.now())
    )
    db.commit()
    return Response(status_code=204)
