package com.zzx.aiprocurementcopilot.chat.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** 模型输出无法解析成目标结构 */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleParseFailure(IllegalStateException e) {
        log.warn("结构化输出解析失败: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(Map.of(
                        "code", "PARSE_FAILED",
                        "message", "无法从描述中解析出完整需求，请补充信息后重试",
                        "hint", "请尽量包含：品类、数量、预算、期望到货时间"
                ));
    }

    /** 模型服务异常（超时、限流、欠费） */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleOther(Exception e) {
        log.error("调用模型服务失败", e);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of(
                        "code", "MODEL_UNAVAILABLE",
                        "message", "AI 服务暂时不可用，请稍后重试"
                ));
    }
}
