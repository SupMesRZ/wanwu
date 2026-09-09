package service

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"net/http"
	"strings"
	"sync"
	"time"

	"github.com/ThinkInAIXYZ/go-mcp/protocol"
	"github.com/ThinkInAIXYZ/go-mcp/server"
	"github.com/ThinkInAIXYZ/go-mcp/transport"
	"github.com/UnicomAI/wanwu/pkg/campusbusiness"
)

type campusBusinessMCPTool struct {
	definition *protocol.Tool
	handler    server.ToolHandlerFunc
}

type campusCreateAdjustmentArguments struct {
	Course       string `json:"course"`
	OriginalTime string `json:"originalTime"`
	TargetTime   string `json:"targetTime"`
	Reason       string `json:"reason"`
	TargetRoom   string `json:"targetRoom,omitempty"`
	Confirmed    bool   `json:"confirmed"`
}

type campusReviewAdjustmentArguments struct {
	RequestID string `json:"requestId"`
	Decision  string `json:"decision"`
	Remark    string `json:"remark,omitempty"`
}

type campusAdjustmentHistoryArguments struct {
	Status string `json:"status,omitempty"`
}

type campusReviewLeaveArguments struct {
	RequestID string `json:"requestId"`
	Decision  string `json:"decision"`
	Remark    string `json:"remark,omitempty"`
	Confirmed bool   `json:"confirmed"`
}

type campusClassroomArguments struct {
	Date      string `json:"date,omitempty"`
	StartTime string `json:"startTime,omitempty"`
	EndTime   string `json:"endTime,omitempty"`
}

type campusDateArguments struct {
	Date string `json:"date,omitempty"`
}

var (
	campusTeacherMCPOnce     sync.Once
	campusTeacherMCPHandler  http.Handler
	campusTeacherMCPInitErr  error
	campusAcademicMCPOnce    sync.Once
	campusAcademicMCPHandler http.Handler
	campusAcademicMCPInitErr error
)

func ServeCampusTeacherMCP(resp http.ResponseWriter, req *http.Request) error {
	if _, ok := CampusBusinessExecutionIdentityFromContext(req.Context()); !ok {
		return errors.New("campus teacher execution identity unavailable")
	}
	campusTeacherMCPOnce.Do(func() {
		campusTeacherMCPHandler, campusTeacherMCPInitErr = newCampusBusinessMCPHandler("campus-teacher", teacherMCPTools())
	})
	if campusTeacherMCPInitErr != nil {
		return errors.New("campus teacher MCP initialization failed")
	}
	campusTeacherMCPHandler.ServeHTTP(resp, req)
	return nil
}

func ServeCampusAcademicMCP(resp http.ResponseWriter, req *http.Request) error {
	if _, ok := CampusBusinessExecutionIdentityFromContext(req.Context()); !ok {
		return errors.New("campus academic execution identity unavailable")
	}
	campusAcademicMCPOnce.Do(func() {
		campusAcademicMCPHandler, campusAcademicMCPInitErr = newCampusBusinessMCPHandler("campus-academic-admin", academicMCPTools())
	})
	if campusAcademicMCPInitErr != nil {
		return errors.New("campus academic MCP initialization failed")
	}
	campusAcademicMCPHandler.ServeHTTP(resp, req)
	return nil
}

func newCampusBusinessMCPHandler(name string, tools []campusBusinessMCPTool) (http.Handler, error) {
	streamTransport, streamHandler, err := transport.NewStreamableHTTPServerTransportAndHandler(
		transport.WithStreamableHTTPServerTransportAndHandlerOptionStateMode(transport.Stateless),
	)
	if err != nil {
		return nil, err
	}
	mcpServer, err := server.NewServer(streamTransport, server.WithServerInfo(protocol.Implementation{Name: name, Version: "1.0.0"}))
	if err != nil {
		return nil, err
	}
	for _, tool := range tools {
		mcpServer.RegisterTool(tool.definition, tool.handler)
	}
	return streamHandler.HandleMCP(), nil
}

func teacherMCPTools() []campusBusinessMCPTool {
	return buildCampusBusinessTools(campusbusiness.TeacherToolDefinitions(""), map[string]func(CampusBusinessExecutionIdentity, json.RawMessage) (any, *campusToolError){
		"query_my_teaching_schedule":       queryMyTeachingSchedule,
		"query_my_teaching_classes":        queryMyTeachingClasses,
		"query_my_invigilation":            queryMyInvigilation,
		"query_available_classrooms":       queryAvailableClassrooms,
		"query_my_adjustment_records":      queryMyAdjustmentRecords,
		"query_pending_leave_applications": queryPendingLeaveApplications,
		"review_leave_application":         reviewLeaveApplication,
		"create_course_adjustment_request": createCourseAdjustmentRequest,
	})
}

