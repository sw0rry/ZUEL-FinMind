package org.swy.zuelfinmind.service;

import ai.z.openapi.ZhipuAiClient;
import ai.z.openapi.service.embedding.EmbeddingCreateParams;
import ai.z.openapi.service.embedding.EmbeddingResponse;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.google.protobuf.Struct;
import com.google.protobuf.Value;
import io.pinecone.clients.Index;
import io.pinecone.clients.Pinecone;
import io.pinecone.configs.PineconeConfig;
import io.pinecone.configs.PineconeConnection;
import io.pinecone.unsigned_indices_model.QueryResponseWithUnsignedIndices;
import io.pinecone.unsigned_indices_model.VectorWithUnsignedIndices;
import jakarta.annotation.PostConstruct;
import org.apache.ibatis.mapping.Environment;
import org.openapitools.db_control.client.model.CreateIndexForModelRequest;
import org.openapitools.db_control.client.model.CreateIndexForModelRequestEmbed;
import org.openapitools.db_control.client.model.DeletionProtection;
import org.openapitools.db_control.client.model.IndexModel;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;
//import org.swy.zuelfinmind.config.PcConfig;
import org.swy.zuelfinmind.entity.ChatRecord;
import org.swy.zuelfinmind.mapper.ChatRecordMapper;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service // 1.告诉Spring：这是“专家”，请开机时把它实例化放到容器里
public class DeepSeekService {

    // 依赖注入
    private final ChatModel chatModel;

    // 引入档案管理员（Mapper）
    private final ChatRecordMapper chatRecordMapper;

    // 注入官方客户端
    private final ZhipuAiClient zhipuAiClient;

    // 注入新服务
//    private final KnowledgeBaseService kbService;

    // 注入向量数据库
    private final Index pineconeIndex;
//    private final Pinecone pineconeClient;

    // 构造函数注入：Spring会自动把ChatModel递给你
    public DeepSeekService(ChatModel chatModel, ChatRecordMapper chatRecordMapper, ZhipuAiClient zhipuAiClient/*, KnowledgeBaseService kbService*/, Index pineconeIndex/*, Pinecone pineconeClient*/) {
        this.chatModel = chatModel;
        this.chatRecordMapper = chatRecordMapper;
        this.zhipuAiClient = zhipuAiClient;
//        this.kbService = kbService;
        this.pineconeIndex = pineconeIndex;
//        this.pineconeClient = pineconeClient;
    }

    public String chat(String userId, String userMessage) {
        // 1.准备“面包顶层”：系统人设
        String systemText = """
                你是一个名为 'ZUEL-FinMind' 的专业金融AI助手，由中南财经政法大学(ZUEL)的学生开发。
                            你的核心原则：
                            1. 只回答金融、经济、编程或数据分析相关的问题。
                            2. 如果用户问生活类问题（如做菜、娱乐），请礼貌但坚决地拒绝，并引导他们回到金融话题。
                            3. 回答要简短精炼，多用数据说话，避免长篇大论。
                """;
        SystemMessage systemMsg = new SystemMessage(systemText);

        // 2.准备”中间夹心“：从数据库捞取历史记忆
        // 逻辑：查出最近的10条，按时间倒序查（最新的在上面），然后反转回来（按时间正序）
        List<Message> historyMessages = getHistoryMessages(userId);

//        // 3.准备”面包底层“：知识库 + 当前提问
//        String finalUserMsg = userMessage;
//        // 新增知识库
//        //  1.先去“书架”里找找有没有相关“小抄”
//        String context = kbService.search(userMessage);
//        //  2.如果找到“小抄”就把它“夹带”在问题里
//        if (!"未找到相关知识".equals(context)) {
//            System.out.println("🤖 RAG 触发！已注入背景知识: " + context);
//            // 【关键咒语】告诉 AI：这是背景资料，请根据这个回答，不要瞎编。
//            finalUserMsg  = String.format(
//                    "【背景资料】：%s\n\n【用户问题】：%s\n\n请根据背景资料回答问题。如果资料里没有答案，就说不知道。",
//                    context,
//                    userMessage
//            );
//        }

        // 算向量
        List<Float> queryVector = getVector(userMessage);

        String finalUserMsg = userMessage;

        // 查Pinecone（HTTP）
        QueryResponseWithUnsignedIndices queryResponse = pineconeIndex.query(1, queryVector, null, null, null, "zuel-namespace", null, false, true);

        // 开始解析
        String context = queryResponse.getMatchesList().stream()
                // 过滤：只保留分数高（相似度高）的结果，比如大于0.75
                .filter(match -> match.getScore() > 0.5)

                // 提取：从Protobuf结构里把文字挖出来
                .map(match -> {
                    // 拿到metadata里的所有字段map
                    Map<String, Value> fieldsMap = match.getMetadata().getFieldsMap();

                    // 【关键点】key是”text“
                    if (fieldsMap.containsKey("text")) {
                        return fieldsMap.get("text").getStringValue();
                    } else {
                        return ""; // 没找到返回空
                    }
                })
                // 拼接：把多条结果拼成一段话，用换行符隔开
                .collect(Collectors.joining("\n\n"));

        // 3. 打印出来看看 (这就是我们要喂给 AI 的背景资料)
        System.out.println("🤖 RAG 检索到的干货:\n" + context);

        UserMessage currentUserMsg = new UserMessage(finalUserMsg);

        // 4.拼接三明治（List顺序：System -> History -> Current）
        List<Message> prompList = new ArrayList<>();
        prompList.add(systemMsg);
        prompList.addAll(historyMessages); // 把查出来的历史全塞进去
        prompList.add(currentUserMsg);

        // 5.发送请求
        Prompt prompt = new Prompt(prompList);
        ChatResponse response = chatModel.call(prompt);
        String aiAnswer = response.getResult().getOutput().getText();

        // 6.记账（持久化本次对话）
        ChatRecord record = new ChatRecord();
        record.setUserId(userId);
        record.setQuestion(userMessage);
        record.setAnswer(aiAnswer);
        record.setCreateTime(LocalDateTime.now());
        chatRecordMapper.insert(record);

        return aiAnswer;
    }

