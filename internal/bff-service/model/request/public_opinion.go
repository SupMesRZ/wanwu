package request

type PublicOpinionListReq struct {
	Keyword    string `json:"keyword"`
	StartTime  string `json:"startTime"`
	EndTime    string `json:"endTime"`
	SourceType string `json:"sourceType"`
	Topic      string `json:"topic"`
	PageNo     int32  `json:"pageNo"`
	PageSize   int32  `json:"pageSize"`
}

func (r *PublicOpinionListReq) Check() error { return nil }

type PublicOpinionIDReq struct {
	ID string `uri:"id" validate:"required"`
}

func (r *PublicOpinionIDReq) Check() error { return nil }
