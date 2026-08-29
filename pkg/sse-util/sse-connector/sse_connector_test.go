package sse_connector

import "testing"

func TestCloseWithoutSession(t *testing.T) {
	if err := Close(nil); err != nil {
		t.Fatal(err)
	}
}
