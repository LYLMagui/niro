package com.niro.web.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.stp.StpUtil;
import com.niro.web.dto.UnboxRecordDTO;
import com.niro.web.dto.param.UnboxRecordSaveParam;
import com.niro.web.service.UnboxRecordService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

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

    @Operation(summary = "删除开箱记录")
    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        Long userId = StpUtil.getLoginIdAsLong();
        unboxRecordService.delete(userId, id);
    }
}
