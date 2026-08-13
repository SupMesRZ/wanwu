package pkg

import (
	"encoding/csv"
	"fmt"
	"net/url"
	"os"
	"path/filepath"
	"strconv"
	"strings"
	"time"
	"unicode"
	"unicode/utf8"

	"github.com/UnicomAI/wanwu/pkg/util"
	"github.com/xuri/excelize/v2"
	"golang.org/x/text/width"
)

const MaxPublicOpinionRows = 1000

var publicOpinionHeaders = []string{
	"标题", "正文", "内容摘要", "来源名称", "来源类型", "公开链接", "发布时间", "主题", "备注",
}

var publicOpinionSourceTypes = map[string]struct{}{
	"学校公开网站": {},
	"公开新闻":   {},
	"公开论坛":   {},
	"公开社交平台": {},
	"公开评论区":  {},
	"校园服务反馈": {},
	"授权数据":   {},
}

type PublicOpinionRow struct {
	Row         int
	Title       string
	Content     string
	Summary     string
	SourceName  string
	SourceType  string
	PublicURL   string
	PublishedAt int64
	Topic       string
	Remark      string
}

type PublicOpinionRowError struct {
	Row    int    `json:"row"`
	Field  string `json:"field"`
	Reason string `json:"reason"`
}

// ParsePublicOpinionFile reads an internal trusted local file path. It never downloads remote content.
func ParsePublicOpinionFile(filePath, fileName, fileType string) ([]PublicOpinionRow, []PublicOpinionRowError, error) {
	typ, err := resolvePublicOpinionFileType(fileName, fileType)
	if err != nil {
		return nil, nil, err
	}

	var rows [][]string
	var date1904 bool
	switch typ {
	case ".csv":
		rows, err = readPublicOpinionCSV(filePath)
	case ".xlsx":
		rows, date1904, err = readPublicOpinionXLSX(filePath)
	}
	if err != nil {
		return nil, nil, err
	}
	if len(rows) == 0 {
		return nil, nil, fmt.Errorf("导入文件为空")
	}

	header, err := parsePublicOpinionHeader(rows[0])
	if err != nil {
		return nil, nil, err
	}

	result := make([]PublicOpinionRow, 0, len(rows)-1)
	errors := make([]PublicOpinionRowError, 0)
	nonEmptyRows := 0
	for i, raw := range rows[1:] {
		if isPublicOpinionBlankRow(raw) {
			continue
		}
		nonEmptyRows++
		if nonEmptyRows > MaxPublicOpinionRows {
			return nil, nil, fmt.Errorf("单次最多处理 %d 条非空业务行", MaxPublicOpinionRows)
		}
		row, rowErrors := validatePublicOpinionRow(i+2, raw, header, typ == ".xlsx", date1904)
		if len(rowErrors) > 0 {
			errors = append(errors, rowErrors...)
			continue
		}
		result = append(result, row)
	}
	return result, errors, nil
}

func NormalizePublicOpinionText(value string) string {
	value = strings.TrimSpace(value)
	value = strings.ReplaceAll(value, "\r\n", "\n")
	value = strings.ReplaceAll(value, "\r", "\n")
	value = width.Fold.String(value)
	var b strings.Builder
	wasSpace := false
	for _, r := range value {
		if unicode.IsSpace(r) {
			if !wasSpace {
				b.WriteByte(' ')
				wasSpace = true
			}
			continue
		}
		wasSpace = false
		b.WriteRune(r)
	}
	return strings.TrimSpace(b.String())
}

func resolvePublicOpinionFileType(fileName, fileType string) (string, error) {
	ext := strings.ToLower(filepath.Ext(strings.TrimSpace(fileName)))
	typ := strings.ToLower(strings.TrimSpace(fileType))
	if typ != "" && !strings.HasPrefix(typ, ".") {
		typ = "." + typ
	}
	if typ == "" {
		typ = ext
	}
	if typ != ".xlsx" && typ != ".csv" {
		return "", fmt.Errorf("不支持的文件类型 %q，仅支持 .xlsx 和 .csv", typ)
	}
	if ext != "" && ext != typ {
		return "", fmt.Errorf("文件扩展名 %q 与文件类型 %q 不一致", ext, typ)
	}
	return typ, nil
}

