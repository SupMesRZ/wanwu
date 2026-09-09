package service

import (
	"errors"
	"testing"
)

func resetCampusLeavesForTest() {
	campusLeaveDB = nil
	campusLeaves.mu.Lock()
	defer campusLeaves.mu.Unlock()
	campusLeaves.byKey = map[string]CampusLeaveApplication{}
	campusLeaves.next = 0
}

func TestCampusLeaveStoreIdempotencyAndIsolation(t *testing.T) {
	resetCampusLeavesForTest()
	a := CampusBusinessExecutionIdentity{UserID: "student-a", OrgID: "org-1", ActualRole: CampusRoleStudent}
	app, err := CreateCampusLeave(a, "exec-a", "2026-09-07T14:00:00+08:00", "2026-09-07T17:00:00+08:00", "病假", "身体不舒服", "", true)
	if err != nil || app.RequestID == "" || app.Status != "pending" {
		t.Fatalf("create failed: %+v %v", app, err)
	}
	retry, err := CreateCampusLeave(a, "exec-a", "2026-09-07T14:00:00+08:00", "2026-09-07T17:00:00+08:00", "病假", "身体不舒服", "", true)
	if err != nil || retry.RequestID != app.RequestID {
		t.Fatalf("retry was not idempotent: %+v %v", retry, err)
	}
	records, err := ListCampusLeaves(a)
	if err != nil {
		t.Fatal(err)
	}
	if got := len(records); got != 1 {
		t.Fatalf("expected one application, got %d", got)
	}
	otherRecords, err := ListCampusLeaves(CampusBusinessExecutionIdentity{UserID: "student-b", OrgID: "org-1", ActualRole: CampusRoleStudent})
	if err != nil {
		t.Fatal(err)
	}
	if got := len(otherRecords); got != 0 {
		t.Fatalf("student isolation failed: %d", got)
	}
}

func TestCampusLeaveStoreRejectsUnsafeRequests(t *testing.T) {
	resetCampusLeavesForTest()
	a := CampusBusinessExecutionIdentity{UserID: "student-a", OrgID: "org-1", ActualRole: CampusRoleStudent}
	for _, tc := range []struct {
		name, start, end, reason, want string
	}{
		{"unconfirmed", "2026-09-08T14:00:00+08:00", "2026-09-08T17:00:00+08:00", "x", "invalid_leave_request"},
		{"reversed", "2026-09-08T17:00:00+08:00", "2026-09-08T14:00:00+08:00", "x", "invalid_time_range"},
		{"empty reason", "2026-09-08T14:00:00+08:00", "2026-09-08T17:00:00+08:00", "", "invalid_leave_request"},
	} {
		t.Run(tc.name, func(t *testing.T) {
			_, err := CreateCampusLeave(a, tc.name, tc.start, tc.end, "病假", tc.reason, "", tc.name != "unconfirmed")
			if err == nil || err.Error() != tc.want {
				t.Fatalf("want %s, got %v", tc.want, err)
			}
		})
	}
}

func TestTeacherReviewsCampusLeave(t *testing.T) {
	resetCampusLeavesForTest()
	oldReviewOrgID := campusTeacherReviewOrgID
	campusTeacherReviewOrgID = "teacher-org"
	defer func() { campusTeacherReviewOrgID = oldReviewOrgID }()
	student := CampusBusinessExecutionIdentity{UserID: "student-a", OrgID: "org-1", ActualRole: CampusRoleStudent}
	teacher := CampusBusinessExecutionIdentity{UserID: "teacher-a", OrgID: "teacher-org", ActualRole: CampusRoleTeacher}
	app, err := CreateCampusLeave(student, "exec-review", "2026-09-08T00:00:00+08:00", "2026-09-09T23:59:59+08:00", "病假", "生病", "", true)
	if err != nil {
		t.Fatal(err)
	}
	pending, err := ListCampusLeavesForReview(teacher, "pending")
	if err != nil || len(pending) != 1 || pending[0].RequestID != app.RequestID {
		t.Fatalf("pending leave unavailable: %+v %v", pending, err)
	}
	if other, err := ListCampusLeavesForReview(CampusBusinessExecutionIdentity{UserID: "teacher-b", OrgID: "org-2", ActualRole: CampusRoleTeacher}, "pending"); err != nil || len(other) != 0 {
		t.Fatalf("cross-org leave exposed: %+v %v", other, err)
	}
	reviewed, err := ReviewCampusLeave(teacher, app.RequestID, "approve", "同意")
	if err != nil || reviewed.Status != "approved" || reviewed.ReviewerID != teacher.UserID {
		t.Fatalf("review failed: %+v %v", reviewed, err)
	}
	if _, err := ReviewCampusLeave(teacher, app.RequestID, "reject", ""); !errors.Is(err, errCampusLeaveNotPending) {
		t.Fatalf("second review should fail, got %v", err)
	}
}
