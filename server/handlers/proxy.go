package handlers

import (
	"net/http"
	"strconv"

	"cftransit-server/models"

	"github.com/gin-gonic/gin"
)

func GetProxies(c *gin.Context) {
	page, _ := strconv.Atoi(c.DefaultQuery("page", "1"))
	perPage, _ := strconv.Atoi(c.DefaultQuery("per_page", "50"))
	sample, _ := strconv.Atoi(c.Query("sample"))
	dc := c.Query("dc")
	region := c.Query("region")
	country := c.Query("country")
	tlsStr := c.Query("tls")

	if page < 1 {
		page = 1
	}
	if perPage < 1 || perPage > 200 {
		perPage = 50
	}

	query := models.DB.Table("proxies").
		Select("proxies.*, COALESCE(ip_info.country, '') as country, COALESCE(ip_info.org, '') as org, COALESCE(ip_info.asn_name, '') as asn_name, COALESCE(ip_info.asn_number, 0) as asn_number").
		Joins("LEFT JOIN ip_info ON proxies.ip::text = ip_info.ip::text")

	if dc != "" {
		query = query.Where("proxies.dc = ?", dc)
	}
	if region != "" {
		query = query.Where("proxies.region = ?", region)
	}
	if country != "" {
		query = query.Where("ip_info.country = ?", country)
	}
	if tlsStr != "" {
		if tlsStr == "true" {
			query = query.Where("proxies.tls = true")
		} else if tlsStr == "false" {
			query = query.Where("proxies.tls = false")
		}
	}

	// 随机采样模式
	if sample > 0 {
		var results []models.ProxyWithInfo
		query.Order("RANDOM()").Limit(sample).Find(&results)
		c.JSON(http.StatusOK, gin.H{"data": results, "total": len(results)})
		return
	}

	// 分页模式
	var total int64
	countQuery := models.DB.Table("proxies").
		Joins("LEFT JOIN ip_info ON proxies.ip::text = ip_info.ip::text")
	if dc != "" {
		countQuery = countQuery.Where("proxies.dc = ?", dc)
	}
	if region != "" {
		countQuery = countQuery.Where("proxies.region = ?", region)
	}
	if country != "" {
		countQuery = countQuery.Where("ip_info.country = ?", country)
	}
	if tlsStr == "true" {
		countQuery = countQuery.Where("proxies.tls = true")
	} else if tlsStr == "false" {
		countQuery = countQuery.Where("proxies.tls = false")
	}
	countQuery.Count(&total)

	var results []models.ProxyWithInfo
	query.Order("proxies.id DESC").
		Offset((page - 1) * perPage).
		Limit(perPage).
		Find(&results)

	c.JSON(http.StatusOK, gin.H{
		"data":     results,
		"total":    total,
		"page":     page,
		"per_page": perPage,
	})
}
