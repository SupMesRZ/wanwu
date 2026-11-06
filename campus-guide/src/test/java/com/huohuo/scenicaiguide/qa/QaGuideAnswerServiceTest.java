package com.huohuo.scenicaiguide.qa;

import com.huohuo.scenicaiguide.qa.mapper.QaFaqMapper;
import com.huohuo.scenicaiguide.qa.mapper.QaGuideScriptMapper;
import com.huohuo.scenicaiguide.qa.mapper.QaKnowledgeChunkMapper;
import com.huohuo.scenicaiguide.qa.entity.QaFaq;
import com.huohuo.scenicaiguide.qa.entity.QaKnowledgeChunk;
import com.huohuo.scenicaiguide.qa.service.QaGuideAnswerService;
import com.huohuo.scenicaiguide.qa.service.QaVectorService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class QaGuideAnswerServiceTest {

    @Test
    void answerFallsBackWhenNoEvidence() {
        QaFaqMapper faqMapper = mock(QaFaqMapper.class);
        QaKnowledgeChunkMapper chunkMapper = mock(QaKnowledgeChunkMapper.class);
        QaGuideScriptMapper scriptMapper = mock(QaGuideScriptMapper.class);
        when(faqMapper.selectList(any())).thenReturn(List.of());
        when(chunkMapper.selectList(any())).thenReturn(List.of());
        when(scriptMapper.selectList(any())).thenReturn(List.of());

        QaGuideAnswerService service = new QaGuideAnswerService(faqMapper, chunkMapper, scriptMapper, mock(QaVectorService.class));

        QaGuideAnswerService.AnswerResult result = service.answer(1L, "门票多少钱", null);

        assertThat(result.sourceType()).isEqualTo("SAFE_FALLBACK");
        assertThat(result.answer()).contains("不能随意编造");
    }

    @Test
    void faqHitIncreasesWhenFaqMatched() {
        QaFaqMapper faqMapper = mock(QaFaqMapper.class);
        QaKnowledgeChunkMapper chunkMapper = mock(QaKnowledgeChunkMapper.class);
        QaGuideScriptMapper scriptMapper = mock(QaGuideScriptMapper.class);
        QaFaq faq = new QaFaq();
        faq.setId(5L);
        faq.setQuestion("开放时间");
        faq.setAnswer("景区每日 9:00 开放");
        faq.setHitCount(2);
        when(faqMapper.selectList(any())).thenReturn(List.of(faq));

        QaGuideAnswerService service = new QaGuideAnswerService(faqMapper, chunkMapper, scriptMapper, mock(QaVectorService.class));

        QaGuideAnswerService.AnswerResult result = service.answer(1L, "开放时间", null);

        assertThat(result.sourceType()).isEqualTo("FAQ");
        verify(faqMapper).updateById(any(QaFaq.class));
    }

    @Test
    void previewDoesNotIncreaseHitCountWhenMatched() {
        QaFaqMapper faqMapper = mock(QaFaqMapper.class);
        QaKnowledgeChunkMapper chunkMapper = mock(QaKnowledgeChunkMapper.class);
        QaGuideScriptMapper scriptMapper = mock(QaGuideScriptMapper.class);
        QaFaq faq = new QaFaq();
        faq.setId(6L);
        faq.setQuestion("停车场在哪里");
        faq.setAnswer("停车场在游客中心东侧");
        faq.setHitCount(3);
        when(faqMapper.selectList(any())).thenReturn(List.of(faq));

        QaGuideAnswerService service = new QaGuideAnswerService(faqMapper, chunkMapper, scriptMapper, mock(QaVectorService.class));

        QaGuideAnswerService.AnswerResult result = service.preview(1L, "停车场在哪里", null);

        assertThat(result.sourceType()).isEqualTo("FAQ");
        assertThat(result.evidence()).isEqualTo("FAQ#6");
        verify(faqMapper, never()).updateById(any(QaFaq.class));
    }

    @Test
    void chunkHitIncreasesWhenRagMatched() {
        QaFaqMapper faqMapper = mock(QaFaqMapper.class);
        QaKnowledgeChunkMapper chunkMapper = mock(QaKnowledgeChunkMapper.class);
        QaGuideScriptMapper scriptMapper = mock(QaGuideScriptMapper.class);
        when(faqMapper.selectList(any())).thenReturn(List.of());
        QaKnowledgeChunk chunk = new QaKnowledgeChunk();
        chunk.setId(9L);
        chunk.setContent("望月桥始建于明代，是古镇中心水系地标");
        chunk.setHitCount(4);
        when(chunkMapper.selectList(any())).thenReturn(List.of(chunk));

        QaGuideAnswerService service = new QaGuideAnswerService(faqMapper, chunkMapper, scriptMapper, mock(QaVectorService.class));

        QaGuideAnswerService.AnswerResult result = service.answer(1L, "望月桥", null);

        assertThat(result.sourceType()).isEqualTo("RAG");
        verify(chunkMapper).updateById(any(QaKnowledgeChunk.class));
    }

    @Test
    void ragMatchesChineseQuestionWithNgramTokens() {
        QaFaqMapper faqMapper = mock(QaFaqMapper.class);
        QaKnowledgeChunkMapper chunkMapper = mock(QaKnowledgeChunkMapper.class);
        QaGuideScriptMapper scriptMapper = mock(QaGuideScriptMapper.class);
        when(faqMapper.selectList(any())).thenReturn(List.of());
        QaKnowledgeChunk relevant = new QaKnowledgeChunk();
        relevant.setId(11L);
        relevant.setContent("灵源湖沿岸适合傍晚散步，湖心亭是观看落日和拍照的主要位置");
        relevant.setHitCount(0);
        QaKnowledgeChunk noise = new QaKnowledgeChunk();
        noise.setId(12L);
        noise.setContent("游客中心提供咨询、寄存和饮水服务");
        noise.setHitCount(0);
        when(chunkMapper.selectList(any())).thenReturn(List.of(noise, relevant));

        QaGuideAnswerService service = new QaGuideAnswerService(faqMapper, chunkMapper, scriptMapper, mock(QaVectorService.class));

        QaGuideAnswerService.AnswerResult result = service.answer(1L, "灵源湖哪里适合拍照看落日", null);

        assertThat(result.sourceType()).isEqualTo("RAG");
        assertThat(result.answer()).contains("湖心亭");
        assertThat(result.evidence()).contains("11");
    }

    @Test
    void ragDoesNotMatchOnlyGenericWords() {
        QaFaqMapper faqMapper = mock(QaFaqMapper.class);
        QaKnowledgeChunkMapper chunkMapper = mock(QaKnowledgeChunkMapper.class);
        QaGuideScriptMapper scriptMapper = mock(QaGuideScriptMapper.class);
        when(faqMapper.selectList(any())).thenReturn(List.of());
        when(scriptMapper.selectList(any())).thenReturn(List.of());
        QaKnowledgeChunk chunk = new QaKnowledgeChunk();
        chunk.setId(13L);
        chunk.setContent("游客中心提供咨询、寄存和饮水服务");
        chunk.setHitCount(0);
        when(chunkMapper.selectList(any())).thenReturn(List.of(chunk));

        QaGuideAnswerService service = new QaGuideAnswerService(faqMapper, chunkMapper, scriptMapper, mock(QaVectorService.class));

        QaGuideAnswerService.AnswerResult result = service.answer(1L, "景区有什么可以介绍一下", null);

        assertThat(result.sourceType()).isEqualTo("SAFE_FALLBACK");
        assertThat(result.evidence()).isEqualTo("NO_EVIDENCE");
    }

    @Test
    void guideScriptFallsBackToPublishedSpotDescription() {
        QaFaqMapper faqMapper = mock(QaFaqMapper.class);
        QaKnowledgeChunkMapper chunkMapper = mock(QaKnowledgeChunkMapper.class);
        QaGuideScriptMapper scriptMapper = mock(QaGuideScriptMapper.class);
        when(scriptMapper.selectList(any())).thenReturn(List.of());

        QaGuideAnswerService service = new QaGuideAnswerService(faqMapper, chunkMapper, scriptMapper, mock(QaVectorService.class));

        QaGuideAnswerService.AnswerResult result = service.guideScript(1L, "望月桥", "望月桥是古镇中心水系地标");

        assertThat(result.sourceType()).isEqualTo("SPOT_DESCRIPTION");
        assertThat(result.evidence()).isEqualTo("PUBLISHED_SPOT_DESCRIPTION:望月桥");
        assertThat(result.answer()).contains("望月桥");
    }

    @Test
    void vectorHitMergesAheadOfKeywordChunks() {
        QaFaqMapper faqMapper = mock(QaFaqMapper.class);
        QaKnowledgeChunkMapper chunkMapper = mock(QaKnowledgeChunkMapper.class);
        QaGuideScriptMapper scriptMapper = mock(QaGuideScriptMapper.class);
        QaVectorService vectorService = mock(QaVectorService.class);
        when(faqMapper.selectList(any())).thenReturn(List.of());
        QaKnowledgeChunk keywordChunk = new QaKnowledgeChunk();
        keywordChunk.setId(11L);
        keywordChunk.setContent("望月桥始建于明代");
        keywordChunk.setHitCount(0);
        keywordChunk.setStatus(1);
        when(chunkMapper.selectList(any())).thenReturn(List.of(keywordChunk));
        QaKnowledgeChunk vectorChunk = new QaKnowledgeChunk();
        vectorChunk.setId(9L);
        vectorChunk.setContent("夏季索道因检修暂停运营");
        vectorChunk.setHitCount(0);
        vectorChunk.setStatus(1);
        when(chunkMapper.selectBatchIds(any())).thenReturn(List.of(vectorChunk));
        when(vectorService.search(any(), eq(1L)))
            .thenReturn(List.of(new QaVectorService.VectorHit(9L, 0.9)));

        QaGuideAnswerService service = new QaGuideAnswerService(faqMapper, chunkMapper, scriptMapper, vectorService);

        QaGuideAnswerService.AnswerResult result = service.answer(1L, "望月桥", null);

        // 语义命中排最前，关键词命中补位，证据链两者都在
        assertThat(result.sourceType()).isEqualTo("RAG");
        assertThat(result.answer()).startsWith("夏季索道因检修暂停运营");
        assertThat(result.evidence()).isEqualTo("CHUNK:[9, 11]");
    }

    @Test
    void vectorHitDedupedWithKeywordHit() {
        QaFaqMapper faqMapper = mock(QaFaqMapper.class);
        QaKnowledgeChunkMapper chunkMapper = mock(QaKnowledgeChunkMapper.class);
        QaGuideScriptMapper scriptMapper = mock(QaGuideScriptMapper.class);
        QaVectorService vectorService = mock(QaVectorService.class);
        when(faqMapper.selectList(any())).thenReturn(List.of());
        QaKnowledgeChunk chunk = new QaKnowledgeChunk();
        chunk.setId(9L);
        chunk.setContent("望月桥始建于明代");
        chunk.setHitCount(0);
        chunk.setStatus(1);
        when(chunkMapper.selectList(any())).thenReturn(List.of(chunk));
        when(chunkMapper.selectBatchIds(any())).thenReturn(List.of(chunk));
        when(vectorService.search(any(), eq(1L)))
            .thenReturn(List.of(new QaVectorService.VectorHit(9L, 0.9)));

        QaGuideAnswerService service = new QaGuideAnswerService(faqMapper, chunkMapper, scriptMapper, vectorService);

        QaGuideAnswerService.AnswerResult result = service.answer(1L, "望月桥", null);

        // 同一切片被两路召回时只出现一次，不能重复进上下文
        assertThat(result.evidence()).isEqualTo("CHUNK:[9]");
    }

    @Test
    void disabledChunkStillSearchableByKeyword() {
        QaFaqMapper faqMapper = mock(QaFaqMapper.class);
        QaKnowledgeChunkMapper chunkMapper = mock(QaKnowledgeChunkMapper.class);
        QaGuideScriptMapper scriptMapper = mock(QaGuideScriptMapper.class);
        when(faqMapper.selectList(any())).thenReturn(List.of());
        QaKnowledgeChunk chunk = new QaKnowledgeChunk();
        chunk.setId(15L);
        chunk.setContent("望月桥始建于明代");
        chunk.setHitCount(0);
        // vector_status=SKIPPED（未向量化）的切片也必须能被关键词检索命中
        chunk.setVectorStatus("SKIPPED");
        when(chunkMapper.selectList(any())).thenReturn(List.of(chunk));

        QaGuideAnswerService service = new QaGuideAnswerService(faqMapper, chunkMapper, scriptMapper, mock(QaVectorService.class));

        QaGuideAnswerService.AnswerResult result = service.answer(1L, "望月桥", null);

        assertThat(result.sourceType()).isEqualTo("RAG");
        assertThat(result.evidence()).contains("15");
    }
}
