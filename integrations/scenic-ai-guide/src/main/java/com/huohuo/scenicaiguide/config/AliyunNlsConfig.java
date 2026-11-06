package com.huohuo.scenicaiguide.config;

import com.alibaba.nls.client.AccessToken;
import com.alibaba.nls.client.protocol.NlsClient;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 阿里云 NLS 智能语音交互配置（ASR/TTS）
 * <p>
 * 官方建议：NlsClient 全局复用，AccessToken 在过期前刷新
 * <p>
 * access-key-id 为空串（未配置密钥的演示部署）时两个 bean 都不装配，
 * 避免启动即发起 token 网络申请导致无密钥环境起不来；
 * 语音服务通过 ObjectProvider 感知缺省并降级。
 * 不用 @ConditionalOnProperty 是因为它对空串也判命中，必须用表达式判非空白
 */
@Configuration
public class AliyunNlsConfig {

    /**
     * NLS 应用 AppKey，在智能语音交互控制台获取
     */
    @Getter
    @Value("${aliyun.nls.app-key}")
    private String appKey;

    @Value("${aliyun.nls.access-key-id}")
    private String accessKeyId;

    @Value("${aliyun.nls.access-key-secret}")
    private String accessKeySecret;

    /**
     * NLS 网关地址（上海外网）
     */
    private static final String NLS_URL = "wss://nls-gateway.cn-shanghai.aliyuncs.com/ws/v1";

    /**
     * 创建 AccessToken，用于 NLS 服务鉴权
     * <p>
     * token 有效期见 {@link AccessToken#getExpireTime()}，过期前需重新获取
     */
    @Bean
    @ConditionalOnExpression("T(org.springframework.util.StringUtils).hasText('${aliyun.nls.access-key-id:}')")
    public AccessToken accessToken() throws Exception {
        // 测试accessKeyId和accessKeySecret能否正常访问
        if(accessKeyId == null || accessKeyId.isBlank()){
            throw new IllegalStateException("请配置 accessKeyId 和 accessKeySecret");
        }
        AccessToken token = new AccessToken(accessKeyId, accessKeySecret);
        token.apply();
        if(token.getToken() == null){
            throw new IllegalStateException("阿里云智能语音交互SDK获取Token失败");
        }
        return token;
    }

    /**
     * NLS 客户端，用于语音合成和识别
     * <p>
     * NlsClient 内部管理 WebSocket 连接池，线程安全，建议全局仅创建一个实例
     */
    @Bean
    @ConditionalOnExpression("T(org.springframework.util.StringUtils).hasText('${aliyun.nls.access-key-id:}')")
    public NlsClient nlsClient(AccessToken accessToken) {
        return new NlsClient(NLS_URL, accessToken.getToken());
    }
}
