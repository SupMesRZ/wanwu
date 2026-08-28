package service

import (
	"testing"
	"time"

	"github.com/UnicomAI/wanwu/internal/bff-service/model/response"
)

func TestCampusStudentMockIsolationAndRoleResolution(t *testing.T) {
	now := time.Date(2026, time.August, 27, 10, 0, 0, 0, campusLocation)
	a1 := buildCampusStudentData("org-a", "user-a", now)
	a2 := buildCampusStudentData("org-a", "user-a", now)
	b := buildCampusStudentData("org-a", "user-b", now)
	c := buildCampusStudentData("org-b", "user-a", now)

	if a1.Profile.StudentNo != a2.Profile.StudentNo {
		t.Fatal("same org and user must receive deterministic data")
	}
	if a1.Profile.StudentNo == b.Profile.StudentNo || a1.Profile.StudentNo == c.Profile.StudentNo {
		t.Fatal("mock data must be isolated by orgId and authenticated userId")
	}
	if got := len(coursesForDay(a1.Courses, now)); got != 1 {
		t.Fatalf("expected one Thursday course, got %d", got)
	}

	role, status := ResolveActualCampusRole([]response.RoleIDName{{Name: CampusRoleStudent}})
	if role != CampusRoleStudent || status != CampusRoleResolved {
		t.Fatalf("expected resolved student role, got %q/%q", role, status)
	}
	_, status = ResolveActualCampusRole([]response.RoleIDName{{Name: CampusRoleTeacher}, {Name: CampusRoleStudent}})
	if status != CampusRoleConflict {
		t.Fatalf("expected order-independent conflict, got %q", status)
	}
	_, status = ResolveActualCampusRole([]response.RoleIDName{{Name: "普通用户"}})
	if status != CampusRoleUnconfigured {
		t.Fatalf("expected unconfigured role, got %q", status)
	}
}
