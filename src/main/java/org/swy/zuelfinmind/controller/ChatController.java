package org.swy.zuelfinmind.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.swy.zuelfinmind.dto.ChatRequest;
import org.swy.zuelfinmind.service.DeepSeekService;

@RestController
@RequestMapping("/chat")
public class ChatController {

    // 注入专家
    private final DeepSeekService deepSeekService;

    // 构造函数注入
    public ChatController(DeepSeekService deepSeekService) {
        this.deepSeekService = deepSeekService;
    }

    // 动作：接受对话请求
    // 网址：POST http://localhost:8080/chat/ask
    @PostMapping("/ask")
    public String talk(@RequestBody ChatRequest request) {
////        // 1.先检查能不能收到
////        System.out.println("收到用户：" + request.getUserId());
////        System.out.println("收到问题：" + request.getMessage());
////
////        // 2.暂时给出假回复
////        return "后端已收到你的问题：" + request.getMessage();
//
//        // 1.接待员拿到用户问题
//        String userQuestion = request.getMessage();
//
//        // 2.转交专家处理（调用Service）
//        String aiAnswer = deepSeekService.chat(userQuestion);
//
//        // 3.把专家回复传回给用户
//        return aiAnswer;

        // 1.接待员拿到用户问题和ID
        String userQuestion = request.getMessage();
        String userId = request.getUserId();

        // 2.转交给专家
        String aiAnswer = deepSeekService.chat(userId, userQuestion);

        // 3.把专家回复传回给用户
        return aiAnswer;
    }
}
