package orm

import (
	"context"
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"os"
	"strings"
	"time"

	errs "github.com/UnicomAI/wanwu/api/proto/err-code"
	"github.com/UnicomAI/wanwu/internal/app-service/client/model"
	app_pkg "github.com/UnicomAI/wanwu/internal/app-service/pkg"
	"github.com/UnicomAI/wanwu/pkg/util"
	"gorm.io/gorm"
	"gorm.io/gorm/clause"
)

const maxPublicOpinionErrorDetails = 200

type PublicOpinionImportInput struct {
	OrgID     string
	CreatorID string
	FileName  string
	FileType  string
	FileSize  int64
	FilePath  string
}

type PublicOpinionListFilter struct {
	OrgID      string
	Keyword    string
	StartTime  int64
	EndTime    int64
	SourceType string
	Topic      string
	PageNo     int32
	PageSize   int32
}

func (c *Client) CreateOpinionImportTask(ctx context.Context, task *model.OpinionImportTask) *errs.Status {
	if task.OrgID == "" {
		return toErrStatus("app_public_opinion_invalid_argument", "orgId 不能为空")
	}
	if err := c.db.WithContext(ctx).Create(task).Error; err != nil {
		return toErrStatus("app_public_opinion_task_create", err.Error())
	}
	return nil
}

func (c *Client) UpdateOpinionImportTask(ctx context.Context, task *model.OpinionImportTask) *errs.Status {
	if task.ID == 0 || task.OrgID == "" {
		return toErrStatus("app_public_opinion_invalid_argument", "taskId 和 orgId 不能为空")
	}
	result := c.db.WithContext(ctx).Model(&model.OpinionImportTask{}).
		Where("id = ? AND org_id = ?", task.ID, task.OrgID).
		Select("file_hash", "status", "total_rows", "success_rows", "duplicate_rows", "failed_rows", "error_detail", "started_at", "finished_at").
		Updates(task)
	if result.Error != nil {
		return toErrStatus("app_public_opinion_task_update", util.Int2Str(task.ID), result.Error.Error())
	}
	if result.RowsAffected == 0 {
		return toErrStatus("app_public_opinion_task_not_found", util.Int2Str(task.ID))
	}
	return nil
}

func (c *Client) GetOpinionImportTask(ctx context.Context, taskID uint32, orgID string) (*model.OpinionImportTask, *errs.Status) {
	var task model.OpinionImportTask
	err := c.db.WithContext(ctx).Where("id = ? AND org_id = ?", taskID, orgID).First(&task).Error
	if errors.Is(err, gorm.ErrRecordNotFound) {
		return nil, toErrStatus("app_public_opinion_task_not_found", util.Int2Str(taskID))
	}
	if err != nil {
		return nil, toErrStatus("app_public_opinion_task_get", util.Int2Str(taskID), err.Error())
	}
	return &task, nil
}

