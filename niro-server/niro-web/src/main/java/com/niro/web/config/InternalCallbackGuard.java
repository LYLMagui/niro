package com.niro.web.config;

import cn.hutool.core.util.StrUtil;
import com.niro.core.util.Assert;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 内部回调口的最小鉴权守卫。
 * 不配置 token 时保持兼容；配置后要求请求头带共享令牌。
 */
@Component
public class InternalCallbackGuard {

    private final String callbackToken;
    private final String callbackHeader;

    public InternalCallbackGuard(
            @Value("${niro.internal.callback-token:}") String callbackToken,
            @Value("${niro.internal.callback-header:X-Niro-Internal-Token}") String callbackHeader
    ) {
        this.callbackToken = callbackToken;
        this.callbackHeader = callbackHeader;
    }

    public void check(HttpServletRequest request, String source) {
        if (StrUtil.isBlank(callbackToken)) {
            return;
        }
        String requestToken = request.getHeader(callbackHeader);
        Assert.isTrue(StrUtil.equals(callbackToken, requestToken), source + "回调鉴权失败");
    }
}
