package org.swy.zuelfinmind.service;

import ai.z.openapi.ZhipuAiClient;
import ai.z.openapi.service.embedding.EmbeddingCreateParams;
import ai.z.openapi.service.embedding.EmbeddingResponse;
import ai.z.openapi.service.embedding.EmbeddingResult;
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
import org.swy.zuelfinmind.SimpleEmbeddingClient;
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

    // 新增：注入Embedding工具
    private final SimpleEmbeddingClient embeddingClient;

//    // 注入官方客户端
//    private final ZhipuAiClient zhipuAiClient;

    // 构造函数注入：Spring会自动把ChatModel递给你
    public DeepSeekService(ChatModel chatModel, ChatRecordMapper chatRecordMapper, SimpleEmbeddingClient embeddingClient) {
        this.chatModel = chatModel;
        this.chatRecordMapper = chatRecordMapper;
        this.embeddingClient = embeddingClient;
    }

//////    // 业务方法：真正打电话给DeepSeek
//////    public String chat(String userMessage) {
//////        // call()方法就是拨号，它会阻塞等待AI回复
//////        // 这里的return就是AI说的话
//////        return chatModel.call(userMessage);
//////    }
////
////    //多加userId参数
////    public String chat(String userId, String userMessage) {
////        // 1.先办正事：给AI打电话
////        String aiAnswer = chatModel.call(userMessage);
////
////        // 2.拿到结果后，开始记账
////        ChatRecord record = new ChatRecord();
////        record.setUserId(userId);
////        record.setQuestion(userMessage);
////        record.setAnswer(aiAnswer);
////        record.setCreateTime(LocalDateTime.now()); // 盖上当前时间戳
////
////        // 3.呼叫管理员存档
////        chatRecordMapper.insert(record);
////
////        // 4.返回答案
////        return aiAnswer;
////    }
//
//    public String chat(String userId, String userMessage) {
//        // 1.准备“洗脑”指令（System Message）
//        // 这里的设定可以修改
//        String systemText = """
//                你是一个名为 'ZUEL-FinMind' 的专业金融AI助手，由中南财经政法大学(ZUEL)的学生开发。
//                            你的核心原则：
//                            1. 只回答金融、经济、编程或数据分析相关的问题。
//                            2. 如果用户问与金融无关的问题（如做菜、娱乐），请礼貌但坚决地拒绝，并引导他们回到金融话题。
//                            3. 回答要简短精炼，多用数据说话，避免长篇大论。
//                """;
//        SystemMessage systemMsg = new SystemMessage(systemText);
//
//        // 2.准备用户提问
//        UserMessage userMsg = new UserMessage(userMessage);
//
//        // 3.把两页纸放进一个文件夹（Prompt）
//        // List.of是Java 9+的新语法，创建一个不可变列表
//        Prompt prompt = new Prompt(List.of(systemMsg, userMsg));
//
//        // 4.打电话给AI（注意：这里返回的是复杂的Response对象，不是简单的String了）
//        ChatResponse response = chatModel.call(prompt);
//
//        // 5.拆信封，拿出AI说的内容
//        String aiAnswer = response.getResult().getOutput().getText();
//
//        // 6.记账（持久化）
//        ChatRecord record = new ChatRecord();
//        record.setUserId(userId);
//        record.setQuestion(userMessage);
//        record.setAnswer(aiAnswer);
//        record.setCreateTime(LocalDateTime.now());
//        chatRecordMapper.insert(record);
//
//        return aiAnswer;
//    }

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

        // 3.准备”面包底层“：当前提问
        UserMessage currentUserMsg = new UserMessage(userMessage);

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
    @PostConstruct
    public void testEmbedding() {
        System.out.println(">>> 正在测试 Embedding(Plan C: 手写 HTTP 版)...");
        try {
            // 尝试把“ZUEL”变成向量
            float[] vector = embeddingClient.embed("ZUEL");

            System.out.println(">>> 成功！向量长度: " + vector.length); // 数组用 .length
            // 只打印前 5 个数字看看
            System.out.print(">>> 前5位数据: [");
            for (int i = 0; i < 5 && i < vector.length; i++) {
                System.out.print(vector[i] + ", ");
            }
            System.out.println("...]");

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

        } catch (Exception e) {
            System.err.println(">>> Embedding 测试失败: " + e.getMessage());
            System.err.println(">>> 提示：可能是 DeepSeek 不支持 embedding 接口，我们需要换一家供应商。");
        }
    }
}
