package middleware

import (
	"net/http"
	"strings"
	"testing"

	"github.com/UnicomAI/wanwu/internal/bff-service/model/response"
	"github.com/UnicomAI/wanwu/internal/bff-service/service"
	jwt_util "github.com/UnicomAI/wanwu/pkg/jwt-util"
	"github.com/gin-gonic/gin"
)

func TestCampusBusinessRoleAuthorization(t *testing.T) {
	gin.SetMode(gin.TestMode)
	if err := jwt_util.InitUserJWT("campus-business-test-key"); err != nil && err.Error() != "already init" {
		t.Fatal(err)
	}
	original := getCampusBusinessPermission
	defer func() { getCampusBusinessPermission = original }()
	getCampusBusinessPermission = func(_ *gin.Context, userID, _ string) (*response.UserPermission, error) {
		role := service.CampusRoleStudent
		if strings.Contains(userID, "teacher") {
			role = service.CampusRoleTeacher
		}
		if strings.Contains(userID, "academic") {
			role = service.CampusRoleAcademicAdmin
		}
		permission := &response.UserPermission{OrgPermission: response.UserOrgPermission{Roles: []response.RoleIDName{{Name: role}}}}
		if strings.Contains(userID, "admin-") {
			permission.OrgPermission.IsAdmin = true
		}
		if strings.Contains(userID, "system-") {
			permission.OrgPermission.IsSystem = true
		}
		return permission, nil
	}

	for name, check := range map[string]gin.HandlerFunc{"teacher": CheckCampusTeacherRole, "academic": CheckCampusAcademicAdminRole} {
		t.Run(name, func(t *testing.T) {
			router := gin.New()
			router.Any("/campus", JWTUser, check, BindCampusBusinessExecutionContext, func(ctx *gin.Context) {
				if _, ok := service.CampusBusinessExecutionIdentityFromContext(ctx.Request.Context()); !ok {
					ctx.Status(http.StatusInternalServerError)
					return
				}
				ctx.Status(http.StatusOK)
			})
			allowed := map[string]string{"teacher": "teacher-a", "academic": "academic-a"}[name]
			if got := executeCampusIdentityRequest(t, router, allowed, "org-a", nil).Code; got != http.StatusOK {
				t.Fatalf("allowed role status=%d", got)
			}
			for _, admin := range []string{"admin-" + allowed, "system-" + allowed} {
				if got := executeCampusIdentityRequest(t, router, admin, "org-a", nil).Code; got != http.StatusOK {
					t.Fatalf("%s status=%d, want 200", admin, got)
				}
			}
			for _, denied := range []string{"student-a", map[string]string{"teacher": "academic-a", "academic": "teacher-a"}[name]} {
				if got := executeCampusIdentityRequest(t, router, denied, "org-a", map[string]string{"X-Preview-Role": name}).Code; got != http.StatusForbidden {
					t.Fatalf("%s status=%d, want 403", denied, got)
				}
			}
		})
	}
}
