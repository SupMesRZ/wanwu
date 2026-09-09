package agent_tool

import (
	"context"
	"encoding/json"
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

func TestTeacherAndAcademicMCPExecutionAuthWhitelist(t *testing.T) {
	original := config.GetConfig().BffServer
	config.GetConfig().BffServer = &config.BffServerConfig{Endpoint: "http://bff-service:6668"}
	defer func() { config.GetConfig().BffServer = original }()

	ctx := execution_context.WithAuth(context.Background(), "Bearer trusted", "org-a")
	for path, kind := range map[string]string{
		"/v1/campus/teacher/mcp":  "campus_teacher",
		"/v1/campus/academic/mcp": "campus_academic_admin",
	} {
		target := "http://bff-service:6667" + path
		_, headers, err := buildMCPConnectionParams(ctx, &request.MCPToolInfo{URL: target, Headers: map[string]string{"X-User-Id": "forged"}})
		if err != nil {
			t.Fatal(err)
		}
		if headers["Authorization"] != "Bearer trusted" || headers["X-Org-Id"] != "org-a" || headers["X-User-Id"] != "" {
			t.Fatalf("trusted execution headers not enforced for %s: %v", target, headers)
		}
		if got := campusMCPKindForURL(target, config.GetConfig().BffServer.Endpoint); got != kind {
			t.Fatalf("kind=%q, want %q", got, kind)
		}
	}
}

func TestRouteCampusWorkflow(t *testing.T) {
	original := config.GetConfig().BffServer
	config.GetConfig().BffServer = &config.BffServerConfig{Endpoint: "http://bff-service:6668"}
	defer func() { config.GetConfig().BffServer = original }()

	ctx := execution_context.WithAuth(context.Background(), "Bearer trusted", "org-a")
	url, body, headers, routed, err := routeCampusWorkflow(ctx, "student_leave_full_process", `{"leaveType":"病假"}`)
	if err != nil || !routed || url != "http://bff-service:6667/v1/campus/workflow/run" {
		t.Fatalf("route failed: url=%q routed=%v err=%v", url, routed, err)
	}
	var payload struct {
		WorkflowCode string         `json:"workflowCode"`
		Input        map[string]any `json:"input"`
	}
	if err := json.Unmarshal([]byte(body), &payload); err != nil || payload.WorkflowCode != "student_leave_full_process" || payload.Input["leaveType"] != "病假" {
		t.Fatalf("unexpected body: %s err=%v", body, err)
	}
	if headers["Authorization"] != "Bearer trusted" || headers["X-Org-Id"] != "org-a" {
		t.Fatalf("trusted execution headers missing: %v", headers)
	}

	_, unchanged, _, routed, err := routeCampusWorkflow(ctx, "ordinary_workflow", `{"x":1}`)
	if err != nil || routed || unchanged != `{"x":1}` {
		t.Fatalf("ordinary workflow was modified: body=%q routed=%v err=%v", unchanged, routed, err)
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

func TestSameToolNames(t *testing.T) {
	want := []string{"query_my_schedule", "query_my_score"}
	if !sameToolNames([]string{"query_my_score", "query_my_schedule"}, want) {
		t.Fatal("same tool names in a different order should match")
	}
	if sameToolNames([]string{"query_my_schedule"}, want) || sameToolNames([]string{"query_my_schedule", "other"}, want) {
		t.Fatal("missing or unexpected tool names should not match")
	}
	if sameToolNames([]string{"query_my_schedule", "query_my_schedule"}, want) {
		t.Fatal("duplicate tool names should not hide a missing tool")
	}
}
