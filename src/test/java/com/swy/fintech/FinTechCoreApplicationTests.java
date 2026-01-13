package com.swy.fintech;

import com.swy.fintech.entity.User;
import com.swy.fintech.mapper.UserMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class FintechApplicationTests {

    @Autowired
    private UserMapper userMapper;

    @Test
    void testInsert() {
        System.out.println(("----- 开始测试插入 -----"));
        User user = new User();
        user.setId(2L); // 刚才用了1，这次用2
        user.setName("Zuel-Student");
        user.setAge(22);
        user.setEmail("student@zuel.edu.cn");

        int result = userMapper.insert(user);
        System.out.println("成功插入条数: " + result);
    }
}