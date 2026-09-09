package service

import (
	"bytes"
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"net/http"
	"regexp"
	"sync"
	"time"

	"github.com/ThinkInAIXYZ/go-mcp/protocol"
	"github.com/ThinkInAIXYZ/go-mcp/server"
	"github.com/ThinkInAIXYZ/go-mcp/transport"
	"github.com/UnicomAI/wanwu/internal/bff-service/model/response"
	"github.com/UnicomAI/wanwu/pkg/campusstudent"
)

var campusStudentWeekPattern = regexp.MustCompile(`^(\d{4})-W(\d{2})$`)

var (
	campusScheduleSchema = json.RawMessage(`{
  "type":"object",
  "properties":{
    "date":{"type":"string","description":"Calendar date in YYYY-MM-DD format"},
    "week":{"type":"string","description":"ISO week in YYYY-Www format"}
  },
  "additionalProperties":false
}`)
	campusTermSchema = json.RawMessage(`{
  "type":"object",
  "properties":{"term":{"type":"string","description":"Academic term, for example 2026-2027-1"}},
  "additionalProperties":false
}`)
	campusNoArgumentsSchema = json.RawMessage(`{
  "type":"object",
  "properties":{},
  "additionalProperties":false
}`)
)

type CampusStudentExecutionIdentity struct {
	UserID      string `json:"userId"`
	OrgID       string `json:"orgId"`
	ExecutionID string `json:"-"`
}

type campusStudentExecutionIdentityKey struct{}
type campusStudentWorkflowExecutionKey struct{}
type campusStudentWorkflowExecuteIDKey struct{}
type campusStudentWorkflowCodeKey struct{}

type campusStudentMCPTool struct {
	definition *protocol.Tool
	handler    server.ToolHandlerFunc
}

type campusScheduleArguments struct {
	Date string `json:"date,omitempty"`
	Week string `json:"week,omitempty"`
}

type campusTermArguments struct {
	Term string `json:"term,omitempty"`
}

type campusNoArguments struct{}

type campusToolError struct {
	Code    string `json:"code"`
	Message string `json:"message"`
}

type campusToolErrorResponse struct {
	Error campusToolError `json:"error"`
}

type campusScheduleResult struct {
	Scope   string                         `json:"scope"`
	Date    string                         `json:"date,omitempty"`
	Week    string                         `json:"week,omitempty"`
	Count   int                            `json:"count"`
	Courses []response.CampusStudentCourse `json:"courses"`
}

type campusExamScheduleResult struct {
	Term  string                       `json:"term"`
	Count int                          `json:"count"`
	Exams []response.CampusStudentExam `json:"exams"`
}

type campusScoreResult struct {
	Term   string                        `json:"term"`
	Count  int                           `json:"count"`
	Scores []response.CampusStudentScore `json:"scores"`
}

type campusLeaveRecordsResult struct {
	Count   int                                 `json:"count"`
	Records []response.CampusStudentLeaveRecord `json:"records"`
}

type campusLearningSummaryResult struct {
	Term     string                                 `json:"term"`
	Overview response.CampusStudentTermOverview     `json:"overview"`
	Analysis response.CampusStudentLearningAnalysis `json:"analysis"`
}

var (
	campusStudentMCPOnce    sync.Once
	campusStudentMCPHandler http.Handler
	campusStudentMCPInitErr error
)

func WithCampusStudentExecutionIdentity(ctx context.Context, userID, orgID string, executionID ...string) context.Context {
	id := CampusStudentExecutionIdentity{UserID: userID, OrgID: orgID}
	if len(executionID) > 0 {
		id.ExecutionID = executionID[0]
	}
	return context.WithValue(ctx, campusStudentExecutionIdentityKey{}, CampusStudentExecutionIdentity{
		UserID: id.UserID, OrgID: id.OrgID, ExecutionID: id.ExecutionID,
	})
}

func WithCampusStudentWorkflowExecution(ctx context.Context, executeID ...string) context.Context {
	ctx = context.WithValue(ctx, campusStudentWorkflowExecutionKey{}, true)
	if len(executeID) > 0 {
		ctx = context.WithValue(ctx, campusStudentWorkflowExecuteIDKey{}, executeID[0])
	}
	return ctx
}

func WithCampusStudentWorkflowCode(ctx context.Context, workflowCode string) context.Context {
	return context.WithValue(ctx, campusStudentWorkflowCodeKey{}, workflowCode)
}

