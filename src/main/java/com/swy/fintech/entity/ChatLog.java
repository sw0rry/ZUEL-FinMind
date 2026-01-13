package com.swy.fintech.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("chat_log") // 对应数据库表名
public class ChatLog {
    @TableId(type = IdType.AUTO) // 告诉 MP，id 是自增的
    private Long id;

    private String userQuestion;
    private String aiResponse;
    private LocalDateTime createTime;
}