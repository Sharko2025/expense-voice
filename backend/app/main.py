import logging
import time

from fastapi import FastAPI, Request

from app.routers import health

log = logging.getLogger("expense_voice")
logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s %(message)s")


def create_app() -> FastAPI:
    app = FastAPI(title="expense-voice", docs_url=None, redoc_url=None, openapi_url=None)

    @app.middleware("http")
    async def log_requests(request: Request, call_next):
        # Only method, path, status and timing. Never bodies, headers or tokens.
        start = time.perf_counter()
        response = await call_next(request)
        ms = int((time.perf_counter() - start) * 1000)
        entry_id = getattr(request.state, "entry_id", None)
        suffix = f" entry_id={entry_id}" if entry_id else ""
        log.info("%s %s %s %dms%s", request.method, request.url.path, response.status_code, ms, suffix)
        return response

    app.include_router(health.router, prefix="/api/v1")
    return app


app = create_app()
