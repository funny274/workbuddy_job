package com.example.fileimport.framework.core;

/**
 * 单文件处理统计。
 * <p>
 * 定义为顶层类型（而非嵌套在 {@link TaskHandler} 里）是有意为之：
 * 嵌套在接口中的 record 在 Lombok 注解处理器参与编译时，
 * 子类中引用 {@code TaskHandler.FileStat} 可能被误判为"未覆写超类方法"，
 * 报出难以定位的编译错误。提为顶层类型可彻底规避这类工具链问题，
 * 同时也让 import 更干净。
 *
 * @param totalRows    解析的总行数
 * @param insertedRows 成功入库的行数
 * @param dirtyRows    脏数据行数
 * @author platform-team
 * @since 1.0.0
 */
public record FileStat(long totalRows, long insertedRows, long dirtyRows) {
}
