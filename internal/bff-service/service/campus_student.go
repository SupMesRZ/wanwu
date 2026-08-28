package service

import (
	"fmt"
	"hash/fnv"
	"math"
	"sort"
	"time"

	"github.com/UnicomAI/wanwu/internal/bff-service/model/response"
)

const (
	CampusRoleStudent       = "student"
	CampusRoleTeacher       = "teacher"
	CampusRoleAcademicAdmin = "academic_admin"

	CampusRoleResolved     = "resolved"
	CampusRoleUnconfigured = "unconfigured"
	CampusRoleConflict     = "conflict"
)

var campusLocation = time.FixedZone("Asia/Shanghai", 8*60*60)

type campusStudentData struct {
	Profile      response.CampusStudentProfile
	Courses      []response.CampusStudentCourse
	Exams        []response.CampusStudentExam
	Scores       []response.CampusStudentScore
	Tasks        []response.CampusStudentTask
	LeaveRecords []response.CampusStudentLeaveRecord
}

// ResolveActualCampusRole resolves only the exact role names returned by IAM.
// Frontend previewRole is never part of this input and cannot grant API access.
func ResolveActualCampusRole(roles []response.RoleIDName) (string, string) {
	found := make(map[string]struct{}, 3)
	for _, role := range roles {
		switch role.Name {
		case CampusRoleStudent, CampusRoleTeacher, CampusRoleAcademicAdmin:
			found[role.Name] = struct{}{}
		}
	}
	if len(found) == 0 {
		return "", CampusRoleUnconfigured
	}
	if len(found) > 1 {
		return "", CampusRoleConflict
	}
	for role := range found {
		return role, CampusRoleResolved
	}
	return "", CampusRoleUnconfigured
}

func GetCampusStudentSummary(orgID, userID string) response.CampusStudentSummary {
	now := time.Now().In(campusLocation)
	data := buildCampusStudentData(orgID, userID, now)
	todayCourses := coursesForDay(data.Courses, now)
	currentScores := scoresForTerm(data.Scores, currentAcademicTerm(now))
	pendingTasks := 0
	for _, task := range data.Tasks {
		if task.Status == "pending" {
			pendingTasks++
		}
	}
	recentScores := append([]response.CampusStudentScore(nil), currentScores...)
	sort.Slice(recentScores, func(i, j int) bool { return recentScores[i].Score > recentScores[j].Score })
	if len(recentScores) > 3 {
		recentScores = recentScores[:3]
	}
	return response.CampusStudentSummary{
		Profile:      data.Profile,
		TodayCourses: todayCourses,
		Tasks:        data.Tasks,
		RecentScores: recentScores,
		Overview: response.CampusStudentOverview{
			CourseCount:      len(data.Courses),
			TodayCourseCount: len(todayCourses),
			PendingTaskCount: pendingTasks,
			AverageScore:     averageScore(currentScores),
		},
	}
}

func GetCampusStudentCourses(orgID, userID string) []response.CampusStudentCourse {
	data := buildCampusStudentData(orgID, userID, time.Now().In(campusLocation))
	return data.Courses
}

func GetCampusStudentTodayCourses(orgID, userID string) []response.CampusStudentCourse {
	return GetCampusStudentCoursesForDate(orgID, userID, time.Now().In(campusLocation))
}

func GetCampusStudentWeekCourses(orgID, userID string) []response.CampusStudentCourse {
	return GetCampusStudentCoursesForWeek(orgID, userID, time.Now().In(campusLocation))
}

func GetCampusStudentCoursesForDate(orgID, userID string, day time.Time) []response.CampusStudentCourse {
	data := buildCampusStudentData(orgID, userID, day.In(campusLocation))
	return coursesForDay(data.Courses, day.In(campusLocation))
}

func GetCampusStudentCoursesForWeek(orgID, userID string, day time.Time) []response.CampusStudentCourse {
	data := buildCampusStudentData(orgID, userID, day.In(campusLocation))
	return coursesForWeek(data.Courses, day.In(campusLocation))
}

