package model

const (
	OpinionImportTaskStatusProcessing     = "processing"
	OpinionImportTaskStatusSuccess        = "success"
	OpinionImportTaskStatusPartialSuccess = "partial_success"
	OpinionImportTaskStatusFailed         = "failed"

	OpinionEventStatusDraft     = "draft"
	OpinionEventStatusAnalyzing = "analyzing"
	OpinionEventStatusCompleted = "completed"
	OpinionEventStatusArchived  = "archived"

	OpinionEventRelationTypeManual = "manual"
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

type OpinionEvent struct {
	ID        uint32 `gorm:"primary_key;autoIncrement"`
	CreatedAt int64  `gorm:"autoCreateTime:milli"`
	UpdatedAt int64  `gorm:"autoUpdateTime:milli;index:idx_opinion_events_org_updated_at,priority:2"`
	OrgID     string `gorm:"size:64;not null;index;index:idx_opinion_events_org_updated_at,priority:1;index:idx_opinion_events_org_status,priority:1;index:idx_opinion_events_org_topic,priority:1"`
	CreatorID string `gorm:"size:64;not null;index"`
	Title     string `gorm:"size:500;not null"`
	Topic     string `gorm:"size:100;index:idx_opinion_events_org_topic,priority:2"`
	Summary   string `gorm:"size:2000"`
	Status    string `gorm:"size:32;not null;default:draft;index:idx_opinion_events_org_status,priority:2"`
}

type OpinionEventItem struct {
	ID           uint32 `gorm:"primary_key;autoIncrement"`
	CreatedAt    int64  `gorm:"autoCreateTime:milli"`
	OrgID        string `gorm:"size:64;not null;index:idx_opinion_event_items_org_event,priority:1"`
	EventID      uint32 `gorm:"not null;index:idx_opinion_event_items_org_event,priority:2"`
	ItemID       uint32 `gorm:"not null;uniqueIndex:idx_opinion_event_items_item_id"`
	RelationType string `gorm:"size:32;not null;default:manual"`
	CreatorID    string `gorm:"size:64;not null;index"`
}
