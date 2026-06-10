package middleware

import (
	"net/http"

	"github.com/gin-gonic/gin"
)

func APIKey(key string) gin.HandlerFunc {
	return func(c *gin.Context) {
		k := c.GetHeader("X-API-Key")
		if k == "" {
			k = c.Query("api_key")
		}
		if k != key {
			c.AbortWithStatusJSON(http.StatusUnauthorized, gin.H{"error": "invalid api key"})
			return
		}
		c.Next()
	}
}
