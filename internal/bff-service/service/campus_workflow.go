package service

import (
	"encoding/json"
	"fmt"
	net_url "net/url"
	"strconv"
	"strings"
	"sync"

	errs "github.com/UnicomAI/wanwu/api/proto/err-code"
	"github.com/UnicomAI/wanwu/internal/bff-service/config"
	"github.com/UnicomAI/wanwu/pkg/constant"
	grpc_util "github.com/UnicomAI/wanwu/pkg/grpc-util"
	trace_util "github.com/UnicomAI/wanwu/pkg/trace-util"
	"github.com/gin-gonic/gin"
)

const (
	campusWorkflowProbeCode    = "campus_workflow_identity_probe"
	campusWorkflowTeacherCode  = "teacher_course_adjustment"
	campusWorkflowAcademicCode = "academic_course_adjustment_approval"
	campusWorkflowLeaveCode    = "student_leave_full_process"
)

type campusWorkflowStage struct {
	title       string
	description string
	kind        string
	toolName    string
	code        string
}

type campusWorkflowDefinition struct {
	code                      string
	name                      string
	desc                      string
	allowedRole               string
	allowWrite                bool
	requiredExecutionIdentity bool
	stages                    []campusWorkflowStage
}

var (
	campusWorkflowEnsureMu sync.Mutex
	campusWorkflowEnsured  sync.Map
)

