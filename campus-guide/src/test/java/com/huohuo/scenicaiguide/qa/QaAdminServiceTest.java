package com.huohuo.scenicaiguide.qa;

import com.huohuo.scenicaiguide.qa.dto.response.KnowledgeDocResponse;
import com.huohuo.scenicaiguide.qa.entity.QaKnowledgeChunk;
import com.huohuo.scenicaiguide.qa.entity.QaKnowledgeDoc;
import com.huohuo.scenicaiguide.qa.mapper.QaFaqMapper;
import com.huohuo.scenicaiguide.qa.mapper.QaGuideScriptMapper;
import com.huohuo.scenicaiguide.qa.mapper.QaKnowledgeChunkMapper;
import com.huohuo.scenicaiguide.qa.mapper.QaKnowledgeDocMapper;
import com.huohuo.scenicaiguide.qa.service.QaAdminService;
import com.huohuo.scenicaiguide.qa.service.QaVectorService;
import com.huohuo.scenicaiguide.scenic.entity.ScenicArea;
import com.huohuo.scenicaiguide.scenic.mapper.ScenicAreaMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class QaAdminServiceTest {

    @Test
    void reindexBuildsChunksFromRealDocSummaryWithoutMockContent() {
        QaKnowledgeDocMapper docMapper = mock(QaKnowledgeDocMapper.class);
        QaKnowledgeChunkMapper chunkMapper = mock(QaKnowledgeChunkMapper.class);
        QaFaqMapper faqMapper = mock(QaFaqMapper.class);
        QaGuideScriptMapper scriptMapper = mock(QaGuideScriptMapper.class);
        ScenicAreaMapper areaMapper = mock(ScenicAreaMapper.class);
        QaKnowledgeDoc doc = new QaKnowledgeDoc();
        doc.setId(3L);
        doc.setScenicAreaId(1L);
        doc.setTitle("望月桥资料");
        doc.setDocType("TEXT");
        doc.setSummary("望月桥位于古镇中心。桥下水系连接东西街区。");
        when(docMapper.selectById(3L)).thenReturn(doc);
        when(areaMapper.selectById(1L)).thenReturn(new ScenicArea());
        List<QaKnowledgeChunk> inserted = new ArrayList<>();
        when(chunkMapper.insert(any(QaKnowledgeChunk.class))).thenAnswer(invocation -> {
            inserted.add(invocation.getArgument(0));
            return 1;
        });

        QaAdminService service = new QaAdminService(docMapper, chunkMapper, faqMapper, scriptMapper, areaMapper, mock(QaVectorService.class));

        service.reindexDoc(3L);

        assertThat(inserted).isNotEmpty();
        assertThat(inserted).allSatisfy(chunk -> {
            assertThat(chunk.getVectorId()).startsWith("local-doc-3-");
            assertThat(chunk.getContent()).contains("望月桥");
            assertThat(chunk.getContent()).doesNotContain("模拟切片");
            assertThat(chunk.getContent()).doesNotContain("后续接入");
        });
        ArgumentCaptor<QaKnowledgeDoc> docCaptor = ArgumentCaptor.forClass(QaKnowledgeDoc.class);
        org.mockito.Mockito.verify(docMapper).updateById(docCaptor.capture());
        assertThat(docCaptor.getValue().getVectorStatus()).isEqualTo("SKIPPED");
        assertThat(docCaptor.getValue().getChunkCount()).isEqualTo(inserted.size());
    }

    @Test
    void reindexWithVectorSuccessMarksDone() {
        QaKnowledgeDocMapper docMapper = mock(QaKnowledgeDocMapper.class);
        QaKnowledgeChunkMapper chunkMapper = mock(QaKnowledgeChunkMapper.class);
        QaFaqMapper faqMapper = mock(QaFaqMapper.class);
        QaGuideScriptMapper scriptMapper = mock(QaGuideScriptMapper.class);
        ScenicAreaMapper areaMapper = mock(ScenicAreaMapper.class);
        QaVectorService vectorService = mock(QaVectorService.class);
        QaKnowledgeDoc doc = new QaKnowledgeDoc();
        doc.setId(3L);
        doc.setScenicAreaId(1L);
        doc.setTitle("望月桥资料");
        doc.setDocType("TEXT");
        doc.setSummary("望月桥位于古镇中心。桥下水系连接东西街区。");
        when(docMapper.selectById(3L)).thenReturn(doc);
        when(areaMapper.selectById(1L)).thenReturn(new ScenicArea());
        when(vectorService.enabled()).thenReturn(true);
        long[] nextId = {100L};
        when(chunkMapper.insert(any(QaKnowledgeChunk.class))).thenAnswer(invocation -> {
            QaKnowledgeChunk chunk = invocation.getArgument(0);
            chunk.setId(nextId[0]++);
            return 1;
        });

        QaAdminService service = new QaAdminService(docMapper, chunkMapper, faqMapper, scriptMapper, areaMapper, vectorService);

        KnowledgeDocResponse response = service.reindexDoc(3L);

        assertThat(response.getVectorStatus()).isEqualTo("DONE");
        org.mockito.Mockito.verify(vectorService)
            .upsertChunks(org.mockito.ArgumentMatchers.eq(3L), org.mockito.ArgumentMatchers.eq(1L), any());
        // 切片回写真实向量索引状态与 Qdrant 点引用
        ArgumentCaptor<QaKnowledgeChunk> chunkCaptor = ArgumentCaptor.forClass(QaKnowledgeChunk.class);
        org.mockito.Mockito.verify(chunkMapper, org.mockito.Mockito.atLeastOnce()).updateById(chunkCaptor.capture());
        assertThat(chunkCaptor.getAllValues()).allSatisfy(update -> {
            assertThat(update.getVectorStatus()).isEqualTo("DONE");
            assertThat(update.getVectorId()).startsWith("qdrant:");
        });
    }

    @Test
    void reindexWithVectorFailureMarksFailedButKeepsChunks() {
        QaKnowledgeDocMapper docMapper = mock(QaKnowledgeDocMapper.class);
        QaKnowledgeChunkMapper chunkMapper = mock(QaKnowledgeChunkMapper.class);
        QaFaqMapper faqMapper = mock(QaFaqMapper.class);
        QaGuideScriptMapper scriptMapper = mock(QaGuideScriptMapper.class);
        ScenicAreaMapper areaMapper = mock(ScenicAreaMapper.class);
        QaVectorService vectorService = mock(QaVectorService.class);
        QaKnowledgeDoc doc = new QaKnowledgeDoc();
        doc.setId(3L);
        doc.setScenicAreaId(1L);
        doc.setTitle("望月桥资料");
        doc.setDocType("TEXT");
        doc.setSummary("望月桥位于古镇中心。桥下水系连接东西街区。");
        when(docMapper.selectById(3L)).thenReturn(doc);
        when(areaMapper.selectById(1L)).thenReturn(new ScenicArea());
        when(vectorService.enabled()).thenReturn(true);
        org.mockito.Mockito.doThrow(new QaVectorService.VectorIndexException("Qdrant 不可达", null))
            .when(vectorService).upsertChunks(any(), any(), any());
        when(chunkMapper.insert(any(QaKnowledgeChunk.class))).thenReturn(1);

        QaAdminService service = new QaAdminService(docMapper, chunkMapper, faqMapper, scriptMapper, areaMapper, vectorService);

        // 向量化失败不阻断重建：切片落库可关键词检索，状态如实标 FAILED
        KnowledgeDocResponse response = service.reindexDoc(3L);

        assertThat(response.getVectorStatus()).isEqualTo("FAILED");
        assertThat(response.getChunkCount()).isGreaterThan(0);
    }
}