func academicMCPTools() []campusBusinessMCPTool {
	return buildCampusBusinessTools(campusbusiness.AcademicToolDefinitions(""), map[string]func(CampusBusinessExecutionIdentity, json.RawMessage) (any, *campusToolError){
		"query_course_operation_overview":   queryCourseOperationOverview,
		"query_pending_adjustment_requests": queryPendingAdjustmentRequests,
		"query_room_utilization":            queryRoomUtilization,
		"query_grade_submission_progress":   queryGradeSubmissionProgress,
		"query_teaching_service_statistics": queryTeachingServiceStatistics,
		"review_course_adjustment_request":  reviewCourseAdjustmentRequest,
	})
}

func buildCampusBusinessTools(defs []campusbusiness.ToolDefinition, handlers map[string]func(CampusBusinessExecutionIdentity, json.RawMessage) (any, *campusToolError)) []campusBusinessMCPTool {
	tools := make([]campusBusinessMCPTool, 0, len(defs))
	for _, def := range defs {
		var document map[string]any
		_ = json.Unmarshal([]byte(def.Schema), &document)
		schema := document["paths"].(map[string]any)["/"+def.Name].(map[string]any)["post"].(map[string]any)["requestBody"].(map[string]any)["content"].(map[string]any)["application/json"].(map[string]any)["schema"].(map[string]any)
		raw, _ := json.Marshal(schema)
		tool := protocol.NewToolWithRawSchema(def.Name, def.Description, raw)
		readOnly := def.ReadOnly
		tool.Annotations = &protocol.ToolAnnotations{ReadOnlyHint: &readOnly}
		tools = append(tools, campusBusinessMCPTool{definition: tool, handler: campusBusinessToolHandler(handlers[def.Name])})
	}
	return tools
}

func campusBusinessToolHandler(run func(CampusBusinessExecutionIdentity, json.RawMessage) (any, *campusToolError)) server.ToolHandlerFunc {
	return func(ctx context.Context, request *protocol.CallToolRequest) (result *protocol.CallToolResult, err error) {
		defer func() {
			if recover() != nil {
				result, err = campusToolErrorResult("internal_error", "Campus business service is temporarily unavailable"), nil
			}
		}()
		identity, ok := CampusBusinessExecutionIdentityFromContext(ctx)
		if !ok {
			return campusToolErrorResult("execution_identity_missing", "Trusted campus execution identity is unavailable"), nil
		}
		data, toolErr := run(identity, request.RawArguments)
		if toolErr != nil {
			return campusToolErrorResult(toolErr.Code, toolErr.Message), nil
		}
		return campusToolJSONResult(data), nil
	}
}

func queryMyTeachingSchedule(identity CampusBusinessExecutionIdentity, raw json.RawMessage) (any, *campusToolError) {
	args, err := decodeCampusToolArguments[campusScheduleArguments](raw)
	if err != nil || (args.Date != "" && args.Week != "") {
		return nil, invalidCampusToolArguments()
	}
	now := time.Now().In(campusLocation)
	data := buildCampusTeacherData(identity, now)
	if args.Date != "" {
		day, err := time.ParseInLocation("2006-01-02", args.Date, campusLocation)
		if err != nil {
			return nil, &campusToolError{Code: "invalid_date", Message: "date must use YYYY-MM-DD format"}
		}
		items := teachingScheduleForDate(data.TeachingSchedule, day)
		return map[string]any{"scope": "date", "date": args.Date, "count": len(items), "courses": items}, nil
	}
	if args.Week != "" {
		monday, err := parseCampusISOWeek(args.Week)
		if err != nil {
			return nil, &campusToolError{Code: "invalid_week", Message: "week must use a valid YYYY-Www ISO week"}
		}
		items := teachingScheduleForWeek(data.TeachingSchedule, monday)
		return map[string]any{"scope": "week", "week": args.Week, "count": len(items), "courses": items}, nil
	}
	weekday := int(now.Weekday())
	if weekday == 0 {
		weekday = 7
	}
	monday := now.AddDate(0, 0, 1-weekday)
	items := teachingScheduleForWeek(data.TeachingSchedule, monday)
	year, week := monday.ISOWeek()
	return map[string]any{"scope": "week", "week": fmt.Sprintf("%d-W%02d", year, week), "count": len(items), "courses": items}, nil
}

func queryMyTeachingClasses(identity CampusBusinessExecutionIdentity, raw json.RawMessage) (any, *campusToolError) {
	if _, err := decodeCampusToolArguments[campusNoArguments](raw); err != nil {
		return nil, invalidCampusToolArguments()
	}
	items := buildCampusTeacherData(identity, time.Now().In(campusLocation)).TeachingClasses
	return map[string]any{"count": len(items), "classes": items}, nil
}

