package orm

import (
	"context"
	"fmt"
	"testing"

	"github.com/UnicomAI/wanwu/internal/app-service/client/model"
	"github.com/glebarez/sqlite"
	"gorm.io/gorm"
)

func newOpinionEventTestClient(t *testing.T) *Client {
	t.Helper()
	dsn := fmt.Sprintf("file:%s?mode=memory&cache=shared", t.Name())
	db, err := gorm.Open(sqlite.Open(dsn), &gorm.Config{})
	if err != nil {
		t.Fatalf("open sqlite: %v", err)
	}
	if err := db.AutoMigrate(&model.OpinionItem{}, &model.OpinionEvent{}, &model.OpinionEventItem{}); err != nil {
		t.Fatalf("auto migrate: %v", err)
	}
	return &Client{db: db}
}

func createOpinionEventTestItem(t *testing.T, client *Client, orgID, suffix string) *model.OpinionItem {
	t.Helper()
	item := &model.OpinionItem{
		OrgID: orgID, CreatorID: "user-1", Title: "title-" + suffix, Content: "content-" + suffix,
		SourceName: "source", SourceType: "公开新闻", PublishedAt: 1000, ContentHash: "hash-" + orgID + "-" + suffix,
	}
	if err := client.db.Create(item).Error; err != nil {
		t.Fatalf("create opinion item: %v", err)
	}
	return item
}

func requireOpinionEventStatusKey(t *testing.T, status interface{ GetTextKey() string }, key string) {
	t.Helper()
	if status == nil || status.GetTextKey() != key {
		t.Fatalf("expected status key %q, got %#v", key, status)
	}
}

func TestCreateOpinionEventAndQuery(t *testing.T) {
	client := newOpinionEventTestClient(t)
	ctx := context.Background()
	item1 := createOpinionEventTestItem(t, client, "org-1", "1")
	item2 := createOpinionEventTestItem(t, client, "org-1", "2")

	event, status := client.CreateOpinionEvent(ctx, CreateOpinionEventInput{
		OrgID: "org-1", CreatorID: "user-1", Title: "  宿舍维修事件  ", Topic: "后勤", Summary: "摘要",
		ItemIDs: []uint32{item2.ID, item1.ID},
	})
	if status != nil {
		t.Fatalf("create event status: %#v", status)
	}
	if event.Status != model.OpinionEventStatusDraft || event.ItemCount != 2 || event.Title != "宿舍维修事件" {
		t.Fatalf("unexpected event: %#v", event)
	}

	events, total, status := client.ListOpinionEvents(ctx, OpinionEventListFilter{
		OrgID: "org-1", Keyword: "维修", Topic: "后勤", Status: model.OpinionEventStatusDraft,
	})
	if status != nil || total != 1 || len(events) != 1 || events[0].ItemCount != 2 {
		t.Fatalf("unexpected event list: events=%#v total=%d status=%#v", events, total, status)
	}

	detail, status := client.GetOpinionEvent(ctx, event.ID, "org-1")
	if status != nil || detail.Event.ItemCount != 2 || len(detail.Items) != 2 {
		t.Fatalf("unexpected detail: detail=%#v status=%#v", detail, status)
	}

	items, total, status := client.ListOpinionItems(ctx, PublicOpinionListFilter{OrgID: "org-1", PageNo: 1, PageSize: 10})
	if status != nil || total != 2 || len(items) != 2 {
		t.Fatalf("unexpected opinion list: items=%#v total=%d status=%#v", items, total, status)
	}
	for _, item := range items {
		if item.EventID != event.ID || item.EventTitle != event.Title || item.EventStatus != model.OpinionEventStatusDraft {
			t.Fatalf("missing event binding: %#v", item)
		}
	}

	updated, status := client.UpdateOpinionEvent(ctx, UpdateOpinionEventInput{
		OrgID: "org-1", EventID: event.ID, Title: "新标题", Topic: "新主题", Summary: "新摘要",
	})
	if status != nil || updated.Title != "新标题" || updated.ItemCount != 2 {
		t.Fatalf("unexpected updated event: event=%#v status=%#v", updated, status)
	}
}

