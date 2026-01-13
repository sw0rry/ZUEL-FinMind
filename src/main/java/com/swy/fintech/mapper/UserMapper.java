package com.swy.fintech.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.swy.fintech.entity.User;

// 不需要 @Mapper 注解了，因为启动类里加了 @MapperScan
public interface UserMapper extends BaseMapper<User> {
}