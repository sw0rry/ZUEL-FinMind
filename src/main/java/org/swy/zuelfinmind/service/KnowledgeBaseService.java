package org.swy.zuelfinmind.service;

import ai.z.openapi.ZhipuAiClient;
import ai.z.openapi.service.embedding.EmbeddingCreateParams;
import ai.z.openapi.service.embedding.EmbeddingResponse;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.swy.zuelfinmind.utils.VectorUtils.cosineSimilarity;

@Service
public class KnowledgeBaseService {

    private final ZhipuAiClient zhipuAiClient;
    //【核心】这就是我们的建议数据库：一个存在内存里的Map
    // Key：文本内容，Value：对应的向量
    private final Map<String, List<Double>> vectorStore = new HashMap<>();

    public KnowledgeBaseService(ZhipuAiClient zhipuAiClient) {
        this.zhipuAiClient = zhipuAiClient;
    }

    /**
     * 动作1：存入知识
     * 将文本转为向量
     */
    public void addDocument(String text) {
        List<Double> vector = getVector(text);
        if (vector != null) {
            vectorStore.put(text, vector);
            System.out.println("✅ 已存入知识库: " + text.substring(0, Math.min(text.length(), 10)) + "...");
        }
    }

    /**
     * 动作2：检索知识
     * 拿着问题找最相似文本
     */
    public String search(String query) {
        // 1.把提问也变成向量
        List<Double> queryVector = getVector(query);
        if (queryVector == null) return "检索失败";

        String bestMatch = null;
        double maxScore = -1.0;

        // 2.【暴力循环】遍历所有库存，一个个算分
        for (Map.Entry<String, List<Double>> entry : vectorStore.entrySet()) {
            double score = cosineSimilarity(queryVector, entry.getValue());
            //打印一下分数过程，观察清楚
            System.out.println("   >>> 与 [" + entry.getKey().substring(0, 5) + "] 的相似度: " + score);

            if (score > maxScore) {
                maxScore = score;
                bestMatch = entry.getKey();
            }
        }

        System.out.println("🔎 检索结果：最高分 " + maxScore + " -> " + bestMatch);
        // 设定一个门槛，如果太不相关（比如小于0.4），就说没找到
        return maxScore > 0.4 ? bestMatch : "未找到相关知识";
    }

    private List<Double> getVector(String text) {
        try {
            EmbeddingCreateParams request = new EmbeddingCreateParams();
            request.setModel("embedding-3");
            request.setDimensions(1024);
            request.setInput(text);
            EmbeddingResponse response = zhipuAiClient.embeddings().createEmbeddings(request);
            if (response.isSuccess()) {
                return response.getData().getData().get(0).getEmbedding();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
}
