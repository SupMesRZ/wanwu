package http_client

import (
	"context"
	"os"
	"path/filepath"
	"strings"
	"testing"
	"time"

	"github.com/UnicomAI/wanwu/pkg/log"
)

func TestLogRequestDoesNotLogAuthorizationHeader(t *testing.T) {
	logFile := filepath.Join(t.TempDir(), "http.log")
	if err := log.InitLog(false, "info", log.Config{
		Enable:   true,
		Filename: logFile,
		Level:    "info",
		LevelOp:  log.LevelGE,
		MaxSize:  1,
	}); err != nil {
		t.Fatal(err)
	}

	const secret = "phase-2a-full-jwt"
	logRequest(context.Background(), &HttpRequestParams{
		Headers:  map[string]string{"Authorization": "Bearer " + secret},
		Url:      "http://agent-service/agent/chat",
		Body:     []byte(`{"safe":true}`),
		LogLevel: LogAll,
	}, "POST-JSON", time.Now(), 200, []byte(`{"ok":true}`), nil)
	_ = log.Log().Sync()

	content, err := os.ReadFile(logFile)
	if err != nil {
		t.Fatal(err)
	}
	if strings.Contains(string(content), secret) || strings.Contains(string(content), "Bearer") {
		t.Fatalf("authorization header leaked into log: %s", content)
	}
}