func campusWorkflowDefinitions() []campusWorkflowDefinition {
	return []campusWorkflowDefinition{
		{code: campusWorkflowLeaveCode, name: "学生智能请假与销假全流程-正式", desc: "student_leave_full_process", allowedRole: CampusRoleStudent, allowWrite: true, requiredExecutionIdentity: true, stages: []campusWorkflowStage{
			{title: "解析请假请求", description: "从用户请求整理时间、类型和原因，身份只来自执行上下文。", kind: "code"},
			{title: "校验必要字段", description: "检查请假时间、类型和原因。", kind: "code"},
			{title: "查询已有请假", description: "读取当前学生本人请假记录。", kind: "mcp", toolName: "query_my_leave_records"},
			{title: "检查时间冲突", description: "发现重叠申请时终止提交。", kind: "code"},
			{title: "查询请假期间课程", description: "读取当前学生本人课程影响。", kind: "mcp", toolName: "query_my_schedule"},
			{title: "确认课程影响", description: "向学生展示影响课程并等待继续或取消。", kind: "input"},
			{title: "课程影响确认校验", description: "只有明确继续才进入申请摘要。", kind: "code", code: "async def main(args: Args) -> Output:\n    confirmation = str(args.params.get(\"input\", \"\")).strip()\n    if confirmation not in [\"继续\", \"继续申请\", \"是\"]:\n        raise ValueError(\"已取消本次请假申请\")\n    return {\"output\": \"已确认继续\"}"},
			{title: "生成请假摘要", description: "汇总时间、类型、原因和课程影响。", kind: "code"},
			{title: "最终确认提交", description: "只有明确确认才允许写入。", kind: "input"},
			{title: "最终确认校验", description: "仅接受明确的确认提交指令。", kind: "code", code: "async def main(args: Args) -> Output:\n    confirmation = str(args.params.get(\"input\", \"\")).strip()\n    if confirmation not in [\"确认\", \"确认提交\", \"是，提交\"]:\n        raise ValueError(\"未确认提交，本次申请不会写入\")\n    return {\"output\": \"已确认提交\"}"},
			{title: "提交请假申请", description: "调用受 Workflow 保护的 create_leave_application。", kind: "mcp", toolName: "create_leave_application"},
			{title: "返回申请结果", description: "返回申请编号和 pending 状态。", kind: "code"},
		}},
		{
			code: campusWorkflowProbeCode, name: "Campus Workflow 身份探针",
			desc:        "Phase 4A 只读身份探针：暂停、恢复后读取当前学生课表，不执行任何业务写入。",
			allowedRole: CampusRoleStudent, allowWrite: false, requiredExecutionIdentity: true,
			stages: []campusWorkflowStage{
				{title: "记录可信身份摘要", description: "仅在服务端上下文中读取身份，不写入工作流业务参数。", kind: "code"},
				{title: "等待用户确认", description: "InputReceiver 中断等待用户回复。", kind: "input"},
				{title: "读取本人课表", description: "调用 query_my_schedule，身份来自可信执行上下文。", kind: "mcp", toolName: "query_my_schedule"},
			},
		},
		{
			code:        campusWorkflowTeacherCode,
			name:        "教师调课申请",
			desc:        "教师调课申请：Agent理解、参数与冲突检查、教师明确确认后，通过河小智教师业务 MCP 提交待审批申请。",
			allowedRole: "teacher", allowWrite: true, requiredExecutionIdentity: true,
			stages: []campusWorkflowStage{
				{title: "整理申请参数", description: "接收结构化调课参数；教师身份只取平台执行身份。", kind: "code"},
				{title: "参数检查", description: "参数不完整时返回继续询问，不自动提交。", kind: "code"},
				{title: "检查课程是否属于当前教师", description: "调用 query_my_teaching_classes，禁止模型传 teacherId。", kind: "mcp", toolName: "query_my_teaching_classes"},
				{title: "检查目标时间", description: "校验目标日期和节次是否完整、有效。", kind: "code"},
				{title: "检查教师时间冲突", description: "调用 query_my_teaching_schedule 检查可信身份对应课表。", kind: "mcp", toolName: "query_my_teaching_schedule"},
				{title: "检查目标教室可用性", description: "调用 query_available_classrooms 检查目标时间教室。", kind: "mcp", toolName: "query_available_classrooms"},
				{title: "生成确认信息", description: "汇总原课程、目标时间、可用教室和调课原因。", kind: "code"},
				{title: "用户明确确认后", description: "中断等待教师确认；未确认不得执行提交节点。", kind: "input"},
				{title: "确认校验", description: "仅接受“确认”，其他输入终止提交。", kind: "code", code: "async def main(args: Args) -> Output:\n    confirmation = str(args.params.get(\"input\", \"\")).strip()\n    if confirmation != \"确认\":\n        raise ValueError(\"请输入“确认”后再提交\")\n    return {\"output\": \"已确认\"}"},
				{title: "MCP提交", description: "调用 create_course_adjustment_request 写入共享 Mock 调课申请。", kind: "mcp", toolName: "create_course_adjustment_request"},
				{title: "待审批", description: "返回 ADJ 申请编号和 pending 状态。", kind: "code"},
			},
		},
		{
			code:        campusWorkflowAcademicCode,
			name:        "教务调课审批",
			desc:        "教务调课审批：查询共享申请、校验状态与教务权限、复核冲突后，通过河小智教务业务 MCP 更新审批状态。",
			allowedRole: "academic_admin", allowWrite: true, requiredExecutionIdentity: true,
			stages: []campusWorkflowStage{
				{title: "查询申请", description: "调用 query_pending_adjustment_requests 读取共享待审批申请。", kind: "mcp", toolName: "query_pending_adjustment_requests"},
				{title: "确认仍为 pending", description: "申请不存在或已处理时终止审批。", kind: "code"},
				{title: "检查教务权限", description: "只接受平台可信 academic_admin execution identity。", kind: "code"},
				{title: "检查最终课程/教室冲突", description: "审批前复核课程、教师时间和目标教室。", kind: "code"},
				{title: "审批", description: "调用 review_course_adjustment_request 执行 approve 或 reject。", kind: "mcp", toolName: "review_course_adjustment_request"},
				{title: "状态更新", description: "更新教师和教务共用的 Mock 调课记录并返回结果。", kind: "code"},
			},
		},
	}
}

// ensureCampusWorkflows imports the competition workflows into Wanwu's existing
// workflow engine. The workflow service owns their IDs, drafts and graph data.
func ensureCampusWorkflows(ctx *gin.Context, orgID string) error {
	if _, ok := campusWorkflowEnsured.Load(orgID); ok {
		return nil
	}
	campusWorkflowEnsureMu.Lock()
	defer campusWorkflowEnsureMu.Unlock()
	if _, ok := campusWorkflowEnsured.Load(orgID); ok {
		return nil
	}

	listed, err := ListWorkflow(ctx, orgID, "", constant.AppTypeWorkflow)
	if err != nil {
		return err
	}
	for _, definition := range campusWorkflowDefinitions() {
		schema, err := buildCampusWorkflowSchema(definition)
		if err != nil {
			return err
		}
		var existingID string
		for _, workflow := range listed.Workflows {
			if workflow.Desc == definition.desc && campusWorkflowNameMatches(workflow.Name, definition.name) {
				existingID = workflow.WorkflowId
				break
			}
		}
		if existingID != "" {
			if err := saveCampusWorkflowSchema(ctx, orgID, existingID, schema); err != nil {
				return err
			}
			continue
		}
		if _, err := importWorkflowData(ctx, orgID, constant.AppTypeWorkflow, workflowImportData{
			Name: definition.name, Desc: definition.desc, Schema: schema,
		}); err != nil {
			return err
		}
	}
	campusWorkflowEnsured.Store(orgID, true)
	return nil
}

