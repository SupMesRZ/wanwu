package orm

import (
	"context"
	"errors"
	"fmt"
	"sort"
	"strings"
	"unicode/utf8"

	errs "github.com/UnicomAI/wanwu/api/proto/err-code"
	"github.com/UnicomAI/wanwu/internal/app-service/client/model"
	"github.com/UnicomAI/wanwu/pkg/util"
	"gorm.io/gorm"
	"gorm.io/gorm/clause"
)

type CreateOpinionEventInput struct {
	OrgID     string
	CreatorID string
	Title     string
	Topic     string
	Summary   string
	ItemIDs   []uint32
}

type UpdateOpinionEventInput struct {
	OrgID   string
	EventID uint32
	Title   string
	Topic   string
	Summary string
}

type ChangeOpinionEventItemsInput struct {
	OrgID     string
	CreatorID string
	EventID   uint32
	ItemIDs   []uint32
}

type OpinionEventListFilter struct {
	OrgID    string
	Keyword  string
	Topic    string
	Status   string
	PageNo   int32
	PageSize int32
}

type OpinionEventWithCount struct {
	model.OpinionEvent
	ItemCount int32 `gorm:"column:item_count"`
}

type OpinionItemWithEvent struct {
	Item        *model.OpinionItem
	EventID     uint32
	EventTitle  string
	EventStatus string
}

type OpinionEventDetail struct {
	Event *OpinionEventWithCount
	Items []*OpinionItemWithEvent
}

type opinionEventTxError struct {
	status *errs.Status
}

func (e *opinionEventTxError) Error() string {
	return e.status.TextKey
}

func abortOpinionEventTx(status *errs.Status) error {
	return &opinionEventTxError{status: status}
}

func (c *Client) runOpinionEventTx(ctx context.Context, databaseErrorKey string, fn func(tx *gorm.DB) error) *errs.Status {
	err := c.db.WithContext(ctx).Transaction(fn)
	if err == nil {
		return nil
	}
	var txErr *opinionEventTxError
	if errors.As(err, &txErr) {
		return txErr.status
	}
	return toErrStatus(databaseErrorKey, err.Error())
}

func (c *Client) CreateOpinionEvent(ctx context.Context, input CreateOpinionEventInput) (*OpinionEventWithCount, *errs.Status) {
	orgID := strings.TrimSpace(input.OrgID)
	creatorID := strings.TrimSpace(input.CreatorID)
	title, topic, summary, status := validateOpinionEventFields(input.Title, input.Topic, input.Summary)
	if status != nil {
		return nil, status
	}
	if orgID == "" || creatorID == "" {
		return nil, toErrStatus("app_public_opinion_invalid_argument", "orgId 和 creatorId 不能为空")
	}
	itemIDs, status := normalizeOpinionEventItemIDs(input.ItemIDs)
	if status != nil {
		return nil, status
	}

	var result *OpinionEventWithCount
	status = c.runOpinionEventTx(ctx, "app_public_opinion_event_create", func(tx *gorm.DB) error {
		if status := lockOpinionItemsForEvent(tx, orgID, itemIDs); status != nil {
			return abortOpinionEventTx(status)
		}
		if status := ensureOpinionItemsUnassigned(tx, orgID, itemIDs); status != nil {
			return abortOpinionEventTx(status)
		}
		event := &model.OpinionEvent{
			OrgID: orgID, CreatorID: creatorID, Title: title, Topic: topic, Summary: summary,
			Status: model.OpinionEventStatusDraft,
		}
		if err := tx.Create(event).Error; err != nil {
			return err
		}
		relations := make([]*model.OpinionEventItem, 0, len(itemIDs))
		for _, itemID := range itemIDs {
			relations = append(relations, &model.OpinionEventItem{
				OrgID: orgID, EventID: event.ID, ItemID: itemID,
				RelationType: model.OpinionEventRelationTypeManual, CreatorID: creatorID,
			})
		}
		if err := tx.Create(&relations).Error; err != nil {
			return err
		}
		result = &OpinionEventWithCount{OpinionEvent: *event, ItemCount: int32(len(itemIDs))}
		return nil
	})
	if status != nil {
		return nil, status
	}
	return result, nil
}

