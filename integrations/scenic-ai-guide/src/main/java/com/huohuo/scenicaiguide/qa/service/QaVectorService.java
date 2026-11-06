package com.huohuo.scenicaiguide.qa.service;

import com.huohuo.scenicaiguide.qa.entity.QaKnowledgeChunk;
import com.huohuo.scenicaiguide.system.service.ConfigService;
import io.qdrant.client.ConditionFactory;
import io.qdrant.client.PointIdFactory;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.ValueFactory;
import io.qdrant.client.VectorsFactory;
import io.qdrant.client.grpc.Collections;
import io.qdrant.client.grpc.Common;
import io.qdrant.client.grpc.Points;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 向量检索增强（P1-18）。开关 qa-vector-enabled 默认关闭，关闭时所有方法安全无操作，
 * 部署不需要 Qdrant；开启后 reindex 真 embedding 写入 Qdrant，问答检索为向量召回 +
 * 关键词召回合并。检索路径任何异常都降级为空召回，由调用方回落纯关键词；
 * 写入路径异常显式抛出，由调用方把切片标记 FAILED——写入失败不该静默
 */
@Service
public class QaVectorService {

    private static final Logger log = LoggerFactory.getLogger(QaVectorService.class);

    public static final String ENABLED_KEY = "qa-vector-enabled";
    public static final String COLLECTION_KEY = "qa-vector-collection";
    public static final String DIMENSION_KEY = "qa-vector-dimension";
    public static final String TOP_K_KEY = "qa-rag-top-k";
    public static final String THRESHOLD_KEY = "qa-rag-similarity-threshold";
    private static final String DEFAULT_COLLECTION = "qa_chunks";
    private static final int DEFAULT_DIMENSION = 1024;
    private static final int DEFAULT_TOP_K = 5;
    private static final double DEFAULT_THRESHOLD = 0.7;
    private static final long RPC_TIMEOUT_SECONDS = 15;

    private final EmbeddingModel embeddingModel;
    private final QdrantClient qdrantClient;
    private final ConfigService configService;
    /** 已确认存在的集合，避免每次写入都发一次存在性检查 */
    private final Set<String> ensuredCollections = ConcurrentHashMap.newKeySet();

    public QaVectorService(ObjectProvider<EmbeddingModel> embeddingModelProvider,
                           ObjectProvider<QdrantClient> qdrantClientProvider,
                           ConfigService configService) {
        this.embeddingModel = embeddingModelProvider.getIfAvailable();
        this.qdrantClient = qdrantClientProvider.getIfAvailable();
        this.configService = configService;
    }

    public boolean enabled() {
        if (embeddingModel == null || qdrantClient == null) {
            return false;
        }
        try {
            return configService.getBoolean(ENABLED_KEY, false);
        } catch (Exception e) {
            log.warn("读取 {} 失败，按关闭处理: {}", ENABLED_KEY, e.getMessage());
            return false;
        }
    }

