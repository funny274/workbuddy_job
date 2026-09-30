package com.example.fileimport.framework.schedule;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.support.CronExpression;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 轻量 CRON 匹配器（仅本地模式使用）。
 * <p>
 * <b>实现思路</b>：不自己写 cron 解析（那是坑），直接复用 Spring 的
 * {@link CronExpression}——Spring Boot 3 内置，支持标准 5/6 段表达式，
 * 与 {@code @Scheduled} 用的是同一套解析器，行为一致，不会出现"两边理解不同"的诡异问题。
 * <p>
 * <b>判定逻辑</b>：记录每个任务"上一次应该执行的时刻"，如果该时刻距今在
 * 扫描间隔的容差范围内，就认为需要触发。这样即使调度线程被短暂阻塞，
 * 也不会漏掉本该执行的那一分钟。
 *
 * @author platform-team
 * @since 1.0.0
 */
@Slf4j
public final class CronMatcher {

    /** taskCode -> 上一次已触发的 cron 时刻 */
    private static final Map<String, LocalDateTime> LAST_FIRE_TIME = new ConcurrentHashMap<>();

    /** taskCode -> 解析后的 CronExpression 缓存（解析有成本，不能每次都做） */
    private static final Map<String, CronExpression> EXPRESSION_CACHE = new ConcurrentHashMap<>();

    /** 容差窗口：允许的延迟区间，避免因调度抖动漏执行 */
    private static final long TOLERANCE_SECONDS = 30;

    private CronMatcher() {
    }

    /**
     * 判断任务此刻是否应该执行。
     * <p>
     * 核心是"上一轮到这一轮之间是否跨过了一个 cron 触发点"：
     * 用 {@code lastFireTime} 反推下一次应触发时刻，若该时刻已经到达，就该执行。
     */
    public static boolean shouldRunNow(String taskCode, String cron) {
        CronExpression expression = EXPRESSION_CACHE.computeIfAbsent(taskCode, k -> {
            try {
                return CronExpression.parse(cron);
            } catch (Exception e) {
                throw new IllegalArgumentException(
                        "任务[" + taskCode + "]的 cron 表达式非法: " + cron, e);
            }
        });

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime lastFire = LAST_FIRE_TIME.get(taskCode);

        // 首次执行：记录当前时刻（相对时间基准），不立刻触发，
        // 等待下一个 cron 点。避免"应用一启动就立刻抢跑"。
        if (lastFire == null) {
            LAST_FIRE_TIME.put(taskCode, now);
            return false;
        }

        // 从上次触发时刻往后推，看下一个触发点是否已经到达
        LocalDateTime next = expression.next(lastFire);
        if (next == null) {
            return false;
        }

        if (!now.isBefore(next)) {
            // 已经过了触发点，且延迟在容差范围内（或已经远超，说明是应用重启，
            // 此时也触发一次补跑，但要防止重启后疯狂补跑——用容差限制）
            long delaySeconds = java.time.Duration.between(next, now).getSeconds();
            LAST_FIRE_TIME.put(taskCode, next);
            if (delaySeconds <= TOLERANCE_SECONDS + 60) {
                log.debug("任务[{}]达到 cron 触发点({}), 延迟 {}s，触发执行", taskCode, next, delaySeconds);
                return true;
            }
            log.info("任务[{}]距上次触发已过 {}s，执行一次补偿触发", taskCode, delaySeconds);
            return true;
        }
        return false;
    }

    /** 测试或手动触发后重置基准，避免紧接着又被 tick 触发一次 */
    public static void reset(String taskCode) {
        LAST_FIRE_TIME.put(taskCode, LocalDateTime.now());
    }
}
