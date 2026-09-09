package service

import (
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"errors"
	"fmt"
	"strings"
	"sync"
	"time"

	"github.com/UnicomAI/wanwu/internal/bff-service/config"
	"github.com/UnicomAI/wanwu/internal/bff-service/model/response"
	"github.com/UnicomAI/wanwu/pkg/log"
	"gorm.io/gorm"
)

type CampusLeaveApplication struct {
	ID                uint64 `json:"-" gorm:"primaryKey"`
	RequestID         string `json:"requestId" gorm:"size:32;uniqueIndex"`
	OwnerUserID       string `json:"-" gorm:"size:64;index:idx_campus_leave_owner"`
	OwnerOrgID        string `json:"-" gorm:"size:64;index:idx_campus_leave_owner"`
	ReviewOrgID       string `json:"-" gorm:"size:64;index"`
	StartTime         string `json:"startTime" gorm:"size:32"`
	EndTime           string `json:"endTime" gorm:"size:32"`
	LeaveType         string `json:"leaveType" gorm:"size:16"`
	Reason            string `json:"reason"`
	AttachmentID      string `json:"attachmentId,omitempty" gorm:"size:128"`
	Status            string `json:"status" gorm:"size:16"`
	ReviewerID        string `json:"reviewerId,omitempty" gorm:"size:64"`
	ReviewRemark      string `json:"reviewRemark,omitempty"`
	ReviewedAt        string `json:"reviewedAt,omitempty" gorm:"size:32"`
	CreatedAt         string `json:"createdAt" gorm:"size:32"`
	UpdatedAt         string `json:"updatedAt" gorm:"size:32"`
	WorkflowExecuteID string `json:"-" gorm:"size:64;index"`
	IdempotencyKey    string `json:"-" gorm:"size:64;uniqueIndex"`
}

type campusLeaveStore struct {
	mu    sync.Mutex
	byKey map[string]CampusLeaveApplication
	next  int
}

var campusLeaves = campusLeaveStore{byKey: map[string]CampusLeaveApplication{}}
var campusLeaveDB *gorm.DB
var campusTeacherReviewOrgID string
var errCampusLeaveTimeConflict = errors.New("LEAVE_TIME_CONFLICT")
var errCampusLeaveNotFound = errors.New("leave_request_not_found")
var errCampusLeaveNotPending = errors.New("leave_request_not_pending")

func initCampusLeaveStore(db *gorm.DB) error {
	if err := db.AutoMigrate(&CampusLeaveApplication{}); err != nil {
		return err
	}
	campusTeacherReviewOrgID = strings.TrimSpace(config.Cfg().CampusBusiness.TeacherReviewOrgID)
	if campusTeacherReviewOrgID != "" {
		if err := db.Model(&CampusLeaveApplication{}).Where("review_org_id = '' OR review_org_id IS NULL").Update("review_org_id", campusTeacherReviewOrgID).Error; err != nil {
			return err
		}
	}
	campusLeaveDB = db
	return nil
}

func campusLeaveReviewOrgID(ownerOrgID string) string {
	if campusTeacherReviewOrgID != "" {
		return campusTeacherReviewOrgID
	}
	return ownerOrgID
}

