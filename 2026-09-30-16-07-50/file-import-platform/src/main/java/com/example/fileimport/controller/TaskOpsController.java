package com.example.fileimport.controller;

import com.example.fileimport.framework.core.TaskContext;
import com.example.fileimport.framework.core.TaskRegistry;
import com.example.fileimport.framework.schedule.CronMatcher;
import com.example.fileimport.framework.schedule.LocalTaskScheduler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 运维接口 —— 生产环境可观测性与应急操作入口。
 * <p>
 * <b>为什么必须有这个类？</b>
 * 定时任务类系统最大的运维痛点是"看不见、动不了"：
 * 任务什么时候跑的？跑成功没有？现在能不能立刻跑一次？
 * 没有这几个接口，运维只能登机器看日志，效率极低。
 * <p>
 * <b>安全提醒</b>：生产环境请给本接口加鉴权（如 Spring Security + 内网访问限制），
 * 不要暴露到公网。手动触发是有副作用的操作。
 *
 * @author platform-team
 * @since 1.0.0
 */
@Slf4j
@RestController
@RequestMapping("/ops/tasks")
public class TaskOpsController {

    private final TaskRegistry taskRegistry;

    /**
     * 用 ObjectProvider 而非直接注入：本地模式下 LocalTaskScheduler 不存在，
     * 直接注入会导致启动失败。这是 Spring 里处理"可选依赖"的标准做法。
     */
    private final ObjectProvider<LocalTaskScheduler> localSchedulerProvider;

    public TaskOpsController(TaskRegistry taskRegistry,
                             ObjectProvider<LocalTaskScheduler> localSchedulerProvider) {
        this.taskRegistry = taskRegistry;
        this.localSchedulerProvider = localSchedulerProvider;
    }

    /** 查询所有已注册任务及其关键配置，用于运维巡检 */
    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> listTasks() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (String taskCode : taskRegistry.getAllTaskCodes()) {
            TaskContext ctx = taskRegistry.getContextTemplate(taskCode);
            Map<String, Object> item = new HashMap<>();
            item.put("taskCode", taskCode);
            item.put("taskName", ctx.getTaskName());
            item.put("cron", taskRegistry.getCron(taskCode));
            item.put("sourceDirs", ctx.getSourceDirs());
            item.put("filePatterns", ctx.getFilePatterns());
            item.put("backupDir", ctx.getBackupDir());
            item.put("concurrency", ctx.getConcurrency());
            item.put("batchSize", ctx.getBatchSize());
            item.put("retryTimes", ctx.getRetryTimes());
            LocalTaskScheduler scheduler = localSchedulerProvider.getIfAvailable();
            item.put("running", scheduler != null && scheduler.isRunning(taskCode));
            list.add(item);
        }
        return ResponseEntity.ok(list);
    }

    /**
     * 手动触发一次任务执行。
     * <p>排障场景：等待下一个 cron 点太久，或需要在发版前验证配置是否生效。
     * <p>注意：本地调度模式下有"防重叠执行"保护，任务正在跑时本次触发会被拒绝。
     */
    @PostMapping("/{taskCode}/trigger")
    public ResponseEntity<Map<String, Object>> trigger(@PathVariable String taskCode) {
        LocalTaskScheduler scheduler = localSchedulerProvider.getIfAvailable();
        if (scheduler == null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "当前为 elasticjob 模式，请通过 ElasticJob 管控台触发"));
        }
        if (taskRegistry.getTask(taskCode).isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "任务不存在: " + taskCode));
        }
        // 重置 cron 基准，避免手动触发后紧接着又被自动调度触发一次
        String cron = taskRegistry.getCron(taskCode);
        if (cron != null) {
            CronMatcher.reset(taskCode);
        }
        boolean accepted = scheduler.triggerAsync(taskCode);
        return ResponseEntity.ok(Map.of(
                "success", accepted,
                "message", accepted ? "任务已提交执行，请查看日志与 exec_log 表" : "任务正在运行中，本次触发被拒绝"));
    }
}
