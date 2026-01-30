package org.swy.zuelfinmind.service;

import ai.z.openapi.ZhipuAiClient;
import ai.z.openapi.service.embedding.EmbeddingCreateParams;
import ai.z.openapi.service.embedding.EmbeddingResponse;
import com.google.protobuf.Struct;
import com.google.protobuf.Value;
import io.pinecone.clients.Index;
import io.pinecone.unsigned_indices_model.QueryResponseWithUnsignedIndices;
import io.pinecone.unsigned_indices_model.VectorWithUnsignedIndices;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.swy.zuelfinmind.entity.ChatRecord;
import org.swy.zuelfinmind.mapper.ChatRecordMapper;
import org.swy.zuelfinmind.utils.DocumentUtils;
import reactor.core.publisher.Flux;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service // 1.告诉Spring：这是“专家”，请开机时把它实例化放到容器里
public class DeepSeekService {

    private static final int BATCH_SIZE = 100;

    private static final String NSP = "zuel-namespace-v5";

    // 依赖注入
    private final ChatModel chatModel;

//    // 引入档案管理员（Mapper）
//    private final ChatRecordMapper chatRecordMapper;

    // ✅ 注入新的后勤官
    private final ChatHistoryService historyService;

    // 注入官方客户端
    private final ZhipuAiClient zhipuAiClient;

    // 注入向量数据库
    private final Index pineconeIndex;

    // 构造函数注入：Spring会自动把ChatModel递给你
    public DeepSeekService(ChatModel chatModel, ChatHistoryService historyService, /*ChatRecordMapper chatRecordMapper ,*/ ZhipuAiClient zhipuAiClient, Index pineconeIndex) {
        this.chatModel = chatModel;
        this.historyService = historyService;
//        this.chatRecordMapper = chatRecordMapper;
        this.zhipuAiClient = zhipuAiClient;
        this.pineconeIndex = pineconeIndex;
    }

//    public Flux<String> streamChat(String userId, String userQ) {
//        // 1.RAG前置处理（和普通版一样，先查资料）===========================
//        List<Message> historyMessages = historyService.getHistoryMessages(userId);
//        List<Float> queryVector = getVector(userQ);
//
//        // 检索 + Rerank（复用逻辑）
//        var pineconeRes = pineconeIndex.query(
//                20,
//                queryVector,
//                null,
//                null,
//                null,
//                NSP,
//                null,
//                false,
//                true
//        );
//        List<String> bestChunks = rerank(pineconeRes, userQ);
//        String context = String.join("\n\n", bestChunks);
//
//        // 构造Prompt
//        String finalUserMsg = (context == null || context.isEmpty())
//                ? userQ
//                : "【背景资料】：" + context + "\n\n【问题】：" + userQ;
//
//        List<Message> promptList = new ArrayList<>();
//        promptList.add(new SystemMessage(
//                """
//          你是一个名为 'ZUEL-FinMind' 的专业金融AI助手，由中南财经政法大学(ZUEL)的学生开发。
//
//          你的核心原则：
//          1. 优先回答有关中南财经政法大学、金融、经济、编程相关的问题。
//          2. 如果用户进行自我介绍或日常问候，请热情回应并记住他们的信息。
//          3. 回答要简短精炼，多用数据说话。
//          """
//        ));
//        promptList.addAll(historyMessages);
//        promptList.add(new UserMessage(finalUserMsg));
//
//        Prompt prompt = new Prompt(promptList);
//
//        // 2.核心：流式调用 + 偷窥数据用于存档 ===============================
//
//        // 用于收集完整的回答，方便最后存库
//        var fullAnswerAccumulator = new StringBuilder();
//
//        return chatModel.stream(prompt)
//                .map(response -> {
//                    // 从流里拿到一个字/词
//                    String chunk = response.getResult().getOutput().getText();
//                    // 可能是null，做个判断
//                    return chunk != null ? chunk : "";
//                })
//                // 【关键】每流过一个字，就往StringBuilder里塞
//                .doOnNext(fullAnswerAccumulator::append)
//                // 【关键】当流结束（OnComplete）时，执行存库操作
//                .doOnComplete(() -> {
//                    String fullAnswer = fullAnswerAccumulator.toString();
//                    System.out.println("✅ 流式生成完毕，存入记忆库。");
//                    // 调用后勤官存库
//                    historyService.saveInteraction(userId, userQ, fullAnswer);
//                })
//                .doOnError(e -> {
//                    System.err.println("❌ 流式生成中断：" + e.getMessage());
//                });
//    }

