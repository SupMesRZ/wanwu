package middleware

import (
	"errors"
	"net/http"

	"github.com/UnicomAI/wanwu/internal/bff-service/service"
	gin_util "github.com/UnicomAI/wanwu/pkg/gin-util"
	"github.com/gin-gonic/gin"
)

var getCampusBusinessPermission = service.GetUserPermission

func CheckCampusTeacherRole(ctx *gin.Context) {
	checkCampusBusinessRole(ctx, service.CampusRoleTeacher)
}

func CheckCampusAcademicAdminRole(ctx *gin.Context) {
	checkCampusBusinessRole(ctx, service.CampusRoleAcademicAdmin)
}

func checkCampusBusinessRole(ctx *gin.Context, required string) {
	userID, err := getUserID(ctx)
	if err != nil {
		denyCampusBusiness(ctx, err)
		return
	}
	orgID, err := getOrgID(ctx)
	if err != nil {
		denyCampusBusiness(ctx, err)
		return
	}
	permission, err := getCampusBusinessPermission(ctx, userID, orgID)
	if err != nil {
		denyCampusBusiness(ctx, err)
		return
	}
	if err := service.ValidateCampusBusinessRole(permission, required); err != nil {
		denyCampusBusiness(ctx, err)
		return
	}
	if role, status := service.ResolveActualCampusRole(permission.OrgPermission.Roles); status == service.CampusRoleResolved {
		ctx.Set(service.CampusBusinessActualRoleKey, role)
	}
}

func BindCampusBusinessExecutionContext(ctx *gin.Context) {
	userID, err := getUserID(ctx)
	if err != nil {
		denyCampusBusiness(ctx, errors.New("campus business identity unavailable"))
		return
	}
	orgID, err := getOrgID(ctx)
	if err != nil {
		denyCampusBusiness(ctx, errors.New("campus business identity unavailable"))
		return
	}
	ctx.Request = ctx.Request.WithContext(service.WithCampusBusinessExecutionIdentity(ctx.Request.Context(), userID, orgID, ctx.GetString(service.CampusBusinessActualRoleKey)))
}

func BindCampusWorkflowExecutionContext(ctx *gin.Context) {
	userID, err := getUserID(ctx)
	if err != nil {
		denyCampusBusiness(ctx, errors.New("campus_workflow_execution_identity_missing"))
		return
	}
	orgID, err := getOrgID(ctx)
	if err != nil {
		denyCampusBusiness(ctx, errors.New("campus_workflow_execution_identity_missing"))
		return
	}
	permission, err := service.GetUserPermission(ctx, userID, orgID)
	if err != nil {
		denyCampusBusiness(ctx, errors.New("campus_workflow_execution_identity_missing"))
		return
	}
	role, err := service.CampusWorkflowRole(permission)
	if err != nil {
		denyCampusBusiness(ctx, errors.New("campus_workflow_role_forbidden"))
		return
	}
	// Replace any client-supplied identity headers with values derived from the
	// verified JWT/org context before calling the internal Workflow Engine.
	ctx.Request.Header.Set("X-User-Id", userID)
	ctx.Request.Header.Set("X-Org-Id", orgID)
	ctx.Request = ctx.Request.WithContext(service.WithCampusBusinessExecutionIdentity(ctx.Request.Context(), userID, orgID, role))
}

func denyCampusBusiness(ctx *gin.Context, err error) {
	gin_util.ResponseErrWithStatus(ctx, http.StatusForbidden, err)
	ctx.Abort()
}
