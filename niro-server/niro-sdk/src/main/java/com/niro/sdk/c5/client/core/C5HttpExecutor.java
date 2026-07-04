package com.niro.sdk.c5.client.core;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.Method;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONException;
import com.alibaba.fastjson2.TypeReference;
import com.niro.sdk.c5.config.C5Config;
import com.niro.sdk.c5.constant.C5HttpConstant;
import com.niro.sdk.c5.constant.C5SdkConstant;
import com.niro.sdk.c5.enums.C5BusinessStatusEnum;
import com.niro.sdk.c5.enums.C5HttpStatusEnum;
import com.niro.sdk.c5.exception.C5ApiException;
import com.niro.sdk.c5.exception.C5BusinessException;
import com.niro.sdk.c5.exception.C5HttpException;
import com.niro.sdk.c5.exception.C5NetworkException;
import com.niro.sdk.c5.exception.C5SerializationException;
import com.niro.sdk.c5.response.C5BaseResponse;
import com.niro.sdk.common.util.SdkHttpUtil;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.slf4j.MDC;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;

import static com.niro.sdk.c5.constant.C5HttpConstant.*;
import static com.niro.sdk.common.util.SdkHttpUtil.*;

/**
 * C5 HTTP 执行器。
 * <p>
 * 统一负责请求构建、响应解析、异常分层和安全日志。
 */
@Slf4j
public class C5HttpExecutor {

    private static final MediaType JSON_MEDIA_TYPE = MediaType.get(CONTENT_TYPE_JSON);

    private final C5Config config;
    private final OkHttpClient httpClient;

    public C5HttpExecutor(C5Config config) {
        this(config, C5HttpClientHolder.getInstance());
    }

    public C5HttpExecutor(C5Config config, OkHttpClient httpClient) {
        this.config = config;
        int timeoutSeconds = Math.max(MIN_TIMEOUT_SECONDS, config.getRequestTimeoutSeconds());
        this.httpClient = httpClient.newBuilder()
                .callTimeout(timeoutSeconds, TimeUnit.SECONDS)
                .readTimeout(timeoutSeconds, TimeUnit.SECONDS)
                .writeTimeout(timeoutSeconds, TimeUnit.SECONDS)
                .build();
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
        MDC.put(C5SdkConstant.MDC_TRACE_ID, context.traceId());
        try (Response response = sendRequest(context.request(), context.traceId(), method, context.path(), startNs)) {
            String body = readBody(response);
            return handleResponse(response.code(), body, typeReference, context.traceId(), method, context.path(), startNs, allowFailureData);
        } catch (IOException e) {
            throw toNetworkException(e, context.traceId(), method, context.path(), startNs);
        } finally {
            MDC.remove(C5SdkConstant.MDC_TRACE_ID);
        }
    }

    private <T> CompletableFuture<T> doExecuteAsync(String endpoint, Method method, Object params,
                                                    TypeReference<C5BaseResponse<T>> typeReference,
                                                    boolean allowFailureData) {
        RequestContext context = buildRequestContext(endpoint, method, params);
        long startNs = System.nanoTime();
        CompletableFuture<T> future = new CompletableFuture<>();
        httpClient.newCall(context.request()).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                MDC.put(C5SdkConstant.MDC_TRACE_ID, context.traceId());
                try {
                    future.completeExceptionally(toNetworkException(e, context.traceId(), method, context.path(), startNs));
                } finally {
                    MDC.remove(C5SdkConstant.MDC_TRACE_ID);
                }
            }

