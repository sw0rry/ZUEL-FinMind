package com.swy.fintech.controller;

import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class EtlController {

    private final VectorStore vectorStore;

    public EtlController(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    @GetMapping("/ai/load")
    public String loadData() {
        // 1. 模拟一段“私有数据” (未来这里可以换成读取 PDF 文件)
        String secretData = "中南财经政法大学的绝密代码是：8888。这个代码可以开启隐藏的金融实验室。";

        // 2. 把文字变成 Document 对象
        Document doc = new Document(secretData);

        // 3. 存入向量库 (这一步会自动调用本地模型进行 Embedding，可能会慢几秒)
        vectorStore.add(List.of(doc));

        return "数据喂食成功！AI 现在知道绝密代码了。";
    }
}