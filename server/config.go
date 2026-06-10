package main

import (
	"os"
	"strconv"
	"strings"

	"gopkg.in/yaml.v3"
)

type Config struct {
	Port          int    `yaml:"port"`
	DSN           string `yaml:"dsn"`
	APIKey        string `yaml:"api_key"`
	SpeedtestPath string `yaml:"speedtest_path"`
}

func LoadConfig(path string) *Config {
	cfg := &Config{
		Port:          8080,
		APIKey:        "changeme",
		SpeedtestPath: "speed.cloudflare.com/__down?bytes=25000000",
	}

	data, err := os.ReadFile(path)
	if err == nil {
		_ = yaml.Unmarshal(data, cfg)
	}

	// 环境变量覆盖
	if v := os.Getenv("CFTRANSIT_PORT"); v != "" {
		if p, err := strconv.Atoi(v); err == nil {
			cfg.Port = p
		}
	}
	if v := os.Getenv("CFTRANSIT_DSN"); v != "" {
		cfg.DSN = v
	}
	if v := os.Getenv("CFTRANSIT_API_KEY"); v != "" {
		cfg.APIKey = v
	}
	if v := os.Getenv("CFTRANSIT_SPEEDTEST_PATH"); v != "" {
		cfg.SpeedtestPath = v
	}

	// GORM postgres driver 需要 postgres:// 前缀
	cfg.DSN = strings.Replace(cfg.DSN, "postgresql://", "postgres://", 1)

	return cfg
}
