package org.swy.zuelfinmind;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * 手写的一个简易 Embedding 客户端
 * 作用：绕过 Maven 依赖，直接通过 HTTP 协议调用智谱 AI
 */
@Component
public class SimpleEmbeddingClient {

    // 读取你在 application.properties 或环境变量里配的 ZHIPU_KEY
    @Value("${YOUR_EB_KEY}")
    private String apiKey;

    private final RestClient restClient = RestClient.builder()
            .baseUrl("https://open.bigmodel.cn/api/paas/v4") // 智谱的 OpenAI 兼容接口
            .build();

    public float[] embed(String text) {
        // 1. 构造请求体 (JSON)
        var requestBody = Map.of(
                "model", "embedding-2",
                "input", text
        );

        // 2. 发送 HTTP POST 请求
        var response = restClient.post()
                .uri("/embeddings")
                .header("Authorization", "Bearer " + apiKey) // 智谱支持直接用 Key
                .contentType(MediaType.APPLICATION_JSON)
                .body(requestBody)
                .retrieve()
                .body(Map.class); // 简单粗暴地转成 Map

        // 3. 解析结果 (从复杂的 JSON 里把向量数组挖出来)
        /*
          智谱返回格式参考 OpenAI:
          {
            "data": [
              { "embedding": [0.1, 0.2, ...] }
            ]
          }
         */
        try {
            List<Map<String, Object>> data = (List<Map<String, Object>>) response.get("data");
            List<Double> embeddingList = (List<Double>) data.get(0).get("embedding");

            // List<Double> 转 float[]
            float[] result = new float[embeddingList.size()];
            for (int i = 0; i < embeddingList.size(); i++) {
                result[i] = embeddingList.get(i).floatValue();
            }
            return result;
        } catch (Exception e) {
            throw new RuntimeException("解析智谱 Embedding 结果失败", e);
        }
    }
}