    public Flux<String> chat(String userId, String userMessage) {
        // 1.准备“面包顶层”：系统人设
        String systemText = """
        你是一个名为 'ZUEL-FinMind' 的专业金融AI助手，由中南财经政法大学(ZUEL)的学生开发。

        你的核心原则：
        1. 优先回答有关ZUEL、金融、经济、编程相关的问题。
        2. 如果用户进行自我介绍或日常问候，请热情回应并记住他们的信息。
        3. 回答要简短精炼，多用数据说话。
        """;

        SystemMessage systemMsg = new SystemMessage(systemText);

        // 2.准备”中间夹心“：从数据库捞取历史记忆
//        // 逻辑：查出最近的10条，按时间倒序查（最新的在上面），然后反转回来（按时间正序）
//        List<Message> historyMessages = getHistoryMessages(userId);

        List<Message> historyMessages = historyService.getHistoryMessages(userId);

        // 3.准备”面包底层“：知识库 + 当前提问
        // 算向量
        List<Float> queryVector = getVector(userMessage);

        // 查Pinecone（HTTP）
        // -----------------------------------------------------------------
        // 修改点 2：检索部分 (Chat) - 扩大搜索范围，降低准入门槛
        // -----------------------------------------------------------------
        // 【核心调优 B】：Top-K 从 3 -> 6
        // 原理：宁可多捞几个无关的，也不能漏掉一个正确的
        // 🔧 【升级点 1】：广撒网，Top-K从6改为20
        QueryResponseWithUnsignedIndices queryResponse = pineconeIndex.query(
                20, // <--- 捞20条，先把范围扩大
                queryVector,
                null,
                null,
                null,
                NSP,
                null,
                false,
                true
        );

        // ---------------------------------------------------------
        // 🔧 【升级点 2】：引入 Java 内存重排序
        // ---------------------------------------------------------
        List<String> bestChunks = rerank(queryResponse, userMessage); // <--- 调用新方法

        // 🔥【修复】增加一道防盗门！
        // 如果 Rerank 之后的第一名分数都太低（比如小于 0.6），说明查出来的都是垃圾，直接丢弃。
        // 注意：这里需要你修改一下 rerank 方法，让它返回带分数的对象，或者我们简单粗暴一点：
        // 我们直接在 Pinecone 返回时就过滤。

        String context = String.join("\n\n", bestChunks);

//        // 开始解析
//        String context = queryResponse.getMatchesList().stream()
//                // 过滤：只保留分数高（相似度高）的结果，比如大于0.75
//                // 【核心调优 C】：阈值从 0.45 -> 0.40 (甚至 0.38)
//                // 原理：DeepSeek 很聪明，稍微不相关一点的资料它能自己剔除，不要在这一步卡太死
//                .filter(match -> match.getScore() > 0.4)
//
//                // 提取：从Protobuf结构里把文字挖出来
//                .map(match -> {
//                    // 拿到metadata里的所有字段map
//                    Map<String, Value> fieldsMap = match.getMetadata().getFieldsMap();
//
//                    // 【关键点】key是”text“
//                    if (fieldsMap.containsKey("text")) {
//                        return fieldsMap.get("text").getStringValue();
//                    } else {
//                        return ""; // 没找到返回空
//                    }
//                })
//                // 拼接：把多条结果拼成一段话，用换行符隔开
//                .collect(Collectors.joining("\n\n"));

        // 3. 打印出来看看 (这就是我们要喂给 AI 的背景资料)
        System.out.println("🤖 RAG 检索到的干货:\n" + context);

        // -----------------------------------------------------------
        // 🔧 【修复点】：根据是否查到资料，动态调整指令
        // -----------------------------------------------------------
        String finalUserMsg;
        if (context == null || context.trim().isEmpty()) {
            // 场景 A：没查到资料 (比如闲聊、打招呼、自我介绍)
            // 策略：不要强迫它“不知道”，而是让它自由发挥，利用历史记录聊天
            System.out.println("🤖 未检索到RAG资料，切换为[自由对话模式]");
            finalUserMsg = userMessage;
        } else {
            // 场景 B：查到了资料 (比如问ZUEL专业)
            // 策略：严格限制范围，防止幻觉
            System.out.println("🤖 检索到RAG资料，切换为[严格知识库模式]");
            finalUserMsg = String.format(
                    "【背景资料】：%s\n\n【用户问题】：%s\n\n请结合背景资料和上下文回答。如果资料中包含答案，请依据资料；如果是闲聊或与资料不相关，请利用你的通用知识回答。",
                    context,
                    userMessage
            );
        }

        UserMessage currentUserMsg = new UserMessage(finalUserMsg);

        // 4.拼接三明治（List顺序：System -> History -> Current）
        List<Message> prompList = new ArrayList<>();
        prompList.add(systemMsg);
        prompList.addAll(historyMessages); // 把查出来的历史全塞进去
        prompList.add(currentUserMsg);

        // 5.发送请求
        Prompt prompt = new Prompt(prompList);
//        ChatResponse response = chatModel.call(prompt);
//        String aiAnswer = response.getResult().getOutput().getText();

        // 6.记账（持久化本次对话）
//        ChatRecord record = new ChatRecord();
//        record.setUserId(userId);
//        record.setQuestion(userMessage);
//        record.setAnswer(aiAnswer);
//        record.setCreateTime(LocalDateTime.now());
//        chatRecordMapper.insert(record);

//        historyService.saveInteraction(userId, userMessage, aiAnswer);
//
//        return aiAnswer;

        // 用于收集完整的回答，方便最后存库
        StringBuilder fullAnswerAccumulator = new StringBuilder();

        return chatModel.stream(prompt)
                .map(response -> {
                    // 从流里拿到一个字/词
                    String chunks = response.getResult().getOutput().getText();
                    // 可能是 null，做个判断
                    return chunks != null ? chunks : "";
                })
                // 【关键】每流过一个字，就往 StringBuilder 里塞
                .doOnNext(fullAnswerAccumulator::append)
                .doOnComplete(() -> {
                    String fullAnswer = fullAnswerAccumulator.toString();
                    System.out.println("✅ 流式生成完毕，存入记忆库。");
                    // 调用后勤官存库
                    historyService.saveInteraction(userId, userMessage, fullAnswer);
                })
                .doOnError(e -> {
                    System.err.println("❌ 流式生成中断：" + e.getMessage());
                });

        // 【关键】当流结束(OnComplete)时，执行存库操作
    }

//    // === 【新增方法】 去档案室查历史记录 ===
//    private List<Message> getHistoryMessages(String userId) {
//        // 1.MyBatis-Plus查询构造器
//        QueryWrapper<ChatRecord> query = new QueryWrapper<>();
//        query.eq("user_id", userId) // 查当前客户
//                .orderByDesc("create_time") // 按时间倒序（为了取最新的）
//                .last("limit 3"); // 只取最近10条，防止上下文爆炸
//
//        // 2.执行查询
//        List<ChatRecord> records = chatRecordMapper.selectList(query);
//
//        // 3.因为查出来是倒序的（最新->最旧），对话要按正序发（旧->新），所以要反转
//        Collections.reverse(records);
//
//        // 4.转换格式：Entity->SpringAI Message
//        List<Message> messages = new ArrayList<>();
//        for (ChatRecord record : records) {
//            // 把“用户的历史问题”转成UserMessage
//            messages.add(new UserMessage(record.getQuestion()));
//            // 把“AI的历史回答”转成AssistantMessage
//            messages.add(new AssistantMessage(record.getAnswer()));
//        }
//        return messages;
//    }

