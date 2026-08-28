package agent_tool

import (
	"context"
	"strings"
	"testing"

	"github.com/UnicomAI/wanwu/internal/agent-service/model/request"
	"github.com/UnicomAI/wanwu/internal/agent-service/pkg/config"
	execution_context "github.com/UnicomAI/wanwu/internal/agent-service/pkg/execution-context"
)

func TestCampusMCPExecutionAuthExactWhitelist(t *testing.T) {
	original := config.GetConfig().BffServer
	config.GetConfig().BffServer = &config.BffServerConfig{Endpoint: "http://bff-service:6668"}
	defer func() { config.GetConfig().BffServer = original }()

	const token = "Bearer phase-2a-jwt"
	ctx := execution_context.WithAuth(context.Background(), token, "org-a")
	exact := "http://bff-service:6667/v1/campus/student/mcp"

	url, headers, err := buildMCPConnectionParams(ctx, &request.MCPToolInfo{
		URL: exact,
		Headers: map[string]string{
			"authorization":  "Bearer spoofed",
			"X-Org-Id":       "org-spoofed",
			"X-User-Id":      "student-b",
			"X-Preview-Role": "student",
		},
	})
	if err != nil {
		t.Fatal(err)
	}
	if url != exact || headers["Authorization"] != token || headers["X-Org-Id"] != "org-a" {
		t.Fatalf("trusted headers not applied: url=%q headers=%v", url, headers)
	}
	for key := range headers {
		if strings.EqualFold(key, "X-User-Id") || strings.EqualFold(key, "X-Preview-Role") {
			t.Fatalf("spoofable identity header survived: %s", key)
		}
	}

	notAllowed := []string{
		"http://bff-service:6668/v1/campus/student/mcp",
		"https://bff-service:6668/v1/campus/student/mcp",
		"http://other:6668/v1/campus/student/mcp",
		"http://bff-service:7777/v1/campus/student/mcp",
		"http://bff-service:6668/v1/campus/student/mcp/extra",
		"http://bff-service:6668/v1/campus/student/mcp?x=1",
		"http://bff-service:6668/v1/campus/student/mcp#fragment",
		"http://user@bff-service:6668/v1/campus/student/mcp",
		"http://third-party.example/mcp",
		"/v1/campus/student/mcp",
	}
	for _, target := range notAllowed {
		_, gotHeaders, err := buildMCPConnectionParams(ctx, &request.MCPToolInfo{URL: target})
		if err != nil {
			t.Fatal(err)
		}
		if gotHeaders["Authorization"] != "" || gotHeaders["X-Org-Id"] != "" {
			t.Fatalf("execution auth leaked to %q: %v", target, gotHeaders)
		}
	}
}

func TestMCPServerLogLabelDoesNotContainBearer(t *testing.T) {
	const secret = "phase-2a-full-jwt"
	label := mcpServerLogLabel(&request.MCPToolInfo{
		URL:       "http://example.invalid/mcp?token=" + secret,
		Headers:   map[string]string{"Authorization": "Bearer " + secret},
		Transport: "streamable",
	})
	if strings.Contains(label, secret) || strings.Contains(label, "Bearer") {
		t.Fatalf("credential present in log label: %s", label)
	}
}
