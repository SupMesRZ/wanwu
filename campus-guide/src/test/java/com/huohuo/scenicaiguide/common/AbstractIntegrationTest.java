package com.huohuo.scenicaiguide.common;

import org.junit.jupiter.api.Tag;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

/**
 * 集成测试基类：Testcontainers 自包含 MySQL 与 Redis
 *
 * <p>静态容器 JVM 内共享一次启动，由 Ryuk 负责销毁；MySQL 用 sql/init.sql 建全量 DDL，
 * 域配置由应用自身的初始化器播种。集成层从此脱离本地 Redis 与远程共享库，
 * 跑集成测试只需要 Docker，不再需要 scenic-redis 容器和 group_db 可达
 */
@Tag(TestTags.INTEGRATION)
@SpringBootTest
public abstract class AbstractIntegrationTest {

    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>(DockerImageName.parse("mysql:8.0"))
        .withDatabaseName("group_db")
        .withUsername("test")
        .withPassword("test")
        .withUrlParam("allowPublicKeyRetrieval", "true")
        .withUrlParam("useSSL", "false")
        .withCopyFileToContainer(MountableFile.forHostPath("sql/init.sql"), "/docker-entrypoint-initdb.d/init.sql");

    private static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine"))
        .withExposedPorts(6379);

    static {
        MYSQL.start();
        REDIS.start();
    }

    @DynamicPropertySource
    static void containerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }
}
