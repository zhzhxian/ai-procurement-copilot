package com.zzx.aiprocurementcopilot.chat.controller;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RestController
public class ChatController {
    private final ChatClient chatClient;

    public ChatController(ChatClient.Builder builder, ChatMemory chatMemory) {
        this.chatClient = builder
                //多轮对话记忆
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .build();
    }

    @GetMapping("/chat")
    public String chat(@RequestParam String message) {
        return chatClient.prompt()
                .user(message)
                .call()
                .content();
    }

    @GetMapping(value = "/chat/stream", produces = "text/event-stream;charset=UTF-8")
    public Flux<String> chatStream(@RequestParam String message) {
        return chatClient.prompt()
                .user(message)
                .stream()
                .content();
    }

    @GetMapping("/chat/pro")
    public String chatPro(@RequestParam String message) {
        return chatClient.prompt()
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
        return chatClient.prompt()
                .user(message)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
                .call()
                .content();
    }
}
