package agent_tool

import (
	"errors"

	"github.com/UnicomAI/wanwu/internal/agent-service/model/request"
	"github.com/UnicomAI/wanwu/internal/agent-service/pkg/config"
	service_model "github.com/UnicomAI/wanwu/internal/agent-service/service/service-model"
	"github.com/UnicomAI/wanwu/pkg/log"
	"github.com/cloudwego/eino/adk"
	"github.com/cloudwego/eino/components/tool"
	"github.com/cloudwego/eino/compose"
	"github.com/gin-gonic/gin"
)

// BuildAgentToolsConfig 构建智能体工具配置
func BuildAgentToolsConfig(ctx *gin.Context, req *request.AgentChatParams, chatInfo *service_model.AgentChatInfo) (adk.ToolsConfig, map[string]*request.ToolConfig, error) {
	params := req.ToolParams
	//无工具调用
	if params == nil {
		return adk.ToolsConfig{
			ToolsNodeConfig: compose.ToolsNodeConfig{},
		}, make(map[string]*request.ToolConfig), nil
	}

	var changeToolName = config.GetToolTemplateConfig().SpecialToolModel(chatInfo.ModelInfo.Provider, chatInfo.ModelInfo.Model)

	//mcp 工具
	var toolList []tool.BaseTool
	//mcp 不用替换工具名
	mcpToolList, mcpToolIDNameMap, mcpErr := GetToolsFromMCPServers(ctx.Request.Context(), req.ToolParams.McpToolList)
	if mcpErr != nil {
		if campusMCP := campusManagedMCP(req.ToolParams.McpToolList); campusMCP != nil {
			log.Errorf("Managed Campus MCP unavailable: %s error_type=%T error=%v", mcpServerLogLabel(campusMCP), mcpErr, mcpErr)
			return adk.ToolsConfig{}, nil, errors.New("[direct]校园业务工具暂时不可用，请稍后重试。")
		}
		log.Errorf("MCP tools unavailable: server_count=%d error_type=%T", len(req.ToolParams.McpToolList), mcpErr)
	}
	if len(mcpToolList) > 0 {
		toolList = append(toolList, mcpToolList...)
	}
	//plugin 工具
	pluginToolList, hasChatDoc, pluginToolIDNameMap, _ := GetToolsFromOpenAPISchema(ctx, req.ToolParams.PluginToolList, changeToolName)
	if len(pluginToolList) > 0 {
		toolList = append(toolList, pluginToolList...)
	}
	//chatDoc 内置工具
	docTool := GetChatDocTool(chatInfo, hasChatDoc)
	if docTool != nil {
		toolList = append(toolList, docTool)
	}
	//skill 工具
	skillToolList, skillToolIDNameMap, _ := GetToolsFromSkills(ctx, req.ToolParams.SkillToolList, req.Input, req.AgentBaseParams.Name, req.UploadFile, chatInfo, changeToolName)
	if len(skillToolList) > 0 {
		toolList = append(toolList, skillToolList...)
	}
	//构造所有工具集合
	totalToolIDNameMap := buildAllToolIDMap(mcpToolIDNameMap, pluginToolIDNameMap, skillToolIDNameMap)
	return adk.ToolsConfig{
		ToolsNodeConfig: compose.ToolsNodeConfig{
			Tools: toolList,
		},
	}, totalToolIDNameMap, nil
}

func campusManagedMCP(toolParamsList []*request.MCPToolInfo) *request.MCPToolInfo {
	if config.GetConfig().BffServer == nil {
		return nil
	}
	for _, info := range toolParamsList {
		if info != nil && campusMCPKindForURL(info.URL, config.GetConfig().BffServer.Endpoint) != "" {
			return info
		}
	}
	return nil
}

// buildAllToolIDMap 构造所有工具集合
func buildAllToolIDMap(mcpToolIDNameMap map[string]*request.ToolConfig, pluginToolIDNameMap map[string]*request.ToolConfig, skillToolIDNameMap map[string]*request.ToolConfig) map[string]*request.ToolConfig {
	var totalToolIDNameMap = make(map[string]*request.ToolConfig)
	if len(mcpToolIDNameMap) > 0 {
		for key, value := range mcpToolIDNameMap {
			totalToolIDNameMap[key] = value
		}
	}
	if len(pluginToolIDNameMap) > 0 {
		for key, value := range pluginToolIDNameMap {
			totalToolIDNameMap[key] = value
		}
	}
	if len(skillToolIDNameMap) > 0 {
		for key, value := range skillToolIDNameMap {
			totalToolIDNameMap[key] = value
		}
	}
	return totalToolIDNameMap
}