func TestCreateOpinionEventValidationAndAtomicRollback(t *testing.T) {
	client := newOpinionEventTestClient(t)
	ctx := context.Background()
	item1 := createOpinionEventTestItem(t, client, "org-1", "1")
	item2 := createOpinionEventTestItem(t, client, "org-1", "2")
	crossOrgItem := createOpinionEventTestItem(t, client, "org-2", "3")

	_, status := client.CreateOpinionEvent(ctx, CreateOpinionEventInput{
		OrgID: "org-1", CreatorID: "user-1", Title: "duplicate request", ItemIDs: []uint32{item1.ID, item1.ID},
	})
	requireOpinionEventStatusKey(t, status, "app_public_opinion_invalid_argument")
	_, status = client.CreateOpinionEvent(ctx, CreateOpinionEventInput{
		OrgID: "org-1", CreatorID: "user-1", Title: "zero request", ItemIDs: []uint32{0},
	})
	requireOpinionEventStatusKey(t, status, "app_public_opinion_invalid_argument")
	_, status = client.CreateOpinionEvent(ctx, CreateOpinionEventInput{
		OrgID: "org-1", CreatorID: "user-1", Title: "cross org", ItemIDs: []uint32{crossOrgItem.ID},
	})
	requireOpinionEventStatusKey(t, status, "app_public_opinion_event_item_not_available")

	first, status := client.CreateOpinionEvent(ctx, CreateOpinionEventInput{
		OrgID: "org-1", CreatorID: "user-1", Title: "first", ItemIDs: []uint32{item1.ID},
	})
	if status != nil {
		t.Fatalf("create first event: %#v", status)
	}
	_, status = client.CreateOpinionEvent(ctx, CreateOpinionEventInput{
		OrgID: "org-1", CreatorID: "user-1", Title: "must rollback", ItemIDs: []uint32{item1.ID, item2.ID},
	})
	requireOpinionEventStatusKey(t, status, "app_public_opinion_event_item_assigned")

	var eventCount, item2RelationCount int64
	client.db.Model(&model.OpinionEvent{}).Count(&eventCount)
	client.db.Model(&model.OpinionEventItem{}).Where("item_id = ?", item2.ID).Count(&item2RelationCount)
	if eventCount != 1 || item2RelationCount != 0 {
		t.Fatalf("transaction was not rolled back: eventCount=%d item2Relations=%d", eventCount, item2RelationCount)
	}

	second := &model.OpinionEvent{OrgID: "org-1", CreatorID: "user-1", Title: "second", Status: model.OpinionEventStatusDraft}
	if err := client.db.Create(second).Error; err != nil {
		t.Fatalf("create second event: %v", err)
	}
	duplicateRelation := &model.OpinionEventItem{
		OrgID: "org-1", EventID: second.ID, ItemID: item1.ID,
		RelationType: model.OpinionEventRelationTypeManual, CreatorID: "user-1",
	}
	if err := client.db.Create(duplicateRelation).Error; err == nil {
		t.Fatal("expected UNIQUE(item_id) to reject a second relation")
	}
	if first.ItemCount != 1 {
		t.Fatalf("unexpected first event count: %d", first.ItemCount)
	}
}

