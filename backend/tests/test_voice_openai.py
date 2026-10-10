"""End-to-end with real audio recorded on the phone. Files live in ../samples (not committed)."""
import uuid
from pathlib import Path

import pytest

from app.config import get_settings

pytestmark = pytest.mark.openai

SAMPLES = Path(__file__).resolve().parents[1] / "samples"
# Recorded on the Fold 8 on 2026-10-11. (amount, category, method)
EXPECTED = {
    "E1_1.m4a": [(20000, "Ăn sáng", None)],  # ăn sáng bánh mì hai chục ngàn
    "E2_1.m4a": [(85000, "Ăn sáng", "transfer")],  # cơm với cá kho 85 ngàn chuyển khoản
    "E3_1.m4a": [(420000, "Mua sắm", "card")],  # mua đồ trên Shopee 420 ngàn cà thẻ
    "E4_1.m4a": [(45000, "Ăn trưa", None)],  # ăn trưa cơm 45 ngàn quán cơm Hà Nội
    "E5_1.m4a": [(1050000, "Đi chợ/Siêu thị", None)],  # siêu thị Coopmart 1 triệu không trăm năm chục
    "E6_1.m4a": [(120000, "Cafe/Đồ uống", None)],  # cà phê ở Trung Nguyên 120 ngàn
    "E7_1.m4a": [(20000, "Gửi xe", None)],  # gửi xe máy hai chục ngàn
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


@pytest.mark.parametrize("name,expected", EXPECTED.items())
def test_real_audio(real_client, name, expected):
    path = SAMPLES / name
    if not path.exists():
        pytest.skip(f"missing sample {name}")
    resp = send(real_client, path)
    assert resp.status_code == 200, resp.text
    got = sorted((t["amount_vnd"], t["category"], t["method"]) for t in resp.json()["transactions"])
    assert got == sorted(expected, key=lambda e: e[0])


def test_real_audio_without_amount(real_client):
    path = SAMPLES / "troi-dep.m4a"
    if not path.exists():
        pytest.skip("missing sample troi-dep.m4a")
    resp = send(real_client, path)
    assert resp.status_code == 422
    assert resp.json()["raw_text"]
