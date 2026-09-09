package middleware

import (
	"errors"
	"net/http"

	"github.com/UnicomAI/wanwu/internal/bff-service/service"
	gin_util "github.com/UnicomAI/wanwu/pkg/gin-util"
	"github.com/gin-gonic/gin"
)

var getCampusStudentPermission = service.GetUserPermission

// CheckCampusStudentRole allows administrators to preview the role while
// regular users still require one unambiguous actual IAM campus role.
func CheckCampusStudentRole(ctx *gin.Context) {
	userID, err := getUserID(ctx)
	if err != nil {
		gin_util.ResponseErrWithStatus(ctx, http.StatusForbidden, err)
		ctx.Abort()
		return
	}
	orgID, err := getOrgID(ctx)
	if err != nil {
		gin_util.ResponseErrWithStatus(ctx, http.StatusForbidden, err)
		ctx.Abort()
		return
	}
	permission, err := getCampusStudentPermission(ctx, userID, orgID)
	if err != nil {
		gin_util.ResponseErrWithStatus(ctx, http.StatusForbidden, err)
		ctx.Abort()
		return
	}
	if permission.OrgPermission.IsAdmin || permission.OrgPermission.IsSystem {
		return
	}
	role, status := service.ResolveActualCampusRole(permission.OrgPermission.Roles)
	if status != service.CampusRoleResolved {
		gin_util.ResponseErrWithStatus(ctx, http.StatusForbidden, errors.New("campus role is "+status))
		ctx.Abort()
		return
	}
	if role != service.CampusRoleStudent {
		gin_util.ResponseErrWithStatus(ctx, http.StatusForbidden, errors.New("campus student role required"))
		ctx.Abort()
	}
}

func BindCampusStudentExecutionContext(ctx *gin.Context) {
	userID, err := getUserID(ctx)
	if err != nil {
		gin_util.ResponseErrWithStatus(ctx, http.StatusForbidden, errors.New("campus student identity unavailable"))
		ctx.Abort()
		return
	}
	orgID, err := getOrgID(ctx)
	if err != nil {
		gin_util.ResponseErrWithStatus(ctx, http.StatusForbidden, errors.New("campus student identity unavailable"))
		ctx.Abort()
		return
	}
	requestCtx := service.WithCampusStudentExecutionIdentity(ctx.Request.Context(), userID, orgID)
	ctx.Request = ctx.Request.WithContext(requestCtx)
}
