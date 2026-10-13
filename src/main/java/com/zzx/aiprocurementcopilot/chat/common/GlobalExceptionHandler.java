package com.zzx.aiprocurementcopilot.chat.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import tools.jackson.core.JacksonException;

import java.util.Map;

/**
 * 模型调用相关的统一异常出口。
 *
 * <p>写这个类之前先确认了两条事实（反编译 spring-ai-model-2.0.1 / Jackson 3.1.5 核对）：
 * <ul>
 *   <li>{@code BeanOutputConverter.convert()} 内部没有任何 try/catch，Jackson 的异常原样往外抛。
 *       所以 {@code entity()} 转换失败时抛的是 {@link JacksonException}（Jackson 3 里它是
 *       RuntimeException 的子类），<b>不是</b> IllegalStateException——
 *       IllegalStateException 只出现在 getJsonSchemaMap() 那条跟转换无关的路径上。</li>
 *   <li>record 紧凑构造器里抛的 IllegalArgumentException，会被 Jackson 包成
 *       ValueInstantiationException，原始消息保留在 cause 链最底层（所以下面要挖到最深处）。</li>
 * </ul>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** 模型输出无法转换成目标结构：JSON 不合法 / schema 校验重试用尽 / record 业务校验失败 */
    @ExceptionHandler(JacksonException.class)
    public ResponseEntity<Map<String, Object>> handleParseFailure(JacksonException e) {
        String reason = rootMessage(e);
        log.warn("结构化输出解析失败: {}", reason);
        // 422；Spring Framework 7 把这个常量的正式名改成了 UNPROCESSABLE_CONTENT（RFC 9110 的改名），
        // 旧的 UNPROCESSABLE_ENTITY 已标注过时
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_CONTENT)
                .body(Map.of(
                        "code", "PARSE_FAILED",
                        "message", "无法从描述中解析出完整需求：" + reason,
                        "hint", "请尽量包含：品类、数量、预算、期望到货时间"
                ));
    }

    /**
     * 请求参数本身的问题（少传参数、类型不对），是调用方的错，返回 400。
     * 特意放在这里：catch-all 的 Exception 处理器会连 Spring MVC 的绑定异常一起吞掉，
     * 不退化成"AI 服务不可用"。
     */
    @ExceptionHandler({MissingServletRequestParameterException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<Map<String, Object>> handleBadRequest(Exception e) {
        log.warn("请求参数不合法: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of(
                        "code", "BAD_REQUEST",
                        "message", "请求参数不合法：" + e.getMessage()
                ));
    }

    /** 模型服务异常（超时、限流、欠费、网络不通）以及其它未预期异常 */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleOther(Exception e) {
        log.error("调用模型服务失败", e);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of(
                        "code", "MODEL_UNAVAILABLE",
                        "message", "AI 服务暂时不可用，请稍后重试"
                ));
    }

    /** 取因果链最深处的消息：业务校验的真实原因（如"品类不能为空"）在最底层，Jackson 自己的消息在外层 */
    private static String rootMessage(Throwable e) {
        Throwable t = e;
        while (t.getCause() != null && t.getCause() != t) {
            t = t.getCause();
        }
        String message = t.getMessage() != null ? t.getMessage() : e.getMessage();
        return message == null ? "未知原因" : message.split("\n")[0];
    }
}