func (c *Client) ListOpinionEvents(ctx context.Context, filter OpinionEventListFilter) ([]*OpinionEventWithCount, int64, *errs.Status) {
	orgID := strings.TrimSpace(filter.OrgID)
	if orgID == "" {
		return nil, 0, toErrStatus("app_public_opinion_invalid_argument", "orgId 不能为空")
	}
	statusValue := strings.TrimSpace(filter.Status)
	if statusValue != "" && !isOpinionEventStatus(statusValue) {
		return nil, 0, toErrStatus("app_public_opinion_invalid_argument", "事件状态不合法")
	}
	pageNo, pageSize := normalizeOpinionEventPage(filter.PageNo, filter.PageSize)
	query := c.db.WithContext(ctx).Model(&model.OpinionEvent{}).Where("opinion_events.org_id = ?", orgID)
	if keyword := strings.TrimSpace(filter.Keyword); keyword != "" {
		like := "%" + keyword + "%"
		query = query.Where("opinion_events.title LIKE ? OR opinion_events.summary LIKE ?", like, like)
	}
	if topic := strings.TrimSpace(filter.Topic); topic != "" {
		query = query.Where("opinion_events.topic = ?", topic)
	}
	if statusValue != "" {
		query = query.Where("opinion_events.status = ?", statusValue)
	}
	var total int64
	if err := query.Count(&total).Error; err != nil {
		return nil, 0, toErrStatus("app_public_opinion_event_list", err.Error())
	}
	var events []*OpinionEventWithCount
	selectQuery := query.
		Joins("LEFT JOIN (SELECT org_id, event_id, COUNT(*) AS item_count FROM opinion_event_items GROUP BY org_id, event_id) AS event_counts ON event_counts.org_id = opinion_events.org_id AND event_counts.event_id = opinion_events.id").
		Select("opinion_events.*, COALESCE(event_counts.item_count, 0) AS item_count")
	if err := selectQuery.Order("opinion_events.updated_at DESC, opinion_events.id DESC").Offset(int((pageNo - 1) * pageSize)).Limit(int(pageSize)).Find(&events).Error; err != nil {
		return nil, 0, toErrStatus("app_public_opinion_event_list", err.Error())
	}
	return events, total, nil
}

func (c *Client) GetOpinionEvent(ctx context.Context, eventID uint32, orgID string) (*OpinionEventDetail, *errs.Status) {
	if eventID == 0 || strings.TrimSpace(orgID) == "" {
		return nil, toErrStatus("app_public_opinion_invalid_argument", "eventId 和 orgId 不能为空")
	}
	detail, status := getOpinionEventDetail(c.db.WithContext(ctx), eventID, strings.TrimSpace(orgID))
	if status != nil {
		return nil, status
	}
	return detail, nil
}

func (c *Client) UpdateOpinionEvent(ctx context.Context, input UpdateOpinionEventInput) (*OpinionEventWithCount, *errs.Status) {
	orgID := strings.TrimSpace(input.OrgID)
	if input.EventID == 0 || orgID == "" {
		return nil, toErrStatus("app_public_opinion_invalid_argument", "eventId 和 orgId 不能为空")
	}
	title, topic, summary, status := validateOpinionEventFields(input.Title, input.Topic, input.Summary)
	if status != nil {
		return nil, status
	}
	status = c.runOpinionEventTx(ctx, "app_public_opinion_event_update", func(tx *gorm.DB) error {
		event, status := lockOpinionEvent(tx, input.EventID, orgID)
		if status != nil {
			return abortOpinionEventTx(status)
		}
		if event.Status == model.OpinionEventStatusArchived {
			return abortOpinionEventTx(toErrStatus("app_public_opinion_event_archived", util.Int2Str(event.ID)))
		}
		event.Title, event.Topic, event.Summary = title, topic, summary
		if err := tx.Model(event).Select("title", "topic", "summary", "updated_at").Updates(event).Error; err != nil {
			return err
		}
		return nil
	})
	if status != nil {
		return nil, status
	}
	detail, status := c.GetOpinionEvent(ctx, input.EventID, orgID)
	if status != nil {
		return nil, status
	}
	return detail.Event, nil
}

