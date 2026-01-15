package com.niro.web.dto.param;

import com.niro.web.enums.PaymentMethodEnum;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 用户Buff配置保存参数
 *
 * @author liyl
 * @since 2025-12-24
 */
@Data
public class UserBuffSettingsParam {

    /**
     * 支付方式
     */
    @NotNull(message = "支付方式不能为空")
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
