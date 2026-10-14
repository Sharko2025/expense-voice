from functools import lru_cache
from pathlib import Path
from typing import Protocol

import openai

from app.config import get_settings
from app.services.errors import AIServiceError

# Vocabulary hint so Vietnamese and English shorthand is transcribed correctly (SPEC: Pipeline AI, step 1).
STT_PROMPT = (
    "k, nghìn, ngàn, ca, cà thẻ, chuyển khoản, tiền mặt, FamilyMart, Circle K, GrabFood, gửi xe, "
    "thousand, million, cash, card, bank transfer, parking, coffee, breakfast"
)


class Transcriber(Protocol):
    def transcribe(self, path: Path) -> str: ...


class OpenAITranscriber:
    def __init__(self, client: openai.OpenAI, model: str, language: str = ""):
        self.client, self.model, self.language = client, model, language

    def transcribe(self, path: Path) -> str:
        # Empty language = auto-detect, so both Vietnamese and English speech work.
        extra = {"language": self.language} if self.language else {}
        try:
            with path.open("rb") as f:
                result = self.client.audio.transcriptions.create(
                    model=self.model, file=f, prompt=STT_PROMPT, **extra
                )
        except openai.OpenAIError as e:
            raise AIServiceError(type(e).__name__) from e
        return result.text.strip()


@lru_cache
def openai_client() -> openai.OpenAI:
    settings = get_settings()
    # No SDK retries: the Android app retries with backoff, and we must stay near the 3 s target.
    return openai.OpenAI(api_key=settings.openai_api_key, timeout=settings.openai_timeout_s, max_retries=0)


def get_transcriber() -> Transcriber:
    settings = get_settings()
    return OpenAITranscriber(openai_client(), settings.openai_stt_model, settings.openai_stt_language)
