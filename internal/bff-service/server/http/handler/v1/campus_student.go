package v1

import (
	"errors"
	"net/http"

	"github.com/UnicomAI/wanwu/internal/bff-service/service"
	gin_util "github.com/UnicomAI/wanwu/pkg/gin-util"
	"github.com/gin-gonic/gin"
)

func GetCampusStudentSummary(ctx *gin.Context) {
	gin_util.Response(ctx, service.GetCampusStudentSummary(getOrgID(ctx), getUserID(ctx)), nil)
}

func GetCampusStudentCourses(ctx *gin.Context) {
	gin_util.Response(ctx, service.GetCampusStudentCourses(getOrgID(ctx), getUserID(ctx)), nil)
}

func GetCampusStudentTodayCourses(ctx *gin.Context) {
	gin_util.Response(ctx, service.GetCampusStudentTodayCourses(getOrgID(ctx), getUserID(ctx)), nil)
}

func GetCampusStudentWeekCourses(ctx *gin.Context) {
	gin_util.Response(ctx, service.GetCampusStudentWeekCourses(getOrgID(ctx), getUserID(ctx)), nil)
}

func GetCampusStudentExams(ctx *gin.Context) {
	gin_util.Response(ctx, service.GetCampusStudentExams(getOrgID(ctx), getUserID(ctx)), nil)
}

func GetCampusStudentScores(ctx *gin.Context) {
	gin_util.Response(ctx, service.GetCampusStudentScores(getOrgID(ctx), getUserID(ctx), ctx.Query("term")), nil)
}

func GetCampusStudentTermOverview(ctx *gin.Context) {
	gin_util.Response(ctx, service.GetCampusStudentTermOverview(getOrgID(ctx), getUserID(ctx), ctx.Query("term")), nil)
}

func GetCampusStudentLearningAnalysis(ctx *gin.Context) {
	gin_util.Response(ctx, service.GetCampusStudentLearningAnalysis(getOrgID(ctx), getUserID(ctx), ctx.Query("term")), nil)
}

func GetCampusStudentLeaveRecords(ctx *gin.Context) {
	gin_util.Response(ctx, service.GetCampusStudentLeaveRecords(getOrgID(ctx), getUserID(ctx)), nil)
}

func HandleCampusStudentMCP(ctx *gin.Context) {
	if err := service.ServeCampusStudentMCP(ctx.Writer, ctx.Request); err != nil {
		gin_util.ResponseErrWithStatus(ctx, http.StatusInternalServerError, err)
	}
}

func GetCampusStudentMCPIdentity(ctx *gin.Context) {
	identity, ok := service.CampusStudentExecutionIdentityFromContext(ctx.Request.Context())
	if !ok {
		gin_util.ResponseErrWithStatus(ctx, http.StatusForbidden, errors.New("campus student execution identity unavailable"))
		return
	}
	gin_util.Response(ctx, identity, nil)
}
