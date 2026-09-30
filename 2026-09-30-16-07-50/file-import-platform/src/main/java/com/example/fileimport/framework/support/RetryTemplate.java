package com.example.fileimport.framework.support;

import com.example.fileimport.framework.core.TaskContext;
import com.example.fileimport.framework.exception.FileImportException;
import com.example.fileimport.framework.exception.FileImportRetryableException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.Callable;
import java.util.function.Predicate;

/**
 * 重试模板 —— 带指数退避 + 随机抖动的重试器。
 * <p>
 * <b>为什么不直接用 Spring Retry？</b> 当然可以用，但自己实现只要 50 行，
 * 且能把"指数退避 + 抖动 + 不重试白名单"这三个关键点讲清楚。
 * 生产环境的重试如果少了这三样，往往比不重试更糟：
 *
 * <h3>指数退避（Exponential Backoff）</h3>
 * 立即重试对"数据库正在恢复"这类故障毫无意义，只会加剧拥塞。
 * 间隔按 1s → 2s → 4s → 8s 递增，给下游恢复的时间窗口。
 *
 * <h3>抖动（Jitter）—— 最容易被忽略但最重要</h3>
 * 如果 100 个线程同时失败、同时重试，会形成"重试风暴"（thundering herd），
 * 在整秒时刻再次把下游打垮。加随机抖动把重试时间打散，是最廉价的保护。
 *
 * <h3>不重试白名单</h3>
 * 数据格式错误重试一万次也不会变对。对不可重试异常快速失败，
 * 节省出来的连接和线程可以处理真正能成功的数据。
 *
 * @author platform-team
 * @since 1.0.0
 */
@Slf4j
@Component
public class RetryTemplate {

    /** 抖动系数：实际间隔在 [base * 0.5, base * 1.5) 之间随机 */
    private static final double JITTER_FACTOR = 0.5;

    /**
     * 执行带重试的操作。
     *
     * @param ctx       任务上下文（提供 retryTimes / retryIntervalMillis）
     * @param action    业务逻辑
     * @param retryable 判断异常是否值得重试的谓词
     * @param <T>       返回值类型
     * @return 业务逻辑的返回值
     * @throws Exception 重试耗尽后仍失败，抛出最后一次的异常
     */
    public <T> T execute(TaskContext ctx, Callable<T> action, Predicate<Exception> retryable)
            throws Exception {

        int maxRetry = Math.max(0, ctx.getRetryTimes());
        long baseInterval = Math.max(100L, ctx.getRetryIntervalMillis());

        Exception lastError = null;

        for (int attempt = 0; attempt <= maxRetry; attempt++) {
            try {
                return action.call();
            } catch (Exception e) {
                lastError = e;

                boolean canRetry = retryable.test(e);
                boolean isLastAttempt = attempt == maxRetry;

                if (!canRetry) {
                    // 不可重试：立即失败，不浪费时间
                    log.warn("任务[{}]遇到不可重试异常，放弃重试: {}", ctx.getTaskCode(), e.getMessage());
                    throw e;
                }
                if (isLastAttempt) {
                    log.error("任务[{}]重试 {} 次后仍然失败", ctx.getTaskCode(), maxRetry);
                    break;
                }

                // 计算退避间隔并叠加抖动
                long backoff = (long) (baseInterval * Math.pow(2, attempt));
                long jitter = (long) (backoff * JITTER_FACTOR * (Math.random() * 2 - 1));
                long sleepMillis = Math.max(100L, backoff + jitter);

                log.warn("任务[{}]第 {} 次尝试失败，{}ms 后重试（共 {} 次）: {}",
                        ctx.getTaskCode(), attempt + 1, sleepMillis, maxRetry, e.getMessage());

                try {
                    Thread.sleep(sleepMillis);
                } catch (InterruptedException ie) {
                    // 恢复中断标志，符合 Java 并发规范
                    Thread.currentThread().interrupt();
                    throw new FileImportException("重试等待被中断", ie);
                }
            }
        }

        throw new FileImportRetryableException(
                "重试 " + maxRetry + " 次后仍失败: " + (lastError == null ? "unknown" : lastError.getMessage()),
                lastError);
    }
}
