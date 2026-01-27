package org.swy.zuelfinmind.service;

import ai.z.openapi.ZhipuAiClient;
import ai.z.openapi.service.embedding.EmbeddingCreateParams;
import ai.z.openapi.service.embedding.EmbeddingResponse;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import jakarta.annotation.PostConstruct;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;
import org.swy.zuelfinmind.entity.ChatRecord;
import org.swy.zuelfinmind.mapper.ChatRecordMapper;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service // 1.告诉Spring：这是“专家”，请开机时把它实例化放到容器里
public class DeepSeekService {

    // 依赖注入
    private final ChatModel chatModel;

    // 引入档案管理员（Mapper）
    private final ChatRecordMapper chatRecordMapper;

    // 注入官方客户端
    private final ZhipuAiClient zhipuAiClient;

    //注入新服务
    private final KnowledgeBaseService kbService;

    // 构造函数注入：Spring会自动把ChatModel递给你
    public DeepSeekService(ChatModel chatModel, ChatRecordMapper chatRecordMapper, ZhipuAiClient zhipuAiClient, KnowledgeBaseService kbService) {
        this.chatModel = chatModel;
        this.chatRecordMapper = chatRecordMapper;
        this.zhipuAiClient = zhipuAiClient;
        this.kbService = kbService;
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

        // 3.准备”面包底层“：知识库 + 当前提问
        String finalUserMsg = userMessage;
        // 新增知识库
        //  1.先去“书架”里找找有没有相关“小抄”
        String context = kbService.search(userMessage);
        //  2.如果找到“小抄”就把它“夹带”在问题里
        if (!"未找到相关知识".equals(context)) {
            System.out.println("🤖 RAG 触发！已注入背景知识: " + context);
            // 【关键咒语】告诉 AI：这是背景资料，请根据这个回答，不要瞎编。
            finalUserMsg  = String.format(
                    "【背景资料】：%s\n\n【用户问题】：%s\n\n请根据背景资料回答问题。如果资料里没有答案，就说不知道。",
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

    // 新增一个启动自测方法
    // @PostConstruct表示：当这个类创建好之后，自动运行这个方法
//    @PostConstruct
//    public void testEmbedding() {
//        System.out.println(">>> 正在测试 Embedding（Master稳定版）...");
//        try {
//
//            // 组装官方请求对象
//            EmbeddingCreateParams request = new EmbeddingCreateParams();
//            request.setModel("embedding-3");
//            request.setDimensions(1024);
//            request.setInput("ZUEL");
//
//            // 发送请求
//            EmbeddingResponse response = zhipuAiClient.embeddings().createEmbeddings(request);
//
//            if (response.isSuccess()) {
//                // 1. 第一层 getData(): 拿到数据包装类
//                // 2. 第二层 getData(): 拿到 List<Embedding> (这就是你问的那个 List)
//                // 3. get(0): 因为我们只发了一句话，所以取第一个
//                // 4. getEmbedding(): 这才是真正的向量 List<Double>
//                List<Double> vectorList = response.getData().getData().get(0).getEmbedding();
//
//                System.out.println(">>> 成功！向量长度: " + vectorList.size());
//
//                // 打印前5位看看
//                System.out.print(">>> 前5位: [");
//                for (int i = 0; i < 5 && i < vectorList.size(); i++) {
//                    System.out.print(vectorList.get(i) + ", ");
//                }
//                System.out.println("...]");
//
//            } else {
//                System.err.println(">>> 调用失败: " + response.getMsg());
//            }
//
//        } catch (Exception e) {
//            System.err.println(">>> Embedding 测试失败: " + e.getMessage());
//        }
//    }

    @PostConstruct
    public void testRAG() {
        System.out.println(">>> 正在初始化知识库...");

        // 1. 模拟存入一些只有你知道的“私有知识”
        kbService.addDocument("ZUEL是中南财经政法大学的简称，位于武汉。");
        kbService.addDocument("Java不仅能写后端，还能通过Spring AI开发大模型应用。");
        kbService.addDocument("小明的身高是180cm，喜欢唱跳rap。"); // 干扰项

        System.out.println(">>> --------------------------------");

        // 2.模拟提问
        String query = "ZUEL在哪里？";
        System.out.println(">>> 提问: " + query);
        String result = kbService.search(query);
        System.out.println(">>> 最终答案: " + result);
    }
}
