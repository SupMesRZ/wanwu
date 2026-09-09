package service

import (
	"context"
	"errors"
	"strings"
	"time"

	"github.com/UnicomAI/wanwu/internal/bff-service/model/response"
	"github.com/UnicomAI/wanwu/pkg/log"
)

type CampusBusinessExecutionIdentity struct {
	UserID     string `json:"userId"`
	OrgID      string `json:"orgId"`
	ActualRole string `json:"actualRole"`
}

const CampusBusinessActualRoleKey = "campus_business_actual_role"

type campusBusinessExecutionIdentityKey struct{}

type campusTeacherProfile struct {
	TeacherID string `json:"teacherId"`
	Name      string `json:"name"`
	College   string `json:"college"`
	Title     string `json:"title"`
}

type campusTeachingSchedule struct {
	CourseID    string `json:"courseId"`
	Course      string `json:"course"`
	ClassName   string `json:"className"`
	Weekday     int    `json:"weekday"`
	WeekdayName string `json:"weekdayName"`
	Date        string `json:"date,omitempty"`
	Periods     string `json:"periods"`
	StartTime   string `json:"startTime"`
	EndTime     string `json:"endTime"`
	Location    string `json:"location"`
}

type campusTeachingClass struct {
	CourseID  string `json:"courseId"`
	Course    string `json:"course"`
	ClassName string `json:"className"`
	Students  int    `json:"students"`
}

type campusInvigilation struct {
	Exam      string `json:"exam"`
	Date      string `json:"date"`
	StartTime string `json:"startTime"`
	EndTime   string `json:"endTime"`
	Location  string `json:"location"`
	Role      string `json:"role"`
}

type campusClassroom struct {
	Room      string `json:"room"`
	Building  string `json:"building"`
	Capacity  int    `json:"capacity"`
	Available bool   `json:"available"`
}

type campusAdjustmentRequest struct {
	ID           uint64 `json:"-" gorm:"primaryKey"`
	RequestID    string `json:"requestId" gorm:"index"`
	OrgID        string `json:"-"`
	TeacherID    string `json:"teacherId"`
	TeacherName  string `json:"teacherName"`
	CourseID     string `json:"courseId"`
	Course       string `json:"course"`
	ClassName    string `json:"className"`
	OriginalTime string `json:"originalTime"`
	TargetTime   string `json:"targetTime"`
	TargetRoom   string `json:"targetRoom"`
	Reason       string `json:"reason"`
	Status       string `json:"status"`
	StatusText   string `json:"statusText"`
	Remark       string `json:"remark,omitempty"`
	ReviewerID   string `json:"reviewerId,omitempty"`
	CreatedAt    string `json:"createdAt"`
	ReviewedAt   string `json:"reviewedAt,omitempty"`
}

type campusTeacherData struct {
	Profile          campusTeacherProfile     `json:"profile"`
	TeachingSchedule []campusTeachingSchedule `json:"teachingSchedule"`
	TeachingClasses  []campusTeachingClass    `json:"teachingClasses"`
	Invigilation     []campusInvigilation     `json:"invigilation"`
}

type campusAcademicData struct {
	CourseOperation         map[string]any   `json:"courseOperation"`
	RoomUtilization         []map[string]any `json:"roomUtilization"`
	GradeSubmissionProgress map[string]any   `json:"gradeSubmissionProgress"`
	ServiceStatistics       map[string]any   `json:"serviceStatistics"`
}

func WithCampusBusinessExecutionIdentity(ctx context.Context, userID, orgID string, actualRole ...string) context.Context {
	role := ""
	if len(actualRole) > 0 {
		role = strings.TrimSpace(actualRole[0])
	}
	return context.WithValue(ctx, campusBusinessExecutionIdentityKey{}, CampusBusinessExecutionIdentity{UserID: userID, OrgID: orgID, ActualRole: role})
}

func CampusBusinessExecutionIdentityFromContext(ctx context.Context) (CampusBusinessExecutionIdentity, bool) {
	identity, ok := ctx.Value(campusBusinessExecutionIdentityKey{}).(CampusBusinessExecutionIdentity)
	return identity, ok && identity.UserID != "" && identity.OrgID != ""
}

func CampusWorkflowExecutionIdentityFromContext(ctx context.Context) (CampusBusinessExecutionIdentity, bool) {
	identity, ok := CampusBusinessExecutionIdentityFromContext(ctx)
	return identity, ok && identity.ActualRole != ""
}

