import os
from dotenv import load_dotenv

# 加载 .env 文件
# 假设 .env 文件位于 niro-server/niro-web/.env
# 从当前文件位置 (src/main/python/config) 向上回溯到 niro-web 目录
# 路径层级：src/main/python/config -> src/main/python -> src/main -> src -> niro-spider -> niro-server
BASE_DIR = os.path.dirname(os.path.dirname(os.path.dirname(os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))))))
ENV_PATH = os.path.join(BASE_DIR, "niro-web", ".env")

# 日志目录: niro/niro-spider/logs (BASE_DIR 是 niro-server，所以需要向上回溯一级)
LOG_DIR = os.path.join(BASE_DIR, "..", "niro-spider", "logs")

if os.path.exists(ENV_PATH):
    load_dotenv(ENV_PATH)
    print(f"✅ 已加载环境变量: {ENV_PATH}")
else:
    print(f"⚠️ 未找到环境变量文件: {ENV_PATH}")

# 爬虫配置文件

# Buff Cookies (建议使用环境变量或外部文件加载，避免硬编码)
BUFF_COOKIE = os.getenv("BUFF_COOKIE", "P_INFO=m17350754926@163.com|1752741552|1|mail163|00&99|null&null&null#CN&null#10#0#0|173926&1||17350754926@163.com; NTES_CMT_USER_INFO=1225918163%7C%E6%9C%89%E6%80%81%E5%BA%A6%E7%BD%91%E5%8F%8B194wrj%7Chttp%3A%2F%2Fcms-bucket.nosdn.127.net%2F2018%2F08%2F13%2F078ea9f65d954410b62a52ac773875a1.jpeg%7Cfalse%7CbTE3MzUwNzU0OTI2QDE2My5jb20%3D; Device-Id=6D97HE58m81Y58rpbFp0; Locale-Supported=zh-Hans; game=csgo; qr_code_verify_ticket=d77zfhI0b5316c98793ec292d93d8a49fdb3; remember_me=U1078483952|l5FYrls1WrkhAIijXLxr7WKO7DcEmr2Q; session=1-zww366jyDaiPSeVHbXQUagkt-wXfl3CiUEEhyitunD_e2022098088; csrf_token=ImExNWUyZmIzYjZmN2M3MDZmNjdjM2IxMjY4OWQxOWNhOWY3Y2EyYmUi.aUJtqA.U6k9iqiJgiEElq34UWIcGRG02EE")

# 爬取间隔 (秒)
CRAWL_INTERVAL = int(os.getenv("CRAWL_INTERVAL", 2))

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

