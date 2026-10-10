"""Real OpenAI parse tests (SPEC: Pipeline AI, mandatory suite). Run: pytest -m openai"""
import pytest

from app.services.parser import get_parser

pytestmark = pytest.mark.openai

CATEGORIES = [
    "Ăn sáng", "Ăn trưa", "Ăn tối", "Cafe/Đồ uống", "Gửi xe", "Xăng/Di chuyển",
    "Đi chợ/Siêu thị", "Mua sắm", "Hóa đơn", "Giải trí", "Sức khỏe", "Khác",
]


def parse(text):
    return get_parser().parse(text, CATEGORIES)


def test_banh_mi_cash():
    [tx] = parse("bánh mì ăn sáng 20k tiền mặt")
    assert "bánh mì" in tx["item"].lower()
    assert (tx["amount_vnd"], tx["category"], tx["method"], tx["merchant"]) == (20000, "Ăn sáng", "cash", None)


def test_cafe_familymart_card():
    [tx] = parse("cafe 27k FamilyMart cà thẻ")
    assert "cafe" in tx["item"].lower() or "cà phê" in tx["item"].lower()
    assert (tx["amount_vnd"], tx["category"], tx["method"], tx["merchant"]) == (27000, "Cafe/Đồ uống", "card", "FamilyMart")


def test_gui_xe_no_method():
    [tx] = parse("gửi xe 5 nghìn")
    assert "gửi xe" in tx["item"].lower()
    assert (tx["amount_vnd"], tx["category"], tx["method"], tx["merchant"]) == (5000, "Gửi xe", None, None)


def test_two_transactions_with_merchant():
    txs = parse("nước cam 15 ca, gửi xe 2 lần 5k ở Vincom")
    assert len(txs) == 2
    by_amount = {t["amount_vnd"]: t for t in txs}
    assert set(by_amount) == {15000, 10000}
    assert "nước cam" in by_amount[15000]["item"].lower()
    assert "gửi xe" in by_amount[10000]["item"].lower()
    # Pending decision: only the parking transaction is required to carry the merchant.
    assert by_amount[10000]["merchant"] == "Vincom"
    assert by_amount[15000]["merchant"] in ("Vincom", None)
    assert all(t["method"] is None for t in txs)


def test_supermarket_transfer():
    [tx] = parse("đi siêu thị 1 triệu 2 chuyển khoản")
    assert (tx["amount_vnd"], tx["category"], tx["method"]) == (1200000, "Đi chợ/Siêu thị", "transfer")


def test_no_amount_no_transactions():
    assert parse("hôm nay trời đẹp") == []
