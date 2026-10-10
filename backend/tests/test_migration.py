import uuid
from datetime import datetime, timezone

import pytest
from alembic import command
from sqlalchemy import inspect, text
from sqlalchemy.exc import IntegrityError

from tests.conftest import alembic_config

EXPECTED_CATEGORIES = [
    "Ăn sáng", "Ăn trưa", "Ăn tối", "Cafe/Đồ uống", "Gửi xe", "Xăng/Di chuyển",
    "Đi chợ/Siêu thị", "Mua sắm", "Hóa đơn", "Giải trí", "Sức khỏe", "Khác",
]


def test_tables_exist(engine):
    names = set(inspect(engine).get_table_names())
    assert {"categories", "voice_entries", "transactions"} <= names


def test_seeded_categories_in_order(db):
    rows = db.execute(text("SELECT name, sort_order, is_active FROM categories ORDER BY sort_order")).all()
    assert [r.name for r in rows] == EXPECTED_CATEGORIES
    assert [r.sort_order for r in rows] == list(range(1, 13))
    assert all(r.is_active for r in rows)


def test_partial_index_on_occurred_at(engine):
    indexes = inspect(engine).get_indexes("transactions")
    idx = [i for i in indexes if i["column_names"] == ["occurred_at"]]
    assert idx, "missing index on transactions(occurred_at)"
    assert "deleted_at IS NULL" in idx[0]["dialect_options"]["postgresql_where"]


def _insert_entry(db) -> uuid.UUID:
    entry_id = uuid.uuid4()
    db.execute(
        text("INSERT INTO voice_entries (id, recorded_at, status) VALUES (:id, :at, 'parsed')"),
        {"id": entry_id, "at": datetime.now(timezone.utc)},
    )
    return entry_id


def _insert_tx(db, entry_id, amount=20000, method=None):
    db.execute(
        text(
            "INSERT INTO transactions (voice_entry_id, occurred_at, item, amount_vnd, method) "
            "VALUES (:e, now(), 'bánh mì', :a, :m)"
        ),
        {"e": entry_id, "a": amount, "m": method},
    )


def test_transaction_defaults(db):
    entry_id = _insert_entry(db)
    _insert_tx(db, entry_id)
    row = db.execute(text("SELECT id, created_at, updated_at, method, deleted_at FROM transactions")).one()
    assert row.id is not None and row.created_at is not None and row.updated_at is not None
    assert row.method is None and row.deleted_at is None


@pytest.mark.parametrize("amount", [0, -5000])
def test_amount_must_be_positive(db, amount):
    entry_id = _insert_entry(db)
    with pytest.raises(IntegrityError):
        with db.begin_nested():
            _insert_tx(db, entry_id, amount=amount)


def test_method_restricted(db):
    entry_id = _insert_entry(db)
    for ok in ("cash", "card", "transfer", None):
        _insert_tx(db, entry_id, method=ok)
    with pytest.raises(IntegrityError):
        with db.begin_nested():
            _insert_tx(db, entry_id, method="momo")


def test_amount_is_bigint(db):
    entry_id = _insert_entry(db)
    _insert_tx(db, entry_id, amount=5_000_000_000)  # > INT32 max
    assert db.execute(text("SELECT amount_vnd FROM transactions")).scalar() == 5_000_000_000


def test_downgrade_then_upgrade_is_clean(engine):
    cfg = alembic_config()
    command.downgrade(cfg, "base")
    assert "transactions" not in inspect(engine).get_table_names()
    command.upgrade(cfg, "head")
    assert "transactions" in inspect(engine).get_table_names()