func queryMyInvigilation(identity CampusBusinessExecutionIdentity, raw json.RawMessage) (any, *campusToolError) {
	if _, err := decodeCampusToolArguments[campusNoArguments](raw); err != nil {
		return nil, invalidCampusToolArguments()
	}
	items := buildCampusTeacherData(identity, time.Now().In(campusLocation)).Invigilation
	return map[string]any{"count": len(items), "invigilation": items}, nil
}

func queryAvailableClassrooms(_ CampusBusinessExecutionIdentity, raw json.RawMessage) (any, *campusToolError) {
	args, err := decodeCampusToolArguments[campusClassroomArguments](raw)
	if err != nil {
		return nil, invalidCampusToolArguments()
	}
	if args.Date != "" {
		if _, err := time.ParseInLocation("2006-01-02", args.Date, campusLocation); err != nil {
			return nil, &campusToolError{Code: "invalid_date", Message: "date must use YYYY-MM-DD format"}
		}
	}
	if (args.StartTime == "") != (args.EndTime == "") || (args.StartTime != "" && (args.StartTime >= args.EndTime || !validClock(args.StartTime) || !validClock(args.EndTime))) {
		return nil, invalidCampusToolArguments()
	}
	rooms := []campusClassroom{{Room: "C301", Building: "综合楼", Capacity: 60, Available: true}, {Room: "A204", Building: "逸夫楼", Capacity: 48, Available: true}, {Room: "201", Building: "理科楼", Capacity: 40, Available: true}}
	return map[string]any{"date": args.Date, "startTime": args.StartTime, "endTime": args.EndTime, "count": len(rooms), "classrooms": rooms}, nil
}

func queryMyAdjustmentRecords(identity CampusBusinessExecutionIdentity, raw json.RawMessage) (any, *campusToolError) {
	if _, err := decodeCampusToolArguments[campusNoArguments](raw); err != nil {
		return nil, invalidCampusToolArguments()
	}
	items, err := campusAdjustmentRecords(identity)
	if err != nil {
		return nil, campusAdjustmentPersistenceError("list teacher records", err)
	}
	return map[string]any{"count": len(items), "records": items}, nil
}

func queryPendingLeaveApplications(identity CampusBusinessExecutionIdentity, raw json.RawMessage) (any, *campusToolError) {
	args, err := decodeCampusToolArguments[campusAdjustmentHistoryArguments](raw)
	if err != nil {
		return nil, invalidCampusToolArguments()
	}
	status := strings.TrimSpace(args.Status)
	if status == "" {
		status = "pending"
	}
	queryStatus := status
	if status == "all" {
		queryStatus = ""
	} else if status != "pending" && status != "approved" && status != "rejected" {
		return nil, invalidCampusToolArguments()
	}
	items, err := ListCampusLeavesForReview(identity, queryStatus)
	if err != nil {
		return nil, &campusToolError{Code: err.Error(), Message: "请假申请暂时无法读取"}
	}
	return map[string]any{"status": status, "count": len(items), "applications": items}, nil
}

func reviewLeaveApplication(identity CampusBusinessExecutionIdentity, raw json.RawMessage) (any, *campusToolError) {
	args, err := decodeCampusToolArguments[campusReviewLeaveArguments](raw)
	if err != nil || strings.TrimSpace(args.RequestID) == "" || (args.Decision != "approve" && args.Decision != "reject") {
		return nil, invalidCampusToolArguments()
	}
	if !args.Confirmed {
		return nil, &campusToolError{Code: "explicit_confirmation_required", Message: "教师必须明确确认后才能审批"}
	}
	item, err := ReviewCampusLeave(identity, args.RequestID, args.Decision, args.Remark)
	if errors.Is(err, errCampusLeaveNotFound) {
		return nil, &campusToolError{Code: err.Error(), Message: "请假申请不存在"}
	}
	if errors.Is(err, errCampusLeaveNotPending) {
		return nil, &campusToolError{Code: err.Error(), Message: "该请假申请已处理"}
	}
	if err != nil {
		return nil, &campusToolError{Code: err.Error(), Message: "请假审批失败"}
	}
	return map[string]any{"application": item}, nil
}

