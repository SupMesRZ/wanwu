package service

import (
	"testing"

	assistant_service "github.com/UnicomAI/wanwu/api/proto/assistant-service"
	"github.com/UnicomAI/wanwu/api/proto/common"
)

func TestCampusRoleAssistantScope(t *testing.T) {
	tools := make([]*assistant_service.AssistantMCPInfos, 0, len(campusTeacherAssistant.tools))
	for name := range campusTeacherAssistant.tools {
		tools = append(tools, &assistant_service.AssistantMCPInfos{McpId: campusTeacherAssistant.mcpID, McpType: "mcpserver", ActionName: name, Enable: true})
	}
	info := &assistant_service.AssistantInfo{
		Category:       1,
		Instructions:   CampusTeacherAssistantPrompt,
		ModelConfig:    &common.AppModelConfig{ModelId: "model-a"},
		MemoryConfig:   &assistant_service.AssistantMemoryConfig{MaxHistoryLength: 10},
		McpInfos:       tools,
		AssistantBrief: &common.AppBriefConfig{Name: "河小智教师助手"},
	}
	if err := validateCampusRoleAssistant(info, campusTeacherAssistant); err != nil {
		t.Fatalf("valid teacher assistant rejected: %v", err)
	}
	info.ToolInfos = []*assistant_service.AssistantToolInfos{{ToolId: "doc_parser"}}
	info.McpInfos = append(info.McpInfos, &assistant_service.AssistantMCPInfos{McpId: "future_mcp", McpType: "mcpserver", ActionName: "future_tool", Enable: true})
	if err := validateCampusRoleAssistant(info, campusTeacherAssistant); err != nil {
		t.Fatalf("extended teacher assistant rejected: %v", err)
	}
	info.McpInfos[0].McpId = "campus_student"
	if err := validateCampusRoleAssistant(info, campusTeacherAssistant); err == nil {
		t.Fatal("cross-role MCP binding must be rejected")
	}
}
