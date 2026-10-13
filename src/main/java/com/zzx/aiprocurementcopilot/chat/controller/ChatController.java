package com.zzx.aiprocurementcopilot.chat.controller;

import com.zzx.aiprocurementcopilot.chat.model.PurchaseRequirement;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RestController
public class ChatController {

    /**
     * 三个解析接口共用同一份提示词。两个原因：
     * ① 它们的差别只在"输出形态"（纯文本 / 结构化 / 结构化+校验），提示词必须一致，
     *    /parse/normal vs /parse vs /parse/pro 的成功率对比实验才成立；
     * ② 规则 2 的措辞与 schema 强耦合：可空字段是靠 @Nullable 从 required 里摘除的，
     *    所以必须让模型"省略字段"，而不是"填 null"（填 null 会被 schema 判类型不符）。
     */
    private static final String PARSE_SYSTEM_PROMPT = """
            你是采购需求解析助手。
            用户会用口语描述采购需求，请提取结构化字段。
            规则：
            1、只提取用户明确提到的信息
            2、没有提到的字段直接省略该字段，不要编造，也不要填 null
            3、日期统一转换成yyyy-MM-dd格式
            """;

    private final ChatClient plainChatClient;
    private final ChatClient memoryChatClient;

    public ChatController(ChatClient plainChatClient, ChatClient memoryChatClient) {
        this.plainChatClient = plainChatClient;
        this.memoryChatClient = memoryChatClient;
    }


    @GetMapping("/chat")
    public String chat(@RequestParam String message) {
        return plainChatClient.prompt()
                .user(message)
                .call()
                .content();
    }

    @GetMapping(value = "/chat/stream", produces = "text/event-stream;charset=UTF-8")
    public Flux<String> chatStream(@RequestParam String message) {
        return plainChatClient.prompt()
                .user(message)
                .stream()
                .content();
    }

    @GetMapping("/chat/pro")
    public String chatPro(@RequestParam String message) {
        return plainChatClient.prompt()
                .system("""
                        你是一个企业采购领域的助手
                        请遵守以下规则：
                        1.只回答采购、供应商链、商品相关问题，其他问题礼貌拒绝
                        2.回答简洁，不超过3句话
                        3.不确定的事情明确说“我不确定”，不要编造
                        4.涉及价格时，说明是参考区间
                        """)
                .user(message)
                .call()
                .content();
    }

    //多轮对话记忆
    @GetMapping("/chat/memory")
    public String chatMemory(@RequestParam String conversationId, @RequestParam String message) {
        return memoryChatClient.prompt()
                .user(message)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                .call()
                .content();
    }


    @GetMapping("/parse/normal")
    public String parseNormal(@RequestParam String text) {
        return plainChatClient.prompt()
                .system(PARSE_SYSTEM_PROMPT)
                .user(text)
                .call()
                .content();
    }

    @GetMapping("/parse")
    public PurchaseRequirement parse(@RequestParam String text) {
        return plainChatClient.prompt()
                .system(PARSE_SYSTEM_PROMPT)
                .user(text)
                .call()
                //转换为目标对象
                .entity(PurchaseRequirement.class);
    }

    @GetMapping("/parse/pro")
    public PurchaseRequirement parsePro(@RequestParam String text) {
        return plainChatClient.prompt()
                .system(PARSE_SYSTEM_PROMPT)
                .user(text)
                .call()
                // validateSchema()：把 record 推导出的 JSON Schema 交给模型做格式约束，并校验模型返回的
                // JSON；不通过就把具体错误回灌给模型让它自己改（maxRepeatAttempts 默认 3，总调用最多 4 次）。
                // 它只校验"结构"（必填/类型）；业务规则（数量>0、预算≥0）在 record 的紧凑构造器里，不在这条重试回环内。
                .entity(PurchaseRequirement.class, entityParamSpec -> entityParamSpec.validateSchema());
    }
}
