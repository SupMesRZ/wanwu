package service

import (
	"fmt"
	"mime/multipart"
	"path/filepath"
	"strings"

	app_service "github.com/UnicomAI/wanwu/api/proto/app-service"
	errs "github.com/UnicomAI/wanwu/api/proto/err-code"
	"github.com/UnicomAI/wanwu/internal/bff-service/model/request"
	"github.com/UnicomAI/wanwu/internal/bff-service/model/response"
	grpc_util "github.com/UnicomAI/wanwu/pkg/grpc-util"
	"github.com/UnicomAI/wanwu/pkg/minio"
	"github.com/UnicomAI/wanwu/pkg/util"
	"github.com/gin-gonic/gin"
	"github.com/xuri/excelize/v2"
)

const (
	PublicOpinionMaxFileSize      int64 = 5 * 1024 * 1024
	PublicOpinionMaxRequestSize   int64 = 6 * 1024 * 1024
	publicOpinionTemplateFileName       = "校园舆情导入模板.xlsx"
)

var publicOpinionSourceTypes = []string{
	"学校公开网站", "公开新闻", "公开论坛", "公开社交平台", "公开评论区", "校园服务反馈", "授权数据",
}

func GeneratePublicOpinionTemplate() (string, []byte, error) {
	f := excelize.NewFile()
	defer func() { _ = f.Close() }()
	const dataSheet = "舆情数据"
	if err := f.SetSheetName("Sheet1", dataSheet); err != nil {
		return "", nil, publicOpinionBFFError(errs.Code_BFFGeneral, "生成模板工作表失败: %v", err)
	}
	headers := []interface{}{"标题*", "正文*", "内容摘要", "来源名称*", "来源类型*", "公开链接", "发布时间*", "主题", "备注"}
	if err := f.SetSheetRow(dataSheet, "A1", &headers); err != nil {
		return "", nil, publicOpinionBFFError(errs.Code_BFFGeneral, "生成模板表头失败: %v", err)
	}
	if err := f.SetColWidth(dataSheet, "A", "A", 28); err != nil {
		return "", nil, err
	}
	if err := f.SetColWidth(dataSheet, "B", "C", 45); err != nil {
		return "", nil, err
	}
	if err := f.SetColWidth(dataSheet, "D", "I", 22); err != nil {
		return "", nil, err
	}
	dv := excelize.NewDataValidation(true)
	dv.Sqref = "E2:E1001"
	dv.SetDropList(publicOpinionSourceTypes)
	if err := f.AddDataValidation(dataSheet, dv); err != nil {
		return "", nil, publicOpinionBFFError(errs.Code_BFFGeneral, "生成来源类型下拉失败: %v", err)
	}
	if err := f.SetPanes(dataSheet, &excelize.Panes{Freeze: true, YSplit: 1, TopLeftCell: "A2", ActivePane: "bottomLeft"}); err != nil {
		return "", nil, err
	}
	const instructionSheet = "填写说明"
	if _, err := f.NewSheet(instructionSheet); err != nil {
		return "", nil, publicOpinionBFFError(errs.Code_BFFGeneral, "生成填写说明失败: %v", err)
	}
	instructions := [][]interface{}{
		{"项目", "说明"},
		{"必填字段", "标题、正文、来源名称、来源类型、发布时间"},
		{"发布时间", "格式：YYYY-MM-DD HH:mm:ss"},
		{"公开链接", "除“授权数据”外必须填写有效的 HTTP/HTTPS 公开链接"},
		{"授权数据", "来源类型为“授权数据”时，备注必须说明数据来源或授权情况"},
		{"数据行数", "单次最多 1000 条非空业务行"},
	}
	for i, row := range instructions {
		cell := fmt.Sprintf("A%d", i+1)
		if err := f.SetSheetRow(instructionSheet, cell, &row); err != nil {
			return "", nil, err
		}
	}
	_ = f.SetColWidth(instructionSheet, "A", "A", 18)
	_ = f.SetColWidth(instructionSheet, "B", "B", 80)
	buffer, err := f.WriteToBuffer()
	if err != nil {
		return "", nil, publicOpinionBFFError(errs.Code_BFFGeneral, "输出模板失败: %v", err)
	}
	return publicOpinionTemplateFileName, buffer.Bytes(), nil
}

func ImportPublicOpinion(ctx *gin.Context, creatorID, orgID string, fileHeader *multipart.FileHeader) (*response.PublicOpinionImportTask, error) {
	if fileHeader == nil {
		return nil, publicOpinionBFFError(errs.Code_BFFInvalidArg, "file 不能为空")
	}
	if fileHeader.Size <= 0 || fileHeader.Size > PublicOpinionMaxFileSize {
		return nil, publicOpinionBFFError(errs.Code_BFFInvalidArg, "文件大小必须大于 0 且不超过 5MB")
	}
	fileName := filepath.Base(strings.TrimSpace(fileHeader.Filename))
	ext := strings.ToLower(filepath.Ext(fileName))
	if ext != ".xlsx" && ext != ".csv" {
		return nil, publicOpinionBFFError(errs.Code_BFFInvalidArg, "仅支持 .xlsx 和 .csv 文件")
	}
	file, err := fileHeader.Open()
	if err != nil {
		return nil, publicOpinionBFFError(errs.Code_BFFInvalidArg, "打开上传文件失败: %v", err)
	}
	defer func() { _ = file.Close() }()
	storedName, uploadedSize, err := minio.UploadFileCommonWithExpire(ctx.Request.Context(), file, ext, fileHeader.Size)
	if err != nil {
		return nil, publicOpinionBFFError(errs.Code_BFFGeneral, "上传导入文件到内部对象存储失败: %v", err)
	}
	fileRef, err := minio.GetUploadFileWithExpire(ctx.Request.Context(), storedName)
	if err != nil {
		return nil, publicOpinionBFFError(errs.Code_BFFGeneral, "获取内部对象存储文件引用失败: %v", err)
	}
	ret, err := app.ImportPublicOpinion(ctx.Request.Context(), &app_service.ImportPublicOpinionReq{
		OrgId:     orgID,
		CreatorId: creatorID,
		FileName:  fileName,
		FileType:  ext,
		FileSize:  uploadedSize,
		FilePath:  fileRef,
	})
	if err != nil {
		return nil, err
	}
	return toPublicOpinionImportTask(ret), nil
}