func campusWorkflowNameMatches(name, managedName string) bool {
	if name == managedName {
		return true
	}
	suffix, ok := strings.CutPrefix(name, managedName+"_")
	n, err := strconv.ParseUint(suffix, 10, 64)
	return ok && err == nil && n > 0
}

func buildCampusWorkflowSchema(definition campusWorkflowDefinition) (string, error) {
	nodes := []map[string]any{campusStartNode(definition.code)}
	edges := make([]map[string]string, 0, len(definition.stages)+1)
	previousID, previousOutput, previousType := "100001", "input", "string"
	for index, stage := range definition.stages {
		id := fmt.Sprintf("%d", 110001+index)
		node, output, outputType := campusStageNode(definition.code, id, index, stage, previousID, previousOutput, previousType)
		nodes = append(nodes, node)
		edges = append(edges, map[string]string{"sourceNodeID": previousID, "targetNodeID": id})
		previousID, previousOutput, previousType = id, output, outputType
	}
	nodes = append(nodes, campusEndNode(previousID, previousOutput, previousType, len(definition.stages)))
	edges = append(edges, map[string]string{"sourceNodeID": previousID, "targetNodeID": "900001"})
	raw, err := json.Marshal(map[string]any{"nodes": nodes, "edges": edges, "versions": map[string]string{"loop": "v2"}})
	return string(raw), err
}

func campusStartNode(workflowCode string) map[string]any {
	outputs := []map[string]any{{"type": "string", "name": "input", "required": false, "description": "补充说明"}}
	if workflowCode == campusWorkflowTeacherCode {
		outputs = append(outputs,
			map[string]any{"type": "string", "name": "course", "required": true, "description": "课程名称或编号"},
			map[string]any{"type": "string", "name": "originalTime", "required": true, "description": "原上课时间"},
			map[string]any{"type": "string", "name": "targetTime", "required": true, "description": "目标上课时间"},
			map[string]any{"type": "string", "name": "reason", "required": true, "description": "调课原因"},
			map[string]any{"type": "string", "name": "targetRoom", "required": false, "description": "目标教室"},
		)
	} else if workflowCode == campusWorkflowAcademicCode {
		outputs = append(outputs,
			map[string]any{"type": "string", "name": "requestId", "required": true, "description": "调课申请编号"},
			map[string]any{"type": "string", "name": "decision", "required": true, "description": "approve 或 reject"},
			map[string]any{"type": "string", "name": "remark", "required": false, "description": "审批意见"},
		)
	} else if workflowCode == campusWorkflowLeaveCode {
		outputs = append(outputs, map[string]any{"type": "string", "name": "startTime", "required": true, "description": "开始时间 RFC3339"}, map[string]any{"type": "string", "name": "endTime", "required": true, "description": "结束时间 RFC3339"}, map[string]any{"type": "string", "name": "leaveType", "required": true, "description": "病假、事假或公假"}, map[string]any{"type": "string", "name": "reason", "required": true, "description": "请假原因"}, map[string]any{"type": "string", "name": "attachmentId", "required": false, "description": "附件编号"})
	}
	return map[string]any{
		"id": "100001", "type": "1", "meta": map[string]any{"position": map[string]int{"x": 80, "y": 0}},
		"data": map[string]any{
			"nodeMeta": map[string]string{"title": "开始", "description": "接收教师或教务用户的自然语言业务请求", "icon": "/api/static/icon/icon-Start-v2.jpg"},
			"outputs":  outputs, "trigger_parameters": outputs,
		},
	}
}

