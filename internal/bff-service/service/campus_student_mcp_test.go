package service

import (
	"context"
	"encoding/json"
	"reflect"
	"strings"
	"testing"

	"github.com/ThinkInAIXYZ/go-mcp/protocol"
)

func TestCampusStudentMCPToolSchemas(t *testing.T) {
	tools := campusStudentMCPTools()
	wantNames := map[string]bool{
		"query_my_schedule":         false,
		"query_my_exam_schedule":    false,
		"query_my_score":            false,
		"query_my_leave_records":    false,
		"query_my_learning_summary": false,
		"create_leave_application":  false,
	}
	for _, tool := range tools {
		wantNames[tool.definition.Name] = true
		var schema struct {
			Type                 string                     `json:"type"`
			Properties           map[string]json.RawMessage `json:"properties"`
			AdditionalProperties *bool                      `json:"additionalProperties"`
		}
		if err := json.Unmarshal(tool.definition.RawInputSchema, &schema); err != nil {
			t.Fatal(err)
		}
		if schema.Type != "object" || schema.AdditionalProperties == nil || *schema.AdditionalProperties {
			t.Fatalf("%s must reject additional properties: %s", tool.definition.Name, tool.definition.RawInputSchema)
		}
		for property := range schema.Properties {
			if forbiddenCampusToolIdentityProperty(property) {
				t.Fatalf("%s exposes forbidden identity property %q", tool.definition.Name, property)
			}
		}
	}
	if len(tools) != len(wantNames) {
		t.Fatalf("unexpected tool count, got %d", len(tools))
	}
	for name, found := range wantNames {
		if !found {
			t.Fatalf("tool not registered: %s", name)
		}
	}
}

func TestCampusStudentMCPScoreIdentityIsolation(t *testing.T) {
	a := callCampusStudentTool(t, "query_my_score", CampusStudentExecutionIdentity{UserID: "student-a", OrgID: "org-a"}, `{}`)
	b := callCampusStudentTool(t, "query_my_score", CampusStudentExecutionIdentity{UserID: "student-b", OrgID: "org-a"}, `{}`)
	if a.IsError || b.IsError {
		t.Fatalf("unexpected score error: A=%s B=%s", campusToolText(t, a), campusToolText(t, b))
	}
	var scoreA, scoreB campusScoreResult
	decodeCampusToolText(t, a, &scoreA)
	decodeCampusToolText(t, b, &scoreB)
	if scoreA.Count == 0 || scoreB.Count == 0 || reflect.DeepEqual(scoreA.Scores, scoreB.Scores) {
		t.Fatalf("student score data mixed: A=%+v B=%+v", scoreA, scoreB)
	}
	if strings.Contains(campusToolText(t, a), "student-a") || strings.Contains(campusToolText(t, a), "org-a") {
		t.Fatal("execution identity must not be returned in tool output")
	}
}

func TestCampusStudentMCPScheduleFilters(t *testing.T) {
	identity := CampusStudentExecutionIdentity{UserID: "student-a", OrgID: "org-a"}

	today := callCampusStudentTool(t, "query_my_schedule", identity, `{}`)
	var todayResult campusScheduleResult
	decodeCampusToolText(t, today, &todayResult)
	if today.IsError || todayResult.Scope != "today" || todayResult.Date == "" {
		t.Fatalf("unexpected today result: %s", campusToolText(t, today))
	}

	date := callCampusStudentTool(t, "query_my_schedule", identity, `{"date":"2026-08-31"}`)
	var dateResult campusScheduleResult
	decodeCampusToolText(t, date, &dateResult)
	if date.IsError || dateResult.Scope != "date" || dateResult.Date != "2026-08-31" || dateResult.Count == 0 {
		t.Fatalf("unexpected date result: %s", campusToolText(t, date))
	}

	week := callCampusStudentTool(t, "query_my_schedule", identity, `{"week":"2026-W36"}`)
	var weekResult campusScheduleResult
	decodeCampusToolText(t, week, &weekResult)
	if week.IsError || weekResult.Scope != "week" || weekResult.Week != "2026-W36" || weekResult.Count == 0 {
		t.Fatalf("unexpected week result: %s", campusToolText(t, week))
	}

	empty := callCampusStudentTool(t, "query_my_schedule", identity, `{"date":"2026-08-30"}`)
	var emptyResult campusScheduleResult
	decodeCampusToolText(t, empty, &emptyResult)
	if empty.IsError || emptyResult.Count != 0 || emptyResult.Courses == nil {
		t.Fatalf("empty schedule must be a successful empty list: %s", campusToolText(t, empty))
	}
}

