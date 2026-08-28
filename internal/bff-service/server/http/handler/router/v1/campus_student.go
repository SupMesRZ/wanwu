package v1

import (
	"net/http"

	v1 "github.com/UnicomAI/wanwu/internal/bff-service/server/http/handler/v1"
	"github.com/UnicomAI/wanwu/internal/bff-service/server/http/middleware"
	mid "github.com/UnicomAI/wanwu/pkg/gin-util/mid-wrap"
	"github.com/gin-gonic/gin"
)

func registerCampusStudent(apiV1 *gin.RouterGroup) {
	studentOnly := middleware.CheckCampusStudentRole
	bindExecution := middleware.BindCampusStudentExecutionContext
	mid.Sub("wga.wanwu_bot").Reg(apiV1, "/campus/student/summary", http.MethodGet, v1.GetCampusStudentSummary, "学生首页摘要", studentOnly)
	mid.Sub("wga.wanwu_bot").Reg(apiV1, "/campus/student/courses", http.MethodGet, v1.GetCampusStudentCourses, "我的课程", studentOnly)
	mid.Sub("wga.wanwu_bot").Reg(apiV1, "/campus/student/courses/today", http.MethodGet, v1.GetCampusStudentTodayCourses, "今日课程", studentOnly)
	mid.Sub("wga.wanwu_bot").Reg(apiV1, "/campus/student/courses/week", http.MethodGet, v1.GetCampusStudentWeekCourses, "本周课程", studentOnly)
	mid.Sub("wga.wanwu_bot").Reg(apiV1, "/campus/student/exams", http.MethodGet, v1.GetCampusStudentExams, "考试安排", studentOnly)
	mid.Sub("wga.wanwu_bot").Reg(apiV1, "/campus/student/scores", http.MethodGet, v1.GetCampusStudentScores, "成绩列表", studentOnly)
	mid.Sub("wga.wanwu_bot").Reg(apiV1, "/campus/student/scores/overview", http.MethodGet, v1.GetCampusStudentTermOverview, "学期成绩概览", studentOnly)
	mid.Sub("wga.wanwu_bot").Reg(apiV1, "/campus/student/learning-analysis", http.MethodGet, v1.GetCampusStudentLearningAnalysis, "学习分析基础数据", studentOnly)
	mid.Sub("wga.wanwu_bot").Reg(apiV1, "/campus/student/leave-records", http.MethodGet, v1.GetCampusStudentLeaveRecords, "请假记录", studentOnly)
	mid.Sub("wga.wanwu_bot").Reg(apiV1, "/campus/student/assistant", http.MethodGet, v1.GetCampusStudentAssistant, "学生助手配置", studentOnly)
	mid.Sub("wga.wanwu_bot").Reg(apiV1, "/campus/student/assistant/conversation", http.MethodPost, v1.CreateCampusStudentAssistantConversation, "创建学生助手会话", studentOnly)
	mid.Sub("wga.wanwu_bot").Reg(apiV1, "/campus/student/assistant/chat", http.MethodPost, v1.CampusStudentAssistantChat, "学生助手流式问答", studentOnly)
	mid.Sub("wga.wanwu_bot").Reg(apiV1, "/campus/student/mcp", http.MethodPost, v1.HandleCampusStudentMCP, "Campus Student MCP", studentOnly, bindExecution)
	mid.Sub("wga.wanwu_bot").Reg(apiV1, "/campus/student/mcp", http.MethodGet, v1.HandleCampusStudentMCP, "Campus Student MCP", studentOnly, bindExecution)
	mid.Sub("wga.wanwu_bot").Reg(apiV1, "/campus/student/mcp", http.MethodDelete, v1.HandleCampusStudentMCP, "Campus Student MCP", studentOnly, bindExecution)
	mid.Sub("wga.wanwu_bot").Reg(apiV1, "/campus/student/mcp/identity", http.MethodGet, v1.GetCampusStudentMCPIdentity, "Campus Student MCP 身份验证", studentOnly, bindExecution)
}
