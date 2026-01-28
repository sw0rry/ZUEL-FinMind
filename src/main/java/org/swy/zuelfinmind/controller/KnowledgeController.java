package org.swy.zuelfinmind.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.swy.zuelfinmind.service.DeepSeekService;

@RestController
@RequestMapping("/ai")
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
}
