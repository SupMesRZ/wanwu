package middleware

import (
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"

	"github.com/UnicomAI/wanwu/internal/bff-service/model/response"
	"github.com/UnicomAI/wanwu/internal/bff-service/service"
	gin_util "github.com/UnicomAI/wanwu/pkg/gin-util"
	jwt_util "github.com/UnicomAI/wanwu/pkg/jwt-util"
	"github.com/gin-gonic/gin"
)

func TestCampusStudentExecutionIdentityAuthorization(t *testing.T) {
	gin.SetMode(gin.TestMode)
	if err := jwt_util.InitUserJWT("campus-phase-2a-test-key"); err != nil && err.Error() != "already init" {
		t.Fatal(err)
	}

	original := getCampusStudentPermission
	defer func() { getCampusStudentPermission = original }()
	getCampusStudentPermission = func(_ *gin.Context, userID, orgID string) (*response.UserPermission, error) {
		role := service.CampusRoleStudent
		if strings.Contains(userID, "teacher") || orgID == "org-b" {
			role = service.CampusRoleTeacher
		}
		if strings.Contains(userID, "academic") {
			role = service.CampusRoleAcademicAdmin
		}
		roles := []response.RoleIDName{{Name: role}}
		if strings.Contains(userID, "conflict") {
			roles = []response.RoleIDName{{Name: service.CampusRoleStudent}, {Name: service.CampusRoleTeacher}}
		}
		permission := &response.UserPermission{OrgPermission: response.UserOrgPermission{
			Roles: roles,
		}}
		if strings.Contains(userID, "admin") {
			permission.OrgPermission.IsAdmin = true
			permission.OrgPermission.IsSystem = true
		}
		return permission, nil
	}

	router := gin.New()
	router.Any("/campus", JWTUser, CheckCampusStudentRole, BindCampusStudentExecutionContext, func(ctx *gin.Context) {
		identity, ok := service.CampusStudentExecutionIdentityFromContext(ctx.Request.Context())
		if !ok {
			ctx.Status(http.StatusInternalServerError)
			return
		}
		ctx.JSON(http.StatusOK, identity)
	})

	t.Run("student A and B remain isolated", func(t *testing.T) {
		a := executeCampusIdentityRequest(t, router, "student-a", "org-a", nil)
		b := executeCampusIdentityRequest(t, router, "student-b", "org-a", nil)
		if a.Code != http.StatusOK || b.Code != http.StatusOK {
			t.Fatalf("expected 200, got A=%d B=%d", a.Code, b.Code)
		}
		var gotA, gotB service.CampusStudentExecutionIdentity
		if err := json.Unmarshal(a.Body.Bytes(), &gotA); err != nil {
			t.Fatal(err)
		}
		if err := json.Unmarshal(b.Body.Bytes(), &gotB); err != nil {
			t.Fatal(err)
		}
		if gotA.UserID != "student-a" || gotB.UserID != "student-b" || gotA == gotB {
			t.Fatalf("identity mix-up: A=%+v B=%+v", gotA, gotB)
		}
	})

	t.Run("missing authorization is 401", func(t *testing.T) {
		req := httptest.NewRequest(http.MethodGet, "/campus", nil)
		req.Header.Set(gin_util.X_ORG_ID, "org-a")
		resp := httptest.NewRecorder()
		router.ServeHTTP(resp, req)
		if resp.Code != http.StatusUnauthorized {
			t.Fatalf("expected 401, got %d", resp.Code)
		}
	})

	for _, userID := range []string{"teacher", "academic", "admin-teacher", "conflict"} {
		t.Run(userID+" is forbidden", func(t *testing.T) {
			resp := executeCampusIdentityRequest(t, router, userID, "org-a", nil)
			if resp.Code != http.StatusForbidden {
				t.Fatalf("expected 403, got %d", resp.Code)
			}
		})
	}

	t.Run("preview role cannot grant access", func(t *testing.T) {
		headers := map[string]string{"X-Preview-Role": "student", "X-Role": "student"}
		resp := executeCampusIdentityRequest(t, router, "teacher", "org-a", headers)
		if resp.Code != http.StatusForbidden {
			t.Fatalf("expected 403, got %d", resp.Code)
		}
	})

	t.Run("org switch rechecks role", func(t *testing.T) {
		if resp := executeCampusIdentityRequest(t, router, "student-a", "org-a", nil); resp.Code != http.StatusOK {
			t.Fatalf("expected org-a access, got %d", resp.Code)
		}
		if resp := executeCampusIdentityRequest(t, router, "student-a", "org-b", nil); resp.Code != http.StatusForbidden {
			t.Fatalf("expected org-b denial, got %d", resp.Code)
		}
	})
}

func executeCampusIdentityRequest(t *testing.T, router http.Handler, userID, orgID string, headers map[string]string) *httptest.ResponseRecorder {
	t.Helper()
	_, token, err := jwt_util.GenerateToken(userID, jwt_util.UserTokenTimeout)
	if err != nil {
		t.Fatal(err)
	}
	req := httptest.NewRequest(http.MethodPost, "/campus?previewRole=student", strings.NewReader(`{"previewRole":"student"}`))
	req.Header.Set("Authorization", "Bearer "+token)
	req.Header.Set(gin_util.X_ORG_ID, orgID)
	for key, value := range headers {
		req.Header.Set(key, value)
	}
	resp := httptest.NewRecorder()
	router.ServeHTTP(resp, req)
	return resp
}
