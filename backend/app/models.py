import uuid
from datetime import datetime

from sqlalchemy import (
    BigInteger,
    Boolean,
    CheckConstraint,
    DateTime,
    Float,
    ForeignKey,
    Index,
    Integer,
    Text,
    func,
    text,
)
from sqlalchemy.dialects.postgresql import UUID
from sqlalchemy.orm import DeclarativeBase, Mapped, mapped_column, relationship


class Base(DeclarativeBase):
    pass


class Category(Base):
    __tablename__ = "categories"

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    name: Mapped[str] = mapped_column(Text, unique=True, nullable=False)
    sort_order: Mapped[int | None] = mapped_column(Integer, server_default=text("0"))
    is_active: Mapped[bool | None] = mapped_column(Boolean, server_default=text("true"))


class VoiceEntry(Base):
    __tablename__ = "voice_entries"

    id: Mapped[uuid.UUID] = mapped_column(UUID(as_uuid=True), primary_key=True)
    recorded_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False)
    received_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), server_default=func.now())
    raw_text: Mapped[str | None] = mapped_column(Text)
    status: Mapped[str] = mapped_column(Text, nullable=False)  # received | transcribed | parsed | failed
    error: Mapped[str | None] = mapped_column(Text)
    latitude: Mapped[float | None] = mapped_column(Float)
    longitude: Mapped[float | None] = mapped_column(Float)
    stt_ms: Mapped[int | None] = mapped_column(Integer)
    parse_ms: Mapped[int | None] = mapped_column(Integer)
    total_ms: Mapped[int | None] = mapped_column(Integer)

    transactions: Mapped[list["Transaction"]] = relationship(back_populates="voice_entry")


class Transaction(Base):
    __tablename__ = "transactions"
    __table_args__ = (
        CheckConstraint("amount_vnd > 0", name="transactions_amount_vnd_check"),
        CheckConstraint("method IN ('cash','card','transfer')", name="transactions_method_check"),
        Index("transactions_occurred_at_idx", "occurred_at", postgresql_where=text("deleted_at IS NULL")),
    )

    id: Mapped[uuid.UUID] = mapped_column(
        UUID(as_uuid=True), primary_key=True, server_default=text("gen_random_uuid()")
    )
    voice_entry_id: Mapped[uuid.UUID | None] = mapped_column(
        UUID(as_uuid=True), ForeignKey("voice_entries.id", ondelete="CASCADE")
    )
    occurred_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), nullable=False)
    item: Mapped[str] = mapped_column(Text, nullable=False)
    amount_vnd: Mapped[int] = mapped_column(BigInteger, nullable=False)
    category_id: Mapped[int | None] = mapped_column(Integer, ForeignKey("categories.id"))
    method: Mapped[str | None] = mapped_column(Text)  # NULL = not stated, never guessed
    merchant: Mapped[str | None] = mapped_column(Text)
    note: Mapped[str | None] = mapped_column(Text)
    deleted_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True))
    created_at: Mapped[datetime | None] = mapped_column(DateTime(timezone=True), server_default=func.now())
    updated_at: Mapped[datetime | None] = mapped_column(
        DateTime(timezone=True), server_default=func.now(), onupdate=func.now()
    )

    voice_entry: Mapped[VoiceEntry | None] = relationship(back_populates="transactions")
    category: Mapped[Category | None] = relationship(lazy="joined")
