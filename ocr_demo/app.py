from __future__ import annotations

import logging
from time import perf_counter

from flask import Flask, jsonify, request

from service import InvalidImageError, ocr_service

logging.basicConfig(level=logging.INFO)

app = Flask(__name__)
app.logger.setLevel(logging.INFO)


@app.get("/healthz")
def healthz():
    return jsonify({"status": "ok"})


@app.post("/ocr/recognize")
def recognize():
    started_at = perf_counter()
    response_body: dict[str, object]
    status_code = 200

    file = request.files.get("file")
    if file is None:
        response_body = {"code": "INVALID_REQUEST", "message": "file is required"}
        status_code = 400
    else:
        payload = file.read()
        if not payload:
            response_body = {"code": "INVALID_REQUEST", "message": "file is required"}
            status_code = 400
        else:
            try:
                response_body = ocr_service.recognize_bytes(payload)
            except InvalidImageError as exc:
                response_body = {"code": "INVALID_IMAGE", "message": str(exc)}
                status_code = 422
            except Exception:
                elapsed_ms = (perf_counter() - started_at) * 1000
                app.logger.exception("ocr recognize failed elapsed_ms=%.2f", elapsed_ms)
                response_body = {"code": "INTERNAL_ERROR", "message": "ocr service failed"}
                status_code = 500

    elapsed_ms = (perf_counter() - started_at) * 1000
    app.logger.info(
        "ocr recognize status=%s elapsed_ms=%.2f diagnostics=%s result=%s",
        status_code,
        elapsed_ms,
        getattr(ocr_service, "last_diagnostics", {}),
        response_body,
    )
    return jsonify(response_body), status_code


if __name__ == "__main__":
    app.run(host="0.0.0.0", port=5000)
