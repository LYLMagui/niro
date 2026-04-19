from __future__ import annotations

import logging
from time import perf_counter
from typing import Any

import cv2
import numpy as np

from ocr_client import OcrClient
from parser import (
    FULL_NAME_CATALOG,
    aggregate_wear_by_digit_vote,
    extract_fields,
    extract_name,
    select_best_exterior_candidate,
    select_best_price_candidate,
    select_best_wear_candidate,
)

logger = logging.getLogger(__name__)

ROI_CONFIG = {
    "name": (0.05, 0.18, 0.31, 0.80),
    "target_card": (0.06, 0.60, 0.30, 0.82),
    "detail_name": (0.40, 0.175, 0.70, 0.22),
    "hero_top_panel": (0.39, 0.10, 0.70, 0.22),
    "hero_bottom_panel": (0.40, 0.70, 0.78, 0.80),
    "quote_panel": (0.02, 0.84, 0.37, 0.97),
    "exterior_primary": (0.395, 0.708, 0.585, 0.802),
    "exterior_fallback": (0.39, 0.702, 0.61, 0.81),
    "wear_primary": (0.57, 0.705, 0.77, 0.775),
    "wear_fallback": (0.56, 0.70, 0.78, 0.78),
}

STITCH_CONFIG = {
    "gap": 24,
    "scale": 2,
    "alpha": 1.4,
}

EXPECTED_IMAGE_WIDTH = 1920
EXPECTED_IMAGE_HEIGHT = 1080

PRICE_PRIMARY_SEARCH = {
    "regions": [
        (0.10, 0.865, 0.225, 0.935),
        (0.098, 0.868, 0.228, 0.938),
    ],
    "scales": [4, 5],
    "preprocesses": ["contrast", "gray", "sharpen"],
}

ROI_FALLBACK_SEARCH = {
    "price": {
        "regions": [
            (0.05, 0.84, 0.22, 0.93),
            (0.04, 0.84, 0.20, 0.93),
        ],
        "scales": [2, 3],
        "preprocesses": ["raw", "contrast"],
    },
}

WEAR_PRIMARY_CONFIGS = [
    {
        "region": ROI_CONFIG["wear_primary"],
        "scale": 4,
        "preprocess": "invert",
    },
    {
        "region": ROI_CONFIG["wear_primary"],
        "scale": 4,
        "preprocess": "contrast",
    },
    {
        "region": ROI_CONFIG["wear_primary"],
        "scale": 5,
        "preprocess": "sharpen",
    },
    {
        "region": ROI_CONFIG["wear_primary"],
        "scale": 4,
        "preprocess": "adaptive",
    },
    {
        "region": ROI_CONFIG["wear_primary"],
        "scale": 5,
        "preprocess": "clahe",
    },
]

WEAR_FALLBACK_CONFIG = {
    "region": ROI_CONFIG["wear_fallback"],
    "scale": 5,
    "preprocess": "binary_inv",
}

EXTERIOR_PRIMARY_CONFIG = {
    "region": ROI_CONFIG["exterior_primary"],
    "scale": 4,
    "preprocess": "contrast",
}

EXTERIOR_FALLBACK_CONFIG = {
    "region": ROI_CONFIG["exterior_fallback"],
    "scale": 5,
    "preprocess": "binary",
}


class InvalidImageError(ValueError):
    pass


