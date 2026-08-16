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

func (s *Service) CreateOpinionEvent(ctx context.Context, req *app_service.CreateOpinionEventReq) (*app_service.OpinionEventInfo, error) {
	if strings.TrimSpace(req.OrgId) == "" || strings.TrimSpace(req.CreatorId) == "" {
		return nil, publicOpinionError("app_public_opinion_invalid_argument", "orgId 和 creatorId 不能为空")
	}
	itemIDs, err := parsePublicOpinionIDs(req.ItemIds, "itemIds")
	if err != nil {
		return nil, err
	}
	event, status := s.cli.CreateOpinionEvent(ctx, orm.CreateOpinionEventInput{
		OrgID: req.OrgId, CreatorID: req.CreatorId, Title: req.Title, Topic: req.Topic, Summary: req.Summary, ItemIDs: itemIDs,
	})
	if status != nil {
		return nil, errStatus(errs.Code_AppPublicOpinion, status)
	}
	return toProtoOpinionEvent(event), nil
}

func (s *Service) UpdateOpinionEvent(ctx context.Context, req *app_service.UpdateOpinionEventReq) (*app_service.OpinionEventInfo, error) {
	eventID, err := parsePublicOpinionID(req.EventId, "eventId")
	if err != nil {
		return nil, err
	}
	if strings.TrimSpace(req.OrgId) == "" {
		return nil, publicOpinionError("app_public_opinion_invalid_argument", "orgId 不能为空")
	}
	event, status := s.cli.UpdateOpinionEvent(ctx, orm.UpdateOpinionEventInput{
		OrgID: req.OrgId, EventID: eventID, Title: req.Title, Topic: req.Topic, Summary: req.Summary,
	})
	if status != nil {
		return nil, errStatus(errs.Code_AppPublicOpinion, status)
	}
	return toProtoOpinionEvent(event), nil
}

func (s *Service) ListOpinionEvents(ctx context.Context, req *app_service.ListOpinionEventsReq) (*app_service.OpinionEventList, error) {
	if strings.TrimSpace(req.OrgId) == "" {
		return nil, publicOpinionError("app_public_opinion_invalid_argument", "orgId 不能为空")
	}
	pageNo, pageSize := publicOpinionPage(req.PageNo, req.PageSize)
	events, total, status := s.cli.ListOpinionEvents(ctx, orm.OpinionEventListFilter{
		OrgID: req.OrgId, Keyword: req.Keyword, Topic: req.Topic, Status: req.Status, PageNo: pageNo, PageSize: pageSize,
	})
	if status != nil {
		return nil, errStatus(errs.Code_AppPublicOpinion, status)
	}
	result := &app_service.OpinionEventList{Total: int32(total), PageNo: pageNo, PageSize: pageSize}
	for _, event := range events {
		result.Items = append(result.Items, toProtoOpinionEvent(event))
	}
	return result, nil
}

func (s *Service) GetOpinionEvent(ctx context.Context, req *app_service.GetOpinionEventReq) (*app_service.OpinionEventDetail, error) {
	eventID, err := parsePublicOpinionID(req.EventId, "eventId")
	if err != nil {
		return nil, err
	}
	if strings.TrimSpace(req.OrgId) == "" {
		return nil, publicOpinionError("app_public_opinion_invalid_argument", "orgId 不能为空")
	}
	detail, status := s.cli.GetOpinionEvent(ctx, eventID, req.OrgId)
	if status != nil {
		return nil, errStatus(errs.Code_AppPublicOpinion, status)
	}
	return toProtoOpinionEventDetail(detail), nil
}

func (s *Service) AddOpinionEventItems(ctx context.Context, req *app_service.ChangeOpinionEventItemsReq) (*app_service.OpinionEventDetail, error) {
	return s.changeOpinionEventItems(ctx, req, true)
}

func (s *Service) RemoveOpinionEventItems(ctx context.Context, req *app_service.ChangeOpinionEventItemsReq) (*app_service.OpinionEventDetail, error) {
	return s.changeOpinionEventItems(ctx, req, false)
}

