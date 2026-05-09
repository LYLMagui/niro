package com.niro.sdk.c5.trade;

import com.niro.sdk.c5.client.C5ApiClient;
import com.niro.sdk.c5.config.C5Config;
import com.niro.sdk.c5.market.C5ProductListRequest;
import com.niro.sdk.c5.market.C5ProductListResponse;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * C5 普通购买真实接口测试。
 */
@Slf4j
class C5NormalBuyIntegrationTest {

    private static final String APP_KEY = "aa7b1b53659e4365816486d7b0a5e058";
    private static final String TRADE_URL = "https://steamcommunity.com/tradeoffer/new/?partner=838116584&token=ONOlXNTF";
    private static final String MARKET_HASH_NAME = "Sealed Dead Hand Terminal";
    private static final int CS2_APP_ID = 730;
    private static final int AUTO_DELIVERY = 2;
    private static final int PAGE_NUM = 1;
    private static final int PAGE_SIZE = 1;

    @Test
    @EnabledIfEnvironmentVariable(named = "C5_ENABLE_REAL_BUY", matches = "true")
    void normalBuyWithFirstProductFromProductList() {
        C5ApiClient client = new C5ApiClient(new C5Config().setAppKey(APP_KEY));

        C5ProductListRequest productListRequest = new C5ProductListRequest()
                .setMarketHashName(MARKET_HASH_NAME)
                .setAppId(CS2_APP_ID)
                .setDelivery(AUTO_DELIVERY)
                .setPageNum(PAGE_NUM)
                .setPageSize(PAGE_SIZE);
        printProductListRequest(productListRequest);

        C5ProductListResponse productList = client.getMarket().searchProductList(productListRequest);
        log.info("C5 在售商品查询响应: {}", productList);

        assertNotNull(productList);
        List<C5ProductListResponse.ProductDTO> products = productList.getList();
        assertNotNull(products);
        assertFalse(products.isEmpty());

        C5ProductListResponse.ProductDTO product = products.getFirst();
        Long productId = Long.valueOf(product.getProductId());
        BigDecimal buyPrice = product.getPrice();
        String outTradeNo = buildOutTradeNo();

        C5NormalBuyRequest normalBuyRequest = new C5NormalBuyRequest()
                .setOutTradeNo(outTradeNo)
                .setTradeUrl(TRADE_URL)
                .setProductId(productId)
                .setBuyPrice(buyPrice);
        printNormalBuyRequest(normalBuyRequest);

        C5BuyResponse buyResponse = client.getTrade().normalBuy(normalBuyRequest);
        log.info("C5 普通购买响应: {}", buyResponse);

        assertNotNull(buyResponse);
        assertNotNull(buyResponse.getOrderId());
    }

    private void printProductListRequest(C5ProductListRequest request) {
        log.info("C5 在售商品查询请求: marketHashName={}, appId={}, delivery={}, pageNum={}, pageSize={}",
                request.getMarketHashName(), request.getAppId(), request.getDelivery(), request.getPageNum(),
                request.getPageSize());
    }

    private void printNormalBuyRequest(C5NormalBuyRequest request) {
        log.info("C5 普通购买请求: outTradeNo={}, tradeUrl={}, productId={}, buyPrice={}",
                request.getOutTradeNo(), maskTradeUrl(request.getTradeUrl()), request.getProductId(),
                request.getBuyPrice());
    }

    private String maskTradeUrl(String tradeUrl) {
        return tradeUrl.replaceAll("token=[^&]+", "token=***");
    }

    private String buildOutTradeNo() {
        return "niro-test-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"));
    }
}
