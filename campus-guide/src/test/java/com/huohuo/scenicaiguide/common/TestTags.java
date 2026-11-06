package com.huohuo.scenicaiguide.common;

/**
 * 测试分层标签
 *
 * <p>离线单测是默认层不需要标注；INTEGRATION 依赖 Spring 容器与本地 Redis / 远程 MySQL；
 * EXTERNAL 调用真实云服务（NLS/OSS/LLM/Qdrant），mvn test 默认排除，用 -Pexternal-tests 启用
 */
public final class TestTags {

    public static final String INTEGRATION = "integration";
    public static final String EXTERNAL = "external";

    private TestTags() {
    }
}
