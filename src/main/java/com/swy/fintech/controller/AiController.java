package com.swy.fintech.controller;

import com.swy.fintech.entity.ChatLog;
import com.swy.fintech.mapper.ChatLogMapper;
import org.springframework.ai.chat.ChatClient;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

@RestController // 【知识点1】告诉 Spring：我是个服务员（Controller），专门负责处理 HTTP 请求，返回的是数据（JSON/文本），不是网页。
public class AiController {

    private final ChatClient chatClient; // 【知识点2】 IOC（控制反转）：我不需要自己 new 一个 ChatClient，Spring 容器里已经造好了，我直接声明我要用。

    // 【知识点3】构造器注入：Spring 启动时，会自动把造好的 ChatClient 塞到这个构造函数里。
    // 面试必问：为什么用构造器注入？答：保证这个组件在使用前一定被初始化了，且不可修改（final）。

    private final ChatLogMapper chatLogMapper;
    // 移除了 VectorStore，因为它卡死了系统
    public AiController(ChatClient chatClient, ChatLogMapper chatLogMapper) {
        this.chatClient = chatClient;
        this.chatLogMapper = chatLogMapper;
    }

    // 【知识点4】 API 映射：当用户在浏览器访问 /ai/chat 时，就会触发这个方法。
    // @RequestParam：把网址里的 ？msg=xxx抓取出来，赋值给 message 变量。

    @GetMapping("/ai/chat")
    public String chat(@RequestParam(value = "msg") String message) {
        // 【知识点5】 Prompt Engineering（提示词工程）：
        // SystemMessage = “上帝指令”（设定人设、规则、边界）。
        // UserMessage = “凡人提问”（实际的业务问题）。

        // 1. 保留你的高冷金融分析师人设
        String systemPrompt = """
            你是一名中南财经政法大学（ZUEL）毕业的资深金融分析师。
            你的行文风格需要专业、犀利，多用数据说话。
            
            【重要规则】
            如果用户问的问题与【金融、经济、编程、数据分析】无关，
            请直接回复：“抱歉，作为金融分析师，我不关注除此之外的话题。”
            不要回答哪怕一个字的无关内容。
            """;

        // 2. 发送请求
        SystemMessage systemMsg = new SystemMessage(systemPrompt);
        UserMessage userMsg = new UserMessage(message);
        Prompt prompt = new Prompt(List.of(systemMsg, userMsg));

        // 【知识点6】调用链：
        // prompt.call() -> 发送 HTTP 请求给 DeepSeek
        // getResult() -> 拿到 JSON 响应
        // getOutput()。getContent() -> 提取出核心回答文本

        // 3. 快速返回，不会卡顿
//        return chatClient.call(prompt).getResult().getOutput().getContent();

        // 调用 AI 获得回答
        String response = chatClient.call(prompt).getResult().getOutput().getContent();

        // 数据留痕
        try {
            ChatLog log = new ChatLog();
            log.setUserQuestion(message); // 记录用户问题
            log.setAiResponse(response); // 记录 AI 回答
            log.setCreateTime(LocalDateTime.now()); // 记录时间

            chatLogMapper.insert(log);
            System.out.println(">>> 审计日志已保存，ID：" + log.getId());
        } catch (Exception e) {
            System.out.println(">>> 日志保存失败：" + e.getMessage());
            // 注意：日志保存失败不应该影响用户看回答，所以这里只打印错误，不抛出异常
        }

        return response;
    }
}