class LazyContext:
    """兜底识别资源的按需加载容器。

    stitched 大图拼接 + target_card 细节裁切都是慢路径,主流程命中 primary
    时应避免触发。各 resolver 通过这个容器访问,第一次调用才真正跑 OCR。
    """

    def __init__(self, client: OcrClient, image: np.ndarray) -> None:
        self.client = client
        self.image = image
        self._stitched_result: dict[str, Any] | None = None
        self._stitched_fields: dict[str, Any] | None = None
        self._target_card_result: dict[str, Any] | None = None
        self._target_card_fields: dict[str, Any] | None = None

    def stitched_result(self) -> dict[str, Any]:
        if self._stitched_result is None:
            self._stitched_result = _run_stitched_panels(self.client, self.image)
            self._stitched_fields = extract_fields(self._stitched_result["texts"])
        return self._stitched_result

    def stitched_fields(self) -> dict[str, Any]:
        if self._stitched_fields is None:
            self.stitched_result()
        return self._stitched_fields  # type: ignore[return-value]

    def target_card_result(self) -> dict[str, Any]:
        if self._target_card_result is None:
            self._target_card_result = self.client.recognize_region_from_image(
                self.image,
                ROI_CONFIG["target_card"],
                scale=3,
                preprocess="raw",
            )
            self._target_card_fields = extract_fields(self._target_card_result["texts"])
        return self._target_card_result

    def target_card_fields(self) -> dict[str, Any]:
        if self._target_card_fields is None:
            self.target_card_result()
        return self._target_card_fields  # type: ignore[return-value]

    def stitched_loaded(self) -> bool:
        return self._stitched_result is not None


def _run_stitched_panels(client: OcrClient, image: np.ndarray) -> dict[str, Any]:
    hero_top_panel = _crop_and_prepare_panel(image, ROI_CONFIG["hero_top_panel"])
    hero_bottom_panel = _crop_and_prepare_panel(image, ROI_CONFIG["hero_bottom_panel"])
    quote_panel = _crop_and_prepare_panel(image, ROI_CONFIG["quote_panel"])

    target_width = max(hero_top_panel.shape[1], hero_bottom_panel.shape[1], quote_panel.shape[1])
    hero_top_panel = _pad_to_width(hero_top_panel, target_width)
    hero_bottom_panel = _pad_to_width(hero_bottom_panel, target_width)
    quote_panel = _pad_to_width(quote_panel, target_width)
    gap = np.full((STITCH_CONFIG["gap"], target_width, 3), 255, dtype=np.uint8)
    stitched = np.vstack([hero_top_panel, gap, hero_bottom_panel, gap, quote_panel])
    return client.recognize_image(stitched)


def _crop_and_prepare_panel(image: np.ndarray, region: tuple[float, float, float, float]) -> np.ndarray:
    height, width = image.shape[:2]
    left, top, right, bottom = region
    x1 = max(0, min(width, int(left * width)))
    y1 = max(0, min(height, int(top * height)))
    x2 = max(0, min(width, int(right * width)))
    y2 = max(0, min(height, int(bottom * height)))

    cropped = image[y1:y2, x1:x2]
    gray = cv2.cvtColor(cropped, cv2.COLOR_BGR2GRAY)
    enhanced = cv2.convertScaleAbs(gray, alpha=STITCH_CONFIG["alpha"], beta=0)
    prepared = cv2.cvtColor(enhanced, cv2.COLOR_GRAY2BGR)
    return cv2.resize(
        prepared,
        None,
        fx=STITCH_CONFIG["scale"],
        fy=STITCH_CONFIG["scale"],
        interpolation=cv2.INTER_CUBIC,
    )


def _pad_to_width(image: np.ndarray, target_width: int) -> np.ndarray:
    if image.shape[1] >= target_width:
        return image
    padding = np.full((image.shape[0], target_width - image.shape[1], 3), 255, dtype=np.uint8)
    return np.hstack([image, padding])


