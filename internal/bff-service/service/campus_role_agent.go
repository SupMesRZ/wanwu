package service

import (
	"context"
	"errors"
	"strconv"
	"strings"

	assistant_service "github.com/UnicomAI/wanwu/api/proto/assistant-service"
	"github.com/UnicomAI/wanwu/internal/bff-service/config"
	"github.com/UnicomAI/wanwu/internal/bff-service/model/request"
	"github.com/UnicomAI/wanwu/internal/bff-service/model/response"
	"github.com/UnicomAI/wanwu/pkg/constant"
	"github.com/UnicomAI/wanwu/pkg/log"
	"github.com/gin-gonic/gin"
)

const CampusTeacherAssistantPrompt = `你是河小智教师助手，是河北大学智能体服务平台中面向教师的校园智能助手。
查询授课、班级、监考、教室或调课状态时必须调用已绑定的教师 MCP Tool，不得编造。
教师身份、教师ID、组织和角色只能来自平台可信 execution identity，不询问、不接受模型参数中的 teacherId/userId/orgId/role。
调课申请先提取课程、原时间、目标时间和原因；参数不完整时继续询问。完成归属、时间、教师冲突和教室可用性检查后生成确认信息；只有用户明确确认后才能调用 create_course_adjustment_request，禁止自动提交。
教师同时作为辅导员审批本组织学生请假。查询待审批请假时调用 query_pending_leave_applications；批准或驳回前必须展示申请编号、学生、时间和原因，只有教师明确确认决定后才能调用 review_leave_application，并传 confirmed=true。
仅使用已绑定的 campus_teacher 工具；不声称接入真实生产系统；回答简洁，不展示 JWT、内部ID、堆栈。`

const CampusAcademicAssistantPrompt = `你是河小智教务助手，是河北大学智能体服务平台中面向教务人员的校园智能助手。
查询课程运行、待审批调课、调课历史、教室、成绩提交或服务统计时必须调用已绑定的教务 MCP Tool，不得编造。
教务身份、组织和角色只能来自平台可信 execution identity，不询问、不接受模型参数中的 userId/orgId/role。
审批时提取 requestId、decision 和可选 remark；先查询申请并确认仍为 pending，检查教务权限与最终课程/教室冲突后，只有用户明确给出批准或驳回决定才调用 review_course_adjustment_request。
仅使用已绑定的 campus_academic_admin 工具；不声称接入真实生产系统；回答简洁，不展示 JWT、内部ID、堆栈。`

type campusRoleAssistantSpec struct {
	label       string
	assistantID func() string
	mcpID       string
	prompt      string
	tools       map[string]struct{}
}

type CampusRoleAssistantSpec = campusRoleAssistantSpec

func CampusTeacherAssistantSpec() CampusRoleAssistantSpec  { return campusTeacherAssistant }
func CampusAcademicAssistantSpec() CampusRoleAssistantSpec { return campusAcademicAssistant }

var campusTeacherAssistant = campusRoleAssistantSpec{
	label:       "teacher",
	assistantID: func() string { return strings.TrimSpace(config.Cfg().CampusBusiness.TeacherAssistantID) },
	mcpID:       "campus_teacher",
	prompt:      CampusTeacherAssistantPrompt,
	tools:       toolSet("query_my_teaching_schedule", "query_my_teaching_classes", "query_my_invigilation", "query_available_classrooms", "query_my_adjustment_records", "query_pending_leave_applications", "review_leave_application", "create_course_adjustment_request"),
}

var campusAcademicAssistant = campusRoleAssistantSpec{
	label:       "academic admin",
	assistantID: func() string { return strings.TrimSpace(config.Cfg().CampusBusiness.AcademicAssistantID) },
	mcpID:       "campus_academic_admin",
	prompt:      CampusAcademicAssistantPrompt,
	tools:       toolSet("query_course_operation_overview", "query_pending_adjustment_requests", "query_room_utilization", "query_grade_submission_progress", "query_teaching_service_statistics", "review_course_adjustment_request"),
}

func toolSet(names ...string) map[string]struct{} {
	ret := make(map[string]struct{}, len(names))
	for _, name := range names {
		ret[name] = struct{}{}
	}
	return ret
}

