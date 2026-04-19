import re
from difflib import SequenceMatcher
from typing import Any

WEAPON_KEYWORDS = [
    "AK-47",
    "AUG",
    "AWP",
    "CZ75",
    "Desert Eagle",
    "Dual Berettas",
    "双持贝瑞塔",
    "FAMAS",
    "Five-SeveN",
    "G3SG1",
    "Galil AR",
    "Glock-18",
    "格洛克18",
    "格洛克18型",
    "M249",
    "M4A1-S",
    "M4A1消音版",
    "M4A4",
    "MAC-10",
    "MAG-7",
    "MP5-SD",
    "MP7",
    "MP9",
    "Negev",
    "Nova",
    "新星",
    "P90",
    "P2000",
    "P250",
    "R8 Revolver",
    "Sawed-Off",
    "SCAR-20",
    "SG 553",
    "SSG 08",
    "Tec-9",
    "UMP-45",
    "USP-S",
    "XM1014",
    "全息武器箱",
    "创世终端机",
]

STAT_TRAK_PATTERNS = [
    "（StatTrak™）",
    "(StatTrak™)",
    "StatTrak™",
    "StatTrak",
    "Stat Trak",
]

STAT_TRAK_REGEX = re.compile(r"[（(]?\s*s\s*t\s*a\s*t\s*t\s*r\s*a\s*k\s*™?\s*[)）]?", re.IGNORECASE)

EXTERIOR_PATTERNS = [
    "崭新出厂",
    "略有磨损",
    "久经沙场",
    "破损不堪",
    "战痕累累",
]

FULL_NAME_CATALOG = [
    "SCAR-20 | 牢笼",
    "AUG | 后发制人",
    "P2000 | 红翼",
    "MP9 | 打口碟",
    "P250 | 牛蛙",
    "MAG-7 | 震级",
    "MP5-SD | 专注",
    "新星 | 目镜",
    "M4A1消音版 | 液化",
    "双持贝瑞塔 | 天矢之眼",
    "MAC-10 | 纸老虎",
    "UMP-45 | 连续体",
    "AWP | 可燃冰",
    "MP7 | 吸烟有害健康",
    "格洛克18型 | 镜面马赛克",
    "M4A4 | 破浪狂飙",
    "AK-47 | 流金王朝",
    "格洛克18 | 镜面马赛克",
    "USP-S | 蓝图",
    "Tec-9 | 兄弟",
    "SG 553 | 集成",
    "XM1014 | 锈蚀烈焰",
    "FAMAS | ZX81彩色",
    "M4A1-S | 液化",
    "P90 | 腐蚀",
    "CZ75 | 复刻",
    "Galil AR | 控制",
]

WEAPON_TO_SKINS = {
    weapon: [skin]
    for weapon, skin in (item.split(" | ", 1) for item in FULL_NAME_CATALOG)
}

SKIN_KEYWORDS = [skin for skins in WEAPON_TO_SKINS.values() for skin in skins]

NAME_NOISE_PATTERNS = [
    "报价",
    "拒绝",
    "Alice",
    "Alica",
    "Aiica",
    "当前报价",
    "收藏品",
    "卖家",
    "在售价",
    "印花",
    "磨损模板",
]

WEAPON_TYPE_PATTERNS = [
    "手枪",
    "步枪",
    "冲锋枪",
    "微型冲锋枪",
    "微冲",
    "霰弹枪",
    "狙击步枪",
    "机枪",
]


FULLWIDTH_TRANSLATION = str.maketrans(
    "０１２３４５６７８９．：－￥＄｜",
    "0123456789.:-¥$|",
)


def _normalize_text(text: str) -> str:
    return (
        text.translate(FULLWIDTH_TRANSLATION)
        .replace("丨", "|")
        .replace("“", "")
        .replace("”", "")
        .strip()
    )


