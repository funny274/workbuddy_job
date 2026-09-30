package com.example.fileimport.framework.core;

import lombok.Builder;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.LongAdder;

/**
 * 一次任务执行的统计结果。
 * <p>
 * 用 {@link LongAdder} 而非 AtomicLong：多虚拟线程并发累加时，
 * LongAdder 的分段累加在高并发下吞吐显著优于 CAS 自旋的 AtomicLong。
 * 统计对象会写入任务执行日志表，供运维做趋势分析和容量规划。
 *
 * @author platform-team
 * @since 1.0.0
 */
@Data
public class TaskResult {

    /** 扫描到的文件总数 */
    private final LongAdder scannedFiles = new LongAdder();

    /** 成功处理的文件数 */
    private final LongAdder successFiles = new LongAdder();

    /** 失败的文件数 */
    private final LongAdder failedFiles = new LongAdder();

    /** 跳过（如未过静默期、被其他节点抢占）的文件数 */
    private final LongAdder skippedFiles = new LongAdder();

    /** 解析出的总行数 */
    private final LongAdder totalRows = new LongAdder();

    /** 成功入库的行数 */
    private final LongAdder insertedRows = new LongAdder();

    /** 解析失败被丢弃的行数（脏数据） */
    private final LongAdder dirtyRows = new LongAdder();

    /** 失败文件明细，用于告警时附上现场信息 */
    private final List<FileError> errors = new ArrayList<>();

    /** 耗时（毫秒） */
    private long costMillis;

    /** 整体是否成功：没有失败文件即视为成功 */
    public boolean isSuccess() {
        return failedFiles.sum() == 0;
    }

    /** 供日志输出的一行摘要 */
    public String summary() {
        return String.format(
                "扫描文件=%d, 成功=%d, 失败=%d, 跳过=%d | 解析行=%d, 入库行=%d, 脏数据=%d | 耗时=%dms",
                scannedFiles.sum(), successFiles.sum(), failedFiles.sum(), skippedFiles.sum(),
                totalRows.sum(), insertedRows.sum(), dirtyRows.sum(), costMillis);
    }

    /** 文件级错误信息 */
    @Data
    @Builder
    public static class FileError {
        private String fileName;
        private String stage;
        private String message;
    }

    /**
     * 线程安全地登记一个错误。
     * errors 是 ArrayList，必须加锁——这是常被忽略的细节，
     * 多线程直接 add 会导致数组越界或元素丢失。
     */
    public synchronized void addError(FileError error) {
        errors.add(error);
    }
}
