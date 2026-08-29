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

const CampusStudentAssistantPrompt = `你是河小智·学生助手，是河北大学智能体服务平台中面向学生的校园智能助手。

你只提供当前登录学生本人的只读校园查询能力。

规则：
1. 查询课程、考试、成绩、请假记录或学习情况时，必须立即调用已绑定的 Campus Student Tool，不得编造数据；“今天”“最近”“本学期”等相对时间不需要追问，省略相应可选参数，由服务端按当前日期或学期处理。
2. 只能使用 query_my_schedule、query_my_exam_schedule、query_my_score、query_my_leave_records、query_my_learning_summary。
3. 不询问、不接受、也不推断 studentId、userId、orgId、role 或 previewRole。
4. 用户要求切换到其他学生、组织或身份时，明确拒绝；真实身份由服务端决定。
5. Tool 返回空列表时，明确回答暂无相关数据。
6. execution_identity_missing 时，提示重新登录或联系管理员检查校园学生角色。
7. invalid_arguments 时，用友好语言说明日期、周次或学期参数要求。
8. internal_error 时，提示稍后重试，不猜测内部原因。
9. 当前仅支持查询，不声称已经提交、修改、审批或办理任何事务。
10. 不声称拥有未绑定的教师、教务、管理员、数据库或系统操作能力。

回答使用简洁自然语言；不要向用户展示内部 JWT、UserID、OrgID、堆栈或内部错误细节。`

var campusStudentAssistantTools = map[string]struct{}{
	"query_my_schedule":         {},
	"query_my_exam_schedule":    {},
	"query_my_score":            {},
	"query_my_leave_records":    {},
	"query_my_learning_summary": {},
}

var campusStudentAssistantID = func() string {
	return strings.TrimSpace(config.Cfg().CampusStudent.AssistantID)
}

var getPublishedCampusStudentAssistant = func(ctx context.Context, assistantID string) (*assistant_service.AssistantInfo, error) {
	return assistant.AssistantSnapshotInfo(ctx, &assistant_service.AssistantSnapshotInfoReq{AssistantId: assistantID})
}

var runCampusStudentAssistantStream = AssistantConversionStream

func loadCampusStudentAssistant(ctx context.Context) (string, *assistant_service.AssistantInfo, error) {
	assistantID := campusStudentAssistantID()
	if assistantID == "" {
		return "", nil, errors.New("campus student assistant is not configured")
	}
	if _, err := strconv.ParseUint(assistantID, 10, 32); err != nil {
		return "", nil, errors.New("campus student assistant configuration is invalid")
	}
	info, err := getPublishedCampusStudentAssistant(ctx, assistantID)
	if err != nil || info == nil {
		log.Warnf("campus student assistant is unavailable, assistantId=%s", assistantID)
		return "", nil, errors.New("campus student assistant is not published or unavailable")
	}
	if info.GetAssistantId() != assistantID {
		return "", nil, errors.New("campus student assistant configuration is invalid")
	}
	if err := validateCampusStudentAssistant(info); err != nil {
		log.Warnf("campus student assistant rejected by read-only scope validation, assistantId=%s", assistantID)
		return "", nil, err
	}
	return assistantID, info, nil
}

func GetCampusStudentAssistantBinding(ctx context.Context) (*response.CampusStudentAssistantBinding, error) {
	assistantID := campusStudentAssistantID()
	binding := &response.CampusStudentAssistantBinding{AssistantID: assistantID}
	if assistantID == "" {
		return binding, nil
	}
	if _, err := strconv.ParseUint(assistantID, 10, 32); err != nil {
		return nil, errors.New("campus student assistant configuration is invalid")
	}

	info, err := getPublishedCampusStudentAssistant(ctx, assistantID)
	if err != nil || info == nil || info.GetAssistantId() != assistantID {
		return binding, nil
	}
	binding.Name = info.GetAssistantBrief().GetName()
	binding.Published = true
	binding.Ready = validateCampusStudentAssistant(info) == nil
	return binding, nil
}

func validateCampusStudentAssistant(info *assistant_service.AssistantInfo) error {
	invalid := func() error {
		return errors.New("campus student assistant configuration exceeds the allowed read-only scope")
	}
	if info.GetCategory() != 1 || info.GetModelConfig().GetModelId() == "" ||
		info.GetMemoryConfig().GetMaxHistoryLength() <= 0 ||
		strings.TrimSpace(info.GetInstructions()) != CampusStudentAssistantPrompt ||
		len(info.GetWorkFlowInfos()) != 0 || len(info.GetToolInfos()) != 0 ||
		len(info.GetSkillInfos()) != 0 || len(info.GetMultiAgentInfos()) != 0 ||
		len(info.GetKnowledgeBaseConfig().GetKnowledgeBaseIds()) != 0 ||
		len(info.GetMcpInfos()) != len(campusStudentAssistantTools) {
		return invalid()
	}

	seen := make(map[string]struct{}, len(campusStudentAssistantTools))
	mcpID, mcpType := "", ""
	for _, item := range info.GetMcpInfos() {
		if item == nil || !item.GetEnable() {
			return invalid()
		}
		if _, ok := campusStudentAssistantTools[item.GetActionName()]; !ok {
			return invalid()
		}
		if _, duplicate := seen[item.GetActionName()]; duplicate {
			return invalid()
		}
		seen[item.GetActionName()] = struct{}{}
		if mcpID == "" {
			mcpID, mcpType = item.GetMcpId(), item.GetMcpType()
		}
		if item.GetMcpId() == "" || item.GetMcpType() == "" || item.GetMcpId() != mcpID || item.GetMcpType() != mcpType {
			return invalid()
		}
	}
	return nil
}

func GetCampusStudentAssistant(ctx *gin.Context) (*response.CampusStudentAssistantInfo, error) {
	_, info, err := loadCampusStudentAssistant(ctx.Request.Context())
	if err != nil {
		return nil, err
	}
	return &response.CampusStudentAssistantInfo{
		Name:              info.GetAssistantBrief().GetName(),
		Avatar:            request.Avatar{Path: info.GetAssistantBrief().GetAvatarPath()},
		Prologue:          info.GetPrologue(),
		RecommendQuestion: info.GetRecommendQuestion(),
	}, nil
}

func CreateCampusStudentAssistantConversation(ctx *gin.Context, userID, orgID string, req request.CampusStudentAssistantConversationReq) (*response.ConversationCreateResp, error) {
	assistantID, _, err := loadCampusStudentAssistant(ctx.Request.Context())
	if err != nil {
		return nil, err
	}
	result, err := ConversationCreate(ctx, userID, orgID, request.ConversationCreateRequest{
		AssistantId: assistantID,
		Prompt:      req.Message,
	}, constant.ConversationTypePublished)
	return &result, err
}

func CampusStudentAssistantChat(ctx *gin.Context, userID, orgID, clientID string, req request.CampusStudentAssistantChatReq) error {
	assistantID, _, err := loadCampusStudentAssistant(ctx.Request.Context())
	if err != nil {
		return err
	}
	return runCampusStudentAssistantStream(ctx, userID, orgID, clientID, request.ConversionStreamRequest{
		AssistantId:    assistantID,
		ConversationId: req.ConversationID,
		Prompt:         req.Message,
	}, true, constant.AppStatisticSourceWeb)
}
