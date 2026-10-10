"""Post-checks on LLM parse output before anything touches the database."""
from dataclasses import dataclass

FALLBACK_CATEGORY = "Khác"
VALID_METHODS = {"cash", "card", "transfer"}


@dataclass(frozen=True)
class NormalizedTransaction:
    item: str
    amount_vnd: int
    category_id: int
    method: str | None
    merchant: str | None


def normalize(raw: list[dict], categories: dict[str, int]) -> list[NormalizedTransaction]:
    """Drop invalid rows, map category names to ids. Never invents a payment method."""
    result = []
    for tx in raw:
        item = (tx.get("item") or "").strip()
        amount = tx.get("amount_vnd")
        if not item or not isinstance(amount, int) or amount <= 0:
            continue
        category = (tx.get("category") or "").strip()
        category_id = categories.get(category, categories[FALLBACK_CATEGORY])
        method = tx.get("method")
        merchant = (tx.get("merchant") or "").strip() or None
        result.append(
            NormalizedTransaction(
                item=item,
                amount_vnd=amount,
                category_id=category_id,
                method=method if method in VALID_METHODS else None,
                merchant=merchant,
            )
        )
    return result
