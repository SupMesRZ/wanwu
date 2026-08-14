package response

type PublicOpinionImportTask struct {
	TaskID        string `json:"taskId"`
	CreatedAt     string `json:"createdAt"`
	UpdatedAt     string `json:"updatedAt"`
	FileName      string `json:"fileName"`
	FileType      string `json:"fileType"`
	FileSize      int64  `json:"fileSize"`
	FileHash      string `json:"fileHash"`
	Status        string `json:"status"`
	TotalRows     int32  `json:"totalRows"`
	SuccessRows   int32  `json:"successRows"`
	DuplicateRows int32  `json:"duplicateRows"`
	FailedRows    int32  `json:"failedRows"`
	ErrorDetail   string `json:"errorDetail"`
	StartedAt     string `json:"startedAt"`
	FinishedAt    string `json:"finishedAt"`
}

type PublicOpinionItem struct {
	ItemID       string `json:"itemId"`
	CreatedAt    string `json:"createdAt"`
	UpdatedAt    string `json:"updatedAt"`
	CreatorID    string `json:"creatorId"`
	ImportTaskID string `json:"importTaskId"`
	Title        string `json:"title"`
	Content      string `json:"content"`
	Summary      string `json:"summary"`
	SourceName   string `json:"sourceName"`
	SourceType   string `json:"sourceType"`
	PublicURL    string `json:"publicUrl"`
	PublishedAt  string `json:"publishedAt"`
	CollectedAt  string `json:"collectedAt"`
	Topic        string `json:"topic"`
	Remark       string `json:"remark"`
}
