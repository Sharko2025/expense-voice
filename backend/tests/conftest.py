import os
import uuid
from datetime import datetime, timezone

import pytest
from alembic import command
from alembic.config import Config
from fastapi.testclient import TestClient
from sqlalchemy import create_engine, select
from sqlalchemy.orm import Session

from app.config import get_settings
from app.db import get_db
from app.main import create_app
from app.models import Category, Transaction, VoiceEntry

TEST_DATABASE_URL = os.environ["TEST_DATABASE_URL"]


def alembic_config() -> Config:
    cfg = Config(os.path.join(os.path.dirname(__file__), "..", "alembic.ini"))
    cfg.set_main_option("sqlalchemy.url", TEST_DATABASE_URL)
    return cfg


@pytest.fixture(scope="session")
def engine():
    cfg = alembic_config()
    command.downgrade(cfg, "base")
    command.upgrade(cfg, "head")
    eng = create_engine(TEST_DATABASE_URL)
    yield eng
    eng.dispose()


@pytest.fixture
def db(engine):
    """Session inside an outer transaction that is rolled back after each test."""
    conn = engine.connect()
    trans = conn.begin()
    session = Session(bind=conn, join_transaction_mode="create_savepoint")
    yield session
    session.close()
    trans.rollback()
    conn.close()


@pytest.fixture
def app(db):
    application = create_app()
    application.dependency_overrides[get_db] = lambda: db
    return application


@pytest.fixture
def client(app):
    return TestClient(app)


@pytest.fixture
def auth():
    return {"Authorization": f"Bearer {get_settings().device_token}"}


class FakeTranscriber:
    def __init__(self, text="bánh mì ăn sáng 20k tiền mặt", error=None):
        self.text, self.error = text, error
        self.calls = []  # (path, existed_at_call_time)

    def transcribe(self, path):
        self.calls.append((path, path.exists()))
        if self.error:
            raise self.error
        return self.text


class FakeParser:
    def __init__(self, transactions=None, error=None):
        self.transactions = transactions if transactions is not None else [
            {"item": "bánh mì", "amount_vnd": 20000, "category": "Ăn sáng", "method": "cash", "merchant": None}
        ]
        self.error = error
        self.calls = []  # (text, category_names)

    def parse(self, text, category_names):
        self.calls.append((text, category_names))
        if self.error:
            raise self.error
        return self.transactions


@pytest.fixture
def stt(app):
    from app.services.stt import get_transcriber

    fake = FakeTranscriber()
    app.dependency_overrides[get_transcriber] = lambda: fake
    return fake


@pytest.fixture
def parser(app):
    from app.services.parser import get_parser

    fake = FakeParser()
    app.dependency_overrides[get_parser] = lambda: fake
    return fake


@pytest.fixture
def make_tx(db):
    def _make(occurred_at, item="bánh mì", amount=20000, category="Ăn sáng", method=None, entry=None, deleted=False):
        if entry is None:
            entry = VoiceEntry(id=uuid.uuid4(), recorded_at=occurred_at, status="parsed")
            db.add(entry)
            db.flush()
        cat_id = db.scalar(select(Category.id).where(Category.name == category))
        tx = Transaction(
            voice_entry_id=entry.id,
            occurred_at=occurred_at,
            item=item,
            amount_vnd=amount,
            category_id=cat_id,
            method=method,
            deleted_at=datetime.now(timezone.utc) if deleted else None,
        )
        db.add(tx)
        db.flush()
        return tx

    return _make
