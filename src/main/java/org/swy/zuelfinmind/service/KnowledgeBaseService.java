package org.swy.zuelfinmind.service;

import ai.z.openapi.ZhipuAiClient;
import ai.z.openapi.service.embedding.EmbeddingCreateParams;
import ai.z.openapi.service.embedding.EmbeddingResponse;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.swy.zuelfinmind.utils.VectorUtils.cosineSimilarity;

@Service
public class KnowledgeBaseService {

    private final ZhipuAiClient zhipuAiClient;
//    //【核心】这就是我们的建议数据库：一个存在内存里的Map
//    // Key：文本内容，Value：对应的向量
//    private final Map<String, List<Double>> vectorStore = new HashMap<>();

    private final ObjectMapper objectMapper = new ObjectMapper(); // JSON工具
    // 存档文件的路径（放在项目根目录下）
    private static final String STORE_FILE = "knowledge_base.json";

    // 内存数据库（改为ConcurrentHashMap防止并发报错）
    private Map<String, List<Double>> vectorStore = new ConcurrentHashMap<>();

    public KnowledgeBaseService(ZhipuAiClient zhipuAiClient) {
        this.zhipuAiClient = zhipuAiClient;
    }

    /**
     * 🟢 启动时自动加载 (读档)
     */
    @PostConstruct
    public void loadFromFile() {
        File file = new File(STORE_FILE);
        if (file.exists()) {
            try {
                // 把JSON文件读回来，变成Map对象
                vectorStore = objectMapper.readValue(file, new TypeReference<Map<String, List<Double>>>() {});
                System.out.println("📂 成功加载知识库存档，当前条目数: " + vectorStore.size());
            } catch (IOException e) {
                System.err.println("⚠️ 读取存档失败: " + e.getMessage());
            }
        } else {
            System.out.println("📂 未发现存档，初始化为空库。");
        }
    }

    /**
     * 💾 保存到文件 (存档)
     */
    private void saveToFile() {
        try {
            // 把内存里的Map写成JSON文件
            objectMapper.writeValue(new File(STORE_FILE), vectorStore);
            System.out.println("💾 知识库已自动存档 (JSON)");
        } catch (IOException e) {
            System.err.println("⚠️ 存档失败: " + e.getMessage());
        }
    }

    /**
     * 动作1：存入知识
     * 将文本转为向量
     * 存完自动保存
     */
    public void addDocument(String text) {
        // 如果已经有了，就不浪费Token重新跑向量了
        if (vectorStore.containsKey(text)) {
            System.out.println("💡 知识已存在，跳过: " + text);
            return;
        }

        List<Double> vector = getVector(text);
        if (vector != null) {
            vectorStore.put(text, vector);
            System.out.println("✅ 已存入知识库: " + text.substring(0, Math.min(text.length(), 10)) + "...");
            // 每次存入都触发存档
            saveToFile();
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
