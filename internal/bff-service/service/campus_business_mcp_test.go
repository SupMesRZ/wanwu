package service

import (
	"encoding/json"
	"strings"
	"testing"

	"github.com/UnicomAI/wanwu/pkg/campusbusiness"
)

func TestCampusBusinessToolsAndSharedAdjustmentWorkflow(t *testing.T) {
	broken, err := campusWorkflowSchemaNeedsMCPRepair(`{"nodes":[{"type":"1009","data":{"inputs":{"mcpInfoList":[]}}}]}`)
	if err != nil || !broken {
		t.Fatalf("empty MCP configuration was not detected: broken=%v err=%v", broken, err)
	}
	teacherDefs := campusbusiness.TeacherToolDefinitions("http://bff/v1/campus/teacher/mcp")
	academicDefs := campusbusiness.AcademicToolDefinitions("http://bff/v1/campus/academic/mcp")
	if len(teacherDefs) != 8 || len(academicDefs) != 6 {
		t.Fatalf("unexpected managed tool counts: teacher=%d academic=%d", len(teacherDefs), len(academicDefs))
	}
	for _, def := range append(teacherDefs, academicDefs...) {
		if strings.Contains(def.Schema, `"teacherId"`) {
			t.Fatalf("tool %s accepts model-provided teacherId", def.Name)
		}
	}
	flows := campusWorkflowDefinitions()
	if len(flows) != 4 || flows[2].code != campusWorkflowTeacherCode || flows[3].code != campusWorkflowAcademicCode {
		t.Fatalf("managed workflows missing: %+v", flows)
	}
	expectedNodes := map[string]map[string]string{
		campusWorkflowLeaveCode:    {"解析请假请求": "5", "查询已有请假": "1009", "最终确认提交": "30", "提交请假申请": "1009"},
		campusWorkflowProbeCode:    {"记录可信身份摘要": "5", "等待用户确认": "30", "读取本人课表": "1009"},
		campusWorkflowTeacherCode:  {"整理申请参数": "5", "用户明确确认后": "30", "确认校验": "5", "MCP提交": "1009", "待审批": "5"},
		campusWorkflowAcademicCode: {"查询申请": "1009", "检查教务权限": "5", "审批": "1009", "状态更新": "5"},
	}
	expectedMCPTools := map[string]map[string]struct{}{
		campusWorkflowLeaveCode:    toolSet("query_my_leave_records", "query_my_schedule", "create_leave_application"),
		campusWorkflowProbeCode:    toolSet("query_my_schedule"),
		campusWorkflowTeacherCode:  toolSet("query_my_teaching_classes", "query_my_teaching_schedule", "query_available_classrooms", "create_course_adjustment_request"),
		campusWorkflowAcademicCode: toolSet("query_pending_adjustment_requests", "review_course_adjustment_request"),
	}
	for _, flow := range flows {
		schema, err := buildCampusWorkflowSchema(flow)
		if err != nil {
			t.Fatal(err)
		}
		var graph struct {
			Nodes []struct {
				Type string `json:"type"`
				Data struct {
					Inputs struct {
						MCPInfoList []struct {
							Name          string `json:"name"`
							Transport     string `json:"transport"`
							StreamableURL string `json:"streamableUrl"`
						} `json:"mcpInfoList"`
					} `json:"inputs"`
					NodeMeta struct {
						Title string `json:"title"`
					} `json:"nodeMeta"`
				} `json:"data"`
			} `json:"nodes"`
			Edges []map[string]string `json:"edges"`
		}
		if err := json.Unmarshal([]byte(schema), &graph); err != nil {
			t.Fatal(err)
		}
		if len(graph.Edges) != len(graph.Nodes)-1 {
			t.Fatalf("%s graph is not connected", flow.code)
		}
		actual := make(map[string]string, len(graph.Nodes))
		actualMCPTools := make(map[string]struct{})
		for _, node := range graph.Nodes {
			actual[node.Data.NodeMeta.Title] = node.Type
			if node.Type == "1009" {
				if len(node.Data.Inputs.MCPInfoList) != 1 || node.Data.Inputs.MCPInfoList[0].Transport != "streamable" || node.Data.Inputs.MCPInfoList[0].StreamableURL == "" {
					t.Fatalf("%s MCP node %q is not executable: %+v", flow.code, node.Data.NodeMeta.Title, node.Data.Inputs.MCPInfoList)
				}
				actualMCPTools[node.Data.Inputs.MCPInfoList[0].Name] = struct{}{}
			}
		}
		for title, nodeType := range expectedNodes[flow.code] {
			if actual[title] != nodeType {
				t.Fatalf("%s node %q: got type %q, want %q", flow.code, title, actual[title], nodeType)
			}
		}
		if len(actualMCPTools) != len(expectedMCPTools[flow.code]) {
			t.Fatalf("%s MCP tool count=%d, want %d", flow.code, len(actualMCPTools), len(expectedMCPTools[flow.code]))
		}
		for name := range expectedMCPTools[flow.code] {
			if _, ok := actualMCPTools[name]; !ok {
				t.Fatalf("%s missing MCP tool %q", flow.code, name)
			}
		}
	}

	originalStore := adjustmentStore
	adjustmentStore = newMemoryCampusAdjustmentStore()
	defer func() {
		adjustmentStore = originalStore
	}()

	teacher := CampusBusinessExecutionIdentity{UserID: "teacher-a", OrgID: "org-a"}
	academic := CampusBusinessExecutionIdentity{UserID: "academic-a", OrgID: "org-academic"}
	otherTeacher := CampusBusinessExecutionIdentity{UserID: "teacher-b", OrgID: "org-a"}

	if got := countFromResult(t, mustToolCall(t, queryMyTeachingSchedule, teacher, `{"date":"2026-08-31"}`)); got != 1 {
		t.Fatalf("teacher schedule count=%d, want 1", got)
	}
	if got := countFromTool(t, queryMyTeachingSchedule, teacher); got != 3 {
		t.Fatalf("teacher weekly schedule count=%d, want 3", got)
	}
	if got := countFromTool(t, queryMyInvigilation, teacher); got != 1 {
		t.Fatalf("teacher invigilation count=%d, want 1", got)
	}
	if got := countFromResult(t, mustToolCall(t, queryAvailableClassrooms, teacher, `{"date":"2026-09-04","startTime":"14:00","endTime":"16:00"}`)); got < 1 {
		t.Fatalf("available classroom count=%d, want at least 1", got)
	}
	overview := mustToolCall(t, queryCourseOperationOverview, academic, `{}`)
	overviewJSON, _ := json.Marshal(overview)
	if !strings.Contains(string(overviewJSON), `"summary":"本周课程整体运行平稳"`) {
		t.Fatalf("unexpected course overview: %s", overviewJSON)
	}

	if _, toolErr := createCourseAdjustmentRequest(teacher, json.RawMessage(`{"course":"高等数学","originalTime":"周三下午","targetTime":"周五下午","reason":"学院有活动","confirmed":false}`)); toolErr == nil || toolErr.Code != "explicit_confirmation_required" {
		t.Fatalf("submission without explicit confirmation was not rejected: %+v", toolErr)
	}
	created, toolErr := createCourseAdjustmentRequest(teacher, json.RawMessage(`{"course":"高等数学","originalTime":"周三下午","targetTime":"周五下午","reason":"学院有活动","confirmed":true}`))
	if toolErr != nil {
		t.Fatal(toolErr)
	}
	if got := adjustmentFromResult(t, created); got.RequestID != "ADJ-001" || got.Status != "pending" || got.TeacherID != teacher.UserID {
		t.Fatalf("unexpected created request: %+v", got)
	}

	pending, toolErr := queryPendingAdjustmentRequests(academic, json.RawMessage(`{}`))
	if toolErr != nil {
		t.Fatal(toolErr)
	}
	if got := countFromResult(t, pending); got != 1 {
		t.Fatalf("academic pending count=%d, want 1", got)
	}
	if got := countFromTool(t, queryMyAdjustmentRecords, otherTeacher); got != 0 {
		t.Fatalf("another teacher can see %d private records", got)
	}

	reviewed, toolErr := reviewCourseAdjustmentRequest(academic, json.RawMessage(`{"requestId":"ADJ-001","decision":"approve","remark":"同意"}`))
	if toolErr != nil {
		t.Fatal(toolErr)
	}
	if got := adjustmentFromResult(t, reviewed); got.Status != "approved" || got.ReviewerID != academic.UserID {
		t.Fatalf("unexpected reviewed request: %+v", got)
	}
	pending, toolErr = queryPendingAdjustmentRequests(academic, json.RawMessage(`{}`))
	if got := countFromResult(t, mustToolResult(t, pending, toolErr)); got != 0 {
		t.Fatalf("approved request remains pending: %d", got)
	}
	history := mustToolCall(t, queryPendingAdjustmentRequests, academic, `{"status":"all"}`)
	historyJSON, _ := json.Marshal(history)
	if countFromResult(t, history) != 1 || !strings.Contains(string(historyJSON), `"status":"approved"`) {
		t.Fatalf("academic history missing approved request: %s", historyJSON)
	}

	records, toolErr := queryMyAdjustmentRecords(teacher, json.RawMessage(`{}`))
	records = mustToolResult(t, records, toolErr)
	payload, _ := json.Marshal(records)
	if !strings.Contains(string(payload), `"requestId":"ADJ-001"`) || !strings.Contains(string(payload), `"status":"approved"`) {
		t.Fatalf("teacher did not observe approved shared state: %s", payload)
	}
}

