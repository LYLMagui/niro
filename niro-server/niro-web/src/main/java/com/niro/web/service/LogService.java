package com.niro.web.service;

import java.util.List;
import java.util.Map;

/**
 * 日志服务接口
 */
public interface LogService {
    /**
     * 根据 TraceID 查询全链路日志
     * @param traceId 追踪ID
     * @return 日志列表
     */
    List<Map<String, Object>> queryLogsByTraceId(String traceId);
}
