import pytest

PROTECTED = [
    ("get", "/api/v1/categories"),
    ("get", "/api/v1/transactions"),
    ("get", "/api/v1/summary"),
    ("post", "/api/v1/voice"),
    ("patch", "/api/v1/transactions/00000000-0000-0000-0000-000000000000"),
    ("delete", "/api/v1/transactions/00000000-0000-0000-0000-000000000000"),
    ("post", "/api/v1/transactions/00000000-0000-0000-0000-000000000000/restore"),
    ("post", "/api/v1/voice/00000000-0000-0000-0000-000000000000/undo"),
]


@pytest.mark.parametrize("method,path", PROTECTED)
def test_missing_token_rejected(client, method, path):
    resp = getattr(client, method)(path)
    assert resp.status_code == 401
    assert resp.json()["detail"] == "Token không hợp lệ"


@pytest.mark.parametrize("method,path", PROTECTED)
def test_wrong_token_rejected(client, method, path):
    resp = getattr(client, method)(path, headers={"Authorization": "Bearer wrong"})
    assert resp.status_code == 401


def test_non_bearer_scheme_rejected(client, auth):
    token = auth["Authorization"].removeprefix("Bearer ")
    resp = client.get("/api/v1/categories", headers={"Authorization": f"Basic {token}"})
    assert resp.status_code == 401


def test_valid_token_accepted(client, auth):
    assert client.get("/api/v1/categories", headers=auth).status_code == 200
