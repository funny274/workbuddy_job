package com.example.fileimport.framework.exception;

/**
 * 可重试异常：数据库瞬断、死锁、网络抖动等。
 * <p>框架会自动按指数退避重试，无需人工介入。
 */
public class FileImportRetryableException extends FileImportException {

    public FileImportRetryableException(String message, Throwable cause) {
        super(message, cause);
    }

    public FileImportRetryableException(String message) {
        super(message);
    }
}
