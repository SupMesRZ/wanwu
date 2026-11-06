package com.huohuo.scenicaiguide.config;

import com.huohuo.scenicaiguide.common.TestTags;
import org.junit.jupiter.api.Tag;

import com.alibaba.nls.client.protocol.NlsClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * NLS 测试
 */
@Tag(TestTags.EXTERNAL)
@SpringBootTest
public class NlsTest {
    @Autowired
    private NlsClient nlsClient;

    @Test
    void testNlsClientInitialized() {
        System.out.println(nlsClient);
    }
}
