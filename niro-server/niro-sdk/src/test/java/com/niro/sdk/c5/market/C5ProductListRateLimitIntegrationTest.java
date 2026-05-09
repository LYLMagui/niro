package com.niro.sdk.c5.market;

import com.niro.sdk.c5.client.C5ApiClient;
import com.niro.sdk.c5.config.C5Config;
import com.niro.sdk.c5.exception.C5BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * C5 在售商品查询限流真实接口测试。
 */
@Slf4j
class C5ProductListRateLimitIntegrationTest {

    private static final String APP_KEY = "aa7b1b53659e4365816486d7b0a5e058";
    private static final String MARKET_HASH_NAME = "Sealed Dead Hand Terminal";
    private static final int CS2_APP_ID = 730;
    private static final int AUTO_DELIVERY = 2;
    private static final int PAGE_NUM = 1;
    private static final int PAGE_SIZE = 1;
    private static final int REQUEST_COUNT = 50;

    @Test
    @EnabledIfEnvironmentVariable(named = "C5_ENABLE_RATE_LIMIT_TEST", matches = "true")
    void productListRateLimitShouldReturnBusinessError() {
        C5ApiClient client = new C5ApiClient(new C5Config().setAppKey(APP_KEY).setRequestTimeoutSeconds(10));
        C5ProductListRequest request = buildProductListRequest();
        log.info("C5 在售商品限流测试请求: requestCount={}, marketHashName={}, appId={}, delivery={}, pageNum={}, pageSize={}",
                REQUEST_COUNT, request.getMarketHashName(), request.getAppId(), request.getDelivery(), request.getPageNum(),
                request.getPageSize());

        CompletableFuture<?>[] futures = IntStream.rangeClosed(1, REQUEST_COUNT)
                .mapToObj(index -> CompletableFuture.runAsync(() -> queryProductList(client, request, index)))
                .toArray(CompletableFuture[]::new);

        try {
            CompletableFuture.allOf(futures).join();
            fail("未触发 C5 在售商品查询限流错误");
        } catch (Exception e) {
            Throwable cause = unwrap(e);
            assertNotNull(cause);
            log.info("C5 在售商品查询限流异常: type={}, message={}", cause.getClass().getName(), cause.getMessage(), cause);
            if (cause instanceof C5BusinessException businessException) {
                log.info("C5 在售商品查询限流业务码: errorCode={}, errorMsg={}, errorData={}",
                        businessException.getErrorCode(), businessException.getErrorMsg(), businessException.getErrorData());
            }
        }
    }

    private void queryProductList(C5ApiClient client, C5ProductListRequest request, int index) {
        C5ProductListResponse response = client.getMarket().searchProductList(request);
        int size = response.getList() == null ? 0 : response.getList().size();
        log.info("C5 在售商品查询限流测试第 {} 次响应: size={}", index, size);
    }

    private C5ProductListRequest buildProductListRequest() {
        return new C5ProductListRequest()
                .setMarketHashName(MARKET_HASH_NAME)
                .setAppId(CS2_APP_ID)
                .setDelivery(AUTO_DELIVERY)
                .setPageNum(PAGE_NUM)
                .setPageSize(PAGE_SIZE);
    }

    private Throwable unwrap(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }
}
