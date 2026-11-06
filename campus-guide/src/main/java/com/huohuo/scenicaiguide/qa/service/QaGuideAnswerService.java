package com.huohuo.scenicaiguide.qa.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.huohuo.scenicaiguide.qa.entity.QaFaq;
import com.huohuo.scenicaiguide.qa.entity.QaGuideScript;
import com.huohuo.scenicaiguide.qa.entity.QaKnowledgeChunk;
import com.huohuo.scenicaiguide.qa.mapper.QaFaqMapper;
import com.huohuo.scenicaiguide.qa.mapper.QaGuideScriptMapper;
import com.huohuo.scenicaiguide.qa.mapper.QaKnowledgeChunkMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class QaGuideAnswerService {

    private static final int RAG_TOP_K = 3;
    private static final int MIN_RAG_SCORE = 3;
    private static final int MAX_CONTEXT_CHARS = 900;
    private static final Pattern SPLIT_PATTERN = Pattern.compile("[，。！？,.!?；;：:\\s、（）()【】《》\"'“”‘’]+");
    private static final Set<String> STOP_WORDS = Set.of(
        "什么", "怎么", "哪里", "哪个", "一下", "可以", "有没有", "介绍", "讲讲", "附近", "校园", "地点", "景点", "导航", "路线");

    private final QaFaqMapper faqMapper;
    private final QaKnowledgeChunkMapper knowledgeChunkMapper;
    private final QaGuideScriptMapper guideScriptMapper;
    private final QaVectorService vectorService;

    public QaGuideAnswerService(QaFaqMapper faqMapper,
                                QaKnowledgeChunkMapper knowledgeChunkMapper,
                                QaGuideScriptMapper guideScriptMapper,
                                QaVectorService vectorService) {
        this.faqMapper = faqMapper;
        this.knowledgeChunkMapper = knowledgeChunkMapper;
        this.guideScriptMapper = guideScriptMapper;
        this.vectorService = vectorService;
    }

    public AnswerResult answer(Long scenicAreaId, String question, String currentSpotName) {
        return answer(scenicAreaId, question, currentSpotName, true);
    }

    public AnswerResult preview(Long scenicAreaId, String question, String currentSpotName) {
        return answer(scenicAreaId, question, currentSpotName, false);
    }

    private AnswerResult answer(Long scenicAreaId, String question, String currentSpotName, boolean increaseHitCount) {
        String normalized = normalize(question);
        QaFaq faq = findFaq(scenicAreaId, normalized);
        if (faq != null) {
            if (increaseHitCount) {
                increaseFaqHitCount(faq);
            }
            return new AnswerResult(faq.getAnswer(), "FAQ", "FAQ#" + faq.getId());
        }

        List<ScoredChunk> chunks = findChunks(scenicAreaId, normalized);
        List<QaKnowledgeChunk> merged = mergeVectorChunks(scenicAreaId, question, chunks);
        if (!merged.isEmpty()) {
            if (increaseHitCount) {
                increaseChunkHitCount(merged);
            }
            String content = merged.stream()
                .limit(RAG_TOP_K)
                .map(chunk -> limitText(chunk.getContent(), MAX_CONTEXT_CHARS / RAG_TOP_K))
                .reduce((left, right) -> left + "\n" + right)
                .orElse("");
            return new AnswerResult(content, "RAG", "CHUNK:" + merged.stream().map(QaKnowledgeChunk::getId).toList());
        }

        if (currentSpotName != null && !currentSpotName.isBlank()) {
            QaGuideScript script = findGuideScript(scenicAreaId, currentSpotName);
            if (script != null) {
                return new AnswerResult(script.getContent(), "GUIDE_SCRIPT", "GUIDE_SCRIPT#" + script.getId());
            }
        }

        return new AnswerResult("当前校园资料里还没有这部分确认信息，我不能随意编造。你可以询问教学楼、图书馆、食堂、宿舍、校门或已录入路线。", "SAFE_FALLBACK", "NO_EVIDENCE");
    }

    public AnswerResult guideScript(Long scenicAreaId, String spotName) {
        return guideScript(scenicAreaId, spotName, null);
    }

    public AnswerResult guideScript(Long scenicAreaId, String spotName, String spotDescription) {
        QaGuideScript script = findGuideScript(scenicAreaId, spotName);
        if (script != null) {
            return new AnswerResult(script.getContent(), "GUIDE_SCRIPT", "GUIDE_SCRIPT#" + script.getId());
        }
        if (spotDescription != null && !spotDescription.isBlank()) {
            return new AnswerResult(spotDescription.trim(), "SPOT_DESCRIPTION", "PUBLISHED_SPOT_DESCRIPTION:" + spotName);
        }
        return new AnswerResult("当前地点还没有配置介绍，我先为你标记到达。", "SAFE_FALLBACK", "NO_GUIDE_SCRIPT");
    }

    private QaFaq findFaq(Long scenicAreaId, String normalizedQuestion) {
        return faqMapper.selectList(new QueryWrapper<QaFaq>()
                .eq("scenic_area_id", scenicAreaId)
                .eq("status", 1)
                .orderByDesc("hit_count"))
            .stream()
            .filter(faq -> match(normalizedQuestion, faq.getQuestion())
                || match(normalizedQuestion, faq.getTags())
                || match(faq.getQuestion(), normalizedQuestion))
            .findFirst()
            .orElse(null);
    }

    private List<ScoredChunk> findChunks(Long scenicAreaId, String normalizedQuestion) {
        Set<String> tokens = searchTokens(normalizedQuestion);
        if (tokens.isEmpty()) {
            return List.of();
        }
        // 关键词检索不依赖向量索引状态，vector_status 是向量索引的真实状态而非可检索标记
        return knowledgeChunkMapper.selectList(new QueryWrapper<QaKnowledgeChunk>()
                .eq("scenic_area_id", scenicAreaId)
                .eq("status", 1)
                .orderByDesc("hit_count")
                .last("LIMIT 50"))
            .stream()
            .map(chunk -> new ScoredChunk(chunk, score(chunk.getContent(), tokens)))
            .filter(item -> item.score() >= MIN_RAG_SCORE)
            .sorted(Comparator.comparingInt(ScoredChunk::score).reversed()
                .thenComparing(item -> item.chunk().getHitCount() == null ? 0 : item.chunk().getHitCount(), Comparator.reverseOrder()))
            .limit(RAG_TOP_K)
            .toList();
    }

    /**
     * 向量召回与关键词召回合并：向量命中在前（语义相关性），关键词命中按分补位去重。
     * 开关关闭或向量检索故障时 search 返回空，自然退化为纯关键词
     */
    private List<QaKnowledgeChunk> mergeVectorChunks(Long scenicAreaId, String question, List<ScoredChunk> keywordChunks) {
        List<QaKnowledgeChunk> merged = new ArrayList<>();
        List<QaVectorService.VectorHit> hits = vectorService.search(question, scenicAreaId);
        if (hits != null && !hits.isEmpty()) {
            Map<Long, QaKnowledgeChunk> byId = new LinkedHashMap<>();
            knowledgeChunkMapper.selectBatchIds(hits.stream().map(QaVectorService.VectorHit::chunkId).toList())
                .stream()
                .filter(chunk -> chunk.getStatus() != null && chunk.getStatus() == 1)
                .forEach(chunk -> byId.put(chunk.getId(), chunk));
            for (QaVectorService.VectorHit hit : hits) {
                QaKnowledgeChunk chunk = byId.get(hit.chunkId());
                if (chunk != null) {
                    merged.add(chunk);
                }
            }
        }
        for (ScoredChunk item : keywordChunks) {
            if (merged.stream().noneMatch(chunk -> chunk.getId().equals(item.chunk().getId()))) {
                merged.add(item.chunk());
            }
        }
        return merged;
    }

    private QaGuideScript findGuideScript(Long scenicAreaId, String spotName) {
        return guideScriptMapper.selectList(new QueryWrapper<QaGuideScript>()
                .eq("scenic_area_id", scenicAreaId)
                .eq("status", 1)
                .orderByDesc("priority"))
            .stream()
            .filter(script -> match(script.getTitle(), spotName) || match(script.getContent(), spotName))
            .findFirst()
            .orElse(null);
    }

    private void increaseFaqHitCount(QaFaq faq) {
        QaFaq update = new QaFaq();
        update.setId(faq.getId());
        update.setHitCount(faq.getHitCount() == null ? 1 : faq.getHitCount() + 1);
        faqMapper.updateById(update);
    }

    private void increaseChunkHitCount(List<QaKnowledgeChunk> chunks) {
        for (QaKnowledgeChunk chunk : chunks) {
            QaKnowledgeChunk update = new QaKnowledgeChunk();
            update.setId(chunk.getId());
            update.setHitCount(chunk.getHitCount() == null ? 1 : chunk.getHitCount() + 1);
            knowledgeChunkMapper.updateById(update);
        }
    }

    private int score(String content, Set<String> tokens) {
        if (content == null) {
            return 0;
        }
        String normalizedContent = normalize(content);
        int score = 0;
        for (String token : tokens) {
            if (normalizedContent.contains(token)) {
                score += token.length() >= 3 ? 2 : 1;
            }
        }
        return score;
    }

    private boolean match(String left, String right) {
        if (left == null || right == null || left.isBlank() || right.isBlank()) {
            return false;
        }
        String a = normalize(left);
        String b = normalize(right);
        return a.contains(b) || b.contains(a);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().replaceAll("[，。！？,.!?；;：:\\s]+", " ").toLowerCase();
    }

    private Set<String> searchTokens(String question) {
        LinkedHashSet<String> tokens = new LinkedHashSet<>();
        String normalized = normalize(question).replace(" ", "");
        for (String part : SPLIT_PATTERN.split(question == null ? "" : question.trim().toLowerCase())) {
            addToken(tokens, part);
        }
        for (int len : List.of(4, 3, 2)) {
            for (int i = 0; i + len <= normalized.length(); i++) {
                addToken(tokens, normalized.substring(i, i + len));
            }
        }
        return tokens;
    }

    private void addToken(Set<String> tokens, String value) {
        if (value == null) {
            return;
        }
        String token = value.trim().toLowerCase();
        if (token.length() < 2 || STOP_WORDS.contains(token)) {
            return;
        }
        tokens.add(token);
    }

    private String limitText(String value, int maxChars) {
        if (value == null || value.length() <= maxChars) {
            return value;
        }
        return value.substring(0, maxChars);
    }

    public record AnswerResult(String answer, String sourceType, String evidence) {
    }

    private record ScoredChunk(QaKnowledgeChunk chunk, int score) {
    }
}
