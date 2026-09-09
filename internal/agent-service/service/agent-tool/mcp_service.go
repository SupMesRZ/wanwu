package agent_tool

import (
	"context"
	"fmt"
	"net/url"
	"strings"
	"time"

	"github.com/UnicomAI/wanwu/internal/agent-service/model/request"
	"github.com/UnicomAI/wanwu/internal/agent-service/pkg/config"
	execution_context "github.com/UnicomAI/wanwu/internal/agent-service/pkg/execution-context"
	mcp_client "github.com/UnicomAI/wanwu/internal/agent-service/service/mcp-client"
	"github.com/UnicomAI/wanwu/pkg/constant"
	gin_util "github.com/UnicomAI/wanwu/pkg/gin-util"
	"github.com/UnicomAI/wanwu/pkg/log"
	mcp_util "github.com/UnicomAI/wanwu/pkg/mcp-util"
	"github.com/cloudwego/eino-ext/components/tool/mcp"
	"github.com/cloudwego/eino/components/tool"
	"github.com/mark3labs/mcp-go/client"
	"github.com/mark3labs/mcp-go/client/transport"
	mcpTypes "github.com/mark3labs/mcp-go/mcp"
	"go.opentelemetry.io/otel"
	"go.opentelemetry.io/otel/propagation"
)

var campusMCPPaths = map[string]string{
	"campus_student":        "/v1/campus/student/mcp",
	"campus_teacher":        "/v1/campus/teacher/mcp",
	"campus_academic_admin": "/v1/campus/academic/mcp",
}

type MCPServerInfo struct {
	Transport    string   `json:"transport"`
	URL          string   `json:"url"`
	ToolNameList []string `json:"toolNameList"`
}

// createMCPClient 根据 transport 类型创建 MCP 客户端
// transport: "sse" 或 "streamable"
func createMCPClient(ctx context.Context, mcpToolInfo *request.MCPToolInfo) (client.MCPClient, error) {
	var mcpClient *client.Client
	var transportType = mcpToolInfo.Transport
	url, headers, err := buildMCPConnectionParams(ctx, mcpToolInfo)
	if err != nil {
		return nil, fmt.Errorf("stage=client_configuration: %w", err)
	}
	if headers == nil {
		headers = make(map[string]string)
	}
	//header 注入traceID
	otel.GetTextMapPropagator().Inject(ctx, propagation.MapCarrier(headers))

	switch transportType {
	case constant.MCPTransportStreamable:
		// 创建 StreamableHTTP 客户端
		if len(headers) > 0 {
			mcpClient, err = client.NewStreamableHttpClient(url, transport.WithHTTPHeaders(headers))
		} else {
			mcpClient, err = client.NewStreamableHttpClient(url)
		}
		if err != nil {
			return nil, fmt.Errorf("stage=streamable_transport_creation: %w", err)
		}
	case constant.MCPTransportSSE:
		// 默认使用 SSE 客户端
		if len(headers) > 0 {
			mcpClient, err = client.NewSSEMCPClient(url, transport.WithHeaders(headers))
		} else {
			mcpClient, err = client.NewSSEMCPClient(url)
		}
		if err != nil {
			return nil, fmt.Errorf("stage=sse_transport_creation: %w", err)
		}
	default:
		return nil, fmt.Errorf("stage=transport_creation: unsupported transport type: %s", transportType)
	}

	retryMcpClient := mcp_client.NewDefaultRetryMcpClient(mcpClient)

	// 启动客户端
	err = retryMcpClient.Start(ctx)
	if err != nil {
		_ = retryMcpClient.Close()
		return nil, fmt.Errorf("stage=transport_start: %w", err)
	}

	// 初始化 MCP 客户端
	initCtx, cancel := context.WithTimeout(ctx, 60*time.Second)
	defer cancel()

	initRequest := mcpTypes.InitializeRequest{}
	initRequest.Params.ProtocolVersion = mcpTypes.LATEST_PROTOCOL_VERSION
	initRequest.Params.ClientInfo = mcpTypes.Implementation{
		Name:    "eino-mcp-client",
		Version: "0.1.0",
	}
	initRequest.Params.Capabilities = mcpTypes.ClientCapabilities{}

	_, err = retryMcpClient.Initialize(initCtx, initRequest)
	if err != nil {
		_ = retryMcpClient.Close()
		return nil, fmt.Errorf("stage=initialize: %w", err)
	}

	log.Infof("MCP client (%s) initialized successfully", transportType)
	return retryMcpClient, nil
}

