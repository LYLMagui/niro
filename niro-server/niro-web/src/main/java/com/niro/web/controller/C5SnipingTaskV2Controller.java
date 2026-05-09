package com.niro.web.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.niro.web.constant.PermissionConstants;
import com.niro.web.dto.C5SnipingAccountDTO;
import com.niro.web.dto.C5SnipingBuyAttemptV2DTO;
import com.niro.web.dto.C5SnipingHitRecordV2DTO;
import com.niro.web.dto.C5SnipingTaskV2DTO;
import com.niro.web.dto.param.C5SnipingTaskV2QueryParam;
import com.niro.web.dto.param.C5SnipingTaskV2SaveParam;
import com.niro.web.service.C5SnipingAccountService;
import com.niro.web.service.C5SnipingTaskV2EventService;
import com.niro.web.service.C5SnipingTaskV2Service;

import java.util.List;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * C5 扫货 2.0 任务接口。
 */
@Tag(name = "C5扫货2.0任务管理")
@RestController
@RequestMapping("/api/c5/sniping/v2/tasks")
@RequiredArgsConstructor
@SaCheckLogin
public class C5SnipingTaskV2Controller {

    private final C5SnipingTaskV2Service c5SnipingTaskV2Service;
    private final C5SnipingTaskV2EventService c5SnipingTaskV2EventService;
    private final C5SnipingAccountService c5SnipingAccountService;

    /**
     * 订阅当前用户的任务运行态事件。
     *
     * @return SSE emitter
     */
    @GetMapping("/events")
    @Operation(summary = "订阅C5扫货2.0运行态事件")
    public SseEmitter subscribeEvents() {
        StpUtil.checkLogin();
        StpUtil.checkPermission(PermissionConstants.Task.SCAN_LIST);
        return c5SnipingTaskV2EventService.subscribe(StpUtil.getLoginIdAsLong());
    }

    /**
     * 创建任务，默认状态为 DRAFT。
     *
     * @param param 创建参数
     */
    @PostMapping
    @SaCheckPermission(PermissionConstants.C5SnipingTask.CREATE)
    @Operation(summary = "创建C5扫货2.0任务")
    public void createTask(@RequestBody @Valid C5SnipingTaskV2SaveParam param) {
        c5SnipingTaskV2Service.createTask(param);
    }

    /**
     * 编辑任务。
     *
     * @param id 任务 ID
     * @param param 编辑参数
     */
    @PutMapping("/{id}")
    @SaCheckPermission(PermissionConstants.C5SnipingTask.UPDATE)
    @Operation(summary = "编辑C5扫货2.0任务")
    public void updateTask(@Parameter(description = "任务ID") @PathVariable Long id,
                           @RequestBody @Valid C5SnipingTaskV2SaveParam param) {
        c5SnipingTaskV2Service.updateTask(id, param);
    }

    /**
     * 查询任务详情。
     *
     * @param id 任务 ID
     * @return 任务详情
     */
    @GetMapping("/{id}")
    @SaCheckPermission(PermissionConstants.C5SnipingTask.DETAIL)
    @Operation(summary = "查询C5扫货2.0任务详情")
    public C5SnipingTaskV2DTO getTask(@Parameter(description = "任务ID") @PathVariable Long id) {
        return c5SnipingTaskV2Service.getTask(id);
    }

    /**
     * 分页查询任务列表。
     *
     * @param param 查询参数
     * @return 任务分页
     */
    @GetMapping
    @SaCheckPermission(PermissionConstants.Task.SCAN_LIST)
    @Operation(summary = "分页查询C5扫货2.0任务")
    public Page<C5SnipingTaskV2DTO> pageTasks(@Valid C5SnipingTaskV2QueryParam param) {
        return c5SnipingTaskV2Service.pageTasks(param);
    }

    /**
     * 查询当前用户可用的 C5 扫货独立账号。
     *
     * @return C5 扫货账号列表
     */
    @GetMapping("/accounts")
    @SaCheckPermission(PermissionConstants.Task.SCAN_LIST)
    @Operation(summary = "查询当前用户可用的C5扫货账号")
    public List<C5SnipingAccountDTO> listAvailableC5Accounts() {
        return c5SnipingAccountService.listAvailableAccounts();
    }

    /**
     * 启用任务。
     *
     * @param id 任务 ID
     */
    @PostMapping("/{id}/enable")
    @SaCheckPermission(PermissionConstants.C5SnipingTask.ENABLE)
    @Operation(summary = "启用C5扫货2.0任务")
    public void enableTask(@Parameter(description = "任务ID") @PathVariable Long id) {
        c5SnipingTaskV2Service.enableTask(id);
    }

    /**
     * 停用任务。
     *
     * @param id 任务 ID
     */
    @PostMapping("/{id}/disable")
    @SaCheckPermission(PermissionConstants.C5SnipingTask.DISABLE)
    @Operation(summary = "停用C5扫货2.0任务")
    public void disableTask(@Parameter(description = "任务ID") @PathVariable Long id) {
        c5SnipingTaskV2Service.disableTask(id);
    }

    /**
     * 删除任务。
     *
     * @param id 任务 ID
     */
    @DeleteMapping("/{id}")
    @SaCheckPermission(PermissionConstants.C5SnipingTask.DELETE)
    @Operation(summary = "删除C5扫货2.0任务")
    public void deleteTask(@Parameter(description = "任务ID") @PathVariable Long id) {
        c5SnipingTaskV2Service.deleteTask(id);
    }

    /**
     * 查询任务命中明细。
     *
     * @param id 任务 ID
     * @param page 当前页
     * @param pageSize 每页数量
     * @return 命中明细分页
     */
    @GetMapping("/{id}/hits")
    @SaCheckPermission(PermissionConstants.C5SnipingTask.DETAIL)
    @Operation(summary = "查询C5扫货2.0命中明细")
    public Page<C5SnipingHitRecordV2DTO> pageHitRecords(@Parameter(description = "任务ID") @PathVariable Long id,
                                                        @RequestParam(defaultValue = "1") Long page,
                                                        @RequestParam(defaultValue = "10") Long pageSize) {
        return c5SnipingTaskV2Service.pageHitRecords(id, page, pageSize);
    }

    /**
     * 查询任务下单尝试明细。
     *
     * @param id 任务 ID
     * @param page 当前页
     * @param pageSize 每页数量
     * @return 下单尝试分页
     */
    @GetMapping("/{id}/buy-attempts")
    @SaCheckPermission(PermissionConstants.C5SnipingTask.DETAIL)
    @Operation(summary = "查询C5扫货2.0下单尝试")
    public Page<C5SnipingBuyAttemptV2DTO> pageBuyAttempts(@Parameter(description = "任务ID") @PathVariable Long id,
                                                          @RequestParam(defaultValue = "1") Long page,
                                                          @RequestParam(defaultValue = "10") Long pageSize) {
        return c5SnipingTaskV2Service.pageBuyAttempts(id, page, pageSize);
    }
}
