package com.niro.web.jobhandler;

import cn.hutool.core.collection.ListUtil;
import com.niro.sdk.c5.client.C5ApiClient;
import com.niro.sdk.c5.client.module.C5OrderClient;
import com.niro.sdk.c5.request.order.C5BuyerStatusRequest;
import com.niro.sdk.c5.response.order.C5BuyerStatusResponse;
import com.niro.web.dto.UserPlatformSettingsDTO;
import com.niro.web.entity.TradeOrderRecord;
import com.niro.web.enums.OrderStatusEnum;
import com.niro.web.manager.TradeOrderRecordManagerMapper;
import com.niro.web.service.UserPlatformSettingsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.MockitoAnnotations;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class C5OrderSyncJobHandlerTest {

    @Mock
    private TradeOrderRecordManagerMapper tradeOrderRecordManagerMapper;

    @Mock
    private UserPlatformSettingsService userPlatformSettingsService;

    @InjectMocks
    private C5OrderSyncJobHandler jobHandler;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void syncC5OrderStatuses_Success() {
        // Mock data
        TradeOrderRecord order = new TradeOrderRecord();
        order.setUserId(1L);
        order.setOrderId("test_order_id");
        order.setStatus(OrderStatusEnum.SUCCESS.getCode());
        order.setPlatform("C5");

        UserPlatformSettingsDTO settings = new UserPlatformSettingsDTO();
        settings.setC5AppKey("test_app_key");

        C5BuyerStatusResponse response = new C5BuyerStatusResponse();
        C5BuyerStatusResponse.OrderBuyDTO statusDTO = new C5BuyerStatusResponse.OrderBuyDTO();
        statusDTO.setOrderId("test_order_id");
        statusDTO.setStatus(11); // Cancelled
        statusDTO.setOrderAssetId("test_asset_id");
        response.setList(ListUtil.toList(statusDTO));

        // Mock method calls
        when(tradeOrderRecordManagerMapper.selectActiveC5Orders(any(LocalDateTime.class)))
                .thenReturn(ListUtil.toList(order));
        when(userPlatformSettingsService.getByUserId(1L)).thenReturn(settings);

        // Mock C5ApiClient construction and method call
        try (MockedConstruction<C5ApiClient> mocked = mockConstruction(C5ApiClient.class,
                (mock, context) -> {
                    C5OrderClient orderClient = mock(C5OrderClient.class);
                    when(mock.getOrder()).thenReturn(orderClient);
                    when(orderClient.batchBuyerStatus(any(C5BuyerStatusRequest.class))).thenReturn(response);
                })) {

            jobHandler.syncC5OrderStatuses();

            // Verify
            verify(tradeOrderRecordManagerMapper, times(1)).updateById(any(TradeOrderRecord.class));
        }
    }
}