func adjustmentFromResult(t *testing.T, value any) campusAdjustmentRequest {
	t.Helper()
	payload, _ := json.Marshal(value)
	var envelope struct {
		Request campusAdjustmentRequest `json:"request"`
	}
	if err := json.Unmarshal(payload, &envelope); err != nil {
		t.Fatal(err)
	}
	return envelope.Request
}

func countFromResult(t *testing.T, value any) int {
	t.Helper()
	payload, _ := json.Marshal(value)
	var envelope struct {
		Count int `json:"count"`
	}
	if err := json.Unmarshal(payload, &envelope); err != nil {
		t.Fatal(err)
	}
	return envelope.Count
}

func countFromTool(t *testing.T, fn func(CampusBusinessExecutionIdentity, json.RawMessage) (any, *campusToolError), identity CampusBusinessExecutionIdentity) int {
	t.Helper()
	value, toolErr := fn(identity, json.RawMessage(`{}`))
	return countFromResult(t, mustToolResult(t, value, toolErr))
}

func mustToolCall(t *testing.T, fn func(CampusBusinessExecutionIdentity, json.RawMessage) (any, *campusToolError), identity CampusBusinessExecutionIdentity, raw string) any {
	t.Helper()
	value, toolErr := fn(identity, json.RawMessage(raw))
	return mustToolResult(t, value, toolErr)
}

func mustToolResult(t *testing.T, value any, toolErr *campusToolError) any {
	t.Helper()
	if toolErr != nil {
		t.Fatal(toolErr)
	}
	return value
}
