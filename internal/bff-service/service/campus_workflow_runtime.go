package service

import (
	"context"
	"errors"
	"fmt"
	"net/http"
	net_url "net/url"
	"strings"
	"sync"
	"time"

	"github.com/UnicomAI/wanwu/internal/bff-service/config"
	"github.com/UnicomAI/wanwu/internal/bff-service/model/response"
	trace_util "github.com/UnicomAI/wanwu/pkg/trace-util"
	"github.com/gin-gonic/gin"
)

type CampusWorkflowRunIdentity struct {
	WorkflowCode   string
	Identity       CampusBusinessExecutionIdentity
	ConversationID string
	CreatedAt      time.Time
	ExpiresAt      time.Time
}

var campusWorkflowRuns sync.Map

func CampusWorkflowDefinition(code string) (CampusWorkflowDefinitionInfo, bool) {
	for _, definition := range campusWorkflowDefinitions() {
		if definition.code == code {
			return CampusWorkflowDefinitionInfo{Code: definition.code, AllowedRole: definition.allowedRole, AllowWrite: definition.allowWrite, RequiredExecutionIdentity: definition.requiredExecutionIdentity}, true
		}
	}
	return CampusWorkflowDefinitionInfo{}, false
}

type CampusWorkflowDefinitionInfo struct {
	Code                      string
	AllowedRole               string
	AllowWrite                bool
	RequiredExecutionIdentity bool
}

func ValidateCampusWorkflowExecution(code string, identity CampusBusinessExecutionIdentity) error {
	definition, ok := CampusWorkflowDefinition(code)
	if !ok {
		return errors.New("campus_workflow_not_registered")
	}
	if !definition.RequiredExecutionIdentity || identity.UserID == "" || identity.OrgID == "" || identity.ActualRole == "" {
		return errors.New("campus_workflow_execution_identity_missing")
	}
	if identity.ActualRole != definition.AllowedRole {
		return errors.New("campus_workflow_role_forbidden")
	}
	return nil
}

func BindCampusWorkflowRun(runID string, code string, identity CampusBusinessExecutionIdentity, conversationID string) error {
	if strings.TrimSpace(runID) == "" {
		return errors.New("campus_workflow_run_id_missing")
	}
	if err := ValidateCampusWorkflowExecution(code, identity); err != nil {
		return err
	}
	now := time.Now()
	campusWorkflowRuns.Store(runID, CampusWorkflowRunIdentity{WorkflowCode: code, Identity: identity, ConversationID: conversationID, CreatedAt: now, ExpiresAt: now.Add(2 * time.Hour)})
	return nil
}

func CampusWorkflowExecutionGrant(runID string) (CampusWorkflowRunIdentity, error) {
	value, ok := campusWorkflowRuns.Load(strings.TrimSpace(runID))
	if !ok {
		return CampusWorkflowRunIdentity{}, errors.New("campus_workflow_execution_grant_not_found")
	}
	grant := value.(CampusWorkflowRunIdentity)
	if !grant.ExpiresAt.IsZero() && time.Now().After(grant.ExpiresAt) {
		campusWorkflowRuns.Delete(strings.TrimSpace(runID))
		return CampusWorkflowRunIdentity{}, errors.New("campus_workflow_execution_grant_expired")
	}
	return grant, nil
}

func ValidateCampusWorkflowResume(runID, code string, identity CampusBusinessExecutionIdentity) error {
	run, err := CampusWorkflowExecutionGrant(runID)
	if err != nil {
		return errors.New("campus_workflow_resume_forbidden")
	}
	if run.WorkflowCode != code || run.Identity != identity {
		return errors.New("campus_workflow_resume_forbidden")
	}
	return ValidateCampusWorkflowExecution(code, identity)
}

