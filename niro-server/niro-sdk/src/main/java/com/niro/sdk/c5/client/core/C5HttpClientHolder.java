package com.niro.sdk.c5.client.core;

import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * C5 HTTP 客户端单例持有者。
 */
@Slf4j
public final class C5HttpClientHolder {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Pattern SENSITIVE_QUERY_PATTERN = Pattern.compile(
            "(?i)([?&](?:app-key|appkey|token|accesstoken|refreshtoken|cookie|setcookie|apisecret|secret|sign|signature|password|appsecret|privatekey|steamcookie)=)[^&\\s]+"
    );

    private static final class Holder {
        private static final OkHttpClient INSTANCE = new OkHttpClient.Builder()
                .connectTimeout(CONNECT_TIMEOUT.toSeconds(), TimeUnit.SECONDS)
                .addInterceptor(new C5SdkLoggingInterceptor())
                .addNetworkInterceptor(httpLoggingInterceptor())
                .build();
    }

    private C5HttpClientHolder() {
    }

    public static OkHttpClient getInstance() {
        return Holder.INSTANCE;
    }

    private static HttpLoggingInterceptor httpLoggingInterceptor() {
        HttpLoggingInterceptor interceptor = new HttpLoggingInterceptor(
                message -> log.debug("OkHttp {}", redactSensitiveQuery(message))
        );
        interceptor.redactHeader("Authorization");
        interceptor.redactHeader("Cookie");
        interceptor.redactHeader("Set-Cookie");
        interceptor.redactHeader("app-key");
        interceptor.setLevel(log.isDebugEnabled() ? HttpLoggingInterceptor.Level.BASIC : HttpLoggingInterceptor.Level.NONE);
        return interceptor;
    }

    private static String redactSensitiveQuery(String message) {
        return SENSITIVE_QUERY_PATTERN.matcher(message).replaceAll("$1***");
    }
}
