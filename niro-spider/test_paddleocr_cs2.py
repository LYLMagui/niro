import os
os.environ["FLAGS_enable_pir_api"] = "0"
os.environ["FLAGS_enable_pir_in_executor"] = "0"

import argparse
import json
import re
import sys
from pathlib import Path
from typing import Any

import requests

try:
    from paddleocr import PaddleOCR
except ImportError:  # pragma: no cover - 环境依赖问题提示
    PaddleOCR = None  # type: ignore[assignment]


DEFAULT_IMAGE_URL = (
    "https://minimax-algeng-chat-tts.oss-cn-wulanchabu.aliyuncs.com/"
    "ccv2%2F2026-03-06%2FMiniMax-M2.5%2F2004507248075744229%2F"
    "1874876f6a0455c274ca0233f4aeb0bc60b3fae4ab97936672285b1ed940ecc2..png"
    "?Expires=1772877005&OSSAccessKeyId=LTAI5tGLnRTkBjLuYPjNcKQ8&"
    "Signature=8WilUFmlc1cp%2BUE1kCUJWCcoHZ0%3D"
)

PRICE_RE = re.compile(
    r"(?:(?:[￥¥$])\s*\d+(?:[.,]\d{1,2})?|\d+(?:[.,]\d{1,2})?\s*(?:元|CNY|RMB|USD))",
    re.IGNORECASE,
)
FLOAT_VALUE_RE = re.compile(r"\b(?:0(?:\.\d+)?|1(?:\.0+)?)\b")
FLOAT_FIELD_RE = re.compile(
    r"(?:磨损度|磨损|float|wear)[\s:：]*([01](?:\.\d+)?)",
    re.IGNORECASE,
)

WEAR_KEYWORDS = [
    "崭新出厂",
    "略有磨损",
    "久经沙场",
    "破损不堪",
    "战痕累累",
]
QUALITY_KEYWORDS = [
    "隐秘",
    "受限",
    "军规",
    "工业级",
    "消费级",
    "违禁",
    "保密",
    "非凡",
]
EXTRA_KEYWORDS = [
    "玄学",
    "图案模板",
    "模板",
    "种子",
    "seed",
    "pattern",
    "印花",
    "磨损指数",
    "StatTrak",
    "Souvenir",
]


def download_image(url: str, output_path: Path, timeout: int = 30) -> Path:
    output_path.parent.mkdir(parents=True, exist_ok=True)
    resp = requests.get(url, timeout=timeout)
    resp.raise_for_status()
    output_path.write_bytes(resp.content)
    return output_path


def to_jsonable(data: Any) -> Any:
    if isinstance(data, (str, int, float, bool)) or data is None:
        return data
    if isinstance(data, (list, tuple)):
        return [to_jsonable(v) for v in data]
    if isinstance(data, dict):
        return {str(k): to_jsonable(v) for k, v in data.items()}
    if hasattr(data, "json"):
        json_value = getattr(data, "json")
        if callable(json_value):
            try:
                json_value = json_value()
            except TypeError:
                json_value = None
        if isinstance(json_value, (dict, list, tuple)):
            return to_jsonable(json_value)
    if hasattr(data, "res"):
        res_value = getattr(data, "res")
        if isinstance(res_value, (dict, list, tuple)):
            return to_jsonable({"res": res_value})
    if hasattr(data, "tolist"):
        return to_jsonable(data.tolist())
    return str(data)


def normalize_ocr_lines(raw_result: Any) -> list[dict[str, Any]]:
    lines: list[dict[str, Any]] = []
    if isinstance(raw_result, dict):
        raw_result = [raw_result]
    if not isinstance(raw_result, list):
        return lines

    for page in raw_result:
        # 结构1：经典 ocr.ocr 输出 [[box, [text, score]], ...]
        if isinstance(page, list):
            for item in page:
                if not isinstance(item, list) or len(item) < 2:
                    continue
                box = item[0]
                info = item[1]
                if not isinstance(info, (list, tuple)) or not info:
                    continue
                text = str(info[0]).strip()
                if not text:
                    continue
                confidence = float(info[1]) if len(info) > 1 else None
                lines.append(
                    {
                        "text": text,
                        "confidence": confidence,
                        "box": box,
                    }
                )
            continue

        # 结构2：新版本可能返回 dict，文本在 rec_texts / rec_scores
        if isinstance(page, dict):
            payload = page.get("res", page)
            if not isinstance(payload, dict):
                continue
            rec_texts = payload.get("rec_texts", []) or []
            rec_scores = payload.get("rec_scores", []) or []
            dt_polys = payload.get("dt_polys", []) or []
            for idx, text in enumerate(rec_texts):
                text_value = str(text).strip()
                if not text_value:
                    continue
                conf_value = rec_scores[idx] if idx < len(rec_scores) else None
                box_value = dt_polys[idx] if idx < len(dt_polys) else None
                lines.append(
                    {
                        "text": text_value,
                        "confidence": float(conf_value) if conf_value is not None else None,
                        "box": box_value,
                    }
                )
    return lines


