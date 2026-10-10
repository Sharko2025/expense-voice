"""initial schema and seed categories

Revision ID: 0001
Revises:
Create Date: 2026-10-10
"""
import sqlalchemy as sa
from alembic import op

revision = "0001"
down_revision = None
branch_labels = None
depends_on = None

CATEGORIES = [
    "Ăn sáng", "Ăn trưa", "Ăn tối", "Cafe/Đồ uống", "Gửi xe", "Xăng/Di chuyển",
    "Đi chợ/Siêu thị", "Mua sắm", "Hóa đơn", "Giải trí", "Sức khỏe", "Khác",
]


def upgrade() -> None:
    # DDL mirrors SPEC.md "Schema DB".
    op.execute("""
        CREATE TABLE categories (
          id          SERIAL PRIMARY KEY,
          name        TEXT UNIQUE NOT NULL,
          sort_order  INT DEFAULT 0,
          is_active   BOOLEAN DEFAULT TRUE
        )
    """)
    op.execute("""
        CREATE TABLE voice_entries (
          id           UUID PRIMARY KEY,
          recorded_at  TIMESTAMPTZ NOT NULL,
          received_at  TIMESTAMPTZ DEFAULT now(),
          raw_text     TEXT,
          status       TEXT NOT NULL,
          error        TEXT,
          latitude     DOUBLE PRECISION,
          longitude    DOUBLE PRECISION,
          stt_ms       INT,
          parse_ms     INT,
          total_ms     INT
        )
    """)
    op.execute("""
        CREATE TABLE transactions (
          id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
          voice_entry_id  UUID REFERENCES voice_entries(id) ON DELETE CASCADE,
          occurred_at     TIMESTAMPTZ NOT NULL,
          item            TEXT NOT NULL,
          amount_vnd      BIGINT NOT NULL
                          CONSTRAINT transactions_amount_vnd_check CHECK (amount_vnd > 0),
          category_id     INT REFERENCES categories(id),
          method          TEXT
                          CONSTRAINT transactions_method_check CHECK (method IN ('cash','card','transfer')),
          merchant        TEXT,
          note            TEXT,
          deleted_at      TIMESTAMPTZ,
          created_at      TIMESTAMPTZ DEFAULT now(),
          updated_at      TIMESTAMPTZ DEFAULT now()
        )
    """)
    op.execute(
        "CREATE INDEX transactions_occurred_at_idx ON transactions (occurred_at) WHERE deleted_at IS NULL"
    )

    categories = sa.table("categories", sa.column("name", sa.Text), sa.column("sort_order", sa.Integer))
    op.bulk_insert(categories, [{"name": n, "sort_order": i} for i, n in enumerate(CATEGORIES, start=1)])


def downgrade() -> None:
    op.execute("DROP TABLE transactions")
    op.execute("DROP TABLE voice_entries")
    op.execute("DROP TABLE categories")