func CampusWorkflowRole(permission *response.UserPermission) (string, error) {
	if permission == nil || permission.OrgPermission.IsAdmin || permission.OrgPermission.IsSystem {
		return "", errors.New("campus workflow role unavailable")
	}
	role, status := ResolveActualCampusRole(permission.OrgPermission.Roles)
	if status != CampusRoleResolved {
		return "", errors.New("campus workflow role is " + status)
	}
	return role, nil
}

func ValidateCampusBusinessRole(permission *response.UserPermission, required string) error {
	if permission == nil {
		return errors.New("campus " + required + " role required")
	}
	if permission.OrgPermission.IsAdmin || permission.OrgPermission.IsSystem {
		return nil
	}
	role, status := ResolveActualCampusRole(permission.OrgPermission.Roles)
	if status != CampusRoleResolved {
		return errors.New("campus role is " + status)
	}
	if role != required {
		return errors.New("campus " + required + " role required")
	}
	return nil
}

func buildCampusTeacherData(identity CampusBusinessExecutionIdentity, now time.Time) campusTeacherData {
	profile := campusTeacherProfile{TeacherID: identity.UserID, Name: "刘老师", College: "数学与信息科学学院", Title: "副教授"}
	schedule := []campusTeachingSchedule{
		{CourseID: "MA202-A", Course: "高等数学", ClassName: "计算机科学与技术2024-1班", Weekday: 1, WeekdayName: "周一", Periods: "第1—2节", StartTime: "08:00", EndTime: "09:40", Location: "综合楼A205"},
		{CourseID: "MA202-B", Course: "高等数学", ClassName: "软件工程2024-2班", Weekday: 3, WeekdayName: "周三", Periods: "第5—6节", StartTime: "14:00", EndTime: "15:40", Location: "综合楼B204"},
		{CourseID: "MA310", Course: "数学建模", ClassName: "数学与应用数学2023-1班", Weekday: 4, WeekdayName: "周四", Periods: "第3—4节", StartTime: "10:10", EndTime: "11:50", Location: "理科楼201"},
	}
	classes := []campusTeachingClass{
		{CourseID: "MA202-A", Course: "高等数学", ClassName: "计算机科学与技术2024-1班", Students: 46},
		{CourseID: "MA202-B", Course: "高等数学", ClassName: "软件工程2024-2班", Students: 43},
		{CourseID: "MA310", Course: "数学建模", ClassName: "数学与应用数学2023-1班", Students: 38},
	}
	invigilationDate := nextWeekday(now, time.Saturday)
	return campusTeacherData{
		Profile: profile, TeachingSchedule: schedule, TeachingClasses: classes,
		Invigilation: []campusInvigilation{{Exam: "大学英语四级模拟考试", Date: invigilationDate.Format("2006-01-02"), StartTime: "09:00", EndTime: "11:00", Location: "综合楼A301", Role: "主监考"}},
	}
}

func buildCampusAcademicData() campusAcademicData {
	return campusAcademicData{
		CourseOperation:         map[string]any{"term": "2026-2027-1", "runningCourses": 1286, "normalRate": 98.6, "adjustedCourses": 3, "alerts": 2, "summary": "本周课程整体运行平稳"},
		RoomUtilization:         []map[string]any{{"building": "综合楼", "rooms": 86, "utilizationRate": 78.4}, {"building": "逸夫楼", "rooms": 42, "utilizationRate": 72.1}, {"building": "理科楼", "rooms": 36, "utilizationRate": 69.8}},
		GradeSubmissionProgress: map[string]any{"totalCourses": 428, "submitted": 396, "pending": 32, "completionRate": 92.5},
		ServiceStatistics:       map[string]any{"period": "本周", "requests": 1286, "completed": 1268, "pending": 18, "averageMinutes": 16.8, "satisfactionRate": 97.2},
	}
}

func campusAdjustmentRecords(identity CampusBusinessExecutionIdentity) ([]campusAdjustmentRequest, error) {
	return adjustmentStore.TeacherRecords(identity.UserID)
}

func campusAdjustmentHistory(status string) ([]campusAdjustmentRequest, error) {
	return adjustmentStore.History(status)
}

