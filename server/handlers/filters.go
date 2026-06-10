package handlers

import (
	"net/http"

	"cftransit-server/models"

	"github.com/gin-gonic/gin"
)

// GetFilters 返回筛选选项（轻量级，不做 JOIN）
// 用于 Android 端加载 Spinner 选项
func GetFilters(c *gin.Context) {
	var dcList []string
	models.DB.Table("proxies").
		Distinct("dc").
		Where("dc IS NOT NULL AND dc != ''").
		Order("dc").
		Pluck("dc", &dcList)

	var countryList []string
	models.DB.Table("ip_info").
		Distinct("country").
		Where("country IS NOT NULL AND country != ''").
		Order("country").
		Pluck("country", &countryList)

	c.JSON(http.StatusOK, gin.H{
		"dcList":      dcList,
		"countryList": countryList,
	})
}