func campusStudentWorkflowCode(ctx context.Context) string {
	v, _ := ctx.Value(campusStudentWorkflowCodeKey{}).(string)
	return v
}
func campusStudentWorkflowExecution(ctx context.Context) bool {
	v, _ := ctx.Value(campusStudentWorkflowExecutionKey{}).(bool)
	return v
}

func CampusStudentExecutionIdentityFromContext(ctx context.Context) (CampusStudentExecutionIdentity, bool) {
	identity, ok := ctx.Value(campusStudentExecutionIdentityKey{}).(CampusStudentExecutionIdentity)
	return identity, ok && identity.UserID != "" && identity.OrgID != ""
}

func ServeCampusStudentMCP(resp http.ResponseWriter, req *http.Request) error {
	if _, ok := CampusStudentExecutionIdentityFromContext(req.Context()); !ok {
		return errors.New("campus student execution identity unavailable")
	}
	campusStudentMCPOnce.Do(func() {
		streamTransport, streamHandler, err := transport.NewStreamableHTTPServerTransportAndHandler(
			transport.WithStreamableHTTPServerTransportAndHandlerOptionStateMode(transport.Stateless),
		)
		if err != nil {
			campusStudentMCPInitErr = err
			return
		}
		mcpServer, err := server.NewServer(streamTransport, server.WithServerInfo(protocol.Implementation{
			Name:    "campus-student",
			Version: "1.0.0",
		}))
		if err != nil {
			campusStudentMCPInitErr = err
			return
		}
		for _, tool := range campusStudentMCPTools() {
			mcpServer.RegisterTool(tool.definition, tool.handler)
		}
		campusStudentMCPHandler = streamHandler.HandleMCP()
	})
	if campusStudentMCPInitErr != nil {
		return errors.New("campus student MCP initialization failed")
	}
	campusStudentMCPHandler.ServeHTTP(resp, req)
	return nil
}

func campusStudentMCPTools() []campusStudentMCPTool {
	defs := campusstudent.ToolDefinitions("")
	defs = append(defs, campusstudent.ToolDefinition{Name: "create_leave_application", Description: "Submit a student leave application (workflow only)", Schema: `{"type":"object","properties":{"startTime":{"type":"string"},"endTime":{"type":"string"},"leaveType":{"type":"string"},"reason":{"type":"string"},"attachmentId":{"type":"string"},"confirmed":{"type":"boolean"}},"required":["startTime","endTime","leaveType","reason","confirmed"],"additionalProperties":false}`})
	tools := make([]campusStudentMCPTool, 0, len(defs))
	for _, def := range defs {
		var run func(CampusStudentExecutionIdentity, json.RawMessage) (any, *campusToolError)
		switch def.Name {
		case "query_my_schedule":
			run = queryMySchedule
		case "query_my_exam_schedule":
			run = queryMyExamSchedule
		case "query_my_score":
			run = queryMyScore
		case "query_my_leave_records":
			run = queryMyLeaveRecords
		case "query_my_learning_summary":
			run = queryMyLearningSummary
		case "create_leave_application":
			run = createLeaveApplication
		}
		var schema map[string]any
		_ = json.Unmarshal([]byte(def.Schema), &schema)
		var props map[string]any
		if def.Name == "create_leave_application" {
			props = schema
		} else {
			props = schema["paths"].(map[string]any)["/"+def.Name].(map[string]any)["post"].(map[string]any)["requestBody"].(map[string]any)["content"].(map[string]any)["application/json"].(map[string]any)["schema"].(map[string]any)
		}
		raw, _ := json.Marshal(props)
		tools = append(tools, campusStudentMCPTool{newCampusStudentMCPTool(def.Name, def.Description, raw), campusStudentToolHandler(run)})
	}
	return tools
}

func newCampusStudentMCPTool(name, description string, schema json.RawMessage) *protocol.Tool {
	readOnly := true
	tool := protocol.NewToolWithRawSchema(name, description, schema)
	tool.Annotations = &protocol.ToolAnnotations{ReadOnlyHint: &readOnly}
	return tool
}

