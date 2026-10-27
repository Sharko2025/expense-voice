import uuid
from datetime import datetime, timedelta, timezone

import pytest
from sqlalchemy import func, select, text

from app.models import Transaction, VoiceEntry
from app.services.errors import AIServiceError

RECORDED_AT = "2026-10-10T08:15:00+07:00"


def post_voice(client, auth, entry_id=None, recorded_at=RECORDED_AT, audio=b"fake-m4a-bytes", **extra):
    entry_id = entry_id or str(uuid.uuid4())
    data = {"entry_id": entry_id, "recorded_at": recorded_at, **extra}
    resp = client.post(
        "/api/v1/voice", headers=auth, data=data, files={"file": ("rec.m4a", audio, "audio/mp4")}
    )
    return entry_id, resp


def count(db, model):
    return db.scalar(select(func.count()).select_from(model))


def test_creates_transactions(client, auth, db, stt, parser):
    parser.transactions = [
        {"item": "bánh mì", "amount_vnd": 20000, "category": "Ăn sáng", "method": "cash", "merchant": None},
        {"item": "cafe", "amount_vnd": 27000, "category": "Cafe/Đồ uống", "method": "card", "merchant": "FamilyMart"},
    ]
    entry_id, resp = post_voice(client, auth)

    assert resp.status_code == 200
    body = resp.json()
    assert body["entry_id"] == entry_id
    assert body["raw_text"] == stt.text
    assert [(t["item"], t["amount_vnd"], t["category"], t["method"], t["merchant"]) for t in body["transactions"]] == [
        ("bánh mì", 20000, "Ăn sáng", "cash", None),
        ("cafe", 27000, "Cafe/Đồ uống", "card", "FamilyMart"),
    ]
    # occurred_at defaults to recorded_at
    assert all(
        datetime.fromisoformat(t["occurred_at"]) == datetime.fromisoformat(RECORDED_AT) for t in body["transactions"]
    )
    entry = db.get(VoiceEntry, uuid.UUID(entry_id))
    assert entry.status == "parsed"
    assert entry.raw_text == stt.text
    assert count(db, Transaction) == 2


def test_parser_receives_active_category_names(client, auth, stt, parser):
    post_voice(client, auth)
    text_arg, names = parser.calls[0]
    assert text_arg == stt.text
    assert names[0] == "Ăn sáng" and names[-1] == "Khác" and len(names) == 12


def test_timings_recorded(client, auth, db, stt, parser):
    entry_id, _ = post_voice(client, auth)
    entry = db.get(VoiceEntry, uuid.UUID(entry_id))
    assert entry.stt_ms is not None and entry.parse_ms is not None and entry.total_ms is not None
    assert entry.total_ms >= entry.stt_ms


def test_lat_lng_stored(client, auth, db, stt, parser):
    entry_id, _ = post_voice(client, auth, lat="10.7769", lng="106.7009")
    entry = db.get(VoiceEntry, uuid.UUID(entry_id))
    assert (entry.latitude, entry.longitude) == (10.7769, 106.7009)


def test_resend_same_entry_id_is_idempotent(client, auth, db, stt, parser):
    entry_id, first = post_voice(client, auth)
    _, second = post_voice(client, auth, entry_id=entry_id)

    assert second.status_code == 200
    assert second.json() == first.json()
    assert len(stt.calls) == 1 and len(parser.calls) == 1
    assert count(db, Transaction) == 1
    assert count(db, VoiceEntry) == 1


def test_resend_after_undo_returns_remaining_transactions(client, auth, db, stt, parser):
    entry_id, _ = post_voice(client, auth)
    db.execute(text("UPDATE transactions SET deleted_at = now()"))
    _, resp = post_voice(client, auth, entry_id=entry_id)
    assert resp.status_code == 200
    assert resp.json()["transactions"] == []
    assert len(stt.calls) == 1


def test_no_transactions_returns_422_with_raw_text(client, auth, db, stt, parser):
    stt.text, parser.transactions = "hôm nay trời đẹp", []
    entry_id, resp = post_voice(client, auth)

    assert resp.status_code == 422
    assert resp.json() == {"detail": "Không hiểu, nói lại nhé", "raw_text": "hôm nay trời đẹp"}
    assert db.get(VoiceEntry, uuid.UUID(entry_id)).status == "parsed"
    assert count(db, Transaction) == 0


def test_resend_after_422_does_not_call_openai_again(client, auth, stt, parser):
    stt.text, parser.transactions = "hôm nay trời đẹp", []
    entry_id, _ = post_voice(client, auth)
    _, resp = post_voice(client, auth, entry_id=entry_id)
    assert resp.status_code == 422
    assert resp.json()["raw_text"] == "hôm nay trời đẹp"
    assert len(stt.calls) == 1 and len(parser.calls) == 1


def test_invalid_rows_from_llm_are_dropped(client, auth, db, stt, parser):
    parser.transactions = [
        {"item": "bánh mì", "amount_vnd": 0, "category": "Ăn sáng", "method": None, "merchant": None},
        {"item": "xôi", "amount_vnd": 15000, "category": "Không tồn tại", "method": None, "merchant": None},
    ]
    _, resp = post_voice(client, auth)
    [tx] = resp.json()["transactions"]
    assert (tx["item"], tx["category"], tx["method"]) == ("xôi", "Khác", None)


@pytest.mark.parametrize("stage", ["stt", "parse"])
def test_openai_failure_returns_502_and_marks_failed(client, auth, db, stt, parser, stage):
    fake = stt if stage == "stt" else parser
    fake.error = AIServiceError("APITimeoutError")
    entry_id, resp = post_voice(client, auth)

    assert resp.status_code == 502
    assert resp.json()["detail"] == "Lỗi xử lý giọng nói, sẽ thử lại"
    entry = db.get(VoiceEntry, uuid.UUID(entry_id))
    assert entry.status == "failed"
    assert stage in entry.error and "APITimeoutError" in entry.error
    assert count(db, Transaction) == 0


