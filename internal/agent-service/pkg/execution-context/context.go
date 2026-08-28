package execution_context

import (
	"context"
	"strings"

	gin_util "github.com/UnicomAI/wanwu/pkg/gin-util"
	"github.com/gin-gonic/gin"
)

type authContextKey struct{}

type authContext struct {
	authorization string
	orgID         string
}

func Middleware(ctx *gin.Context) {
	requestCtx := WithAuth(ctx.Request.Context(), ctx.GetHeader("Authorization"), ctx.GetHeader(gin_util.X_ORG_ID))
	ctx.Request = ctx.Request.WithContext(requestCtx)
	ctx.Request.Header.Del("Authorization")
	ctx.Request.Header.Del(gin_util.X_ORG_ID)
}

func WithAuth(ctx context.Context, authorization, orgID string) context.Context {
	if !validBearer(authorization) || orgID == "" || strings.ContainsAny(orgID, "\r\n") {
		return ctx
	}
	return context.WithValue(ctx, authContextKey{}, authContext{authorization: authorization, orgID: orgID})
}

func FromContext(ctx context.Context) (authorization, orgID string, ok bool) {
	auth, ok := ctx.Value(authContextKey{}).(authContext)
	return auth.authorization, auth.orgID, ok
}

func validBearer(value string) bool {
	scheme, token, ok := strings.Cut(value, " ")
	return ok && scheme == "Bearer" && token != "" && !strings.ContainsAny(token, " \r\n")
}
