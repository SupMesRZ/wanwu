package com.huohuo.scenicaiguide.qa.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.huohuo.scenicaiguide.common.api.PageResponse;
import com.huohuo.scenicaiguide.common.enums.ErrorCode;
import com.huohuo.scenicaiguide.common.exception.BusinessException;
import com.huohuo.scenicaiguide.qa.dto.request.FaqRequest;
import com.huohuo.scenicaiguide.qa.dto.request.GuideScriptRequest;
import com.huohuo.scenicaiguide.qa.dto.request.KnowledgeDocRequest;
import com.huohuo.scenicaiguide.qa.dto.response.FaqResponse;
import com.huohuo.scenicaiguide.qa.dto.response.GuideScriptResponse;
import com.huohuo.scenicaiguide.qa.dto.response.KnowledgeChunkResponse;
import com.huohuo.scenicaiguide.qa.dto.response.KnowledgeDocResponse;
import com.huohuo.scenicaiguide.qa.entity.QaFaq;
import com.huohuo.scenicaiguide.qa.entity.QaGuideScript;
import com.huohuo.scenicaiguide.qa.entity.QaKnowledgeChunk;
import com.huohuo.scenicaiguide.qa.entity.QaKnowledgeDoc;
import com.huohuo.scenicaiguide.qa.mapper.QaFaqMapper;
import com.huohuo.scenicaiguide.qa.mapper.QaGuideScriptMapper;
import com.huohuo.scenicaiguide.qa.mapper.QaKnowledgeChunkMapper;
import com.huohuo.scenicaiguide.qa.mapper.QaKnowledgeDocMapper;
import com.huohuo.scenicaiguide.scenic.mapper.ScenicAreaMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.ArrayList;

@Service
public class QaAdminService {

    private static final Logger log = LoggerFactory.getLogger(QaAdminService.class);

    private final QaKnowledgeDocMapper knowledgeDocMapper;
    private final QaKnowledgeChunkMapper knowledgeChunkMapper;
    private final QaFaqMapper faqMapper;
    private final QaGuideScriptMapper guideScriptMapper;
    private final ScenicAreaMapper scenicAreaMapper;
    private final QaVectorService vectorService;

    @Autowired
    public QaAdminService(QaKnowledgeDocMapper knowledgeDocMapper,
                          QaKnowledgeChunkMapper knowledgeChunkMapper,
                          QaFaqMapper faqMapper,
                          QaGuideScriptMapper guideScriptMapper,
                          ScenicAreaMapper scenicAreaMapper,
                          QaVectorService vectorService) {
        this.knowledgeDocMapper = knowledgeDocMapper;
        this.knowledgeChunkMapper = knowledgeChunkMapper;
        this.faqMapper = faqMapper;
        this.guideScriptMapper = guideScriptMapper;
        this.scenicAreaMapper = scenicAreaMapper;
        this.vectorService = vectorService;
    }

