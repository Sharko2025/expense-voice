from datetime import datetime

from tests.test_transactions import VN, vn


def get_summary(client, auth, month):
    resp = client.get(f"/api/v1/summary?month={month}", headers=auth)
    assert resp.status_code == 200, resp.text
    return resp.json()


def test_month_totals_and_categories(client, auth, make_tx):
    make_tx(vn(2026, 10, 1, 7), amount=20000, category="Ăn sáng")
    make_tx(vn(2026, 10, 2, 7), amount=25000, category="Ăn sáng")
    make_tx(vn(2026, 10, 5, 12), amount=1200000, category="Đi chợ/Siêu thị")
    make_tx(vn(2026, 10, 6, 9), amount=27000, category="Cafe/Đồ uống")

    s = get_summary(client, auth, "2026-10")
    assert s["month"] == "2026-10"
    assert s["total_vnd"] == 1272000
    assert s["count"] == 4
    assert [(c["category"], c["total_vnd"], c["count"]) for c in s["by_category"]] == [
        ("Đi chợ/Siêu thị", 1200000, 1),
        ("Ăn sáng", 45000, 2),
        ("Cafe/Đồ uống", 27000, 1),
    ]
    assert all(isinstance(c["category_id"], int) for c in s["by_category"])


def test_compares_with_previous_month(client, auth, make_tx):
    make_tx(vn(2026, 9, 15), amount=100000)
    make_tx(vn(2026, 10, 15), amount=115000)
    s = get_summary(client, auth, "2026-10")
    assert s["prev_month"] == "2026-09"
    assert s["prev_total_vnd"] == 100000
    assert s["diff_vnd"] == 15000
    assert s["diff_pct"] == 15.0


def test_diff_pct_negative_and_rounded(client, auth, make_tx):
    make_tx(vn(2026, 9, 15), amount=300000)
    make_tx(vn(2026, 10, 15), amount=200000)
    s = get_summary(client, auth, "2026-10")
    assert s["diff_vnd"] == -100000
    assert s["diff_pct"] == -33.3


def test_diff_pct_null_when_previous_month_empty(client, auth, make_tx):
    make_tx(vn(2026, 10, 15), amount=50000)
    s = get_summary(client, auth, "2026-10")
    assert s["prev_total_vnd"] == 0
    assert s["diff_vnd"] == 50000
    assert s["diff_pct"] is None


def test_month_boundaries_in_vietnam_time(client, auth, make_tx):
    make_tx(vn(2026, 10, 31, 23, 59), amount=10000)  # 16:59 UTC Oct 31 -> October
    make_tx(vn(2026, 11, 1, 0, 1), amount=20000)  # 17:01 UTC Oct 31 -> November
    make_tx(vn(2026, 10, 1, 0, 1), amount=40000)  # 17:01 UTC Sep 30 -> October
    assert get_summary(client, auth, "2026-10")["total_vnd"] == 50000
    assert get_summary(client, auth, "2026-11")["total_vnd"] == 20000


def test_january_compares_with_previous_december(client, auth, make_tx):
    make_tx(vn(2025, 12, 20), amount=10000)
    s = get_summary(client, auth, "2026-01")
    assert s["prev_month"] == "2025-12" and s["prev_total_vnd"] == 10000


def test_soft_deleted_excluded(client, auth, make_tx):
    make_tx(vn(2026, 10, 15), amount=50000)
    make_tx(vn(2026, 10, 15), amount=99000, deleted=True)
    make_tx(vn(2026, 9, 15), amount=77000, deleted=True)
    s = get_summary(client, auth, "2026-10")
    assert s["total_vnd"] == 50000 and s["count"] == 1 and s["prev_total_vnd"] == 0


def test_empty_month(client, auth):
    s = get_summary(client, auth, "2030-01")
    assert (s["total_vnd"], s["count"], s["by_category"], s["diff_pct"]) == (0, 0, [], None)


def test_defaults_to_current_month(client, auth, make_tx):
    make_tx(datetime.now(VN), amount=12000)
    resp = client.get("/api/v1/summary", headers=auth)
    assert resp.status_code == 200
    assert resp.json()["month"] == datetime.now(VN).strftime("%Y-%m")
    assert resp.json()["total_vnd"] == 12000


def test_amounts_are_integers(client, auth, make_tx):
    make_tx(vn(2026, 10, 15), amount=5_000_000_000)
    s = get_summary(client, auth, "2026-10")
    assert s["total_vnd"] == 5_000_000_000 and isinstance(s["total_vnd"], int)


def test_invalid_month_rejected(client, auth):
    for bad in ("2026-13", "2026-1", "abc", "2026-10-01"):
        assert client.get(f"/api/v1/summary?month={bad}", headers=auth).status_code == 422