def extract_wear(texts: list[str]) -> float | None:
    for text in texts:
        normalized = _normalize_text(text)
        match = re.search(r"\b0\.\d{3,}\b", normalized)
        if match:
            return float(match.group(0))

        short_match = re.search(r"(?<!\d)\.\d{3,}\b", normalized)
        if short_match:
            return float(f"0{short_match.group(0)}")

        digits = re.sub(r"[^0-9]", "", normalized)
        if len(digits) >= 4 and normalized.startswith("0"):
            return float(f"0.{digits[1:]}")
    return None


def extract_price(texts: list[str]) -> float | None:
    for text in texts:
        normalized = _normalize_text(text)
        currency_match = re.search(r"[¥$]\s*(\d{1,3}(?:,\d{3})*(?:\.\d{1,2})?|\d+(?:\.\d{1,2})?)", normalized)
        if currency_match:
            return float(currency_match.group(1).replace(",", ""))

    candidates: list[float] = []
    for text in texts:
        stripped = _normalize_text(text)
        if not stripped:
            continue
        numeric = stripped.replace(",", "")
        if re.fullmatch(r"\d+(?:\.\d{1,2})?", numeric):
            value = float(numeric)
            if 0 < value <= 100000:
                candidates.append(value)

    if candidates:
        return max(candidates)
    return None


def _normalize_name_separator(text: str) -> str:
    left, right = [part.strip() for part in text.split("|", 1)]
    return f"{left} | {right}" if right else left


def _sanitize_weapon_candidate(text: str) -> str:
    sanitized = text
    for pattern in STAT_TRAK_PATTERNS:
        sanitized = sanitized.replace(pattern, "")
    sanitized = STAT_TRAK_REGEX.sub("", sanitized)
    sanitized = re.sub(r"(?<=[A-Za-z])\s+(?=[A-Za-z])", "", sanitized)
    sanitized = re.sub(r"(?<=[A-Za-z])\s+(?=\d)", "", sanitized)
    sanitized = re.sub(r"(?<=\d)\s+(?=[A-Za-z])", "", sanitized)
    sanitized = re.sub(r"(?<=\d)\s+(?=\d)", "", sanitized)
    sanitized = re.sub(r"\s*\-\s*", "-", sanitized)
    sanitized = re.sub(r"[^A-Za-z0-9\- ]", "", sanitized)
    return re.sub(r"\s+", " ", sanitized).strip()


def _similarity(left: str, right: str) -> float:
    if not left or not right:
        return 0.0
    return SequenceMatcher(None, left.lower(), right.lower()).ratio()


def _normalize_weapon_name(text: str) -> str | None:
    normalized = _sanitize_weapon_candidate(_normalize_text(text))
    if not normalized:
        return None

    best_keyword: str | None = None
    best_score = 0.0
    for keyword in WEAPON_KEYWORDS:
        keyword_sanitized = _sanitize_weapon_candidate(keyword)
        if not keyword_sanitized:
            continue
        if keyword_sanitized.lower() in normalized.lower():
            score = len(keyword_sanitized) + 100
        else:
            score = _similarity(normalized, keyword_sanitized)
        if score > best_score:
            best_score = score
            best_keyword = keyword

    if best_keyword is None:
        return None
    if isinstance(best_score, float) and best_score < 0.72:
        return None
    return best_keyword


def _is_exterior_like(text: str) -> bool:
    compact = re.sub(r"\s+", "", _normalize_text(text))
    if not compact:
        return False
    if any(pattern in compact for pattern in EXTERIOR_PATTERNS):
        return True
    if compact.startswith("略有") and ("损" in compact or "磨" in compact):
        return True
    return any(SequenceMatcher(None, compact, pattern).ratio() >= 0.65 for pattern in EXTERIOR_PATTERNS)


def _is_name_noise(text: str) -> bool:
    normalized = _normalize_text(text)
    if not normalized:
        return True
    if any(noise in normalized for noise in NAME_NOISE_PATTERNS):
        return True
    if normalized in WEAPON_TYPE_PATTERNS:
        return True
    if _is_exterior_like(normalized):
        return True
    if re.fullmatch(r"[¥$]?[0-9][0-9.,]*", normalized):
        return True
    return len(re.sub(r"[^A-Za-z0-9\u4e00-\u9fff]", "", normalized)) <= 1


