package request

type CampusStudentAssistantConversationReq struct {
	Message string `json:"message" validate:"required"`
}

func (r *CampusStudentAssistantConversationReq) Check() error { return nil }

type CampusStudentAssistantChatReq struct {
	ConversationID string `json:"conversationId" validate:"required"`
	Message        string `json:"message" validate:"required"`
}

func (r *CampusStudentAssistantChatReq) Check() error { return nil }
