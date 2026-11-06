package com.huohuo.scenicaiguide.qa;

import com.huohuo.scenicaiguide.common.TestTags;
import org.junit.jupiter.api.Tag;

import io.qdrant.client.QdrantClient;
import io.qdrant.client.grpc.Collections;
import io.qdrant.client.grpc.Points;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import java.util.Arrays;
import java.util.List;
import static io.qdrant.client.PointIdFactory.id;
import static io.qdrant.client.ValueFactory.value;
import static io.qdrant.client.VectorFactory.vector;
import static io.qdrant.client.VectorsFactory.vectors;
import static org.junit.jupiter.api.Assertions.*;

@Tag(TestTags.EXTERNAL)
@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class QdrantConnectionTest {

    @Autowired
    private QdrantClient qdrantClient;

    private static final String TEST_COLLECTION = "test_scenic";
    private static final int VECTOR_SIZE = 4;

    @Test
    @Order(1)
    @DisplayName("连通性：列出所有 Collection")
    void testListCollections() throws Exception {
        List<String> collections = qdrantClient.listCollectionsAsync().get();
        assertNotNull(collections);
    }

    @Test
    @Order(2)
    @DisplayName("Collection 生命周期：创建 → 存在 → 删除")
    void testCollectionLifecycle() throws Exception {
        // 如果已存在先清理
        List<String> existing = qdrantClient.listCollectionsAsync().get();
        if (existing.contains(TEST_COLLECTION)) {
            qdrantClient.deleteCollectionAsync(TEST_COLLECTION).get();
        }

        // 创建
        qdrantClient.createCollectionAsync(TEST_COLLECTION,
                Collections.VectorParams.newBuilder()
                        .setSize(VECTOR_SIZE)
                        .setDistance(Collections.Distance.Cosine)
                        .build()
        ).get();

        // 验证存在
        List<String> afterCreate = qdrantClient.listCollectionsAsync().get();
        assertTrue(afterCreate.contains(TEST_COLLECTION));

        // 删除
        qdrantClient.deleteCollectionAsync(TEST_COLLECTION).get();

        // 验证已删除
        List<String> afterDelete = qdrantClient.listCollectionsAsync().get();
        assertFalse(afterDelete.contains(TEST_COLLECTION));
    }

    @Test
    @Order(3)
    @DisplayName("向量写入与相似度检索")
    void testUpsertAndSearch() throws Exception {
        // 确保 Collection 存在
        List<String> existing = qdrantClient.listCollectionsAsync().get();
        if (!existing.contains(TEST_COLLECTION)) {
            qdrantClient.createCollectionAsync(TEST_COLLECTION,
                    Collections.VectorParams.newBuilder()
                            .setSize(VECTOR_SIZE)
                            .setDistance(Collections.Distance.Cosine)
                            .build()
            ).get();
        }

        // 插入 3 条测试向量（使用工厂方法简化构建）
        List<Points.PointStruct> points = Arrays.asList(
                Points.PointStruct.newBuilder()
                        .setId(id(1))
                        .setVectors(vectors(vector(1.0f, 0.0f, 0.0f, 0.0f)))
                        .putPayload("name", value("断桥"))
                        .build(),
                Points.PointStruct.newBuilder()
                        .setId(id(2))
                        .setVectors(vectors(vector(0.0f, 1.0f, 0.0f, 0.0f)))
                        .putPayload("name", value("雷峰塔"))
                        .build(),
                Points.PointStruct.newBuilder()
                        .setId(id(3))
                        .setVectors(vectors(vector(0.9f, 0.1f, 0.0f, 0.0f)))
                        .putPayload("name", value("望断桥"))
                        .build()
        );

        qdrantClient.upsertAsync(TEST_COLLECTION, points).get();

        // 构建 SearchPoints 请求，withPayload 必须显式开启否则 payload 不返回
        Points.SearchPoints searchRequest = Points.SearchPoints.newBuilder()
                .setCollectionName(TEST_COLLECTION)
                .addAllVector(Arrays.asList(0.95f, 0.05f, 0.0f, 0.0f))
                .setLimit(2)
                .setWithPayload(
                        Points.WithPayloadSelector.newBuilder().setEnable(true).build()
                )
                .build();

        List<Points.ScoredPoint> results = qdrantClient.searchAsync(searchRequest).get();

        assertEquals(2, results.size());

        // 第一个结果应该是断桥（向量最接近）
        String firstName = results.getFirst().getPayloadMap().get("name").getStringValue();
        assertEquals("断桥", firstName);

        // 清理
        qdrantClient.deleteCollectionAsync(TEST_COLLECTION).get();
    }
}