func GetCampusStudentExams(orgID, userID string) []response.CampusStudentExam {
	data := buildCampusStudentData(orgID, userID, time.Now().In(campusLocation))
	return data.Exams
}

func GetCampusStudentScores(orgID, userID, term string) []response.CampusStudentScore {
	now := time.Now().In(campusLocation)
	data := buildCampusStudentData(orgID, userID, now)
	if term == "" {
		term = currentAcademicTerm(now)
	}
	return scoresForTerm(data.Scores, term)
}

func GetCampusStudentTermOverview(orgID, userID, term string) response.CampusStudentTermOverview {
	now := time.Now().In(campusLocation)
	if term == "" {
		term = currentAcademicTerm(now)
	}
	scores := scoresForTerm(buildCampusStudentData(orgID, userID, now).Scores, term)
	return termOverview(term, scores)
}

func GetCampusStudentLearningAnalysis(orgID, userID, term string) response.CampusStudentLearningAnalysis {
	now := time.Now().In(campusLocation)
	if term == "" {
		term = currentAcademicTerm(now)
	}
	data := buildCampusStudentData(orgID, userID, now)
	scores := scoresForTerm(data.Scores, term)
	previousTerm := previousAcademicTerm(term)
	previousAverage := averageScore(scoresForTerm(data.Scores, previousTerm))
	currentAverage := averageScore(scores)
	return response.CampusStudentLearningAnalysis{
		Term:                 term,
		AverageScore:         currentAverage,
		PreviousAverageScore: previousAverage,
		Trend: []response.CampusStudentTrendPoint{
			{Term: previousAcademicTerm(previousTerm), AverageScore: round1(previousAverage - 1.8)},
			{Term: previousTerm, AverageScore: previousAverage},
			{Term: term, AverageScore: currentAverage},
		},
		CourseScores: scores,
		Suggestions:  learningSuggestions(scores),
	}
}

func GetCampusStudentLeaveRecords(orgID, userID string) []response.CampusStudentLeaveRecord {
	data := buildCampusStudentData(orgID, userID, time.Now().In(campusLocation))
	return data.LeaveRecords
}

