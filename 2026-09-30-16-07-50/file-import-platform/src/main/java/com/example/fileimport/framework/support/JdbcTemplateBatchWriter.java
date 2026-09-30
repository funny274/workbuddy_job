package com.example.fileimport.framework.support;

import com.example.fileimport.framework.core.TaskContext;
import com.example.fileimport.framework.exception.FileImportException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.SQLException;
import java.util.List;

/**
 * 基于 JdbcTemplate 的通用批量写入器。
 * <p>
 * <b>为什么用裸 JDBC Batch 而不用 MyBatis-Plus 的 saveBatch？</b>
 * <p>
 * MyBatis-Plus 的 {@code saveBatch} 本质也是 JDBC Batch，但它包了一层
 * SqlSession + 一级缓存，在超大写入量下有两个隐患：
 * <ol>
 *   <li>一级缓存持续膨胀，可能引发 OOM（需手动 {@code sqlSession.flushStatements()}）；</li>
 *   <li>多线程共享 SqlSessionTemplate 时行为不直观，排查问题成本高。</li>
 * </ol>
 * 对于"只管灌数据"这种场景，裸 JdbcTemplate + {@code rewriteBatchedStatements=true}
 * 是最直接、最可控、性能最好的方案。这也是国内大厂做批量导入的常见做法。
 * <p>
 * <b>关键性能开关（在 JDBC URL 上）</b>：
 * {@code rewriteBatchedStatements=true} —— 让 MySQL 驱动把 N 条 INSERT
 * 合并成一条多值 INSERT 发送，这是批量写入提速最关键的一步，
 * 不开这个参数批量写入的性能提升非常有限（甚至为负）。
 *
 * @author platform-team
 * @since 1.0.0
 */
@Slf4j
public class JdbcTemplateBatchWriter implements JdbcBatchWriter {

    private final JdbcTemplate jdbcTemplate;

    /** 建好的 INSERT 语句，避免每批都拼接字符串 */
    private final String insertSql;

    /** 事务管理器，用于控制"一批一事务" */
    private final org.springframework.transaction.support.TransactionTemplate transactionTemplate;

    /** 批次大小（仅用于 preferredBatchSize 提示） */
    private final int batchSize;

    public JdbcTemplateBatchWriter(JdbcTemplate jdbcTemplate,
                                   org.springframework.transaction.support.TransactionTemplate transactionTemplate,
                                   String insertSql,
                                   int batchSize) {
        this.jdbcTemplate = jdbcTemplate;
        this.transactionTemplate = transactionTemplate;
        this.insertSql = insertSql;
        this.batchSize = batchSize;
    }

    @Override
    public void write(List<?> rows) throws BatchWriteException {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        try {
            // 用 TransactionTemplate 显式控制事务边界：
            // 一批 = 一个事务。批太大则单次回滚代价高，批太小则事务开销大，
            // 所以 batchSize 是个需要根据实际压测调优的参数（默认 1000）。
            transactionTemplate.executeWithoutResult(status -> {
                jdbcTemplate.batchUpdate(insertSql, new RowBatchPreparedStatementSetter(rows));
            });
        } catch (Exception e) {
            throw translate(e);
        }
    }

    /**
     * 把底层异常翻译成带"可否重试"语义的业务异常。
     * <p>
     * 这个判断直接决定了系统的自我恢复能力：
     * <ul>
     *   <li>死锁、锁等待超时、连接中断、通信异常 → 可重试</li>
     *   <li>唯一键冲突、字段超长、非空约束、类型转换错误 → 不可重试（数据问题，重试无意义）</li>
     * </ul>
     * 对不可重试异常快速失败并落死信文件，避免无效重试拖垮线程池。
     */
    private BatchWriteException translate(Exception e) {
        Throwable root = rootCause(e);
        if (root instanceof SQLException sqlEx) {
            int errorCode = sqlEx.getErrorCode();
            String sqlState = sqlEx.getSQLState();
            // MySQL 常见可重试错误码
            boolean retryable = errorCode == 1213        // Deadlock found
                    || errorCode == 1205                  // Lock wait timeout exceeded
                    || errorCode == 1040                  // Too many connections
                    || errorCode == 2006                  // MySQL server has gone away
                    || errorCode == 2013                  // Lost connection during query
                    || "40001".equals(sqlState);          // 序列化失败（标准 SQLState）
            // 数据类错误明确标记为不可重试
            if (errorCode == 1062 || errorCode == 1406 || errorCode == 1048 || errorCode == 1366) {
                retryable = false;
            }
            return new BatchWriteException(
                    "批量写入失败 [errCode=" + errorCode + ", sqlState=" + sqlState + "] " + sqlEx.getMessage(),
                    sqlEx, retryable);
        }
        // 网络类异常统一按可重试处理
        String msg = root.getMessage() == null ? "" : root.getMessage();
        boolean networkIssue = msg.contains("Connection") || msg.contains("connect")
                || msg.contains("timeout") || msg.contains("Socket");
        return new BatchWriteException("批量写入失败: " + msg, root, networkIssue);
    }

    /** 递归取根异常，避免被层层包装的异常掩盖真实错误码 */
    private Throwable rootCause(Throwable t) {
        Throwable cur = t;
        int depth = 0;
        while (cur.getCause() != null && cur.getCause() != cur && depth++ < 20) {
            cur = cur.getCause();
        }
        return cur;
    }

    @Override
    public int preferredBatchSize() {
        return batchSize;
    }

    /**
     * 把 PO 列表设置到 PreparedStatement 上。
     * 具体映射由调用方传入的 {@code RowSetter} 决定，保持框架与业务模型解耦。
     */
    public static class RowBatchPreparedStatementSetter
            implements org.springframework.jdbc.core.BatchPreparedStatementSetter {

        private final List<?> rows;

        public RowBatchPreparedStatementSetter(List<?> rows) {
            this.rows = rows;
        }

        @Override
        public void setValues(java.sql.PreparedStatement ps, int i) throws SQLException {
            Object row = rows.get(i);
            if (!(row instanceof BindableRow bindable)) {
                throw new FileImportException(
                        "行对象未实现 BindableRow 接口，无法写入: " + row.getClass().getName());
            }
            bindable.bind(ps);
        }

        @Override
        public int getBatchSize() {
            return rows.size();
        }
    }
}
