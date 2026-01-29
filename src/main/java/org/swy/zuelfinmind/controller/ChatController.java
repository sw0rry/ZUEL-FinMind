//package org.swy.zuelfinmind.controller;
//
//import org.springframework.web.bind.annotation.PostMapping;
//import org.springframework.web.bind.annotation.RequestBody;
//import org.springframework.web.bind.annotation.RequestMapping;
//import org.springframework.web.bind.annotation.RestController;
//import org.swy.zuelfinmind.dto.ChatRequest;
//import org.swy.zuelfinmind.service.DeepSeekService;
//
//@RestController
//@RequestMapping("/ai")
//public class ChatController {
//
//    // 注入专家
//    private final DeepSeekService deepSeekService;
//
//    // 构造函数注入
//    public ChatController(DeepSeekService deepSeekService) {
//        this.deepSeekService = deepSeekService;
//    }
//
//    // 动作：接受对话请求
//    // 网址：POST http://localhost:8080/chat/ask
//    @PostMapping("/chat")
//    public String talk(@RequestBody ChatRequest request) {
//
//        // 1.接待员拿到用户问题和ID
//        String userQuestion = request.getMessage();
//        String userId = request.getUserId();
//
//        // 2.转交给专家
//        String aiAnswer = deepSeekService.chat(userId, userQuestion);
//
//        // 3.把专家回复传回给用户
//        return aiAnswer;
//    }
//}
