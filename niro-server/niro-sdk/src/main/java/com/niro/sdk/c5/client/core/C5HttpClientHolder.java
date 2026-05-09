package com.niro.sdk.c5.client.core;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.concurrent.Executors;

/**
 * C5 HTTP 客户端单例持有者
 * <p>
 * 全局共享一个 {@link HttpClient}：
 * <ul>
 *   <li>避免每个 {@code C5HttpExecutor} 实例新建一份连接池与虚拟线程池；</li>
 *   <li>{@code HttpClient} 自身按 host 维度复用 HTTP/2 连接，多账号场景共享同一实例不会串数据；</li>
 *   <li>采用 JDK 21 虚拟线程执行器承载 IO，连接超时固定 10s（业务级超时由请求级 {@code requestTimeoutSeconds} 控制）。</li>
 * </ul>
 */
public final class C5HttpClientHolder {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);

    private static final class Holder {
        private static final HttpClient INSTANCE = HttpClient.newBuilder()
                .executor(Executors.newVirtualThreadPerTaskExecutor())
                .connectTimeout(CONNECT_TIMEOUT)
                .version(HttpClient.Version.HTTP_2)
                .build();
    }

    private C5HttpClientHolder() {
    }

    public static HttpClient getInstance() {
        return Holder.INSTANCE;
    }
}
