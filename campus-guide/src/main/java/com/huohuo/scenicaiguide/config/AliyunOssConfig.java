package com.huohuo.scenicaiguide.config;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 阿里云 OSS 客户端配置
 * <p>
 * access-key-id 为空串（未配置密钥的演示部署）时不装配，
 * OSSClientBuilder 对空密钥直接抛异常，必须挡在 bean 创建之前。
 * 不用 @ConditionalOnProperty 是因为它对空串也判命中，必须用表达式判非空白
 */
@Configuration
public class AliyunOssConfig {

    @Value("${aliyun.oss.endpoint}")
    private String endpoint;

    @Value("${aliyun.oss.access-key-id}")
    private String accessKeyId;

    @Value("${aliyun.oss.access-key-secret}")
    private String accessKeySecret;

    @Bean
    @ConditionalOnExpression("T(org.springframework.util.StringUtils).hasText('${aliyun.oss.access-key-id:}')")
    public OSS ossClient() {
        return new OSSClientBuilder().build(endpoint, accessKeyId, accessKeySecret);
    }
}
