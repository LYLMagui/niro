import sys
from pathlib import Path
from pprint import pprint

from ocr_client import OcrClient
from parser import select_best_exterior_candidate, select_best_price_candidate, select_best_wear_candidate
from service import (
    EXTERIOR_FALLBACK_CONFIG,
    EXTERIOR_PRIMARY_CONFIG,
    ROI_CONFIG,
    ROI_FALLBACK_SEARCH,
    WEAR_FALLBACK_CONFIG,
    WEAR_PRIMARY_CONFIGS,
    ocr_service,
)


def print_texts(title: str, texts: list[str]) -> None:
    print(f"\n{title}:")
    if not texts:
        print("<empty>")
        return
    for index, text in enumerate(texts, start=1):
        print(f"{index:02d}. {text}")


def collect_search_candidates(
    client: OcrClient,
    image,
    regions: list[tuple[float, float, float, float]],
    scales: list[int],
    preprocesses: list[str],
) -> list[dict]:
    results: list[dict] = []
    for region in regions:
        for preprocess in preprocesses:
            for scale in scales:
                results.append(client.recognize_region_from_image(image, region, scale, preprocess))
    return results


def main() -> None:
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8")

    image_path = Path(r"D:\MySpace\niro\ocr_demo\images\c1c0d5aeb25acb802528aa50d93948f2_720.png")
    # 把这里替换成你的目标截图路径即可。

    if not image_path.exists():
        raise FileNotFoundError(f"图片不存在: {image_path}")

    client = OcrClient()

    full_result = client.recognize(image_path)
    image = ocr_service.decode_image(image_path.read_bytes())
    stitched_result = ocr_service._recognize_stitched_panels(image)
    name_result = client.recognize_region_from_image(image, ROI_CONFIG["name"])
    price_candidates = collect_search_candidates(
        client,
        image,
        ROI_FALLBACK_SEARCH["price"]["regions"],
        ROI_FALLBACK_SEARCH["price"]["scales"],
        ROI_FALLBACK_SEARCH["price"]["preprocesses"],
    )
    wear_candidates = [
        client.recognize_region_from_image(
            image,
            config["region"],
            scale=config["scale"],
            preprocess=config["preprocess"],
        )
        for config in WEAR_PRIMARY_CONFIGS
    ]
    wear_candidates.append(
        client.recognize_region_from_image(
            image,
            WEAR_FALLBACK_CONFIG["region"],
            scale=WEAR_FALLBACK_CONFIG["scale"],
            preprocess=WEAR_FALLBACK_CONFIG["preprocess"],
        )
    )

    fields = ocr_service.recognize_image(image)
    best_price = select_best_price_candidate(price_candidates)
    best_wear = select_best_wear_candidate(wear_candidates)
    exterior_candidates = [
        client.recognize_region_from_image(
            image,
            EXTERIOR_PRIMARY_CONFIG["region"],
            scale=EXTERIOR_PRIMARY_CONFIG["scale"],
            preprocess=EXTERIOR_PRIMARY_CONFIG["preprocess"],
        ),
        client.recognize_region_from_image(
            image,
            EXTERIOR_FALLBACK_CONFIG["region"],
            scale=EXTERIOR_FALLBACK_CONFIG["scale"],
            preprocess=EXTERIOR_FALLBACK_CONFIG["preprocess"],
        ),
    ]
    best_exterior = select_best_exterior_candidate(exterior_candidates)

    print(f"image: {image_path}")
    print("\nROI 提取结果:")
    pprint(fields)

    print("\n价格最佳候选:")
    pprint(best_price)
    print("\n磨损最佳候选:")
    pprint(best_wear)

    print_texts("拼接主链路 OCR", stitched_result["texts"])
    print_texts("名称区域 OCR", name_result["texts"])
    if best_price:
        print_texts("价格最佳 OCR", [best_price["source_text"]])
    if best_wear:
        print_texts("磨损最佳 OCR", [best_wear["source_text"]])
    if best_exterior:
        print_texts("外观最佳 OCR", [best_exterior["source_text"]])
    print_texts("整图 OCR", full_result["texts"])


if __name__ == "__main__":
    main()
