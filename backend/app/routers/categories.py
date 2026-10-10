from fastapi import APIRouter, Depends
from sqlalchemy import select
from sqlalchemy.orm import Session

from app.db import get_db
from app.models import Category
from app.schemas import CategoryOut

router = APIRouter()


def active_categories(db: Session) -> list[Category]:
    stmt = select(Category).where(Category.is_active.is_(True)).order_by(Category.sort_order, Category.id)
    return list(db.scalars(stmt))


@router.get("/categories", response_model=list[CategoryOut])
def list_categories(db: Session = Depends(get_db)):
    return active_categories(db)
