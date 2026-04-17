package com.niro.web.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONException;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.niro.core.exception.BusinessException;
import com.niro.core.util.Assert;
import com.niro.web.dto.UnboxRecordOcrResultDTO;
import com.niro.web.service.UnboxRecordOcrService;
import io.netty.channel.ChannelOption;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Duration;

/**
 * 开箱记录 OCR 服务实现
 */
@Slf4j
@Service
public class UnboxRecordOcrServiceImpl implements UnboxRecordOcrService {

    private static final long MAX_FILE_SIZE = 5L * 1024 * 1024;
    private static final String DEFAULT_FAILURE_MESSAGE = "OCR 服务调用失败";

    private final WebClient webClient;
    private final Duration readTimeout;

    public UnboxRecordOcrServiceImpl(
            @Value("${niro.ocr.base-url:http://127.0.0.1:5000}") String ocrBaseUrl,
            @Value("${niro.ocr.connect-timeout-ms:3000}") int connectTimeout,
            @Value("${niro.ocr.read-timeout-ms:30000}") int readTimeout
    ) {
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectTimeout)
                .responseTimeout(Duration.ofMillis(readTimeout));
        this.webClient = WebClient.builder()
                .baseUrl(StrUtil.removeSuffix(StrUtil.trim(ocrBaseUrl), "/"))
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
        this.readTimeout = Duration.ofMillis(readTimeout);
    }

    @Override
    public UnboxRecordOcrResultDTO recognize(MultipartFile file) {
        long startedAt = System.currentTimeMillis();
        validateFile(file);
        byte[] bytes = getFileBytes(file);
        MultipartBodyBuilder bodyBuilder = new MultipartBodyBuilder();
        bodyBuilder.part("file", new ByteArrayResource(bytes) {
                    @Override
                    public String getFilename() {
                        return normalizeFilename(file.getOriginalFilename());
                    }
                })
                .contentType(resolveContentType(file.getContentType()));

        String body;
        try {
            body = webClient.post()
                    .uri("/ocr/recognize")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(BodyInserters.fromMultipartData(bodyBuilder.build()))
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, response -> response.bodyToMono(String.class)
                            .defaultIfEmpty("")
                            .flatMap(errorBody -> Mono.error(new BusinessException(extractErrorMessage(errorBody)))))
                    .bodyToMono(String.class)
                    .block(readTimeout);
        } catch (WebClientRequestException ex) {
            log.warn("调用 OCR 服务失败", ex);
            throw new BusinessException("OCR 服务不可用");
        }

        Assert.notBlank(body, "OCR 服务返回为空");

        try {
            JSONObject result = JSONUtil.parseObj(body);
            UnboxRecordOcrResultDTO dto = new UnboxRecordOcrResultDTO();
            dto.setName(normalizeText(result.getObj("name")));
            dto.setPrice(normalizeDecimal(result.getObj("price")));
            dto.setWear(normalizeDecimal(result.getObj("wear")));
            dto.setExterior(normalizeInteger(result.getObj("exterior")));
            log.info(
                    "OCR 识别完成 | elapsedMs={} | filename={} | name={} | price={} | wear={} | exterior={}",
                    System.currentTimeMillis() - startedAt,
                    normalizeFilename(file.getOriginalFilename()),
                    dto.getName(),
                    dto.getPrice(),
                    dto.getWear(),
                    dto.getExterior()
            );
            return dto;
        } catch (JSONException | NumberFormatException ex) {
            log.warn("解析 OCR 服务响应失败: {}", body, ex);
            throw new BusinessException("OCR 服务返回格式异常");
        }
    }

    private void validateFile(MultipartFile file) {
        Assert.notNull(file, "图片文件不能为空");
        Assert.isFalse(file.isEmpty(), "图片文件不能为空");
        Assert.isTrue(StrUtil.startWith(StrUtil.blankToDefault(file.getContentType(), ""), "image/"), "仅支持上传图片文件");
        Assert.isTrue(file.getSize() <= MAX_FILE_SIZE, "图片大小不能超过 5MB");
    }

    private byte[] getFileBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            log.warn("读取 OCR 图片文件失败: {}", file.getOriginalFilename(), e);
            throw new BusinessException("读取图片文件失败");
        }
    }

    private String extractErrorMessage(String body) {
        if (StrUtil.isBlank(body)) {
            return DEFAULT_FAILURE_MESSAGE;
        }
        try {
            JSONObject errorBody = JSONUtil.parseObj(body);
            return StrUtil.blankToDefault(errorBody.getStr("message"), DEFAULT_FAILURE_MESSAGE);
        } catch (JSONException ex) {
            log.warn("解析 OCR 错误响应失败: {}", body, ex);
            return DEFAULT_FAILURE_MESSAGE;
        }
    }

    private String normalizeText(Object value) {
        if (value == null) {
            return null;
        }
        String text = StrUtil.trim(String.valueOf(value));
        return StrUtil.isBlank(text) || StrUtil.equalsIgnoreCase(text, "null") ? null : text;
    }

    private BigDecimal normalizeDecimal(Object value) {
        if (value == null) {
            return null;
        }
        String text = StrUtil.trim(String.valueOf(value));
        if (StrUtil.isBlank(text) || StrUtil.equalsIgnoreCase(text, "null")) {
            return null;
        }
        return new BigDecimal(text);
    }

    private Integer normalizeInteger(Object value) {
        if (value == null) {
            return null;
        }
        String text = StrUtil.trim(String.valueOf(value));
        if (StrUtil.isBlank(text) || StrUtil.equalsIgnoreCase(text, "null")) {
            return null;
        }
        return Integer.valueOf(text);
    }

    private String normalizeFilename(String originalFilename) {
        String filename = StrUtil.blankToDefault(StrUtil.trim(originalFilename), "ocr-image");
        return StrUtil.contains(filename, ".") ? filename : filename + ".png";
    }

    private MediaType resolveContentType(String contentType) {
        if (StrUtil.isBlank(contentType)) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
        return MediaType.parseMediaType(contentType);
    }
}