    @PostConstruct
    public void initData() {
        System.out.println(">>> 正在通过官方 SDK 初始化数据...");
//        Pinecone pc = new Pinecone.Builder(apiKey).build();
//        String indexName = "zuel-finmind";
//        String cloud = "aws";
//        String region = "us-east-1";
//        String vectorType = "dense";
//        Map<String, String> tags = new HashMap<>();
//        tags.put("项目", "zuel");
//        tags.put("制作", "sworry");
//        pc.createServerlessIndex(
//                indexName,
//                "cosine",
//                1024,
//                cloud,
//                region,
//                DeletionProtection.DISABLED,
//                tags
//        );
//
//        PineconeConfig config = new PineconeConfig(apiKey);
//        config.setHost("INDEX_HOST");
//        PineconeConnection connection = new PineconeConnection(config);
//        Index index = new Index(config, connection, "INDEX_NAME");

        List<String> texts = List.of(
                "ZUEL (中南财经政法大学) 的王牌专业是会计学、金融学和法学。",
                "DeepSeek 是一家专注通用的 AI 公司，提供强大的推理模型。",
                "Pinecone 是一个云端向量数据库，官方 SDK 比 Spring 封装更灵活。"
        );

        for (int i = 0; i < texts.size(); i++) {
            String text = texts.get(i);

            List<Float> vector = getVector(text); // 智谱算向量

            if (vector != null) {
                // 构造 Metadata (把文本存进去)
                Struct metadata = Struct.newBuilder()
                                .putFields("text", Value.newBuilder().setStringValue(text).build())
                                .putFields("source", Value.newBuilder().setStringValue("init-job").build())
                                .build();

                try {
                    pineconeIndex.upsert("" + i, vector, null, null, metadata, "zuel-namespace");
                    System.out.println("✅ 成功！已上传 " + (i + 1) + " 条数据到 Pinecone。");
                } catch (Exception e) {
                    System.err.println("❌ 上传失败: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }

    }

    // === 【新增方法】 去档案室查历史记录 ===
    private List<Message> getHistoryMessages(String userId) {
        // 1.MyBatis-Plus查询构造器
        QueryWrapper<ChatRecord> query = new QueryWrapper<>();
        query.eq("user_id", userId) // 查当前客户
                .orderByDesc("create_time") // 按时间倒序（为了取最新的）
                .last("limit 10"); // 只取最近10条，防止上下文爆炸

        // 2.执行查询
        List<ChatRecord> records = chatRecordMapper.selectList(query);

        // 3.因为查出来是倒序的（最新->最旧），对话要按正序发（旧->新），所以要反转
        Collections.reverse(records);

        // 4.转换格式：Entity->SpringAI Message
        List<Message> messages = new ArrayList<>();
        for (ChatRecord record : records) {
            // 把“用户的历史问题”转成UserMessage
            messages.add(new UserMessage(record.getUserId()));
            // 把“AI的历史回答”转成AssistantMessage
            messages.add(new AssistantMessage(record.getAnswer()));
        }
        return messages;
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
}
