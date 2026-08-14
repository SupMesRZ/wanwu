package v1

import (
	"net/http"

	v1 "github.com/UnicomAI/wanwu/internal/bff-service/server/http/handler/v1"
	mid "github.com/UnicomAI/wanwu/pkg/gin-util/mid-wrap"
	"github.com/gin-gonic/gin"
)

func registerPublicOpinion(apiV1 *gin.RouterGroup) {
	mid.Sub("public_opinion.manage").Reg(apiV1, "/public-opinion/import/template", http.MethodGet, v1.DownloadPublicOpinionImportTemplate, "下载舆情导入模板")
	mid.Sub("public_opinion.manage").Reg(apiV1, "/public-opinion/import", http.MethodPost, v1.ImportPublicOpinion, "导入舆情文件")
	mid.Sub("public_opinion.manage").Reg(apiV1, "/public-opinion/import/:id", http.MethodGet, v1.GetPublicOpinionImportTask, "查询舆情导入任务")
	mid.Sub("public_opinion.view").Reg(apiV1, "/public-opinion/item/list", http.MethodPost, v1.ListPublicOpinionItems, "查询舆情列表")
	mid.Sub("public_opinion.view").Reg(apiV1, "/public-opinion/item/:id", http.MethodGet, v1.GetPublicOpinionItem, "查询舆情详情")
}