def _normalize_skin_fragment(text: str, weapon_name: str) -> str | None:
    stripped = text
    for pattern in STAT_TRAK_PATTERNS:
        stripped = stripped.replace(pattern, "")
    if weapon_name:
        stripped = re.sub(re.escape(weapon_name), "", stripped, flags=re.IGNORECASE)
    stripped = stripped.strip("|-–—_ ")
    stripped = re.sub(r"^[A-Za-z0-9]{1,2}(?=[\u4e00-\u9fff])", "", stripped)
    if not stripped or _is_name_noise(stripped):
        return None
    if not re.search(r"[\u4e00-\u9fff]", stripped):
        normalized_fragment_weapon = _normalize_weapon_name(stripped)
        if weapon_name and normalized_fragment_weapon == weapon_name:
            return None
    if len(re.sub(r"[^A-Za-z0-9\u4e00-\u9fff]", "", stripped)) < 2:
        return None
    return stripped


def _match_skin_keyword(text: str, weapon_name: str) -> str | None:
    skins = WEAPON_TO_SKINS.get(weapon_name, [])
    if not skins:
        return None

    normalized = _normalize_text(text)
    compact = re.sub(r"[^A-Za-z0-9\u4e00-\u9fff]", "", normalized)
    if not compact:
        return None

    best_skin: str | None = None
    best_score = 0.0
    for skin in skins:
        skin_compact = re.sub(r"[^A-Za-z0-9\u4e00-\u9fff]", "", skin)
        if not skin_compact:
            continue
        if skin_compact in compact:
            score = len(skin_compact) + 100
        else:
            score = SequenceMatcher(None, compact, skin_compact).ratio()
        if score > best_score:
            best_score = score
            best_skin = skin

    if best_skin is None:
        return None
    if isinstance(best_score, float) and best_score < 0.6:
        return None
    return best_skin


def _clean_skin_name(text: str, weapon_name: str) -> str | None:
    normalized = _normalize_text(text)
    if not normalized:
        return None
    if _is_name_noise(normalized):
        return None

    candidates: list[str] = []
    for fragment in normalized.split("|"):
        cleaned = _normalize_skin_fragment(fragment, weapon_name)
        if cleaned:
            candidates.append(cleaned)

    if not candidates:
        cleaned = _normalize_skin_fragment(normalized, weapon_name)
        if cleaned:
            candidates.append(cleaned)

    if not candidates:
        matched_skin = _match_skin_keyword(normalized, weapon_name)
        if matched_skin:
            candidates.append(matched_skin)

    if not candidates:
        return None

    direct_match = next((item for item in candidates if item in WEAPON_TO_SKINS.get(weapon_name, [])), None)
    if direct_match:
        return direct_match

    matched_candidate = next(
        (matched for item in candidates if (matched := _match_skin_keyword(item, weapon_name)) is not None),
        None,
    )
    if matched_candidate:
        return matched_candidate

    return max(candidates, key=lambda item: len(re.sub(r"[^A-Za-z0-9\u4e00-\u9fff]", "", item)))


def extract_name(texts: list[str]) -> str | None:
    normalized_texts = [_normalize_text(text) for text in texts if text.strip()]
    if not normalized_texts:
        return None

    merged_texts: list[str] = []
    token_buffer: list[str] = []
    for text in normalized_texts:
        if len(text) == 1 and re.fullmatch(r"[A-Za-z0-9()™-]", text):
            token_buffer.append(text)
            continue
        if text == "|":
            token_buffer.append(text)
            continue
        if token_buffer and re.fullmatch(r"[A-Za-z0-9()™-]+", text):
            token_buffer.append(text)
            continue
        if _is_name_noise(text):
            if token_buffer:
                merged_texts.append(" ".join(token_buffer))
                token_buffer = []
            continue
        if token_buffer:
            token_buffer.append(text)
            merged_texts.append(" ".join(token_buffer))
            token_buffer = []
            continue
        merged_texts.append(text)
    if token_buffer:
        merged_texts.append(" ".join(token_buffer))

    candidates = merged_texts or normalized_texts

    weapon_name: str | None = None
    weapon_index = -1
    for index, text in enumerate(candidates):
        weapon_name = _normalize_weapon_name(text)
        if weapon_name:
            weapon_index = index
            break

    if not weapon_name:
        return None

    same_line = _clean_skin_name(candidates[weapon_index], weapon_name)
    if same_line:
        if "|" in candidates[weapon_index]:
            return _normalize_name_separator(f"{weapon_name} | {same_line}")
        if same_line != weapon_name:
            return _normalize_name_separator(f"{weapon_name} | {same_line}")

    next_text = candidates[weapon_index + 1] if weapon_index + 1 < len(candidates) else ""
    next_skin = _clean_skin_name(next_text, weapon_name) if next_text else None
    if next_skin:
        return _normalize_name_separator(f"{weapon_name} | {next_skin}")

    return weapon_name