func readPublicOpinionCSV(filePath string) ([][]string, error) {
	f, err := os.Open(filePath)
	if err != nil {
		return nil, fmt.Errorf("读取 CSV 文件失败: %w", err)
	}
	defer func() { _ = f.Close() }()
	r := csv.NewReader(f)
	r.FieldsPerRecord = -1
	rows, err := r.ReadAll()
	if err != nil {
		return nil, fmt.Errorf("解析 CSV 文件失败: %w", err)
	}
	return rows, nil
}

func readPublicOpinionXLSX(filePath string) ([][]string, bool, error) {
	f, err := excelize.OpenFile(filePath)
	if err != nil {
		return nil, false, fmt.Errorf("读取 XLSX 文件失败: %w", err)
	}
	defer func() { _ = f.Close() }()
	sheets := f.GetSheetList()
	if len(sheets) == 0 {
		return nil, false, fmt.Errorf("XLSX 文件没有工作表")
	}
	rows, err := f.GetRows(sheets[0], excelize.Options{RawCellValue: true})
	if err != nil {
		return nil, false, fmt.Errorf("解析 XLSX 文件失败: %w", err)
	}
	props, err := f.GetWorkbookProps()
	if err != nil {
		return nil, false, fmt.Errorf("读取 XLSX 日期属性失败: %w", err)
	}
	date1904 := props.Date1904 != nil && *props.Date1904
	return rows, date1904, nil
}

func parsePublicOpinionHeader(row []string) (map[string]int, error) {
	positions := make(map[string]int, len(publicOpinionHeaders))
	for i, value := range row {
		name := strings.TrimPrefix(strings.TrimSpace(strings.TrimSuffix(strings.TrimSpace(value), "*")), "\ufeff")
		for _, expected := range publicOpinionHeaders {
			if name == expected {
				if _, exists := positions[expected]; exists {
					return nil, fmt.Errorf("文件模板包含重复字段 %q", expected)
				}
				positions[expected] = i
			}
		}
	}
	for _, required := range []string{"标题", "正文", "来源名称", "来源类型", "发布时间"} {
		if _, ok := positions[required]; !ok {
			return nil, fmt.Errorf("文件模板缺少必填字段 %q", required)
		}
	}
	return positions, nil
}

