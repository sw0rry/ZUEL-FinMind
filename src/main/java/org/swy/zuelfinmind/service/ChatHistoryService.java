package org.swy.zuelfinmind.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
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

    private static final String HISTORY_KEY_PREFIX = "finmind:history:";

    private final RedisTemplate<String, Object> redisTemplate;

    private final ChatRecordMapper chatRecordMapper;

    public ChatHistoryService(RedisTemplate<String, Object> redisTemplate, ChatRecordMapper chatRecordMapper) {
        this.redisTemplate = redisTemplate;
        this.chatRecordMapper = chatRecordMapper;
    }

    /**
     * 获取历史记录（Redis -> MySQL -> 回填）
     */
    public List<Message> getHistoryMessages(String userId) {
        String key = HISTORY_KEY_PREFIX + userId;
        List<Message> messages = new ArrayList<>();

        // 1.⚡ 先查 Redis (内存)
        Long size = redisTemplate.opsForList().size(key);
        if (size != null && size > 0) {
            List<Object> cachedHistory = redisTemplate.opsForList().range(key, 0, -1);
            // Redis里存的是“User：xxx”这种字符串，我们需要解析回Message对象
            // 为了简单，这里建议Redis只存纯文本，但为了给AI用，我们需要转对象
            // *简化策略*：这里演示直接走DB兜底的逻辑更稳，等熟练后再把Message序列化进Redis
        }

        // --- 暂时降级策略：为了不让代码太复杂，我们在Redis里只存String方便调试 ---
        // --- 真正的生产环境这里会直接把List<Message>序列化进去

        // 2.🐢 Redis没命中或逻辑复杂，直接查DB（原来逻辑）
        // （注：为了快速跑通Patch 1.0，我们先保持DB读取，下一版再完全把List<Message>塞入Redis）
        // 既然选择跑通，我们先保留原来DB逻辑作为核心，Redis用来做“频次限制”或“短期记忆”

        // ...此处保留原来DB逻辑...
        // 1.MyBatis-Plus查询构造器
        var query = new QueryWrapper<ChatRecord>();
        query.eq("user_id", userId) // 查当前客户
                .orderByDesc("create_time") // 按时间倒序（为了取最新的）
                .last("limit 3"); // 只取最近3条，防止上下文爆炸

        // 2.执行查询
        List<ChatRecord> records = chatRecordMapper.selectList(query);

        // 3.因为查出来是倒序的（最新->最旧），对话要按正序发（旧->新），所以要反转
        Collections.reverse(records);

        // 4.转换格式：Entity->SpringAI Message
        for (ChatRecord record : records) {
            // 把“用户的历史问题”转成UserMessage
            messages.add(new UserMessage(record.getQuestion()));
            // 把“AI的历史回答”转成AssistantMessage
            messages.add(new AssistantMessage(record.getAnswer()));
        }
        return messages;
    }

    /**
     * 保存对话（同时写入MySQL和Redis）
     */
    public void saveInteraction(String userId, String userQ,String aiA) {
        // 1.🐢 存 MySQL (永恒的记忆)
        ChatRecord record = new ChatRecord();
        record.setUserId(userId);
        record.setQuestion(userQ);
        record.setAnswer(aiA);
        record.setCreateTime(LocalDateTime.now());
        chatRecordMapper.insert(record);

        // 2.⚡ 存 Redis (为了下一次读取快)
        // 这里我们把最新的对话推入List
        String key = HISTORY_KEY_PREFIX + userId;
        String historyEntry = "Q:" + userQ + " | A:" + aiA;
        redisTemplate.opsForList().rightPush(key, historyEntry);

        // 只保留最近10条，防止内存爆炸
        if (redisTemplate.opsForList().size(key) > 10) {
            redisTemplate.opsForList().leftPop(key);
        }
        // 续命1小时
        redisTemplate.expire(key, 1,  TimeUnit.HOURS);
    }
}