func (s *Service) changeOpinionEventItems(ctx context.Context, req *app_service.ChangeOpinionEventItemsReq, add bool) (*app_service.OpinionEventDetail, error) {
	eventID, err := parsePublicOpinionID(req.EventId, "eventId")
	if err != nil {
		return nil, err
	}
	if strings.TrimSpace(req.OrgId) == "" || (add && strings.TrimSpace(req.CreatorId) == "") {
		return nil, publicOpinionError("app_public_opinion_invalid_argument", "orgId 和 creatorId 不能为空")
	}
	itemIDs, err := parsePublicOpinionIDs(req.ItemIds, "itemIds")
	if err != nil {
		return nil, err
	}
	input := orm.ChangeOpinionEventItemsInput{
		OrgID: req.OrgId, CreatorID: req.CreatorId, EventID: eventID, ItemIDs: itemIDs,
	}
	var detail *orm.OpinionEventDetail
	var status *errs.Status
	if add {
		detail, status = s.cli.AddOpinionEventItems(ctx, input)
	} else {
		detail, status = s.cli.RemoveOpinionEventItems(ctx, input)
	}
	if status != nil {
		return nil, errStatus(errs.Code_AppPublicOpinion, status)
	}
	return toProtoOpinionEventDetail(detail), nil
}

func (s *Service) UpdateOpinionEventStatus(ctx context.Context, req *app_service.UpdateOpinionEventStatusReq) (*app_service.OpinionEventInfo, error) {
	eventID, err := parsePublicOpinionID(req.EventId, "eventId")
	if err != nil {
		return nil, err
	}
	if strings.TrimSpace(req.OrgId) == "" {
		return nil, publicOpinionError("app_public_opinion_invalid_argument", "orgId 不能为空")
	}
	event, status := s.cli.UpdateOpinionEventStatus(ctx, eventID, req.OrgId, req.Status)
	if status != nil {
		return nil, errStatus(errs.Code_AppPublicOpinion, status)
	}
	return toProtoOpinionEvent(event), nil
}

func parsePublicOpinionID(value, field string) (uint32, error) {
	value = strings.TrimSpace(value)
	parsed, err := strconv.ParseUint(value, 10, 32)
	if err != nil || parsed == 0 {
		return 0, publicOpinionError("app_public_opinion_invalid_id", field, value)
	}
	return uint32(parsed), nil
}

func parsePublicOpinionIDs(values []string, field string) ([]uint32, error) {
	if len(values) == 0 {
		return nil, publicOpinionError("app_public_opinion_invalid_argument", field+" 不能为空")
	}
	result := make([]uint32, 0, len(values))
	seen := make(map[uint32]struct{}, len(values))
	for _, value := range values {
		id, err := parsePublicOpinionID(value, field)
		if err != nil {
			return nil, err
		}
		if _, ok := seen[id]; ok {
			return nil, publicOpinionError("app_public_opinion_invalid_argument", field+" 包含重复 ID")
		}
		seen[id] = struct{}{}
		result = append(result, id)
	}
	return result, nil
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

func toProtoOpinionItem(item *orm.OpinionItemWithEvent) *app_service.PublicOpinionItemInfo {
	info := item.Item
	return &app_service.PublicOpinionItemInfo{
		ItemId:       strconv.FormatUint(uint64(info.ID), 10),
		CreatedAt:    info.CreatedAt,
		UpdatedAt:    info.UpdatedAt,
		OrgId:        info.OrgID,
		CreatorId:    info.CreatorID,
		ImportTaskId: strconv.FormatUint(uint64(info.ImportTaskID), 10),
		Title:        info.Title,
		Content:      info.Content,
		Summary:      info.Summary,
		SourceName:   info.SourceName,
		SourceType:   info.SourceType,
		PublicUrl:    info.PublicURL,
		PublishedAt:  info.PublishedAt,
		CollectedAt:  info.CollectedAt,
		Topic:        info.Topic,
		Remark:       info.Remark,
		ContentHash:  info.ContentHash,
		UrlHash:      info.URLHash,
		EventId:      formatPublicOpinionUint32(item.EventID),
		EventTitle:   item.EventTitle,
		EventStatus:  item.EventStatus,
	}
}

func toProtoOpinionEvent(event *orm.OpinionEventWithCount) *app_service.OpinionEventInfo {
	return &app_service.OpinionEventInfo{
		EventId: strconv.FormatUint(uint64(event.ID), 10), CreatedAt: event.CreatedAt, UpdatedAt: event.UpdatedAt,
		OrgId: event.OrgID, CreatorId: event.CreatorID, Title: event.Title, Topic: event.Topic,
		Summary: event.Summary, Status: event.Status, ItemCount: event.ItemCount,
	}
}

func toProtoOpinionEventDetail(detail *orm.OpinionEventDetail) *app_service.OpinionEventDetail {
	result := &app_service.OpinionEventDetail{Event: toProtoOpinionEvent(detail.Event)}
	for _, item := range detail.Items {
		result.Items = append(result.Items, toProtoOpinionItem(item))
	}
	return result
}

func formatPublicOpinionUint32(value uint32) string {
	if value == 0 {
		return ""
	}
	return strconv.FormatUint(uint64(value), 10)
}