func validatePublicOpinionRow(rowNo int, raw []string, header map[string]int, excelDate, date1904 bool) (PublicOpinionRow, []PublicOpinionRowError) {
	get := func(name string) string {
		index, ok := header[name]
		if !ok || index >= len(raw) {
			return ""
		}
		return raw[index]
	}
	row := PublicOpinionRow{
		Row:        rowNo,
		Title:      NormalizePublicOpinionText(get("标题")),
		Content:    NormalizePublicOpinionText(get("正文")),
		Summary:    NormalizePublicOpinionText(get("内容摘要")),
		SourceName: NormalizePublicOpinionText(get("来源名称")),
		SourceType: NormalizePublicOpinionText(get("来源类型")),
		PublicURL:  strings.TrimSpace(get("公开链接")),
		Topic:      NormalizePublicOpinionText(get("主题")),
		Remark:     NormalizePublicOpinionText(get("备注")),
	}
	var errs []PublicOpinionRowError
	checkLength := func(field, value string, min, max int) {
		length := utf8.RuneCountInString(value)
		if length < min {
			errs = append(errs, PublicOpinionRowError{Row: rowNo, Field: field, Reason: "不能为空"})
		} else if length > max {
			errs = append(errs, PublicOpinionRowError{Row: rowNo, Field: field, Reason: fmt.Sprintf("最多 %d 个 Unicode 字符", max)})
		}
	}
	checkLength("标题", row.Title, 1, 500)
	checkLength("正文", row.Content, 1, 50000)
	checkLength("内容摘要", row.Summary, 0, 2000)
	checkLength("来源名称", row.SourceName, 1, 128)
	checkLength("公开链接", row.PublicURL, 0, 2048)
	checkLength("主题", row.Topic, 0, 100)
	checkLength("备注", row.Remark, 0, 1000)

	if row.SourceType == "" {
		errs = append(errs, PublicOpinionRowError{Row: rowNo, Field: "来源类型", Reason: "不能为空"})
	} else if _, ok := publicOpinionSourceTypes[row.SourceType]; !ok {
		errs = append(errs, PublicOpinionRowError{Row: rowNo, Field: "来源类型", Reason: "不在允许范围内"})
	}
	if row.SourceType == "授权数据" {
		if row.Remark == "" {
			errs = append(errs, PublicOpinionRowError{Row: rowNo, Field: "备注", Reason: "授权数据必须说明数据来源或授权情况"})
		}
	} else {
		if row.PublicURL == "" {
			errs = append(errs, PublicOpinionRowError{Row: rowNo, Field: "公开链接", Reason: "非授权数据必须填写公开链接"})
		} else if parsed, err := url.ParseRequestURI(row.PublicURL); err != nil || (parsed.Scheme != "http" && parsed.Scheme != "https") || parsed.Host == "" {
			errs = append(errs, PublicOpinionRowError{Row: rowNo, Field: "公开链接", Reason: "必须是有效的 HTTP 或 HTTPS URL"})
		}
	}

	publishedAt, err := parsePublicOpinionTime(strings.TrimSpace(get("发布时间")), excelDate, date1904)
	if err != nil {
		errs = append(errs, PublicOpinionRowError{Row: rowNo, Field: "发布时间", Reason: err.Error()})
	} else {
		row.PublishedAt = publishedAt
	}
	return row, errs
}

func parsePublicOpinionTime(value string, excelDate, date1904 bool) (int64, error) {
	if value == "" {
		return 0, fmt.Errorf("不能为空")
	}
	if excelDate {
		if serial, err := strconv.ParseFloat(value, 64); err == nil {
			t, err := excelize.ExcelDateToTime(serial, date1904)
			if err == nil {
				loc := util.UTC8
				if loc == nil {
					loc = time.FixedZone("Asia/Shanghai", 8*60*60)
				}
				return time.Date(t.Year(), t.Month(), t.Day(), t.Hour(), t.Minute(), t.Second(), 0, loc).UnixMilli(), nil
			}
		}
	}
	loc := util.UTC8
	if loc == nil {
		loc = time.FixedZone("Asia/Shanghai", 8*60*60)
	}
	if util.UTC8 != nil {
		if ts, err := util.Str2Time(value); err == nil {
			return ts, nil
		}
	} else if t, err := time.ParseInLocation("2006-01-02 15:04:05", value, loc); err == nil {
		return t.UnixMilli(), nil
	}
	if t, err := time.ParseInLocation("2006-01-02 15:04", value, loc); err == nil {
		return t.UnixMilli(), nil
	}
	if util.UTC8 != nil {
		if ts, err := util.Str2Date(value); err == nil {
			return ts, nil
		}
	} else if t, err := time.ParseInLocation("2006-01-02", value, loc); err == nil {
		return t.UnixMilli(), nil
	}
	return 0, fmt.Errorf("格式必须为 YYYY-MM-DD HH:mm:ss、YYYY-MM-DD HH:mm 或 YYYY-MM-DD")
}

func isPublicOpinionBlankRow(row []string) bool {
	for _, value := range row {
		if strings.TrimSpace(value) != "" {
			return false
		}
	}
	return true
}
