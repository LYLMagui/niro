package com.niro.sdk.c5.client.core;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.Method;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.niro.sdk.c5.config.C5Config;
import com.niro.sdk.c5.exception.C5ApiException;
import com.niro.sdk.c5.exception.C5BusinessException;
import com.niro.sdk.c5.exception.C5HttpException;
import com.niro.sdk.c5.exception.C5NetworkException;
import com.niro.sdk.c5.exception.C5SerializationException;
import com.niro.sdk.c5.response.C5BaseResponse;
import lombok.extern.slf4j.Slf4j;

import static com.niro.sdk.c5.constant.C5HttpConstant.*;
import static com.niro.sdk.common.util.SdkHttpUtil.*;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import com.niro.sdk.c5.enums.C5BusinessStatusEnum;

import com.niro.sdk.c5.enums.C5HttpStatusEnum;

/**
 * C5 HTTP 执行器。
 * <p>
 * 统一负责请求构建、响应解析、异常分层和安全日志。
 */
@Slf4j
public class C5HttpExecutor {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private final C5Config config;
    private final HttpClient httpClient;

    public C5HttpExecutor(C5Config config) {
        this(config, C5HttpClientHolder.getInstance());
    }

    public C5HttpExecutor(C5Config config, HttpClient httpClient) {
        this.config = config;
        this.httpClient = httpClient;
    }

    public <T> T execute(String endpoint, Method method, Object params,
                         TypeReference<C5BaseResponse<T>> typeReference) {
        return doExecute(endpoint, method, params, typeReference, false);
    }

