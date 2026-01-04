import os
from dotenv import load_dotenv

# 路径配置
# 当前文件位置: niro-spider/config/settings.py
SPIDER_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
PROJECT_ROOT = os.path.dirname(SPIDER_DIR)

# 环境变量路径：优先加载 niro-server/niro-web/.env，因为后端更新 Cookie 会写到这个文件
ENV_PATH_WEB = os.path.join(PROJECT_ROOT, "niro-server", "niro-web", ".env")
ENV_PATH_ROOT = os.path.join(PROJECT_ROOT, ".env")

if os.path.exists(ENV_PATH_WEB):
    load_dotenv(ENV_PATH_WEB, override=True)
    print(f"✅ 已加载 Web 环境变量: {ENV_PATH_WEB}")
elif os.path.exists(ENV_PATH_ROOT):
    load_dotenv(ENV_PATH_ROOT, override=True)
    print(f"✅ 已加载 Root 环境变量: {ENV_PATH_ROOT}")
else:
    print(f"⚠️ 未找到 .env 文件，将使用默认配置")

# 日志目录: niro-spider/logs
LOG_DIR = os.path.join(SPIDER_DIR, "logs")

# 爬虫配置文件

# Buff Cookies (建议使用环境变量或外部文件加载，避免硬编码)
BUFF_COOKIE = os.getenv("BUFF_COOKIE", "Device-Id=50omZWVdzKwfAiqZlPmm; P_INFO=17350754926|1765718256|1|netease_buff|00&99|null&null&null#fuj&350100#10#0|&0|null|17350754926; hb_MA-B480-7AA0C2ACD2CD_source=buff.163.com; Locale-Supported=zh-Hans; game=csgo; qr_code_verify_ticket=59bxPxhc480d0024c45943fd6ce25dbf2390; remember_me=U1078483952|WaxLBM8NMWLAcDLuO9FdLqBCKRRhaql1; session=1-kPyi7cyRqMIG6B9ej3chkP392wl1gBUusYjnhufueI5I2022098088; csrf_token=IjY4MjI3YzRhOGZhZTUxYWUxYmVjMDRiYWMzZTkzYjJiYjJiY2JkMWEi.aU0q2Q.P7MQTVYBLJ0upF3mEwk0fLhc_aQ")

# 爬取间隔 (秒)
CRAWL_INTERVAL = int(os.getenv("CRAWL_INTERVAL", 5))

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

