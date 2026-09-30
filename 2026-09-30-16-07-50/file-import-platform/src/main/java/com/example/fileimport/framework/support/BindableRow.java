package com.example.fileimport.framework.support;

import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * 可绑定到 PreparedStatement 的行对象。
 * <p>
 * 框架不关心你的 PO 长什么样，只要求你"会把自己填进 PS"。
 * 这样框架免于反射（反射在百万级写入下开销可观），
 * 又保持了与业务模型的解耦——典型的"约定优于配置"。
 *
 * <h3>为什么不用反射自动映射？</h3>
 * 反射 + 注解在 100 万行级别的写入中，字段读取的额外开销可达数十毫秒到数百毫秒，
 * 且出错时堆栈难读。手写 bind 虽然多打几行字，但可读、可调试、零反射开销——
 * 生产环境的批量导入通常都这么写。
 *
 * <h3>示例</h3>
 * <pre>{@code
 * public class OrderDetailPO implements BindableRow {
 *     private String orderNo;
 *     private BigDecimal amount;
 *
 *     @Override
 *     public void bind(PreparedStatement ps) throws SQLException {
 *         ps.setString(1, orderNo);
 *         ps.setBigDecimal(2, amount);
 *     }
 * }
 * }</pre>
 *
 * @author platform-team
 * @since 1.0.0
 */
@FunctionalInterface
public interface BindableRow {

    /**
     * 将当前对象绑定到 PreparedStatement。
     * <p><b>注意</b>：下标从 1 开始，顺序必须与 INSERT 语句的占位符一一对应。
     *
     * @param ps 由框架提供，调用方不要 close / execute
     */
    void bind(PreparedStatement ps) throws SQLException;
}
