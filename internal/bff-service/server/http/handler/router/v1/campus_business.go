package v1

import (
	"net/http"

	v1 "github.com/UnicomAI/wanwu/internal/bff-service/server/http/handler/v1"
	"github.com/UnicomAI/wanwu/internal/bff-service/server/http/middleware"
	mid "github.com/UnicomAI/wanwu/pkg/gin-util/mid-wrap"
	"github.com/gin-gonic/gin"
)

func registerCampusBusiness(apiV1 *gin.RouterGroup) {
	bind := middleware.BindCampusBusinessExecutionContext
	teacherOnly := middleware.CheckCampusTeacherRole
	academicOnly := middleware.CheckCampusAcademicAdminRole
	mid.Sub("app.agent").Reg(apiV1, "/campus/teacher/assistant/binding", http.MethodGet, v1.GetCampusTeacherAssistantBinding, "教师助手对象绑定")
	mid.Sub("app.agent").Reg(apiV1, "/campus/academic/assistant/binding", http.MethodGet, v1.GetCampusAcademicAssistantBinding, "教务助手对象绑定")
	workflowIdentity := middleware.BindCampusWorkflowExecutionContext
	mid.Sub("app.workflow").Reg(apiV1, "/campus/workflow/run", http.MethodPost, v1.RunCampusWorkflow, "Campus Workflow 运行", workflowIdentity)
	mid.Sub("app.workflow").Reg(apiV1, "/campus/workflow/resume", http.MethodPost, v1.ResumeCampusWorkflow, "Campus Workflow 恢复", workflowIdentity)
	mid.Sub("wga.wanwu_bot").Reg(apiV1, "/campus/teacher/assistant", http.MethodGet, v1.GetCampusTeacherAssistant, "教师助手配置", teacherOnly)
	mid.Sub("wga.wanwu_bot").Reg(apiV1, "/campus/teacher/assistant/conversation", http.MethodPost, v1.CreateCampusTeacherAssistantConversation, "创建教师助手会话", teacherOnly)
	mid.Sub("wga.wanwu_bot").Reg(apiV1, "/campus/teacher/assistant/chat", http.MethodPost, v1.CampusTeacherAssistantChat, "教师助手流式问答", teacherOnly)
	mid.Sub("wga.wanwu_bot").Reg(apiV1, "/campus/academic/assistant", http.MethodGet, v1.GetCampusAcademicAssistant, "教务助手配置", academicOnly)
	mid.Sub("wga.wanwu_bot").Reg(apiV1, "/campus/academic/assistant/conversation", http.MethodPost, v1.CreateCampusAcademicAssistantConversation, "创建教务助手会话", academicOnly)
	mid.Sub("wga.wanwu_bot").Reg(apiV1, "/campus/academic/assistant/chat", http.MethodPost, v1.CampusAcademicAssistantChat, "教务助手流式问答", academicOnly)
	for _, method := range []string{http.MethodPost, http.MethodGet, http.MethodDelete} {
		mid.Sub("wga.wanwu_bot").Reg(apiV1, "/campus/teacher/mcp", method, v1.HandleCampusTeacherMCP, "Campus Teacher MCP", teacherOnly, bind)
		mid.Sub("wga.wanwu_bot").Reg(apiV1, "/campus/academic/mcp", method, v1.HandleCampusAcademicMCP, "Campus Academic Admin MCP", academicOnly, bind)
	}
	mid.Sub("wga.wanwu_bot").Reg(apiV1, "/campus/teacher/mcp/identity", http.MethodGet, v1.GetCampusBusinessMCPIdentity, "Campus Teacher MCP 身份验证", teacherOnly, bind)
	mid.Sub("wga.wanwu_bot").Reg(apiV1, "/campus/academic/mcp/identity", http.MethodGet, v1.GetCampusBusinessMCPIdentity, "Campus Academic Admin MCP 身份验证", academicOnly, bind)
}
