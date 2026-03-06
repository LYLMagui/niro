import json
import re
from pathlib import Path
from typing import Any, Dict, List, Optional, Tuple

import requests

try:
    from paddleocr import PaddleOCR
except ImportError as exc:
    raise ImportError(
        "未安装 paddleocr，请先执行: pip install paddleocr paddlepaddle"
    ) from exc


IMAGE_URL = (
    "https://minimax-algeng-chat-tts.oss-cn-wulanchabu.aliyuncs.com/"
    "ccv2%2F2026-03-06%2FMiniMax-M2.5%2F2004507248075744229%2F"
    "1874876f6a0455c274ca0233f4aeb0bc60b3fae4ab97936672285b1ed940ecc2..png"
    "?Expires=1772877005&OSSAccessKeyId=LTAI5tGLnRTkBjLuYPjNcKQ8&"
    "Signature=8WilUFmlc1cp%2BUE1kCUJWCcoHZ0%3D"
)


def download_image(url: str, output_path: Path) -> Path:
    output_path.parent.mkdir(parents=True, exist_ok=True)
    response = requests.get(url, timeout=30)
    response.raise_for_status()
    output_path.write_bytes(response.content)
    return output_path


def run_ocr(image_path: Path) -> List[Any]:
    ocr = PaddleOCR(use_angle_cls=True, lang="ch", use_gpu=False)
    result = ocr.ocr(str(image_path), cls=True)
    return result or []


def flatten_ocr_result(ocr_result: List[Any]) -> List[Dict[str, Any]]:
    lines: List[Dict[str, Any]] = []
    for block in ocr_result:
        if not block:
            continue
        for item in block:
            if not item or len(item) < 2:
                continue
            box = item[0]
            text = str(item[1][0]).strip()
            score = float(item[1][1])
            if not text:
                continue
            y_top = min(point[1] for point in box) if box else 0.0
            lines.append(
                {
                    "text": text,
                    "score": round(score, 4),
                    "box": box,
                    "y_top": y_top,
                }
            )
    return lines


def extract_price(lines: List[Dict[str, Any]]) -> Tuple[Optional[str], Optional[str]]:
    currency_pattern = re.compile(r"(?:￥|¥|\$)\s*([0-9]+(?:[.,][0-9]{1,2})?)")
    number_pattern = re.compile(r"([0-9]+(?:[.,][0-9]{1,2}))")
    keyword_pattern = re.compile(r"(价格|售价|price|卖价|求购|购买)", re.IGNORECASE)

    candidates: List[Tuple[int, str, str]] = []
    for line in lines:
        text = line["text"]
        currency_match = currency_pattern.search(text)
        if currency_match:
            price = currency_match.group(1).replace(",", ".")
            candidates.append((100, price, text))
            continue

        if keyword_pattern.search(text):
            number_match = number_pattern.search(text)
            if number_match:
                price = number_match.group(1).replace(",", ".")
                candidates.append((80, price, text))
                continue

        number_match = number_pattern.search(text)
        if number_match and "." in number_match.group(1):
            price = number_match.group(1).replace(",", ".")
            candidates.append((30, price, text))

    if not candidates:
        return None, None
    candidates.sort(key=lambda x: x[0], reverse=True)
    return candidates[0][1], candidates[0][2]


def extract_wear(lines: List[Dict[str, Any]]) -> Tuple[Optional[str], Optional[str]]:
    labeled_pattern = re.compile(
        r"(?:float|wear|磨损(?:度|值)?)\s*[:：]?\s*([01](?:\.\d+)?)",
        re.IGNORECASE,
    )
    float_pattern = re.compile(r"\b(0\.\d{3,}|1\.0+)\b")

    for line in lines:
        text = line["text"]
        labeled_match = labeled_pattern.search(text)
        if labeled_match:
            return labeled_match.group(1), text

    for line in lines:
        text = line["text"]
        float_match = float_pattern.search(text)
        if float_match:
            return float_match.group(1), text

    return None, None


