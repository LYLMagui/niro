package com.niro.sdk.c5.client;

import com.niro.sdk.c5.client.core.C5HttpExecutor;
import com.niro.sdk.c5.account.C5AccountClient;
import com.niro.sdk.c5.inventory.C5InventoryClient;
import com.niro.sdk.c5.market.C5MarketClient;
import com.niro.sdk.c5.order.C5OrderClient;
import com.niro.sdk.c5.purchase.C5PurchaseClient;
import com.niro.sdk.c5.trade.C5TradeClient;
import com.niro.sdk.c5.config.C5Config;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * C5 开放平台客户端
 * <p>
 * SDK 统一入口，负责初始化 HTTP 引擎及各功能模块。
 * 使用 JDK 21 虚拟线程和连接池技术优化性能。
 */
@Slf4j
@Getter
public class C5ApiClient {

    /**
     * HTTP 执行引擎 (核心组件，负责连接池和请求发送)
     */
    private final C5HttpExecutor executor;

    // ================== 功能模块 ==================

    /**
     * 账户模块 (余额、Steam信息)
     */
    private final C5AccountClient account;

    /**
     * 市场模块 (行情、搜索、存世量)
     */
    private final C5MarketClient market;

    /**
     * 交易模块 (购买、批量购买)
     */
    private final C5TradeClient trade;

    /**
     * 求购模块 (求购单管理)
     */
    private final C5PurchaseClient purchase;

    /**
     * 库存模块 (用户库存查询)
     */
    private final C5InventoryClient inventory;

    /**
     * 订单模块
     */
    private final C5OrderClient order;

    /**
     * 构造 C5 客户端
     *
     * @param config C5 配置信息
     */
    public C5ApiClient(C5Config config) {
        // 初始化核心引擎 (单例 HttpClient，线程安全)
        this.executor = new C5HttpExecutor(config);

        // 初始化各功能模块
        this.account = new C5AccountClient(executor);
        this.market = new C5MarketClient(executor);
        this.trade = new C5TradeClient(executor);
        this.purchase = new C5PurchaseClient(executor);
        this.inventory = new C5InventoryClient(executor);
        this.order = new C5OrderClient(executor);

        log.info("C5ApiClient initialized with Virtual Thread support.");
    }
}
