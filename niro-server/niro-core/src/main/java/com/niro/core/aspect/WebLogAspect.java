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

import cn.hutool.core.util.StrUtil;
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
        
        String params = "";
        try {
            params = objectMapper.writeValueAsString(logArgs);
        } catch (Throwable e) {
            // 捕获 Throwable 防止某些情况下序列化导致 StackOverflowError
            params = "无法序列化参数";
        }

        log.info("🌐 收到请求 | URL: {} | Method: {} | IP: {} | Class: {} | Method: {} | Params: {}", 
                url, method, ip, className, methodName, params);

        // 执行目标方法
        Object result = proceedingJoinPoint.proceed();

        // 计算耗时
        long takeTime = System.currentTimeMillis() - startTime;
        
        // 记录响应结果（可选，如果结果太大可以截断或不打印）
        String resultStr = "";
        try {
            resultStr = objectMapper.writeValueAsString(result);
            // 如果返回结果过长，截取前1000个字符
            if (StrUtil.length(resultStr) > 1000) {
                resultStr = StrUtil.sub(resultStr, 0, 1000) + "...";
            }
        } catch (Throwable e) {
            // 捕获 Throwable 防止某些情况下序列化导致 StackOverflowError
            resultStr = "无法序列化结果";
        }

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
