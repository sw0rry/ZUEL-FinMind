package com.swy.fintech.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.swy.fintech.entity.ChatLog;
// 启动类已经加了 @MapperScan，这里不用加注解，直接继承接口就行
public interface ChatLogMapper extends BaseMapper<ChatLog> {
}