`NoResourceFoundException` 是 Spring Boot 3.x 引入的新异常，当请求的静态资源不存在时会抛出此异常，而不是像以前那样返回 404 状态码。由于我们在 `WebConfig.java` 中配置了静态资源排除路径，但这些资源在文件系统中确实不存在，Spring Boot 就会抛出这个异常。

虽然这个异常本身是预期的行为（资源确实不存在），但全局异常处理器 `GlobalExceptionHandler` 捕获了它并打印了 ERROR 级别的日志，这会弄脏日志文件并造成误解。

**解决计划：**

1.  **修改 `GlobalExceptionHandler.java`**：
    *   新增一个专门处理 `NoResourceFoundException` 的异常处理方法。
    *   将该异常的日志级别从 ERROR 降低为 DEBUG 或 WARN，或者完全忽略（直接返回 404）。
    *   这样可以避免无关紧要的 404 错误刷屏。

**修改后的代码示例：**

```java
import org.springframework.web.servlet.resource.NoResourceFoundException;

// ...

@ExceptionHandler(NoResourceFoundException.class)
public Result<Void> handlerNoResourceFoundException(NoResourceFoundException ex) {
    log.debug("静态资源未找到: {}", ex.getResourcePath());
    return Result.failure(StatusCode.NOT_FOUND_CODE, "资源不存在");
}
```

这样，当浏览器请求 `/favicon.ico` 或其他不存在的静态资源时，后台只会记录一条调试日志，而不会报 ERROR 异常。