func GetToolsFromMCPServers(ctx context.Context, toolParamsList []*request.MCPToolInfo) ([]tool.BaseTool, map[string]*request.ToolConfig, error) {
	if len(toolParamsList) == 0 {
		return nil, nil, nil
	}

	var allTools []tool.BaseTool
	var toolMap = make(map[string]*request.ToolConfig)

	for _, serverInfo := range toolParamsList {
		label := mcpServerLogLabel(serverInfo)
		log.Infof("Connecting to MCP server: %s", label)

		mcpClient, err := createMCPClient(ctx, serverInfo)
		if err != nil {
			return nil, nil, fmt.Errorf("failed to create MCP client for %s: %w", label, err)
		}
		// 注意:不要在这里关闭客户端,因为工具在后续使用时还需要这个连接
		// defer mcpClient.Close()

		tools, err := mcp.GetTools(ctx, &mcp.Config{
			Cli:          mcpClient,
			ToolNameList: serverInfo.ToolNameList,
		})
		if err != nil {
			_ = mcpClient.Close()
			stage := "tools_list"
			if strings.Contains(err.Error(), "input schema") {
				stage = "tool_schema"
			}
			return nil, nil, fmt.Errorf("stage=%s server=%s: %w", stage, label, err)
		}

		toolNames := make([]string, 0, len(tools))
		for _, loadedTool := range tools {
			info, err := loadedTool.Info(ctx)
			if err != nil {
				_ = mcpClient.Close()
				return nil, nil, fmt.Errorf("stage=tool_schema server=%s: %w", label, err)
			}
			toolNames = append(toolNames, info.Name)
		}
		campusEndpoint := config.GetConfig().BffServer != nil && campusMCPKindForURL(serverInfo.URL, config.GetConfig().BffServer.Endpoint) != ""
		if campusEndpoint && !sameToolNames(toolNames, serverInfo.ToolNameList) {
			_ = mcpClient.Close()
			return nil, nil, fmt.Errorf("stage=tool_name_filter server=%s: requested=%v loaded=%v", label, serverInfo.ToolNameList, toolNames)
		}
		log.Infof("Loaded %d tools from %s tool_names=%v", len(tools), label, toolNames)
		if len(serverInfo.ToolNameList) > 0 {
			//mcp 的方法名先不做替换，因为mcp的函数名基本都是符合规则一般不会有特殊字符
			for _, toolName := range serverInfo.ToolNameList {
				toolMap[toolName] = &request.ToolConfig{
					Avatar:   serverInfo.Avatar,
					ToolName: toolName,
					ToolID:   toolName,
				}
			}
		}
		allTools = append(allTools, tools...)
	}

	return allTools, toolMap, nil
}

func sameToolNames(got, want []string) bool {
	if len(got) != len(want) {
		return false
	}
	wanted := make(map[string]int, len(want))
	for _, name := range want {
		wanted[name]++
	}
	for _, name := range got {
		if wanted[name] == 0 {
			return false
		}
		wanted[name]--
	}
	return true
}

