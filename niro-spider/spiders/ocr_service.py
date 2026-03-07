import asyncio
import json
import os
import re
import signal
import sys
from pathlib import Path
from typing import Any

import httpx
from loguru import logger
from redis.asyncio import Redis

# 关闭部分 Paddle 特性以提升兼容性（与测试脚本保持一致）
os.environ["FLAGS_enable_pir_api"] = "0"
os.environ["FLAGS_enable_pir_in_executor"] = "0"
os.environ["FLAGS_use_mkldnn"] = "0"

# 修复直接运行时的项目导入路径
current_dir = os.path.dirname(os.path.abspath(__file__))
project_root = os.path.dirname(current_dir)
if project_root not in sys.path:
    sys.path.insert(0, project_root)

from config import settings
from utils.logger import setup_logging

try:
    from paddleocr import PaddleOCR
except ImportError:  # pragma: no cover
    PaddleOCR = None  # type: ignore[assignment]


PRICE_RE = re.compile(r"[￥¥$]\s*[\d.,]+", re.IGNORECASE)
FLOAT_VALUE_RE = re.compile(r"\b0?\.\d{5,10}\b")
FLOAT_FIELD_RE = re.compile(
    r"(?:磨损度|磨损|float|wear)[\s:：]*([01](?:\.\d+)?)",
    re.IGNORECASE,
)
NAME_PATTERN_RE = re.compile(r"[|\★\(\)]")
WEAR_TEXT_CLEAN_RE = re.compile(r"[\s:：,，。!！?？、/\\|_\-]+")

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


def get_box_center(box: Any) -> tuple[float, float]:
    if not isinstance(box, (list, tuple)) or len(box) < 4:
        return (0.5, 0.5)
    try:
        xs = [float(p[0]) for p in box]
        ys = [float(p[1]) for p in box]
        return ((min(xs) + max(xs)) / 2, (min(ys) + max(ys)) / 2)
    except (TypeError, ValueError, IndexError):
        return (0.5, 0.5)


def get_image_size(lines: list[dict[str, Any]]) -> tuple[float, float]:
    max_x, max_y = 0.0, 0.0
    for line in lines:
        box = line.get("box")
        if isinstance(box, (list, tuple)) and len(box) >= 4:
            try:
                xs = [float(p[0]) for p in box]
                ys = [float(p[1]) for p in box]
                max_x = max(max_x, max(xs))
                max_y = max(max_y, max(ys))
            except (TypeError, ValueError, IndexError):
                continue
    if max_x == 0:
        max_x = 1000
    if max_y == 0:
        max_y = 1000
    return (max_x, max_y)


def classify_by_position(lines: list[dict[str, Any]]) -> dict[str, list[dict[str, Any]]]:
    img_w, img_h = get_image_size(lines)
    top_region: list[dict[str, Any]] = []
    left_bottom: list[dict[str, Any]] = []
    center_bottom: list[dict[str, Any]] = []
    others: list[dict[str, Any]] = []

    for line in lines:
        center_x, center_y = get_box_center(line.get("box"))
        nx, ny = center_x / img_w, center_y / img_h
        line_with_pos = {**line, "nx": nx, "ny": ny}
        if ny < 0.35:
            top_region.append(line_with_pos)
        elif nx < 0.4 and ny > 0.75:
            left_bottom.append(line_with_pos)
        elif 0.3 < nx < 0.9 and 0.5 < ny < 0.85:
            center_bottom.append(line_with_pos)
        else:
            others.append(line_with_pos)

    return {
        "top": top_region,
        "left_bottom": left_bottom,
        "center_bottom": center_bottom,
        "others": others,
    }


def extract_price_from_region(lines: list[dict[str, Any]]) -> str | None:
    for line in lines:
        match = PRICE_RE.search(line["text"])
        if match:
            return match.group(0)
    return None


def extract_wear_from_region(lines: list[dict[str, Any]]) -> str | None:
    wear_level: str | None = None
    for line in lines:
        text = line["text"]
        normalized_text = WEAR_TEXT_CLEAN_RE.sub("", text)
        for keyword in WEAR_KEYWORDS:
            if keyword in text or keyword in normalized_text:
                wear_level = keyword
                break
        field_match = FLOAT_FIELD_RE.search(text)
        if field_match:
            return field_match.group(1)
        float_match = FLOAT_VALUE_RE.search(text)
        if float_match:
            return float_match.group(0)
    return wear_level


def extract_wear_level_from_region(lines: list[dict[str, Any]]) -> str | None:
    for line in lines:
        text = line["text"]
        normalized_text = WEAR_TEXT_CLEAN_RE.sub("", text)
        for keyword in WEAR_KEYWORDS:
            if keyword in text or keyword in normalized_text:
                return keyword
    return None


