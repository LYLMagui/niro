package com.niro.web.service.impl;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.niro.web.service.LogService;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 日志服务实现类
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LogServiceImpl implements LogService {

    private final ElasticsearchClient elasticsearchClient;

    @Override
    public List<Map<String, Object>> queryLogsByTraceId(String traceId) {
        try {
            // 查询所有 niro-* 索引，匹配 traceId
            SearchResponse<Map> response = elasticsearchClient.search(s -> s
                    .index("niro-*")
                    .query(q -> q
                            .bool(b -> b
                                    .should(s1 -> s1.term(t -> t.field("traceId.keyword").value(traceId)))
                                    .should(s2 -> s2.term(t -> t.field("extra.traceId.keyword").value(traceId)))
                            )
                    )
                    .sort(so -> so.field(f -> f.field("@timestamp").order(SortOrder.Asc)))
                    .size(1000), Map.class);

            return response.hits().hits().stream()
                    .map(hit -> (Map<String, Object>) hit.source())
                    .collect(Collectors.toList());
        } catch (IOException e) {
            log.error("查询 ES 日志失败, traceId: {}", traceId, e);
            return new ArrayList<>();
        }
    }
}