func (c *Client) AddOpinionEventItems(ctx context.Context, input ChangeOpinionEventItemsInput) (*OpinionEventDetail, *errs.Status) {
	orgID := strings.TrimSpace(input.OrgID)
	creatorID := strings.TrimSpace(input.CreatorID)
	if input.EventID == 0 || orgID == "" || creatorID == "" {
		return nil, toErrStatus("app_public_opinion_invalid_argument", "eventId、orgId 和 creatorId 不能为空")
	}
	itemIDs, status := normalizeOpinionEventItemIDs(input.ItemIDs)
	if status != nil {
		return nil, status
	}
	var result *OpinionEventDetail
	status = c.runOpinionEventTx(ctx, "app_public_opinion_event_items_add", func(tx *gorm.DB) error {
		event, status := lockOpinionEvent(tx, input.EventID, orgID)
		if status != nil {
			return abortOpinionEventTx(status)
		}
		if event.Status == model.OpinionEventStatusArchived {
			return abortOpinionEventTx(toErrStatus("app_public_opinion_event_archived", util.Int2Str(event.ID)))
		}
		if status := lockOpinionItemsForEvent(tx, orgID, itemIDs); status != nil {
			return abortOpinionEventTx(status)
		}
		if status := ensureOpinionItemsUnassigned(tx, orgID, itemIDs); status != nil {
			return abortOpinionEventTx(status)
		}
		relations := make([]*model.OpinionEventItem, 0, len(itemIDs))
		for _, itemID := range itemIDs {
			relations = append(relations, &model.OpinionEventItem{
				OrgID: orgID, EventID: event.ID, ItemID: itemID,
				RelationType: model.OpinionEventRelationTypeManual, CreatorID: creatorID,
			})
		}
		if err := tx.Create(&relations).Error; err != nil {
			return err
		}
		result, status = getOpinionEventDetail(tx, event.ID, orgID)
		if status != nil {
			return abortOpinionEventTx(status)
		}
		return nil
	})
	if status != nil {
		return nil, status
	}
	return result, nil
}

func (c *Client) RemoveOpinionEventItems(ctx context.Context, input ChangeOpinionEventItemsInput) (*OpinionEventDetail, *errs.Status) {
	orgID := strings.TrimSpace(input.OrgID)
	if input.EventID == 0 || orgID == "" {
		return nil, toErrStatus("app_public_opinion_invalid_argument", "eventId 和 orgId 不能为空")
	}
	itemIDs, status := normalizeOpinionEventItemIDs(input.ItemIDs)
	if status != nil {
		return nil, status
	}
	var result *OpinionEventDetail
	status = c.runOpinionEventTx(ctx, "app_public_opinion_event_items_remove", func(tx *gorm.DB) error {
		event, status := lockOpinionEvent(tx, input.EventID, orgID)
		if status != nil {
			return abortOpinionEventTx(status)
		}
		if event.Status == model.OpinionEventStatusArchived {
			return abortOpinionEventTx(toErrStatus("app_public_opinion_event_archived", util.Int2Str(event.ID)))
		}
		var relationCount int64
		if err := tx.Model(&model.OpinionEventItem{}).
			Where("org_id = ? AND event_id = ? AND item_id IN ?", orgID, event.ID, itemIDs).
			Count(&relationCount).Error; err != nil {
			return err
		}
		if relationCount != int64(len(itemIDs)) {
			return abortOpinionEventTx(toErrStatus("app_public_opinion_event_item_not_related"))
		}
		resultDelete := tx.Where("org_id = ? AND event_id = ? AND item_id IN ?", orgID, event.ID, itemIDs).
			Delete(&model.OpinionEventItem{})
		if resultDelete.Error != nil {
			return resultDelete.Error
		}
		if resultDelete.RowsAffected != int64(len(itemIDs)) {
			return abortOpinionEventTx(toErrStatus("app_public_opinion_event_item_not_related"))
		}
		result, status = getOpinionEventDetail(tx, event.ID, orgID)
		if status != nil {
			return abortOpinionEventTx(status)
		}
		return nil
	})
	if status != nil {
		return nil, status
	}
	return result, nil
}

