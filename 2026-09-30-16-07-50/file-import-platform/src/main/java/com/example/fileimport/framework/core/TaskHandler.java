package com.example.fileimport.framework.core;

import java.io.BufferedReader;
import java.io.Writer;

/**
 * 业务处理器 SPI —— <b>新增任务时，真正需要你写代码的地方只有这里</b>。
 * <p>
 * 框架负责：找文件 → 加锁 → 静默期判断 → 建 Reader → 开流 → 批量提交 → 移文件 → 记日志 → 发告警。
 * 你只需负责：把一行文本变成一张数据库记录。
 * <p>
 * 这种"框架管流程、业务管转换"的分工，就是模板方法模式（Template Method）的落地：
 * {@link AbstractFileImportTask} 是模板，本接口是模板里的可变部分。
 *
 * <h3>两种实现方式，按数据量选</h3>
 * <ul>
 *   <li><b>方式 A（推荐，大文件必用）：流式逐行处理</b><br>
 *       实现 {@link #handleLine}，框架按行喂给你，你用 {@link BatchBuffer} 攒批。
 *       内存占用与文件大小无关（O(1)），10GB 文件也不会 OOM。</li>
 *   <li><b>方式 B（仅小文件）：整文件读写</b><br>
 *       实现 {@link #handleFile}，自己拿 Reader/Writer 随意发挥。
 *       不推荐用于大文件，因为容易出现"读到内存里再处理"的写法。</li>
 * </ul>
 * 两个方法都有默认实现：只实现其中一个即可，另一个不用管。
 *
 * @author platform-team
 * @since 1.0.0
 */
public interface TaskHandler {

    /**
     * 任务编码，必须与配置中的 taskCode 一致。
     * 框架据此把配置路由到正确的 Handler 实现，避免 if-else 大杂烩。
     */
    String taskCode();

    /**
     * 逐行处理入口（方式 A）。
     * <p>
     * <b>注意：该方法会被多个虚拟线程并发调用（每个文件一个线程），实现必须线程安全。</b>
     * 通常的做法是：所有可变状态都放在 {@link BatchBuffer} 里，
     * 而 BatchBuffer 由框架按"文件+批次"维度创建，天然隔离。
     *
     * @param lineNo  行号（从 1 开始，已扣除表头），用于报错定位
     * @param rawLine 原始行文本（未做 split，避免框架强加解析方式）
     * @param buffer  攒批缓冲区，攒够 batchSize 框架会自动 flush（提交数据库）
     * @param ctx     任务上下文，可读取 extras 里的业务参数
     * @throws Exception 抛出即视为该行处理失败；框架会记脏数据并继续，不会中断整个文件
     */
    default void handleLine(long lineNo, String rawLine, BatchBuffer buffer, TaskContext ctx) throws Exception {
        throw new UnsupportedOperationException(
                "Handler[" + taskCode() + "] 未实现 handleLine，请实现 handleLine 或 handleFile 之一");
    }

    /**
     * 整文件处理入口（方式 B），给需要特殊处理的场景留的口子。
     * <p>默认实现会退化为逐行读取并调用 {@link #handleLine}。
     *
     * @param reader 文件内容读取流（框架已按配置的编码构造好）
     * @param result 统计结果，可累加行数
     * @param ctx    任务上下文
     */
    default void handleFile(BufferedReader reader, TaskResult result, TaskContext ctx) throws Exception {
        throw new UnsupportedOperationException(
                "Handler[" + taskCode() + "] 未实现 handleFile");
    }

    /**
     * 是否使用方式 B（整文件处理）。
     * 返回 true 时框架走 {@link #handleFile}，false 走逐行 + 攒批。
     */
    default boolean useWholeFileMode() {
        return false;
    }

    /**
     * 文件处理成功后的业务回调（可选）。
     * 例如：写一条导入汇总记录、通知下游、调用对账接口。
     *
     * @param fileName 文件名
     * @param stat     该文件的处理统计（{@link FileStat}）
     * @param ctx      任务上下文
     */
    default void onFileSuccess(String fileName, FileStat stat, TaskContext ctx) {
        // 默认空实现
    }

    /**
     * 文件处理失败后的业务回调（可选）。
     * 通常用于发送告警、写入待人工处理队列。
     */
    default void onFileFailure(String fileName, Throwable cause, TaskContext ctx) {
        // 默认空实现
    }
}
