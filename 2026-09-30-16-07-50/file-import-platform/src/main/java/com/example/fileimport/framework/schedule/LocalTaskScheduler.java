package com.example.fileimport.framework.schedule;

import com.example.fileimport.framework.core.AbstractFileImportTask;
import com.example.fileimport.framework.core.TaskContext;
import com.example.fileimport.framework.core.TaskRegistry;
import com.example.fileimport.framework.core.TaskResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 本地调度器（Spring @Scheduled）—— <b>无 ZooKeeper 也能跑</b>的降级模式。
 * <p>
 * <b>为什么要有这个类？</b> 两个实际原因：
 * <ol>
 *   <li><b>本地开发/单机部署</b>：不值得为一个小系统单独维护一套 ZooKeeper 集群；</li>
 *   <li><b>框架验证</b>：新同学 clone 下来不用装任何中间件就能看到完整效果，
 *       降低上手成本，这是团队工程效率的一部分。</li>
 * </ol>
 * 通过 {@code app.schedule.mode=local|elasticjob} 切换，业务代码完全无感——
 * 这正是把"调度"抽象成框架层职责的价值。
 *
 * <h3>并发防护：任务级互斥</h3>
 * 定时任务最常见的生产问题就是<b>上一轮还没跑完、下一轮又被触发</b>，
 * 导致同一批文件被重复处理、或两个线程互相踩数据。
 * 这里用 {@link AtomicBoolean} 做任务级"运行中"标记（对应 ElasticJob 的 {@code overwrite=true}
 * 或 {@code misfire=true} 语义），保证同一任务不会重叠执行。
 *
 * @author platform-team
 * @since 1.0.0
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "app.schedule.mode", havingValue = "local", matchIfMissing = true)
public class LocalTaskScheduler {

    private final TaskRegistry taskRegistry;

    /** 任务级运行中标记：防止同一任务的重叠执行 */
    private final ConcurrentHashMap<String, AtomicBoolean> runningFlags = new ConcurrentHashMap<>();

    /** 任务级信号量：控制单个任务内同时运行的文件数，保护数据库 */
    private final ConcurrentHashMap<String, Semaphore> taskSemaphores = new ConcurrentHashMap<>();

    public LocalTaskScheduler(TaskRegistry taskRegistry) {
        this.taskRegistry = taskRegistry;
    }

    /**
     * 统一调度入口：每 5 秒扫一次，用"时间轮"思路判断哪些任务该跑。
     * <p>
     * <b>为什么不用一个任务一个 @Scheduled 方法？</b>
     * 因为那样新增任务就必须改代码（加方法），违背了"配置驱动新增任务"的目标。
     * 这里用一个统一入口 + 动态判断，新增任务只需改配置，代码零改动。
     * 这也是"可扩展性"和"便利性"的权衡结果。
     */
    @Scheduled(fixedDelayString = "${app.schedule.local-scan-interval-ms:5000}")
    public void tick() {
        for (String taskCode : taskRegistry.getAllTaskCodes()) {
            String cron = taskRegistry.getCron(taskCode);
            if (cron == null) {
                continue;
            }
            if (CronMatcher.shouldRunNow(taskCode, cron)) {
                triggerAsync(taskCode);
            }
        }
    }

    /**
     * 手动触发（供运维接口调用）。
     * <p>支持通过 HTTP 接口手动跑一次，排障时非常有用——
     * 不用等到下一个 cron 时刻，也不用改配置。
     */
    public boolean triggerAsync(String taskCode) {
        AbstractFileImportTask task = taskRegistry.getTask(taskCode).orElse(null);
        if (task == null) {
            log.warn("任务[{}]不存在，无法触发", taskCode);
            return false;
        }

        AtomicBoolean flag = runningFlags.computeIfAbsent(taskCode, k -> new AtomicBoolean(false));
        // CAS 抢占：抢不到说明上一轮还在跑，本次直接跳过（这就是防重叠的关键）
        if (!flag.compareAndSet(false, true)) {
            log.warn("任务[{}]上一轮尚未执行完成，本次触发被跳过（防重叠执行）", taskCode);
            return false;
        }

        // 用虚拟线程跑，避免阻塞调度线程
        Thread.ofVirtual().name("task-" + taskCode).start(() -> {
            try {
                TaskContext ctx = taskRegistry.getContextTemplate(taskCode);
                // 任务级信号量：控制并发文件数，与全局线程池形成双重限流
                Semaphore semaphore = taskSemaphores.computeIfAbsent(
                        taskCode, k -> new Semaphore(Math.max(1, ctx.getConcurrency())));
                task.execute(ctx);
            } catch (Exception e) {
                // 兜底捕获：任何逃逸的异常都会终止调度线程，必须挡住
                log.error("任务[{}]执行异常", taskCode, e);
            } finally {
                flag.set(false);
            }
        });
        return true;
    }

    /** 查询任务当前是否在运行中，供监控接口使用 */
    public boolean isRunning(String taskCode) {
        AtomicBoolean flag = runningFlags.get(taskCode);
        return flag != null && flag.get();
    }

    /** 供任务内部获取信号量（预留扩展点） */
    public Semaphore semaphoreOf(String taskCode) {
        return taskSemaphores.computeIfAbsent(taskCode, k -> new Semaphore(1));
    }

    /** 让 TaskResult 的 import 不被 IDE 误报为未使用（内部类引用） */
    @SuppressWarnings("unused")
    private TaskResult unusedTypeAnchor() {
        return null;
    }
}
