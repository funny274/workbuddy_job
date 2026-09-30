package com.example.fileimport.framework.exception;

/**
 * 文件导入平台统一业务异常基类。
 * <p>
 * 统一异常体系的价值：<b>让"异常类型"本身携带处理语义</b>。
 * 运维/告警系统看到 {@link FileImportRetryableException} 就知道会自动重试，不需要人工介入；
 * 看到 {@link FileImportDataException} 就知道要人去查数据。
 * 这比在异常消息里塞字符串判断要可靠得多。
 *
 * @author platform-team
 * @since 1.0.0
 */
public class FileImportException extends RuntimeException {

    public FileImportException(String message) {
        super(message);
    }

    public FileImportException(String message, Throwable cause) {
        super(message, cause);
    }
}
