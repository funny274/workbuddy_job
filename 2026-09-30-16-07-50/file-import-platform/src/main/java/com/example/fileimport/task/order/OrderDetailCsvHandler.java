package com.example.fileimport.task.order;

import com.example.fileimport.framework.core.BatchBuffer;
import com.example.fileimport.framework.core.TaskContext;
import com.example.fileimport.framework.core.TaskHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * ==========================================================================
 * <b>【模板】订单明细 CSV 导入任务 —— 新增任务请照着这个文件改</b>
 * ==========================================================================
 *
 * <p>这是本框架的"参考实现"。写一个新任务时，把本文件复制一份，改以下几处即可：
 * <ol>
 *   <li>{@link #taskCode()} 的返回值 → 新任务编码（需与 yml 配置一致）</li>
 *   <li>{@link #handleLine} 里的解析逻辑 → 新文件的字段格式</li>
 *   <li>对应的 PO 与 RowBinder → 新表结构</li>
 *   <li>增加 yml 配置段</li>
 * </ol>
 * 其余全部（扫描、限流、重试、幂等、归档、日志、告警）由框架自动提供，不用重写。
 *
 * <h3>输入文件格式（CSV，UTF-8，首行表头）</h3>
 * <pre>
 * order_no,customer_no,product_code,quantity,unit_price,amount,order_time
 * ORD20260930001,C0001,P10086,3,19.90,59.70,2026-09-30 10:15:23
 * ORD20260930002,C0002,P10087,1,299.00,299.00,2026-09-30 10:16:01
 * </pre>
 *
 * <h3>为什么用 {@code split(',')} 而不是 commons-csv 解析器？</h3>
 * 如果字段里<b>不含引号包裹的逗号</b>（大多数国内系统的清单文件都是这种简单格式），
 * {@code String.split} 是最快的选择——它不分配对象、不建状态机。
 * 而 commons-csv 在大文件下每行都要走状态机解析，CPU 开销明显更高。
 * <p>
 * <b>但如果字段里可能有引号包裹的逗号或换行</b>，必须换成 CSV 解析器，
 * 否则字段会被切错——这是典型的"性能与正确性"权衡，由具体数据格式决定。
 * 需要严格 CSV 解析时，在 {@code handleLine} 里改用 {@code CSVFormat.DEFAULT.parse(...)} 即可。
 *
 * @author platform-team
 * @since 1.0.0
 */
@Slf4j
@Component
public class OrderDetailCsvHandler implements TaskHandler {

    /** 任务编码，必须与 application.yml 中 app.task.tasks 下的 key 一致 */
    public static final String TASK_CODE = "ORDER_DETAIL_IMPORT";

    /** 期望的字段数，用于格式校验 */
    private static final int EXPECTED_COLUMNS = 7;

    @Override
    public String taskCode() {
        return TASK_CODE;
    }

    /**
     * 逐行处理 —— 本框架的核心扩展点。
     * <p>
     * <b>并发说明</b>：本方法会被多个虚拟线程并发调用（每个文件一个线程），
     * 所以这里<b>不能有任何实例可变状态</b>。
     * 所有随行变化的状态都放在参数 {@code buffer} 里，天然线程隔离。
     *
     * @param lineNo  行号（已扣除表头），用于错误定位
     * @param rawLine 原始行，未做分隔符切分
     * @param buffer  攒批缓冲，攒够 batchSize 自动提交数据库
     * @param ctx     任务上下文，可读 extras 中的业务参数
     */
    @Override
    public void handleLine(long lineNo, String rawLine, BatchBuffer buffer, TaskContext ctx)
            throws Exception {
        // ---- 1. 切分字段 ----
        // 用 -1 参数保留尾部空字段：默认的 split(",") 会丢弃尾部空串，
        // 导致"最后一列缺失"的行被误判为列数不足。这是非常隐蔽的坑。
        String[] cols = rawLine.split(",", -1);

        if (cols.length < EXPECTED_COLUMNS) {
            // 抛异常即可，框架会记为脏数据并继续处理下一行，不会中断整个文件
            throw new IllegalArgumentException(
                    "列数不足: 期望 " + EXPECTED_COLUMNS + ", 实际 " + cols.length
                            + ", 内容=[" + abbreviate(rawLine) + "]");
        }

        // ---- 2. 组装业务对象 ----
        OrderDetail detail = new OrderDetail();
        detail.setOrderNo(trim(cols[0]));
        detail.setCustomerNo(trim(cols[1]));
        detail.setProductCode(trim(cols[2]));
        detail.setQuantity(parseInt(cols[3], "quantity"));
        detail.setUnitPrice(OrderDetailRowBinder.parseAmount(cols[4], "unit_price"));
        detail.setAmount(OrderDetailRowBinder.parseAmount(cols[5], "amount"));
        detail.setOrderTime(OrderDetailRowBinder.parseDateTime(cols[6]));

        // ---- 3. 业务校验（关键字段非空）----
        // 校验放在这里而不是数据库层，是为了让错误信息带上行号，定位更快。
        // 如果靠数据库非空约束报错，你只知道"有个空值"，不知道是哪一行。
        if (isBlank(detail.getOrderNo())) {
            throw new IllegalArgumentException("order_no 为空");
        }
        if (isBlank(detail.getProductCode())) {
            throw new IllegalArgumentException("product_code 为空");
        }

        // ---- 4. 业务规则校验示例：金额自洽性检查 ----
        // 数量 × 单价 与 amount 差异超过阈值时告警，但不阻断入库（业务选择）。
        // 改成本模板时，这类校验是体现"业务理解"的地方。
        BigDecimal expected = detail.getUnitPrice().multiply(BigDecimal.valueOf(
                detail.getQuantity() == null ? 0 : detail.getQuantity()));
        if (detail.getAmount().subtract(expected).abs().compareTo(new BigDecimal("1.00")) > 0) {
            // 只打日志不抛异常：金额小差异在实际业务里很常见（优惠、四舍五入），
            // 直接判失败会导致大量正常数据进不去。
            log.warn("任务[{}]第 {} 行金额不自洽: orderNo={}, 期望={}, 实际={}",
                    ctx.getTaskCode(), lineNo, detail.getOrderNo(), expected, detail.getAmount());
        }

        // ---- 5. 注入溯源信息 ----
        // 渠道号从配置的 extras 里读，这样同一个 Handler 可以服务多个渠道的数据，
        // 只需在 yml 里配不同的 extras.channelCode。
        String channelCode = ctx.getExtra("channelCode", "DEFAULT");
        OrderDetailRowBinder.fillTraceInfo(detail, ctx, channelCode);

        // ---- 6. 加入攒批缓冲区 ----
        // add 内部会在攒够 batchSize 时自动提交；文件末尾的余量由框架负责 flush。
        buffer.add(new OrderDetailRowBinder(detail));
    }

    // ==================== 解析工具方法 ====================
    // 放在 Handler 内部而非抽成公共类，是因为这些解析规则可能与具体业务域强相关。
    // 若多个任务确有共性，再上提到公共工具类。

    /** 整数字段解析，失败抛出可定位的异常 */
    private Integer parseInt(String text, String fieldName) {
        String v = trim(text);
        if (v.isEmpty()) {
            return null;
        }
        try {
            return Integer.valueOf(v);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("字段[" + fieldName + "]非整数: [" + v + "]");
        }
    }

    private String trim(String s) {
        return s == null ? "" : s.trim();
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    /** 截断长文本用于日志，避免一行脏数据把日志撑爆 */
    private String abbreviate(String s) {
        if (s == null) {
            return "";
        }
        return s.length() > 100 ? s.substring(0, 100) + "..." : s;
    }
}
