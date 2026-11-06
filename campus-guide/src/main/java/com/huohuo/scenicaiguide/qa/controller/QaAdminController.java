package com.huohuo.scenicaiguide.qa.controller;

import com.huohuo.scenicaiguide.common.api.ApiResponse;
import com.huohuo.scenicaiguide.common.api.PageResponse;
import com.huohuo.scenicaiguide.qa.dto.request.FaqRequest;
import com.huohuo.scenicaiguide.qa.dto.request.GuideScriptRequest;
import com.huohuo.scenicaiguide.qa.dto.request.KnowledgeDocRequest;
import com.huohuo.scenicaiguide.qa.dto.request.QaAnswerPreviewRequest;
import com.huohuo.scenicaiguide.qa.dto.response.FaqResponse;
import com.huohuo.scenicaiguide.qa.dto.response.GuideScriptResponse;
import com.huohuo.scenicaiguide.qa.dto.response.KnowledgeChunkResponse;
import com.huohuo.scenicaiguide.qa.dto.response.KnowledgeDocResponse;
import com.huohuo.scenicaiguide.qa.dto.response.QaAnswerPreviewResponse;
import com.huohuo.scenicaiguide.qa.service.QaAdminService;
import com.huohuo.scenicaiguide.qa.service.QaGuideAnswerService;
import com.huohuo.scenicaiguide.system.security.PermissionCode;
import com.huohuo.scenicaiguide.system.security.RequireAdmin;
import com.huohuo.scenicaiguide.system.security.RequirePermission;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "知识库管理", description = "知识文档、切片、FAQ 和讲解词")
@RestController
@RequestMapping("/api/v1/admin/qa")
@RequireAdmin
@RequirePermission(PermissionCode.QA_READ)
public class QaAdminController {

    private final QaAdminService qaAdminService;
    private final QaGuideAnswerService qaGuideAnswerService;

    @Autowired
    public QaAdminController(QaAdminService qaAdminService, QaGuideAnswerService qaGuideAnswerService) {
        this.qaAdminService = qaAdminService;
        this.qaGuideAnswerService = qaGuideAnswerService;
    }

    @Operation(summary = "查询知识文档")
    @GetMapping("/knowledge-docs")
    public ApiResponse<PageResponse<KnowledgeDocResponse>> listDocs(
            @RequestParam(required = false) Long scenicAreaId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String vectorStatus,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long pageSize) {
        return ApiResponse.ok(qaAdminService.listDocs(scenicAreaId, keyword, vectorStatus, page, pageSize));
    }

    @Operation(summary = "创建知识文档")
    @PostMapping("/knowledge-docs")
    @RequirePermission(PermissionCode.QA_WRITE)
    public ApiResponse<KnowledgeDocResponse> createDoc(@Valid @RequestBody KnowledgeDocRequest request) {
        return ApiResponse.ok(qaAdminService.createDoc(request));
    }

    @Operation(summary = "更新知识文档")
    @PutMapping("/knowledge-docs/{id}")
    @RequirePermission(PermissionCode.QA_WRITE)
    public ApiResponse<KnowledgeDocResponse> updateDoc(
            @PathVariable Long id,
            @Valid @RequestBody KnowledgeDocRequest request) {
        return ApiResponse.ok(qaAdminService.updateDoc(id, request));
    }

    @Operation(summary = "删除知识文档")
    @DeleteMapping("/knowledge-docs/{id}")
    @RequirePermission(PermissionCode.QA_WRITE)
    public ApiResponse<Void> deleteDoc(@PathVariable Long id) {
        qaAdminService.deleteDoc(id);
        return ApiResponse.ok();
    }

    @Operation(summary = "重建知识文档索引")
    // 重建会批量消耗 embedding 额度，单独一个权限码
    @PostMapping("/knowledge-docs/{id}/reindex")
    @RequirePermission(PermissionCode.QA_REINDEX)
    public ApiResponse<KnowledgeDocResponse> reindexDoc(@PathVariable Long id) {
        return ApiResponse.ok(qaAdminService.reindexDoc(id));
    }

