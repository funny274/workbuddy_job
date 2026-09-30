package com.example.fileimport.framework.core;

import com.example.fileimport.framework.support.JdbcBatchWriter;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

/**
 * 批次缓冲区 —— 数据库写入压力的核心调节阀。
 * <p>
 * <b>为什么需要它？</b><br>
 * 如果每读一行就 insert 一次，100 万行就是 100 万次网络往返 + 100 万次事务，
 * 数据库直接被打死。攒批后 100 万行变成 500~2000 次批量提交，吞吐可提升两个数量级。
 * <p>
 * <b>线程模型</b>：每个文件、每个处理线程独享一个 BatchBuffer 实例，
 * 因此内部 List 不需要并发容器，省掉同步开销（这是"线程封闭"的典型应用）。
 * <p>
 * <b>使用姿势</b>（在 Handler 里）：
 * <pre>{@code
 * public void handleLine(long lineNo, String rawLine, BatchBuffer buffer, TaskContext ctx) {
 *     OrderDetail po = parse(rawLine);
 *     buffer.add(po);          // 攒批，攒够 batchSize 自动提交
 * }
 * }</pre>
 * 文件处理结束时框架会自动调用 {@link #flush()} 把尾巴数据提交掉。
 *
 * @author platform-team
 * @since 1.0.0
 */
@Slf4j
public class BatchBuffer {

    /** 攒批阈值，达到即提交 */
    private final int batchSize;

    /** 底层批量写入器（封装了 JDBC Batch + 事务 + 重试） */
    private final JdbcBatchWriter writer;

    /** 待提交数据 */
    private final List<Object> pending;

    /** 已提交总条数 */
    @Getter
    private long committedCount;

    /** 本次处理里被判定为脏数据、未入库的条数 */
    @Getter
    private long dirtyCount;

    /** 任务上下文，便于写入时按任务维度打标 */
    private final TaskContext ctx;

    public BatchBuffer(int batchSize, JdbcBatchWriter writer, TaskContext ctx) {
        this.batchSize = Math.max(1, batchSize);
        this.writer = writer;
        this.ctx = ctx;
        // 预分配容量，避免扩容带来的数组拷贝
        this.pending = new ArrayList<>(this.batchSize);
    }

    /**
     * 加入一条待入库记录并检查是否需要提交。
     * <p>
     * 这里刻意不在 add 里捕获异常：写入失败属于需要上层感知的严重问题，
     * 由 Handler 的异常处理逻辑统一决定是重试还是标记脏数据。
     *
     * @param po 实体对象（MyBatis-Plus 的 Entity 或自定义 PO）
     * @throws Exception 数据库写入异常
     */
    public void add(Object po) throws Exception {
        pending.add(po);
        if (pending.size() >= batchSize) {
            flush();
        }
    }

    /**
     * 强制提交当前攒批数据。
     * <p>文件尾部不足一个 batchSize 的数据，以及 Handler 主动想提前落库时调用。
     */
    public void flush() throws Exception {
        if (pending.isEmpty()) {
            return;
        }
        int size = pending.size();
        long start = System.currentTimeMillis();
        // 交给 writer 做真正的批量写入（含事务与重试）
        writer.write(pending);
        committedCount += size;
        pending.clear();

        long cost = System.currentTimeMillis() - start;
        // 慢批次预警：单批超 3 秒说明 DB 有压力，运维可据此告警
        if (cost > 3000L) {
            log.warn("[{}] 批次提交偏慢: rows={}, cost={}ms，请关注数据库负载",
                    ctx.getTaskCode(), size, cost);
        } else if (log.isDebugEnabled()) {
            log.debug("[{}] 批次提交完成: rows={}, cost={}ms", ctx.getTaskCode(), size, cost);
        }
    }

    /**
     * 标记一条脏数据（解析失败/格式非法），不入库但计数，
     * 便于结束后判断"脏数据率"是否超阈值而告警。
     */
    public void markDirty() {
        dirtyCount++;
    }

    /** 当前攒批中未提交的条数 */
    public int pendingSize() {
        return pending.size();
    }

    /** 解析出当前攒批数据的快照，供极端场景下（如批次失败）落盘留痕 */
    public List<Object> snapshotPending() {
        return new ArrayList<>(pending);
    }
}
