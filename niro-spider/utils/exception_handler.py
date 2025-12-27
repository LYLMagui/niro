import functools
from utils.logger import get_logger

logger = get_logger(__name__)

def handle_api_error(default_return=None):
    """
    爬虫 API 异常处理装饰器
    :param default_return: 发生异常时的默认返回值
    """
    def decorator(func):
        @functools.wraps(func)
        def wrapper(*args, **kwargs):
            try:
                return func(*args, **kwargs)
            except Exception as e:
                logger.error(f"API 调用异常 [{func.__name__}]: {e}")
                # 可以根据需要在这里增加更细致的异常分类处理
                return default_return
        return wrapper
    return decorator
