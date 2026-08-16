package app

import "testing"

func TestParsePublicOpinionIDs(t *testing.T) {
	ids, err := parsePublicOpinionIDs([]string{"2", "1"}, "itemIds")
	if err != nil || len(ids) != 2 || ids[0] != 2 || ids[1] != 1 {
		t.Fatalf("unexpected parsed ids: ids=%v err=%v", ids, err)
	}
	if _, err := parsePublicOpinionIDs([]string{"invalid"}, "itemIds"); err == nil {
		t.Fatal("expected invalid string id to fail")
	}
	if _, err := parsePublicOpinionIDs([]string{"1", "1"}, "itemIds"); err == nil {
		t.Fatal("expected duplicate ids to fail")
	}
	if _, err := parsePublicOpinionIDs([]string{"0"}, "itemIds"); err == nil {
		t.Fatal("expected zero id to fail")
	}
}
