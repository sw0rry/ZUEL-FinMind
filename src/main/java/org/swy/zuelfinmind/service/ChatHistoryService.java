package org.swy.zuelfinmind.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.swy.zuelfinmind.entity.ChatRecord;
import org.swy.zuelfinmind.mapper.ChatRecordMapper;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class ChatHistoryService {

    private static final Logger log = LoggerFactory.getLogger(ChatHistoryService.class);
    private static final String HISTORY_KEY_PREFIX = "finmind:history:";

    // 限制历史上下文轮数（3轮 = 6条消息），避免Token爆炸
    private static final int MAX_HISTORY_ROUNDS = 3;

    // 注入Jackson用于把对象转成JSON字符串
    private final ObjectMapper objectMapper;

    // 建议使用String， String泛型，最稳健
    private final RedisTemplate<String, String> redisTemplate;

    private final ChatRecordMapper chatRecordMapper;

    public ChatHistoryService(ObjectMapper objectMapper, RedisTemplate<String, String> redisTemplate, ChatRecordMapper chatRecordMapper) {
        this.objectMapper = objectMapper;
        this.redisTemplate = redisTemplate;
        this.chatRecordMapper = chatRecordMapper;
    }

    /**
     * 内部类：数据胶囊（DTO）
     * 用来把一问一答打包成JSON,方便存Redis
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class HistoryNode {
        public String question;
        public String answer;

        // Jackson需要无参构造
        public HistoryNode() {}
        public HistoryNode(String question, String answer) {
            this.question = question;
            this.answer = answer;
        }
    }

    /**
     * 获取历史记录（Redis -> Miss -> MySQL -> 回填）
     */
    public List<Message> getHistoryMessages(String userId) {
        String key = HISTORY_KEY_PREFIX + userId;
        List<Message> messages = new ArrayList<>();

        // 打印一下，证明方法进来了
        System.out.println("🔍 [调试] 正在获取历史记录，UserID: " + userId);

        // 1.⚡ 先查 Redis (内存)
        try {
            // 获取列表所有内容（0 到 -1）
            List<String> cachedJsonList = redisTemplate.opsForList().range(key, 0, -1);

            if (cachedJsonList != null && !cachedJsonList.isEmpty()) {
//                log.info("✅ Redis 缓存命中: User [{}]", userId);
                // ---> 这里就是【命中】！！！ <---
                System.out.println("✅ [调试] Redis 命中！直接返回内存数据。条数：" + cachedJsonList.size());
                for (String json : cachedJsonList) {
                    // JSON 反序列化 -> HistoryNode对象
                    HistoryNode node = objectMapper.readValue(json, HistoryNode.class);
                    // 转成Spring AI的Message对象
                    messages.add(new UserMessage(node.question));
                    messages.add(new AssistantMessage(node.answer));
                }
                return messages; // 直接返回，不再查库
            }
        } catch (Exception e) {
            System.out.println("❌ [调试] Redis 报错：" + e.getMessage());
//            log.error("❌ Redis 读取/解析失败，降级查MySQL: {}", e.getMessage());
            // 不要抛出异常，继续走下面的数据库流程作为兜底
        }

        // 2.🐢 Redis没命中，查 MySQL (数据库兜底)
//        log.info("🐢 Redis 未命中，查询 MySQL: User [{}]", userId);
        System.out.println("🐢 [调试] Redis 未命中 (为空)，准备去查数据库...");
        // ...此处保留原来DB逻辑...
        // 1.MyBatis-Plus查询构造器
        var query = new QueryWrapper<ChatRecord>();
        query.eq("user_id", userId) // 查当前客户
                .orderByDesc("create_time") // 按时间倒序（为了取最新的）
                .last("limit " + MAX_HISTORY_ROUNDS); // 只取最近3条，防止上下文爆炸

        // 2.执行查询
        List<ChatRecord> records = chatRecordMapper.selectList(query);

        // 数据库没数据，就是真没了
        if (records.isEmpty()) {
            return messages;
        }

        // 3.因为查出来是倒序的（最新->最旧），对话要按正序发（旧->新），所以要反转
        Collections.reverse(records);

        // 4.🔄 【关键一步】缓存回填 (Cache Backfill)
        // 将查到的数据写回Redis，这样下一次请求就能命中了
        try {
            for (ChatRecord record : records) {
                // 转成Node
                HistoryNode node = new HistoryNode(record.getQuestion(), record.getAnswer());
                // 转成JSON
                String json = objectMapper.writeValueAsString(node);
                // 推入Redis
                redisTemplate.opsForList().rightPush(key, json);

                // 在最后回填 Redis 的地方也加一句：
                System.out.println("💾 [调试] 已从数据库查到并回填入 Redis");
            }
            // 设置过期时间（1小时）
            redisTemplate.expire(key, 1, TimeUnit.HOURS);
        } catch (Exception e) {
            log.error("❌ Redis 回填失败: {}", e.getMessage());
        }

        // 5.构造最终返回
        for (ChatRecord record : records) {
            messages.add(new UserMessage(record.getQuestion()));
            messages.add(new AssistantMessage(record.getAnswer()));
        }

        return messages;
    }

    /**
     * 保存对话（同时写入MySQL和Redis）
     */
    public void saveInteraction(String userId, String userQ,String aiA) {
        // 1.🐢 存 MySQL (永恒的记忆)
        try {
            ChatRecord record = new ChatRecord();
            record.setUserId(userId);
            record.setQuestion(userQ);
            record.setAnswer(aiA);
            record.setCreateTime(LocalDateTime.now());
            chatRecordMapper.insert(record);
        } catch (Exception e) {
            log.error("❌ MySQL 保存失败", e);
        }

        // 2.⚡ 存 Redis (为了下一次读取快)
        // 这里我们把最新的对话推入List
        try {
            String key = HISTORY_KEY_PREFIX + userId;

            // 构造对象 -> JSON
            HistoryNode node = new HistoryNode(userQ, aiA);
            String json = objectMapper.writeValueAsString(node);

            // 推入列表尾部（Right Push）
            redisTemplate.opsForList().rightPush(key, json);

            // 维护长度：如果超过限制，弹出最左边（最旧）的数据
            Long size = redisTemplate.opsForList().size(key);
            // 只保留最近10条，防止内存爆炸
            if (size !=null && size > MAX_HISTORY_ROUNDS) {
                redisTemplate.opsForList().leftPop(key);
            }
            // 续命1小时
            redisTemplate.expire(key, 1,  TimeUnit.HOURS);
        } catch (Exception e) {
            log.error("❌ Redis 保存失败", e);
        }

    }
}