func campusStageNode(workflowCode, id string, index int, stage campusWorkflowStage, previousID, previousOutput, previousType string) (map[string]any, string, string) {
	meta := map[string]any{"position": map[string]int{"x": 440 + index*360, "y": 0}}
	nodeMeta := map[string]string{"title": stage.title, "description": stage.description}
	input := campusWorkflowRef(previousID, previousOutput, previousType)
	switch stage.kind {
	case "mcp":
		nodeMeta["icon"], nodeMeta["subTitle"], nodeMeta["mainColor"] = "/api/static/icon/icon-MCP-v2.png", "MCP工具", "#FF811A"
		endpoint := "http://bff-service:6668/callback/v1/campus/teacher/mcp"
		if workflowCode == campusWorkflowAcademicCode {
			endpoint = "http://bff-service:6668/callback/v1/campus/academic/mcp"
		} else if workflowCode == campusWorkflowProbeCode || workflowCode == campusWorkflowLeaveCode {
			endpoint = "http://bff-service:6668/callback/v1/campus/student/mcp"
		}
		parameters := campusMCPInputParameters(stage.toolName)
		if workflowCode == campusWorkflowLeaveCode && stage.toolName == "query_my_schedule" {
			parameters = []map[string]any{{"name": "date", "input": campusWorkflowRef("100001", "startTime", "string")}}
		}
		if workflowCode == campusWorkflowLeaveCode && stage.toolName == "create_leave_application" {
			ref := func(name string) map[string]any {
				return map[string]any{"name": name, "input": campusWorkflowRef("100001", name, "string")}
			}
			parameters = []map[string]any{ref("startTime"), ref("endTime"), ref("leaveType"), ref("reason"), ref("attachmentId"), {"name": "confirmed", "input": campusWorkflowLiteral("boolean", true, 3)}}
		}
		return map[string]any{
			"id": id, "type": "1009", "meta": meta,
			"data": map[string]any{
				"nodeMeta": nodeMeta,
				"outputs":  []map[string]any{{"type": "object", "name": "result", "schema": []map[string]any{{"type": "list", "name": "content", "schema": map[string]any{"type": "object", "schema": []any{}}}}}},
				"inputs": map[string]any{
					"inputParameters": parameters,
					"mcpInfoList": []map[string]any{{
						"serverUrl": endpoint, "streamableUrl": endpoint, "transport": "streamable", "name": stage.toolName,
						"apiAuth": map[string]any{"authType": "none"}, "headers": map[string]string{},
					}},
				},
			},
		}, "result.content", "list"
	case "input":
		nodeMeta["icon"], nodeMeta["subTitle"], nodeMeta["mainColor"] = "/api/static/icon/icon-Input-v2.jpg", "输入", "#5C62FF"
		return map[string]any{
			"id": id, "type": "30", "meta": meta,
			"data": map[string]any{
				"nodeMeta": nodeMeta,
				"outputs":  []map[string]any{{"type": "string", "name": "confirmation", "required": true, "description": "请输入“确认”后继续提交"}},
				"inputs":   map[string]any{"outputSchema": `[{"type":"string","name":"confirmation","required":true,"description":"请输入“确认”后继续提交"}]`},
			},
		}, "confirmation", "string"
	default:
		nodeMeta["icon"], nodeMeta["subTitle"], nodeMeta["mainColor"] = "/api/static/icon/icon-Code-v2.jpg", "代码", "#00B2B2"
		return map[string]any{
			"id": id, "type": "5", "meta": meta,
			"data": map[string]any{
				"nodeMeta": nodeMeta, "outputs": []map[string]string{{"type": "string", "name": "output"}},
				"inputs": map[string]any{
					"inputParameters": []map[string]any{{"name": "input", "input": input}},
					"settingOnError":  map[string]any{"processType": 1, "timeoutMs": 60000},
					"code":            campusStageCode(stage), "language": 3,
				},
			},
		}, "output", "string"
	}
}

func campusEndNode(previousID, previousOutput, previousType string, stageCount int) map[string]any {
	return map[string]any{
		"id": "900001", "type": "2", "meta": map[string]any{"position": map[string]int{"x": 440 + stageCount*360, "y": 0}},
		"data": map[string]any{
			"nodeMeta": map[string]string{"title": "结束", "description": "返回调课申请或审批结果", "icon": "/api/static/icon/icon-End-v2.jpg"},
			"inputs":   map[string]any{"inputParameters": []map[string]any{{"name": "output", "input": campusWorkflowRef(previousID, previousOutput, previousType)}}, "terminatePlan": "returnVariables"},
		},
	}
}

func campusWorkflowRef(blockID, name, valueType string) map[string]any {
	rawMeta := 1
	input := map[string]any{"type": valueType, "value": map[string]any{"type": "ref", "content": map[string]string{"source": "block-output", "blockID": blockID, "name": name}, "rawMeta": map[string]int{"type": rawMeta}}}
	if valueType == "list" {
		input["schema"] = map[string]any{"type": "object", "schema": []any{}}
		input["value"].(map[string]any)["rawMeta"] = map[string]int{"type": 103}
	}
	return input
}

func campusWorkflowLiteral(valueType string, content any, rawType int) map[string]any {
	return map[string]any{"type": valueType, "value": map[string]any{"type": "literal", "content": content, "rawMeta": map[string]int{"type": rawType}}}
}