    @Operation(summary = "查询指定文档切片")
    @GetMapping("/knowledge-docs/{id}/chunks")
    public ApiResponse<PageResponse<KnowledgeChunkResponse>> listDocChunks(
            @PathVariable Long id,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long pageSize) {
        return ApiResponse.ok(qaAdminService.listChunks(id, null, keyword, page, pageSize));
    }

    @Operation(summary = "查询知识切片")
    @GetMapping("/knowledge-chunks")
    public ApiResponse<PageResponse<KnowledgeChunkResponse>> listChunks(
            @RequestParam(required = false) Long docId,
            @RequestParam(required = false) Long scenicAreaId,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long pageSize) {
        return ApiResponse.ok(qaAdminService.listChunks(docId, scenicAreaId, keyword, page, pageSize));
    }

    @Operation(summary = "查询 FAQ")
    @GetMapping("/faqs")
    public ApiResponse<PageResponse<FaqResponse>> listFaqs(
            @RequestParam(required = false) Long scenicAreaId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long pageSize) {
        return ApiResponse.ok(qaAdminService.listFaqs(scenicAreaId, keyword, status, page, pageSize));
    }

    @Operation(summary = "创建 FAQ")
    @PostMapping("/faqs")
    @RequirePermission(PermissionCode.QA_WRITE)
    public ApiResponse<FaqResponse> createFaq(@Valid @RequestBody FaqRequest request) {
        return ApiResponse.ok(qaAdminService.createFaq(request));
    }

    @Operation(summary = "更新 FAQ")
    @PutMapping("/faqs/{id}")
    @RequirePermission(PermissionCode.QA_WRITE)
    public ApiResponse<FaqResponse> updateFaq(@PathVariable Long id, @Valid @RequestBody FaqRequest request) {
        return ApiResponse.ok(qaAdminService.updateFaq(id, request));
    }

    @Operation(summary = "删除 FAQ")
    @DeleteMapping("/faqs/{id}")
    @RequirePermission(PermissionCode.QA_WRITE)
    public ApiResponse<Void> deleteFaq(@PathVariable Long id) {
        qaAdminService.deleteFaq(id);
        return ApiResponse.ok();
    }

    @Operation(summary = "查询讲解词")
    @GetMapping("/guide-scripts")
    public ApiResponse<PageResponse<GuideScriptResponse>> listGuideScripts(
            @RequestParam(required = false) Long scenicAreaId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long pageSize) {
        return ApiResponse.ok(qaAdminService.listGuideScripts(scenicAreaId, keyword, status, page, pageSize));
    }

    @Operation(summary = "创建讲解词")
    @PostMapping("/guide-scripts")
    @RequirePermission(PermissionCode.QA_WRITE)
    public ApiResponse<GuideScriptResponse> createGuideScript(@Valid @RequestBody GuideScriptRequest request) {
        return ApiResponse.ok(qaAdminService.createGuideScript(request));
    }

    @Operation(summary = "更新讲解词")
    @PutMapping("/guide-scripts/{id}")
    @RequirePermission(PermissionCode.QA_WRITE)
    public ApiResponse<GuideScriptResponse> updateGuideScript(
            @PathVariable Long id,
            @Valid @RequestBody GuideScriptRequest request) {
        return ApiResponse.ok(qaAdminService.updateGuideScript(id, request));
    }

    @Operation(summary = "删除讲解词")
    @DeleteMapping("/guide-scripts/{id}")
    @RequirePermission(PermissionCode.QA_WRITE)
    public ApiResponse<Void> deleteGuideScript(@PathVariable Long id) {
        qaAdminService.deleteGuideScript(id);
        return ApiResponse.ok();
    }

    @Operation(summary = "预览用户问答命中结果")
    @PostMapping("/answer-preview")
    public ApiResponse<QaAnswerPreviewResponse> previewAnswer(@Valid @RequestBody QaAnswerPreviewRequest request) {
        return ApiResponse.ok(QaAnswerPreviewResponse.from(qaGuideAnswerService.preview(
            request.getScenicAreaId(), request.getQuestion(), request.getCurrentSpotName())));
    }
}
