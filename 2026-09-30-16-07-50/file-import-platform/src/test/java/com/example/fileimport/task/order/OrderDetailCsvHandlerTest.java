package com.example.fileimport.task.order;

import com.example.fileimport.framework.core.BatchBuffer;
import com.example.fileimport.framework.core.TaskContext;
import com.example.fileimport.framework.support.JdbcBatchWriter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 订单 CSV 解析器单元测试。
 * <p>
 * 用内存实现替换 {@link JdbcBatchWriter}，因此<b>不依赖数据库</b>即可验证解析逻辑，
 * 这正是"把写入抽成接口"带来的可测试性收益（见 JdbcBatchWriter 的设计说明）。
 *
 * @author platform-team
 * @since 1.0.0
 */
class OrderDetailCsvHandlerTest {

    private final OrderDetailCsvHandler handler = new OrderDetailCsvHandler();

    /** 内存批量写入器：把待写入对象收集到 List，供断言检查 */
    private static class InMemoryWriter implements JdbcBatchWriter {
        final List<Object> rows = new ArrayList<>();

        @Override
        public void write(List<?> batch) {
            rows.addAll(batch);
        }
    }

    private TaskContext context() {
        Map<String, String> extras = new HashMap<>();
        extras.put("channelCode", "WEB");
        return TaskContext.builder()
                .taskCode(OrderDetailCsvHandler.TASK_CODE)
                .batchNo("20260930120000-test")
                .batchSize(10)
                .extras(extras)
                .charset(StandardCharsets.UTF_8)
                .build();
    }

    @Test
    @DisplayName("正常行：字段被正确解析并进入攒批缓冲")
    void shouldParseValidLine() throws Exception {
        InMemoryWriter writer = new InMemoryWriter();
        TaskContext ctx = context();
        BatchBuffer buffer = new BatchBuffer(10, writer, ctx);

        handler.handleLine(1L,
                "ORD001,C0001,P10086,3,19.90,59.70,2026-09-30 10:15:23",
                buffer, ctx);
        buffer.flush();

        assertEquals(1, writer.rows.size(), "应有 1 条记录入库");
    }

    @Test
    @DisplayName("列数不足：抛异常（由框架记为脏数据，不中断文件）")
    void shouldRejectTooFewColumns() {
        BatchBuffer buffer = new BatchBuffer(10, new InMemoryWriter(), context());
        TaskContext ctx = context();

        assertThrows(IllegalArgumentException.class,
                () -> handler.handleLine(1L, "ORD001,C0001", buffer, ctx));
    }

    @Test
    @DisplayName("订单号为空：抛异常")
    void shouldRejectBlankOrderNo() {
        BatchBuffer buffer = new BatchBuffer(10, new InMemoryWriter(), context());
        TaskContext ctx = context();

        assertThrows(IllegalArgumentException.class,
                () -> handler.handleLine(1L,
                        ",C0001,P10086,3,19.90,59.70,2026-09-30 10:15:23", buffer, ctx));
    }

    @Test
    @DisplayName("时间格式非法：抛数据异常")
    void shouldRejectInvalidDateTime() {
        BatchBuffer buffer = new BatchBuffer(10, new InMemoryWriter(), context());
        TaskContext ctx = context();

        assertThrows(Exception.class,
                () -> handler.handleLine(1L,
                        "ORD001,C0001,P10086,3,19.90,59.70,NOT_A_DATE", buffer, ctx));
    }

    @Test
    @DisplayName("金额用 BigDecimal 精确解析，不丢精度")
    void shouldParseAmountWithExactPrecision() {
        BigDecimal parsed = OrderDetailRowBinder.parseAmount(" 12345.6789 ", "amount");
        assertEquals(new BigDecimal("12345.6789"), parsed);
    }

    @Test
    @DisplayName("尾部空字段不应导致列数误判（split 的 -1 参数）")
    void shouldKeepTrailingEmptyColumns() throws Exception {
        InMemoryWriter writer = new InMemoryWriter();
        TaskContext ctx = context();
        BatchBuffer buffer = new BatchBuffer(10, writer, ctx);

        // 最后一列（order_time）为空，但列数仍为 7 —— 应能正常处理
        handler.handleLine(1L, "ORD001,C0001,P10086,3,19.90,59.70,", buffer, ctx);
        buffer.flush();

        assertEquals(1, writer.rows.size(), "尾部空字段应被保留为 7 列，正常入库");
    }

    @Test
    @DisplayName("攒批达到阈值时自动提交")
    void shouldAutoFlushWhenBatchFull() throws Exception {
        InMemoryWriter writer = new InMemoryWriter();
        TaskContext ctx = context();
        // 批大小设为 3：写入 3 行后应自动 flush 一次
        BatchBuffer buffer = new BatchBuffer(3, writer, ctx);

        for (int i = 0; i < 3; i++) {
            handler.handleLine(i + 1L,
                    "ORD00" + i + ",C0001,P10086,1,10.00,10.00,2026-09-30 10:15:23",
                    buffer, ctx);
        }

        assertEquals(3, writer.rows.size(), "攒够 3 条应自动提交");
        assertEquals(0, buffer.pendingSize(), "提交后缓冲区应清空");
    }
}