func campusMCPInputParameters(toolName string) []map[string]any {
	parameter := func(name, valueType string) map[string]any {
		return map[string]any{"name": name, "input": campusWorkflowRef("100001", name, valueType)}
	}
	switch toolName {
	case "create_course_adjustment_request":
		return []map[string]any{
			parameter("course", "string"), parameter("originalTime", "string"), parameter("targetTime", "string"),
			parameter("reason", "string"), parameter("targetRoom", "string"),
			{"name": "confirmed", "input": campusWorkflowLiteral("boolean", true, 3)},
		}
	case "review_course_adjustment_request":
		return []map[string]any{parameter("requestId", "string"), parameter("decision", "string"), parameter("remark", "string")}
	case "create_leave_application":
		return []map[string]any{parameter("startTime", "string"), parameter("endTime", "string"), parameter("leaveType", "string"), parameter("reason", "string"), parameter("attachmentId", "string"), {"name": "confirmed", "input": campusWorkflowLiteral("boolean", true, 3)}}
	default:
		return []map[string]any{}
	}
}

func campusStageCode(stage campusWorkflowStage) string {
	if stage.code != "" {
		return stage.code
	}
	return "async def main(args: Args) -> Output:\n    return {\"output\": str(args.params.get(\"input\", \"\"))}"
}

func campusWorkflowNeedsMCPRepair(ctx *gin.Context, orgID, workflowID string) (bool, error) {
	url, _ := net_url.JoinPath(config.Cfg().Workflow.Endpoint, "/api/workflow_api/canvas/draft")
	ret := struct {
		Code int    `json:"code"`
		Msg  string `json:"msg"`
		Data struct {
			Workflow struct {
				Schema json.RawMessage `json:"schema_json"`
			} `json:"workflow"`
		} `json:"data"`
	}{}
	resp, err := trace_util.NewResty(ctx).R().
		SetContext(ctx.Request.Context()).
		SetHeaders(workflowHttpReqHeader(ctx)).
		SetBody(map[string]string{"workflow_id": workflowID, "space_id": orgID}).
		SetResult(&ret).
		Post(url)
	if err != nil {
		return false, err
	}
	if resp.StatusCode() >= 300 || ret.Code != 0 {
		return false, grpc_util.ErrorStatusWithKey(errs.Code_BFFGeneral, "bff_workflow_canvas", fmt.Sprintf("[%d] code %d msg %s", resp.StatusCode(), ret.Code, ret.Msg))
	}
	schema := string(ret.Data.Workflow.Schema)
	if len(schema) > 0 && schema[0] == '"' {
		if err := json.Unmarshal(ret.Data.Workflow.Schema, &schema); err != nil {
			return false, err
		}
	}
	return campusWorkflowSchemaNeedsMCPRepair(schema)
}

func campusWorkflowSchemaNeedsMCPRepair(schema string) (bool, error) {
	var graph struct {
		Nodes []struct {
			Type string `json:"type"`
			Data struct {
				Inputs struct {
					MCPInfoList []json.RawMessage `json:"mcpInfoList"`
				} `json:"inputs"`
			} `json:"data"`
		} `json:"nodes"`
	}
	if err := json.Unmarshal([]byte(schema), &graph); err != nil {
		return false, err
	}
	for _, node := range graph.Nodes {
		if node.Type == "1009" && len(node.Data.Inputs.MCPInfoList) == 0 {
			return true, nil
		}
	}
	return false, nil
}

func saveCampusWorkflowSchema(ctx *gin.Context, orgID, workflowID, schema string) error {
	url, _ := net_url.JoinPath(config.Cfg().Workflow.Endpoint, "/api/workflow_api/save")
	ret := struct {
		Code int    `json:"code"`
		Msg  string `json:"msg"`
	}{}
	resp, err := trace_util.NewResty(ctx).R().
		SetContext(ctx.Request.Context()).
		SetHeaders(workflowHttpReqHeader(ctx)).
		SetBody(map[string]any{"workflow_id": workflowID, "space_id": orgID, "schema": schema, "submit_commit_id": ""}).
		SetResult(&ret).
		Post(url)
	if err != nil {
		return grpc_util.ErrorStatusWithKey(errs.Code_BFFGeneral, "bff_workflow_save", err.Error())
	}
	if resp.StatusCode() >= 300 || ret.Code != 0 {
		return grpc_util.ErrorStatusWithKey(errs.Code_BFFGeneral, "bff_workflow_save", fmt.Sprintf("[%d] code %d msg %s", resp.StatusCode(), ret.Code, ret.Msg))
	}
	return nil
}
