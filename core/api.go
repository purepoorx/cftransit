package transit

import (
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"time"
)

// ProxyInfo 从服务端获取的代理信息
type ProxyInfo struct {
	IP       string `json:"ip"`
	Port     int    `json:"port"`
	TLS      bool   `json:"tls"`
	DC       string `json:"dc"`
	Region   string `json:"region"`
	City     string `json:"city"`
	Country  string `json:"country"`
}

var httpClient = &http.Client{Timeout: 15 * time.Second}

func fetchProxies(sample int) ([]ProxyInfo, error) {
	base := getAPIBaseURL()
	key := getAPIKey()
	if base == "" {
		return nil, fmt.Errorf("未配置 API 地址")
	}

	url := fmt.Sprintf("%s/proxies?sample=%d", base, sample)
	req, _ := http.NewRequestWithContext(scanCtx(), "GET", url, nil)
	req.Header.Set("X-API-Key", key)

	resp, err := httpClient.Do(req)
	if err != nil {
		return nil, fmt.Errorf("获取代理列表失败: %w", err)
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		body, _ := io.ReadAll(io.LimitReader(resp.Body, 1024))
		return nil, fmt.Errorf("服务端返回 %d: %s", resp.StatusCode, string(body))
	}

	var result struct {
		Data  []ProxyInfo `json:"data"`
		Total int         `json:"total"`
	}
	if err := json.NewDecoder(resp.Body).Decode(&result); err != nil {
		return nil, fmt.Errorf("解析代理列表失败: %w", err)
	}

	return result.Data, nil
}

func fetchSpeedTestURL() (string, error) {
	base := getAPIBaseURL()
	key := getAPIKey()
	if base == "" {
		return "", fmt.Errorf("未配置 API 地址")
	}

	url := fmt.Sprintf("%s/speedtest/url", base)
	req, _ := http.NewRequestWithContext(scanCtx(), "GET", url, nil)
	req.Header.Set("X-API-Key", key)

	resp, err := httpClient.Do(req)
	if err != nil {
		return "", fmt.Errorf("获取测速 URL 失败: %w", err)
	}
	defer resp.Body.Close()

	var result struct {
		URL string `json:"url"`
	}
	if err := json.NewDecoder(resp.Body).Decode(&result); err != nil {
		return "", fmt.Errorf("解析测速 URL 失败: %w", err)
	}

	return result.URL, nil
}

// TestConnection 测试与服务端的连接，返回 JSON 结果
func TestConnection() string {
	base := getAPIBaseURL()
	key := getAPIKey()
	if base == "" {
		return `{"ok":false,"error":"未配置 API 地址"}`
	}

	url := fmt.Sprintf("%s/stats", base)
	req, _ := http.NewRequest("GET", url, nil)
	req.Header.Set("X-API-Key", key)

	client := &http.Client{Timeout: 10 * time.Second}
	resp, err := client.Do(req)
	if err != nil {
		return fmt.Sprintf(`{"ok":false,"error":"连接失败: %s"}`, err.Error())
	}
	defer resp.Body.Close()

	if resp.StatusCode == http.StatusUnauthorized {
		return `{"ok":false,"error":"API Key 无效"}`
	}
	if resp.StatusCode != http.StatusOK {
		return fmt.Sprintf(`{"ok":false,"error":"服务端返回 %d"}`, resp.StatusCode)
	}

	var stats json.RawMessage
	json.NewDecoder(resp.Body).Decode(&stats)
	b, _ := json.Marshal(map[string]interface{}{"ok": true, "stats": stats})
	return string(b)
}
