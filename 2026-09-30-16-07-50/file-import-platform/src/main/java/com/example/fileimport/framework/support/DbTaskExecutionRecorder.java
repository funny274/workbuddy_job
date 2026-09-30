package com.example.fileimport.framework.support;

import com.example.fileimport.framework.core.TaskContext;
import com.example.fileimport.framework.core.TaskExecutionRecorder;
import com.example.fileimport.framework.core.TaskResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 基于数据库的任务执行记录器。
 * <p>
 * 落库表 {@code sys_task_exec_log}。用 JdbcTemplate 直接写而非走 ORM，
 * 因为这是一条"运维日志"性质的数据，追求写入快速、字段固定，不需要 ORM 的灵活性。
 * <p>
 * <b>重要设计：记录失败不能影响主流程。</b>
 * 如果日志表写不进去（磁盘满、表被锁），整批文件处理不应该因此失败——
 * 所以我们捕获所有异常并降级为本地日志。这类"非核心链路"的隔离很容易被忽略，
 * 但恰恰是系统稳定性的体现。
 *
 * @author platform-team
 * @since 1.0.0
 */
@Slf4j
@Component
public class DbTaskExecutionRecorder implements TaskExecutionRecorder {

    private static final String INSERT_SQL = """
            INSERT INTO sys_task_exec_log
              (task_code, batch_no, instance_id, start_time, end_time, cost_millis,
               scanned_files, success_files, failed_files, skipped_files,
               total_rows, inserted_rows, dirty_rows, status, error_msg)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            """;

    private final JdbcTemplate jdbcTemplate;

    public DbTaskExecutionRecorder(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void record(TaskContext ctx, TaskResult result) {
        try {
            String errorMsg = buildErrorMsg(result);
            jdbcTemplate.update(INSERT_SQL,
                    ctx.getTaskCode(),
                    ctx.getBatchNo(),
                    ctx.getInstanceId(),
                    ctx.getStartTime() == null ? LocalDateTime.now() : ctx.getStartTime(),
                    LocalDateTime.now(),
                    result.getCostMillis(),
                    result.getScannedFiles().sum(),
                    result.getSuccessFiles().sum(),
                    result.getFailedFiles().sum(),
                    result.getSkippedFiles().sum(),
                    result.getTotalRows().sum(),
                    result.getInsertedRows().sum(),
                    result.getDirtyRows().sum(),
                    result.isSuccess() ? "SUCCESS" : "FAILED",
                    // 字段长度限制，超长会插入失败——务必截断
                    truncate(errorMsg, 2000));
        } catch (Exception e) {
            // 降级：日志记录失败不影响业务，只打本地日志
            log.error("写入任务执行日志失败（已降级，不影响主流程）: task={}, batch={}",
                    ctx.getTaskCode(), ctx.getBatchNo(), e);
        }
    }

    /** 把失败明细汇总成一段可读文本，方便运维直接在库里看到原因 */
    private String buildErrorMsg(TaskResult result) {
        if (result.getErrors().isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        int limit = Math.min(result.getErrors().size(), 10);
        for (int i = 0; i < limit; i++) {
            TaskResult.FileError err = result.getErrors().get(i);
            sb.append('[').append(err.getStage()).append("] ")
                    .append(err.getFileName()).append(": ")
                    .append(err.getMessage()).append("; ");
        }
        if (result.getErrors().size() > limit) {
            sb.append("... 共 ").append(result.getErrors().size()).append(" 个错误");
        }
        return sb.toString();
    }

    private String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() > max ? s.substring(0, max) : s;
    }
}
