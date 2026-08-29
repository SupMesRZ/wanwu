package middleware

import (
	"io"
	"net/http/httptest"
	"strings"
	"testing"

	"github.com/gin-gonic/gin"
)

func TestRequestBodyRemainsReadable(t *testing.T) {
	const payload = `{"jsonrpc":"2.0","id":1,"method":"initialize","params":{}}`
	ctx, _ := gin.CreateTestContext(httptest.NewRecorder())
	ctx.Request = httptest.NewRequest("POST", "/mcp", strings.NewReader(payload))

	if _, err := requestBody(ctx); err != nil {
		t.Fatal(err)
	}
	got, err := io.ReadAll(ctx.Request.Body)
	if err != nil {
		t.Fatal(err)
	}
	if string(got) != payload {
		t.Fatalf("request body was consumed: got %q", got)
	}
}