func loadCampusRoleAssistant(ctx context.Context, spec campusRoleAssistantSpec) (string, *assistant_service.AssistantInfo, error) {
	assistantID := spec.assistantID()
	if assistantID == "" {
		return "", nil, errors.New("campus " + spec.label + " assistant is not configured")
	}
	if _, err := strconv.ParseUint(assistantID, 10, 32); err != nil {
		return "", nil, errors.New("campus " + spec.label + " assistant configuration is invalid")
	}
	info, err := assistant.AssistantSnapshotInfo(ctx, &assistant_service.AssistantSnapshotInfoReq{AssistantId: assistantID})
	if err != nil || info == nil || info.GetAssistantId() != assistantID {
		log.Warnf("campus %s assistant is unavailable, assistantId=%s", spec.label, assistantID)
		return "", nil, errors.New("campus " + spec.label + " assistant is not published or unavailable")
	}
	if err := validateCampusRoleAssistant(info, spec); err != nil {
		return "", nil, err
	}
	return assistantID, info, nil
}

func validateCampusRoleAssistant(info *assistant_service.AssistantInfo, spec campusRoleAssistantSpec) error {
	return validateCampusAssistant(info, spec.mcpID, spec.tools, spec.label)
}

func validateCampusAssistant(info *assistant_service.AssistantInfo, mcpID string, requiredTools map[string]struct{}, label string) error {
	invalid := func() error { return errors.New("campus " + label + " assistant is missing required MCP tools") }
	if info.GetModelConfig().GetModelId() == "" {
		return invalid()
	}
	seen := make(map[string]struct{}, len(requiredTools))
	for _, item := range info.GetMcpInfos() {
		if item == nil || !item.GetEnable() || item.GetMcpId() != mcpID || item.GetMcpType() != "mcpserver" {
			continue
		}
		if _, required := requiredTools[item.GetActionName()]; required {
			seen[item.GetActionName()] = struct{}{}
		}
	}
	if len(seen) != len(requiredTools) {
		return invalid()
	}
	return nil
}

func GetCampusRoleAssistantBinding(ctx context.Context, spec campusRoleAssistantSpec) (*response.CampusStudentAssistantBinding, error) {
	assistantID := spec.assistantID()
	binding := &response.CampusStudentAssistantBinding{AssistantID: assistantID}
	if assistantID == "" {
		return binding, nil
	}
	if _, err := strconv.ParseUint(assistantID, 10, 32); err != nil {
		return binding, nil
	}
	// Keep publication status separate from scope validation. A published assistant
	// with an invalid campus scope must be shown as "configuration pending", not
	// incorrectly reported as "unpublished".
	info, err := assistant.AssistantSnapshotInfo(ctx, &assistant_service.AssistantSnapshotInfoReq{AssistantId: assistantID})
	if err != nil || info == nil || info.GetAssistantId() != assistantID {
		return binding, nil
	}
	binding.Name = info.GetAssistantBrief().GetName()
	binding.Published = true
	binding.Ready = validateCampusRoleAssistant(info, spec) == nil
	return binding, nil
}

func GetCampusRoleAssistant(ctx *gin.Context, spec campusRoleAssistantSpec) (*response.CampusStudentAssistantInfo, error) {
	_, info, err := loadCampusRoleAssistant(ctx.Request.Context(), spec)
	if err != nil {
		return nil, err
	}
	return &response.CampusStudentAssistantInfo{Name: info.GetAssistantBrief().GetName(), Avatar: request.Avatar{Path: info.GetAssistantBrief().GetAvatarPath()}, Prologue: info.GetPrologue(), RecommendQuestion: info.GetRecommendQuestion()}, nil
}

func CreateCampusRoleAssistantConversation(ctx *gin.Context, userID, orgID string, req request.CampusStudentAssistantConversationReq, spec campusRoleAssistantSpec) (*response.ConversationCreateResp, error) {
	assistantID, _, err := loadCampusRoleAssistant(ctx.Request.Context(), spec)
	if err != nil {
		return nil, err
	}
	result, err := ConversationCreate(ctx, userID, orgID, request.ConversationCreateRequest{AssistantId: assistantID, Prompt: req.Message}, constant.ConversationTypePublished)
	return &result, err
}

func CampusRoleAssistantChat(ctx *gin.Context, userID, orgID, clientID string, req request.CampusStudentAssistantChatReq, spec campusRoleAssistantSpec) error {
	assistantID, _, err := loadCampusRoleAssistant(ctx.Request.Context(), spec)
	if err != nil {
		return err
	}
	return AssistantConversionStream(ctx, userID, orgID, clientID, request.ConversionStreamRequest{AssistantId: assistantID, ConversationId: req.ConversationID, Prompt: req.Message}, true, constant.AppStatisticSourceWeb)
}

func GetCampusTeacherAssistantBinding(ctx context.Context) (*response.CampusStudentAssistantBinding, error) {
	return GetCampusRoleAssistantBinding(ctx, campusTeacherAssistant)
}
func GetCampusAcademicAssistantBinding(ctx context.Context) (*response.CampusStudentAssistantBinding, error) {
	return GetCampusRoleAssistantBinding(ctx, campusAcademicAssistant)
}
