package org.swy.zuelfinmind.service.strategy.impl;

import ai.z.openapi.ZhipuAiClient;
import ai.z.openapi.service.embedding.EmbeddingCreateParams;
import ai.z.openapi.service.embedding.EmbeddingResponse;
import com.google.protobuf.Struct;
import com.google.protobuf.Value;
import io.pinecone.clients.Index;
import io.pinecone.unsigned_indices_model.QueryResponseWithUnsignedIndices;
import io.pinecone.unsigned_indices_model.VectorWithUnsignedIndices;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.swy.zuelfinmind.model.VectorSearchResult;
import org.swy.zuelfinmind.service.strategy.VectorStoreStrategy;
import org.swy.zuelfinmind.utils.DocumentUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class PineconeVectorStore implements VectorStoreStrategy {

    private static final int BATCH_SIZE = 96;

    private static final String NAMESPACE = "zuel-finmind";

    private final ZhipuAiClient zhipuAiClient;

    private final Index index;

    public PineconeVectorStore(ZhipuAiClient zhipuAiClient, Index index) {
        this.zhipuAiClient = zhipuAiClient;
        this.index = index;
    }


    // 因为用户问题与知识都需要通过同一个向量模型处理，所以这里对知识进行pinecone集成处理作为参考
    // 我们还是用zhipu生成向量
//    @Override
//    public String store(MultipartFile file) {
//        // 1.【咀嚼】解析文件
//        String content = DocumentUtils.parseFile(file);
//        if (content.isEmpty()) return "";
//
//        // 2.【切割】切块 + 重叠
//        List<String> chunks = DocumentUtils.splitText(content, 200, 50);
//
//        // 3.【消化】批量向量化并上传
////        ArrayList<VectorWithUnsignedIndices> upsertList = new ArrayList<>();
//
////        for (int i = 0; i < chunks.size(); i++) {
////            String chunkText = chunks.get(i);
////            List<Float> vector = getVector(chunkText);
////        }
//        ArrayList<Map<String, String>> upsertRecords = new ArrayList<>();
//
//        for (int i = 0; i < chunks.size(); i++) {
//            HashMap<String, String> record = new HashMap<>();
//            record.put("id", "rec" + i);
//            record.put("material", chunks.get(i));
//
//            upsertRecords.add(record);
//
//        }
//        try {
//            index.upsertRecords("zuel-finmind", upsertRecords);
//            System.out.println("✅ 学习完成！已存入知识片段");
//        } catch (Exception e) {
//            System.out.println("❌ 学习失败，未能生成向量。" + e.getMessage());
//            e.printStackTrace();
//        }
//        return "";
//    }

    public String store(MultipartFile file) {
        // 1.【咀嚼】解析文件
        String content = DocumentUtils.parseFile(file);
        if (content.isEmpty()) return "文件解析失败或内容为空";

        // 2.【切割】切成500字的小块，重叠50字
        // -----------------------------------------------------------------
        // 修改点 1：上传部分 (Upload) - 缩小切片，提高精度
        // -----------------------------------------------------------------
        // 【核心调优A】：Chunk Size从 500 -> 250
        // 原理：切的越细，细节丢失越少，检索越精准
        // Overlap从50 -> 30：保持一点重叠即可
        List<String> chunks = DocumentUtils.splitText(content, 200, 50);

        // 3.【消化】批量向量化
        ArrayList<VectorWithUnsignedIndices> upsertList = new ArrayList<>();

        for (int i = 0; i < chunks.size(); i++) {
            String chunkText = chunks.get(i);
            List<Float> vector = getVector(chunkText); // 调用智谱Embedding

            if (vector != null) {
                // 构造Pinecone数据
                // 注意：这里建议给 ID 加个时间戳或者版本号，防止和昨天的旧数据混淆
                // 比如: .setId(file.getOriginalFilename() + "_v2_part_" + i)
                // 但为了简单，你也可以先去 Pinecone 控制台把旧索引删了重建
                VectorWithUnsignedIndices vectorWithUnsignedIndices = new VectorWithUnsignedIndices(
                        file.getOriginalFilename() + "_part_" + i,
                        vector,
                        Struct.newBuilder()
                                .putFields("material", Value.newBuilder().setStringValue(chunkText).build())
                                .putFields("source", Value.newBuilder().setStringValue(file.getOriginalFilename()).build())
                                .build(),
                        null

                );
                upsertList.add(vectorWithUnsignedIndices);
            }
        }

        // 4.上传给Pinecone
        ArrayList<ArrayList<VectorWithUnsignedIndices>> batches = batches(upsertList);
        try {
            for (ArrayList<VectorWithUnsignedIndices> batch : batches) {
                index.upsert(batch, NAMESPACE);
            }
            return "✅ 成功！已批量上传数据到 Pinecone。";
        } catch (Exception e) {
            e.printStackTrace();
            return "❌ 上传失败: " + e.getMessage();
        }
    }


    // 这里也可以直接通过String问题查找，pinecone自动转为向量
//    @Override
//    public void search(String query, int topK) {
//        List<String> fields = new ArrayList<>();
//        fields.add("material");
//
//        SearchRecordsResponse recordsResponse;
//        try {
//            recordsResponse = index.searchRecordsByText(
//                    query,
//                    "zuel-finmind",
//                    fields,
//                    10,
//                    null,
//                    null
//            );
//        } catch (ApiException e) {
//            throw new RuntimeException(e);
//        }
//        System.out.println(recordsResponse.getResult().getHits().get(0).getFields());
//        System.out.println(recordsResponse.getResult().getHits().get(0).getScore());
//    }

    @Override
    public List<VectorSearchResult> search(String query) {
        // 1.向量相似度
        List<Float> queryVector = getVector(query);

        QueryResponseWithUnsignedIndices queryResponse = index.query(
                20,
                queryVector,
                null,
                null,
                null,
                NAMESPACE,
                null,
                false,
                true
        );

        return queryResponse.getMatchesList().stream()
                .filter(match -> match.getScore() > 0.4)
                .map(match -> {
//                    String text = match.getMetadata().getFieldsMap().get("material").getStringValue();
//                    Float vectorScore = match.getScore();
//                    String source = match.getMetadata().getFieldsMap().get("material").getStringValue();

                    // 业务逻辑，在DeepseekService里加
//                    //加入重排序
//                    Float score = rerank(text, vectorScore);

//                    return new VectorSearchResult(text, score, source);

                    System.out.printf("文本前缀：%s | 原始向量得分：%.2f \n",
                            match.getMetadata().getFieldsMap().get("material").getStringValue()
                                    .substring(0, Math.min (match.getMetadata().getFieldsMap().get("material").getStringValue().length (), 20)), // 更长前缀，避免越界
                            match.getScore());

                    return new VectorSearchResult(
                            match.getMetadata().getFieldsMap().get("material").getStringValue(),
                            match.getScore(),
                            match.getMetadata().getFieldsMap().get("source").getStringValue()
                    );
                })
                .toList();

    }

    // --- 工具方法：调用智谱获取向量（Double转Float）
    private List<Float> getVector(String text) {
        try {
            EmbeddingCreateParams request = new EmbeddingCreateParams();
            request.setModel("embedding-3");
            request.setDimensions(1024);
            request.setInput(text);

            EmbeddingResponse response = zhipuAiClient.embeddings().createEmbeddings(request);

            if (response.isSuccess()) {
                // 智谱返回List<Double>,Pinecone需要List<Float>
                List<Double> doubleList = response.getData().getData().get(0).getEmbedding();
                return doubleList.stream().map(Double::floatValue).collect(Collectors.toList());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    // --- 工具方法：切分向量，批次上次
    private static ArrayList<ArrayList<VectorWithUnsignedIndices>> batches(ArrayList<VectorWithUnsignedIndices> vectors) {

        ArrayList<ArrayList<VectorWithUnsignedIndices>> batches = new ArrayList<>();

        if (vectors.size() <= BATCH_SIZE) {
            batches.add(vectors);
            return batches;
        }

        ArrayList<VectorWithUnsignedIndices> batch = new ArrayList<>();

        for (int i = 0; i < vectors.size(); i++) {
            if (i % BATCH_SIZE == 0 && i != 0) {
                batches.add(batch);
            }
            batch.add(vectors.get(i));
        }
        return batches;
    }

//    private Float rerank(String text, Float vectorScore) {
//        // 简单分词：把用户问题按空格或标点切开（简易版，不需要引入 Jieba）
//        // 比如“ZUEL新增了什么实验班” -> ["ZUEL", "新增", "了", "什么", "实验班"]
//        String[] keywords = text.split("[\\s,?.!，。？！]+");
//
//        int hitCount = 0;
//        for (String keyword : keywords) {
//            if (keyword.length() > 1 && text.contains(keyword)) { // 忽略单字，防止干扰
//                hitCount++;
//            }
//        }
//        // 归一化：假设命中3个词就是满分（避免分数爆炸）
//        double keywordScore = Math.min(hitCount / 3.0, 1.0);
//
//        return (float) ((vectorScore * 0.8) + (keywordScore * 0.2));
//    }
}
