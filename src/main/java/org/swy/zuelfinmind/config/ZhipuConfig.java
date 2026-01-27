package org.swy.zuelfinmind.config;

import ai.z.openapi.ZhipuAiClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Configuration
public class ZhipuConfig {

    @Value("${YOUR_EB_KEY}")
    private String apiKey;

    @Bean
    public ZhipuAiClient zhipuAiClient() {
        return ZhipuAiClient.builder().ofZHIPU()
                .apiKey(apiKey)
                .networkConfig(
                        30, // 连接超时（connect）
                        60, // 读取超时（read）- Embedding有时候慢，给多点
                        60, // 写入超时（write）
                        200, // 【关键修改】总超时 (Call Timeout)。 200 > 30+60+60，安全！
                        TimeUnit.SECONDS) // 设置超时时间
                .build();
    }
}
