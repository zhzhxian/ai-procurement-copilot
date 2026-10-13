package com.zzx.aiprocurementcopilot.chat.model;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import org.jspecify.annotations.Nullable;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.List;

/**
 * 采购需求（从用户的自然语言描述中提取）
 *
 * <p>字段标注的三条规则（均经实测验证）：
 * <ul>
 *   <li>不带 {@link Nullable} 的组件会进入 JSON Schema 的 required 列表——模型少给这个字段，
 *       validateSchema() 会拦下来并触发自纠正重试</li>
 *   <li>带 {@link Nullable} 的组件不要求出现，但"省略这个键"才合法；显式给 null 仍会被 schema 拒绝
 *       （属性类型是 string/number，null 不属于其中）</li>
 *   <li>{@link JsonPropertyDescription} 会写进 schema 的 description，让模型直接看到字段含义，
 *       比把说明堆在 system prompt 里更贴近约束发生的地方</li>
 * </ul>
 */
public record PurchaseRequirement(
        @JsonPropertyDescription("品类，如：笔记本电脑") String category,
        @JsonPropertyDescription("采购数量，整数") Integer quantity,
        @Nullable @JsonPropertyDescription("单件预算上限（元）") BigDecimal unitBudget,
        @Nullable @JsonPropertyDescription("期望交付日期，格式 yyyy-MM-dd") String deliveryDate,
        @Nullable @JsonPropertyDescription("使用场景") String usageScenario,
        @Nullable @JsonPropertyDescription("必须满足的条件") List<String> mustHave
) {

    /**
     * 紧凑构造器是 record 唯一的初始化入口：正常 new、Jackson 反序列化、Java 反序列化
     * 都会经过这里。模型的输出是不可信输入，在对象诞生这一刻就拦住非法数据。
     *
     * <p>注意：这里校验不到"字段缺失"——缺字段在 Jackson 那层就失败了（必填字段缺失会被
     * schema 校验拦下并重试；可空字段缺失是正常的，值为 null）。这里管的是"值不合规"。
     */
    public PurchaseRequirement {
        if (!StringUtils.hasText(category)) {
            throw new IllegalArgumentException("品类不能为空");
        }
        if (quantity == null || quantity <= 0) {
            throw new IllegalArgumentException("数量必须大于 0");
        }
        if (unitBudget != null && unitBudget.signum() < 0) {
            throw new IllegalArgumentException("预算不能为负数");
        }
    }
}
