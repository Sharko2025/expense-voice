"""End-to-end with real audio recorded on the phone. Files live in ../samples (not committed)."""
import uuid
from pathlib import Path

import pytest

from app.config import get_settings

pytestmark = pytest.mark.openai

SAMPLES = Path(__file__).resolve().parents[1] / "samples"
EXPECTED = {
    "banh-mi.m4a": [20000],
    "cafe-familymart.m4a": [27000],
    "gui-xe.m4a": [5000],
    "nuoc-cam-vincom.m4a": [10000, 15000],
    "sieu-thi.m4a": [1200000],
}


@pytest.fixture
def real_client(app):
    from fastapi.testclient import TestClient

    return TestClient(app)


def send(client, path):
    return client.post(
        "/api/v1/voice",
        headers={"Authorization": f"Bearer {get_settings().device_token}"},
        data={"entry_id": str(uuid.uuid4()), "recorded_at": "2026-10-10T08:15:00+07:00"},
        files={"file": (path.name, path.read_bytes(), "audio/mp4")},
    )


@pytest.mark.parametrize("name,amounts", EXPECTED.items())
def test_real_audio(real_client, name, amounts):
    path = SAMPLES / name
    if not path.exists():
        pytest.skip(f"missing sample {name}")
    resp = send(real_client, path)
    assert resp.status_code == 200, resp.text
    assert sorted(t["amount_vnd"] for t in resp.json()["transactions"]) == amounts


def test_real_audio_without_amount(real_client):
    path = SAMPLES / "troi-dep.m4a"
    if not path.exists():
        pytest.skip("missing sample troi-dep.m4a")
    resp = send(real_client, path)
    assert resp.status_code == 422
    assert resp.json()["raw_text"]