func createCourseAdjustmentRequest(identity CampusBusinessExecutionIdentity, raw json.RawMessage) (any, *campusToolError) {
	args, err := decodeCampusToolArguments[campusCreateAdjustmentArguments](raw)
	if err != nil || strings.TrimSpace(args.Course) == "" || strings.TrimSpace(args.OriginalTime) == "" || strings.TrimSpace(args.TargetTime) == "" || strings.TrimSpace(args.Reason) == "" {
		return nil, invalidCampusToolArguments()
	}
	if !args.Confirmed {
		return nil, &campusToolError{Code: "explicit_confirmation_required", Message: "The user must explicitly confirm before submission"}
	}
	item, toolErr := createCampusAdjustment(identity, args.Course, args.OriginalTime, args.TargetTime, args.TargetRoom, args.Reason)
	if toolErr != nil {
		return nil, toolErr
	}
	return map[string]any{"request": item, "checks": map[string]bool{"courseOwnership": true, "targetTime": true, "teacherConflict": true, "classroomAvailability": true}}, nil
}

func queryCourseOperationOverview(_ CampusBusinessExecutionIdentity, raw json.RawMessage) (any, *campusToolError) {
	if _, err := decodeCampusToolArguments[campusNoArguments](raw); err != nil {
		return nil, invalidCampusToolArguments()
	}
	return buildCampusAcademicData().CourseOperation, nil
}

func queryPendingAdjustmentRequests(_ CampusBusinessExecutionIdentity, raw json.RawMessage) (any, *campusToolError) {
	args, err := decodeCampusToolArguments[campusAdjustmentHistoryArguments](raw)
	if err != nil {
		return nil, invalidCampusToolArguments()
	}
	status := strings.TrimSpace(args.Status)
	if status == "" {
		status = "pending"
	}
	queryStatus := status
	if status == "all" {
		queryStatus = ""
	} else if status != "pending" && status != "approved" && status != "rejected" {
		return nil, invalidCampusToolArguments()
	}
	items, err := campusAdjustmentHistory(queryStatus)
	if err != nil {
		return nil, campusAdjustmentPersistenceError("list adjustments", err)
	}
	return map[string]any{"status": status, "count": len(items), "requests": items}, nil
}

func queryRoomUtilization(identity CampusBusinessExecutionIdentity, raw json.RawMessage) (any, *campusToolError) {
	args, err := decodeCampusToolArguments[campusDateArguments](raw)
	if err != nil {
		return nil, invalidCampusToolArguments()
	}
	if args.Date != "" {
		if _, err := time.ParseInLocation("2006-01-02", args.Date, campusLocation); err != nil {
			return nil, &campusToolError{Code: "invalid_date", Message: "date must use YYYY-MM-DD format"}
		}
	}
	return map[string]any{"date": args.Date, "buildings": buildCampusAcademicData().RoomUtilization}, nil
}

func queryGradeSubmissionProgress(_ CampusBusinessExecutionIdentity, raw json.RawMessage) (any, *campusToolError) {
	if _, err := decodeCampusToolArguments[campusNoArguments](raw); err != nil {
		return nil, invalidCampusToolArguments()
	}
	return buildCampusAcademicData().GradeSubmissionProgress, nil
}

func queryTeachingServiceStatistics(_ CampusBusinessExecutionIdentity, raw json.RawMessage) (any, *campusToolError) {
	if _, err := decodeCampusToolArguments[campusNoArguments](raw); err != nil {
		return nil, invalidCampusToolArguments()
	}
	return buildCampusAcademicData().ServiceStatistics, nil
}

func reviewCourseAdjustmentRequest(identity CampusBusinessExecutionIdentity, raw json.RawMessage) (any, *campusToolError) {
	args, err := decodeCampusToolArguments[campusReviewAdjustmentArguments](raw)
	if err != nil || strings.TrimSpace(args.RequestID) == "" {
		return nil, invalidCampusToolArguments()
	}
	item, toolErr := reviewCampusAdjustment(identity, args.RequestID, args.Decision, args.Remark)
	if toolErr != nil {
		return nil, toolErr
	}
	return map[string]any{"request": item, "checks": map[string]bool{"permission": true, "pending": true, "finalCourseConflict": true, "finalRoomConflict": true}}, nil
}

func teachingScheduleForDate(items []campusTeachingSchedule, day time.Time) []campusTeachingSchedule {
	weekday := int(day.Weekday())
	if weekday == 0 {
		weekday = 7
	}
	ret := make([]campusTeachingSchedule, 0)
	for _, item := range items {
		if item.Weekday == weekday {
			item.Date = day.Format("2006-01-02")
			ret = append(ret, item)
		}
	}
	return ret
}

func teachingScheduleForWeek(items []campusTeachingSchedule, monday time.Time) []campusTeachingSchedule {
	ret := append([]campusTeachingSchedule(nil), items...)
	for i := range ret {
		ret[i].Date = monday.AddDate(0, 0, ret[i].Weekday-1).Format("2006-01-02")
	}
	return ret
}

func validClock(value string) bool {
	_, err := time.Parse("15:04", value)
	return err == nil
}