            @Override
            public void onResponse(Call call, Response response) {
                MDC.put(C5SdkConstant.MDC_TRACE_ID, context.traceId());
                try (response) {
                    String body = readBody(response);
                    T result = handleResponse(response.code(), body, typeReference, context.traceId(), method, context.path(), startNs, allowFailureData);
                    future.complete(result);
                } catch (IOException e) {
                    future.completeExceptionally(toNetworkException(e, context.traceId(), method, context.path(), startNs));
                } catch (RuntimeException e) {
                    future.completeExceptionally(e);
                } finally {
                    MDC.remove(C5SdkConstant.MDC_TRACE_ID);
                }
            }
        });
        return future;
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
        String paramSummary = SdkHttpUtil.summarizeParams(queryParams, bodyParams, C5HttpConstant.SENSITIVE_KEYS);
        Request request = buildRequest(uri, method, bodyParams, traceId, path, paramSummary);
        return new RequestContext(request, traceId, path);
    }

    private Response sendRequest(Request request, String traceId, Method method, String path, long startNs) {
        try {
            return httpClient.newCall(request).execute();
        } catch (IOException e) {
            throw toNetworkException(e, traceId, method, path, startNs);
        }
    }

    private C5NetworkException toNetworkException(Throwable throwable, String traceId,
                                                  Method method, String path, long startNs) {
        Throwable cause = unwrapCompletionException(throwable);
        if (cause instanceof SocketTimeoutException) {
            log.error("C5 SDK request timeout c5TraceId={}, method={}, endpoint={}, costMs={}",
                    traceId, method, path, elapsedMs(startNs, NANOS_PER_MILLI), cause);
            return new C5NetworkException("Request timeout: " + cause.getMessage(), cause, true);
        }
        if (cause instanceof InterruptedIOException && Thread.currentThread().isInterrupted()) {
            Thread.currentThread().interrupt();
            log.error("C5 SDK request interrupted c5TraceId={}, method={}, endpoint={}, costMs={}",
                    traceId, method, path, elapsedMs(startNs, NANOS_PER_MILLI), cause);
            return new C5NetworkException("Request interrupted: " + cause.getMessage(), cause, false);
        }
        if (cause instanceof InterruptedException) {
            Thread.currentThread().interrupt();
            log.error("C5 SDK request interrupted c5TraceId={}, method={}, endpoint={}, costMs={}",
                    traceId, method, path, elapsedMs(startNs, NANOS_PER_MILLI), cause);
            return new C5NetworkException("Request interrupted: " + cause.getMessage(), cause, false);
        }
        log.error("C5 SDK request io failed c5TraceId={}, method={}, endpoint={}, costMs={}",
                traceId, method, path, elapsedMs(startNs, NANOS_PER_MILLI), cause);
        return new C5NetworkException("Request failed: " + cause.getMessage(), cause, true);
    }

    private Throwable unwrapCompletionException(Throwable throwable) {
        if (throwable instanceof CompletionException && throwable.getCause() != null) {
            return throwable.getCause();
        }
        return throwable;
    }

    private String readBody(Response response) throws IOException {
        ResponseBody responseBody = response.body();
        return responseBody == null ? "" : responseBody.string();
    }

    private <T> T handleResponse(int statusCode, String body, TypeReference<C5BaseResponse<T>> typeReference,
                                 String traceId, Method method, String path, long startNs,
                                 boolean allowFailureData) {
        long costMs = elapsedMs(startNs, NANOS_PER_MILLI);
        int bodyLength = body == null ? 0 : body.length();

        if (statusCode < HTTP_OK_MIN || statusCode >= HTTP_OK_MAX) {
            String statusDesc = C5HttpStatusEnum.getDesc(statusCode, "HTTP 请求失败");
            log.warn("C5 SDK http failed c5TraceId={}, method={}, endpoint={}, status={}, statusDesc={}, costMs={}, responseLength={}",
                    traceId, method, path, statusCode, statusDesc, costMs, bodyLength);
            throw new C5HttpException(statusCode, body,
                    String.format("C5 HTTP Error [%d]: %s, endpoint=%s, responseLength=%d", statusCode, statusDesc, path, bodyLength));
        }

        C5BaseResponse<T> resp = parseResponse(body, typeReference, traceId, method, path, costMs);

        if (!resp.isSuccess()) {
            String errorMsg = C5BusinessStatusEnum.getDesc(resp.getErrorCode(), resp.getErrorMsg());
            log.warn("C5 SDK business failed c5TraceId={}, method={}, endpoint={}, status={}, costMs={}, errorCode={}, errorMsg={}, allowFailureData={}, responseLength={}",
                    traceId, method, path, statusCode, costMs,
                    resp.getErrorCode(), errorMsg, allowFailureData, bodyLength);
            if (allowFailureData && resp.getData() != null) {
                return resp.getData();
            }
            throw new C5BusinessException(resp.getErrorCode(), errorMsg, resp.getErrorData());
        }

        log.info("C5 SDK response c5TraceId={}, method={}, endpoint={}, status={}, costMs={}, responseLength={}",
                traceId, method, path, statusCode, costMs, bodyLength);
        return resp.getData();
    }

    private <T> C5BaseResponse<T> parseResponse(String body, TypeReference<C5BaseResponse<T>> typeReference,
                                                String traceId, Method method, String path, long costMs) {
        C5BaseResponse<T> resp;
        try {
            resp = JSON.parseObject(body, typeReference);
        } catch (JSONException e) {
            log.error("C5 SDK parse failed c5TraceId={}, method={}, endpoint={}, costMs={}, body={}",
                    traceId, method, path, costMs, truncate(body, LOG_BODY_LIMIT), e);
            throw new C5SerializationException("Response parse failed: " + e.getMessage(), e);
        }
        if (resp == null) {
            log.warn("C5 SDK empty response c5TraceId={}, method={}, endpoint={}, costMs={}",
                    traceId, method, path, costMs);
            throw new C5SerializationException("Response parse failed: empty body", null);
        }
        return resp;
    }

    private Request buildRequest(URI uri, Method method, Map<String, Object> bodyParams,
                                 String traceId, String path, String paramSummary) {
        Request.Builder builder = new Request.Builder()
                .url(uri.toString())
                .header(HEADER_ACCEPT, CONTENT_TYPE_JSON)
                .header(HEADER_TRACE, traceId)
                .tag(C5RequestLogContext.class, new C5RequestLogContext(traceId, method.name(), path, paramSummary));

        if (method == Method.POST) {
            builder.header(HEADER_CONTENT_TYPE, CONTENT_TYPE_JSON);
            if (CollUtil.isNotEmpty(bodyParams)) {
                String json;
                try {
                    json = JSON.toJSONString(bodyParams);
                } catch (JSONException e) {
                    throw new C5SerializationException("Serialize request body failed", e);
                }
                builder.post(RequestBody.create(json, JSON_MEDIA_TYPE));
            } else {
                builder.post(RequestBody.create("", JSON_MEDIA_TYPE));
            }
        } else {
            builder.get();
        }
        return builder.build();
    }

    private record RequestContext(Request request, String traceId, String path) {
    }
}
