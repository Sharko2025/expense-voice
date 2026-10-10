from app.services.normalize import NormalizedTransaction, normalize

CATEGORIES = {"Ăn sáng": 1, "Cafe/Đồ uống": 4, "Gửi xe": 5, "Khác": 12}


def tx(**overrides):
    base = {"item": "bánh mì", "amount_vnd": 20000, "category": "Ăn sáng", "method": "cash", "merchant": None}
    base.update(overrides)
    return base


def test_valid_transaction_passes_through():
    assert normalize([tx()], CATEGORIES) == [
        NormalizedTransaction(item="bánh mì", amount_vnd=20000, category_id=1, method="cash", merchant=None)
    ]


def test_unknown_category_falls_back_to_khac():
    [result] = normalize([tx(category="Ăn vặt")], CATEGORIES)
    assert result.category_id == 12


def test_non_positive_amount_dropped():
    assert normalize([tx(amount_vnd=0), tx(amount_vnd=-5000), tx(item="cafe")], CATEGORIES) == [
        NormalizedTransaction(item="cafe", amount_vnd=20000, category_id=1, method="cash", merchant=None)
    ]


def test_blank_item_dropped():
    assert normalize([tx(item="   "), tx(item="")], CATEGORIES) == []


def test_null_method_is_never_filled():
    [result] = normalize([tx(method=None)], CATEGORIES)
    assert result.method is None


def test_unexpected_method_becomes_null():
    [result] = normalize([tx(method="momo")], CATEGORIES)
    assert result.method is None


def test_whitespace_trimmed_and_blank_merchant_is_null():
    [a, b] = normalize([tx(item="  cafe ", merchant="  FamilyMart "), tx(merchant="  ")], CATEGORIES)
    assert a.item == "cafe" and a.merchant == "FamilyMart"
    assert b.merchant is None


def test_category_match_ignores_surrounding_whitespace():
    [result] = normalize([tx(category=" Gửi xe ")], CATEGORIES)
    assert result.category_id == 5


def test_empty_input():
    assert normalize([], CATEGORIES) == []
