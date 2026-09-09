package campusbusiness

import (
	"encoding/json"
	"fmt"
)

const (
	TeacherMCPCode  = "campus_teacher"
	AcademicMCPCode = "campus_academic_admin"
)

type ToolDefinition struct {
	Name        string
	Description string
	Schema      string
	ReadOnly    bool
}

func TeacherToolDefinitions(endpoint string) []ToolDefinition {
	return []ToolDefinition{
		{Name: "query_my_teaching_schedule", Description: "查询当前登录教师本人的授课课表；无参数返回本周完整课表，指定 date 返回当天，指定 week 返回对应周。身份来自可信执行上下文，不接受 teacherId。", Schema: schema("Campus Teacher MCP", endpoint, "query_my_teaching_schedule", map[string]any{"date": property("string", "日期，YYYY-MM-DD"), "week": property("string", "ISO 周，YYYY-Www")}, nil), ReadOnly: true},
		{Name: "query_my_teaching_classes", Description: "查询当前登录教师本人负责的课程和班级。身份来自可信执行上下文，不接受 teacherId。", Schema: schema("Campus Teacher MCP", endpoint, "query_my_teaching_classes", map[string]any{}, nil), ReadOnly: true},
		{Name: "query_my_invigilation", Description: "查询当前登录教师本人的监考安排；用户询问本周或近期监考时调用。身份来自可信执行上下文，不接受 teacherId。", Schema: schema("Campus Teacher MCP", endpoint, "query_my_invigilation", map[string]any{}, nil), ReadOnly: true},
		{Name: "query_available_classrooms", Description: "按日期和起止时间查询可用教室；用户询问某时段有没有空教室时调用。", Schema: schema("Campus Teacher MCP", endpoint, "query_available_classrooms", map[string]any{"date": property("string", "日期，YYYY-MM-DD"), "startTime": property("string", "开始时间，HH:MM"), "endTime": property("string", "结束时间，HH:MM")}, nil), ReadOnly: true},
		{Name: "query_my_adjustment_records", Description: "查询当前登录教师本人的调课申请及最新审批状态。身份来自可信执行上下文，不接受 teacherId。", Schema: schema("Campus Teacher MCP", endpoint, "query_my_adjustment_records", map[string]any{}, nil), ReadOnly: true},
		{Name: "query_pending_leave_applications", Description: "以辅导员身份查询本组织学生的请假申请；无参数默认返回待审批，也可查询全部或指定状态。", Schema: schema("Campus Teacher MCP", endpoint, "query_pending_leave_applications", map[string]any{
			"status": map[string]any{"type": "string", "enum": []string{"pending", "approved", "rejected", "all"}, "description": "申请状态；省略时为 pending"},
		}, nil), ReadOnly: true},
		{Name: "review_leave_application", Description: "以辅导员身份批准或驳回学生请假；仅在用户明确确认申请编号和审批决定后调用。", Schema: schema("Campus Teacher MCP", endpoint, "review_leave_application", map[string]any{
			"requestId": property("string", "请假申请编号，例如 LEV-20260907-001"), "decision": map[string]any{"type": "string", "enum": []string{"approve", "reject"}, "description": "审批决定"}, "remark": property("string", "审批意见，可省略"), "confirmed": property("boolean", "教师是否已明确确认审批"),
		}, []string{"requestId", "decision", "confirmed"})},
		{Name: "create_course_adjustment_request", Description: "创建教师调课申请。仅在已检查课程归属、目标时间、教师冲突和教室可用性，且用户明确确认后调用；教师身份只能来自可信执行上下文。", Schema: schema("Campus Teacher MCP", endpoint, "create_course_adjustment_request", map[string]any{
			"course": property("string", "课程名称或课程编号"), "originalTime": property("string", "原上课时间"), "targetTime": property("string", "目标上课时间"), "reason": property("string", "调课原因"), "targetRoom": property("string", "目标教室，可省略"), "confirmed": property("boolean", "用户是否已明确确认提交"),
		}, []string{"course", "originalTime", "targetTime", "reason", "confirmed"})},
	}
}

func AcademicToolDefinitions(endpoint string) []ToolDefinition {
	return []ToolDefinition{
		{Name: "query_course_operation_overview", Description: "查询本周课程运行概览；用户询问课程运行情况时调用。", Schema: schema("Campus Academic Admin MCP", endpoint, "query_course_operation_overview", map[string]any{}, nil), ReadOnly: true},
		{Name: "query_pending_adjustment_requests", Description: "查询调课申请；无参数默认返回待审批，查询全部历史时 status=all，也可按 approved 或 rejected 筛选。", Schema: schema("Campus Academic Admin MCP", endpoint, "query_pending_adjustment_requests", map[string]any{
			"status": map[string]any{"type": "string", "enum": []string{"pending", "approved", "rejected", "all"}, "description": "申请状态；省略时为 pending，查询全部历史使用 all"},
		}, nil), ReadOnly: true},
		{Name: "query_room_utilization", Description: "查询教室使用情况。", Schema: schema("Campus Academic Admin MCP", endpoint, "query_room_utilization", map[string]any{"date": property("string", "日期，YYYY-MM-DD")}, nil), ReadOnly: true},
		{Name: "query_grade_submission_progress", Description: "查询成绩提交进度。", Schema: schema("Campus Academic Admin MCP", endpoint, "query_grade_submission_progress", map[string]any{}, nil), ReadOnly: true},
		{Name: "query_teaching_service_statistics", Description: "查询教学服务统计。", Schema: schema("Campus Academic Admin MCP", endpoint, "query_teaching_service_statistics", map[string]any{}, nil), ReadOnly: true},
		{Name: "review_course_adjustment_request", Description: "审批或驳回指定调课申请；仅在用户明确给出申请编号和决定时调用，教务身份来自可信执行上下文。", Schema: schema("Campus Academic Admin MCP", endpoint, "review_course_adjustment_request", map[string]any{
			"requestId": property("string", "调课申请编号，例如 ADJ-001"), "decision": map[string]any{"type": "string", "enum": []string{"approve", "reject"}, "description": "审批决定"}, "remark": property("string", "审批意见，可省略"),
		}, []string{"requestId", "decision"})},
	}
}

func TeacherEndpoint(base string) string {
	return fmt.Sprintf("%s/v1/campus/teacher/mcp", base)
}

func AcademicEndpoint(base string) string {
	return fmt.Sprintf("%s/v1/campus/academic/mcp", base)
}

func property(kind, description string) map[string]any {
	return map[string]any{"type": kind, "description": description}
}

func schema(title, endpoint, name string, properties map[string]any, required []string) string {
	requestSchema := map[string]any{"type": "object", "properties": properties, "additionalProperties": false}
	if len(required) > 0 {
		requestSchema["required"] = required
	}
	b, _ := json.Marshal(map[string]any{
		"openapi": "3.0.0", "info": map[string]any{"title": title, "version": "1.0.0"},
		"servers": []any{map[string]any{"url": endpoint}},
		"paths": map[string]any{"/" + name: map[string]any{"post": map[string]any{
			"operationId": name, "summary": name, "description": name,
			"requestBody": map[string]any{"content": map[string]any{"application/json": map[string]any{"schema": requestSchema}}},
			"responses":   map[string]any{"200": map[string]any{"description": "OK"}},
		}}},
	})
	return string(b)
}