func campusStudentToolHandler(run func(CampusStudentExecutionIdentity, json.RawMessage) (any, *campusToolError)) server.ToolHandlerFunc {
	return func(ctx context.Context, request *protocol.CallToolRequest) (result *protocol.CallToolResult, err error) {
		defer func() {
			if recover() != nil {
				result = campusToolErrorResult("internal_error", "Campus student service is temporarily unavailable")
				err = nil
			}
		}()

		identity, ok := CampusStudentExecutionIdentityFromContext(ctx)
		if !ok {
			return campusToolErrorResult("execution_identity_missing", "Trusted campus student identity is unavailable"), nil
		}
		if request.Name == "create_leave_application" && (!campusStudentWorkflowExecution(ctx) || campusStudentWorkflowCode(ctx) != campusWorkflowLeaveCode) {
			return campusToolErrorResult("workflow_required", "This operation is only available inside the approved workflow"), nil
		}
		data, toolErr := run(identity, request.RawArguments)
		if toolErr != nil {
			return campusToolErrorResult(toolErr.Code, toolErr.Message), nil
		}
		return campusToolJSONResult(data), nil
	}
}

type leaveArguments struct {
	StartTime    string `json:"startTime"`
	EndTime      string `json:"endTime"`
	LeaveType    string `json:"leaveType"`
	Reason       string `json:"reason"`
	AttachmentID string `json:"attachmentId,omitempty"`
	Confirmed    bool   `json:"confirmed"`
}

func createLeaveApplication(identity CampusStudentExecutionIdentity, raw json.RawMessage) (any, *campusToolError) {
	args, err := decodeCampusToolArguments[leaveArguments](raw)
	if err != nil {
		return nil, invalidCampusToolArguments()
	}
	app, err := CreateCampusLeave(CampusBusinessExecutionIdentity{UserID: identity.UserID, OrgID: identity.OrgID, ActualRole: CampusRoleStudent}, identity.ExecutionID, args.StartTime, args.EndTime, args.LeaveType, args.Reason, args.AttachmentID, args.Confirmed)
	if err != nil {
		return nil, &campusToolError{Code: err.Error(), Message: "请假申请未提交"}
	}
	return app, nil
}

func queryMySchedule(identity CampusStudentExecutionIdentity, raw json.RawMessage) (any, *campusToolError) {
	args, err := decodeCampusToolArguments[campusScheduleArguments](raw)
	if err != nil {
		return nil, invalidCampusToolArguments()
	}
	if args.Date != "" && args.Week != "" {
		return nil, &campusToolError{Code: "date_week_conflict", Message: "date and week cannot be used together"}
	}

	if args.Date != "" {
		day, err := time.ParseInLocation("2006-01-02", args.Date, campusLocation)
		if err != nil {
			if parsed, parseErr := time.Parse(time.RFC3339, args.Date); parseErr == nil {
				args.Date = parsed.In(campusLocation).Format("2006-01-02")
				day = time.Date(parsed.In(campusLocation).Year(), parsed.In(campusLocation).Month(), parsed.In(campusLocation).Day(), 0, 0, 0, 0, campusLocation)
				err = nil
			}
		}
		if err != nil {
			return nil, &campusToolError{Code: "invalid_date", Message: "date must use YYYY-MM-DD format"}
		}
		courses := GetCampusStudentCoursesForDate(identity.OrgID, identity.UserID, day)
		return campusScheduleResult{Scope: "date", Date: args.Date, Count: len(courses), Courses: courses}, nil
	}

	if args.Week != "" {
		monday, err := parseCampusISOWeek(args.Week)
		if err != nil {
			return nil, &campusToolError{Code: "invalid_week", Message: "week must use a valid YYYY-Www ISO week"}
		}
		courses := GetCampusStudentCoursesForWeek(identity.OrgID, identity.UserID, monday)
		return campusScheduleResult{Scope: "week", Week: args.Week, Count: len(courses), Courses: courses}, nil
	}

	today := time.Now().In(campusLocation)
	courses := GetCampusStudentTodayCourses(identity.OrgID, identity.UserID)
	return campusScheduleResult{Scope: "today", Date: today.Format("2006-01-02"), Count: len(courses), Courses: courses}, nil
}

func queryMyExamSchedule(identity CampusStudentExecutionIdentity, raw json.RawMessage) (any, *campusToolError) {
	args, err := decodeCampusToolArguments[campusTermArguments](raw)
	if err != nil {
		return nil, invalidCampusToolArguments()
	}
	term := currentAcademicTerm(time.Now().In(campusLocation))
	if args.Term != "" && args.Term != term {
		return nil, termNotFound()
	}
	exams := GetCampusStudentExams(identity.OrgID, identity.UserID)
	return campusExamScheduleResult{Term: term, Count: len(exams), Exams: exams}, nil
}

