package com.swy.fintech;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.swy.fintech.mapper") // 扫描你的Mapper包
public class FintechApplication {
    public static void main(String[] args) {
        SpringApplication.run(FintechApplication.class, args);
    }
}