package service

import (
	"errors"
	"fmt"
	"sync"

	"gorm.io/gorm"
)

var (
	errCampusAdjustmentNotFound                         = errors.New("campus adjustment not found")
	errCampusAdjustmentNotPending                       = errors.New("campus adjustment not pending")
	adjustmentStore               campusAdjustmentStore = newMemoryCampusAdjustmentStore()
)

type campusAdjustmentStore interface {
	TeacherRecords(userID string) ([]campusAdjustmentRequest, error)
	History(status string) ([]campusAdjustmentRequest, error)
	Find(requestID string) (campusAdjustmentRequest, error)
	Create(campusAdjustmentRequest) (campusAdjustmentRequest, error)
	ReviewPending(requestID, status, statusText, remark, reviewerID, reviewedAt string) (campusAdjustmentRequest, error)
}

type memoryCampusAdjustmentStore struct {
	sync.Mutex
	next    uint64
	records []campusAdjustmentRequest
}

func newMemoryCampusAdjustmentStore() *memoryCampusAdjustmentStore {
	return &memoryCampusAdjustmentStore{next: 1}
}

func (s *memoryCampusAdjustmentStore) TeacherRecords(userID string) ([]campusAdjustmentRequest, error) {
	s.Lock()
	defer s.Unlock()
	items := make([]campusAdjustmentRequest, 0)
	for _, item := range s.records {
		if item.TeacherID == userID {
			items = append(items, item)
		}
	}
	return items, nil
}

func (s *memoryCampusAdjustmentStore) History(status string) ([]campusAdjustmentRequest, error) {
	s.Lock()
	defer s.Unlock()
	items := make([]campusAdjustmentRequest, 0)
	for _, item := range s.records {
		if status == "" || item.Status == status {
			items = append(items, item)
		}
	}
	return items, nil
}

func (s *memoryCampusAdjustmentStore) Find(requestID string) (campusAdjustmentRequest, error) {
	s.Lock()
	defer s.Unlock()
	for _, item := range s.records {
		if item.RequestID == requestID {
			return item, nil
		}
	}
	return campusAdjustmentRequest{}, errCampusAdjustmentNotFound
}

func (s *memoryCampusAdjustmentStore) Create(item campusAdjustmentRequest) (campusAdjustmentRequest, error) {
	s.Lock()
	defer s.Unlock()
	item.ID = s.next
	item.RequestID = fmt.Sprintf("ADJ-%03d", item.ID)
	s.next++
	s.records = append(s.records, item)
	return item, nil
}

func (s *memoryCampusAdjustmentStore) ReviewPending(requestID, status, statusText, remark, reviewerID, reviewedAt string) (campusAdjustmentRequest, error) {
	s.Lock()
	defer s.Unlock()
	for i := range s.records {
		item := &s.records[i]
		if item.RequestID != requestID {
			continue
		}
		if item.Status != "pending" {
			return campusAdjustmentRequest{}, errCampusAdjustmentNotPending
		}
		item.Status, item.StatusText, item.Remark = status, statusText, remark
		item.ReviewerID, item.ReviewedAt = reviewerID, reviewedAt
		return *item, nil
	}
	return campusAdjustmentRequest{}, errCampusAdjustmentNotFound
}

type mysqlCampusAdjustmentStore struct{ db *gorm.DB }

func initCampusAdjustmentStore(db *gorm.DB) error {
	if err := db.AutoMigrate(&campusAdjustmentRequest{}); err != nil {
		return err
	}
	adjustmentStore = &mysqlCampusAdjustmentStore{db: db}
	return nil
}

func (s *mysqlCampusAdjustmentStore) TeacherRecords(userID string) ([]campusAdjustmentRequest, error) {
	var items []campusAdjustmentRequest
	err := s.db.Where("teacher_id = ?", userID).Order("id DESC").Find(&items).Error
	return items, err
}

func (s *mysqlCampusAdjustmentStore) History(status string) ([]campusAdjustmentRequest, error) {
	var items []campusAdjustmentRequest
	query := s.db.Order("id DESC")
	if status != "" {
		query = query.Where("status = ?", status)
	}
	err := query.Find(&items).Error
	return items, err
}

func (s *mysqlCampusAdjustmentStore) Find(requestID string) (campusAdjustmentRequest, error) {
	var item campusAdjustmentRequest
	err := s.db.Where("request_id = ?", requestID).First(&item).Error
	if errors.Is(err, gorm.ErrRecordNotFound) {
		err = errCampusAdjustmentNotFound
	}
	return item, err
}

func (s *mysqlCampusAdjustmentStore) Create(item campusAdjustmentRequest) (campusAdjustmentRequest, error) {
	err := s.db.Transaction(func(tx *gorm.DB) error {
		if err := tx.Create(&item).Error; err != nil {
			return err
		}
		item.RequestID = fmt.Sprintf("ADJ-%03d", item.ID)
		return tx.Model(&item).Update("request_id", item.RequestID).Error
	})
	return item, err
}

func (s *mysqlCampusAdjustmentStore) ReviewPending(requestID, status, statusText, remark, reviewerID, reviewedAt string) (campusAdjustmentRequest, error) {
	updates := map[string]any{"status": status, "status_text": statusText, "remark": remark, "reviewer_id": reviewerID, "reviewed_at": reviewedAt}
	result := s.db.Model(&campusAdjustmentRequest{}).Where("request_id = ? AND status = ?", requestID, "pending").Updates(updates)
	if result.Error != nil {
		return campusAdjustmentRequest{}, result.Error
	}
	if result.RowsAffected == 0 {
		if _, err := s.Find(requestID); err != nil {
			return campusAdjustmentRequest{}, err
		}
		return campusAdjustmentRequest{}, errCampusAdjustmentNotPending
	}
	return s.Find(requestID)
}