func RunCampusWorkflow(ctx *gin.Context, code string, identity CampusBusinessExecutionIdentity, conversationID string, input map[string]any) (any, error) {
	if err := ValidateCampusWorkflowExecution(code, identity); err != nil {
		return nil, err
	}
	workflowID, err := campusManagedWorkflowID(ctx, identity.OrgID, code)
	if err != nil {
		return nil, err
	}
	input = stripCampusIdentityInput(input)
	ret := &response.CozeWorkflowTestRunResponse{}
	workflowInput := campusWorkflowInput(input)
	resp, err := trace_util.NewResty(ctx).R().
		SetContext(ctx.Request.Context()).
		SetHeaders(workflowHttpReqHeader(ctx)).
		SetBody(map[string]any{"workflow_id": workflowID, "space_id": identity.OrgID, "input": workflowInput}).
		SetResult(ret).
		Post(config.Cfg().Workflow.Endpoint + "/api/workflow_api/test_run")
	if err != nil || resp.StatusCode() >= http.StatusMultipleChoices || ret.Code != 0 || ret.Data == nil || ret.Data.ExecuteID == "" {
		return nil, errors.New("campus_workflow_run_failed")
	}
	if err := BindCampusWorkflowRun(ret.Data.ExecuteID, code, identity, conversationID); err != nil {
		return nil, err
	}
	if code == campusWorkflowLeaveCode || code == campusWorkflowCompleteLeaveCode {
		return waitCampusLeaveWorkflow(ctx, workflowID, identity.OrgID, ret.Data.ExecuteID)
	}
	return ret.Data, nil
}

func waitCampusLeaveWorkflow(ctx *gin.Context, workflowID, orgID, executeID string) (map[string]string, error) {
	pollCtx, cancel := context.WithTimeout(ctx.Request.Context(), time.Minute)
	defer cancel()
	ticker := time.NewTicker(time.Second)
	defer ticker.Stop()
	processURL, _ := net_url.JoinPath(config.Cfg().Workflow.Endpoint, config.Cfg().Workflow.GetProcessUri)

	for {
		select {
		case <-pollCtx.Done():
			return nil, errors.New("请假申请处理超时，尚未确认提交结果，请稍后查询请假记录")
		case <-ticker.C:
			ret := &response.CozeGetWorkflowProcessResponse{}
			resp, err := trace_util.NewResty(ctx).R().
				SetContext(pollCtx).
				SetHeaders(workflowHttpReqHeader(ctx)).
				SetQueryParams(map[string]string{"execute_id": executeID, "workflow_id": workflowID, "space_id": orgID}).
				SetResult(ret).
				Get(processURL)
			if err != nil || resp.StatusCode() >= http.StatusMultipleChoices || ret.Code != 0 || ret.Data == nil {
				return nil, errors.New("请假申请状态查询失败，尚未确认提交结果，请稍后查询请假记录")
			}
			switch ret.Data.ExecuteStatus {
			case 0, 1:
				continue
			case 2:
				for _, node := range ret.Data.NodeResults {
					if node.NodeType == "End" && node.NodeId == "900001" {
						return campusLeaveWorkflowResult(node.Output, executeID)
					}
				}
				return nil, errors.New("请假申请未完成：工作流未返回最终结果")
			case 3:
				return nil, errors.New("请假申请未提交：工作流执行失败")
			case 4:
				return nil, errors.New("请假申请未提交：工作流已取消")
			default:
				return nil, errors.New("请假申请状态异常，尚未确认提交结果")
			}
		}
	}
}

