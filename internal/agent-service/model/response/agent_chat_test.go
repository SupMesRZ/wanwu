package response

import (
	"errors"
	"testing"
)

func TestBuildErrMsgDirect(t *testing.T) {
	const want = "学生校园业务工具暂时不可用，请稍后重试。"
	response, message := buildErrMsg(errors.New("[direct]" + want))
	if response != want || message != "" {
		t.Fatalf("unexpected direct error response: response=%q message=%q", response, message)
	}
}
