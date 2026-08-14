package app

import (
	"context"
	"strconv"
	"strings"

	app_service "github.com/UnicomAI/wanwu/api/proto/app-service"
	errs "github.com/UnicomAI/wanwu/api/proto/err-code"
	"github.com/UnicomAI/wanwu/internal/app-service/client/model"
	"github.com/UnicomAI/wanwu/internal/app-service/client/orm"
)

func (s *Service) ImportPublicOpinion(ctx context.Context, req *app_service.ImportPublicOpinionReq) (*app_service.OpinionImportTaskInfo, error) {
	if strings.TrimSpace(req.OrgId) == "" || strings.TrimSpace(req.CreatorId) == "" || strings.TrimSpace(req.FileName) == "" || strings.TrimSpace(req.FilePath) == "" {
		return nil, publicOpinionError("app_public_opinion_invalid_argument", "orgId、creatorId、fileName 和 filePath 不能为空")
	}
	if req.FileSize < 0 {
		return nil, publicOpinionError("app_public_opinion_invalid_argument", "fileSize 不能为负数")
	}
	task, status := s.cli.ImportPublicOpinion(ctx, orm.PublicOpinionImportInput{
		OrgID:     req.OrgId,
		CreatorID: req.CreatorId,
		FileName:  req.FileName,
		FileType:  req.FileType,
		FileSize:  req.FileSize,
		FileRef:   req.FilePath,
	})
	if status != nil {
		return nil, errStatus(errs.Code_AppPublicOpinion, status)
	}
	return toProtoOpinionImportTask(task), nil
}

func (s *Service) GetPublicOpinionImportTask(ctx context.Context, req *app_service.GetPublicOpinionImportTaskReq) (*app_service.OpinionImportTaskInfo, error) {
	taskID, err := parsePublicOpinionID(req.TaskId, "taskId")
	if err != nil {
		return nil, err
	}
	if strings.TrimSpace(req.OrgId) == "" {
		return nil, publicOpinionError("app_public_opinion_invalid_argument", "orgId 不能为空")
	}
	task, status := s.cli.GetOpinionImportTask(ctx, taskID, req.OrgId)
	if status != nil {
		return nil, errStatus(errs.Code_AppPublicOpinion, status)
	}
	return toProtoOpinionImportTask(task), nil
}

func (s *Service) ListPublicOpinionItems(ctx context.Context, req *app_service.ListPublicOpinionItemsReq) (*app_service.PublicOpinionItemList, error) {
	if strings.TrimSpace(req.OrgId) == "" {
		return nil, publicOpinionError("app_public_opinion_invalid_argument", "orgId 不能为空")
	}
	if req.StartTime < 0 || req.EndTime < 0 || (req.StartTime > 0 && req.EndTime > 0 && req.StartTime > req.EndTime) {
		return nil, publicOpinionError("app_public_opinion_invalid_argument", "时间范围不合法")
	}
	pageNo, pageSize := publicOpinionPage(req.PageNo, req.PageSize)
	items, total, status := s.cli.ListOpinionItems(ctx, orm.PublicOpinionListFilter{
		OrgID:      req.OrgId,
		Keyword:    req.Keyword,
		StartTime:  req.StartTime,
		EndTime:    req.EndTime,
		SourceType: req.SourceType,
		Topic:      req.Topic,
		PageNo:     pageNo,
		PageSize:   pageSize,
	})
	if status != nil {
		return nil, errStatus(errs.Code_AppPublicOpinion, status)
	}
	resp := &app_service.PublicOpinionItemList{
		Total:    int32(total),
		PageNo:   pageNo,
		PageSize: pageSize,
	}
	for _, item := range items {
		resp.Items = append(resp.Items, toProtoOpinionItem(item))
	}
	return resp, nil
}

func (s *Service) GetPublicOpinionItem(ctx context.Context, req *app_service.GetPublicOpinionItemReq) (*app_service.PublicOpinionItemInfo, error) {
	itemID, err := parsePublicOpinionID(req.ItemId, "itemId")
	if err != nil {
		return nil, err
	}
	if strings.TrimSpace(req.OrgId) == "" {
		return nil, publicOpinionError("app_public_opinion_invalid_argument", "orgId 不能为空")
	}
	item, status := s.cli.GetOpinionItem(ctx, itemID, req.OrgId)
	if status != nil {
		return nil, errStatus(errs.Code_AppPublicOpinion, status)
	}
	return toProtoOpinionItem(item), nil
}

func parsePublicOpinionID(value, field string) (uint32, error) {
	value = strings.TrimSpace(value)
	parsed, err := strconv.ParseUint(value, 10, 32)
	if err != nil || parsed == 0 {
		return 0, publicOpinionError("app_public_opinion_invalid_id", field, value)
	}
	return uint32(parsed), nil
}

func publicOpinionError(key string, args ...string) error {
	return errStatus(errs.Code_AppPublicOpinion, &errs.Status{TextKey: key, Args: args})
}

func publicOpinionPage(pageNo, pageSize int32) (int32, int32) {
	if pageNo < 1 {
		pageNo = 1
	}
	if pageSize < 1 {
		pageSize = 10
	}
	if pageSize > 100 {
		pageSize = 100
	}
	return pageNo, pageSize
}

func toProtoOpinionImportTask(task *model.OpinionImportTask) *app_service.OpinionImportTaskInfo {
	return &app_service.OpinionImportTaskInfo{
		TaskId:        strconv.FormatUint(uint64(task.ID), 10),
		CreatedAt:     task.CreatedAt,
		UpdatedAt:     task.UpdatedAt,
		OrgId:         task.OrgID,
		CreatorId:     task.CreatorID,
		FileName:      task.FileName,
		FileType:      task.FileType,
		FileSize:      task.FileSize,
		FileHash:      task.FileHash,
		Status:        task.Status,
		TotalRows:     task.TotalRows,
		SuccessRows:   task.SuccessRows,
		DuplicateRows: task.DuplicateRows,
		FailedRows:    task.FailedRows,
		ErrorDetail:   task.ErrorDetail,
		StartedAt:     task.StartedAt,
		FinishedAt:    task.FinishedAt,
	}
}

func toProtoOpinionItem(item *model.OpinionItem) *app_service.PublicOpinionItemInfo {
	return &app_service.PublicOpinionItemInfo{
		ItemId:       strconv.FormatUint(uint64(item.ID), 10),
		CreatedAt:    item.CreatedAt,
		UpdatedAt:    item.UpdatedAt,
		OrgId:        item.OrgID,
		CreatorId:    item.CreatorID,
		ImportTaskId: strconv.FormatUint(uint64(item.ImportTaskID), 10),
		Title:        item.Title,
		Content:      item.Content,
		Summary:      item.Summary,
		SourceName:   item.SourceName,
		SourceType:   item.SourceType,
		PublicUrl:    item.PublicURL,
		PublishedAt:  item.PublishedAt,
		CollectedAt:  item.CollectedAt,
		Topic:        item.Topic,
		Remark:       item.Remark,
		ContentHash:  item.ContentHash,
		UrlHash:      item.URLHash,
	}
}
