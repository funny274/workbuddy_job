package com.example.fileimport.task.order;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单明细 PO —— 对应表 {@code t_order_detail}。
 * <p>
 * <b>注意</b>：这里没有用 Lombok 的 {@code @Data} 去自动生成 bind 逻辑，
 * 而是显式实现 {@link com.example.fileimport.framework.support.BindableRow}。
 * 原因见该接口的注释：批量写入场景下，显式绑定比反射映射快得多，且出错时堆栈清晰。
 *
 * @author platform-team
 * @since 1.0.0
 */
@Data
public class OrderDetail {

    /** 订单号 */
    private String orderNo;

    /** 客户号 */
    private String customerNo;

    /** 商品编码 */
    private String productCode;

    /** 数量 */
    private Integer quantity;

    /** 单价 */
    private BigDecimal unitPrice;

    /** 订单金额 */
    private BigDecimal amount;

    /** 订单时间（上游给的是 yyyy-MM-dd HH:mm:ss 字符串） */
    private LocalDateTime orderTime;

    /** 渠道号，来自任务配置的 extras，用于区分数据来源 */
    private String channelCode;

    /** 来源文件批次号，便于数据溯源（哪条数据来自哪个文件） */
    private String batchNo;
}
