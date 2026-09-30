package com.example.fileimport.framework.support;

import java.util.List;

/**
 * JDBC 批量写入器抽象。
 * <p>
 * 抽成接口而非直接用 MyBatis-Plus 的 {@code IService.saveBatch}，原因有三：
 * <ol>
 *   <li><b>可替换</b>：不同任务可能写不同的表，甚至写不同数据源（多数据源场景），
 *       各任务注入自己的实现即可，框架层不需要知道表结构。</li>
 *   <li><b>可测试</b>：单测时可以用内存实现替换，不依赖真实数据库。</li>
 *   <li><b>控制力</b>：批量写入的重试、超时、事务边界、rewriteBatchedStatements
 *       这些细节需要自己掌控，而不是被 ORM 的默认行为牵着走。</li>
 * </ol>
 *
 * @author platform-team
 * @since 1.0.0
 */
public interface JdbcBatchWriter {

    /**
     * 批量写入一批数据（同一事务内）。
     * <p>
     * <b>实现约定</b>：
     * <ul>
     *   <li>必须是原子的：要么全部成功，要么整批回滚。</li>
     *   <li>失败时应抛出 {@link BatchWriteException}，由上层决定重试策略。</li>
     *   <li>实现需保证线程安全（可能被多个虚拟线程同时调用）。</li>
     * </ul>
     *
     * @param rows 待写入数据，非空且 size > 0
     * @throws BatchWriteException 写入失败
     */
    void write(List<?> rows) throws BatchWriteException;

    /**
     * 该写入器建议的批次大小。
     * 框架在未显式配置时用它兜底。
     */
    default int preferredBatchSize() {
        return 1000;
    }

    /**
     * 批量写入异常。
     * <p>用 {@link #isRetryable()} 区分"重试有用"和"重试白搭"：
     * 死锁/连接中断可以重试，而唯一键冲突、字段超长重试一万次也没用——
     * 后者必须快速失败并落死信，否则会拖垮整个批次。
     */
    class BatchWriteException extends RuntimeException {

        /** 是否可通过重试恢复 */
        private final boolean retryable;

        public BatchWriteException(String message, Throwable cause, boolean retryable) {
            super(message, cause);
            this.retryable = retryable;
        }

        public BatchWriteException(String message, boolean retryable) {
            super(message);
            this.retryable = retryable;
        }

        public boolean isRetryable() {
            return retryable;
        }
    }
}
