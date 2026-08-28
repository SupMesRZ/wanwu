package service

import (
	"context"
	"testing"

	"google.golang.org/grpc/metadata"
)

func TestWithExecutionAuthMetadata(t *testing.T) {
	const authorization = "Bearer phase-2a-jwt"
	ctx := metadata.NewOutgoingContext(context.Background(), metadata.Pairs(executionAuthorizationMetadata, "Bearer stale"))
	ctx = withExecutionAuthMetadata(ctx, authorization, "org-a")
	md, ok := metadata.FromOutgoingContext(ctx)
	if !ok || len(md.Get(executionAuthorizationMetadata)) != 1 || md.Get(executionAuthorizationMetadata)[0] != authorization || md.Get(executionOrgIDMetadata)[0] != "org-a" {
		t.Fatalf("execution metadata missing: %v", md)
	}

	rejected := withExecutionAuthMetadata(context.Background(), authorization+"\r\nX-Evil: yes", "org-a")
	if _, ok := metadata.FromOutgoingContext(rejected); ok {
		t.Fatal("CRLF authorization must not enter metadata")
	}
}
