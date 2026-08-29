package campusstudent

import (
	"encoding/json"
	"testing"
)

func TestToolDefinitions(t *testing.T) {
	defs := ToolDefinitions("http://bff")
	if len(defs) != 5 {
		t.Fatalf("got %d tools", len(defs))
	}
	for _, def := range defs {
		var doc map[string]any
		if err := json.Unmarshal([]byte(def.Schema), &doc); err != nil {
			t.Fatal(err)
		}
		if doc["openapi"] != "3.0.0" {
			t.Fatalf("%s missing openapi", def.Name)
		}
	}
}