func campusLeaveWorkflowResult(output, executeID string) (map[string]string, error) {
	if strings.Contains(output, "completed") || strings.Contains(output, "已销假") {
		start := strings.Index(output, "LEV-")
		if start < 0 {
			return nil, errors.New("销假未完成：未返回申请编号")
		}
		end := start + len("LEV-")
		for end < len(output) && (output[end] == '-' || output[end] >= '0' && output[end] <= '9') {
			end++
		}
		return map[string]string{"requestId": output[start:end], "status": "completed", "workflowExecuteId": executeID}, nil
	}
	switch {
	case strings.Contains(output, "LEAVE_TIME_CONFLICT"):
		return nil, errors.New("请假申请未提交：请假时间与已有申请冲突")
	case strings.Contains(output, "invalid_leave_type"):
		return nil, errors.New("请假申请未提交：请假类型只能是病假、事假或公假")
	case strings.Contains(output, "invalid_start_time"), strings.Contains(output, "invalid_time_range"):
		return nil, errors.New("请假申请未提交：请假时间格式或范围不正确")
	case strings.Contains(output, "invalid_leave_request"):
		return nil, errors.New("请假申请未提交：请假信息不完整或尚未确认")
	case strings.Contains(output, "execution_identity_missing"):
		return nil, errors.New("请假申请未提交：登录身份失效，请重新登录")
	case strings.Contains(output, "internal_error"):
		return nil, errors.New("请假申请未提交：系统处理失败，请稍后重试")
	case strings.Contains(output, `"error"`), strings.Contains(output, `\"error\"`):
		return nil, errors.New("请假申请未提交：工作流返回错误")
	}
	start := strings.Index(output, "LEV-")
	if start < 0 {
		return nil, errors.New("请假申请未完成：未生成申请编号")
	}
	end := start + len("LEV-")
	for end < len(output) && (output[end] == '-' || output[end] >= '0' && output[end] <= '9') {
		end++
	}
	return map[string]string{"requestId": output[start:end], "status": "pending", "workflowExecuteId": executeID}, nil
}

func campusWorkflowInput(input map[string]any) map[string]string {
	clean := stripCampusIdentityInput(input)
	if clean == nil {
		return nil
	}
	result := make(map[string]string, len(clean))
	for key, value := range clean {
		result[key] = fmt.Sprint(value)
	}
	return result
}

func ResumeCampusWorkflow(ctx *gin.Context, code, runID, eventID, data string, identity CampusBusinessExecutionIdentity) error {
	if err := ValidateCampusWorkflowResume(runID, code, identity); err != nil {
		return err
	}
	workflowID, err := campusManagedWorkflowID(ctx, identity.OrgID, code)
	if err != nil {
		return err
	}
	if strings.TrimSpace(eventID) == "" {
		return errors.New("campus_workflow_event_id_missing")
	}
	ret := &response.CozeCommonResp{}
	resp, err := trace_util.NewResty(ctx).R().
		SetContext(ctx.Request.Context()).
		SetHeaders(workflowHttpReqHeader(ctx)).
		SetBody(map[string]string{"workflow_id": workflowID, "execute_id": runID, "event_id": eventID, "data": data, "space_id": identity.OrgID}).
		SetResult(ret).
		Post(config.Cfg().Workflow.Endpoint + "/api/workflow_api/test_resume")
	if err != nil || resp.StatusCode() >= http.StatusMultipleChoices || ret.Code != 0 {
		return errors.New("campus_workflow_resume_failed")
	}
	return nil
}

func campusManagedWorkflowID(ctx *gin.Context, orgID, code string) (string, error) {
	_, ok := CampusWorkflowDefinition(code)
	if !ok {
		return "", errors.New("campus_workflow_not_registered")
	}
	if err := ensureCampusWorkflows(ctx, orgID); err != nil {
		return "", err
	}
	list, err := ListWorkflowInOrg(ctx, orgID, "", "workflow")
	if err != nil {
		return "", err
	}
	for _, managed := range campusWorkflowDefinitions() {
		if managed.code == code {
			if workflowID := campusWorkflowID(list.Workflows, managed); workflowID != "" {
				return workflowID, nil
			}
		}
	}
	return "", errors.New("campus_workflow_not_found")
}

func stripCampusIdentityInput(input map[string]any) map[string]any {
	if input == nil {
		return nil
	}
	copy := make(map[string]any, len(input))
	for key, value := range input {
		switch strings.ToLower(key) {
		case "studentid", "userid", "orgid", "role", "actualrole", "previewrole":
			continue
		default:
			copy[key] = value
		}
	}
	return copy
}