def test_resend_after_failure_reprocesses(client, auth, db, stt, parser):
    stt.error = AIServiceError("APIConnectionError")
    entry_id, _ = post_voice(client, auth)
    stt.error = None
    _, resp = post_voice(client, auth, entry_id=entry_id)

    assert resp.status_code == 200
    assert len(resp.json()["transactions"]) == 1
    entry = db.get(VoiceEntry, uuid.UUID(entry_id))
    assert entry.status == "parsed" and entry.error is None
    assert count(db, Transaction) == 1


def test_in_progress_entry_returns_409(client, auth, db, stt, parser):
    entry_id = uuid.uuid4()
    db.add(VoiceEntry(id=entry_id, recorded_at=datetime.now(timezone.utc), status="received"))
    db.flush()
    _, resp = post_voice(client, auth, entry_id=str(entry_id))
    assert resp.status_code == 409
    assert resp.json()["detail"] == "Đang xử lý, thử lại sau"
    assert stt.calls == []


def test_stale_in_progress_entry_is_reprocessed(client, auth, db, stt, parser):
    entry_id = uuid.uuid4()
    db.add(
        VoiceEntry(
            id=entry_id,
            recorded_at=datetime.now(timezone.utc),
            received_at=datetime.now(timezone.utc) - timedelta(minutes=10),
            status="transcribed",
        )
    )
    db.flush()
    _, resp = post_voice(client, auth, entry_id=str(entry_id))
    assert resp.status_code == 200
    assert len(stt.calls) == 1


@pytest.mark.parametrize("error", [None, AIServiceError("boom")])
def test_audio_file_deleted_after_transcribe(client, auth, stt, parser, error):
    stt.error = error
    post_voice(client, auth)
    [(path, existed)] = stt.calls
    assert existed, "audio must exist while transcribing"
    assert not path.exists(), "audio must be deleted right after transcribing"


def test_recorded_at_without_timezone_rejected(client, auth, stt, parser):
    _, resp = post_voice(client, auth, recorded_at="2026-10-10T08:15:00")
    assert resp.status_code == 422
    assert stt.calls == []


def test_invalid_entry_id_rejected(client, auth, stt, parser):
    _, resp = post_voice(client, auth, entry_id="not-a-uuid")
    assert resp.status_code == 422


def test_empty_audio_rejected(client, auth, db, stt, parser):
    _, resp = post_voice(client, auth, audio=b"")
    assert resp.status_code == 422
    assert resp.json()["detail"] == "File ghi âm rỗng"
    assert count(db, VoiceEntry) == 0


def test_oversized_audio_rejected(client, auth, db, stt, parser, monkeypatch):
    from app.config import get_settings

    monkeypatch.setattr(get_settings(), "max_upload_bytes", 10)
    _, resp = post_voice(client, auth, audio=b"x" * 11)
    assert resp.status_code == 422
    assert resp.json()["detail"] == "File ghi âm quá lớn"
    assert count(db, VoiceEntry) == 0


# ---- occurred_on: recording for an earlier day from the app ----


def test_occurred_on_earlier_day_stores_noon_that_day(client, auth, db, stt, parser):
    entry_id, resp = post_voice(client, auth, occurred_on="2026-10-07")

    assert resp.status_code == 200
    noon = datetime(2026, 10, 7, 12, 0, tzinfo=timezone(timedelta(hours=7)))
    assert [datetime.fromisoformat(t["occurred_at"]) for t in resp.json()["transactions"]] == [noon]
    # recorded_at stays the real time of speaking
    entry = db.get(VoiceEntry, uuid.UUID(entry_id))
    assert entry.recorded_at == datetime.fromisoformat(RECORDED_AT)


def test_occurred_on_same_day_keeps_recorded_at(client, auth, stt, parser):
    # 08:15 today; noon today would be in the future.
    _, resp = post_voice(client, auth, occurred_on="2026-10-10")
    assert resp.status_code == 200
    assert datetime.fromisoformat(resp.json()["transactions"][0]["occurred_at"]) == datetime.fromisoformat(RECORDED_AT)


def test_occurred_on_uses_vietnam_day_of_recorded_at(client, auth, stt, parser):
    # 2026-10-10 01:00 in Vietnam is still 2026-10-09 in UTC; the 9th is the day before.
    _, resp = post_voice(client, auth, recorded_at="2026-10-09T18:00:00+00:00", occurred_on="2026-10-09")
    assert resp.status_code == 200
    noon = datetime(2026, 10, 9, 12, 0, tzinfo=timezone(timedelta(hours=7)))
    assert datetime.fromisoformat(resp.json()["transactions"][0]["occurred_at"]) == noon


def test_occurred_on_future_rejected(client, auth, db, stt, parser):
    _, resp = post_voice(client, auth, occurred_on="2026-10-11")
    assert resp.status_code == 422
    assert resp.json()["detail"] == "Không ghi được cho ngày tương lai"
    assert stt.calls == []
    assert count(db, VoiceEntry) == 0


def test_occurred_on_bad_format_rejected(client, auth, stt, parser):
    _, resp = post_voice(client, auth, occurred_on="07/10/2026")
    assert resp.status_code == 422
    assert stt.calls == []


def test_occurred_on_resend_is_idempotent(client, auth, db, stt, parser):
    entry_id, first = post_voice(client, auth, occurred_on="2026-10-07")
    _, second = post_voice(client, auth, entry_id=entry_id, occurred_on="2026-10-07")
    assert second.json() == first.json()
    assert count(db, Transaction) == 1
