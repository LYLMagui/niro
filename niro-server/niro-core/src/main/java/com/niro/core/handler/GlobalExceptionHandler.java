package com.niro.core.handler;

import static org.springframework.core.Ordered.HIGHEST_PRECEDENCE;

import java.util.stream.Collectors;

import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.core.annotation.Order;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import com.niro.core.exception.BusinessException;
import com.niro.core.result.Result;
import com.niro.core.result.StatusCode;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;

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

    @ExceptionHandler(NotLoginException.class)
    public Result<Void> handlerNotLoginException(NotLoginException ex, HttpServletRequest request, HttpServletResponse response) {
        log.warn("未登录或登录过期 | URI: {} | Msg: {}", request.getRequestURI(), ex.getMessage());
        // 设置 HTTP 状态码为 401，确保前端 Axios 拦截器能捕获到 Error
        response.setStatus(401);
        return Result.failure(StatusCode.UNAUTHORIZED_CODE, "未登录或登录过期，请重新登录");
    }

    @ExceptionHandler(NotPermissionException.class)
    public Result<Void> handlerNotPermissionException(NotPermissionException ex, HttpServletRequest request, HttpServletResponse response) {
        log.warn("权限校验失败 | URI: {} | Permission: {} | Msg: {}", request.getRequestURI(), ex.getPermission(), ex.getMessage());
        response.setStatus(403);
        return Result.failure(StatusCode.FORBIDDEN_CODE, ex.getMessage());
    }

    @ExceptionHandler(BusinessException.class)
    public Result<Void> handlerBusinessException(BusinessException ex, HttpServletRequest request) {
        log.warn("❌ 业务异常 | URI: {} | Code: {} | Msg: {}", request.getRequestURI(), ex.getCode(), ex.getMessage());
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

    @ExceptionHandler(NoResourceFoundException.class)
    public Result<Void> handlerNoResourceFoundException(NoResourceFoundException ex, HttpServletRequest request) {
        log.debug("接口未找到 | URI: {}", request.getRequestURI());
        return Result.failure(StatusCode.NOT_FOUND_CODE, "404");
    }

    @ExceptionHandler(Exception.class)
    public Result<Void> handlerException(Exception ex, HttpServletRequest request) {
        log.error("系统未知异常 | URI: {}", request.getRequestURI(), ex);
        return Result.failure("系统繁忙，请稍后重试");
    }


}