func buildCampusStudentData(orgID, userID string, now time.Time) campusStudentData {
	seed := campusStudentSeed(orgID, userID)
	currentTerm := currentAcademicTerm(now)
	previousTerm := previousAcademicTerm(currentTerm)
	scoreShift := float64(int(seed%7) - 3)
	studentNo := fmt.Sprintf("%d%08d", now.Year()-2, seed%100000000)

	courses := []response.CampusStudentCourse{
		{CourseID: "CS201", CourseName: "数据结构", Teacher: "王老师", Building: "逸夫楼", Room: "302", Weekday: 1, WeekdayName: "周一", StartTime: "10:10", EndTime: "11:50", Periods: "第3—4节", Credits: 4, CourseType: "专业必修"},
		{CourseID: "MA202", CourseName: "高等数学", Teacher: "刘老师", Building: "综合楼", Room: "A205", Weekday: 2, WeekdayName: "周二", StartTime: "08:00", EndTime: "09:40", Periods: "第1—2节", Credits: 4, CourseType: "公共必修"},
		{CourseID: "AI210", CourseName: "人工智能导论", Teacher: "张老师", Building: "综合楼", Room: "C301", Weekday: 3, WeekdayName: "周三", StartTime: "14:00", EndTime: "15:40", Periods: "第5—6节", Credits: 3, CourseType: "专业必修"},
		{CourseID: "EN204", CourseName: "大学英语", Teacher: "李老师", Building: "综合楼", Room: "B204", Weekday: 4, WeekdayName: "周四", StartTime: "16:10", EndTime: "17:50", Periods: "第7—8节", Credits: 2, CourseType: "公共必修"},
		{CourseID: "PE202", CourseName: "大学体育", Teacher: "赵老师", Building: "体育场", Room: "东区", Weekday: 5, WeekdayName: "周五", StartTime: "10:10", EndTime: "11:50", Periods: "第3—4节", Credits: 1, CourseType: "公共必修"},
		{CourseID: "CS230", CourseName: "计算机网络", Teacher: "陈老师", Building: "逸夫楼", Room: "408", Weekday: 5, WeekdayName: "周五", StartTime: "14:00", EndTime: "15:40", Periods: "第5—6节", Credits: 3, CourseType: "专业必修"},
	}
	scores := []response.CampusStudentScore{
		newCampusScore(currentTerm, "CS201", "数据结构", 4, 88+scoreShift),
		newCampusScore(currentTerm, "MA202", "高等数学", 4, 84+scoreShift),
		newCampusScore(currentTerm, "AI210", "人工智能导论", 3, 91+scoreShift),
		newCampusScore(currentTerm, "EN204", "大学英语", 2, 86+scoreShift),
		newCampusScore(previousTerm, "CS120", "程序设计基础", 4, 85+scoreShift),
		newCampusScore(previousTerm, "MA101", "高等数学（上）", 5, 82+scoreShift),
		newCampusScore(previousTerm, "EN103", "大学英语（上）", 2, 87+scoreShift),
	}
	return campusStudentData{
		Profile: response.CampusStudentProfile{
			StudentNo: studentNo,
			Name:      "河大同学",
			College:   "网络空间安全与计算机学院",
			Major:     "计算机科学与技术",
			ClassName: fmt.Sprintf("计算机科学 %d-%d 班", now.Year()-2, seed%3+1),
			Grade:     fmt.Sprintf("%d级", now.Year()-2),
		},
		Courses: courses,
		Exams: []response.CampusStudentExam{
			{ExamID: fmt.Sprintf("EX-%08d-01", seed%100000000), CourseID: "CS201", CourseName: "数据结构", ExamTime: now.AddDate(0, 0, 21).Format("2006-01-02 09:00"), DurationMinutes: 120, Building: "综合楼", Room: "A301", Seat: fmt.Sprintf("%02d", seed%30+1), Status: "scheduled"},
			{ExamID: fmt.Sprintf("EX-%08d-02", seed%100000000), CourseID: "MA202", CourseName: "高等数学", ExamTime: now.AddDate(0, 0, 28).Format("2006-01-02 14:30"), DurationMinutes: 120, Building: "综合楼", Room: "B202", Seat: fmt.Sprintf("%02d", (seed+11)%30+1), Status: "scheduled"},
		},
		Scores: scores,
		Tasks: []response.CampusStudentTask{
			{TaskID: fmt.Sprintf("TASK-%08d-01", seed%100000000), Title: "完成数据结构章节练习", Type: "homework", CourseName: "数据结构", DueAt: now.AddDate(0, 0, 2).Format("2006-01-02 20:00"), Status: "pending"},
			{TaskID: fmt.Sprintf("TASK-%08d-02", seed%100000000), Title: "确认考试安排", Type: "notice", DueAt: now.AddDate(0, 0, 5).Format("2006-01-02 18:00"), Status: "pending"},
		},
		LeaveRecords: []response.CampusStudentLeaveRecord{
			{ApplicationNo: fmt.Sprintf("LV-%08d-01", seed%100000000), StartTime: now.AddDate(0, 0, -12).Format("2006-01-02 14:00"), EndTime: now.AddDate(0, 0, -12).Format("2006-01-02 18:00"), LeaveType: "事假", Reason: "个人事务", Status: "approved", StatusText: "已通过", CreatedAt: now.AddDate(0, 0, -14).Format("2006-01-02 10:20")},
			{ApplicationNo: fmt.Sprintf("LV-%08d-02", seed%100000000), StartTime: now.AddDate(0, 0, 3).Format("2006-01-02 08:00"), EndTime: now.AddDate(0, 0, 3).Format("2006-01-02 12:00"), LeaveType: "病假", Reason: "身体不适", Status: "pending", StatusText: "审批中", CreatedAt: now.AddDate(0, 0, -1).Format("2006-01-02 16:45")},
		},
	}
}