def pick_name_with_position(lines: list[dict[str, Any]]) -> str | None:
    candidates: list[str] = []
    for line in lines:
        text = line["text"]
        if NAME_PATTERN_RE.search(text):
            candidates.append(text)
    if candidates:
        return max(candidates, key=len)
    for line in lines:
        text = line["text"]
        if len(text) >= 4 and not any(q in text for q in QUALITY_KEYWORDS):
            return text
    return None


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


def extract_fields(lines: list[dict[str, Any]]) -> dict[str, Any]:
    texts = [line["text"] for line in lines]
    regions = classify_by_position(lines)

    price = extract_price_from_region(regions["left_bottom"])
    if price is None:
        price = extract_price_from_region(regions["others"] + regions["center_bottom"])

    wear_raw = extract_wear_from_region(regions["center_bottom"])
    if wear_raw is None:
        wear_raw = extract_wear_from_region(regions["others"])

    wear_float: str | None = None
    wear_level: str | None = None
    if wear_raw is not None:
        if wear_raw in WEAR_KEYWORDS:
            wear_level = wear_raw
        else:
            wear_float = wear_raw

    if wear_level is None:
        wear_level = extract_wear_level_from_region(regions["center_bottom"])
    if wear_level is None:
        wear_level = extract_wear_level_from_region(regions["others"])
    if wear_level is None:
        wear_level = extract_wear_level_from_region(lines)

    name = pick_name_with_position(regions["top"])
    if name is None:
        name = pick_name_with_position(regions["others"])

    qualities: list[str] = []
    for line in regions["top"]:
        text = line["text"]
        if any(q in text for q in QUALITY_KEYWORDS):
            qualities.append(text)

    extras: list[str] = []
    for text in texts:
        if any(k.lower() in text.lower() for k in EXTRA_KEYWORDS):
            extras.append(text)

    return {
        "name": name,
        "price": price,
        "wear_float": wear_float,
        "wear_level": wear_level,
        "quality": dedupe(qualities),
        "extra_info": dedupe(extras),
    }


