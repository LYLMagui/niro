package com.niro.sdk.c5.client.module;

import com.fasterxml.jackson.core.type.TypeReference;
import com.niro.sdk.c5.client.core.C5HttpEngine;
import com.niro.sdk.c5.config.C5Config;
import com.niro.sdk.c5.request.market.C5ProductListRequest;
import com.niro.sdk.c5.response.C5BaseResponse;
import com.niro.sdk.c5.response.market.C5ProductListResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * C5MarketClient 单元测试
 */
class C5MarketClientTest {

    private C5HttpEngine engine;
    private C5MarketClient marketClient;

    @BeforeEach
    void setUp() {
        engine = mock(C5HttpEngine.class);
        marketClient = new C5MarketClient(engine);
    }

    @Test
    void searchProductList_ShouldCallCorrectEndpointAndReturnData() {
        // 1. 准备请求数据
        C5ProductListRequest req = new C5ProductListRequest()
                .setMarketHashName("AK-47 | Redline (Field-Tested)")
                .setAppId(730)
                .setPageNum(1)
                .setPageSize(20);

        // 2. 准备模拟响应数据
        C5ProductListResponse mockResponseData = new C5ProductListResponse();
        mockResponseData.setPageNum(1);
        mockResponseData.setPageSize(20);
        mockResponseData.setHasMore(false);

        C5ProductListResponse.ProductDTO product = new C5ProductListResponse.ProductDTO();
        product.setProductId("123456");
        product.setPrice(new BigDecimal("100.00"));
        mockResponseData.setList(List.of(product));

        // 3. 配置 Mock 行为
        when(engine.execute(eq("/merchant/market/v2/products/list"), eq("POST"), eq(req), any()))
                .thenReturn(mockResponseData);

        // 4. 执行调用
        C5ProductListResponse actualResponse = marketClient.searchProductList(req);

        // 5. 验证结果
        assertNotNull(actualResponse);
        assertEquals(1, actualResponse.getList().size());
        assertEquals("123456", actualResponse.getList().get(0).getProductId());
        assertEquals(new BigDecimal("100.00"), actualResponse.getList().get(0).getPrice());

        // 6. 验证请求参数捕获
        ArgumentCaptor<String> endpointCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> methodCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object> paramsCaptor = ArgumentCaptor.forClass(Object.class);
        
        verify(engine).execute(endpointCaptor.capture(), methodCaptor.capture(), paramsCaptor.capture(), any());
        
        assertEquals("/merchant/market/v2/products/list", endpointCaptor.getValue());
        assertEquals("POST", methodCaptor.getValue());
        assertEquals(req, paramsCaptor.getValue());
    }
}
