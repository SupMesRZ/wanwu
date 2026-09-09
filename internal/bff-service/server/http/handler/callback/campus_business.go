package callback

import (
	"crypto/subtle"
	"errors"
	"net/http"
	"os"
	"strings"

	"github.com/UnicomAI/wanwu/internal/bff-service/service"
	gin_util "github.com/UnicomAI/wanwu/pkg/gin-util"
	"github.com/gin-gonic/gin"
)

func CampusTeacherWorkflowMCP(ctx *gin.Context) {
	serveCampusWorkflowMCP(ctx, service.CampusRoleTeacher, service.ServeCampusTeacherMCP)
}

func CampusAcademicWorkflowMCP(ctx *gin.Context) {
	serveCampusWorkflowMCP(ctx, service.CampusRoleAcademicAdmin, service.ServeCampusAcademicMCP)
}

func CampusStudentWorkflowMCP(ctx *gin.Context) {
	grant, ok := campusWorkflowGrant(ctx)
	if !ok {
		gin_util.ResponseErrWithStatus(ctx, http.StatusForbidden, errors.New("campus workflow execution identity unavailable"))
		return
	}
	definition, registered := service.CampusWorkflowDefinition(grant.WorkflowCode)
	if !registered || definition.AllowedRole != service.CampusRoleStudent || grant.Identity.ActualRole != service.CampusRoleStudent {
		gin_util.ResponseErrWithStatus(ctx, http.StatusForbidden, errors.New("campus student workflow role required"))
		return
	}
	studentCtx := service.WithCampusStudentExecutionIdentity(ctx.Request.Context(), grant.Identity.UserID, grant.Identity.OrgID, executeIDHeader(ctx))
	studentCtx = service.WithCampusStudentWorkflowExecution(studentCtx, executeIDHeader(ctx))
	studentCtx = service.WithCampusStudentWorkflowCode(studentCtx, grant.WorkflowCode)
	ctx.Request = ctx.Request.WithContext(studentCtx)
	if err := service.ServeCampusStudentMCP(ctx.Writer, ctx.Request); err != nil {
		gin_util.ResponseErrWithStatus(ctx, http.StatusInternalServerError, errors.New("campus_workflow_mcp_failed"))
	}
}

func executeIDHeader(ctx *gin.Context) string {
	return strings.TrimSpace(ctx.GetHeader("X-Campus-Workflow-Execute-Id"))
}

func serveCampusWorkflowMCP(ctx *gin.Context, requiredRole string, serve func(http.ResponseWriter, *http.Request) error) {
	grant, ok := campusWorkflowGrant(ctx)
	if !ok {
		gin_util.ResponseErrWithStatus(ctx, http.StatusForbidden, errors.New("campus workflow execution identity unavailable"))
		return
	}
	definition, registered := service.CampusWorkflowDefinition(grant.WorkflowCode)
	if !registered || definition.AllowedRole != requiredRole || grant.Identity.ActualRole != requiredRole {
		gin_util.ResponseErrWithStatus(ctx, http.StatusForbidden, errors.New("campus workflow role required"))
		return
	}
	ctx.Request = ctx.Request.WithContext(service.WithCampusBusinessExecutionIdentity(ctx.Request.Context(), grant.Identity.UserID, grant.Identity.OrgID, grant.Identity.ActualRole))
	if err := serve(ctx.Writer, ctx.Request); err != nil {
		gin_util.ResponseErrWithStatus(ctx, http.StatusInternalServerError, errors.New("campus_workflow_mcp_failed"))
	}
}

func campusWorkflowGrant(ctx *gin.Context) (service.CampusWorkflowRunIdentity, bool) {
	secret := os.Getenv("WANWU_CAMPUS_WORKFLOW_INTERNAL_SECRET")
	provided := ctx.GetHeader("X-Campus-Workflow-Internal")
	if secret == "" || subtle.ConstantTimeCompare([]byte(secret), []byte(provided)) != 1 {
		return service.CampusWorkflowRunIdentity{}, false
	}
	executeID := strings.TrimSpace(ctx.GetHeader("X-Campus-Workflow-Execute-Id"))
	if executeID == "" {
		return service.CampusWorkflowRunIdentity{}, false
	}
	grant, err := service.CampusWorkflowExecutionGrant(executeID)
	return grant, err == nil
}
