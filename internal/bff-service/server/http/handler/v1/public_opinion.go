package v1

import (
	"fmt"
	"net/http"
	"net/url"

	errs "github.com/UnicomAI/wanwu/api/proto/err-code"
	"github.com/UnicomAI/wanwu/internal/bff-service/model/request"
	"github.com/UnicomAI/wanwu/internal/bff-service/service"
	gin_util "github.com/UnicomAI/wanwu/pkg/gin-util"
	grpc_util "github.com/UnicomAI/wanwu/pkg/grpc-util"
	"github.com/gin-gonic/gin"
)

func DownloadPublicOpinionImportTemplate(ctx *gin.Context) {
	fileName, data, err := service.GeneratePublicOpinionTemplate()
	if err != nil {
		gin_util.Response(ctx, nil, err)
		return
	}
	ctx.Header("Content-Disposition", "attachment; filename*=utf-8''"+url.QueryEscape(fileName))
	ctx.Header("Access-Control-Expose-Headers", "Content-Disposition")
	ctx.Data(http.StatusOK, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", data)
}

func ImportPublicOpinion(ctx *gin.Context) {
	ctx.Request.Body = http.MaxBytesReader(ctx.Writer, ctx.Request.Body, service.PublicOpinionMaxRequestSize)
	fileHeader, err := ctx.FormFile("file")
	if err != nil {
		gin_util.Response(ctx, nil, grpc_util.ErrorStatus(errs.Code_BFFInvalidArg, fmt.Sprintf("读取 multipart 文件失败: %v", err)))
		return
	}
	resp, err := service.ImportPublicOpinion(ctx, getUserID(ctx), getOrgID(ctx), fileHeader)
	gin_util.Response(ctx, resp, err)
}

func GetPublicOpinionImportTask(ctx *gin.Context) {
	var req request.PublicOpinionIDReq
	if !gin_util.BindUri(ctx, &req) {
		return
	}
	resp, err := service.GetPublicOpinionImportTask(ctx, getOrgID(ctx), req.ID)
	gin_util.Response(ctx, resp, err)
}

func ListPublicOpinionItems(ctx *gin.Context) {
	var req request.PublicOpinionListReq
	if !gin_util.Bind(ctx, &req) {
		return
	}
	resp, err := service.ListPublicOpinionItems(ctx, getOrgID(ctx), &req)
	gin_util.Response(ctx, resp, err)
}

func GetPublicOpinionItem(ctx *gin.Context) {
	var req request.PublicOpinionIDReq
	if !gin_util.BindUri(ctx, &req) {
		return
	}
	resp, err := service.GetPublicOpinionItem(ctx, getOrgID(ctx), req.ID)
	gin_util.Response(ctx, resp, err)
}
