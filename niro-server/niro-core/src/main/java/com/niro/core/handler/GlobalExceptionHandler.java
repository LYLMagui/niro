package com.niro.common.handler;

import com.niro.common.exception.BusinessException;
import com.niro.common.result.Result;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.core.annotation.Order;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

import static org.springframework.core.Ordered.HIGHEST_PRECEDENCE;

/**
 * 全局异常处理器
 *
 * @author liyl
 * @date 2025-12-19
 */
@RestControllerAdvice
@Slf4j
@Order(HIGHEST_PRECEDENCE)
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public Result<Void> handlerBusinessException(BusinessException ex, HttpServletRequest request) {
        log.warn("业务异常 | URI: {} | Code: {} | Msg: {}", request.getRequestURI(), ex.getCode(), ex.getMessage());
        return Result.failure(ex.getCode(), ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handlerMethodArgumentNotValidException(MethodArgumentNotValidException ex, HttpServletRequest request) {
        // 拼接所有参数校验错误信息
        String message = ex.getBindingResult().getAllErrors().stream()
                .map(DefaultMessageSourceResolvable::getDefaultMessage)
                .collect(Collectors.joining("; "));
        log.warn("参数校验失败 | URI: {} | Msg: {}", request.getRequestURI(), message);
        return Result.failure(message);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public Result<Void> handlerIllegalArgumentException(IllegalArgumentException ex, HttpServletRequest request) {
        log.error("非法参数异常 | URI: {}", request.getRequestURI(), ex);
        return Result.failure(ex.getMessage());
    }

    @ExceptionHandler(NullPointerException.class)
    public Result<Void> handlerNullPointerException(NullPointerException ex, HttpServletRequest request) {
        log.error("空指针异常 | URI: {}", request.getRequestURI(), ex);
        return Result.failure("系统内部错误(NPE)");
    }

    @ExceptionHandler(Exception.class)
    public Result<Void> handlerException(Exception ex, HttpServletRequest request) {
        log.error("系统未知异常 | URI: {}", request.getRequestURI(), ex);
        return Result.failure("系统繁忙，请稍后重试");
    }


}
