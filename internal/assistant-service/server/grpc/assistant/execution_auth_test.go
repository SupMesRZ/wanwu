package assistant

import (
	"context"
	"testing"

	"google.golang.org/grpc/metadata"
)

func TestExecutionAuthHeadersFromIncomingMetadata(t *testing.T) {
	const authorization = "Bearer phase-2a-jwt"
	ctx := metadata.NewIncomingContext(context.Background(), metadata.Pairs(
		executionAuthorizationMetadata, authorization,
		executionOrgIDMetadata, "org-a",
	))
	headers := executionAuthHeaders(ctx)
	if headers["Authorization"] != authorization || headers["X-Org-Id"] != "org-a" || len(headers) != 2 {
		t.Fatalf("unexpected execution headers: %v", headers)
	}

	malformed := metadata.NewIncomingContext(context.Background(), metadata.Pairs(
		executionAuthorizationMetadata, "Bearer invalid token",
		executionOrgIDMetadata, "org-a",
	))
	if headers := executionAuthHeaders(malformed); headers != nil {
		t.Fatalf("malformed authorization must be rejected: %v", headers)
	}
}
