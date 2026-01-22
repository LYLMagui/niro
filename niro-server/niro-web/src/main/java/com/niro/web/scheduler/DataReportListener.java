package com.niro.web.scheduler;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.niro.web.entity.BuffGoods;
import com.niro.web.entity.BuffGoodsCategory;
import com.niro.web.entity.BuffSticker;
import com.niro.web.service.BuffGoodsCategoryService;
import com.niro.web.service.BuffGoodsService;
import com.niro.web.service.BuffStickerService;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
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
    private final BuffStickerService buffStickerService;

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
                // 阻塞式获取消息
                String message = stringRedisTemplate.opsForList().rightPop(REDIS_KEY_DATA_REPORT, 5, TimeUnit.SECONDS);
                if (message != null) {
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
            JSONObject json = JSONUtil.parseObj(message);
            String type = json.getStr("type");
            
            if ("GOODS_LIST".equals(type)) {
                handleGoodsList(json);
            } else if ("CATEGORY_LIST".equals(type)) {
                handleCategoryList(json.getJSONArray("data"));
            } else if ("STICKER_LIST".equals(type)) {
                handleStickerList(json.getJSONArray("data"));
            } else {
                log.warn("收到未知类型的上报数据: {}", type);
            }
            
        } catch (Exception e) {
            log.error("处理上报数据失败: {}", message, e);
        }
    }

    private void handleGoodsList(JSONObject json) {
        JSONArray data = json.getJSONArray("data");
        JSONObject meta = json.getJSONObject("meta");
        String syncTag = meta != null ? meta.getStr("syncTag") : null;

        if (CollUtil.isEmpty(data)) return;
        List<BuffGoods> list = new ArrayList<>();
        
        for (Object item : data) {
            JSONObject obj = (JSONObject) item;
            BuffGoods goods = new BuffGoods();
            // 映射字段 (Python 下划线 -> Java 驼峰/字段)
            goods.setGoodsId(obj.getLong("goods_id"));
            goods.setName(obj.getStr("name"));
            goods.setMarketHashName(obj.getStr("market_hash_name"));
            goods.setShortName(obj.getStr("short_name"));
            goods.setIconUrl(obj.getStr("icon_url"));
            goods.setOriginalIconUrl(obj.getStr("original_icon_url"));
            goods.setCategoryId(obj.getLong("category_id"));
            goods.setRarity(obj.getStr("rarity"));
            goods.setExterior(obj.getStr("exterior"));
            goods.setTags(obj.getJSONObject("tags"));
            goods.setLastSyncTag(syncTag);
            
            list.add(goods);
        }
        
        if (CollUtil.isNotEmpty(list)) {
            for (BuffGoods goods : list) {
                try {
                    BuffGoods exist = buffGoodsService.lambdaQuery().eq(BuffGoods::getGoodsId, goods.getGoodsId()).one();
                    if (exist != null) {
                        goods.setId(exist.getId());
                        buffGoodsService.updateById(goods);
                    } else {
                        buffGoodsService.save(goods);
                    }
                } catch (Exception e) {
                    log.error("保存商品失败: {}", goods.getGoodsId(), e);
                }
            }
            log.info("✅ 批量处理商品数据: {} 条", list.size());
        }
    }

    private void handleCategoryList(JSONArray data) {
        if (CollUtil.isEmpty(data)) return;
        List<BuffGoodsCategory> list = new ArrayList<>();
        
        for (Object item : data) {
            JSONObject obj = (JSONObject) item;
            BuffGoodsCategory cat = new BuffGoodsCategory();
            cat.setName(obj.getStr("name"));
            cat.setInternalName(obj.getStr("internal_name"));
            cat.setCategoryType(obj.getStr("category_type"));
            cat.setFullInternalName(obj.getStr("full_internal_name"));
            cat.setParentId(obj.getLong("parent_id"));
            list.add(cat);
        }

        for (BuffGoodsCategory cat : list) {
            try {
                // 根据 internalName 查重
                BuffGoodsCategory exist = buffGoodsCategoryService.lambdaQuery()
                        .eq(BuffGoodsCategory::getInternalName, cat.getInternalName())
                        .one();
                
                if (exist != null) {
                    cat.setId(exist.getId());
                    buffGoodsCategoryService.updateById(cat);
                } else {
                    buffGoodsCategoryService.save(cat);
                }
            } catch (Exception e) {
                log.error("保存分类失败: {}", cat.getInternalName(), e);
            }
        }
        log.info("✅ 批量处理分类数据: {} 条", list.size());
    }

    private void handleStickerList(JSONArray data) {
        if (CollUtil.isEmpty(data)) return;
        List<BuffSticker> list = new ArrayList<>();
        
        for (Object item : data) {
            JSONObject obj = (JSONObject) item;
            BuffSticker sticker = new BuffSticker();
            sticker.setStickerId(obj.getLong("sticker_id"));
            sticker.setName(obj.getStr("name"));
            sticker.setImageUrl(obj.getStr("image_url"));
            sticker.setPrice(obj.getBigDecimal("price"));
            sticker.setSellNum(obj.getInt("sell_num"));
            list.add(sticker);
        }

        for (BuffSticker sticker : list) {
            try {
                BuffSticker exist = buffStickerService.lambdaQuery()
                        .eq(BuffSticker::getStickerId, sticker.getStickerId())
                        .one();
                
                if (exist != null) {
                    sticker.setId(exist.getId());
                    buffStickerService.updateById(sticker);
                } else {
                    buffStickerService.save(sticker);
                }
            } catch (Exception e) {
                log.error("保存印花失败: {}", sticker.getStickerId(), e);
            }
        }
        log.info("✅ 批量处理印花数据: {} 条", list.size());
    }

    // Spring 容器销毁时停止线程
    public void destroy() {
        this.running = false;
        executorService.shutdown();
    }
}
