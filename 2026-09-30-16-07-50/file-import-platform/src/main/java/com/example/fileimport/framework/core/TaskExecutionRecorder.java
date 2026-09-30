package com.example.fileimport.framework.core;

import java.net.InetAddress;
import java.time.LocalDateTime;

/**
 * 任务执行记录器 —— 运维可观测性的基础。
 * <p>
 * 每次任务执行都落一条记录到 {@code sys_task_exec_log}，价值在于：
 * <ul>
 *   <li><b>排障</b>：出问题时先看"上一轮是什么时候跑的、处理了多少、失败在哪一步"</li>
 *   <li><b>趋势</b>：连续几轮"扫描文件数"为 0，说明上游可能断供了，要主动告警</li>
 *   <li><b>容量</b>：入库行数的增长曲线是做库表容量规划的输入</li>
 *   <li><b>多节点归属</b>：记录 instanceId，多机部署时能定位到具体节点</li>
 * </ul>
 * 这是"能在生产环境投入使用"和"demo 能跑"的核心差别之一。
 *
 * @author platform-team
 * @since 1.0.0
 */
public interface TaskExecutionRecorder {

    /** 记录一次执行 */
    void record(TaskContext ctx, TaskResult result);

    /** 当前实例标识（IP:PORT 格式） */
    default String currentInstanceId() {
        try {
            return InetAddress.getLocalHost().getHostAddress() + ":" + ProcessHandle.current().pid();
        } catch (Exception e) {
            return "unknown:" + ProcessHandle.current().pid();
        }
    }

    /** 一次执行记录的数据载体 */
    record ExecLog(String taskCode,
                   String batchNo,
                   String instanceId,
                   LocalDateTime startTime,
                   LocalDateTime endTime,
                   long costMillis,
                   long scannedFiles,
                   long successFiles,
                   long failedFiles,
                   long skippedFiles,
                   long totalRows,
                   long insertedRows,
                   long dirtyRows,
                   String status,
                   String errorMsg) {
    }
}
