//package org.swy.zuelfinmind.controller;
//
//import org.springframework.http.MediaType;
//import org.springframework.web.bind.annotation.*;
//import org.swy.zuelfinmind.service.DeepSeekService;
//import reactor.core.publisher.Flux;
//
//import java.awt.*;
//
//@RestController
//@RequestMapping("ai/chat")
//@CrossOrigin
//public class ChatController {
//
//    private final DeepSeekService deepSeekService;
//
//    public ChatController(DeepSeekService deepSeekService) {
//        this.deepSeekService = deepSeekService;
//    }
//
//    // 必须使用 TEXT_EVENT_STREAM_VALUE
//    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
//    public Flux<String> streamChat(@RequestParam String userId,
//                                   // 2. 这里的参数名必须和前端 fetch URL 里的 key 一模一样！
//                                   // 前端写的是 &message=...，所以这里必须是 message
//                                   @RequestParam String message) {
//        return deepSeekService.streamChat(userId, message);
//    }
//}
