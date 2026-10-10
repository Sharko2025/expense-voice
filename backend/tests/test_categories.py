from sqlalchemy import text


def test_lists_active_categories_in_sort_order(client, auth, db):
    db.execute(text("UPDATE categories SET is_active = false WHERE name = 'Giải trí'"))
    resp = client.get("/api/v1/categories", headers=auth)
    assert resp.status_code == 200
    body = resp.json()
    names = [c["name"] for c in body]
    assert names[0] == "Ăn sáng" and names[-1] == "Khác"
    assert "Giải trí" not in names
    assert len(names) == 11
    assert set(body[0]) == {"id", "name", "sort_order"}