def pick_name(lines: list[dict[str, Any]]) -> str | None:
    texts = [line["text"] for line in lines]
    candidates: list[str] = []
    for text in texts:
        low = text.lower()
        if PRICE_RE.search(text):
            continue
        if FLOAT_FIELD_RE.search(low):
            continue
        if any(k in text for k in WEAR_KEYWORDS):
            continue
        if len(text) < 2:
            continue
        if "|" in text or "★" in text or "（" in text or "(" in text:
            candidates.append(text)
    if candidates:
        return max(candidates, key=len)

    for text in texts:
        if len(text) >= 4 and not PRICE_RE.search(text):
            return text
    return None


def extract_fields(lines: list[dict[str, Any]]) -> dict[str, Any]:
    texts = [line["text"] for line in lines]

    prices: list[str] = []
    wears: list[str] = []
    extras: list[str] = []
    qualities: list[str] = []

    for text in texts:
        for p in PRICE_RE.findall(text):
            prices.append(p)

        field_match = FLOAT_FIELD_RE.search(text)
        if field_match:
            wears.append(field_match.group(1))
        elif any(w in text for w in WEAR_KEYWORDS):
            wears.append(text)
        else:
            # 兜底：识别纯 float 形态文本
            float_match = FLOAT_VALUE_RE.search(text)
            if float_match and ("." in float_match.group(0)):
                wears.append(float_match.group(0))

        if any(q in text for q in QUALITY_KEYWORDS):
            qualities.append(text)
        if any(k.lower() in text.lower() for k in EXTRA_KEYWORDS):
            extras.append(text)

    # 去重但保持顺序
    def dedupe(values: list[str]) -> list[str]:
        seen: set[str] = set()
        result: list[str] = []
        for value in values:
            key = value.strip()
            if not key or key in seen:
                continue
            seen.add(key)
            result.append(key)
        return result

    prices = dedupe(prices)
    wears = dedupe(wears)
    qualities = dedupe(qualities)
    extras = dedupe(extras)

    result = {
        "name": pick_name(lines),
        "price": prices[0] if prices else None,
        "wear_float": wears[0] if wears else None,
        "quality": qualities,
        "extra_info": extras,
        "all_text_lines": texts,
    }
    return result


def run(args: argparse.Namespace) -> dict[str, Any]:
    if PaddleOCR is None:
        raise RuntimeError(
            "未检测到 paddleocr。请先在 Python 3.11 环境安装：\n"
            "  python -m pip install paddlepaddle paddleocr\n"
            "当前 Python 版本: "
            f"{sys.version.split()[0]}"
        )

    image_path = Path(args.image_path).resolve()
    json_path = Path(args.output_json).resolve()

    if args.download:
        download_image(args.url, image_path)
        print(f"[INFO] 图片已下载到: {image_path}")
    elif not image_path.exists():
        raise FileNotFoundError(f"图片不存在: {image_path}")

    ocr = PaddleOCR(
        use_textline_orientation=True,
        lang="ch",
    )
    raw_result = ocr.ocr(str(image_path))
    raw_jsonable = to_jsonable(raw_result)

    print("\n===== 完整 OCR 原始结果（调试）=====")
    print(json.dumps(raw_jsonable, ensure_ascii=False, indent=2))

    lines = normalize_ocr_lines(raw_jsonable)
    structured = extract_fields(lines)
    structured["image_path"] = str(image_path)

    json_path.parent.mkdir(parents=True, exist_ok=True)
    json_path.write_text(
        json.dumps(structured, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )

    print("\n===== 结构化提取结果 =====")
    print(json.dumps(structured, ensure_ascii=False, indent=2))
    print(f"\n[INFO] JSON 已保存: {json_path}")
    return structured


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="PaddleOCR 提取 CS2 饰品交易图片信息（CPU 版）"
    )
    parser.add_argument("--url", default=DEFAULT_IMAGE_URL, help="图片 URL")
    parser.add_argument(
        "--image-path",
        default="tests/output/cs2_trade.png",
        help="本地图片路径",
    )
    parser.add_argument(
        "--output-json",
        default="tests/output/cs2_trade_ocr_result.json",
        help="结构化结果 JSON 输出路径",
    )
    parser.add_argument(
        "--download",
        action="store_true",
        default=True,
        help="是否先从 URL 下载图片（默认开启）",
    )
    parser.add_argument(
        "--no-download",
        action="store_false",
        dest="download",
        help="不下载，直接读取 --image-path",
    )
    return parser


if __name__ == "__main__":
    arguments = build_parser().parse_args()
    run(arguments)
