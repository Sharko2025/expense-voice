import uuid
from datetime import datetime
from typing import Annotated, Literal

from pydantic import (
    AwareDatetime,
    BaseModel,
    ConfigDict,
    Field,
    StrictInt,
    StringConstraints,
    field_validator,
)

from app.models import Transaction


class CategoryOut(BaseModel):
    model_config = ConfigDict(from_attributes=True)

    id: int
    name: str
    sort_order: int | None


class TransactionOut(BaseModel):
    id: uuid.UUID
    voice_entry_id: uuid.UUID | None
    occurred_at: datetime
    item: str
    amount_vnd: int
    category_id: int | None
    category: str | None
    method: Literal["cash", "card", "transfer"] | None
    merchant: str | None
    note: str | None
    created_at: datetime | None
    updated_at: datetime | None

    @classmethod
    def from_model(cls, tx: Transaction) -> "TransactionOut":
        return cls(
            id=tx.id,
            voice_entry_id=tx.voice_entry_id,
            occurred_at=tx.occurred_at,
            item=tx.item,
            amount_vnd=tx.amount_vnd,
            category_id=tx.category_id,
            category=tx.category.name if tx.category else None,
            method=tx.method,
            merchant=tx.merchant,
            note=tx.note,
            created_at=tx.created_at,
            updated_at=tx.updated_at,
        )


class VoiceResult(BaseModel):
    entry_id: uuid.UUID
    raw_text: str | None
    transactions: list[TransactionOut]


class TransactionPatch(BaseModel):
    """Only the fields SPEC allows editing. Omitted fields stay unchanged."""

    model_config = ConfigDict(extra="forbid")

    item: Annotated[str, StringConstraints(strip_whitespace=True, min_length=1)] = None
    amount_vnd: Annotated[StrictInt, Field(gt=0)] = None
    category_id: StrictInt | None = None
    method: Literal["cash", "card", "transfer"] | None = None
    merchant: str | None = None
    occurred_at: AwareDatetime = None

    @field_validator("item", "amount_vnd", "occurred_at", mode="before")
    @classmethod
    def not_null(cls, v):
        if v is None:
            raise ValueError("không được để trống")
        return v


class CategoryTotal(BaseModel):
    category_id: int | None
    category: str | None
    total_vnd: int
    count: int


class SummaryOut(BaseModel):
    month: str
    total_vnd: int
    count: int
    prev_month: str
    prev_total_vnd: int
    diff_vnd: int
    diff_pct: float | None  # display only; money stays integer
    by_category: list[CategoryTotal]