func TestAddAndRemoveOpinionEventItemsAreAtomic(t *testing.T) {
	client := newOpinionEventTestClient(t)
	ctx := context.Background()
	item1 := createOpinionEventTestItem(t, client, "org-1", "1")
	item2 := createOpinionEventTestItem(t, client, "org-1", "2")
	item3 := createOpinionEventTestItem(t, client, "org-1", "3")
	item4 := createOpinionEventTestItem(t, client, "org-1", "4")
	crossOrgItem := createOpinionEventTestItem(t, client, "org-2", "5")

	event1, status := client.CreateOpinionEvent(ctx, CreateOpinionEventInput{
		OrgID: "org-1", CreatorID: "user-1", Title: "event-1", ItemIDs: []uint32{item1.ID},
	})
	if status != nil {
		t.Fatalf("create event 1: %#v", status)
	}
	event2, status := client.CreateOpinionEvent(ctx, CreateOpinionEventInput{
		OrgID: "org-1", CreatorID: "user-1", Title: "event-2", ItemIDs: []uint32{item3.ID},
	})
	if status != nil {
		t.Fatalf("create event 2: %#v", status)
	}

	detail, status := client.AddOpinionEventItems(ctx, ChangeOpinionEventItemsInput{
		OrgID: "org-1", CreatorID: "user-1", EventID: event1.ID, ItemIDs: []uint32{item2.ID},
	})
	if status != nil || detail.Event.ItemCount != 2 {
		t.Fatalf("add item: detail=%#v status=%#v", detail, status)
	}
	_, status = client.AddOpinionEventItems(ctx, ChangeOpinionEventItemsInput{
		OrgID: "org-1", CreatorID: "user-1", EventID: event1.ID, ItemIDs: []uint32{item3.ID, item4.ID},
	})
	requireOpinionEventStatusKey(t, status, "app_public_opinion_event_item_assigned")
	_, status = client.AddOpinionEventItems(ctx, ChangeOpinionEventItemsInput{
		OrgID: "org-1", CreatorID: "user-1", EventID: event1.ID, ItemIDs: []uint32{crossOrgItem.ID},
	})
	requireOpinionEventStatusKey(t, status, "app_public_opinion_event_item_not_available")
	var item4RelationCount int64
	client.db.Model(&model.OpinionEventItem{}).Where("item_id = ?", item4.ID).Count(&item4RelationCount)
	if item4RelationCount != 0 {
		t.Fatalf("atomic add created relation for item 4: %d", item4RelationCount)
	}

	detail, status = client.RemoveOpinionEventItems(ctx, ChangeOpinionEventItemsInput{
		OrgID: "org-1", EventID: event1.ID, ItemIDs: []uint32{item2.ID},
	})
	if status != nil || detail.Event.ItemCount != 1 {
		t.Fatalf("remove item: detail=%#v status=%#v", detail, status)
	}
	var originalItemCount int64
	client.db.Model(&model.OpinionItem{}).Where("id = ? AND org_id = ?", item2.ID, "org-1").Count(&originalItemCount)
	if originalItemCount != 1 {
		t.Fatal("removing relation deleted the original opinion item")
	}

	_, status = client.RemoveOpinionEventItems(ctx, ChangeOpinionEventItemsInput{
		OrgID: "org-1", EventID: event1.ID, ItemIDs: []uint32{item1.ID, item4.ID},
	})
	requireOpinionEventStatusKey(t, status, "app_public_opinion_event_item_not_related")
	var item1RelationCount int64
	client.db.Model(&model.OpinionEventItem{}).Where("event_id = ? AND item_id = ?", event1.ID, item1.ID).Count(&item1RelationCount)
	if item1RelationCount != 1 {
		t.Fatal("atomic remove deleted a valid relation before rejecting the request")
	}
	_ = event2
}

func TestOpinionEventArchiveAndStatusTransitions(t *testing.T) {
	client := newOpinionEventTestClient(t)
	ctx := context.Background()
	item1 := createOpinionEventTestItem(t, client, "org-1", "1")
	item2 := createOpinionEventTestItem(t, client, "org-1", "2")
	event, status := client.CreateOpinionEvent(ctx, CreateOpinionEventInput{
		OrgID: "org-1", CreatorID: "user-1", Title: "event", ItemIDs: []uint32{item1.ID},
	})
	if status != nil {
		t.Fatalf("create event: %#v", status)
	}

	_, status = client.UpdateOpinionEventStatus(ctx, event.ID, "org-1", model.OpinionEventStatusCompleted)
	requireOpinionEventStatusKey(t, status, "app_public_opinion_event_invalid_status_transition")
	_, status = client.UpdateOpinionEventStatus(ctx, event.ID, "org-1", model.OpinionEventStatusAnalyzing)
	if status != nil {
		t.Fatalf("draft -> analyzing: %#v", status)
	}
	_, status = client.UpdateOpinionEventStatus(ctx, event.ID, "org-1", model.OpinionEventStatusDraft)
	requireOpinionEventStatusKey(t, status, "app_public_opinion_event_invalid_status_transition")
	_, status = client.UpdateOpinionEventStatus(ctx, event.ID, "org-1", model.OpinionEventStatusCompleted)
	if status != nil {
		t.Fatalf("analyzing -> completed: %#v", status)
	}
	_, status = client.UpdateOpinionEventStatus(ctx, event.ID, "org-1", model.OpinionEventStatusArchived)
	if status != nil {
		t.Fatalf("completed -> archived: %#v", status)
	}

	_, status = client.UpdateOpinionEvent(ctx, UpdateOpinionEventInput{
		OrgID: "org-1", EventID: event.ID, Title: "cannot update",
	})
	requireOpinionEventStatusKey(t, status, "app_public_opinion_event_archived")
	_, status = client.AddOpinionEventItems(ctx, ChangeOpinionEventItemsInput{
		OrgID: "org-1", CreatorID: "user-1", EventID: event.ID, ItemIDs: []uint32{item2.ID},
	})
	requireOpinionEventStatusKey(t, status, "app_public_opinion_event_archived")
	_, status = client.RemoveOpinionEventItems(ctx, ChangeOpinionEventItemsInput{
		OrgID: "org-1", EventID: event.ID, ItemIDs: []uint32{item1.ID},
	})
	requireOpinionEventStatusKey(t, status, "app_public_opinion_event_archived")
	_, status = client.UpdateOpinionEventStatus(ctx, event.ID, "org-1", model.OpinionEventStatusAnalyzing)
	requireOpinionEventStatusKey(t, status, "app_public_opinion_event_invalid_status_transition")
	_, status = client.CreateOpinionEvent(ctx, CreateOpinionEventInput{
		OrgID: "org-1", CreatorID: "user-1", Title: "reuse archived item", ItemIDs: []uint32{item1.ID},
	})
	requireOpinionEventStatusKey(t, status, "app_public_opinion_event_item_assigned")
}