func CreateCampusLeave(identity CampusBusinessExecutionIdentity, executeID, startTime, endTime, leaveType, reason, attachmentID string, confirmed bool) (CampusLeaveApplication, error) {
	if identity.ActualRole != CampusRoleStudent || identity.UserID == "" || identity.OrgID == "" {
		return CampusLeaveApplication{}, errors.New("execution_identity_missing")
	}
	if !confirmed || strings.TrimSpace(reason) == "" || strings.TrimSpace(startTime) == "" || strings.TrimSpace(endTime) == "" {
		return CampusLeaveApplication{}, errors.New("invalid_leave_request")
	}
	if leaveType != "病假" && leaveType != "事假" && leaveType != "公假" {
		return CampusLeaveApplication{}, errors.New("invalid_leave_type")
	}
	start, err := time.Parse(time.RFC3339, startTime)
	if err != nil {
		return CampusLeaveApplication{}, errors.New("invalid_start_time")
	}
	end, err := time.Parse(time.RFC3339, endTime)
	if err != nil || !start.Before(end) {
		return CampusLeaveApplication{}, errors.New("invalid_time_range")
	}
	normalized := fmt.Sprintf("%s|%s|%s|%s|%s|%s", executeID, start.UTC().Format(time.RFC3339), end.UTC().Format(time.RFC3339), leaveType, strings.TrimSpace(reason), attachmentID)
	hash := sha256.Sum256([]byte(normalized))
	key := hex.EncodeToString(hash[:])
	// ponytail: this lock assumes one BFF replica; use a DB advisory lock before horizontal scaling.
	campusLeaves.mu.Lock()
	defer campusLeaves.mu.Unlock()
	if campusLeaveDB != nil {
		return createCampusLeaveInDB(identity, executeID, start, end, leaveType, reason, attachmentID, key)
	}
	if old, ok := campusLeaves.byKey[key]; ok {
		return old, nil
	}
	for _, old := range campusLeaves.byKey {
		if old.OwnerUserID != identity.UserID || old.OwnerOrgID != identity.OrgID || (old.Status != "pending" && old.Status != "approved") {
			continue
		}
		os, _ := time.Parse(time.RFC3339, old.StartTime)
		ee, e2 := time.Parse(time.RFC3339, old.EndTime)
		if e2 == nil && start.Before(ee) && end.After(os) {
			return CampusLeaveApplication{}, errCampusLeaveTimeConflict
		}
	}
	campusLeaves.next++
	now := time.Now()
	app := CampusLeaveApplication{RequestID: fmt.Sprintf("LEV-%s-%03d", now.Format("20060102"), campusLeaves.next), OwnerUserID: identity.UserID, OwnerOrgID: identity.OrgID, ReviewOrgID: campusLeaveReviewOrgID(identity.OrgID), StartTime: start.UTC().Format(time.RFC3339), EndTime: end.UTC().Format(time.RFC3339), LeaveType: leaveType, Reason: strings.TrimSpace(reason), AttachmentID: attachmentID, Status: "pending", CreatedAt: now.UTC().Format(time.RFC3339), UpdatedAt: now.UTC().Format(time.RFC3339), WorkflowExecuteID: executeID, IdempotencyKey: key}
	campusLeaves.byKey[key] = app
	return app, nil
}

func createCampusLeaveInDB(identity CampusBusinessExecutionIdentity, executeID string, start, end time.Time, leaveType, reason, attachmentID, key string) (CampusLeaveApplication, error) {
	var app CampusLeaveApplication
	err := campusLeaveDB.Transaction(func(tx *gorm.DB) error {
		if err := tx.Where("idempotency_key = ?", key).First(&app).Error; err == nil {
			return nil
		} else if !errors.Is(err, gorm.ErrRecordNotFound) {
			return err
		}
		var conflicts int64
		if err := tx.Model(&CampusLeaveApplication{}).
			Where("owner_user_id = ? AND owner_org_id = ? AND status IN ? AND start_time < ? AND end_time > ?", identity.UserID, identity.OrgID, []string{"pending", "approved"}, end.UTC().Format(time.RFC3339), start.UTC().Format(time.RFC3339)).
			Count(&conflicts).Error; err != nil {
			return err
		}
		if conflicts > 0 {
			return errCampusLeaveTimeConflict
		}
		now := time.Now()
		app = CampusLeaveApplication{RequestID: "PENDING-" + key[:16], OwnerUserID: identity.UserID, OwnerOrgID: identity.OrgID, ReviewOrgID: campusLeaveReviewOrgID(identity.OrgID), StartTime: start.UTC().Format(time.RFC3339), EndTime: end.UTC().Format(time.RFC3339), LeaveType: leaveType, Reason: strings.TrimSpace(reason), AttachmentID: attachmentID, Status: "pending", CreatedAt: now.UTC().Format(time.RFC3339), UpdatedAt: now.UTC().Format(time.RFC3339), WorkflowExecuteID: executeID, IdempotencyKey: key}
		if err := tx.Create(&app).Error; err != nil {
			return err
		}
		app.RequestID = fmt.Sprintf("LEV-%s-%03d", now.Format("20060102"), app.ID)
		return tx.Model(&app).Update("request_id", app.RequestID).Error
	})
	if err != nil && !errors.Is(err, errCampusLeaveTimeConflict) {
		log.Errorf("campus leave create failed: %v", err)
		return CampusLeaveApplication{}, errors.New("internal_error")
	}
	return app, err
}

func ListCampusLeaves(identity CampusBusinessExecutionIdentity) ([]response.CampusStudentLeaveRecord, error) {
	if campusLeaveDB != nil {
		var apps []CampusLeaveApplication
		if err := campusLeaveDB.Where("owner_user_id = ? AND owner_org_id = ?", identity.UserID, identity.OrgID).Order("id DESC").Find(&apps).Error; err != nil {
			log.Errorf("campus leave list failed: %v", err)
			return nil, errors.New("internal_error")
		}
		return campusLeaveRecords(apps), nil
	}
	campusLeaves.mu.Lock()
	defer campusLeaves.mu.Unlock()
	apps := make([]CampusLeaveApplication, 0)
	for _, app := range campusLeaves.byKey {
		if app.OwnerUserID == identity.UserID && app.OwnerOrgID == identity.OrgID {
			apps = append(apps, app)
		}
	}
	return campusLeaveRecords(apps), nil
}