func (c *Client) UpdateOpinionEventStatus(ctx context.Context, eventID uint32, orgID, targetStatus string) (*OpinionEventWithCount, *errs.Status) {
	orgID = strings.TrimSpace(orgID)
	targetStatus = strings.TrimSpace(targetStatus)
	if eventID == 0 || orgID == "" || !isOpinionEventStatus(targetStatus) {
		return nil, toErrStatus("app_public_opinion_invalid_argument", "eventId、orgId 或 status 不合法")
	}
	status := c.runOpinionEventTx(ctx, "app_public_opinion_event_status_update", func(tx *gorm.DB) error {
		event, status := lockOpinionEvent(tx, eventID, orgID)
		if status != nil {
			return abortOpinionEventTx(status)
		}
		if !canTransitionOpinionEventStatus(event.Status, targetStatus) {
			return abortOpinionEventTx(toErrStatus("app_public_opinion_event_invalid_status_transition", event.Status, targetStatus))
		}
		event.Status = targetStatus
		if err := tx.Model(event).Select("status", "updated_at").Updates(event).Error; err != nil {
			return err
		}
		return nil
	})
	if status != nil {
		return nil, status
	}
	detail, status := c.GetOpinionEvent(ctx, eventID, orgID)
	if status != nil {
		return nil, status
	}
	return detail.Event, nil
}

func validateOpinionEventFields(title, topic, summary string) (string, string, string, *errs.Status) {
	title, topic, summary = strings.TrimSpace(title), strings.TrimSpace(topic), strings.TrimSpace(summary)
	if utf8.RuneCountInString(title) == 0 {
		return "", "", "", toErrStatus("app_public_opinion_invalid_argument", "事件标题不能为空")
	}
	if utf8.RuneCountInString(title) > 500 {
		return "", "", "", toErrStatus("app_public_opinion_invalid_argument", "事件标题最多 500 个 Unicode 字符")
	}
	if utf8.RuneCountInString(topic) > 100 {
		return "", "", "", toErrStatus("app_public_opinion_invalid_argument", "事件主题最多 100 个 Unicode 字符")
	}
	if utf8.RuneCountInString(summary) > 2000 {
		return "", "", "", toErrStatus("app_public_opinion_invalid_argument", "事件摘要最多 2000 个 Unicode 字符")
	}
	return title, topic, summary, nil
}

func normalizeOpinionEventItemIDs(itemIDs []uint32) ([]uint32, *errs.Status) {
	if len(itemIDs) == 0 {
		return nil, toErrStatus("app_public_opinion_invalid_argument", "itemIds 不能为空")
	}
	result := append([]uint32(nil), itemIDs...)
	sort.Slice(result, func(i, j int) bool { return result[i] < result[j] })
	for i, itemID := range result {
		if itemID == 0 {
			return nil, toErrStatus("app_public_opinion_invalid_argument", "itemId 不能为 0")
		}
		if i > 0 && result[i-1] == itemID {
			return nil, toErrStatus("app_public_opinion_invalid_argument", fmt.Sprintf("itemIds 包含重复值 %d", itemID))
		}
	}
	return result, nil
}

func lockOpinionItemsForEvent(tx *gorm.DB, orgID string, itemIDs []uint32) *errs.Status {
	var items []model.OpinionItem
	err := tx.Clauses(clause.Locking{Strength: "UPDATE"}).
		Select("id", "org_id").
		Where("org_id = ? AND id IN ?", orgID, itemIDs).
		Order("id ASC").Find(&items).Error
	if err != nil {
		return toErrStatus("app_public_opinion_event_items_get", err.Error())
	}
	if len(items) != len(itemIDs) {
		return toErrStatus("app_public_opinion_event_item_not_available")
	}
	return nil
}

