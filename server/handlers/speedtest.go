package handlers

import (
	"net/http"

	"github.com/gin-gonic/gin"
)

func GetSpeedtestURL(path string) gin.HandlerFunc {
	return func(c *gin.Context) {
		c.JSON(http.StatusOK, gin.H{"path": path})
	}
}
