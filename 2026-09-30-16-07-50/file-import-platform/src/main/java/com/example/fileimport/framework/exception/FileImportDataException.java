package com.example.fileimport.framework.exception;

/**
 * 数据异常：字段超长、格式非法、唯一键冲突等。
 * <p><b>不可重试</b>。框架会立即将该文件移入错误目录并告警，
 * 避免无效重试浪费数据库连接和线程资源。
 */
public class FileImportDataException extends FileImportException {

    public FileImportDataException(String message) {
        super(message);
    }

    public FileImportDataException(String message, Throwable cause) {
        super(message, cause);
    }
}
