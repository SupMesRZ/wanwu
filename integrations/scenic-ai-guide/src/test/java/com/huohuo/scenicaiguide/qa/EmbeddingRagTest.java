package com.huohuo.scenicaiguide.qa;

import com.huohuo.scenicaiguide.common.TestTags;
import org.junit.jupiter.api.Tag;

import io.qdrant.client.QdrantClient;
import io.qdrant.client.grpc.Collections;
import io.qdrant.client.grpc.Common;
import io.qdrant.client.grpc.JsonWithInt;
import io.qdrant.client.grpc.Points;
import org.junit.jupiter.api.*;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Embedding 模型 + Qdrant 向量检索 + RAG 全流程测试
 *
 * 使用景区讲解词验证：切片 → 向量化 → 存储 → 检索 → LLM 生成
 */
@Tag(TestTags.EXTERNAL)
@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class EmbeddingRagTest {

    @Autowired
    private EmbeddingModel embeddingModel;

    @Autowired
    private QdrantClient qdrantClient;

    @Autowired
    private ChatModel chatModel;

    @Value("${qdrant.collection-name}")
    private String collectionName;

    private static final Path MD_DIR = Paths.get("src/test/resources/md资源包");

    /** 向量维度，由第一步实测获取 */
    private int vectorDimension;

    /** 切片后的文档块 */
    private List<DocumentChunk> chunks;

    /**
     * 文档切片数据结构
     */
    record DocumentChunk(String source, String section, String text, int chunkIndex) {}

    /**
     * 按 Markdown ### 标题切分，正文超过 800 字时按句号二次切分
     */
    private List<DocumentChunk> chunkMarkdown(String content, String sourceFile) {
        List<DocumentChunk> result = new ArrayList<>();
        // 去掉文件开头到第一个 ### 之前的说明文字
        int firstSection = content.indexOf("###");
        if (firstSection < 0) {
            return result;
        }
        String body = content.substring(firstSection);

        // 按 ### 切分
        String[] rawSections = body.split("(?m)^###\\s+");
        int index = 0;
        for (String raw : rawSections) {
            String trimmed = raw.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            String[] parts = trimmed.split("\n", 2);
            String title = parts[0].trim();
            String sectionText = parts.length > 1 ? parts[1].trim() : "";

            // 过滤太短的内容
            if (sectionText.length() < 30) {
                continue;
            }

            // 超过 800 字时，按句号二次切分
            if (sectionText.length() > 800) {
                List<String> subChunks = splitBySentence(sectionText, 600);
                for (String sub : subChunks) {
                    result.add(new DocumentChunk(sourceFile, title, sub, index++));
                }
            } else {
                result.add(new DocumentChunk(sourceFile, title, sectionText, index++));
            }
        }
        return result;
    }

    /**
     * 按句号切分文本，每个片段不超过 maxLen 字（尽量在句号处断开）
     */
    private List<String> splitBySentence(String text, int maxLen) {
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        // 按句号分割，保留句号
        String[] sentences = text.split("(?<=。)");
        for (String sentence : sentences) {
            if (current.length() + sentence.length() > maxLen && current.length() > 0) {
                result.add(current.toString().trim());
                current = new StringBuilder();
            }
            current.append(sentence);
        }
        if (current.length() > 0) {
            result.add(current.toString().trim());
        }
        // 如果某个片段仍然太长（没有句号），强制截断
        List<String> finalResult = new ArrayList<>();
        for (String seg : result) {
            if (seg.length() > maxLen) {
                for (int i = 0; i < seg.length(); i += maxLen) {
                    finalResult.add(seg.substring(i, Math.min(i + maxLen, seg.length())));
                }
            } else {
                finalResult.add(seg);
            }
        }
        return finalResult;
    }

    /**
     * float[] 转 List&lt;Float&gt;，用于 Qdrant
     */
    private List<Float> toFloatVector(float[] floatArray) {
        List<Float> list = new ArrayList<>(floatArray.length);
        for (float f : floatArray) {
            list.add(f);
        }
        return list;
    }

    @Test
    @Order(1)
    @DisplayName("embedding 模型连通性：获取向量维度")
    void testEmbeddingModelConnectivity() {
        String sample = "庆都山位于河北唐县，海拔约220米，是唐尧文化的发祥地";

        EmbeddingRequest request = new EmbeddingRequest(List.of(sample), null);
        EmbeddingResponse response = embeddingModel.call(request);

        assertNotNull(response, "embedding 响应不应为空");
        assertFalse(response.getResults().isEmpty(), "应至少返回一个 embedding");

        float[] vector = response.getResults().get(0).getOutput();
        assertNotNull(vector, "向量不应为空");
        assertTrue(vector.length >= 128, "向量维度应 >= 128");

        vectorDimension = vector.length;
        System.out.println("=== Embedding 模型连通性 OK ===");
        System.out.println("模型: qwen3-vl-embedding");
        System.out.println("向量维度: " + vectorDimension);
    }

    @Test
    @Order(2)
    @DisplayName("Qdrant：删除旧 collection 并重建")
    void testQdrantCollectionSetup() throws Exception {
        // 如果存在则删除
        boolean exists = qdrantClient.collectionExistsAsync(collectionName).get();
        if (exists) {
            qdrantClient.deleteCollectionAsync(collectionName).get();
            System.out.println("已删除旧 collection: " + collectionName);
        }

        // 创建新 collection
        Collections.VectorParams params = Collections.VectorParams.newBuilder()
                .setSize(vectorDimension)
                .setDistance(Collections.Distance.Cosine)
                .build();

        qdrantClient.createCollectionAsync(collectionName, params).get();

        // 验证创建成功
        boolean created = qdrantClient.collectionExistsAsync(collectionName).get();
        assertTrue(created, "collection 应创建成功");

        System.out.println("=== Qdrant Collection 重建 OK ===");
        System.out.println("collection: " + collectionName);
        System.out.println("向量维度: " + vectorDimension);
        System.out.println("距离度量: Cosine");
    }

    @Test
    @Order(3)
    @DisplayName("读取讲解词 → 切片 → 向量化 → 存入 Qdrant")
    void testChunkAndStore() throws Exception {
        // 读取所有 md 文件
        chunks = new ArrayList<>();
        try (var stream = Files.list(MD_DIR)) {
            List<Path> mdFiles = stream
                    .filter(p -> p.toString().endsWith(".md"))
                    .toList();
            for (Path mdFile : mdFiles) {
                String content = Files.readString(mdFile);
                String fileName = mdFile.getFileName().toString();
                List<DocumentChunk> fileChunks = chunkMarkdown(content, fileName);
                chunks.addAll(fileChunks);
                System.out.println("[" + fileName + "] 切片数: " + fileChunks.size());
            }
        }

        assertFalse(chunks.isEmpty(), "应至少有一个切片");
        System.out.println("总切片数: " + chunks.size());

        // 批量 embed（每批 10 个，避免单次请求过大）
        List<List<Float>> allVectors = new ArrayList<>();
        int batchSize = 10;
        for (int i = 0; i < chunks.size(); i += batchSize) {
            List<String> batchTexts = chunks.subList(i, Math.min(i + batchSize, chunks.size()))
                    .stream()
                    .map(c -> c.text)
                    .toList();

            EmbeddingRequest request = new EmbeddingRequest(batchTexts, null);
            EmbeddingResponse response = embeddingModel.call(request);

            for (var embedding : response.getResults()) {
                allVectors.add(toFloatVector(embedding.getOutput()));
            }
            System.out.println("Embedding 批次 " + (i / batchSize + 1) + " 完成");
        }

        assertEquals(chunks.size(), allVectors.size(), "向量数应与切片数一致");

        // 写入 Qdrant
        List<Points.PointStruct> points = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            DocumentChunk chunk = chunks.get(i);
            Points.PointStruct point = Points.PointStruct.newBuilder()
                    .setId(Common.PointId.newBuilder().setNum(i + 1))
                    .setVectors(Points.Vectors.newBuilder()
                            .setVector(Points.Vector.newBuilder()
                                    .addAllData(allVectors.get(i))
                                    .build())
                            .build())
                    .putPayload("source", JsonWithInt.Value.newBuilder()
                            .setStringValue(chunk.source).build())
                    .putPayload("section", JsonWithInt.Value.newBuilder()
                            .setStringValue(chunk.section).build())
                    .putPayload("text", JsonWithInt.Value.newBuilder()
                            .setStringValue(chunk.text).build())
                    .putPayload("chunk_index", JsonWithInt.Value.newBuilder()
                            .setIntegerValue(chunk.chunkIndex).build())
                    .build();
            points.add(point);
        }

        qdrantClient.upsertAsync(collectionName, points).get();

        // 验证写入
        long count = qdrantClient.countAsync(collectionName).get();
        assertEquals(chunks.size(), count, "Qdrant 中点数应与切片数一致");

        System.out.println("=== 切片存储 OK ===");
        System.out.println("写入 Qdrant 点数: " + chunks.size());

        // 打印切片样例
        System.out.println("\n--- 切片样例 ---");
        for (int i = 0; i < Math.min(3, chunks.size()); i++) {
            DocumentChunk c = chunks.get(i);
            System.out.println("[" + i + "] " + c.source + " / " + c.section);
            System.out.println("    " + c.text.substring(0, Math.min(60, c.text.length())) + "...");
        }
    }

    @Test
    @Order(4)
    @DisplayName("相似度检索：游客提问测试")
    void testSimilaritySearch() throws Exception {
        // 游客提问
        String[] queries = {
                "庆都山有什么好玩的",
                "唐尧古城有什么好吃的",
                "怎么游览庆都山"
        };

        for (String query : queries) {
            System.out.println("\n=== 查询: " + query + " ===");

            // 查询向量化
            EmbeddingRequest request = new EmbeddingRequest(List.of(query), null);
            EmbeddingResponse response = embeddingModel.call(request);
            List<Float> queryVector = toFloatVector(response.getResults().get(0).getOutput());

            // Qdrant 搜索 top-3
            Points.SearchPoints searchRequest = Points.SearchPoints.newBuilder()
                    .setCollectionName(collectionName)
                    .addAllVector(queryVector)
                    .setLimit(3)
                    .setWithPayload(Points.WithPayloadSelector.newBuilder().setEnable(true).build())
                    .build();

            List<Points.ScoredPoint> results = qdrantClient.searchAsync(searchRequest).get();

            assertFalse(results.isEmpty(), "应至少召回一个结果");

            System.out.println("召回 " + results.size() + " 条:");
            for (int i = 0; i < results.size(); i++) {
                Points.ScoredPoint point = results.get(i);
                String text = point.getPayloadOrDefault("text",
                        JsonWithInt.Value.newBuilder().setStringValue("").build()
                ).getStringValue();
                String section = point.getPayloadOrDefault("section",
                        JsonWithInt.Value.newBuilder().setStringValue("").build()
                ).getStringValue();
                System.out.println("  [" + (i + 1) + "] score=" + String.format("%.4f", point.getScore())
                        + " section=" + section);
                System.out.println("      " + text.substring(0, Math.min(80, text.length())) + "...");
            }
        }
    }

    @Test
    @Order(5)
    @DisplayName("RAG 全流程：检索 + LLM 生成回答")
    void testRagPipeline() throws Exception {
        String userQuestion = "我想去庆都山玩，有什么推荐的路线和必看景点？";
        System.out.println("\n=== RAG 测试 ===");
        System.out.println("游客提问: " + userQuestion);

        // 1. 检索
        EmbeddingRequest embedReq = new EmbeddingRequest(List.of(userQuestion), null);
        EmbeddingResponse embedResp = embeddingModel.call(embedReq);
        List<Float> queryVector = toFloatVector(embedResp.getResults().get(0).getOutput());

        Points.SearchPoints searchRequest = Points.SearchPoints.newBuilder()
                .setCollectionName(collectionName)
                .addAllVector(queryVector)
                .setLimit(3)
                .setWithPayload(Points.WithPayloadSelector.newBuilder().setEnable(true).build())
                .build();

        List<Points.ScoredPoint> results = qdrantClient.searchAsync(searchRequest).get();

        assertFalse(results.isEmpty(), "应召回至少一条知识");

        // 2. 组装检索上下文
        StringBuilder context = new StringBuilder();
        for (Points.ScoredPoint point : results) {
            String text = point.getPayloadOrDefault("text",
                    JsonWithInt.Value.newBuilder().setStringValue("").build()
            ).getStringValue();
            context.append(text).append("\n\n");
        }

        System.out.println("\n召回上下文（共 " + results.size() + " 条）:");
        for (int i = 0; i < results.size(); i++) {
            String text = results.get(i).getPayloadOrDefault("text",
                    JsonWithInt.Value.newBuilder().setStringValue("").build()
            ).getStringValue();
            System.out.println("[" + (i + 1) + "] " + text.substring(0, Math.min(60, text.length())) + "...");
        }

        // 3. 组装 Prompt
        String systemPrompt = "你是景区AI导览员。请根据以下参考资料回答游客问题，如果资料不足请诚实说明。\n\n参考资料：\n"
                + context;

        List<Message> messages = List.of(
                new SystemMessage(systemPrompt),
                new UserMessage(userQuestion)
        );

        // 4. LLM 生成
        Prompt prompt = new Prompt(messages);
        ChatResponse response = chatModel.call(prompt);

        assertNotNull(response);
        assertNotNull(response.getResult());
        assertNotNull(response.getResult().getOutput());

        String answer = response.getResult().getOutput().getText();
        assertNotNull(answer);
        assertFalse(answer.isBlank());

        System.out.println("\n=== AI 回答 ===");
        System.out.println(answer);

        // 5. 验证回答基于检索内容（简单启发式：检查回答中是否包含关键词）
        String lowerAnswer = answer.toLowerCase();
        boolean hasRelevantKeywords = lowerAnswer.contains("庆都") || lowerAnswer.contains("尧")
                || lowerAnswer.contains("古镇") || lowerAnswer.contains("灵源");
        assertTrue(hasRelevantKeywords,
                "回答应包含景区相关关键词，验证基于知识库生成而非幻觉");
    }
}
