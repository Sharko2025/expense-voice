import uuid
from datetime import date, datetime, timedelta, timezone
from zoneinfo import ZoneInfo

import pytest
from sqlalchemy import select

from app.models import Category, Transaction, VoiceEntry

VN = ZoneInfo("Asia/Ho_Chi_Minh")


def vn(y, m, d, hh=12, mm=0):
    return datetime(y, m, d, hh, mm, tzinfo=VN)


# ---- GET /transactions ----


def test_list_by_vietnam_day_boundaries(client, auth, make_tx):
    late = make_tx(vn(2026, 10, 10, 23, 30), item="late")  # 16:30 UTC on the 10th
    early_next = make_tx(vn(2026, 10, 11, 0, 10), item="next")  # 17:10 UTC on the 10th
    make_tx(vn(2026, 10, 9, 23, 59), item="before")

    resp = client.get("/api/v1/transactions?from=2026-10-10&to=2026-10-10", headers=auth)
    assert resp.status_code == 200
    assert [t["id"] for t in resp.json()] == [str(late.id)]

    resp = client.get("/api/v1/transactions?from=2026-10-11&to=2026-10-11", headers=auth)
    assert [t["id"] for t in resp.json()] == [str(early_next.id)]


def test_list_range_inclusive_newest_first(client, auth, make_tx):
    a = make_tx(vn(2026, 10, 1, 8))
    b = make_tx(vn(2026, 10, 3, 9))
    c = make_tx(vn(2026, 10, 3, 18))
    make_tx(vn(2026, 10, 4, 0, 0))
    resp = client.get("/api/v1/transactions?from=2026-10-01&to=2026-10-03", headers=auth)
    assert [t["id"] for t in resp.json()] == [str(c.id), str(b.id), str(a.id)]


def test_list_excludes_soft_deleted(client, auth, make_tx):
    make_tx(vn(2026, 10, 10), deleted=True)
    resp = client.get("/api/v1/transactions?from=2026-10-10&to=2026-10-10", headers=auth)
    assert resp.json() == []


def test_list_defaults_to_today(client, auth, make_tx):
    now = make_tx(datetime.now(VN))
    make_tx(datetime.now(VN) - timedelta(days=2))
    resp = client.get("/api/v1/transactions", headers=auth)
    assert [t["id"] for t in resp.json()] == [str(now.id)]


def test_list_payload_shape(client, auth, make_tx):
    make_tx(vn(2026, 10, 10), method="card")
    [t] = client.get("/api/v1/transactions?from=2026-10-10&to=2026-10-10", headers=auth).json()
    assert t["category"] == "Ăn sáng" and t["method"] == "card" and t["amount_vnd"] == 20000


def test_list_rejects_inverted_range(client, auth):
    resp = client.get("/api/v1/transactions?from=2026-10-10&to=2026-10-01", headers=auth)
    assert resp.status_code == 422


# ---- PATCH /transactions/{id} ----


def test_patch_updates_allowed_fields(client, auth, db, make_tx):
    tx = make_tx(vn(2026, 10, 10))
    cafe_id = db.scalar(select(Category.id).where(Category.name == "Cafe/Đồ uống"))
    new_time = "2026-10-10T07:00:00+07:00"
    resp = client.patch(
        f"/api/v1/transactions/{tx.id}",
        headers=auth,
        json={
            "item": "cà phê sữa",
            "amount_vnd": 29000,
            "category_id": cafe_id,
            "method": "transfer",
            "merchant": "Highlands",
            "occurred_at": new_time,
        },
    )
    assert resp.status_code == 200
    body = resp.json()
    assert (body["item"], body["amount_vnd"], body["category"], body["method"], body["merchant"]) == (
        "cà phê sữa", 29000, "Cafe/Đồ uống", "transfer", "Highlands",
    )
    assert datetime.fromisoformat(body["occurred_at"]) == datetime.fromisoformat(new_time)


def test_patch_partial_keeps_other_fields(client, auth, make_tx):
    tx = make_tx(vn(2026, 10, 10), method="cash")
    resp = client.patch(f"/api/v1/transactions/{tx.id}", headers=auth, json={"amount_vnd": 25000})
    body = resp.json()
    assert body["amount_vnd"] == 25000 and body["item"] == "bánh mì" and body["method"] == "cash"


