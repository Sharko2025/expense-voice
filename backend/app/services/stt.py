from functools import lru_cache
from pathlib import Path
from typing import Protocol

import openai

from app.config import get_settings
from app.services.errors import AIServiceError

# Vocabulary hint so Vietnamese shorthand is transcribed correctly (SPEC: Pipeline AI, step 1).
STT_PROMPT = "k, nghìn, ca, cà thẻ, chuyển khoản, tiền mặt, FamilyMart, Circle K, GrabFood, gửi xe"


class Transcriber(Protocol):
    def transcribe(self, path: Path) -> str: ...


class OpenAITranscriber:
    def __init__(self, client: openai.OpenAI, model: str):
        self.client, self.model = client, model

    def transcribe(self, path: Path) -> str:
        try:
            with path.open("rb") as f:
                result = self.client.audio.transcriptions.create(
                    model=self.model, file=f, language="vi", prompt=STT_PROMPT
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
    return OpenAITranscriber(openai_client(), get_settings().openai_stt_model)
