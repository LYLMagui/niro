import json
import logging
import os
from pathlib import Path
from threading import RLock
from typing import Any

import cv2
import numpy as np

os.environ.setdefault("PADDLE_PDX_DISABLE_MODEL_SOURCE_CHECK", "True")
os.environ.setdefault("PROTOCOL_BUFFERS_PYTHON_IMPLEMENTATION", "python")

from paddleocr import PaddleOCR


_MODEL_VARIANT = os.environ.get("PADDLE_OCR_VARIANT", "mobile").lower()
if _MODEL_VARIANT not in {"mobile", "server"}:
    raise ValueError(
        f"PADDLE_OCR_VARIANT 必须是 mobile 或 server,当前值: {_MODEL_VARIANT!r}"
    )

_DET_MODEL = f"PP-OCRv5_{_MODEL_VARIANT}_det"
_REC_MODEL = f"PP-OCRv5_{_MODEL_VARIANT}_rec"

logger = logging.getLogger(__name__)


class OcrClient:
    def __init__(self) -> None:
        self.ocr = PaddleOCR(
            device="cpu",
            use_doc_orientation_classify=False,
            use_doc_unwarping=False,
            use_textline_orientation=False,
            text_detection_model_name=_DET_MODEL,
            text_recognition_model_name=_REC_MODEL,
            enable_mkldnn=True,
            cpu_threads=4,
        )
        self.use_predict_api = hasattr(self.ocr, "predict")
        self._infer_lock = RLock()
        self._warmup_images = [
            np.full((64, 320, 3), 255, dtype=np.uint8),
            np.full((96, 420, 3), 255, dtype=np.uint8),
            np.zeros((96, 420, 3), dtype=np.uint8),
        ]
        self._warmup()

    def _warmup(self) -> None:
        for image in self._warmup_images:
            try:
                self._infer_once(image)
            except Exception:
                logger.warning("ocr warmup pass failed, retrying", exc_info=True)
                self._infer_once(image)

    def _parse_result(self, results: Any) -> dict[str, Any]:
        if not results:
            return {"texts": [], "scores": []}

        result = results[0] if isinstance(results, list) else results

        if hasattr(result, "json"):
            payload = result.json
            if isinstance(payload, str):
                payload = json.loads(payload)

            res = payload.get("res", payload)
            return {
                "texts": [text for text in res.get("rec_texts", []) if text],
                "scores": list(res.get("rec_scores", [])),
            }

        if isinstance(result, dict):
            res = result.get("res", result)
            return {
                "texts": [text for text in res.get("rec_texts", []) if text],
                "scores": list(res.get("rec_scores", [])),
            }

        legacy_rows = result if isinstance(result, list) else results
        texts: list[str] = []
        scores: list[float] = []
        for row in legacy_rows:
            if not isinstance(row, (list, tuple)) or len(row) < 2:
                continue
            rec = row[1]
            if not isinstance(rec, (list, tuple)) or len(rec) < 2:
                continue
            text, score = rec[0], rec[1]
            if text:
                texts.append(str(text))
                scores.append(float(score))
        return {"texts": texts, "scores": scores}

    def _infer_once(self, image_input: str | np.ndarray) -> Any:
        with self._infer_lock:
            if self.use_predict_api:
                return self.ocr.predict(input=image_input)
            return self.ocr.ocr(image_input, cls=False)

    def _infer(self, image_input: str | np.ndarray) -> Any:
        try:
            return self._infer_once(image_input)
        except RuntimeError as exc:
            if str(exc).strip() != "std::exception":
                raise
            logger.warning("ocr inference first pass hit std::exception, retrying")
            return self._infer_once(image_input)

    def recognize(self, image_path: str | Path) -> dict[str, Any]:
        image_path = Path(image_path)
        return self._parse_result(self._infer(str(image_path)))

    def recognize_image(self, image: np.ndarray) -> dict[str, Any]:
        return self._parse_result(self._infer(image))

    def recognize_region(
        self,
        image_path: str | Path,
        region: tuple[float, float, float, float],
        scale: int = 1,
        preprocess: str = "raw",
        return_cropped: bool = False,
    ) -> dict[str, Any]:
        image_path = Path(image_path)
        image = cv2.imread(str(image_path))
        if image is None:
            raise FileNotFoundError(f"无法读取图片: {image_path}")
        return self.recognize_region_from_image(image, region, scale, preprocess, return_cropped)

    def recognize_region_from_image(
        self,
        image: np.ndarray,
        region: tuple[float, float, float, float],
        scale: int = 1,
        preprocess: str = "raw",
        return_cropped: bool = False,
    ) -> dict[str, Any]:
        height, width = image.shape[:2]
        left, top, right, bottom = region
        x1 = max(0, min(width, int(left * width)))
        y1 = max(0, min(height, int(top * height)))
        x2 = max(0, min(width, int(right * width)))
        y2 = max(0, min(height, int(bottom * height)))

        if x2 <= x1 or y2 <= y1:
            raise ValueError(f"非法区域: {region}")

        cropped = image[y1:y2, x1:x2]
        cropped = self._preprocess(cropped, preprocess)
        if scale > 1:
            cropped = cv2.resize(cropped, None, fx=scale, fy=scale, interpolation=cv2.INTER_CUBIC)

        parsed = self.recognize_image(cropped)
        parsed["region"] = region
        parsed["scale"] = scale
        parsed["preprocess"] = preprocess
        if return_cropped:
            parsed["cropped_shape"] = list(cropped.shape[:2])
        return parsed

    def _preprocess(self, image: Any, preprocess: str) -> Any:
        if preprocess == "raw":
            return image

        gray = cv2.cvtColor(image, cv2.COLOR_BGR2GRAY)
        if preprocess == "gray":
            return cv2.cvtColor(gray, cv2.COLOR_GRAY2BGR)

        if preprocess == "binary":
            _, binary = cv2.threshold(gray, 0, 255, cv2.THRESH_BINARY + cv2.THRESH_OTSU)
            return cv2.cvtColor(binary, cv2.COLOR_GRAY2BGR)

        if preprocess == "binary_inv":
            _, binary = cv2.threshold(gray, 0, 255, cv2.THRESH_BINARY_INV + cv2.THRESH_OTSU)
            return cv2.cvtColor(binary, cv2.COLOR_GRAY2BGR)

        if preprocess == "contrast":
            enhanced = cv2.convertScaleAbs(gray, alpha=2.0, beta=0)
            return cv2.cvtColor(enhanced, cv2.COLOR_GRAY2BGR)

        if preprocess == "sharpen":
            enhanced = cv2.convertScaleAbs(gray, alpha=1.4, beta=0)
            kernel = np.array([[0, -1, 0], [-1, 5, -1], [0, -1, 0]], dtype=np.float32)
            sharpened = cv2.filter2D(enhanced, -1, kernel)
            return cv2.cvtColor(sharpened, cv2.COLOR_GRAY2BGR)

        if preprocess == "adaptive":
            binary = cv2.adaptiveThreshold(
                gray,
                255,
                cv2.ADAPTIVE_THRESH_GAUSSIAN_C,
                cv2.THRESH_BINARY,
                15,
                10,
            )
            return cv2.cvtColor(binary, cv2.COLOR_GRAY2BGR)

        if preprocess == "clahe":
            clahe = cv2.createCLAHE(clipLimit=3.0, tileGridSize=(8, 8))
            enhanced = clahe.apply(gray)
            return cv2.cvtColor(enhanced, cv2.COLOR_GRAY2BGR)

        if preprocess == "invert":
            inverted = 255 - gray
            enhanced = cv2.convertScaleAbs(inverted, alpha=1.5, beta=0)
            return cv2.cvtColor(enhanced, cv2.COLOR_GRAY2BGR)

        raise ValueError(f"不支持的预处理方式: {preprocess}")
