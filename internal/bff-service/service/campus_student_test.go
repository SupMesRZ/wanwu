package service

import (
	"context"
	"testing"
	"time"

	assistant_service "github.com/UnicomAI/wanwu/api/proto/assistant-service"
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

func TestCampusStudentAssistantBindingUsesConfiguredPublishedObject(t *testing.T) {
	originalID := campusStudentAssistantID
	originalGet := getPublishedCampusStudentAssistant
	defer func() {
		campusStudentAssistantID = originalID
		getPublishedCampusStudentAssistant = originalGet
	}()

	campusStudentAssistantID = func() string { return "42" }
	getPublishedCampusStudentAssistant = func(_ context.Context, assistantID string) (*assistant_service.AssistantInfo, error) {
		if assistantID != "42" {
			t.Fatalf("unexpected assistant id: %s", assistantID)
		}
		return newValidCampusStudentAssistant(), nil
	}

	binding, err := GetCampusStudentAssistantBinding(context.Background())
	if err != nil {
		t.Fatal(err)
	}
	if binding.AssistantID != "42" || !binding.Published || !binding.Ready || binding.Name != "河小智·学生助手" {
		t.Fatalf("unexpected binding: %+v", binding)
	}
}
