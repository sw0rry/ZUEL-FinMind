package org.swy.zuelfinmind.dto;

import lombok.Data;

@Data // Lombok,自动生成get/set方法
public class ChatRequest {

    // 用户ID
    private String userId;

    // 问题
    private String message;

    // 为什么没有createTime？因为我们要自己在Controller里生成当前时间，而不是获取前端传来的时间。
}
