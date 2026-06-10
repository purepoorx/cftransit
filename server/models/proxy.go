package models

import "time"

type Proxy struct {
	ID         int64      `json:"id" gorm:"column:id;primaryKey"`
	IP         string     `json:"ip" gorm:"column:ip;type:inet"`
	Port       int        `json:"port" gorm:"column:port"`
	SelfEgress bool       `json:"selfEgress" gorm:"column:self_egress"`
	TLS        bool       `json:"tls" gorm:"column:tls"`
	DC         string     `json:"dc" gorm:"column:dc"`
	Region     string     `json:"region" gorm:"column:region"`
	City       string     `json:"city" gorm:"column:city"`
	LatencyMs  int        `json:"latencyMs" gorm:"column:latency_ms"`
	SourcePort int        `json:"sourcePort" gorm:"column:source_port"`
	CreatedAt  time.Time  `json:"createdAt" gorm:"column:created_at"`
	UpdatedAt  *time.Time `json:"updatedAt" gorm:"column:updated_at"`
}

func (Proxy) TableName() string {
	return "proxies"
}

// ProxyWithInfo 代理 + IP 地理信息（JOIN 结果）
type ProxyWithInfo struct {
	Proxy
	Country    string `json:"country"`
	Org        string `json:"org"`
	AsnName    string `json:"asnName"`
	AsnNumber  int    `json:"asnNumber"`
}