func buildMCPConnectionParams(ctx context.Context, info *request.MCPToolInfo) (string, map[string]string, error) {
	apiAuth := info.ApiAuth
	headers := info.Headers
	bffEndpoint := ""
	if config.GetConfig().BffServer != nil {
		bffEndpoint = config.GetConfig().BffServer.Endpoint
	}
	campusEndpoint := campusMCPKindForURL(info.URL, bffEndpoint) != ""
	if campusEndpoint {
		apiAuth = nil
		headers = withoutIdentityHeaders(headers)
	}

	mergedURL, mergedHeaders, err := mcp_util.MergeMcpParams(info.URL, apiAuth, headers)
	if err != nil {
		return "", nil, err
	}
	if mergedHeaders == nil {
		mergedHeaders = make(map[string]string)
	}
	if campusEndpoint {
		if authorization, orgID, ok := execution_context.FromContext(ctx); ok {
			mergedHeaders["Authorization"] = authorization
			mergedHeaders[gin_util.X_ORG_ID] = orgID
		}
	}
	return mergedURL, mergedHeaders, nil
}

func isExactCampusStudentMCPURL(target, bffEndpoint string) bool {
	return isExactCampusMCPURL(target, bffEndpoint, campusMCPPaths["campus_student"])
}

func campusMCPKindForURL(target, bffEndpoint string) string {
	for kind, path := range campusMCPPaths {
		if isExactCampusMCPURL(target, bffEndpoint, path) {
			return kind
		}
	}
	return ""
}

func isExactCampusMCPURL(target, bffEndpoint, path string) bool {
	allowedBase, err := campusMCPBFFBaseURL(bffEndpoint)
	if err != nil {
		return false
	}
	allowed, err := url.JoinPath(allowedBase, path)
	if err != nil {
		return false
	}
	targetURL, err := url.Parse(target)
	if err != nil {
		return false
	}
	allowedURL, err := url.Parse(allowed)
	if err != nil {
		return false
	}
	return exactMCPURL(targetURL, allowedURL)
}

func campusMCPBFFBaseURL(bffEndpoint string) (string, error) {
	u, err := url.Parse(bffEndpoint)
	if err != nil || u.Scheme == "" || u.Hostname() == "" {
		return "", fmt.Errorf("invalid bff endpoint")
	}
	// BFF callback traffic uses 6668; Campus HTTP routes are served on 6667.
	if u.Port() == "6668" {
		u.Host = u.Hostname() + ":6667"
	}
	u.Path, u.RawQuery, u.Fragment = "", "", ""
	return strings.TrimRight(u.String(), "/"), nil
}

func exactMCPURL(target, allowed *url.URL) bool {
	return allowed.Scheme != "" && allowed.Hostname() != "" &&
		allowed.User == nil && allowed.RawQuery == "" && !allowed.ForceQuery && allowed.Fragment == "" && allowed.Opaque == "" &&
		target.Scheme == allowed.Scheme &&
		target.Hostname() == allowed.Hostname() &&
		target.Port() == allowed.Port() &&
		target.EscapedPath() == allowed.EscapedPath() &&
		target.User == nil && target.RawQuery == "" && !target.ForceQuery && target.Fragment == "" && target.Opaque == ""
}

func withoutIdentityHeaders(headers map[string]string) map[string]string {
	filtered := make(map[string]string, len(headers))
	for key, value := range headers {
		switch strings.ToLower(key) {
		case "authorization", "x-org-id", "x-user-id", "x-student-id", "x-role", "x-preview-role":
			continue
		}
		filtered[key] = value
	}
	return filtered
}

func mcpServerLogLabel(info *request.MCPToolInfo) string {
	kind := "mcp"
	if config.GetConfig().BffServer != nil {
		if campusKind := campusMCPKindForURL(info.URL, config.GetConfig().BffServer.Endpoint); campusKind != "" {
			kind = campusKind
		}
	}
	endpoint := "invalid"
	if parsed, err := url.Parse(info.URL); err == nil && parsed.Scheme != "" && parsed.Host != "" {
		endpoint = parsed.Scheme + "://" + parsed.Host + parsed.EscapedPath()
	}
	return fmt.Sprintf("id=%s transport=%s endpoint=%s tools=%v", kind, info.Transport, endpoint, info.ToolNameList)
}
