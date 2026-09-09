package request

func (r *CampusWorkflowRunReq) Check() error    { return nil }
func (r *CampusWorkflowResumeReq) Check() error { return nil }

type CampusWorkflowRunReq struct {
	WorkflowCode   string         `json:"workflowCode" validate:"required"`
	ConversationID string         `json:"conversationId"`
	Input          map[string]any `json:"input"`
}

type CampusWorkflowResumeReq struct {
	WorkflowCode  string `json:"workflowCode" validate:"required"`
	WorkflowRunID string `json:"workflowRunId" validate:"required"`
	EventID       string `json:"eventId" validate:"required"`
	Data          string `json:"data"`
}
