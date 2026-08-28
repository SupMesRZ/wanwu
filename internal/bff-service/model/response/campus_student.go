package response

import "github.com/UnicomAI/wanwu/internal/bff-service/model/request"

type CampusStudentAssistantInfo struct {
	Name              string         `json:"name"`
	Avatar            request.Avatar `json:"avatar"`
	Prologue          string         `json:"prologue"`
	RecommendQuestion []string       `json:"recommendQuestion"`
}

type CampusStudentProfile struct {
	StudentNo string `json:"studentNo"`
	Name      string `json:"name"`
	College   string `json:"college"`
	Major     string `json:"major"`
	ClassName string `json:"className"`
	Grade     string `json:"grade"`
}

type CampusStudentCourse struct {
	CourseID    string  `json:"courseId"`
	CourseName  string  `json:"courseName"`
	Teacher     string  `json:"teacher"`
	Building    string  `json:"building"`
	Room        string  `json:"room"`
	Weekday     int     `json:"weekday"`
	WeekdayName string  `json:"weekdayName"`
	Date        string  `json:"date,omitempty"`
	StartTime   string  `json:"startTime"`
	EndTime     string  `json:"endTime"`
	Periods     string  `json:"periods"`
	Credits     float64 `json:"credits"`
	CourseType  string  `json:"courseType"`
}

type CampusStudentExam struct {
	ExamID          string `json:"examId"`
	CourseID        string `json:"courseId"`
	CourseName      string `json:"courseName"`
	ExamTime        string `json:"examTime"`
	DurationMinutes int    `json:"durationMinutes"`
	Building        string `json:"building"`
	Room            string `json:"room"`
	Seat            string `json:"seat"`
	Status          string `json:"status"`
}

type CampusStudentScore struct {
	Term       string  `json:"term"`
	CourseID   string  `json:"courseId"`
	CourseName string  `json:"courseName"`
	Credits    float64 `json:"credits"`
	Score      float64 `json:"score"`
	GradePoint float64 `json:"gradePoint"`
	Status     string  `json:"status"`
}

type CampusStudentTask struct {
	TaskID     string `json:"taskId"`
	Title      string `json:"title"`
	Type       string `json:"type"`
	CourseName string `json:"courseName,omitempty"`
	DueAt      string `json:"dueAt"`
	Status     string `json:"status"`
}

type CampusStudentLeaveRecord struct {
	ApplicationNo string `json:"applicationNo"`
	StartTime     string `json:"startTime"`
	EndTime       string `json:"endTime"`
	LeaveType     string `json:"leaveType"`
	Reason        string `json:"reason"`
	Status        string `json:"status"`
	StatusText    string `json:"statusText"`
	CreatedAt     string `json:"createdAt"`
}

type CampusStudentSummary struct {
	Profile      CampusStudentProfile  `json:"profile"`
	TodayCourses []CampusStudentCourse `json:"todayCourses"`
	Tasks        []CampusStudentTask   `json:"tasks"`
	RecentScores []CampusStudentScore  `json:"recentScores"`
	Overview     CampusStudentOverview `json:"overview"`
}

type CampusStudentOverview struct {
	CourseCount      int     `json:"courseCount"`
	TodayCourseCount int     `json:"todayCourseCount"`
	PendingTaskCount int     `json:"pendingTaskCount"`
	AverageScore     float64 `json:"averageScore"`
}

type CampusStudentTermOverview struct {
	Term          string  `json:"term"`
	CourseCount   int     `json:"courseCount"`
	TotalCredits  float64 `json:"totalCredits"`
	AverageScore  float64 `json:"averageScore"`
	HighestScore  float64 `json:"highestScore"`
	LowestScore   float64 `json:"lowestScore"`
	PassedCourses int     `json:"passedCourses"`
}

type CampusStudentTrendPoint struct {
	Term         string  `json:"term"`
	AverageScore float64 `json:"averageScore"`
}

type CampusStudentLearningAnalysis struct {
	Term                 string                    `json:"term"`
	AverageScore         float64                   `json:"averageScore"`
	PreviousAverageScore float64                   `json:"previousAverageScore"`
	Trend                []CampusStudentTrendPoint `json:"trend"`
	CourseScores         []CampusStudentScore      `json:"courseScores"`
	Suggestions          []string                  `json:"suggestions"`
}