EXTERIOR_LABEL_TO_VALUE = {
    "崭新出厂": 0,
    "略有磨损": 1,
    "久经沙场": 2,
    "破损不堪": 3,
    "战痕累累": 4,
}


EXTERIOR_PARTIAL_HINTS = {
    "崭新出厂": ["崭新", "出厂"],
    "略有磨损": ["略有", "磨损", "略有磨", "有磨损", "略有损", "略磨"],
    "久经沙场": ["久经", "沙场"],
    "破损不堪": ["破损", "不堪"],
    "战痕累累": ["战痕", "累累"],
}


def extract_exterior(texts: list[str]) -> int | None:
    candidate = select_best_exterior_candidate([
        {
            "texts": texts,
            "scores": [],
            "source_type": "direct",
            "preprocess": "raw",
        }
    ])
    if candidate is None:
        return None
    return candidate["value"]


def select_best_exterior_candidate(ocr_results: list[dict[str, Any]]) -> dict[str, Any] | None:
    candidates: list[dict[str, Any]] = []
    for result in ocr_results:
        normalized_texts = [_normalize_text(text).replace(" ", "") for text in result.get("texts", []) if _normalize_text(text).strip()]
        if not normalized_texts:
            continue

        avg_score = _mean_score(result.get("scores", []))
        joined = "".join(normalized_texts)
        source_type = result.get("source_type", "fallback")
        preprocess = result.get("preprocess")
        sources = normalized_texts + [joined]

        for source_text in sources:
            for label, value in EXTERIOR_LABEL_TO_VALUE.items():
                score = 0.0
                if label in source_text:
                    score = 180
                elif any(hint in source_text for hint in EXTERIOR_PARTIAL_HINTS[label]):
                    score = 120
                else:
                    similarity = SequenceMatcher(None, source_text, label).ratio()
                    if similarity >= 0.55:
                        score = 90 + similarity * 40

                if score <= 0:
                    continue

                source_bonus = 25 if source_type == "primary" else 12 if source_type == "stitched" else 0
                preprocess_bonus = 8 if preprocess in {"contrast", "binary", "binary_inv", "sharpen"} else 0
                completeness_bonus = 25 if label in source_text else 10 if len(source_text) >= 3 else 0
                candidates.append(
                    {
                        "kind": "exterior",
                        "value": value,
                        "value_text": label,
                        "source_text": source_text,
                        "region": result.get("region"),
                        "scale": result.get("scale"),
                        "preprocess": preprocess,
                        "source_type": source_type,
                        "score": round(score + source_bonus + preprocess_bonus + completeness_bonus + avg_score * 10, 2),
                    }
                )

    ranked = _aggregate_candidates(candidates)
    if not ranked:
        return None
    ranked[0]["alternatives"] = [
        {
            "value": item["value"],
            "value_text": item["value_text"],
            "votes": item["votes"],
            "score": item["score"],
            "preprocess": item["preprocess"],
            "scale": item["scale"],
            "source_type": item.get("source_type"),
            "source_text": item["source_text"],
        }
        for item in ranked[:5]
    ]
    return ranked[0]


def extract_fields(texts: list[str]) -> dict[str, Any]:
    return {
        "name": extract_name(texts),
        "price": extract_price(texts),
        "wear": extract_wear(texts),
    }


