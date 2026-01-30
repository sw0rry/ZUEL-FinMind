package org.swy.zuelfinmind.controller;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.swy.zuelfinmind.service.DeepSeekService;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/ai")
// 允许跨域，防止某些浏览器报CORS错误
@CrossOrigin(origins = "*")
public class KnowledgeController {

    private final DeepSeekService deepSeekService;

    public KnowledgeController(DeepSeekService deepSeekService) {
        this.deepSeekService = deepSeekService;
    }

    // 上传接口：Postman选POST -> Body -> form-data -> key填“file”（类型选File）
    @PostMapping("/upload")
    public String uploadFile(@RequestParam("file") MultipartFile file) {
        return deepSeekService.uploadAndLearn(file);
    }

    // 1. 【新增】聊天接口 (修复 405 问题的关键)
    // 前端用的是 GET 请求，所以这里必须是 @GetMapping
    @GetMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE + ";charset=UTF-8")
    public Flux<String> chat(@RequestParam("userId") String userId,
                             @RequestParam("message") String message) {
        // 调用 DeepSeekService 的 chat 方法
        return deepSeekService.chat(userId, message);
    }
}
