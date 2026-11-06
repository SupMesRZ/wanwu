package com.huohuo.scenicaiguide.config;

import io.qdrant.client.QdrantClient;
import io.qdrant.client.QdrantGrpcClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Qdrant 向量数据库客户端配置
 */
@Configuration
public class QdrantConfig {

    @Value("${qdrant.host:localhost}")
    private String host;

    @Value("${qdrant.port:6333}")
    private int port;

    /** API Key，留空表示无认证 */
    @Value("${qdrant.api-key:}")
    private String apiKey;

    /** 是否启用 TLS，默认 true */
    @Value("${qdrant.use-tls:true}")
    private boolean useTls;

    /** 请求超时（秒），默认 10 秒 */
    @Value("${qdrant.timeout-seconds:10}")
    private long timeoutSeconds;

    @Bean
    public QdrantClient qdrantClient() {
        QdrantGrpcClient.Builder builder = QdrantGrpcClient.newBuilder(host, port, useTls)
                .withTimeout(Duration.ofSeconds(timeoutSeconds));
        if (apiKey != null && !apiKey.isBlank()) {
            builder.withApiKey(apiKey);
        }
        return new QdrantClient(builder.build());
    }
}
