package org.swy.zuelfinmind.config;

import io.pinecone.clients.Index;
import io.pinecone.configs.PineconeConfig;
import io.pinecone.configs.PineconeConnection;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PcConfig {

    @Value("${YOUR_EDB_KEY}")
    private String apiKey;

//    @Bean
//    public Pinecone pineconeClient() {
//        // 初始化客户端
//        Pinecone pc = new Pinecone.Builder(apiKey).build();
//        String indexName = "zuel-finmind";
//        String cloud = "aws";
//        String region = "us-east-1";
//        String vectorType = "dense";
//        Map<String, String> tags = new HashMap<>();
//        tags.put("project", "zuel");
//        tags.put("maker", "sworry");
//        pc.createServerlessIndex(
//                indexName,
//                "cosine",
//                1024,
//                cloud,
//                region,
//                DeletionProtection.DISABLED,
//                tags
//        );
//        return pc;
//    }

    @Bean
    public Index pineconeIndex() {
        // 获取索引连接
        PineconeConfig config = new PineconeConfig(apiKey);
        config.setHost("https://zuel-finmind-w81fj87.svc.aped-4627-b74a.pinecone.io");
        PineconeConnection connection = new PineconeConnection(config);

        return new Index(config, connection, "zuel-finmind");
    }
}
