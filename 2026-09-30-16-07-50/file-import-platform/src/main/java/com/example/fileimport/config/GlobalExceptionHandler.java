package com.example.fileimport.config;

import com.example.fileimport.framework.exception.FileImportDataException;
import com.example.fileimport.framework.exception.FileImportException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 全局异常处理。
 * <p>
 * 统一异常出口的价值：<b>不让堆栈信息泄漏给调用方，同时保证日志里有完整现场。</b>
 * 生产环境返回给前端/调用方的错误信息应简洁，详细堆栈只进日志。
 * 这也是等保/安全审计的常见要求。
 *
 * @author platform-team
 * @since 1.0.0
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 业务异常：返回 400 + 可读原因，不返回堆栈 */
    @ExceptionHandler({FileImportException.class, FileImportDataException.class})
    public ResponseEntity<Map<String, Object>> handleBusinessException(Exception e) {
        log.warn("业务异常: {}", e.getMessage());
        return build(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    /** 参数校验失败 */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException e) {
        log.warn("参数异常: {}", e.getMessage());
        return build(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    /**
     * 兜底异常处理。
     * <p><b>关键：这里必须打完整堆栈</b>，否则线上问题无从排查。
     * 但返回给调用方的只有一句通用提示。
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleUnknown(Exception e) {
        log.error("系统异常", e);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "系统内部错误，请联系运维并提供时间点");
    }

    private ResponseEntity<Map<String, Object>> build(HttpStatus status, String message) {
        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", LocalDateTime.now().toString());
        body.put("status", status.value());
        body.put("message", message);
        return ResponseEntity.status(status).body(body);
    }
}