func ListCampusLeavesForReview(identity CampusBusinessExecutionIdentity, status string) ([]CampusLeaveApplication, error) {
	if identity.ActualRole != CampusRoleTeacher || identity.UserID == "" || identity.OrgID == "" {
		return nil, errors.New("permission_denied")
	}
	if campusLeaveDB != nil {
		var apps []CampusLeaveApplication
		query := campusLeaveDB.Where("review_org_id = ?", identity.OrgID).Order("id DESC")
		if status != "" {
			query = query.Where("status = ?", status)
		}
		if err := query.Find(&apps).Error; err != nil {
			log.Errorf("campus leave review list failed: %v", err)
			return nil, errors.New("internal_error")
		}
		return apps, nil
	}
	campusLeaves.mu.Lock()
	defer campusLeaves.mu.Unlock()
	apps := make([]CampusLeaveApplication, 0)
	for _, app := range campusLeaves.byKey {
		if app.ReviewOrgID == identity.OrgID && (status == "" || app.Status == status) {
			apps = append(apps, app)
		}
	}
	return apps, nil
}

func ReviewCampusLeave(identity CampusBusinessExecutionIdentity, requestID, decision, remark string) (CampusLeaveApplication, error) {
	if identity.ActualRole != CampusRoleTeacher || identity.UserID == "" || identity.OrgID == "" {
		return CampusLeaveApplication{}, errors.New("permission_denied")
	}
	status, ok := map[string]string{"approve": "approved", "reject": "rejected"}[decision]
	if strings.TrimSpace(requestID) == "" || !ok {
		return CampusLeaveApplication{}, errors.New("invalid_arguments")
	}
	now := time.Now().UTC().Format(time.RFC3339)
	updates := map[string]any{"status": status, "reviewer_id": identity.UserID, "review_remark": strings.TrimSpace(remark), "reviewed_at": now, "updated_at": now}
	if campusLeaveDB != nil {
		var app CampusLeaveApplication
		err := campusLeaveDB.Transaction(func(tx *gorm.DB) error {
			if err := tx.Where("request_id = ? AND review_org_id = ?", requestID, identity.OrgID).First(&app).Error; errors.Is(err, gorm.ErrRecordNotFound) {
				return errCampusLeaveNotFound
			} else if err != nil {
				return err
			}
			if app.Status != "pending" {
				return errCampusLeaveNotPending
			}
			result := tx.Model(&CampusLeaveApplication{}).Where("id = ? AND status = ?", app.ID, "pending").Updates(updates)
			if result.Error != nil {
				return result.Error
			}
			if result.RowsAffected == 0 {
				return errCampusLeaveNotPending
			}
			return tx.First(&app, app.ID).Error
		})
		if err != nil && !errors.Is(err, errCampusLeaveNotFound) && !errors.Is(err, errCampusLeaveNotPending) {
			log.Errorf("campus leave review failed: %v", err)
			return CampusLeaveApplication{}, errors.New("internal_error")
		}
		return app, err
	}
	campusLeaves.mu.Lock()
	defer campusLeaves.mu.Unlock()
	for key, app := range campusLeaves.byKey {
		if app.RequestID != requestID || app.ReviewOrgID != identity.OrgID {
			continue
		}
		if app.Status != "pending" {
			return CampusLeaveApplication{}, errCampusLeaveNotPending
		}
		app.Status, app.ReviewerID, app.ReviewRemark, app.ReviewedAt, app.UpdatedAt = status, identity.UserID, strings.TrimSpace(remark), now, now
		campusLeaves.byKey[key] = app
		return app, nil
	}
	return CampusLeaveApplication{}, errCampusLeaveNotFound
}

func campusLeaveRecords(apps []CampusLeaveApplication) []response.CampusStudentLeaveRecord {
	result := make([]response.CampusStudentLeaveRecord, 0, len(apps))
	for _, app := range apps {
		result = append(result, response.CampusStudentLeaveRecord{ApplicationNo: app.RequestID, StartTime: app.StartTime, EndTime: app.EndTime, LeaveType: app.LeaveType, Reason: app.Reason, Status: app.Status, StatusText: map[string]string{"pending": "审批中", "approved": "已通过", "rejected": "已驳回"}[app.Status], CreatedAt: app.CreatedAt})
	}
	return result
}

func decodeLeavePayload(raw json.RawMessage) (map[string]any, error) {
	var v map[string]any
	if err := json.Unmarshal(raw, &v); err != nil {
		return nil, err
	}
	return v, nil
}