func (c *Client) ImportPublicOpinion(ctx context.Context, input PublicOpinionImportInput) (*model.OpinionImportTask, *errs.Status) {
	startedAt := time.Now().UnixMilli()
	task := &model.OpinionImportTask{
		OrgID:     strings.TrimSpace(input.OrgID),
		CreatorID: strings.TrimSpace(input.CreatorID),
		FileName:  strings.TrimSpace(input.FileName),
		FileType:  strings.TrimSpace(input.FileType),
		FileSize:  input.FileSize,
		Status:    model.OpinionImportTaskStatusProcessing,
		StartedAt: startedAt,
	}
	if status := c.CreateOpinionImportTask(ctx, task); status != nil {
		return nil, status
	}

	fileHash, err := publicOpinionFileHash(input.FilePath)
	if err != nil {
		return c.failPublicOpinionTask(ctx, task, 0, "file", err.Error())
	}
	task.FileHash = fileHash
	rows, rowErrors, err := app_pkg.ParsePublicOpinionFile(input.FilePath, input.FileName, input.FileType)
	if err != nil {
		return c.failPublicOpinionTask(ctx, task, 0, "file", err.Error())
	}

	failedRows := countPublicOpinionErrorRows(rowErrors)
	task.TotalRows = int32(len(rows)) + failedRows
	task.FailedRows = failedRows
	task.ErrorDetail = marshalPublicOpinionErrors(rowErrors)
	collectedAt := startedAt

	err = c.db.WithContext(ctx).Transaction(func(tx *gorm.DB) error {
		for _, row := range rows {
			contentHash := publicOpinionContentHash(row.Title, row.Content)
			item := &model.OpinionItem{
				OrgID:        task.OrgID,
				CreatorID:    task.CreatorID,
				ImportTaskID: task.ID,
				Title:        row.Title,
				Content:      row.Content,
				Summary:      row.Summary,
				SourceName:   row.SourceName,
				SourceType:   row.SourceType,
				PublicURL:    row.PublicURL,
				PublishedAt:  row.PublishedAt,
				CollectedAt:  collectedAt,
				Topic:        row.Topic,
				Remark:       row.Remark,
				ContentHash:  contentHash,
				URLHash:      publicOpinionURLHash(row.PublicURL),
			}
			result := tx.Clauses(clause.OnConflict{
				Columns:   []clause.Column{{Name: "org_id"}, {Name: "content_hash"}},
				DoNothing: true,
			}).Create(item)
			if result.Error != nil {
				return fmt.Errorf("create opinion item row %d: %w", row.Row, result.Error)
			}
			if result.RowsAffected == 0 {
				task.DuplicateRows++
				continue
			}
			task.SuccessRows++
		}
		task.Status = publicOpinionFinalStatus(task.SuccessRows, task.DuplicateRows, task.FailedRows)
		task.FinishedAt = time.Now().UnixMilli()
		result := tx.Model(&model.OpinionImportTask{}).
			Where("id = ? AND org_id = ?", task.ID, task.OrgID).
			Select("file_hash", "status", "total_rows", "success_rows", "duplicate_rows", "failed_rows", "error_detail", "finished_at").
			Updates(task)
		if result.Error != nil {
			return fmt.Errorf("update import task: %w", result.Error)
		}
		if result.RowsAffected == 0 {
			return fmt.Errorf("import task %d not found", task.ID)
		}
		return nil
	})
	if err != nil {
		// All item writes were rolled back. The failed task is updated independently.
		task.SuccessRows = 0
		task.DuplicateRows = 0
		task.FailedRows = task.TotalRows
		task.Status = model.OpinionImportTaskStatusFailed
		task.FinishedAt = time.Now().UnixMilli()
		task.ErrorDetail = marshalPublicOpinionErrors([]app_pkg.PublicOpinionRowError{{Row: 0, Field: "database", Reason: err.Error()}})
		if status := c.UpdateOpinionImportTask(ctx, task); status != nil {
			return nil, status
		}
		return nil, toErrStatus("app_public_opinion_import_database", err.Error())
	}
	return task, nil
}

func (c *Client) CreateOpinionItem(ctx context.Context, item *model.OpinionItem) *errs.Status {
	if item.OrgID == "" {
		return toErrStatus("app_public_opinion_invalid_argument", "orgId 不能为空")
	}
	if err := c.db.WithContext(ctx).Create(item).Error; err != nil {
		return toErrStatus("app_public_opinion_item_create", err.Error())
	}
	return nil
}

