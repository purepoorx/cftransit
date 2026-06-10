package main

import (
	"context"
	"fmt"
	"net/http"
	"os"
	"os/signal"
	"path/filepath"
	"syscall"
	"time"

	"cftransit-server/handlers"
	"cftransit-server/middleware"
	"cftransit-server/models"

	"github.com/gin-gonic/gin"
)

func main() {
	// 加载配置
	exe, _ := os.Executable()
	cfgPath := filepath.Join(filepath.Dir(exe), "config.yaml")
	// 开发环境 fallback
	if _, err := os.Stat(cfgPath); err != nil {
		cfgPath = "config.yaml"
	}
	cfg := LoadConfig(cfgPath)

	// 初始化数据库
	models.InitDB(cfg.DSN)

	// Gin 路由
	gin.SetMode(gin.ReleaseMode)
	r := gin.Default()
	r.Use(middleware.CORS())

	api := r.Group("/")
	api.Use(middleware.APIKey(cfg.APIKey))
	{
		api.GET("/proxies", handlers.GetProxies)
		api.GET("/stats", handlers.GetStats)
		api.GET("/speedtest/url", handlers.GetSpeedtestURL(cfg.SpeedtestURL))
	}

	// 启动 HTTP 服务
	addr := fmt.Sprintf(":%d", cfg.Port)
	srv := &http.Server{Addr: addr, Handler: r}

	go func() {
		fmt.Printf("[server] 监听 %s\n", addr)
		if err := srv.ListenAndServe(); err != nil && err != http.ErrServerClosed {
			fmt.Printf("[server] 启动失败: %v\n", err)
			os.Exit(1)
		}
	}()

	// 优雅关闭
	quit := make(chan os.Signal, 1)
	signal.Notify(quit, syscall.SIGINT, syscall.SIGTERM)
	<-quit
	fmt.Println("[server] 正在关闭...")

	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()
	srv.Shutdown(ctx)
	fmt.Println("[server] 已关闭")
}
