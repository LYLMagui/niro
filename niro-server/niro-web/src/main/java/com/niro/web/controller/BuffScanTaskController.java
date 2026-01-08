package com.niro.web.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.niro.web.dto.BuffScanTaskDTO;
import com.niro.web.dto.param.BuffScanTaskParam;
import com.niro.web.dto.param.TaskQueryParam;
import com.niro.web.service.BuffScanTaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 扫货任务接口
 *
 * @author liyl
 * @since 2025-12-24
 */
@Tag(name = "扫货任务管理")
@RestController
@RequestMapping("/task")
@RequiredArgsConstructor
public class BuffScanTaskController {

    private final BuffScanTaskService buffScanTaskService;

    @PostMapping("/add")
    @Operation(summary = "创建任务")
    public void addTask(@RequestBody @Valid BuffScanTaskParam param) {
        buffScanTaskService.saveTask(param);
    }

    @PutMapping("/update")
    @Operation(summary = "更新任务")
    public void updateTask(@RequestBody @Valid BuffScanTaskParam param) {
        buffScanTaskService.updateTask(param);
    }

    @DeleteMapping("/delete/{id}")
    @Operation(summary = "删除任务")
    public void deleteTask(@PathVariable("id") Long id) {
        buffScanTaskService.deleteTask(id);
    }

    @PostMapping("/status/{id}/{status}")
    @Operation(summary = "更新任务状态 (0:停止 1:运行)")
    public void updateStatus(@PathVariable("id") Long id, @PathVariable("status") Integer status) {
        buffScanTaskService.updateStatus(id, status);
    }

    @GetMapping("/page")
    @Operation(summary = "分页查询任务")
    public Page<BuffScanTaskDTO> pageTask(@Valid TaskQueryParam param) {
        return buffScanTaskService.pageTask(param);
    }
}