func queryMyScore(identity CampusStudentExecutionIdentity, raw json.RawMessage) (any, *campusToolError) {
	args, err := decodeCampusToolArguments[campusTermArguments](raw)
	if err != nil {
		return nil, invalidCampusToolArguments()
	}
	scores := GetCampusStudentScores(identity.OrgID, identity.UserID, args.Term)
	if args.Term != "" && len(scores) == 0 {
		return nil, termNotFound()
	}
	term := args.Term
	if term == "" {
		term = currentAcademicTerm(time.Now().In(campusLocation))
	}
	return campusScoreResult{Term: term, Count: len(scores), Scores: scores}, nil
}

func queryMyLeaveRecords(identity CampusStudentExecutionIdentity, raw json.RawMessage) (any, *campusToolError) {
	if _, err := decodeCampusToolArguments[campusNoArguments](raw); err != nil {
		return nil, invalidCampusToolArguments()
	}
	records, err := GetCampusStudentLeaveRecords(identity.OrgID, identity.UserID)
	if err != nil {
		return nil, &campusToolError{Code: "internal_error", Message: "请假记录暂时无法读取"}
	}
	return campusLeaveRecordsResult{Count: len(records), Records: records}, nil
}

func queryMyLearningSummary(identity CampusStudentExecutionIdentity, raw json.RawMessage) (any, *campusToolError) {
	args, err := decodeCampusToolArguments[campusTermArguments](raw)
	if err != nil {
		return nil, invalidCampusToolArguments()
	}
	overview := GetCampusStudentTermOverview(identity.OrgID, identity.UserID, args.Term)
	if args.Term != "" && overview.CourseCount == 0 {
		return nil, termNotFound()
	}
	analysis := GetCampusStudentLearningAnalysis(identity.OrgID, identity.UserID, args.Term)
	return campusLearningSummaryResult{Term: overview.Term, Overview: overview, Analysis: analysis}, nil
}

func decodeCampusToolArguments[T any](raw json.RawMessage) (T, error) {
	var result T
	if len(raw) == 0 {
		raw = json.RawMessage(`{}`)
	}
	trimmed := bytes.TrimSpace(raw)
	if len(trimmed) < 2 || trimmed[0] != '{' || trimmed[len(trimmed)-1] != '}' {
		return result, errors.New("arguments must be a JSON object")
	}
	decoder := json.NewDecoder(bytes.NewReader(trimmed))
	decoder.DisallowUnknownFields()
	if err := decoder.Decode(&result); err != nil {
		return result, err
	}
	if err := decoder.Decode(&struct{}{}); err != io.EOF {
		return result, errors.New("arguments contain trailing data")
	}
	return result, nil
}

func parseCampusISOWeek(value string) (time.Time, error) {
	matches := campusStudentWeekPattern.FindStringSubmatch(value)
	if len(matches) != 3 {
		return time.Time{}, errors.New("invalid ISO week")
	}
	var year, week int
	if _, err := fmt.Sscanf(value, "%d-W%d", &year, &week); err != nil || week < 1 || week > 53 {
		return time.Time{}, errors.New("invalid ISO week")
	}
	jan4 := time.Date(year, time.January, 4, 0, 0, 0, 0, campusLocation)
	weekday := int(jan4.Weekday())
	if weekday == 0 {
		weekday = 7
	}
	monday := jan4.AddDate(0, 0, 1-weekday+(week-1)*7)
	isoYear, isoWeek := monday.ISOWeek()
	if isoYear != year || isoWeek != week {
		return time.Time{}, errors.New("invalid ISO week")
	}
	return monday, nil
}

func campusToolJSONResult(data any) *protocol.CallToolResult {
	payload, err := json.Marshal(data)
	if err != nil {
		return campusToolErrorResult("internal_error", "Campus student service is temporarily unavailable")
	}
	return protocol.NewCallToolResult([]protocol.Content{&protocol.TextContent{Type: "text", Text: string(payload)}}, false)
}

func campusToolErrorResult(code, message string) *protocol.CallToolResult {
	payload, _ := json.Marshal(campusToolErrorResponse{Error: campusToolError{Code: code, Message: message}})
	return protocol.NewCallToolResult([]protocol.Content{&protocol.TextContent{Type: "text", Text: string(payload)}}, true)
}

func invalidCampusToolArguments() *campusToolError {
	return &campusToolError{Code: "invalid_arguments", Message: "Arguments must match the tool schema"}
}

func termNotFound() *campusToolError {
	return &campusToolError{Code: "term_not_found", Message: "The requested academic term has no available data"}
}
