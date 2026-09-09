package v1

import (
	"errors"
	"net/http"
	"strings"

	"github.com/UnicomAI/wanwu/internal/bff-service/model/request"
	"github.com/UnicomAI/wanwu/internal/bff-service/service"
	gin_util "github.com/UnicomAI/wanwu/pkg/gin-util"
	"github.com/gin-gonic/gin"
)

func GetCampusTeacherAssistantBinding(ctx *gin.Context) {
	resp, err := service.GetCampusTeacherAssistantBinding(ctx.Request.Context())
	gin_util.Response(ctx, resp, err)
}

func GetCampusAcademicAssistantBinding(ctx *gin.Context) {
	resp, err := service.GetCampusAcademicAssistantBinding(ctx.Request.Context())
	gin_util.Response(ctx, resp, err)
}

func GetCampusTeacherAssistant(ctx *gin.Context) {
	resp, err := service.GetCampusRoleAssistant(ctx, service.CampusTeacherAssistantSpec())
	gin_util.Response(ctx, resp, err)
}

func GetCampusAcademicAssistant(ctx *gin.Context) {
	resp, err := service.GetCampusRoleAssistant(ctx, service.CampusAcademicAssistantSpec())
	gin_util.Response(ctx, resp, err)
}

func CreateCampusTeacherAssistantConversation(ctx *gin.Context) {
	createCampusRoleAssistantConversation(ctx, service.CampusTeacherAssistantSpec())
}

func CreateCampusAcademicAssistantConversation(ctx *gin.Context) {
	createCampusRoleAssistantConversation(ctx, service.CampusAcademicAssistantSpec())
}

func createCampusRoleAssistantConversation(ctx *gin.Context, spec service.CampusRoleAssistantSpec) {
	var req request.CampusStudentAssistantConversationReq
	if !gin_util.Bind(ctx, &req) {
		return
	}
	resp, err := service.CreateCampusRoleAssistantConversation(ctx, getUserID(ctx), getOrgID(ctx), req, spec)
	gin_util.Response(ctx, resp, err)
}

func CampusTeacherAssistantChat(ctx *gin.Context) {
	campusRoleAssistantChat(ctx, service.CampusTeacherAssistantSpec())
}

func CampusAcademicAssistantChat(ctx *gin.Context) {
	campusRoleAssistantChat(ctx, service.CampusAcademicAssistantSpec())
}

func campusRoleAssistantChat(ctx *gin.Context, spec service.CampusRoleAssistantSpec) {
	var req request.CampusStudentAssistantChatReq
	if !gin_util.Bind(ctx, &req) {
		return
	}
	if err := service.CampusRoleAssistantChat(ctx, getUserID(ctx), getOrgID(ctx), getClientID(ctx), req, spec); err != nil {
		gin_util.Response(ctx, nil, err)
	}
}

func HandleCampusTeacherMCP(ctx *gin.Context) {
	if err := service.ServeCampusTeacherMCP(ctx.Writer, ctx.Request); err != nil {
		gin_util.ResponseErrWithStatus(ctx, http.StatusInternalServerError, err)
	}
}

func HandleCampusAcademicMCP(ctx *gin.Context) {
	if err := service.ServeCampusAcademicMCP(ctx.Writer, ctx.Request); err != nil {
		gin_util.ResponseErrWithStatus(ctx, http.StatusInternalServerError, err)
	}
}

func GetCampusBusinessMCPIdentity(ctx *gin.Context) {
	identity, ok := service.CampusBusinessExecutionIdentityFromContext(ctx.Request.Context())
	if !ok {
		gin_util.ResponseErrWithStatus(ctx, http.StatusForbidden, errors.New("campus business execution identity unavailable"))
		return
	}
	gin_util.Response(ctx, identity, nil)
}

func RunCampusWorkflow(ctx *gin.Context) {
	var req request.CampusWorkflowRunReq
	if !gin_util.Bind(ctx, &req) {
		return
	}
	identity, ok := service.CampusWorkflowExecutionIdentityFromContext(ctx.Request.Context())
	if !ok {
		gin_util.ResponseErrWithStatus(ctx, http.StatusForbidden, errors.New("campus_workflow_execution_identity_missing"))
		return
	}
	resp, err := service.RunCampusWorkflow(ctx, req.WorkflowCode, identity, req.ConversationID, req.Input)
	if err != nil {
		writeCampusWorkflowError(ctx, err)
		return
	}
	gin_util.Response(ctx, resp, nil)
}

func ResumeCampusWorkflow(ctx *gin.Context) {
	var req request.CampusWorkflowResumeReq
	if !gin_util.Bind(ctx, &req) {
		return
	}
	identity, ok := service.CampusWorkflowExecutionIdentityFromContext(ctx.Request.Context())
	if !ok {
		gin_util.ResponseErrWithStatus(ctx, http.StatusForbidden, errors.New("campus_workflow_execution_identity_missing"))
		return
	}
	if err := service.ResumeCampusWorkflow(ctx, req.WorkflowCode, req.WorkflowRunID, req.EventID, req.Data, identity); err != nil {
		writeCampusWorkflowError(ctx, err)
		return
	}
	gin_util.Response(ctx, map[string]any{"workflowRunId": req.WorkflowRunID}, nil)
}

func writeCampusWorkflowError(ctx *gin.Context, err error) {
	status := http.StatusBadRequest
	if strings.Contains(err.Error(), "forbidden") || strings.Contains(err.Error(), "identity") || strings.Contains(err.Error(), "role") {
		status = http.StatusForbidden
	}
	gin_util.ResponseErrWithStatus(ctx, status, errors.New(err.Error()))
}
