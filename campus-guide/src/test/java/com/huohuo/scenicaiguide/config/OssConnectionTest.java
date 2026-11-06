package com.huohuo.scenicaiguide.config;

import com.huohuo.scenicaiguide.common.TestTags;
import org.junit.jupiter.api.Tag;

import com.aliyun.oss.OSS;
import com.aliyun.oss.model.Bucket;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 阿里云 OSS 连通性测试
 */
@Tag(TestTags.EXTERNAL)
@SpringBootTest
class OssConnectionTest {

    @Autowired
    private OSS ossClient;

    @Value("${aliyun.oss.bucket-name}")
    private String bucketName;

    @Test
    void testListBuckets() {
        List<Bucket> buckets = ossClient.listBuckets();
        assertNotNull(buckets);

        boolean exists = buckets.stream()
                .anyMatch(b -> b.getName().equals(bucketName));

        System.out.println("OSS 连通性正常，共 " + buckets.size() + " 个 bucket");
        System.out.println("Bucket '" + bucketName + "' 存在: " + exists);

        assertTrue(exists, "配置的 bucket '" + bucketName + "' 应在 OSS 中存在");
    }
}
