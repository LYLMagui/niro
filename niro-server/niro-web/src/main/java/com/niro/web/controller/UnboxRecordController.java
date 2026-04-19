package com.niro.web.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.niro.web.dto.UnboxRecordC5ListingPageDTO;
import com.niro.web.dto.UnboxRecordDTO;
import com.niro.web.dto.UnboxRecordOcrResultDTO;
import com.niro.web.dto.UnboxRecordPageDTO;
import com.niro.web.dto.UnboxRecordSummaryDTO;
import com.niro.web.dto.param.UnboxRecordC5ListingQueryParam;
import com.niro.web.dto.param.UnboxRecordSaveParam;
import com.niro.web.service.UnboxRecordOcrService;
import com.niro.web.service.UnboxRecordService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;

/**
 * 开箱记录控制器
 */
@Tag(name = "开箱记录")
@RestController
@RequestMapping("/unbox/record")
@SaCheckLogin
@RequiredArgsConstructor
public class UnboxRecordController {

    private final UnboxRecordService unboxRecordService;
    private final UnboxRecordOcrService unboxRecordOcrService;

    @Operation(summary = "分页查询开箱记录")
    @GetMapping("/page")
    public Page<UnboxRecordPageDTO> page(@RequestParam(defaultValue = "1") Integer page,
                                         @RequestParam(defaultValue = "10") Integer pageSize,
                                         @RequestParam(required = false) LocalDate startDate,
                                         @RequestParam(required = false) LocalDate endDate) {
        Long userId = StpUtil.getLoginIdAsLong();
        return unboxRecordService.page(userId, page, pageSize, startDate, endDate);
    }

    @Operation(summary = "查询开箱记录汇总")
    @GetMapping("/summary")
    public UnboxRecordSummaryDTO summary(@RequestParam(required = false) LocalDate startDate,
                                         @RequestParam(required = false) LocalDate endDate) {
        Long userId = StpUtil.getLoginIdAsLong();
        return unboxRecordService.summary(userId, startDate, endDate);
    }

    @Operation(summary = "查询开箱记录列表")
    @GetMapping("/list")
    public List<UnboxRecordDTO> list(@RequestParam(required = false) LocalDate startDate,
                                     @RequestParam(required = false) LocalDate endDate) {
        Long userId = StpUtil.getLoginIdAsLong();
        return unboxRecordService.list(userId, startDate, endDate);
    }

    @Operation(summary = "查询开箱记录详情")
    @GetMapping("/{id}")
    public UnboxRecordDTO getDetail(@PathVariable Long id) {
        Long userId = StpUtil.getLoginIdAsLong();
        return unboxRecordService.getDetail(userId, id);
    }

    @Operation(summary = "新增开箱记录")
    @PostMapping
    public Long create(@RequestBody @Valid UnboxRecordSaveParam param) {
        Long userId = StpUtil.getLoginIdAsLong();
        return unboxRecordService.create(userId, param);
    }

    @Operation(summary = "更新开箱记录")
    @PutMapping("/{id}")
    public void update(@PathVariable Long id, @RequestBody @Valid UnboxRecordSaveParam param) {
        Long userId = StpUtil.getLoginIdAsLong();
        unboxRecordService.update(userId, id, param);
    }

    @Operation(summary = "开箱记录 OCR 识别")
    @PostMapping(value = "/ocr", consumes = "multipart/form-data")
    public UnboxRecordOcrResultDTO recognize(@RequestPart("file") MultipartFile file) {
        return unboxRecordOcrService.recognize(file);
    }

    @Operation(summary = "查询开箱记录 C5 在售列表")
    @PostMapping("/c5/listings")
    public UnboxRecordC5ListingPageDTO listC5Listings(@RequestBody @Valid UnboxRecordC5ListingQueryParam param) {
        Long userId = StpUtil.getLoginIdAsLong();
        return unboxRecordService.listC5Listings(userId, param);
    }

    @Operation(summary = "删除开箱记录")
    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        Long userId = StpUtil.getLoginIdAsLong();
        unboxRecordService.delete(userId, id);
    }
}