func TestOpinionEventStatusTransitionMatrix(t *testing.T) {
	tests := []struct {
		current string
		target  string
		allowed bool
	}{
		{model.OpinionEventStatusDraft, model.OpinionEventStatusAnalyzing, true},
		{model.OpinionEventStatusDraft, model.OpinionEventStatusArchived, true},
		{model.OpinionEventStatusAnalyzing, model.OpinionEventStatusCompleted, true},
		{model.OpinionEventStatusAnalyzing, model.OpinionEventStatusArchived, true},
		{model.OpinionEventStatusCompleted, model.OpinionEventStatusArchived, true},
		{model.OpinionEventStatusDraft, model.OpinionEventStatusCompleted, false},
		{model.OpinionEventStatusAnalyzing, model.OpinionEventStatusDraft, false},
		{model.OpinionEventStatusCompleted, model.OpinionEventStatusAnalyzing, false},
		{model.OpinionEventStatusArchived, model.OpinionEventStatusDraft, false},
		{model.OpinionEventStatusArchived, model.OpinionEventStatusAnalyzing, false},
		{model.OpinionEventStatusArchived, model.OpinionEventStatusCompleted, false},
	}
	for _, test := range tests {
		if actual := canTransitionOpinionEventStatus(test.current, test.target); actual != test.allowed {
			t.Fatalf("transition %s -> %s: expected %v, got %v", test.current, test.target, test.allowed, actual)
		}
	}
}

func TestOpinionEventOrganizationIsolation(t *testing.T) {
	client := newOpinionEventTestClient(t)
	ctx := context.Background()
	item := createOpinionEventTestItem(t, client, "org-1", "1")
	event, status := client.CreateOpinionEvent(ctx, CreateOpinionEventInput{
		OrgID: "org-1", CreatorID: "user-1", Title: "event", ItemIDs: []uint32{item.ID},
	})
	if status != nil {
		t.Fatalf("create event: %#v", status)
	}
	_, status = client.GetOpinionEvent(ctx, event.ID, "org-2")
	requireOpinionEventStatusKey(t, status, "app_public_opinion_event_not_found")
	_, status = client.UpdateOpinionEvent(ctx, UpdateOpinionEventInput{
		OrgID: "org-2", EventID: event.ID, Title: "cross org update",
	})
	requireOpinionEventStatusKey(t, status, "app_public_opinion_event_not_found")
	_, status = client.RemoveOpinionEventItems(ctx, ChangeOpinionEventItemsInput{
		OrgID: "org-2", EventID: event.ID, ItemIDs: []uint32{item.ID},
	})
	requireOpinionEventStatusKey(t, status, "app_public_opinion_event_not_found")
}
