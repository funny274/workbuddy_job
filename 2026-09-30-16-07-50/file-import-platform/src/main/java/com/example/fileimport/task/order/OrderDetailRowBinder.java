package com.example.fileimport.task.order;

import com.example.fileimport.framework.core.TaskContext;
import com.example.fileimport.framework.exception.FileImportDataException;
import com.example.fileimport.framework.support.BindableRow;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 订单明细的行绑定实现。
 * <p>
 * 把"如何把一个业务对象写进 PreparedStatement"这件具体的事从 PO 里拆出来，
 * 好处是：<b>同一个 PO 可以对应多张结构相似的表</b>（比如分表场景 t_order_detail_0/1/2），
 * 只需换一个 RowBinder 实现，PO 本身不用动。
 *
 * @author platform-team
 * @since 1.0.0
 */
@Slf4j
public class OrderDetailRowBinder implements BindableRow {

    /** 上游时间格式。若上游有多种格式，这里做成 List<DateTimeFormatter> 依次尝试 */
    private static final DateTimeFormatter DATE_TIME_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final OrderDetail detail;

    public OrderDetailRowBinder(OrderDetail detail) {
        this.detail = detail;
    }

    /**
     * 绑定字段到 PreparedStatement。
     * <p>
     * <b>关键实践</b>：
     * <ul>
     *   <li>下标从 1 开始，必须与 INSERT 语句中的 ? 顺序严格一致。
     *       <b>顺序错位是批量导入最高频的低级错误</b>，且往往"能跑但数据全错"，极难发现。
     *       建议把 INSERT 语句和本方法紧挨着写在同一个类里，对照着看。</li>
     *   <li>可空字段必须显式调用 {@code setNull(idx, Types.XXX)}，
     *       直接传 null 在部分驱动下会抛 NPE。</li>
     *   <li>{@code setBigDecimal} 而非 {@code setDouble}：金额字段绝不能用 double，
     *       浮点精度丢失会导致对账差异。</li>
     * </ul>
     */
    @Override
    public void bind(PreparedStatement ps) throws SQLException {
        ps.setString(1, detail.getOrderNo());
        ps.setString(2, detail.getCustomerNo());
        ps.setString(3, detail.getProductCode());

        // 数量：可空，显式设 NULL
        if (detail.getQuantity() != null) {
            ps.setInt(4, detail.getQuantity());
        } else {
            ps.setNull(4, Types.INTEGER);
        }

        // 金额：必须用 BigDecimal，不能用 double
        ps.setBigDecimal(5, detail.getUnitPrice() == null ? BigDecimal.ZERO : detail.getUnitPrice());
        ps.setBigDecimal(6, detail.getAmount() == null ? BigDecimal.ZERO : detail.getAmount());

        // 时间：用 Timestamp.valueOf 而非字符串拼接，避免时区与格式问题
        if (detail.getOrderTime() != null) {
            ps.setTimestamp(7, Timestamp.valueOf(detail.getOrderTime()));
        } else {
            ps.setNull(7, Types.TIMESTAMP);
        }

        ps.setString(8, detail.getChannelCode());
        ps.setString(9, detail.getBatchNo());
    }

    /**
     * 解析时间字符串，失败抛数据异常（不可重试类）。
     * <p>放在这里而不是 Handler 里，是因为"时间格式解析"是订单这个业务域的知识，
     * 应该归业务对象自己管。
     */
    public static LocalDateTime parseDateTime(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(text.trim(), DATE_TIME_FMT);
        } catch (Exception e) {
            throw new FileImportDataException("时间格式非法: [" + text + "]，期望 yyyy-MM-dd HH:mm:ss", e);
        }
    }

    /**
     * 解析金额，做基本校验。
     * <p>负金额在订单场景通常意味着异常，这里做防御性检查并记录。
     */
    public static BigDecimal parseAmount(String text, String fieldName) {
        if (text == null || text.isBlank()) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(text.trim());
        } catch (NumberFormatException e) {
            throw new FileImportDataException("字段[" + fieldName + "]金额格式非法: [" + text + "]", e);
        }
    }

    /** 供 TaskContext 使用：把上下文信息注入 PO，便于数据溯源 */
    public static void fillTraceInfo(OrderDetail detail, TaskContext ctx, String channelCode) {
        detail.setChannelCode(channelCode);
        detail.setBatchNo(ctx.getBatchNo());
    }
}