func ensureOpinionItemsUnassigned(tx *gorm.DB, orgID string, itemIDs []uint32) *errs.Status {
	var count int64
	if err := tx.Model(&model.OpinionEventItem{}).
		Where("org_id = ? AND item_id IN ?", orgID, itemIDs).
		Count(&count).Error; err != nil {
		return toErrStatus("app_public_opinion_event_items_get", err.Error())
	}
	if count > 0 {
		return toErrStatus("app_public_opinion_event_item_assigned")
	}
	return nil
}

func lockOpinionEvent(tx *gorm.DB, eventID uint32, orgID string) (*model.OpinionEvent, *errs.Status) {
	var event model.OpinionEvent
	err := tx.Clauses(clause.Locking{Strength: "UPDATE"}).
		Where("id = ? AND org_id = ?", eventID, orgID).First(&event).Error
	if errors.Is(err, gorm.ErrRecordNotFound) {
		return nil, toErrStatus("app_public_opinion_event_not_found", util.Int2Str(eventID))
	}
	if err != nil {
		return nil, toErrStatus("app_public_opinion_event_get", util.Int2Str(eventID), err.Error())
	}
	return &event, nil
}

func getOpinionEventDetail(db *gorm.DB, eventID uint32, orgID string) (*OpinionEventDetail, *errs.Status) {
	var event model.OpinionEvent
	err := db.Where("id = ? AND org_id = ?", eventID, orgID).First(&event).Error
	if errors.Is(err, gorm.ErrRecordNotFound) {
		return nil, toErrStatus("app_public_opinion_event_not_found", util.Int2Str(eventID))
	}
	if err != nil {
		return nil, toErrStatus("app_public_opinion_event_get", util.Int2Str(eventID), err.Error())
	}
	var relations []model.OpinionEventItem
	if err := db.Where("event_id = ? AND org_id = ?", eventID, orgID).Order("item_id ASC").Find(&relations).Error; err != nil {
		return nil, toErrStatus("app_public_opinion_event_get", util.Int2Str(eventID), err.Error())
	}
	items := make([]*OpinionItemWithEvent, 0, len(relations))
	if len(relations) > 0 {
		itemIDs := make([]uint32, 0, len(relations))
		for _, relation := range relations {
			itemIDs = append(itemIDs, relation.ItemID)
		}
		var models []*model.OpinionItem
		if err := db.Where("org_id = ? AND id IN ?", orgID, itemIDs).
			Order("published_at DESC, id DESC").Find(&models).Error; err != nil {
			return nil, toErrStatus("app_public_opinion_event_get", util.Int2Str(eventID), err.Error())
		}
		if len(models) != len(relations) {
			return nil, toErrStatus("app_public_opinion_event_get", util.Int2Str(eventID), "事件关联数据不完整")
		}
		for _, item := range models {
			items = append(items, &OpinionItemWithEvent{
				Item: item, EventID: event.ID, EventTitle: event.Title, EventStatus: event.Status,
			})
		}
	}
	return &OpinionEventDetail{
		Event: &OpinionEventWithCount{OpinionEvent: event, ItemCount: int32(len(relations))},
		Items: items,
	}, nil
}

func normalizeOpinionEventPage(pageNo, pageSize int32) (int32, int32) {
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

func isOpinionEventStatus(status string) bool {
	switch status {
	case model.OpinionEventStatusDraft, model.OpinionEventStatusAnalyzing,
		model.OpinionEventStatusCompleted, model.OpinionEventStatusArchived:
		return true
	default:
		return false
	}
}

func canTransitionOpinionEventStatus(current, target string) bool {
	switch current {
	case model.OpinionEventStatusDraft:
		return target == model.OpinionEventStatusAnalyzing || target == model.OpinionEventStatusArchived
	case model.OpinionEventStatusAnalyzing:
		return target == model.OpinionEventStatusCompleted || target == model.OpinionEventStatusArchived
	case model.OpinionEventStatusCompleted:
		return target == model.OpinionEventStatusArchived
	default:
		return false
	}
}
