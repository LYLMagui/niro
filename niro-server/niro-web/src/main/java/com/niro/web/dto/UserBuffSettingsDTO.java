package com.niro.web.dto;

import com.niro.web.enums.PaymentMethodEnum;
import lombok.Data;

/**
 * 用户Buff配置DTO
 *
 * @author liyl
 * @since 2025-12-24
 */
@Data
public class UserBuffSettingsDTO {

    /**
     * ID
     */
    private Long id;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 支付方式
     */
    private PaymentMethodEnum paymentMethod;

    /**
     * 企业微信企业ID
     */
    private String wecomCorpid;

    /**
     * 企业微信应用Secret
     */
    private String wecomCorpsecret;

    /**
     * 企业微信应用AgentID
     */
    private String wecomAgentid;

    /**
     * 企业微信接收人
     */
    private String wecomTouser;
}
