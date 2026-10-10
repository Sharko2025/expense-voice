from datetime import date, datetime

from fastapi import APIRouter, Depends, HTTPException, Query
from sqlalchemy import func, select
from sqlalchemy.orm import Session

from app.db import get_db
from app.models import Category, Transaction
from app.schemas import CategoryTotal, SummaryOut
from app.timeutil import month_range, parse_month, shift_month, today_vn

router = APIRouter()


def _in_range(start: datetime, end: datetime):
    return (Transaction.deleted_at.is_(None), Transaction.occurred_at >= start, Transaction.occurred_at < end)


@router.get("/summary", response_model=SummaryOut)
def summary(month: str | None = Query(None), db: Session = Depends(get_db)):
    if month is None:
        first = today_vn().replace(day=1)
    else:
        try:
            first = parse_month(month)
        except ValueError:
            raise HTTPException(422, "Tháng phải có dạng YYYY-MM")
    prev = shift_month(first, -1)

    start, end = month_range(first)
    rows = db.execute(
        select(
            Transaction.category_id,
            Category.name,
            func.sum(Transaction.amount_vnd).label("total"),
            func.count().label("n"),
        )
        .outerjoin(Category, Category.id == Transaction.category_id)
        .where(*_in_range(start, end))
        .group_by(Transaction.category_id, Category.name)
    ).all()
    by_category = sorted(
        (CategoryTotal(category_id=r.category_id, category=r.name, total_vnd=int(r.total), count=r.n) for r in rows),
        key=lambda c: (-c.total_vnd, c.category or ""),
    )
    total = sum(c.total_vnd for c in by_category)

    prev_start, prev_end = month_range(prev)
    prev_total = int(
        db.scalar(select(func.coalesce(func.sum(Transaction.amount_vnd), 0)).where(*_in_range(prev_start, prev_end)))
    )

    diff = total - prev_total
    return SummaryOut(
        month=_fmt(first),
        total_vnd=total,
        count=sum(c.count for c in by_category),
        prev_month=_fmt(prev),
        prev_total_vnd=prev_total,
        diff_vnd=diff,
        diff_pct=round(diff * 100 / prev_total, 1) if prev_total else None,
        by_category=by_category,
    )


def _fmt(first: date) -> str:
    return f"{first.year:04d}-{first.month:02d}"