    /**
     * 🆕 核心功能：上传文件 -> 解析 -> 切块 -> 向量化 -> 存库
     */
    public String uploadAndLearn(MultipartFile file) {
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

        // 3.【消化】批量向量化并上传
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
                                .putFields("text", Value.newBuilder().setStringValue(chunkText).build())
                                .putFields("source", Value.newBuilder().setStringValue(file.getOriginalFilename()).build())
                                .build(),
                        null

                );

                upsertList.add(vectorWithUnsignedIndices);
            }
        }

        // 4.发送给Pinecone
        boolean isUpsert = UpsertBatch(upsertList);
        if (isUpsert) {
            return "✅ 学习完成！已存入知识片段";
        } else {
            return "❌ 学习失败，未能生成向量。";
        }
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

    private boolean UpsertBatch(ArrayList<VectorWithUnsignedIndices> vectors) {
        if (!vectors.isEmpty()) {
            ArrayList<ArrayList<VectorWithUnsignedIndices>> chunks = chunks(vectors);
            try {
                // pineconeIndex 是你在类成员变量里注入好的 Index 对象
                for (ArrayList<VectorWithUnsignedIndices> chunk : chunks) {
                    pineconeIndex.upsert(chunk, NSP);
                }
                System.out.println("✅ 成功！已批量上传数据到 Pinecone。");
                return true;
            } catch (Exception e) {
                System.err.println("❌ 上传失败: " + e.getMessage());
                e.printStackTrace();
            }
        }
        return false;
    }

    // A helper function that breaks an ArrayList into chunks of batchSize
    private static ArrayList<ArrayList<VectorWithUnsignedIndices>> chunks(ArrayList<VectorWithUnsignedIndices> vectors) {
        ArrayList<ArrayList<VectorWithUnsignedIndices>> chunks = new ArrayList<>();
        ArrayList<VectorWithUnsignedIndices> chunk = new ArrayList<>();

        if (vectors.size() <= BATCH_SIZE) {
            chunks.add(vectors);
            return chunks;
        }

        for (int i = 0; i < vectors.size(); i++) {
            if (i % BATCH_SIZE == 0 && i != 0) {
                chunks.add(chunk);
                chunk = new ArrayList<>();
            }

            chunk.add(vectors.get(i));
        }

        return chunks;
    }

    /**
     * 🧠 核心算法：内存重排序 (Hybrid Rerank)
     * 结合了“向量相似度”和“关键词匹配度”
     */
    private List<String> rerank(QueryResponseWithUnsignedIndices response, String userQuery) {
        // 1.提取所有候选项
        var matches = response.getMatchesList();
        if (matches == null || matches.isEmpty()) return Collections.emptyList();

        // 简单分词：把用户问题按空格或标点切开（简易版，不需要引入 Jieba）
        // 比如“ZUEL新增了什么实验班” -> ["ZUEL", "新增", "了", "什么", "实验班"]
        String[] keywords = userQuery.split("[\\s,?.!，。？！]+");

        // 2.定义一个临时类来存分数
        class ScoreChunk {
            String text;
            double finalScore;

            ScoreChunk(String text, double vectorScore, double keywordScore) {
                this.text = text;
                // 🔥 核心公式：向量分占 80%，关键词分占 20%
                this.finalScore = (vectorScore * 0.8) + (keywordScore * 0.2);
            }
        }

        List<ScoreChunk> scoredList = new ArrayList<>();

        for (var match : matches) {
            if (!match.getMetadata().getFieldsMap().containsKey("text")) continue;

            String text = match.getMetadata().getFieldsMap().get("text").getStringValue();
            float vectorScore = match.getScore(); // 0.0 ~ 1.0

            if (vectorScore < 0.4) continue;

            // 3.计算关键词命中率
            int hitCount = 0;
            for (String keyword : keywords) {
                if (keyword.length() > 1 && text.contains(keyword)) { // 忽略单字，防止干扰
                    hitCount++;
                }
            }
            // 归一化：假设命中3个词就是满分（避免分数爆炸）
            double keywordScore = Math.min(hitCount / 3.0, 1.0);

            System.out.printf("Doc: %.20s... | V: %.2f | K: %.2f | Final: %.2f%n", text, vectorScore, keywordScore, (vectorScore * 0.8) + (keywordScore * 0.2));

            scoredList.add(new ScoreChunk(text, vectorScore, keywordScore));
        }

        // 4.按最终分数倒序排列（分数高的排在前面）
        scoredList.sort((a,b) -> Double.compare(b.finalScore, a.finalScore));

        // 5.取前5名（Top 5）
        return scoredList.stream()
                .filter(match -> match.finalScore > 0.45)
                .limit(5)
                .map(s -> s.text)
                .collect(Collectors.toList());
    }
}
