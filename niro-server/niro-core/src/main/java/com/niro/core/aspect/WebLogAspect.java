package com.niro.core.aspect;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.niro.core.constant.LogSanitizeConstant;
import com.niro.core.util.LogSanitizer;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 接口请求日志切面
 *
 * @author liyl
 * @date 2025/12/20
 */
@Aspect
@Component
@Slf4j
@RequiredArgsConstructor
public class WebLogAspect {

    private final ObjectMapper objectMapper;

    /**
     * 定义切点，切入点为controller层的所有方法
     */
    @Pointcut("execution(* com.niro..controller..*.*(..))")
    public void webLog() {
    }

    /**
     * 环绕通知
     */
    @Around("webLog()")
    public Object doAround(ProceedingJoinPoint proceedingJoinPoint) throws Throwable {
        long startTime = System.currentTimeMillis();

        // 获取当前请求对象
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        HttpServletRequest request = attributes.getRequest();

        // 记录请求信息
        String url = request.getRequestURL().toString();
        String method = request.getMethod();
        String ip = getIpAddress(request);
        String className = proceedingJoinPoint.getSignature().getDeclaringTypeName();
        String methodName = proceedingJoinPoint.getSignature().getName();

        // 获取请求参数（过滤掉文件等无法序列化的参数）
        Object[] args = proceedingJoinPoint.getArgs();
        List<Object> logArgs = Arrays.stream(args)
                .filter(arg -> (!(arg instanceof HttpServletRequest) && !(arg instanceof MultipartFile)))
                .collect(Collectors.toList());

        String params = LogSanitizer.stringify(objectMapper, logArgs, LogSanitizeConstant.LOG_BODY_MAX_LENGTH);

        log.info("🌐 收到请求 | URL: {} | Method: {} | IP: {} | Class: {} | Method: {} | Params: {}",
                url, method, ip, className, methodName, params);

        // 执行目标方法，异常时打 error 并 rethrow，避免吞掉栈
        Object result;
        try {
            result = proceedingJoinPoint.proceed();
        } catch (Throwable ex) {
            long costMs = System.currentTimeMillis() - startTime;
            log.error("❌ 请求异常 | URL: {} | Method: {} | IP: {} | Class: {} | Method: {} | CostMs: {} | Msg: {}",
                    url, method, ip, className, methodName, costMs, ex.getMessage());
            throw ex;
        }

        long takeTime = System.currentTimeMillis() - startTime;

        String resultStr = LogSanitizer.stringify(objectMapper, result, LogSanitizeConstant.LOG_BODY_MAX_LENGTH);

        log.info("✅ 请求结束 | Time: {}ms | Result: {}", takeTime, resultStr);

        return result;
    }

    /**
     * 获取IP地址
     */
    private String getIpAddress(HttpServletRequest request) {
        String ip = request.getHeader("x-forwarded-for");
        if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        return ip;
    }
}