def _mean_score(scores: list[float]) -> float:
    if not scores:
        return 0.0
    return sum(float(score) for score in scores) / len(scores)


def _aggregate_candidates(candidates: list[dict[str, Any]]) -> list[dict[str, Any]]:
    aggregated: dict[str, dict[str, Any]] = {}
    for candidate in candidates:
        key = candidate["value_text"]
        bucket = aggregated.setdefault(
            key,
            {
                "value_text": candidate["value_text"],
                "value": candidate["value"],
                "votes": 0,
                "score": 0.0,
                "best_sample": candidate,
                "samples": [],
            },
        )
        bucket["votes"] += 1
        bucket["score"] += candidate["score"]
        bucket["samples"].append(candidate)
        if candidate["score"] > bucket["best_sample"]["score"]:
            bucket["best_sample"] = candidate

    ranked: list[dict[str, Any]] = []
    for bucket in aggregated.values():
        best = dict(bucket["best_sample"])
        best["votes"] = bucket["votes"]
        best["score"] = round(bucket["score"] + bucket["votes"] * 10, 2)
        best["samples"] = bucket["samples"]
        ranked.append(best)

    ranked.sort(
        key=lambda item: (
            item["score"],
            item["votes"],
            len(item["value_text"]),
        ),
        reverse=True,
    )
    return ranked


def select_best_price_candidate(ocr_results: list[dict[str, Any]]) -> dict[str, Any] | None:
    candidates: list[dict[str, Any]] = []
    for result in ocr_results:
        normalized_texts = [_normalize_text(text) for text in result.get("texts", []) if text.strip()]
        if not normalized_texts:
            continue

        avg_score = _mean_score(result.get("scores", []))
        sources = normalized_texts + [" ".join(normalized_texts), "".join(normalized_texts)]
        seen: set[tuple[str, str]] = set()
        source_type = result.get("source_type", "fallback")
        for source_text in sources:
            patterns = [
                (r"(?:接受报价|报价)[^0-9¥$]{0,6}[¥$]?(\d+\.\d{2})", 140),
                (r"[¥$](\d+\.\d{2})", 120),
                (r"(\d+\.\d{2})", 70),
            ]
            for pattern, base_score in patterns:
                for match in re.finditer(pattern, source_text):
                    value_text = match.group(1)
                    signature = (pattern, value_text)
                    if signature in seen:
                        continue
                    seen.add(signature)

                    value = float(value_text)
                    if not 0 < value <= 100000:
                        continue

                    context_bonus = 0
                    if "接受报价" in source_text:
                        context_bonus += 20
                    elif "报价" in source_text:
                        context_bonus += 10
                    if value < 1000:
                        context_bonus += 10
                    if source_type == "primary":
                        context_bonus += 45
                    elif source_type == "fallback":
                        context_bonus -= 5

                    candidates.append(
                        {
                            "kind": "price",
                            "value": value,
                            "value_text": value_text,
                            "source_text": source_text,
                            "region": result.get("region"),
                            "scale": result.get("scale"),
                            "preprocess": result.get("preprocess"),
                            "source_type": source_type,
                            "score": round(base_score + context_bonus + avg_score * 10, 2),
                        }
                    )

    ranked = _aggregate_candidates(candidates)
    if not ranked:
        return None
    ranked[0]["alternatives"] = [
        {
            "value": item["value"],
            "value_text": item["value_text"],
            "votes": item["votes"],
            "score": item["score"],
            "preprocess": item["preprocess"],
            "scale": item["scale"],
            "source_type": item.get("source_type"),
            "source_text": item["source_text"],
        }
        for item in ranked[:5]
    ]
    return ranked[0]


