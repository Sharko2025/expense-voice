from pathlib import Path
from types import SimpleNamespace

from app.services.stt import STT_PROMPT, OpenAITranscriber


class FakeAudio:
    def __init__(self):
        self.kwargs = None
        self.transcriptions = self

    def create(self, **kwargs):
        self.kwargs = kwargs
        return SimpleNamespace(text=" coffee 27k \n")


def make(tmp_path, language):
    audio = FakeAudio()
    client = SimpleNamespace(audio=audio)
    path = Path(tmp_path) / "a.m4a"
    path.write_bytes(b"x")
    return OpenAITranscriber(client, "stt-model", language=language), audio, path


def test_auto_language_omits_param(tmp_path):
    t, audio, path = make(tmp_path, language="")
    assert t.transcribe(path) == "coffee 27k"
    assert "language" not in audio.kwargs
    assert audio.kwargs["prompt"] == STT_PROMPT
    assert audio.kwargs["model"] == "stt-model"


def test_fixed_language_is_sent(tmp_path):
    t, audio, path = make(tmp_path, language="vi")
    t.transcribe(path)
    assert audio.kwargs["language"] == "vi"


def test_prompt_covers_both_languages():
    for word in ("nghìn", "cà thẻ", "chuyển khoản", "thousand", "card", "bank transfer"):
        assert word in STT_PROMPT