def test_patch_can_clear_method_and_merchant(client, auth, make_tx):
    tx = make_tx(vn(2026, 10, 10), method="cash")
    resp = client.patch(f"/api/v1/transactions/{tx.id}", headers=auth, json={"method": None, "merchant": None})
    assert resp.status_code == 200
    assert resp.json()["method"] is None and resp.json()["merchant"] is None


def test_patch_updates_updated_at(client, auth, db, make_tx):
    tx = make_tx(vn(2026, 10, 10))
    db.execute(Transaction.__table__.update().values(updated_at=datetime(2020, 1, 1, tzinfo=timezone.utc)))
    resp = client.patch(f"/api/v1/transactions/{tx.id}", headers=auth, json={"item": "xôi"})
    assert datetime.fromisoformat(resp.json()["updated_at"]).year > 2020


@pytest.mark.parametrize(
    "payload",
    [
        {"amount_vnd": 0},
        {"amount_vnd": -1000},
        {"amount_vnd": 20000.5},
        {"amount_vnd": None},
        {"item": ""},
        {"item": "   "},
        {"item": None},
        {"method": "momo"},
        {"category_id": 99999},
        {"occurred_at": "2026-10-10T07:00:00"},
        {"occurred_at": None},
        {"note": "not editable"},
    ],
)
def test_patch_rejects_invalid(client, auth, make_tx, payload):
    tx = make_tx(vn(2026, 10, 10))
    resp = client.patch(f"/api/v1/transactions/{tx.id}", headers=auth, json=payload)
    assert resp.status_code == 422


def test_patch_unknown_or_deleted_returns_404(client, auth, make_tx):
    resp = client.patch(f"/api/v1/transactions/{uuid.uuid4()}", headers=auth, json={"item": "x"})
    assert resp.status_code == 404
    tx = make_tx(vn(2026, 10, 10), deleted=True)
    resp = client.patch(f"/api/v1/transactions/{tx.id}", headers=auth, json={"item": "x"})
    assert resp.status_code == 404


# ---- DELETE / restore ----


def test_delete_is_soft_and_idempotent(client, auth, db, make_tx):
    tx = make_tx(vn(2026, 10, 10))
    assert client.delete(f"/api/v1/transactions/{tx.id}", headers=auth).status_code == 204
    db.refresh(tx)
    assert tx.deleted_at is not None
    assert client.delete(f"/api/v1/transactions/{tx.id}", headers=auth).status_code == 204
    assert db.get(Transaction, tx.id) is not None


def test_delete_unknown_returns_404(client, auth):
    assert client.delete(f"/api/v1/transactions/{uuid.uuid4()}", headers=auth).status_code == 404


def test_restore(client, auth, db, make_tx):
    tx = make_tx(vn(2026, 10, 10), deleted=True)
    resp = client.post(f"/api/v1/transactions/{tx.id}/restore", headers=auth)
    assert resp.status_code == 200
    assert resp.json()["id"] == str(tx.id)
    db.refresh(tx)
    assert tx.deleted_at is None
    listed = client.get("/api/v1/transactions?from=2026-10-10&to=2026-10-10", headers=auth).json()
    assert [t["id"] for t in listed] == [str(tx.id)]


def test_restore_unknown_returns_404(client, auth):
    assert client.post(f"/api/v1/transactions/{uuid.uuid4()}/restore", headers=auth).status_code == 404


# ---- POST /voice/{entry_id}/undo ----


def test_undo_voice_entry_soft_deletes_all_its_transactions(client, auth, db, make_tx):
    first = make_tx(vn(2026, 10, 10))
    entry = db.get(VoiceEntry, first.voice_entry_id)
    second = make_tx(vn(2026, 10, 10), item="cafe", entry=entry)
    other = make_tx(vn(2026, 10, 10), item="khác")

    resp = client.post(f"/api/v1/voice/{entry.id}/undo", headers=auth)
    assert resp.status_code == 204
    for tx in (first, second, other):
        db.refresh(tx)
    assert first.deleted_at is not None and second.deleted_at is not None
    assert other.deleted_at is None


def test_undo_unknown_entry_returns_404(client, auth):
    assert client.post(f"/api/v1/voice/{uuid.uuid4()}/undo", headers=auth).status_code == 404


def test_today_helper_uses_vietnam_timezone():
    from app.timeutil import day_range

    start, end = day_range(date(2026, 10, 10), date(2026, 10, 10))
    assert start == datetime(2026, 10, 9, 17, 0, tzinfo=timezone.utc)
    assert end == datetime(2026, 10, 10, 17, 0, tzinfo=timezone.utc)