    public <T> CompletableFuture<T> executeAsync(String endpoint, Method method, Object params,
                                                 TypeReference<C5BaseResponse<T>> typeReference) {
        try {
            return doExecuteAsync(endpoint, method, params, typeReference, false);
        } catch (RuntimeException e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    /**
     * 仅用于"批量部分成功"语义的接口（例如 {@code /merchant/trade/v1/batch/buy}）：
     * 调用方需要自行依据 data 内容判断每条记录的成败。其他接口请使用 {@link #execute}，
     * 让业务失败统一抛异常。
     */
    public <T> T executeAllowFailureData(String endpoint, Method method, Object params,
                                         TypeReference<C5BaseResponse<T>> typeReference) {
        return doExecute(endpoint, method, params, typeReference, true);
    }

    /**
     * 仅用于"批量部分成功"语义接口的异步执行入口。
     */
    public <T> CompletableFuture<T> executeAllowFailureDataAsync(String endpoint, Method method, Object params,
                                                                 TypeReference<C5BaseResponse<T>> typeReference) {
        try {
            return doExecuteAsync(endpoint, method, params, typeReference, true);
        } catch (RuntimeException e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    private <T> T doExecute(String endpoint, Method method, Object params,
                            TypeReference<C5BaseResponse<T>> typeReference,
                            boolean allowFailureData) {
        RequestContext context = buildRequestContext(endpoint, method, params);
        long startNs = System.nanoTime();
        log.info("[c5-trace={}] >>> method={}, endpoint={}, {}",
                context.traceId(), method, context.path(), context.paramSummary());

        HttpResponse<String> response = sendRequest(context.request(), context.traceId(), method, context.path(), startNs);
        return handleResponse(response, typeReference, context.traceId(), method, context.path(), startNs, allowFailureData);
    }

    private <T> CompletableFuture<T> doExecuteAsync(String endpoint, Method method, Object params,
                                                    TypeReference<C5BaseResponse<T>> typeReference,
                                                    boolean allowFailureData) {
        RequestContext context = buildRequestContext(endpoint, method, params);
        long startNs = System.nanoTime();
        log.info("[c5-trace={}] >>> method={}, endpoint={}, {}",
                context.traceId(), method, context.path(), context.paramSummary());

        return httpClient.sendAsync(context.request(), HttpResponse.BodyHandlers.ofString())
                .handle((response, throwable) -> {
                    if (throwable != null) {
                        throw toCompletionException(toNetworkException(throwable, context.traceId(), method, context.path(), startNs));
                    }
                    return handleResponse(response, typeReference, context.traceId(), method, context.path(), startNs, allowFailureData);
                });
    }

    private RequestContext buildRequestContext(String endpoint, Method method, Object params) {
        if (StrUtil.isBlank(config.getAppKey())) {
            throw new C5ApiException("App Key is not configured");
        }
        if (StrUtil.isBlank(endpoint)) {
            throw new C5ApiException("Endpoint is required");
        }
        if (StrUtil.isBlank(config.getBaseUrl())) {
            throw new C5ApiException("Base URL is not configured");
        }

        String traceId = newTraceId(TRACE_ID_LENGTH);
        String path = normalizeEndpoint(endpoint);
        String baseUrl = trimTrailingSlash(config.getBaseUrl());
        String fullUrl = baseUrl + path;

        Map<String, Object> queryParams = new LinkedHashMap<>();
        Map<String, Object> bodyParams = new LinkedHashMap<>();
        queryParams.put(QUERY_APP_KEY, config.getAppKey());

        Map<String, Object> incoming = toParamMap(params);
        if (method == Method.GET) {
            incoming.forEach((k, v) -> {
                if (v != null) {
                    queryParams.put(k, v);
                }
            });
        } else {
            bodyParams.putAll(incoming);
        }

        URI uri = buildUri(fullUrl, queryParams);
        HttpRequest request = buildRequest(uri, method, bodyParams, traceId);
        String paramSummary = summarizeParams(queryParams, bodyParams, SENSITIVE_KEYS);
        return new RequestContext(request, traceId, path, paramSummary);
    }

    private HttpResponse<String> sendRequest(HttpRequest request, String traceId,
                                             Method method, String path, long startNs) {
        try {
            return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (HttpTimeoutException e) {
            log.error("[c5-trace={}] !!! request timeout: method={}, endpoint={}, costMs={}",
                    traceId, method, path, elapsedMs(startNs, NANOS_PER_MILLI), e);
            throw new C5NetworkException("Request timeout: " + e.getMessage(), e, true);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("[c5-trace={}] !!! request interrupted: method={}, endpoint={}, costMs={}",
                    traceId, method, path, elapsedMs(startNs, NANOS_PER_MILLI), e);
            throw new C5NetworkException("Request interrupted: " + e.getMessage(), e, false);
        } catch (IOException e) {
            log.error("[c5-trace={}] !!! request io failed: method={}, endpoint={}, costMs={}",
                    traceId, method, path, elapsedMs(startNs, NANOS_PER_MILLI), e);
            throw new C5NetworkException("Request failed: " + e.getMessage(), e, true);
        }
    }

    private C5NetworkException toNetworkException(Throwable throwable, String traceId,
                                                  Method method, String path, long startNs) {
        Throwable cause = unwrapCompletionException(throwable);
        if (cause instanceof HttpTimeoutException) {
            log.error("[c5-trace={}] !!! request timeout: method={}, endpoint={}, costMs={}",
                    traceId, method, path, elapsedMs(startNs, NANOS_PER_MILLI), cause);
            return new C5NetworkException("Request timeout: " + cause.getMessage(), cause, true);
        }
        if (cause instanceof InterruptedException) {
            Thread.currentThread().interrupt();
            log.error("[c5-trace={}] !!! request interrupted: method={}, endpoint={}, costMs={}",
                    traceId, method, path, elapsedMs(startNs, NANOS_PER_MILLI), cause);
            return new C5NetworkException("Request interrupted: " + cause.getMessage(), cause, false);
        }
        log.error("[c5-trace={}] !!! request io failed: method={}, endpoint={}, costMs={}",
                traceId, method, path, elapsedMs(startNs, NANOS_PER_MILLI), cause);
        return new C5NetworkException("Request failed: " + cause.getMessage(), cause, true);
    }

    private Throwable unwrapCompletionException(Throwable throwable) {
        if (throwable instanceof CompletionException && throwable.getCause() != null) {
            return throwable.getCause();
        }
        return throwable;
    }

    private CompletionException toCompletionException(RuntimeException exception) {
        if (exception instanceof CompletionException completionException) {
            return completionException;
        }
        return new CompletionException(exception);
    }

    private <T> T handleResponse(HttpResponse<String> response, TypeReference<C5BaseResponse<T>> typeReference,
                                 String traceId, Method method, String path, long startNs,
                                 boolean allowFailureData) {
        long costMs = elapsedMs(startNs, NANOS_PER_MILLI);
        int statusCode = response.statusCode();
        String body = response.body() == null ? "" : response.body();

        if (statusCode < HTTP_OK_MIN || statusCode >= HTTP_OK_MAX) {
            String truncatedBody = truncate(body, LOG_BODY_LIMIT);
            String statusDesc = C5HttpStatusEnum.getDesc(statusCode, "HTTP 请求失败");
            log.warn("[c5-trace={}] <<< http error: method={}, endpoint={}, status={}, statusDesc={}, costMs={}, body={}",
                    traceId, method, path, statusCode, statusDesc, costMs, truncatedBody);
            throw new C5HttpException(statusCode, truncatedBody,
                    String.format("C5 HTTP Error [%d]: %s, endpoint=%s, body=%s", statusCode, statusDesc, path, truncatedBody));
        }

        C5BaseResponse<T> resp = parseResponse(body, typeReference, traceId, method, path, costMs);

        if (!resp.isSuccess()) {
            String errorMsg = C5BusinessStatusEnum.getDesc(resp.getErrorCode(), resp.getErrorMsg());
            log.warn("[c5-trace={}] <<< business failed: method={}, endpoint={}, costMs={}, errorCode={}, errorMsg={}, errorData={}, allowFailureData={}",
                    traceId, method, path, costMs,
                    resp.getErrorCode(), errorMsg, truncate(String.valueOf(resp.getErrorData()), LOG_BODY_LIMIT), allowFailureData);
            if (allowFailureData && resp.getData() != null) {
                return resp.getData();
            }
            throw new C5BusinessException(resp.getErrorCode(), errorMsg, resp.getErrorData());
        }

        if (log.isDebugEnabled()) {
            log.debug("[c5-trace={}] <<< success: method={}, endpoint={}, costMs={}, body={}",
                    traceId, method, path, costMs, truncate(body, LOG_BODY_LIMIT));
        } else {
            log.info("[c5-trace={}] <<< success: method={}, endpoint={}, costMs={}",
                    traceId, method, path, costMs);
        }
        return resp.getData();
    }

    private <T> C5BaseResponse<T> parseResponse(String body, TypeReference<C5BaseResponse<T>> typeReference,
                                                String traceId, Method method, String path, long costMs) {
        C5BaseResponse<T> resp;
        try {
            resp = OBJECT_MAPPER.readValue(body, typeReference);
        } catch (JsonProcessingException e) {
            log.error("[c5-trace={}] <<< parse failed: method={}, endpoint={}, costMs={}, body={}",
                    traceId, method, path, costMs, truncate(body, LOG_BODY_LIMIT), e);
            throw new C5SerializationException("Response parse failed: " + e.getMessage(), e);
        }
        if (resp == null) {
            log.warn("[c5-trace={}] <<< empty response: method={}, endpoint={}, costMs={}",
                    traceId, method, path, costMs);
            throw new C5SerializationException("Response parse failed: empty body", null);
        }
        return resp;
    }

    private HttpRequest buildRequest(URI uri, Method method, Map<String, Object> bodyParams, String traceId) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(uri)
                .timeout(Duration.ofSeconds(Math.max(MIN_TIMEOUT_SECONDS, config.getRequestTimeoutSeconds())))
                .header(HEADER_ACCEPT, CONTENT_TYPE_JSON)
                .header(HEADER_TRACE, traceId);

        if (method == Method.POST) {
            builder.header(HEADER_CONTENT_TYPE, CONTENT_TYPE_JSON);
            if (CollUtil.isNotEmpty(bodyParams)) {
                String json;
                try {
                    json = OBJECT_MAPPER.writeValueAsString(bodyParams);
                } catch (JsonProcessingException e) {
                    throw new C5SerializationException("Serialize request body failed", e);
                }
                builder.POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8));
            } else {
                builder.POST(HttpRequest.BodyPublishers.noBody());
            }
        } else {
            builder.GET();
        }
        return builder.build();
    }

    private record RequestContext(HttpRequest request, String traceId, String path, String paramSummary) {
    }
}
