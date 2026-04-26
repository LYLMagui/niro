package com.niro.web.scheduler;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import jakarta.annotation.PreDestroy;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.niro.web.dto.report.BuffGoodsCategoryReportDTO;
import com.niro.web.dto.report.BuffGoodsReportDTO;
import com.niro.web.entity.BuffGoods;
import com.niro.web.entity.BuffGoodsCategory;
import com.niro.web.service.BuffGoodsCategoryService;
import com.niro.web.service.BuffGoodsService;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 数据上报监听器 (Data Report Listener)
 * 消费 niro:data:report 队列，处理 Python 爬虫上报的 商品、分类、印花 数据
 *
 * @author niro
 * @since 2026-01-23
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataReportListener implements ApplicationRunner {

    private final StringRedisTemplate stringRedisTemplate;
    private final BuffGoodsService buffGoodsService;
    private final BuffGoodsCategoryService buffGoodsCategoryService;
    private final ObjectMapper objectMapper;

    private static final String REDIS_KEY_DATA_REPORT = "niro:data:report";
    private volatile boolean running = true;
    
    // 单线程处理入库，避免并发数据库压力
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    @Override
    public void run(ApplicationArguments args) {
        log.info("🚀 启动数据上报监听器 (DataReportListener)...");
        executorService.submit(this::consumeMessage);
    }

    private void consumeMessage() {
        while (running) {
            try {
                // 阻塞式获取消息 (左进右出 -> FIFO: Python rpush, Java leftPop)
                String message = stringRedisTemplate.opsForList().leftPop(REDIS_KEY_DATA_REPORT, 5, TimeUnit.SECONDS);
                if (message != null) {
                    // 打印前200个字符用于调试
                    log.info("📥 [DataReport] 收到上报数据 (len={}): {}", message.length(), message.length() > 200 ? message.substring(0, 200) + "..." : message);
                    handleMessage(message);
                }
            } catch (Exception e) {
                log.error("消费数据上报消息异常", e);
                try {
                    TimeUnit.SECONDS.sleep(1);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    private void handleMessage(String message) {
        try {
            JsonNode rootNode = objectMapper.readTree(message);
            String type = rootNode.has("type") ? rootNode.get("type").asText() : "";
            JsonNode dataNode = rootNode.get("data");
            JsonNode metaNode = rootNode.get("meta");
            
            if (dataNode == null || dataNode.isEmpty()) {
                return;
            }

            if ("GOODS_LIST".equals(type)) {
                handleGoodsList(dataNode, metaNode);
            } else if ("CATEGORY_LIST".equals(type)) {
                handleCategoryList(dataNode);
            } else {
                log.warn("收到未知类型的上报数据: {}", type);
            }
            
        } catch (Exception e) {
            log.error("处理上报数据失败: {}", message, e);
        }
    }

    private void handleGoodsList(JsonNode dataNode, JsonNode metaNode) {
        try {
            List<BuffGoodsReportDTO> dtoList = objectMapper.convertValue(dataNode, new TypeReference<List<BuffGoodsReportDTO>>() {});
            if (CollUtil.isEmpty(dtoList)) return;

            String syncTag = (metaNode != null && metaNode.has("syncTag")) ? metaNode.get("syncTag").asText() : null;
            List<BuffGoods> list = new ArrayList<>();

            for (BuffGoodsReportDTO dto : dtoList) {
                BuffGoods goods = BeanUtil.copyProperties(dto, BuffGoods.class);
                
                // 特殊处理
                goods.setLastSyncTag(syncTag);
                
                list.add(goods);
            }

            // 批量处理逻辑
            if (CollUtil.isNotEmpty(list)) {
                Set<Long> goodsIds = list.stream().map(BuffGoods::getGoodsId).collect(Collectors.toSet());
                
                // 1. 批量查询存在的记录
                List<BuffGoods> exists = buffGoodsService.lambdaQuery()
                        .in(BuffGoods::getGoodsId, goodsIds)
                        .select(BuffGoods::getId, BuffGoods::getGoodsId)
                        .list();
                
                Map<Long, Long> existMap = exists.stream()
                        .collect(Collectors.toMap(BuffGoods::getGoodsId, BuffGoods::getId));
                
                // 2. 填充 ID 以便更新
                for (BuffGoods goods : list) {
                    if (existMap.containsKey(goods.getGoodsId())) {
                        goods.setId(existMap.get(goods.getGoodsId()));
                    }
                }
                
                // 3. 批量保存或更新
                buffGoodsService.saveOrUpdateBatch(list);
                log.info("✅ 批量处理商品数据: {} 条", list.size());
            }

        } catch (Exception e) {
            log.error("处理商品列表失败", e);
        }
    }

    private void handleCategoryList(JsonNode dataNode) {
        try {
            List<BuffGoodsCategoryReportDTO> dtoList = objectMapper.convertValue(dataNode, new TypeReference<List<BuffGoodsCategoryReportDTO>>() {});
            if (CollUtil.isEmpty(dtoList)) return;

            List<BuffGoodsCategory> list = new ArrayList<>();
            for (BuffGoodsCategoryReportDTO dto : dtoList) {
                list.add(BeanUtil.copyProperties(dto, BuffGoodsCategory.class));
            }

            if (CollUtil.isNotEmpty(list)) {
                Set<String> internalNames = list.stream().map(BuffGoodsCategory::getInternalName).collect(Collectors.toSet());
                
                // 1. 批量查询存在的记录
                List<BuffGoodsCategory> exists = buffGoodsCategoryService.lambdaQuery()
                        .in(BuffGoodsCategory::getInternalName, internalNames)
                        .select(BuffGoodsCategory::getId, BuffGoodsCategory::getInternalName)
                        .list();
                
                Map<String, Long> existMap = exists.stream()
                        .collect(Collectors.toMap(BuffGoodsCategory::getInternalName, BuffGoodsCategory::getId));
                
                // 2. 填充 ID
                for (BuffGoodsCategory cat : list) {
                    if (existMap.containsKey(cat.getInternalName())) {
                        cat.setId(existMap.get(cat.getInternalName()));
                    }
                }
                
                // 3. 批量保存
                buffGoodsCategoryService.saveOrUpdateBatch(list);
                log.info("✅ 批量处理分类数据: {} 条", list.size());
            }
        } catch (Exception e) {
            log.error("处理分类列表失败", e);
        }
    }


    // Spring 容器销毁时停止线程
    @PreDestroy
    public void destroy() {
        this.running = false;
        executorService.shutdown();
    }
}
