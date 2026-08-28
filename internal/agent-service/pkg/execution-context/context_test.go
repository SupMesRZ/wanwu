package execution_context

import (
	"net/http"
	"net/http/httptest"
	"testing"

	gin_util "github.com/UnicomAI/wanwu/pkg/gin-util"
	"github.com/gin-gonic/gin"
)

func TestMiddlewareMovesAuthIntoRequestContext(t *testing.T) {
	gin.SetMode(gin.TestMode)
	router := gin.New()
	router.GET("/chat", Middleware, func(ctx *gin.Context) {
		authorization, orgID, ok := FromContext(ctx.Request.Context())
		if !ok || authorization != "Bearer phase-2a-jwt" || orgID != "org-a" {
			ctx.Status(http.StatusInternalServerError)
			return
		}
		if ctx.GetHeader("Authorization") != "" || ctx.GetHeader(gin_util.X_ORG_ID) != "" {
			ctx.Status(http.StatusInternalServerError)
			return
		}
		ctx.Status(http.StatusNoContent)
	})

	req := httptest.NewRequest(http.MethodGet, "/chat", nil)
	req.Header.Set("Authorization", "Bearer phase-2a-jwt")
	req.Header.Set(gin_util.X_ORG_ID, "org-a")
	resp := httptest.NewRecorder()
	router.ServeHTTP(resp, req)
	if resp.Code != http.StatusNoContent {
		t.Fatalf("expected 204, got %d", resp.Code)
	}
}
