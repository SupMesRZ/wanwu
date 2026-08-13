package model

const (
	OpinionImportTaskStatusProcessing     = "processing"
	OpinionImportTaskStatusSuccess        = "success"
	OpinionImportTaskStatusPartialSuccess = "partial_success"
	OpinionImportTaskStatusFailed         = "failed"
)

type OpinionImportTask struct {
	ID            uint32 `gorm:"primary_key;autoIncrement"`
	CreatedAt     int64  `gorm:"autoCreateTime:milli"`
	UpdatedAt     int64  `gorm:"autoUpdateTime:milli"`
	OrgID         string `gorm:"size:64;index"`
	CreatorID     string `gorm:"size:64;index"`
	FileName      string `gorm:"size:512"`
	FileType      string `gorm:"size:16"`
	FileSize      int64
	FileHash      string `gorm:"size:64;index"`
	Status        string `gorm:"size:32;index"`
	TotalRows     int32
	SuccessRows   int32
	DuplicateRows int32
	FailedRows    int32
	ErrorDetail   string
	StartedAt     int64
	FinishedAt    int64
}

type OpinionItem struct {
	ID           uint32 `gorm:"primary_key;autoIncrement"`
	CreatedAt    int64  `gorm:"autoCreateTime:milli"`
	UpdatedAt    int64  `gorm:"autoUpdateTime:milli"`
	OrgID        string `gorm:"size:64;index;uniqueIndex:idx_opinion_items_org_content_hash,priority:1"`
	CreatorID    string `gorm:"size:64;index"`
	ImportTaskID uint32 `gorm:"index"`
	Title        string `gorm:"size:500"`
	Content      string
	Summary      string `gorm:"size:2000"`
	SourceName   string `gorm:"size:128"`
	SourceType   string `gorm:"size:64;index"`
	PublicURL    string `gorm:"size:2048"`
	PublishedAt  int64  `gorm:"index"`
	CollectedAt  int64  `gorm:"index"`
	Topic        string `gorm:"size:100;index"`
	Remark       string `gorm:"size:1000"`
	ContentHash  string `gorm:"size:64;uniqueIndex:idx_opinion_items_org_content_hash,priority:2"`
	URLHash      string `gorm:"size:64;index"`
}
