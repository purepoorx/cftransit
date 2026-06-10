package models

import "time"

type IPInfo struct {
	IP            string     `json:"ip" gorm:"column:ip;type:inet;primaryKey"`
	City          string     `json:"city" gorm:"column:city"`
	Region        string     `json:"region" gorm:"column:region"`
	Country       string     `json:"country" gorm:"column:country"`
	Org           string     `json:"org" gorm:"column:org"`
	Timezone      string     `json:"timezone" gorm:"column:timezone"`
	AsnNumber     int        `json:"asnNumber" gorm:"column:asn_number"`
	AsnName       string     `json:"asnName" gorm:"column:asn_name"`
	AsnDomain     string     `json:"asnDomain" gorm:"column:asn_domain"`
	AsnRoute      string     `json:"asnRoute" gorm:"column:asn_route"`
	AsnType       string     `json:"asnType" gorm:"column:asn_type"`
	CompanyName   string     `json:"companyName" gorm:"column:company_name"`
	CompanyDomain string     `json:"companyDomain" gorm:"column:company_domain"`
	CompanyType   string     `json:"companyType" gorm:"column:company_type"`
	UpdatedAt     *time.Time `json:"updatedAt" gorm:"column:updated_at"`
}

func (IPInfo) TableName() string {
	return "ip_info"
}