def select_best_wear_candidate(ocr_results: list[dict[str, Any]]) -> dict[str, Any] | None:
    candidates: list[dict[str, Any]] = []
    for result in ocr_results:
        normalized_texts = [_normalize_text(text) for text in result.get("texts", []) if text.strip()]
        if not normalized_texts:
            continue

        avg_score = _mean_score(result.get("scores", []))
        joined = "".join(normalized_texts)
        sources = normalized_texts + [joined]
        seen: set[tuple[str, str]] = set()
        source_type = result.get("source_type", "fallback")
        for source_text in sources:
            strategies = [
                (r"(0\.\d{6,12})", lambda text: text, 180),
                (r"(\.\d{6,12})", lambda text: f"0{text}", 165),
                (r"(0\d{6,12})", lambda text: f"0.{text[1:]}", 150),
            ]
            for pattern, transform, base_score in strategies:
                for match in re.finditer(pattern, source_text):
                    raw_value_text = match.group(1)
                    value_text = transform(raw_value_text)
                    signature = (pattern, value_text)
                    if signature in seen:
                        continue
                    seen.add(signature)

                    value = float(value_text)
                    if not 0 < value < 1:
                        continue

                    decimals = len(value_text.split(".", 1)[1]) if "." in value_text else 0
                    quality_bonus = 65 if decimals >= 9 else 35 if decimals >= 8 else 5 if decimals >= 7 else -25
                    source_bonus = 35 if source_type == "primary" else -10 if source_type == "fallback" else 0
                    preprocess_bonus = 10 if result.get("preprocess") in {"contrast", "sharpen", "gray", "adaptive", "clahe", "invert"} else 0
                    candidates.append(
                        {
                            "kind": "wear",
                            "value": value,
                            "value_text": value_text,
                            "raw_value_text": raw_value_text,
                            "source_text": source_text,
                            "region": result.get("region"),
                            "scale": result.get("scale"),
                            "preprocess": result.get("preprocess"),
                            "source_type": source_type,
                            "score": round(
                                base_score + quality_bonus + source_bonus + preprocess_bonus + decimals * 2 + avg_score * 10,
                                2,
                            ),
                        }
                    )

    ranked = _aggregate_candidates(candidates)
    if not ranked:
        return None
    ranked[0]["alternatives"] = [
        {
            "value": item["value"],
            "value_text": item["value_text"],
            "raw_value_text": item.get("raw_value_text"),
            "votes": item["votes"],
            "score": item["score"],
            "preprocess": item["preprocess"],
            "scale": item["scale"],
            "source_type": item.get("source_type"),
            "source_text": item["source_text"],
        }
        for item in ranked[:5]
    ]
    return ranked[0]


def aggregate_wear_by_digit_vote(ocr_results: list[dict[str, Any]]) -> str | None:
    """对多个 OCR 结果按位对齐投票。

    每个 0.xxxxx 候选按位拆开,同一位上按字符出现次数加权投票。
    适合解决 9/8 这类系统性混淆:只要多数候选读对,结果就对。

    返回投票后的 wear 文本;候选不足或格式异常时返回 None。
    """
    weighted_texts: list[tuple[str, float]] = []
    for result in ocr_results:
        avg_score = _mean_score(result.get("scores", []))
        weight = max(avg_score, 0.1)
        for text in result.get("texts", []):
            normalized = _normalize_text(text)
            match = re.search(r"0\.\d{6,12}", normalized)
            if match:
                weighted_texts.append((match.group(0), weight))
                continue
            short = re.search(r"(?<!\d)\.\d{6,12}", normalized)
            if short:
                weighted_texts.append((f"0{short.group(0)}", weight))

    if len(weighted_texts) < 3:
        return None

    max_len = max(len(text) for text, _ in weighted_texts)
    threshold = (len(weighted_texts) + 1) // 2

    voted_chars: list[str] = []
    for pos in range(max_len):
        char_weights: dict[str, float] = {}
        supplied = 0
        for text, weight in weighted_texts:
            if pos < len(text):
                ch = text[pos]
                char_weights[ch] = char_weights.get(ch, 0.0) + weight
                supplied += 1
        if supplied < threshold or not char_weights:
            break
        voted_chars.append(max(char_weights.items(), key=lambda item: item[1])[0])

    voted_text = "".join(voted_chars)
    if not re.fullmatch(r"0\.\d{3,}", voted_text):
        return None

    if all(text == weighted_texts[0][0] for text, _ in weighted_texts):
        return None

    return voted_text
