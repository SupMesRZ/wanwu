package com.huohuo.scenicaiguide.qa;

import com.google.common.util.concurrent.Futures;
import com.huohuo.scenicaiguide.qa.entity.QaKnowledgeChunk;
import com.huohuo.scenicaiguide.qa.service.QaVectorService;
import com.huohuo.scenicaiguide.system.service.ConfigService;
import io.qdrant.client.PointIdFactory;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.grpc.Collections;
import io.qdrant.client.grpc.Points;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class QaVectorServiceTest {

    @Test
    void disabledServiceNoopsEverywhere() {
        EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
        QdrantClient qdrantClient = mock(QdrantClient.class);
        ConfigService configService = mock(ConfigService.class);
        when(configService.getBoolean(eq(QaVectorService.ENABLED_KEY), anyBoolean())).thenReturn(false);
        QaVectorService service = service(embeddingModel, qdrantClient, configService);

        assertThat(service.enabled()).isFalse();
        assertThat(service.search("望月桥", 1L)).isEmpty();
        service.upsertChunks(3L, 1L, List.of(chunk(9L)));
        service.deleteByDoc(3L);

        org.mockito.Mockito.verifyNoInteractions(qdrantClient);
        org.mockito.Mockito.verifyNoInteractions(embeddingModel);
    }

    @Test
    void missingBeansDisableTheService() {
        ConfigService configService = mock(ConfigService.class);
        when(configService.getBoolean(eq(QaVectorService.ENABLED_KEY), anyBoolean())).thenReturn(true);
        @SuppressWarnings("unchecked")
        ObjectProvider<EmbeddingModel> emptyEmbedding = mock(ObjectProvider.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<QdrantClient> emptyQdrant = mock(ObjectProvider.class);
        QaVectorService service = new QaVectorService(emptyEmbedding, emptyQdrant, configService);

        assertThat(service.enabled()).isFalse();
        assertThat(service.search("望月桥", 1L)).isEmpty();
    }

    @Test
    void searchKeepsOnlyHitsAboveThreshold() {
        EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
        QdrantClient qdrantClient = mock(QdrantClient.class);
        ConfigService configService = enabledConfig();
        when(embeddingModel.embed("凉快的地方")).thenReturn(new float[]{0.1f, 0.2f});
        Points.ScoredPoint strong = Points.ScoredPoint.newBuilder()
            .setId(PointIdFactory.id(9L)).setScore(0.9f).build();
        Points.ScoredPoint weak = Points.ScoredPoint.newBuilder()
            .setId(PointIdFactory.id(8L)).setScore(0.5f).build();
        when(qdrantClient.searchAsync(any(Points.SearchPoints.class)))
            .thenReturn(Futures.immediateFuture(List.of(strong, weak)));
        QaVectorService service = service(embeddingModel, qdrantClient, configService);

        List<QaVectorService.VectorHit> hits = service.search("凉快的地方", 1L);

        assertThat(hits).containsExactly(new QaVectorService.VectorHit(9L, 0.9f));
    }

    @Test
    void searchDegradesToEmptyOnQdrantFailure() {
        EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
        QdrantClient qdrantClient = mock(QdrantClient.class);
        ConfigService configService = enabledConfig();
        when(embeddingModel.embed(anyString())).thenReturn(new float[]{0.1f});
        when(qdrantClient.searchAsync(any(Points.SearchPoints.class)))
            .thenReturn(Futures.immediateFailedFuture(new RuntimeException("Qdrant 连接拒绝")));
        QaVectorService service = service(embeddingModel, qdrantClient, configService);

        assertThat(service.search("望月桥", 1L)).isEmpty();
    }

    @Test
    void upsertCreatesMissingCollectionThenWritesPoints() {
        EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
        QdrantClient qdrantClient = mock(QdrantClient.class);
        ConfigService configService = enabledConfig();
        when(embeddingModel.embed(any(List.class)))
            .thenReturn(List.of(new float[]{0.1f}, new float[]{0.2f}));
        when(qdrantClient.collectionExistsAsync("qa_chunks"))
            .thenReturn(Futures.immediateFuture(false));
        when(qdrantClient.createCollectionAsync(eq("qa_chunks"), any(Collections.VectorParams.class)))
            .thenReturn(Futures.immediateFuture(Collections.CollectionOperationResponse.getDefaultInstance()));
        when(qdrantClient.upsertAsync(eq("qa_chunks"), any(List.class)))
            .thenReturn(Futures.immediateFuture(Points.UpdateResult.getDefaultInstance()));
        QaVectorService service = service(embeddingModel, qdrantClient, configService);

        service.upsertChunks(3L, 1L, List.of(chunk(9L), chunk(10L)));

        verify(qdrantClient).createCollectionAsync(eq("qa_chunks"), any(Collections.VectorParams.class));
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Points.PointStruct>> captor = ArgumentCaptor.forClass(List.class);
        verify(qdrantClient).upsertAsync(eq("qa_chunks"), captor.capture());
        assertThat(captor.getValue()).hasSize(2);
        assertThat(captor.getValue().get(0).getId().getNum()).isEqualTo(9L);
        assertThat(captor.getValue().get(0).getPayloadOrThrow("scenic_area_id").getIntegerValue()).isEqualTo(1L);
    }

    @Test
    void upsertFailureSurfacesAsVectorIndexException() {
        EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
        QdrantClient qdrantClient = mock(QdrantClient.class);
        ConfigService configService = enabledConfig();
        when(embeddingModel.embed(any(List.class))).thenThrow(new RuntimeException("embedding 接口超时"));
        when(qdrantClient.collectionExistsAsync("qa_chunks"))
            .thenReturn(Futures.immediateFuture(true));
        QaVectorService service = service(embeddingModel, qdrantClient, configService);

        assertThatThrownBy(() -> service.upsertChunks(3L, 1L, List.of(chunk(9L))))
            .isInstanceOf(QaVectorService.VectorIndexException.class);
    }

    private static QaKnowledgeChunk chunk(Long id) {
        QaKnowledgeChunk chunk = new QaKnowledgeChunk();
        chunk.setId(id);
        chunk.setContent("切片内容 " + id);
        return chunk;
    }

    private static ConfigService enabledConfig() {
        ConfigService configService = mock(ConfigService.class);
        when(configService.getBoolean(eq(QaVectorService.ENABLED_KEY), anyBoolean())).thenReturn(true);
        when(configService.getString(eq(QaVectorService.COLLECTION_KEY), anyString())).thenReturn("qa_chunks");
        when(configService.getInt(eq(QaVectorService.DIMENSION_KEY), anyInt())).thenReturn(1024);
        when(configService.getInt(eq(QaVectorService.TOP_K_KEY), anyInt())).thenReturn(5);
        when(configService.getDouble(eq(QaVectorService.THRESHOLD_KEY), anyDouble())).thenReturn(0.7);
        return configService;
    }

    private static QaVectorService service(EmbeddingModel embeddingModel, QdrantClient qdrantClient,
                                           ConfigService configService) {
        @SuppressWarnings("unchecked")
        ObjectProvider<EmbeddingModel> embeddingProvider = mock(ObjectProvider.class);
        when(embeddingProvider.getIfAvailable()).thenReturn(embeddingModel);
        @SuppressWarnings("unchecked")
        ObjectProvider<QdrantClient> qdrantProvider = mock(ObjectProvider.class);
        when(qdrantProvider.getIfAvailable()).thenReturn(qdrantClient);
        return new QaVectorService(embeddingProvider, qdrantProvider, configService);
    }
}