func createCampusAdjustment(identity CampusBusinessExecutionIdentity, course, originalTime, targetTime, targetRoom, reason string) (campusAdjustmentRequest, *campusToolError) {
	data := buildCampusTeacherData(identity, time.Now().In(campusLocation))
	var owned *campusTeachingSchedule
	for i := range data.TeachingSchedule {
		if strings.EqualFold(data.TeachingSchedule[i].CourseID, course) || data.TeachingSchedule[i].Course == course {
			owned = &data.TeachingSchedule[i]
			if strings.Contains(originalTime, data.TeachingSchedule[i].WeekdayName) {
				break
			}
		}
	}
	if owned == nil {
		return campusAdjustmentRequest{}, &campusToolError{Code: "course_not_owned", Message: "The course does not belong to the current teacher"}
	}
	if strings.TrimSpace(targetTime) == "" || targetTime == originalTime {
		return campusAdjustmentRequest{}, &campusToolError{Code: "invalid_target_time", Message: "Target time must be complete and different from original time"}
	}
	for _, lesson := range data.TeachingSchedule {
		if lesson.CourseID != owned.CourseID && strings.Contains(targetTime, lesson.WeekdayName) && strings.Contains(targetTime, periodOfDay(lesson.StartTime)) {
			return campusAdjustmentRequest{}, &campusToolError{Code: "teacher_time_conflict", Message: "The teacher already has a class at the target time"}
		}
	}
	if targetRoom == "" {
		targetRoom = "综合楼C301"
	}
	if !campusClassroomAvailable(targetRoom) {
		return campusAdjustmentRequest{}, &campusToolError{Code: "classroom_conflict", Message: "The target classroom is unavailable"}
	}

	request := campusAdjustmentRequest{
		OrgID:     identity.OrgID,
		TeacherID: identity.UserID, TeacherName: data.Profile.Name, CourseID: owned.CourseID, Course: owned.Course, ClassName: owned.ClassName,
		OriginalTime: originalTime, TargetTime: targetTime, TargetRoom: targetRoom, Reason: reason,
		Status: "pending", StatusText: "待审批", CreatedAt: time.Now().In(campusLocation).Format("2006-01-02 15:04"),
	}
	request, err := adjustmentStore.Create(request)
	if err != nil {
		return campusAdjustmentRequest{}, campusAdjustmentPersistenceError("create", err)
	}
	return request, nil
}

func reviewCampusAdjustment(identity CampusBusinessExecutionIdentity, requestID, decision, remark string) (campusAdjustmentRequest, *campusToolError) {
	if decision != "approve" && decision != "reject" {
		return campusAdjustmentRequest{}, invalidCampusToolArguments()
	}
	item, err := adjustmentStore.Find(requestID)
	if errors.Is(err, errCampusAdjustmentNotFound) {
		return campusAdjustmentRequest{}, &campusToolError{Code: "request_not_found", Message: "Adjustment request not found"}
	}
	if err != nil {
		return campusAdjustmentRequest{}, campusAdjustmentPersistenceError("find", err)
	}
	if item.Status != "pending" {
		return campusAdjustmentRequest{}, &campusToolError{Code: "request_not_pending", Message: "The adjustment request is no longer pending"}
	}
	if decision == "approve" && !campusClassroomAvailable(item.TargetRoom) {
		return campusAdjustmentRequest{}, &campusToolError{Code: "final_classroom_conflict", Message: "The target classroom is no longer available"}
	}
	status := map[string]string{"approve": "approved", "reject": "rejected"}[decision]
	statusText := map[string]string{"approve": "已批准", "reject": "已驳回"}[decision]
	item, err = adjustmentStore.ReviewPending(requestID, status, statusText, remark, identity.UserID, time.Now().In(campusLocation).Format("2006-01-02 15:04"))
	if errors.Is(err, errCampusAdjustmentNotPending) {
		return campusAdjustmentRequest{}, &campusToolError{Code: "request_not_pending", Message: "The adjustment request is no longer pending"}
	}
	if err != nil {
		return campusAdjustmentRequest{}, campusAdjustmentPersistenceError("review", err)
	}
	return item, nil
}

func campusAdjustmentPersistenceError(operation string, err error) *campusToolError {
	log.Errorf("campus adjustment %s failed: %v", operation, err)
	return &campusToolError{Code: "internal_error", Message: "Campus adjustment service is temporarily unavailable"}
}

func nextWeekday(from time.Time, weekday time.Weekday) time.Time {
	days := (int(weekday) - int(from.Weekday()) + 7) % 7
	if days == 0 {
		days = 7
	}
	return from.AddDate(0, 0, days)
}

func periodOfDay(start string) string {
	if start < "12:00" {
		return "上午"
	}
	return "下午"
}

func campusClassroomAvailable(room string) bool {
	normalized := strings.ReplaceAll(strings.TrimSpace(room), " ", "")
	switch normalized {
	case "C301", "综合楼C301", "A204", "逸夫楼A204", "201", "理科楼201":
		return true
	default:
		return false
	}
}