    /**
     * reindex 调用：整篇文档的切片向量化后写入 Qdrant，点 ID 即切片 ID。
     * 失败抛 VectorIndexException，由调用方把文档与切片标记 FAILED
     */
    public void upsertChunks(Long docId, Long scenicAreaId, List<QaKnowledgeChunk> chunks) {
        if (!enabled() || chunks == null || chunks.isEmpty()) {
            return;
        }
        try {
            ensureCollection();
            List<String> texts = chunks.stream().map(QaKnowledgeChunk::getContent).toList();
            List<float[]> vectors = embeddingModel.embed(texts);
            List<Points.PointStruct> points = new ArrayList<>();
            for (int index = 0; index < chunks.size(); index++) {
                QaKnowledgeChunk chunk = chunks.get(index);
                points.add(Points.PointStruct.newBuilder()
                    .setId(PointIdFactory.id(chunk.getId()))
                    .setVectors(VectorsFactory.vectors(vectors.get(index)))
                    .putAllPayload(Map.of(
                        "doc_id", ValueFactory.value(docId),
                        "scenic_area_id", ValueFactory.value(scenicAreaId),
                        "chunk_id", ValueFactory.value(chunk.getId())))
                    .build());
            }
            qdrantClient.upsertAsync(collection(), points).get(RPC_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new VectorIndexException("切片向量化写入失败: " + e.getMessage(), e);
        }
    }

    /** 删除文档时同步清掉向量点。删除失败只告警，不阻断文档删除 */
    public void deleteByDoc(Long docId) {
        if (!enabled()) {
            return;
        }
        try {
            qdrantClient.deleteAsync(collection(), Common.Filter.newBuilder()
                    .addMust(ConditionFactory.match("doc_id", docId))
                    .build())
                .get(RPC_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("删除文档 {} 的向量点失败，残留点不影响检索正确性: {}", docId, e.getMessage());
        }
    }

    /**
     * 问答调用：问题向量化后按景区过滤召回。任何异常都降级为空列表，
     * 由调用方回落纯关键词检索——向量是增强，不是单点
     */
    public List<VectorHit> search(String question, Long scenicAreaId) {
        if (!enabled() || question == null || question.isBlank()) {
            return List.of();
        }
        try {
            float[] vector = embeddingModel.embed(question);
            List<Points.ScoredPoint> points = qdrantClient.searchAsync(Points.SearchPoints.newBuilder()
                    .setCollectionName(collection())
                    .addAllVector(toFloatList(vector))
                    .setLimit(topK())
                    .setFilter(Common.Filter.newBuilder()
                        .addMust(ConditionFactory.match("scenic_area_id", scenicAreaId))
                        .build())
                    .build())
                .get(RPC_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            double threshold = threshold();
            return points.stream()
                .filter(point -> point.getScore() >= threshold)
                .map(point -> new VectorHit(point.getId().getNum(), point.getScore()))
                .toList();
        } catch (Exception e) {
            log.warn("向量检索失败，降级关键词检索: {}", e.getMessage());
            return List.of();
        }
    }

    private void ensureCollection() throws Exception {
        String name = collection();
        if (ensuredCollections.contains(name)) {
            return;
        }
        Boolean exists = qdrantClient.collectionExistsAsync(name).get(RPC_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        if (!Boolean.TRUE.equals(exists)) {
            qdrantClient.createCollectionAsync(name, Collections.VectorParams.newBuilder()
                    .setSize(dimension())
                    .setDistance(Collections.Distance.Cosine)
                    .build())
                .get(RPC_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            log.info("已创建 Qdrant 集合 {}（{} 维 Cosine）", name, dimension());
        }
        ensuredCollections.add(name);
    }

    private String collection() {
        try {
            String value = configService.getString(COLLECTION_KEY, DEFAULT_COLLECTION);
            return value == null || value.isBlank() ? DEFAULT_COLLECTION : value.trim();
        } catch (Exception e) {
            return DEFAULT_COLLECTION;
        }
    }

    private int dimension() {
        try {
            return Math.max(1, configService.getInt(DIMENSION_KEY, DEFAULT_DIMENSION));
        } catch (Exception e) {
            return DEFAULT_DIMENSION;
        }
    }

    private int topK() {
        try {
            return Math.max(1, configService.getInt(TOP_K_KEY, DEFAULT_TOP_K));
        } catch (Exception e) {
            return DEFAULT_TOP_K;
        }
    }

    private double threshold() {
        try {
            return configService.getDouble(THRESHOLD_KEY, DEFAULT_THRESHOLD);
        } catch (Exception e) {
            return DEFAULT_THRESHOLD;
        }
    }

    private static List<Float> toFloatList(float[] vector) {
        List<Float> result = new ArrayList<>(vector.length);
        for (float value : vector) {
            result.add(value);
        }
        return result;
    }

    public record VectorHit(long chunkId, double score) {
    }

    public static class VectorIndexException extends RuntimeException {

        public VectorIndexException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
