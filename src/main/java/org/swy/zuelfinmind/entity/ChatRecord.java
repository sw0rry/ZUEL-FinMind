package org.swy.zuelfinmind.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("chat_record") // 绑定数据库表名
public class ChatRecord {

    @TableId(type = IdType.AUTO) // 对应数据库的AUTO_INCREMENT
    private Long id;

    private String userId;

    private String question;

    private String answer;

    private LocalDateTime createTime;
}