func TestCampusStudentMCPRejectsInvalidArguments(t *testing.T) {
	identity := CampusStudentExecutionIdentity{UserID: "student-a", OrgID: "org-a"}
	tests := []struct {
		name string
		tool string
		args string
		code string
	}{
		{"date and week", "query_my_schedule", `{"date":"2026-08-31","week":"2026-W36"}`, "date_week_conflict"},
		{"invalid date", "query_my_schedule", `{"date":"2026-02-30"}`, "invalid_date"},
		{"invalid week zero", "query_my_schedule", `{"week":"2026-W00"}`, "invalid_week"},
		{"invalid week 54", "query_my_schedule", `{"week":"2026-W54"}`, "invalid_week"},
		{"unknown term score", "query_my_score", `{"term":"1900-1901-1"}`, "term_not_found"},
		{"unknown term exam", "query_my_exam_schedule", `{"term":"1900-1901-1"}`, "term_not_found"},
		{"unknown term summary", "query_my_learning_summary", `{"term":"1900-1901-1"}`, "term_not_found"},
	}
	for _, test := range tests {
		t.Run(test.name, func(t *testing.T) {
			result := callCampusStudentTool(t, test.tool, identity, test.args)
			if !result.IsError || campusToolErrorCode(t, result) != test.code {
				t.Fatalf("expected %s, got %s", test.code, campusToolText(t, result))
			}
		})
	}

	for _, property := range []string{"studentId", "student_id", "userId", "user_id", "uid", "orgId", "org_id", "role", "previewRole", "Authorization", "header"} {
		t.Run("forged "+property, func(t *testing.T) {
			result := callCampusStudentTool(t, "query_my_score", identity, `{"`+property+`":"forged"}`)
			if !result.IsError || campusToolErrorCode(t, result) != "invalid_arguments" {
				t.Fatalf("forged property accepted: %s", campusToolText(t, result))
			}
		})
	}
}

func TestCampusStudentMCPAllToolsRequireExecutionIdentity(t *testing.T) {
	const bearer = "Bearer phase-2b-full-jwt"
	for _, tool := range campusStudentMCPTools() {
		result, err := tool.handler(context.Background(), protocol.NewCallToolRequestWithRawArguments(tool.definition.Name, json.RawMessage(`{}`)))
		if err != nil {
			t.Fatal(err)
		}
		text := campusToolText(t, result)
		if !result.IsError || campusToolErrorCode(t, result) != "execution_identity_missing" || strings.Contains(text, bearer) {
			t.Fatalf("%s did not enforce trusted identity: %s", tool.definition.Name, text)
		}
	}

	wrapped := campusStudentToolHandler(func(CampusStudentExecutionIdentity, json.RawMessage) (any, *campusToolError) {
		panic("Bearer phase-2b-full-jwt userId=student-a")
	})
	ctx := WithCampusStudentExecutionIdentity(context.Background(), "student-a", "org-a")
	result, err := wrapped(ctx, protocol.NewCallToolRequestWithRawArguments("test", json.RawMessage(`{}`)))
	if err != nil {
		t.Fatal(err)
	}
	if !result.IsError || campusToolErrorCode(t, result) != "internal_error" || strings.Contains(campusToolText(t, result), "phase-2b-full-jwt") {
		t.Fatalf("internal error leaked sensitive data: %s", campusToolText(t, result))
	}
}

func TestCampusStudentMCPBusinessTools(t *testing.T) {
	identity := CampusStudentExecutionIdentity{UserID: "student-a", OrgID: "org-a"}
	for _, name := range []string{"query_my_exam_schedule", "query_my_score", "query_my_leave_records", "query_my_learning_summary"} {
		result := callCampusStudentTool(t, name, identity, `{}`)
		if result.IsError || strings.Contains(campusToolText(t, result), "student-a") || strings.Contains(campusToolText(t, result), "org-a") {
			t.Fatalf("unexpected %s result: %s", name, campusToolText(t, result))
		}
	}
}

func callCampusStudentTool(t *testing.T, name string, identity CampusStudentExecutionIdentity, arguments string) *protocol.CallToolResult {
	t.Helper()
	for _, tool := range campusStudentMCPTools() {
		if tool.definition.Name != name {
			continue
		}
		ctx := WithCampusStudentExecutionIdentity(context.Background(), identity.UserID, identity.OrgID)
		result, err := tool.handler(ctx, protocol.NewCallToolRequestWithRawArguments(name, json.RawMessage(arguments)))
		if err != nil {
			t.Fatal(err)
		}
		return result
	}
	t.Fatalf("tool not found: %s", name)
	return nil
}

func campusToolText(t *testing.T, result *protocol.CallToolResult) string {
	t.Helper()
	if result == nil || len(result.Content) != 1 {
		t.Fatalf("unexpected tool result: %+v", result)
	}
	text, ok := result.Content[0].(*protocol.TextContent)
	if !ok {
		t.Fatalf("unexpected tool content: %+v", result.Content[0])
	}
	return text.Text
}

func decodeCampusToolText(t *testing.T, result *protocol.CallToolResult, target any) {
	t.Helper()
	if err := json.Unmarshal([]byte(campusToolText(t, result)), target); err != nil {
		t.Fatal(err)
	}
}

func campusToolErrorCode(t *testing.T, result *protocol.CallToolResult) string {
	t.Helper()
	var payload campusToolErrorResponse
	decodeCampusToolText(t, result, &payload)
	return payload.Error.Code
}

func forbiddenCampusToolIdentityProperty(property string) bool {
	normalized := strings.ToLower(strings.ReplaceAll(property, "_", ""))
	switch normalized {
	case "studentid", "userid", "uid", "orgid", "role", "previewrole", "authorization", "header":
		return true
	default:
		return false
	}
}