class OcrRecognitionService:
    def __init__(self, client: OcrClient | None = None) -> None:
        self.client = client or OcrClient()
        self.last_diagnostics: dict[str, Any] = {}

    def decode_image(self, payload: bytes) -> np.ndarray:
        if not payload:
            raise InvalidImageError("empty image payload")

        buffer = np.frombuffer(payload, dtype=np.uint8)
        image = cv2.imdecode(buffer, cv2.IMREAD_COLOR)
        if image is None:
            raise InvalidImageError("unable to decode image")
        return image

    def recognize_bytes(self, payload: bytes) -> dict[str, Any]:
        self.last_diagnostics = {}
        image = self.decode_image(payload)
        return self.recognize_image(image)

    def recognize_image(self, image: np.ndarray) -> dict[str, Any]:
        started_at = perf_counter()
        full_fields: dict[str, Any] | None = None
        self._validate_image_size(image)

        lazy = LazyContext(self.client, image)

        name, name_meta = self._resolve_name(image, lazy)
        price = self._resolve_price(image, lazy)
        wear, wear_meta = self._resolve_wear(image, lazy)
        exterior, exterior_meta = self._resolve_exterior(image, wear)

        if full_fields is None and (price is None or wear is None):
            full_fields = self._extract_full_fields(image)

        result = {
            "name": name,
            "price": price if price is not None else full_fields["price"] if full_fields else None,
            "wear": wear if wear is not None else full_fields["wear"] if full_fields else None,
            "exterior": exterior,
        }

        stitched_price = lazy.stitched_fields().get("price") if lazy.stitched_loaded() else None
        elapsed_ms = round((perf_counter() - started_at) * 1000, 2)
        self.last_diagnostics = {
            "elapsed_ms": elapsed_ms,
            "name_source": name_meta["source"],
            "name_quality": name_meta["quality"],
            "wear_source": wear_meta["source"],
            "wear_decimals": wear_meta["decimals"],
            "wear_fallback_used": wear_meta["fallback_used"],
            "wear_value_text": wear_meta["value_text"],
            "wear_score": wear_meta["score"],
            "wear_preprocess": wear_meta["preprocess"],
            "wear_scale": wear_meta["scale"],
            "wear_region": wear_meta["region"],
            "wear_voted": wear_meta.get("voted", False),
            "exterior_source": exterior_meta["source"],
            "exterior_quality": exterior_meta["quality"],
            "price_used_stitched": stitched_price is not None and stitched_price == price and price is not None,
            "stitched_loaded": lazy.stitched_loaded(),
        }
        logger.info("ocr fields elapsed_ms=%.2f diagnostics=%s result=%s", elapsed_ms, self.last_diagnostics, result)
        return result

    def _resolve_name(self, image: np.ndarray, lazy: LazyContext) -> tuple[str | None, dict[str, Any]]:
        hero_top_result = self.client.recognize_region_from_image(
            image,
            ROI_CONFIG["hero_top_panel"],
            scale=2,
            preprocess="raw",
        )
        hero_top_name = extract_name(hero_top_result["texts"])
        if self._is_complete_name(hero_top_name):
            return hero_top_name, {"source": "hero_top_panel", "quality": "full_name"}

        target_card_result = lazy.target_card_result()
        target_card_name = extract_name(target_card_result["texts"])
        if self._is_complete_name(target_card_name):
            return target_card_name, {"source": "target_card", "quality": "full_name"}

        detail_name_result = self.client.recognize_region_from_image(
            image,
            ROI_CONFIG["detail_name"],
            scale=4,
            preprocess="raw",
        )
        detail_name_value = extract_name(detail_name_result["texts"])
        if self._is_complete_name(detail_name_value):
            return detail_name_value, {"source": "detail_name", "quality": "full_name"}

        name_result = self.client.recognize_region_from_image(
            image,
            ROI_CONFIG["name"],
            scale=3,
            preprocess="raw",
        )
        name_value = extract_name(name_result["texts"])
        if self._is_complete_name(name_value):
            return name_value, {"source": "name", "quality": "full_name"}

        fallback_name = hero_top_name or target_card_name or detail_name_value or name_value
        quality = "weapon_only" if fallback_name else "missing"
        source = (
            "hero_top_panel"
            if hero_top_name
            else "target_card"
            if target_card_name
            else "detail_name"
            if detail_name_value
            else "name"
            if name_value
            else "none"
        )
        return fallback_name, {"source": source, "quality": quality}

    def _resolve_price(self, image: np.ndarray, lazy: LazyContext) -> float | None:
        primary_price = self._select_best_candidate(
            self._iterate_search_candidates(
                image,
                PRICE_PRIMARY_SEARCH["regions"],
                PRICE_PRIMARY_SEARCH["scales"],
                PRICE_PRIMARY_SEARCH["preprocesses"],
                source_type="primary",
            ),
            select_best_price_candidate,
        )
        if primary_price is not None:
            return primary_price

        target_card_fields = lazy.target_card_fields()
        if target_card_fields["price"] is not None:
            return target_card_fields["price"]

        price_candidate = self._select_best_candidate(
            [lazy.target_card_result()],
            select_best_price_candidate,
        )
        if price_candidate is not None:
            return price_candidate

        stitched_fields = lazy.stitched_fields()
        if stitched_fields["price"] is not None:
            return stitched_fields["price"]

        return self._select_best_candidate(
            self._iterate_search_candidates(
                image,
                ROI_FALLBACK_SEARCH["price"]["regions"],
                ROI_FALLBACK_SEARCH["price"]["scales"],
                ROI_FALLBACK_SEARCH["price"]["preprocesses"],
                source_type="fallback",
            ),
            select_best_price_candidate,
        )

    def _resolve_wear(self, image: np.ndarray, lazy: LazyContext) -> tuple[float | None, dict[str, Any]]:
        primary_results: list[dict[str, Any]] = []
        for index, config in enumerate(WEAR_PRIMARY_CONFIGS):
            primary_result = self.client.recognize_region_from_image(
                image,
                config["region"],
                scale=config["scale"],
                preprocess=config["preprocess"],
            )
            primary_result["source_type"] = "primary"
            primary_results.append(primary_result)

            if self._can_early_stop_wear(primary_results, is_last=index == len(WEAR_PRIMARY_CONFIGS) - 1):
                candidate = select_best_wear_candidate(primary_results)
                candidate = self._apply_digit_vote(candidate, primary_results)
                if self._is_high_quality_wear(candidate):
                    return candidate["value"], self._build_wear_meta(candidate, fallback_used=False)

        primary_candidate = select_best_wear_candidate(primary_results)
        primary_candidate = self._apply_digit_vote(primary_candidate, primary_results)
        if self._is_high_quality_wear(primary_candidate):
            return primary_candidate["value"], self._build_wear_meta(primary_candidate, fallback_used=False)

        fallback_result = self.client.recognize_region_from_image(
            image,
            WEAR_FALLBACK_CONFIG["region"],
            scale=WEAR_FALLBACK_CONFIG["scale"],
            preprocess=WEAR_FALLBACK_CONFIG["preprocess"],
        )
        fallback_result["source_type"] = "fallback"
        fallback_candidate = select_best_wear_candidate([fallback_result])

        if self._should_keep_primary_wear(primary_candidate, fallback_candidate):
            return primary_candidate["value"], self._build_wear_meta(primary_candidate, fallback_used=False)

        if fallback_candidate is not None:
            return fallback_candidate["value"], self._build_wear_meta(fallback_candidate, fallback_used=True)

        stitched_fields = lazy.stitched_fields()
        if stitched_fields["wear"] is not None:
            stitched_text = str(stitched_fields["wear"])
            return stitched_fields["wear"], {
                "source": "stitched_parsed",
                "decimals": self._count_wear_decimals(stitched_text),
                "fallback_used": True,
                "value_text": stitched_text,
                "score": None,
                "preprocess": None,
                "scale": None,
                "region": None,
            }

        return None, {
            "source": "missing",
            "decimals": 0,
            "fallback_used": True,
            "value_text": None,
            "score": None,
            "preprocess": None,
            "scale": None,
            "region": None,
        }

    def _recognize_stitched_panels(self, image: np.ndarray) -> dict[str, Any]:
        return _run_stitched_panels(self.client, image)

    def _resolve_exterior(self, image: np.ndarray, wear: float | None) -> tuple[int | None, dict[str, Any]]:
        inferred_exterior = self._infer_exterior_from_wear(wear)
        if inferred_exterior is not None:
            return inferred_exterior, {"source": "wear_inferred", "quality": "full"}

        primary_result = self.client.recognize_region_from_image(
            image,
            EXTERIOR_PRIMARY_CONFIG["region"],
            scale=EXTERIOR_PRIMARY_CONFIG["scale"],
            preprocess=EXTERIOR_PRIMARY_CONFIG["preprocess"],
        )
        primary_result["source_type"] = "primary"
        primary_candidate = select_best_exterior_candidate([primary_result])
        if self._is_high_quality_exterior(primary_candidate):
            return primary_candidate["value"], {"source": "primary", "quality": "full"}

        fallback_result = self.client.recognize_region_from_image(
            image,
            EXTERIOR_FALLBACK_CONFIG["region"],
            scale=EXTERIOR_FALLBACK_CONFIG["scale"],
            preprocess=EXTERIOR_FALLBACK_CONFIG["preprocess"],
        )
        fallback_result["source_type"] = "fallback"

        stitched_result = self.client.recognize_region_from_image(
            image,
            ROI_CONFIG["hero_bottom_panel"],
            scale=2,
            preprocess="contrast",
        )
        stitched_result["source_type"] = "stitched"

        candidates = [primary_result, fallback_result, stitched_result]
        best_candidate = select_best_exterior_candidate(candidates)
        if best_candidate is None:
            return None, {"source": "missing", "quality": "missing"}

        quality = "full" if self._is_high_quality_exterior(best_candidate) else "partial"
        return best_candidate["value"], {
            "source": best_candidate.get("source_type", "fallback"),
            "quality": quality,
        }

    def _iterate_search_candidates(
        self,
        image: np.ndarray,
        regions: list[tuple[float, float, float, float]],
        scales: list[int],
        preprocesses: list[str],
        source_type: str = "fallback",
    ):
        for region in regions:
            for scale in scales:
                for preprocess in preprocesses:
                    result = self.client.recognize_region_from_image(
                        image,
                        region,
                        scale,
                        preprocess,
                    )
                    result["source_type"] = source_type
                    yield result

    def _extract_full_fields(self, image: np.ndarray) -> dict[str, Any]:
        full_result = self.client.recognize_image(image)
        return extract_fields(full_result["texts"])

    def _is_complete_name(self, value: str | None) -> bool:
        return bool(value and value in FULL_NAME_CATALOG)

    def _count_wear_decimals(self, value_text: str | None) -> int:
        if not value_text or "." not in value_text:
            return 0
        return len(value_text.split(".", 1)[1])

    def _validate_image_size(self, image: np.ndarray) -> None:
        height, width = image.shape[:2]
        if width != EXPECTED_IMAGE_WIDTH or height != EXPECTED_IMAGE_HEIGHT:
            raise InvalidImageError(f"image must be {EXPECTED_IMAGE_WIDTH}x{EXPECTED_IMAGE_HEIGHT}, got {width}x{height}")

    def _build_wear_meta(self, candidate: dict[str, Any], fallback_used: bool) -> dict[str, Any]:
        return {
            "source": candidate.get("source_type", "primary"),
            "decimals": self._count_wear_decimals(candidate.get("value_text")),
            "fallback_used": fallback_used,
            "value_text": candidate.get("value_text"),
            "score": candidate.get("score"),
            "preprocess": candidate.get("preprocess"),
            "scale": candidate.get("scale"),
            "region": candidate.get("region"),
            "voted": candidate.get("voted", False),
        }

    def _apply_digit_vote(
        self,
        candidate: dict[str, Any] | None,
        ocr_results: list[dict[str, Any]],
    ) -> dict[str, Any] | None:
        if candidate is None:
            return None
        voted_text = aggregate_wear_by_digit_vote(ocr_results)
        if voted_text is None or voted_text == candidate.get("value_text"):
            return candidate
        try:
            voted_value = float(voted_text)
        except ValueError:
            return candidate
        if not 0 < voted_value < 1:
            return candidate
        voted_candidate = dict(candidate)
        voted_candidate["value"] = voted_value
        voted_candidate["value_text"] = voted_text
        voted_candidate["voted"] = True
        return voted_candidate

    def _is_high_quality_wear(self, candidate: dict[str, Any] | None) -> bool:
        if candidate is None:
            return False
        decimals = self._count_wear_decimals(candidate.get("value_text"))
        return candidate.get("source_type") == "primary" and decimals >= 9

    def _can_early_stop_wear(self, results: list[dict[str, Any]], is_last: bool) -> bool:
        """尝试在跑完所有候选前提前返回。

        触发路径(任何一条满足即可):
        - 2 个及以上候选投票一致 + primary + 9 位小数 (稳态,可以相信)
        - 最新一轮识别出 primary + 9 位小数 + OCR raw score >= 0.85
          (高置信单轮,不等投票。门槛压到 0.85 是为了 mobile 模型——
           server 模型本来就容易超过,mobile 在干净图上也能达到)
        最后一轮不走早停,让常规流程走完(会触发投票修正)。
        """
        if is_last:
            return False
        if not results:
            return False

        candidate = select_best_wear_candidate(results)
        if (
            candidate is not None
            and candidate.get("source_type") == "primary"
            and self._count_wear_decimals(candidate.get("value_text")) >= 9
            and candidate.get("votes", 0) >= 2
        ):
            return True

        latest = results[-1]
        latest_candidate = select_best_wear_candidate([latest])
        if (
            latest_candidate is None
            or latest_candidate.get("source_type") != "primary"
            or self._count_wear_decimals(latest_candidate.get("value_text")) < 9
        ):
            return False

        raw_scores = [float(s) for s in latest.get("scores", []) if s is not None]
        if raw_scores and min(raw_scores) >= 0.85:
            return True

        return False

    def _should_keep_primary_wear(
        self,
        primary_candidate: dict[str, Any] | None,
        fallback_candidate: dict[str, Any] | None,
    ) -> bool:
        if primary_candidate is None:
            return False
        if fallback_candidate is None:
            return True

        primary_decimals = self._count_wear_decimals(primary_candidate.get("value_text"))
        fallback_decimals = self._count_wear_decimals(fallback_candidate.get("value_text"))
        primary_score = float(primary_candidate.get("score") or 0)
        fallback_score = float(fallback_candidate.get("score") or 0)

        if primary_decimals >= 9:
            return True
        if primary_decimals >= fallback_decimals + 2:
            return True
        if primary_decimals >= 8 and fallback_decimals <= 6:
            return True
        return primary_score >= fallback_score + 35

    def _is_high_quality_exterior(self, candidate: dict[str, Any] | None) -> bool:
        if candidate is None:
            return False
        source_text = candidate.get("source_text", "")
        return candidate.get("source_type") == "primary" and len(source_text) >= 3

    def _infer_exterior_from_wear(self, wear: float | None) -> int | None:
        if wear is None:
            return None
        if wear < 0.07:
            return 0
        if wear < 0.15:
            return 1
        if wear < 0.38:
            return 2
        if wear < 0.45:
            return 3
        return 4

    def _select_best_candidate(self, results, selector) -> float | None:
        collected: list[dict[str, Any]] = []
        for result in results:
            collected.append(result)
            best = selector(collected)
            if best is not None and best.get("votes", 0) >= 1:
                return best["value"]
        return None


ocr_service = OcrRecognitionService()
