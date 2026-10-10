import json
from typing import Protocol

import openai

from app.config import get_settings
from app.services.errors import AIServiceError
from app.services.stt import openai_client

# Copied verbatim from SPEC.md "Pipeline AI". Change only together with the openai test suite.
SYSTEM_PROMPT = """Bạn tách câu nói tiếng Việt về chi tiêu thành danh sách giao dịch.
Quy tắc:
- Mỗi món có số tiền là 1 giao dịch. Một câu có thể có nhiều giao dịch.
- Số tiền ra số nguyên VND: "20k" = "20 nghìn" = "20 ngàn" = "20 ca" = "hai chục" = 20000; "1 triệu 2" = 1200000; "20 000 vnd" = 20000. Số trơn dưới 1000 ("cafe 27") hiểu là nghìn.
- "gửi xe 2 lần 5k" = 1 giao dịch 10000, item ghi "gửi xe (2 lần)".
- method: "tiền mặt"/"cash" -> cash; "cà thẻ"/"quẹt thẻ"/"chạm"/"thẻ" -> card; "chuyển khoản"/"CK"/"quét QR"/"QR" -> transfer. Không nói -> null. Không đoán.
- merchant: tên cửa hàng/nơi mua nếu được nói ("FamilyMart", "bánh mì cô Hai", "ở Vincom" -> "Vincom"). Không nói -> null.
- category: chọn đúng 1 tên trong danh sách: {categories}. Không chắc -> "Khác".
- item: tên món ngắn gọn, giữ nguyên tiếng Việt.
- Không có số tiền -> trả transactions rỗng."""

JSON_SCHEMA = {
    "type": "object",
    "additionalProperties": False,
    "required": ["transactions"],
    "properties": {
        "transactions": {
            "type": "array",
            "items": {
                "type": "object",
                "additionalProperties": False,
                "required": ["item", "amount_vnd", "category", "method", "merchant"],
                "properties": {
                    "item": {"type": "string"},
                    "amount_vnd": {"type": "integer"},
                    "category": {"type": "string"},
                    "method": {"type": ["string", "null"], "enum": ["cash", "card", "transfer", None]},
                    "merchant": {"type": ["string", "null"]},
                },
            },
        }
    },
}


class TransactionParser(Protocol):
    def parse(self, text: str, category_names: list[str]) -> list[dict]: ...


def build_system_prompt(category_names: list[str]) -> str:
    return SYSTEM_PROMPT.replace("{categories}", ", ".join(category_names))


class OpenAIParser:
    def __init__(self, client: openai.OpenAI, model: str):
        self.client, self.model = client, model

    def parse(self, text: str, category_names: list[str]) -> list[dict]:
        try:
            resp = self.client.chat.completions.create(
                model=self.model,
                temperature=0,
                messages=[
                    {"role": "system", "content": build_system_prompt(category_names)},
                    {"role": "user", "content": text},
                ],
                response_format={
                    "type": "json_schema",
                    "json_schema": {"name": "transactions", "strict": True, "schema": JSON_SCHEMA},
                },
            )
        except openai.OpenAIError as e:
            raise AIServiceError(type(e).__name__) from e

        content = resp.choices[0].message.content
        if not content:
            raise AIServiceError("EmptyResponse")
        try:
            return json.loads(content)["transactions"]
        except (json.JSONDecodeError, KeyError, TypeError) as e:
            raise AIServiceError("InvalidJSON") from e


def get_parser() -> TransactionParser:
    return OpenAIParser(openai_client(), get_settings().openai_parse_model)
