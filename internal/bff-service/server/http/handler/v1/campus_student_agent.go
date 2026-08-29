package v1

import (
	"github.com/UnicomAI/wanwu/internal/bff-service/model/request"
	"github.com/UnicomAI/wanwu/internal/bff-service/service"
	gin_util "github.com/UnicomAI/wanwu/pkg/gin-util"
	"github.com/gin-gonic/gin"
)

func GetCampusStudentAssistant(ctx *gin.Context) {
	resp, err := service.GetCampusStudentAssistant(ctx)
	gin_util.Response(ctx, resp, err)
}

func GetCampusStudentAssistantBinding(ctx *gin.Context) {
	resp, err := service.GetCampusStudentAssistantBinding(ctx.Request.Context())
	gin_util.Response(ctx, resp, err)
}

func CreateCampusStudentAssistantConversation(ctx *gin.Context) {
	var req request.CampusStudentAssistantConversationReq
	if !gin_util.Bind(ctx, &req) {
		return
	}
	resp, err := service.CreateCampusStudentAssistantConversation(ctx, getUserID(ctx), getOrgID(ctx), req)
	gin_util.Response(ctx, resp, err)
}

func CampusStudentAssistantChat(ctx *gin.Context) {
	var req request.CampusStudentAssistantChatReq
	if !gin_util.Bind(ctx, &req) {
		return
	}
	if err := service.CampusStudentAssistantChat(ctx, getUserID(ctx), getOrgID(ctx), getClientID(ctx), req); err != nil {
		gin_util.Response(ctx, nil, err)
	}
}
