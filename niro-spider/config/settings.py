import os
import time
from dotenv import load_dotenv

# 强制设置时区为上海
os.environ['TZ'] = 'Asia/Shanghai'
if hasattr(time, 'tzset'):
    time.tzset()

# 路径配置
# 当前文件位置: niro-spider/config/settings.py
SPIDER_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
PROJECT_ROOT = os.path.dirname(SPIDER_DIR)

# 环境变量路径：优先加载当前目录、Web 目录、根目录
ENV_PATH_SPIDER = os.path.join(SPIDER_DIR, ".env")
ENV_PATH_WEB = os.path.join(PROJECT_ROOT, "niro-server", "niro-web", ".env")
ENV_PATH_SERVER = os.path.join(PROJECT_ROOT, "niro-server", ".env")
ENV_PATH_ROOT = os.path.join(PROJECT_ROOT, ".env")

# 按优先级尝试加载
loaded = False
for path in [ENV_PATH_SPIDER, ENV_PATH_SERVER, ENV_PATH_WEB, ENV_PATH_ROOT]:
    if os.path.exists(path):
        load_dotenv(path, override=True)
        print(f"✅ 已加载环境变量: {path}") # 调试输出
        loaded = True

# 如果没有加载到文件，且关键环境变量也不存在，才发出警告
if not loaded and not os.getenv("DB_PASSWORD"):
    # 只有在非 Docker 环境下或者确实缺失关键配置时才警告
    if not os.path.exists("/.dockerenv"):
        print(f"⚠️ 未找到 .env 文件且未检测到关键环境变量，将使用系统环境变量或默认配置")

# 日志目录: niro-spider/logs
LOG_DIR = os.path.join(SPIDER_DIR, "logs")

# 爬虫配置文件

# Buff Cookies (建议使用环境变量或外部文件加载，避免硬编码)
BUFF_COOKIE = os.getenv("BUFF_COOKIE", "Device-Id=50omZWVdzKwfAiqZlPmm; hb_MA-B480-7AA0C2ACD2CD_source=buff.163.com; P_INFO=17369636359|1767632934|1|netease_buff|00&99|null&null&null#fuj&350100#10#0|&0|null|17369636359; remember_me=U1090370748|zPBaqwhQvQrPnS7XSknyAWgCoFArOkoQ; session=1-1WQb7Zdwllnf5oKU29tpHwpnaLq0vzhWLMYq69jj3FgB2016617444; Locale-Supported=zh-Hans; game=csgo; csrf_token=IjYyOWFiYWNiOWViM2E4ZDBhMTg1YWQ4OWE0MzkzNTRhNTgxYWQzZDki.aV6ZwQ.Cw8eSOo3XTnzKF0DmaSYbHaYV5Y")

# 爬取间隔 (秒)
CRAWL_INTERVAL_MIN = float(os.getenv("CRAWL_INTERVAL_MIN", 8))
CRAWL_INTERVAL_MAX = float(os.getenv("CRAWL_INTERVAL_MAX", 12))

# Elasticsearch 配置
ES_HOST = os.getenv("ES_HOST", "localhost")
ES_PORT = int(os.getenv("ES_PORT", 9200))
ES_INDEX = os.getenv("ES_INDEX", "buff_goods")

# Redis 配置
REDIS_HOST = os.getenv("REDIS_HOST", "localhost")
REDIS_PORT = int(os.getenv("REDIS_PORT", 6379))
REDIS_PASSWORD = os.getenv("REDIS_PASSWORD", None)
REDIS_DB = int(os.getenv("REDIS_DB", 0))


# PostgreSQL 配置
DB_HOST = os.getenv("DB_HOST", "localhost")
DB_PORT = int(os.getenv("DB_PORT", 5432))
DB_NAME = os.getenv("DB_NAME", "niro")
DB_SCHEMA = os.getenv("DB_SCHEMA", "public")
DB_USERNAME = os.getenv("DB_USERNAME", "postgres")
DB_PASSWORD = os.getenv("DB_PASSWORD", "password")
DB_MIN_CONN = int(os.getenv("PG_MIN_CONN", 1))
DB_MAX_CONN = int(os.getenv("PG_MAX_CONN", 10))

# 企业微信通知配置
WECOM_CORPID = os.getenv("WECOM_CORPID", "")     # 企业ID
WECOM_CORPSECRET = os.getenv("WECOM_CORPSECRET", "") # 自建应用Secret
WECOM_AGENTID = os.getenv("WECOM_AGENTID", "")   # 自建应用AgentID
WECOM_TOUSER = os.getenv("WECOM_TOUSER", "@all") # 接收消息的用户ID，默认为全部

# 代理配置 (支持 v2rayA 等提供的 HTTP/SOCKS5 代理)
# 示例: http://127.0.0.1:20171
PROXY_URL = os.getenv("PROXY_URL", "").strip("`'\" ")
if not PROXY_URL: PROXY_URL = None
ENABLE_PROXY = os.getenv("ENABLE_PROXY", "false").lower() == "true"

