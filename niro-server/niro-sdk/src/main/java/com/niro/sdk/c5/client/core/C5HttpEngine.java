package com.niro.sdk.c5.client.core;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.net.url.UrlBuilder;
import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.niro.sdk.c5.config.C5Config;
import com.niro.sdk.c5.exception.C5ApiException;
import com.niro.sdk.c5.response.C5BaseResponse;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;

/**
 * C5 HTTP 执行引擎
 * <p>
 * 基于 JDK 21 HttpClient 实现，支持虚拟线程和连接池复用。
 */
@Slf4j
public class C5HttpEngine {

    private final C5Config config;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public C5HttpEngine(C5Config config) {
        this.config = config;
        this.objectMapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        // 使用 JDK 21 虚拟线程执行器构建 HttpClient
        this.httpClient = HttpClient.newBuilder()
                .executor(Executors.newVirtualThreadPerTaskExecutor())
                .connectTimeout(Duration.ofSeconds(10))
                .version(HttpClient.Version.HTTP_2) // 尝试使用 HTTP/2，会自动降级
                .build();
    }

    /**
     * 执行 HTTP 请求
     *
     * @param endpoint      API 端点
     * @param method        请求方法 (GET/POST)
     * @param params        请求参数 (POJO 或 Map)
     * @param typeReference 响应类型引用 (Jackson)
     * @param <T>           响应数据类型
     * @return 响应数据
     */
    public <T> T execute(String endpoint, String method, Object params,
            TypeReference<C5BaseResponse<T>> typeReference) {
        String baseUrl = config.getBaseUrl();
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        if (!endpoint.startsWith("/")) {
            endpoint = "/" + endpoint;
        }
        String fullUrl = baseUrl + endpoint;

        // 准备参数
        Map<String, Object> queryParams = new HashMap<>();
        Map<String, Object> bodyParams = new HashMap<>();

        if (StrUtil.isBlank(config.getAppKey())) {
            throw new C5ApiException("App Key is not configured");
        }
        queryParams.put("app-key", config.getAppKey());

        if (params != null) {
            Map<String, Object> paramMap;
            if (params instanceof Map) {
                paramMap = (Map<String, Object>) params;
            } else {
                paramMap = BeanUtil.beanToMap(params, false, true);
            }

            if ("GET".equalsIgnoreCase(method)) {
                queryParams.putAll(paramMap);
            } else {
                // POST 请求参数放入 Body，但在 C5 中 app-key 仍在 query
                bodyParams.putAll(paramMap);
            }
        }

        // 构建 URL
        UrlBuilder urlBuilder = UrlBuilder.ofHttp(fullUrl);
        if (CollUtil.isNotEmpty(queryParams)) {
            queryParams.forEach((k, v) -> {
                if (v != null) {
                    urlBuilder.addQuery(k, v);
                }
            });
        }

        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(urlBuilder.toURI())
                .timeout(Duration.ofSeconds(30))
                .header("Accept", "application/json")
                .header("Content-Type", "application/json");

        // 构建 Body
        if ("POST".equalsIgnoreCase(method)) {
            if (CollUtil.isNotEmpty(bodyParams)) {
                try {
                    String jsonBody = objectMapper.writeValueAsString(bodyParams);
                    requestBuilder.POST(HttpRequest.BodyPublishers.ofString(jsonBody));
                } catch (JsonProcessingException e) {
                    throw new C5ApiException("Failed to serialize request body", e);
                }
            } else {
                requestBuilder.POST(HttpRequest.BodyPublishers.noBody());
            }
        } else {
            requestBuilder.GET();
        }

        try {
            log.info("C5 API Request: {} {}", method, urlBuilder.toString());
            if (log.isDebugEnabled()) {
                log.debug("Params: {}", objectMapper.writeValueAsString(params));
            }

            HttpResponse<String> response = httpClient.send(requestBuilder.build(),
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new C5ApiException(response.statusCode(), "HTTP Error: " + response.statusCode());
            }

            String body = response.body();
            log.debug("C5 API Response: {}", body);

            C5BaseResponse<T> resp = objectMapper.readValue(body, typeReference);

            if (resp == null) {
                throw new C5ApiException("Response parsing failed");
            }

            // 即使 success 为 false，只要 data 节点有值，我们也返回 data (用于处理批量操作中的部分失败)
            // 如果 data 为空且 success 为 false，则抛出异常
            if (!resp.isSuccess() && resp.getData() == null) {
                throw new C5ApiException(resp.getErrorCode(), resp.getErrorMsg(), resp.getErrorData());
            }

            return resp.getData();

        } catch (IOException | InterruptedException e) {
            log.error("C5 API Request Failed", e);
            throw new C5ApiException("Request failed: " + e.getMessage(), e);
        }
    }
}
