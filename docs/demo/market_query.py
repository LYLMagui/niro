import sys
from pathlib import Path

CURRENT_DIR = Path(__file__).resolve().parent
SCRIPT_PATH = CURRENT_DIR / "梦魇挂单.py"
namespace = {"__name__": "market_query_loader"}
exec(SCRIPT_PATH.read_text(encoding="utf-8"), namespace)

if __name__ == "__main__":
    try:
        sys.stdout.reconfigure(encoding="utf-8")
        sys.stdin.reconfigure(encoding="utf-8")
    except Exception:
        pass
    sys.exit(namespace["main"]())