def extract_name(
    lines: List[Dict[str, Any]],
    matched_price_line: Optional[str],
    matched_wear_line: Optional[str],
) -> Optional[str]:
    skip_keywords = re.compile(
        r"(价格|售价|price|磨损|float|wear|¥|￥|\$|库存|在售|出售|购买|折扣|steam)",
        re.IGNORECASE,
    )
    candidates: List[Tuple[float, str]] = []

    for line in lines:
        text = line["text"]
        if text == matched_price_line or text == matched_wear_line:
            continue
        if skip_keywords.search(text):
            continue
        if re.fullmatch(r"[0-9.]+", text):
            continue

        score = 0.0
        if "|" in text:
            score += 4.0
        if "(" in text or "（" in text:
            score += 2.0
        if re.search(r"[A-Za-z]", text) and re.search(r"[\u4e00-\u9fff]", text):
            score += 1.0
        score += min(len(text) / 8.0, 3.0)
        score += max(0.0, 3.0 - float(line["y_top"]) / 120.0)
        candidates.append((score, text))

    if not candidates:
        return None
    candidates.sort(key=lambda x: x[0], reverse=True)
    return candidates[0][1]


def extract_other_info(
    lines: List[Dict[str, Any]],
    name: Optional[str],
    matched_price_line: Optional[str],
    matched_wear_line: Optional[str],
) -> List[str]:
    quality_keywords = re.compile(
        r"(崭新出厂|略有磨损|久经沙场|破损不堪|战痕累累|隐秘|受限|军规级|工业级|消费级|StatTrak|玄学|模板|图案|编号|seed)",
        re.IGNORECASE,
    )

    other_lines: List[str] = []
    for line in lines:
        text = line["text"]
        if text in {name, matched_price_line, matched_wear_line}:
            continue
        if quality_keywords.search(text):
            other_lines.append(text)

    if not other_lines:
        for line in lines:
            text = line["text"]
            if text in {name, matched_price_line, matched_wear_line}:
                continue
            if len(text) >= 2:
                other_lines.append(text)

    unique_lines: List[str] = []
    seen = set()
    for text in other_lines:
        if text not in seen:
            seen.add(text)
            unique_lines.append(text)
    return unique_lines


def build_structured_result(image_url: str, image_path: Path, ocr_result: List[Any]) -> Dict[str, Any]:
    lines = flatten_ocr_result(ocr_result)
    price, matched_price_line = extract_price(lines)
    wear, matched_wear_line = extract_wear(lines)
    name = extract_name(lines, matched_price_line, matched_wear_line)
    other_info = extract_other_info(lines, name, matched_price_line, matched_wear_line)

    return {
        "image_url": image_url,
        "image_path": str(image_path),
        "extracted": {
            "name": name,
            "price": price,
            "wear_float": wear,
            "other_visible_info": other_info,
        },
        "recognized_lines": [{"text": line["text"], "score": line["score"]} for line in lines],
        "matched_lines": {
            "price_line": matched_price_line,
            "wear_line": matched_wear_line,
        },
    }


def main() -> None:
    root_dir = Path(__file__).resolve().parents[1]
    image_path = root_dir / "tests" / "tmp" / "cs2_trade_sample.png"
    output_json_path = root_dir / "tests" / "tmp" / "cs2_ocr_result.json"

    print("1) 下载图片...")
    download_image(IMAGE_URL, image_path)
    print(f"图片已保存: {image_path}")

    print("2) 执行 OCR（CPU）...")
    ocr_result = run_ocr(image_path)

    print("3) 完整 OCR 原始结果（调试）:")
    print(json.dumps(ocr_result, ensure_ascii=False, indent=2))

    structured_result = build_structured_result(IMAGE_URL, image_path, ocr_result)

    output_json_path.parent.mkdir(parents=True, exist_ok=True)
    output_json_path.write_text(
        json.dumps(structured_result, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )

    print("4) 结构化提取结果:")
    print(json.dumps(structured_result["extracted"], ensure_ascii=False, indent=2))
    print(f"5) JSON 已保存: {output_json_path}")


if __name__ == "__main__":
    main()
