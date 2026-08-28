package campusstudent

import (
	"encoding/json"
	fmt "fmt"
)

const MCPCode = "campus_student"

type ToolDefinition struct {
	Name        string
	Description string
	Schema      string
}

func ToolDefinitions(endpoint string) []ToolDefinition {
	return []ToolDefinition{
		{Name: "query_my_schedule", Description: "Query the authenticated student's own schedule", Schema: schema(endpoint, "query_my_schedule", map[string]any{"date": map[string]any{"type": "string", "description": "Calendar date in YYYY-MM-DD format"}, "week": map[string]any{"type": "string", "description": "ISO week in YYYY-Www format"}})},
		{Name: "query_my_exam_schedule", Description: "Query the authenticated student's own exam schedule", Schema: schema(endpoint, "query_my_exam_schedule", map[string]any{"term": map[string]any{"type": "string", "description": "Academic term"}})},
		{Name: "query_my_score", Description: "Query the authenticated student's own scores", Schema: schema(endpoint, "query_my_score", map[string]any{"term": map[string]any{"type": "string", "description": "Academic term"}})},
		{Name: "query_my_leave_records", Description: "Query the authenticated student's own leave records", Schema: schema(endpoint, "query_my_leave_records", map[string]any{})},
		{Name: "query_my_learning_summary", Description: "Query the authenticated student's own learning summary", Schema: schema(endpoint, "query_my_learning_summary", map[string]any{"term": map[string]any{"type": "string", "description": "Academic term"}})},
	}
}

func schema(endpoint, name string, properties map[string]any) string {
	b, _ := json.Marshal(map[string]any{
		"openapi": "3.0.0", "info": map[string]any{"title": "Campus Student MCP", "version": "1.0.0"},
		"servers": []any{map[string]any{"url": endpoint}},
		"paths": map[string]any{"/" + name: map[string]any{"post": map[string]any{
			"operationId": name, "summary": name, "description": name,
			"requestBody": map[string]any{"content": map[string]any{"application/json": map[string]any{"schema": map[string]any{"type": "object", "properties": properties, "additionalProperties": false}}}},
			"responses":   map[string]any{"200": map[string]any{"description": "OK"}},
		}}},
	})
	return string(b)
}

func Endpoint(base string) string { return fmt.Sprintf("%s/v1/campus/student/mcp", base) }