func GetPublicOpinionImportTask(ctx *gin.Context, orgID, taskID string) (*response.PublicOpinionImportTask, error) {
	ret, err := app.GetPublicOpinionImportTask(ctx.Request.Context(), &app_service.GetPublicOpinionImportTaskReq{TaskId: taskID, OrgId: orgID})
	if err != nil {
		return nil, err
	}
	return toPublicOpinionImportTask(ret), nil
}

func ListPublicOpinionItems(ctx *gin.Context, orgID string, req *request.PublicOpinionListReq) (*response.PageResult, error) {
	startTime, err := parsePublicOpinionHTTPTime("startTime", req.StartTime)
	if err != nil {
		return nil, err
	}
	endTime, err := parsePublicOpinionHTTPTime("endTime", req.EndTime)
	if err != nil {
		return nil, err
	}
	if startTime > 0 && endTime > 0 && startTime > endTime {
		return nil, publicOpinionBFFError(errs.Code_BFFInvalidArg, "startTime 不能晚于 endTime")
	}
	ret, err := app.ListPublicOpinionItems(ctx.Request.Context(), &app_service.ListPublicOpinionItemsReq{
		OrgId: orgID, Keyword: req.Keyword, StartTime: startTime, EndTime: endTime,
		SourceType: req.SourceType, Topic: req.Topic, PageNo: req.PageNo, PageSize: req.PageSize,
	})
	if err != nil {
		return nil, err
	}
	items := make([]*response.PublicOpinionItem, 0, len(ret.Items))
	for _, item := range ret.Items {
		items = append(items, toPublicOpinionItem(item))
	}
	return &response.PageResult{List: items, Total: int64(ret.Total), PageNo: int(ret.PageNo), PageSize: int(ret.PageSize)}, nil
}

func GetPublicOpinionItem(ctx *gin.Context, orgID, itemID string) (*response.PublicOpinionItem, error) {
	ret, err := app.GetPublicOpinionItem(ctx.Request.Context(), &app_service.GetPublicOpinionItemReq{ItemId: itemID, OrgId: orgID})
	if err != nil {
		return nil, err
	}
	return toPublicOpinionItem(ret), nil
}

func parsePublicOpinionHTTPTime(field, value string) (int64, error) {
	value = strings.TrimSpace(value)
	if value == "" {
		return 0, nil
	}
	ret, err := util.Str2Time(value)
	if err != nil {
		return 0, publicOpinionBFFError(errs.Code_BFFInvalidArg, "%s 格式必须为 YYYY-MM-DD HH:mm:ss", field)
	}
	return ret, nil
}

func toPublicOpinionImportTask(task *app_service.OpinionImportTaskInfo) *response.PublicOpinionImportTask {
	return &response.PublicOpinionImportTask{
		TaskID: task.TaskId, CreatedAt: formatPublicOpinionTime(task.CreatedAt), UpdatedAt: formatPublicOpinionTime(task.UpdatedAt),
		FileName: task.FileName, FileType: task.FileType, FileSize: task.FileSize, FileHash: task.FileHash, Status: task.Status,
		TotalRows: task.TotalRows, SuccessRows: task.SuccessRows, DuplicateRows: task.DuplicateRows, FailedRows: task.FailedRows,
		ErrorDetail: task.ErrorDetail, StartedAt: formatPublicOpinionTime(task.StartedAt), FinishedAt: formatPublicOpinionTime(task.FinishedAt),
	}
}

func toPublicOpinionItem(item *app_service.PublicOpinionItemInfo) *response.PublicOpinionItem {
	return &response.PublicOpinionItem{
		ItemID: item.ItemId, CreatedAt: formatPublicOpinionTime(item.CreatedAt), UpdatedAt: formatPublicOpinionTime(item.UpdatedAt),
		CreatorID: item.CreatorId, ImportTaskID: item.ImportTaskId, Title: item.Title, Content: item.Content, Summary: item.Summary,
		SourceName: item.SourceName, SourceType: item.SourceType, PublicURL: item.PublicUrl,
		PublishedAt: formatPublicOpinionTime(item.PublishedAt), CollectedAt: formatPublicOpinionTime(item.CollectedAt), Topic: item.Topic, Remark: item.Remark,
	}
}

func formatPublicOpinionTime(value int64) string {
	if value <= 0 {
		return ""
	}
	return util.Time2Str(value)
}

func publicOpinionBFFError(code errs.Code, format string, args ...interface{}) error {
	return grpc_util.ErrorStatus(code, fmt.Sprintf(format, args...))
}
