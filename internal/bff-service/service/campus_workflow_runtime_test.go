package service

import "testing"

func TestCampusWorkflowRegistryAndResumeIsolation(t *testing.T) {
	teacher := CampusBusinessExecutionIdentity{UserID: "student-a", OrgID: "org-1", ActualRole: CampusRoleStudent}
	if err := ValidateCampusWorkflowExecution("teacher_course_adjustment", teacher); err == nil {
		t.Fatal("student must not run teacher workflow")
	}
	if err := ValidateCampusWorkflowExecution("academic_course_adjustment_approval", CampusBusinessExecutionIdentity{UserID: "teacher-a", OrgID: "org-1", ActualRole: CampusRoleTeacher}); err == nil {
		t.Fatal("teacher must not run academic workflow")
	}
	if err := ValidateCampusWorkflowExecution("campus_workflow_identity_probe", teacher); err != nil {
		t.Fatalf("student probe rejected: %v", err)
	}
	if err := ValidateCampusWorkflowExecution("campus_workflow_identity_probe", CampusBusinessExecutionIdentity{UserID: "admin", OrgID: "org-1", ActualRole: CampusRoleAcademicAdmin}); err == nil {
		t.Fatal("wrong role must not run student probe")
	}
	if err := ValidateCampusWorkflowExecution("campus_workflow_identity_probe", CampusBusinessExecutionIdentity{UserID: "student-a", OrgID: "org-1"}); err == nil {
		t.Fatal("missing execution identity must fail closed")
	}

	runID := "phase4a-run-a"
	if err := BindCampusWorkflowRun(runID, "campus_workflow_identity_probe", teacher, "conversation-a"); err != nil {
		t.Fatal(err)
	}
	if err := ValidateCampusWorkflowResume(runID, "campus_workflow_identity_probe", teacher); err != nil {
		t.Fatalf("owner resume rejected: %v", err)
	}
	for _, identity := range []CampusBusinessExecutionIdentity{
		{UserID: "student-b", OrgID: "org-1", ActualRole: CampusRoleStudent},
		{UserID: "admin", OrgID: "org-1", ActualRole: CampusRoleAcademicAdmin},
		{UserID: "student-a", OrgID: "org-2", ActualRole: CampusRoleStudent},
		{UserID: "student-a", OrgID: "org-1", ActualRole: CampusRoleTeacher},
	} {
		if err := ValidateCampusWorkflowResume(runID, "campus_workflow_identity_probe", identity); err == nil {
			t.Fatalf("forged resume accepted: %+v", identity)
		}
	}
}

func TestStripCampusIdentityInput(t *testing.T) {
	got := stripCampusIdentityInput(map[string]any{"studentId": "B", "userId": "B", "orgId": "other", "role": "admin", "previewRole": "student", "reason": "keep"})
	if len(got) != 1 || got["reason"] != "keep" {
		t.Fatalf("identity fields survived input sanitization: %#v", got)
	}
}

func TestCampusWorkflowNameMatchesGeneratedSuffix(t *testing.T) {
	managed := "学生智能请假与销假全流程-正式"
	for _, name := range []string{managed, managed + "_1", managed + "_12"} {
		if !campusWorkflowNameMatches(name, managed) {
			t.Fatalf("managed workflow name rejected: %q", name)
		}
	}
	for _, name := range []string{managed + "_", managed + "_0", managed + "_x", managed + "_1_extra", managed + "副本"} {
		if campusWorkflowNameMatches(name, managed) {
			t.Fatalf("unmanaged workflow name accepted: %q", name)
		}
	}
}
