package org.swy.zuelfinmind.service;

import com.huaban.analysis.jieba.JiebaSegmenter;
import com.huaban.analysis.jieba.SegToken;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.swy.zuelfinmind.model.VectorSearchResult;
import org.swy.zuelfinmind.service.strategy.impl.PineconeVectorStore;
import reactor.core.publisher.Flux;

import java.util.*;
import java.util.stream.Collectors;

@Service // 1.告诉Spring：这是“专家”，请开机时把它实例化放到容器里
public class DeepSeekService {

    // 依赖注入
    private final ChatModel chatModel;

    // ✅ 注入新的后勤官
    private final ChatHistoryService historyService;

    private final PineconeVectorStore vectorStore;

    // 实例化分词器（线程安全，可以做成成员变量）
    private final JiebaSegmenter segmenter =  new JiebaSegmenter();

    // 定义停用词表 (过滤掉没用的字，防止噪音干扰)
    private static final Set<String> STOP_WORDS = Set.of(
            "的", "了", "和", "是", "就", "都", "而", "及", "与", "在", "这", "那", "有", "什么", "怎么", "我", "你", "它"
             // 可选：如果每个文档都有ZUEL，那它就不是区分特征，可以过滤
    );

    // 构造函数注入：Spring会自动把ChatModel递给你
    public DeepSeekService(ChatModel chatModel, ChatHistoryService historyService, PineconeVectorStore vectorStore) {
        this.chatModel = chatModel;
        this.historyService = historyService;
        this.vectorStore = vectorStore;
    }

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

        List<Message> historyMessages = historyService.getHistoryMessages(userId);

        // 3.准备”面包底层“：知识库 + 当前提问
        List<VectorSearchResult> candidates = vectorStore.search(userMessage);

        // ---------------------------------------------------------
        // 🔧 【升级点 2】：引入 Java 内存重排序
        // ---------------------------------------------------------
        List<String> bestChunks = rerank(candidates, userMessage);

        String context = String.join("\n\n", bestChunks);

        // 3. 打印出来看看 (这就是我们要喂给 AI 的背景资料)
        System.out.println("🤖 RAG 检索到的干货:\n" + context);

        // -----------------------------------------------------------
        // 🔧 【修复点】：根据是否查到资料，动态调整指令
        // -----------------------------------------------------------
        String finalUserMsg;
        if (context.trim().isEmpty()) {
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
                .doOnError(e -> System.err.println("❌ 流式生成中断：" + e.getMessage()));
    }

    /**
     * 🆕 核心功能：上传文件 -> 解析 -> 切块 -> 向量化 -> 存库
     */
    public String uploadAndLearn(MultipartFile file) {
        return vectorStore.store(file);
    }

    /**
     * 🧠 核心算法：内存重排序 (Hybrid Rerank)
     * 结合了“向量相似度”和“关键词匹配度”
     * 🔄 修复版 Rerank：引入真正的中文分词
     */
    private List<String> rerank(List<VectorSearchResult> candidates, String userMessage) {

        // 简单分词：把用户问题按空格或标点切开（简易版，不需要引入 Jieba）
        // 比如“ZUEL新增了什么实验班” -> ["ZUEL", "新增", "了", "什么", "实验班"]
//        String[] keywords = userMessage.split("[\\s,?.!，。？！]+");



        // --- 🟢 变化点 1：使用结巴分词 ---
        // SegMode.SEARCH 用于搜索引擎模式，切得比较细
        List<SegToken> tokens = segmenter.process(userMessage, JiebaSegmenter.SegMode.SEARCH);

        // 提取关键词列表
        // 核心优化：将 List 转为 HashSet，把 contains 方法的时间复杂度从 O(n) 降到 O(1)
        Set<String> keywords = new HashSet<>();
        for (SegToken token : tokens) {
            String word = token.word;
            // 过滤规则：长度大于1且不在停用此表中
            if (word.length() > 1 && !STOP_WORDS.contains(word)) {
                keywords.add(word);
            }
        }

        return candidates.stream()
                .map(candidate -> {
                    long hitCounts = keywords.stream()
                            .filter(keyword -> candidate.getText().contains(keyword))
                            .count();

                    double keywordScore = Math.min(hitCounts / 3.0, 1.0);

                    float score = (float) ((candidate.getScore() * 0.8) + (keywordScore * 0.2));

                    System.out.printf("文本前缀：%s | 原始向量得分：%.2f | 关键词命中数：%d | 最终得分：%.2f \n",
                            candidate.getText().substring(0, Math.min (candidate.getText().length (), 20)), // 更长前缀，避免越界
                            candidate.getScore(),
                            hitCounts,
                            score);

                    return new VectorSearchResult(candidate.getText(), score, candidate.getSource());
                })
                .filter(candidate -> candidate.getScore() > 0.65)// 智谱0.4，集成0.65
                .sorted((a,b) -> Float.compare(b.getScore(), a.getScore()))
                .limit(5)
                .map(VectorSearchResult::getText)
                .collect(Collectors.toList());
    }
}
