package handlers

import (
	"net/http"

	"cftransit-server/models"

	"github.com/gin-gonic/gin"
)

type dcStat struct {
	DC    string `json:"dc"`
	Count int64  `json:"count"`
}

type regionStat struct {
	Region string `json:"region"`
	Count  int64  `json:"count"`
}

type countryStat struct {
	Country string `json:"country"`
	Count   int64  `json:"count"`
}

func GetStats(c *gin.Context) {
	var totalProxies int64
	models.DB.Table("proxies").Count(&totalProxies)

	var tlsCount int64
	models.DB.Table("proxies").Where("tls = true").Count(&tlsCount)

	var dcStats []dcStat
	models.DB.Table("proxies").
		Select("dc, count(*) as count").
		Where("dc IS NOT NULL AND dc != ''").
		Group("dc").
		Order("count DESC").
		Limit(50).
		Find(&dcStats)

	var regionStats []regionStat
	models.DB.Table("proxies").
		Select("region, count(*) as count").
		Where("region IS NOT NULL AND region != ''").
		Group("region").
		Order("count DESC").
		Limit(20).
		Find(&regionStats)

	var countryStats []countryStat
	models.DB.Table("proxies").
		Select("ip_info.country, count(*) as count").
		Joins("JOIN ip_info ON proxies.ip::text = ip_info.ip::text").
		Where("ip_info.country IS NOT NULL AND ip_info.country != ''").
		Group("ip_info.country").
		Order("count DESC").
		Limit(50).
		Find(&countryStats)

	c.JSON(http.StatusOK, gin.H{
		"totalProxies": totalProxies,
		"tlsCount":     tlsCount,
		"dcStats":      dcStats,
		"regionStats":  regionStats,
		"countryStats": countryStats,
	})
}