func (c *Client) ListOpinionItems(ctx context.Context, filter PublicOpinionListFilter) ([]*model.OpinionItem, int64, *errs.Status) {
	pageNo, pageSize := normalizePublicOpinionPage(filter.PageNo, filter.PageSize)
	query := c.db.WithContext(ctx).Model(&model.OpinionItem{}).Where("org_id = ?", filter.OrgID)
	if keyword := strings.TrimSpace(filter.Keyword); keyword != "" {
		like := "%" + keyword + "%"
		query = query.Where("title LIKE ? OR content LIKE ? OR summary LIKE ?", like, like, like)
	}
	if filter.StartTime > 0 {
		query = query.Where("published_at >= ?", filter.StartTime)
	}
	if filter.EndTime > 0 {
		query = query.Where("published_at <= ?", filter.EndTime)
	}
	if sourceType := strings.TrimSpace(filter.SourceType); sourceType != "" {
		query = query.Where("source_type = ?", sourceType)
	}
	if topic := strings.TrimSpace(filter.Topic); topic != "" {
		query = query.Where("topic = ?", topic)
	}
	var total int64
	if err := query.Count(&total).Error; err != nil {
		return nil, 0, toErrStatus("app_public_opinion_item_list", err.Error())
	}
	var items []*model.OpinionItem
	if err := query.Order("published_at DESC, id DESC").Offset(int((pageNo - 1) * pageSize)).Limit(int(pageSize)).Find(&items).Error; err != nil {
		return nil, 0, toErrStatus("app_public_opinion_item_list", err.Error())
	}
	return items, total, nil
}

func (c *Client) GetOpinionItem(ctx context.Context, itemID uint32, orgID string) (*model.OpinionItem, *errs.Status) {
	var item model.OpinionItem
	err := c.db.WithContext(ctx).Where("id = ? AND org_id = ?", itemID, orgID).First(&item).Error
	if errors.Is(err, gorm.ErrRecordNotFound) {
		return nil, toErrStatus("app_public_opinion_item_not_found", util.Int2Str(itemID))
	}
	if err != nil {
		return nil, toErrStatus("app_public_opinion_item_get", util.Int2Str(itemID), err.Error())
	}
	return &item, nil
}

func (c *Client) failPublicOpinionTask(ctx context.Context, task *model.OpinionImportTask, row int, field, reason string) (*model.OpinionImportTask, *errs.Status) {
	task.Status = model.OpinionImportTaskStatusFailed
	task.FinishedAt = time.Now().UnixMilli()
	task.ErrorDetail = marshalPublicOpinionErrors([]app_pkg.PublicOpinionRowError{{Row: row, Field: field, Reason: reason}})
	if status := c.UpdateOpinionImportTask(ctx, task); status != nil {
		return nil, status
	}
	return nil, toErrStatus("app_public_opinion_import_file", reason)
}

func normalizePublicOpinionPage(pageNo, pageSize int32) (int32, int32) {
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

func publicOpinionFinalStatus(success, duplicate, failed int32) string {
	if failed == 0 {
		return model.OpinionImportTaskStatusSuccess
	}
	if success > 0 || duplicate > 0 {
		return model.OpinionImportTaskStatusPartialSuccess
	}
	return model.OpinionImportTaskStatusFailed
}

func publicOpinionFileHash(filePath string) (string, error) {
	f, err := os.Open(filePath)
	if err != nil {
		return "", fmt.Errorf("读取导入文件失败: %w", err)
	}
	defer func() { _ = f.Close() }()
	h := sha256.New()
	if _, err := io.Copy(h, f); err != nil {
		return "", fmt.Errorf("计算文件哈希失败: %w", err)
	}
	return hex.EncodeToString(h.Sum(nil)), nil
}

func publicOpinionContentHash(title, content string) string {
	sum := sha256.Sum256([]byte(title + "\n" + content))
	return hex.EncodeToString(sum[:])
}

func publicOpinionURLHash(value string) string {
	value = strings.TrimSpace(value)
	if value == "" {
		return ""
	}
	sum := sha256.Sum256([]byte(value))
	return hex.EncodeToString(sum[:])
}

func countPublicOpinionErrorRows(details []app_pkg.PublicOpinionRowError) int32 {
	rows := make(map[int]struct{}, len(details))
	for _, detail := range details {
		rows[detail.Row] = struct{}{}
	}
	return int32(len(rows))
}

func marshalPublicOpinionErrors(details []app_pkg.PublicOpinionRowError) string {
	if len(details) == 0 {
		return "[]"
	}
	if len(details) > maxPublicOpinionErrorDetails {
		details = details[:maxPublicOpinionErrorDetails]
	}
	data, err := json.Marshal(details)
	if err != nil {
		return "[]"
	}
	return string(data)
}