func campusStudentSeed(orgID, userID string) uint32 {
	h := fnv.New32a()
	_, _ = h.Write([]byte(orgID + "\x00" + userID))
	return h.Sum32()
}

func coursesForDay(courses []response.CampusStudentCourse, day time.Time) []response.CampusStudentCourse {
	weekday := int(day.Weekday())
	if weekday == 0 {
		weekday = 7
	}
	ret := make([]response.CampusStudentCourse, 0)
	for _, course := range courses {
		if course.Weekday == weekday {
			course.Date = day.Format("2006-01-02")
			ret = append(ret, course)
		}
	}
	return ret
}

func coursesForWeek(courses []response.CampusStudentCourse, day time.Time) []response.CampusStudentCourse {
	weekday := int(day.Weekday())
	if weekday == 0 {
		weekday = 7
	}
	monday := day.AddDate(0, 0, 1-weekday)
	ret := make([]response.CampusStudentCourse, 0, len(courses))
	for _, course := range courses {
		course.Date = monday.AddDate(0, 0, course.Weekday-1).Format("2006-01-02")
		ret = append(ret, course)
	}
	return ret
}

func currentAcademicTerm(now time.Time) string {
	if now.Month() >= time.August {
		return fmt.Sprintf("%d-%d-1", now.Year(), now.Year()+1)
	}
	return fmt.Sprintf("%d-%d-2", now.Year()-1, now.Year())
}

func previousAcademicTerm(term string) string {
	var start, end, semester int
	if _, err := fmt.Sscanf(term, "%d-%d-%d", &start, &end, &semester); err != nil {
		return term
	}
	if semester == 2 {
		return fmt.Sprintf("%d-%d-1", start, end)
	}
	return fmt.Sprintf("%d-%d-2", start-1, end-1)
}

func scoresForTerm(scores []response.CampusStudentScore, term string) []response.CampusStudentScore {
	ret := make([]response.CampusStudentScore, 0)
	for _, score := range scores {
		if score.Term == term {
			ret = append(ret, score)
		}
	}
	return ret
}

func newCampusScore(term, courseID, courseName string, credits, score float64) response.CampusStudentScore {
	score = math.Max(0, math.Min(100, score))
	return response.CampusStudentScore{
		Term:       term,
		CourseID:   courseID,
		CourseName: courseName,
		Credits:    credits,
		Score:      score,
		GradePoint: round1(math.Max(0, (score-50)/10)),
		Status:     "published",
	}
}

func averageScore(scores []response.CampusStudentScore) float64 {
	if len(scores) == 0 {
		return 0
	}
	total := 0.0
	for _, score := range scores {
		total += score.Score
	}
	return round1(total / float64(len(scores)))
}

func termOverview(term string, scores []response.CampusStudentScore) response.CampusStudentTermOverview {
	overview := response.CampusStudentTermOverview{Term: term, CourseCount: len(scores)}
	if len(scores) == 0 {
		return overview
	}
	overview.HighestScore = scores[0].Score
	overview.LowestScore = scores[0].Score
	for _, score := range scores {
		overview.TotalCredits += score.Credits
		overview.HighestScore = math.Max(overview.HighestScore, score.Score)
		overview.LowestScore = math.Min(overview.LowestScore, score.Score)
		if score.Score >= 60 {
			overview.PassedCourses++
		}
	}
	overview.AverageScore = averageScore(scores)
	return overview
}

func learningSuggestions(scores []response.CampusStudentScore) []string {
	if len(scores) == 0 {
		return []string{"当前学期暂无已发布成绩，请先保持课程学习节奏。"}
	}
	lowest := scores[0]
	for _, score := range scores[1:] {
		if score.Score < lowest.Score {
			lowest = score
		}
	}
	return []string{
		fmt.Sprintf("%s是当前相对薄弱课程，建议优先复习近期章节并完成专项练习。", lowest.CourseName),
		"本周可安排 3 次 45 分钟集中复习，并在每次学习后记录一个待解决问题。",
	}
}

func round1(value float64) float64 {
	return math.Round(value*10) / 10
}
