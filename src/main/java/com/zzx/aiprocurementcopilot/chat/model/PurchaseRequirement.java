package com.zzx.aiprocurementcopilot.chat.model;

import java.math.BigDecimal;
import java.util.List;

/**
 * 采购需求（从用户的自然语言描述中提取）
 */
public record PurchaseRequirement(
        String category,        // 品类，如"笔记本电脑"
        Integer quantity,       // 数量
        BigDecimal unitBudget,  // 单件预算上限（元）
        String deliveryDate,    // 期望交付时间，格式 yyyy-MM-dd
        String usageScenario,   // 使用场景
        List<String> mustHave   // 必须满足的条件
) {}
