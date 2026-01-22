package com.niro.core.constant;

import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * User-Agent 常量类
 * 用于模拟不同浏览器的请求头
 * @author niro
 * @date 2026-01-22
 */
public class UserAgentConstant {

    /**
     * 主流浏览器 User-Agent 列表
     */
    public static final List<String> USER_AGENTS = Arrays.asList(
        // Mac Chrome
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_12_6) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/73.0.3683.75 Safari/537.36",
        // Mac Firefox
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10.12; rv:65.0) Gecko/20100101 Firefox/65.0",
        // Mac Safari
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_12_6) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/12.0.3 Safari/605.1.15",
        // Windows Chrome
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/72.0.3626.121 Safari/537.36",
        // Windows Edge
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/144.0.0.0 Safari/537.36 Edg/144.0.0.0",
        // iOS Chrome
        "Mozilla/5.0 (iPhone; CPU iPhone OS 7_0_4 like Mac OS X) AppleWebKit/537.51.1 (KHTML, like Gecko) CriOS/31.0.1650.18 Mobile/11B554a Safari/8536.25",
        // iOS Safari
        "Mozilla/5.0 (iPhone; CPU iPhone OS 8_3 like Mac OS X) AppleWebKit/600.1.4 (KHTML, like Gecko) Version/8.0 Mobile/12F70 Safari/600.1.4",
        // Android Chrome
        "Mozilla/5.0 (Linux; Android 4.2.1; M040 Build/JOP40D) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/31.0.1650.59 Mobile Safari/537.36",
        // Android Webkit
        "Mozilla/5.0 (Linux; U; Android 4.4.4; zh-cn; M351 Build/KTU84P) AppleWebKit/534.30 (KHTML, like Gecko) Version/4.0 Mobile Safari/534.30"
    );

    /**
     * 获取随机 User-Agent
     * @return 随机 User-Agent 字符串
     */
    public static String getRandomUserAgent() {
        return USER_AGENTS.get(new Random().nextInt(USER_AGENTS.size()));
    }
}