class OCRService:
    def __init__(self):
        self.redis: Redis | None = None
        self.http_client: httpx.AsyncClient | None = None
        self.running = True
        self._tasks: set[asyncio.Task[Any]] = set()
        self._ocr: Any = None
        self._ocr_lock = asyncio.Lock()
        max_concurrency = max(settings.OCR_MAX_CONCURRENCY, 1)
        self._semaphore = asyncio.Semaphore(max_concurrency)

    async def init_resources(self):
        self.redis = Redis(
            host=settings.REDIS_HOST,
            port=settings.REDIS_PORT,
            password=settings.REDIS_PASSWORD,
            db=settings.REDIS_DB,
            decode_responses=True,
        )
        timeout = httpx.Timeout(settings.OCR_CALLBACK_TIMEOUT)
        limits = httpx.Limits(
            max_connections=settings.OCR_HTTP_MAX_CONNECTIONS,
            max_keepalive_connections=settings.OCR_HTTP_KEEPALIVE_CONNECTIONS,
        )
        self.http_client = httpx.AsyncClient(timeout=timeout, limits=limits, follow_redirects=True)
        logger.info(f"OCR 服务已连接 Redis: {settings.REDIS_HOST}:{settings.REDIS_PORT}")

    async def _ensure_ocr(self):
        if self._ocr is not None:
            return
        async with self._ocr_lock:
            if self._ocr is not None:
                return
            if PaddleOCR is None:
                raise RuntimeError(
                    "未检测到 paddleocr，请安装: python -m pip install paddlepaddle paddleocr"
                )
            self._ocr = await asyncio.to_thread(
                PaddleOCR,
                use_textline_orientation=True,
                lang=settings.OCR_LANG,
            )
            logger.info("PaddleOCR 初始化完成")

    async def close(self):
        self.running = False
        if self._tasks:
            logger.info(f"OCR 服务退出中，等待 {len(self._tasks)} 个任务完成")
            await asyncio.gather(*self._tasks, return_exceptions=True)
        if self.http_client is not None:
            await self.http_client.aclose()
        if self.redis is not None:
            await self.redis.close()
        logger.info("OCR 服务已停止")

    def _resolve_image_path(self, image_path: str) -> Path:
        clean_path = image_path.strip()
        if not clean_path:
            raise ValueError("imagePath 不能为空")

        images_root = Path(settings.IMAGES_DIR).resolve()
        target = (images_root / clean_path).resolve()
        try:
            target.relative_to(images_root)
        except ValueError as exc:
            raise ValueError(f"imagePath 超出 IMAGES_DIR 范围: {image_path}") from exc
        if not target.exists():
            raise FileNotFoundError(f"图片不存在: {target}")
        return target

    async def _recognize_image(self, image_file: Path) -> dict[str, Any]:
        await self._ensure_ocr()
        raw_result = await asyncio.to_thread(self._ocr.ocr, str(image_file))
        lines = normalize_ocr_lines(to_jsonable(raw_result))
        return extract_fields(lines)

    def _build_error_payload(self, task_id: Any, error: str) -> dict[str, Any]:
        return {
            "taskId": task_id,
            "success": False,
            "data": None,
            "error": error,
        }

    def _build_success_payload(self, task_id: Any, data: dict[str, Any]) -> dict[str, Any]:
        return {
            "taskId": task_id,
            "success": True,
            "data": {
                "name": data.get("name"),
                "price": data.get("price"),
                "wear_float": data.get("wear_float"),
                "wear_level": data.get("wear_level"),
                "quality": data.get("quality") or [],
                "extra_info": data.get("extra_info") or [],
            },
            "error": None,
        }

    async def _send_callback(self, callback_url: str | None, payload: dict[str, Any]):
        if not callback_url:
            logger.error(f"任务 {payload.get('taskId')} 缺少 callbackUrl，无法回调")
            return
        if self.http_client is None:
            logger.error(f"任务 {payload.get('taskId')} 回调失败：HTTP 客户端未初始化")
            return
        try:
            resp = await self.http_client.post(callback_url, json=payload)
            if resp.status_code >= 400:
                logger.error(
                    f"任务 {payload.get('taskId')} 回调失败: HTTP {resp.status_code} | {callback_url}"
                )
            else:
                logger.info(
                    f"任务 {payload.get('taskId')} 回调成功: HTTP {resp.status_code} | {callback_url}"
                )
        except Exception as exc:
            logger.error(f"任务 {payload.get('taskId')} 回调异常: {exc}")

    async def process_task(self, task_data: dict[str, Any]):
        task_id = task_data.get("taskId")
        callback_url = task_data.get("callbackUrl")
        image_path = task_data.get("imagePath")

        try:
            if task_id is None:
                raise ValueError("taskId 不能为空")
            if not image_path:
                raise ValueError("imagePath 不能为空")

            async with self._semaphore:
                resolved_image = self._resolve_image_path(str(image_path))
                ocr_data = await self._recognize_image(resolved_image)

            payload = self._build_success_payload(task_id=task_id, data=ocr_data)
        except Exception as exc:
            logger.exception(f"OCR 任务执行失败 taskId={task_id}: {exc}")
            payload = self._build_error_payload(task_id=task_id, error=str(exc))

        await self._send_callback(callback_url=callback_url, payload=payload)

    async def start(self):
        await self.init_resources()
        queue_names = [settings.OCR_QUEUE_NAME]
        logger.info(f"OCR 服务开始监听队列: {queue_names}")

        while self.running:
            try:
                if self.redis is None:
                    await asyncio.sleep(1)
                    continue

                result = await self.redis.blpop(queue_names, timeout=settings.OCR_QUEUE_BLPOP_TIMEOUT)
                if not result:
                    continue

                queue_name, data_json = result
                logger.debug(f"[OCR Queue Popped] Queue: {queue_name} | RawData: {data_json[:200]}...")

                task_data: Any = json.loads(data_json)
                if isinstance(task_data, str):
                    try:
                        task_data = json.loads(task_data)
                    except json.JSONDecodeError:
                        pass
                if not isinstance(task_data, dict):
                    logger.error(f"OCR 任务格式错误: {task_data}")
                    continue

                task = asyncio.create_task(self.process_task(task_data))
                self._tasks.add(task)
                task.add_done_callback(self._tasks.discard)
            except json.JSONDecodeError as exc:
                logger.error(f"OCR 队列消息 JSON 解析失败: {exc}")
            except Exception as exc:
                logger.error(f"OCR 队列监听异常: {exc}")
                await asyncio.sleep(1)


def install_signal_handlers(service: OCRService):
    def _stop(sig_name: str):
        logger.warning(f"收到信号 {sig_name}，OCR 服务准备停止")
        service.running = False

    loop = asyncio.get_running_loop()
    for sig_name in ("SIGINT", "SIGTERM"):
        sig = getattr(signal, sig_name, None)
        if sig is None:
            continue
        try:
            loop.add_signal_handler(sig, lambda s=sig_name: _stop(s))
        except NotImplementedError:
            signal.signal(sig, lambda *_args, s=sig_name: _stop(s))


async def run_ocr_service():
    service = OCRService()
    install_signal_handlers(service)
    try:
        await service.start()
    finally:
        await service.close()


if __name__ == "__main__":
    setup_logging()
    asyncio.run(run_ocr_service())
