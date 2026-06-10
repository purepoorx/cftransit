package handlers

import (
	"net/http"

	"github.com/gin-gonic/gin"
)

func GetSpeedtestURL(url string) gin.HandlerFunc {
	return func(c *gin.Context) {
		c.JSON(http.StatusOK, gin.H{"url": url})
	}
}