    public PageResponse<KnowledgeDocResponse> listDocs(Long scenicAreaId, String keyword, String vectorStatus,
                                                       long page, long pageSize) {
        QueryWrapper<QaKnowledgeDoc> wrapper = new QueryWrapper<QaKnowledgeDoc>()
            .orderByDesc("updated_at");
        if (scenicAreaId != null) {
            wrapper.eq("scenic_area_id", scenicAreaId);
        }
        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(w -> w.like("title", keyword).or().like("summary", keyword));
        }
        if (vectorStatus != null && !vectorStatus.isBlank()) {
            wrapper.eq("vector_status", vectorStatus.trim().toUpperCase(Locale.ROOT));
        }
        Page<QaKnowledgeDoc> result = knowledgeDocMapper.selectPage(new Page<>(page, pageSize), wrapper);
        List<KnowledgeDocResponse> records = result.getRecords().stream()
            .map(this::toDocResponse)
            .toList();
        return PageResponse.of(records, result.getCurrent(), result.getSize(), result.getTotal());
    }

    @Transactional
    public KnowledgeDocResponse createDoc(KnowledgeDocRequest request) {
        ensureAreaExists(request.getScenicAreaId());
        QaKnowledgeDoc entity = new QaKnowledgeDoc();
        fillDoc(entity, request);
        entity.setParseStatus("PENDING");
        entity.setVectorStatus("PENDING");
        entity.setChunkCount(0);
        knowledgeDocMapper.insert(entity);
        return toDocResponse(entity);
    }

    @Transactional
    public KnowledgeDocResponse updateDoc(Long docId, KnowledgeDocRequest request) {
        QaKnowledgeDoc entity = getDoc(docId);
        ensureAreaExists(request.getScenicAreaId());
        fillDoc(entity, request);
        knowledgeDocMapper.updateById(entity);
        return toDocResponse(entity);
    }

    @Transactional
    public void deleteDoc(Long docId) {
        getDoc(docId);
        knowledgeDocMapper.deleteById(docId);
        knowledgeChunkMapper.delete(new QueryWrapper<QaKnowledgeChunk>().eq("doc_id", docId));
        vectorService.deleteByDoc(docId);
    }

    @Transactional
    public KnowledgeDocResponse reindexDoc(Long docId) {
        QaKnowledgeDoc entity = getDoc(docId);
        knowledgeChunkMapper.delete(new QueryWrapper<QaKnowledgeChunk>().eq("doc_id", docId));
        List<String> contents = buildKnowledgeChunks(entity);
        boolean vectorEnabled = vectorService.enabled();
        List<QaKnowledgeChunk> chunks = new ArrayList<>();
        int index = 1;
        for (String content : contents) {
            QaKnowledgeChunk chunk = new QaKnowledgeChunk();
            chunk.setDocId(entity.getId());
            chunk.setScenicAreaId(entity.getScenicAreaId());
            chunk.setChunkIndex(index);
            chunk.setContent(content);
            chunk.setVectorId("local-doc-" + entity.getId() + "-" + index);
            chunk.setTokenCount(Math.max(1, content.length() / 2));
            chunk.setHitCount(0);
            chunk.setVectorStatus("PENDING");
            chunk.setStatus(1);
            knowledgeChunkMapper.insert(chunk);
            chunks.add(chunk);
            index++;
        }
        // vector_status 语义是真实的向量索引状态：开关关闭记 SKIPPED，
        // 向量化成功 DONE、失败 FAILED。关键词检索不依赖它，不再当过滤条件用
        String vectorStatus = "SKIPPED";
        if (vectorEnabled && !chunks.isEmpty()) {
            try {
                vectorService.upsertChunks(entity.getId(), entity.getScenicAreaId(), chunks);
                vectorStatus = "DONE";
            } catch (QaVectorService.VectorIndexException e) {
                vectorStatus = "FAILED";
                log.warn("文档 {} 向量化失败，关键词检索不受影响: {}", docId, e.getMessage());
            }
        }
        for (QaKnowledgeChunk chunk : chunks) {
            QaKnowledgeChunk update = new QaKnowledgeChunk();
            update.setId(chunk.getId());
            update.setVectorStatus(vectorStatus);
            if ("DONE".equals(vectorStatus)) {
                update.setVectorId("qdrant:" + chunk.getId());
            }
            knowledgeChunkMapper.updateById(update);
            chunk.setVectorStatus(vectorStatus);
        }
        entity.setParseStatus("PARSED");
        entity.setVectorStatus(vectorStatus);
        entity.setChunkCount(contents.size());
        knowledgeDocMapper.updateById(entity);
        return toDocResponse(entity);
    }

    public PageResponse<KnowledgeChunkResponse> listChunks(Long docId, Long scenicAreaId, String keyword,
                                                           long page, long pageSize) {
        QueryWrapper<QaKnowledgeChunk> wrapper = new QueryWrapper<QaKnowledgeChunk>()
            .orderByAsc("doc_id")
            .orderByAsc("chunk_index");
        if (docId != null) {
            wrapper.eq("doc_id", docId);
        }
        if (scenicAreaId != null) {
            wrapper.eq("scenic_area_id", scenicAreaId);
        }
        if (keyword != null && !keyword.isBlank()) {
            wrapper.like("content", keyword);
        }
        Page<QaKnowledgeChunk> result = knowledgeChunkMapper.selectPage(new Page<>(page, pageSize), wrapper);
        List<KnowledgeChunkResponse> records = result.getRecords().stream()
            .map(KnowledgeChunkResponse::from)
            .toList();
        return PageResponse.of(records, result.getCurrent(), result.getSize(), result.getTotal());
    }

    public PageResponse<FaqResponse> listFaqs(Long scenicAreaId, String keyword, Integer status,
                                              long page, long pageSize) {
        QueryWrapper<QaFaq> wrapper = new QueryWrapper<QaFaq>().orderByDesc("updated_at");
        if (scenicAreaId != null) {
            wrapper.eq("scenic_area_id", scenicAreaId);
        }
        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(w -> w.like("question", keyword).or().like("answer", keyword));
        }
        if (status != null) {
            wrapper.eq("status", status);
        }
        Page<QaFaq> result = faqMapper.selectPage(new Page<>(page, pageSize), wrapper);
        List<FaqResponse> records = result.getRecords().stream().map(FaqResponse::from).toList();
        return PageResponse.of(records, result.getCurrent(), result.getSize(), result.getTotal());
    }

    public FaqResponse createFaq(FaqRequest request) {
        ensureAreaExists(request.getScenicAreaId());
        QaFaq entity = new QaFaq();
        fillFaq(entity, request);
        entity.setHitCount(0);
        faqMapper.insert(entity);
        return FaqResponse.from(entity);
    }

    public FaqResponse updateFaq(Long faqId, FaqRequest request) {
        QaFaq entity = getFaq(faqId);
        ensureAreaExists(request.getScenicAreaId());
        fillFaq(entity, request);
        faqMapper.updateById(entity);
        return FaqResponse.from(entity);
    }

    public void deleteFaq(Long faqId) {
        getFaq(faqId);
        faqMapper.deleteById(faqId);
    }

    public PageResponse<GuideScriptResponse> listGuideScripts(Long scenicAreaId,
                                                              String keyword, Integer status, long page, long pageSize) {
        QueryWrapper<QaGuideScript> wrapper = new QueryWrapper<QaGuideScript>()
            .orderByDesc("priority")
            .orderByDesc("updated_at");
        if (scenicAreaId != null) {
            wrapper.eq("scenic_area_id", scenicAreaId);
        }
        if (keyword != null && !keyword.isBlank()) {
            wrapper.and(w -> w.like("title", keyword).or().like("content", keyword));
        }
        if (status != null) {
            wrapper.eq("status", status);
        }
        Page<QaGuideScript> result = guideScriptMapper.selectPage(new Page<>(page, pageSize), wrapper);
        List<GuideScriptResponse> records = result.getRecords().stream().map(GuideScriptResponse::from).toList();
        return PageResponse.of(records, result.getCurrent(), result.getSize(), result.getTotal());
    }

    public GuideScriptResponse createGuideScript(GuideScriptRequest request) {
        ensureAreaExists(request.getScenicAreaId());
        QaGuideScript entity = new QaGuideScript();
        fillGuideScript(entity, request);
        guideScriptMapper.insert(entity);
        return GuideScriptResponse.from(entity);
    }

    public GuideScriptResponse updateGuideScript(Long scriptId, GuideScriptRequest request) {
        QaGuideScript entity = getGuideScript(scriptId);
        ensureAreaExists(request.getScenicAreaId());
        fillGuideScript(entity, request);
        guideScriptMapper.updateById(entity);
        return GuideScriptResponse.from(entity);
    }

    public void deleteGuideScript(Long scriptId) {
        getGuideScript(scriptId);
        guideScriptMapper.deleteById(scriptId);
    }

    private void fillDoc(QaKnowledgeDoc entity, KnowledgeDocRequest request) {
        entity.setScenicAreaId(request.getScenicAreaId());
        entity.setTitle(request.getTitle().trim());
        entity.setFileName(defaultText(request.getFileName(), request.getTitle().trim()));
        entity.setFileUrl(request.getFileUrl());
        entity.setDocType(defaultText(request.getDocType(), "TEXT").toUpperCase(Locale.ROOT));
        entity.setSummary(request.getSummary());
        entity.setStatus(request.getStatus() == null ? 1 : request.getStatus());
    }

    private void fillFaq(QaFaq entity, FaqRequest request) {
        entity.setScenicAreaId(request.getScenicAreaId());
        entity.setQuestion(request.getQuestion().trim());
        entity.setAnswer(request.getAnswer().trim());
        entity.setCategory(request.getCategory());
        entity.setTags(request.getTags());
        entity.setStatus(request.getStatus() == null ? 1 : request.getStatus());
    }

    private void fillGuideScript(QaGuideScript entity, GuideScriptRequest request) {
        entity.setScenicAreaId(request.getScenicAreaId());
        entity.setTitle(request.getTitle().trim());
        entity.setContent(request.getContent().trim());
        entity.setLanguage(defaultText(request.getLanguage(), "zh-CN"));
        entity.setPriority(request.getPriority() == null ? 0 : request.getPriority());
        entity.setAudioAssetId(request.getAudioAssetId());
        entity.setVideoAssetId(request.getVideoAssetId());
        entity.setStatus(request.getStatus() == null ? 1 : request.getStatus());
    }

    private KnowledgeDocResponse toDocResponse(QaKnowledgeDoc entity) {
        return KnowledgeDocResponse.from(entity);
    }

    private List<String> buildKnowledgeChunks(QaKnowledgeDoc entity) {
        String content = defaultText(entity.getSummary(), entity.getTitle());
        String normalized = content.replace("\r\n", "\n").trim();
        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String part : normalized.split("(?<=[。！？；;\\n])")) {
            String sentence = part.trim();
            if (sentence.isBlank()) {
                continue;
            }
            if (current.length() > 0 && current.length() + sentence.length() > 360) {
                chunks.add(prefixTitle(entity, current.toString()));
                current.setLength(0);
            }
            current.append(sentence);
        }
        if (current.length() > 0) {
            chunks.add(prefixTitle(entity, current.toString()));
        }
        if (chunks.isEmpty()) {
            chunks.add(prefixTitle(entity, entity.getTitle()));
        }
        return chunks;
    }

    private String prefixTitle(QaKnowledgeDoc entity, String content) {
        String title = entity.getTitle() == null ? "" : entity.getTitle().trim();
        String body = content == null ? "" : content.trim();
        if (body.startsWith("【" + title + "】")) {
            return body;
        }
        return "【" + title + "】" + body;
    }

    private QaKnowledgeDoc getDoc(Long docId) {
        QaKnowledgeDoc entity = knowledgeDocMapper.selectById(docId);
        if (entity == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "知识文档不存在");
        }
        return entity;
    }

    private QaFaq getFaq(Long faqId) {
        QaFaq entity = faqMapper.selectById(faqId);
        if (entity == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "FAQ 不存在");
        }
        return entity;
    }

    private QaGuideScript getGuideScript(Long scriptId) {
        QaGuideScript entity = guideScriptMapper.selectById(scriptId);
        if (entity == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "讲解词不存在");
        }
        return entity;
    }

    private void ensureAreaExists(Long scenicAreaId) {
        if (scenicAreaId == null || scenicAreaMapper.selectById(scenicAreaId) == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "校区不存在");
        }
    }

    private String defaultText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
