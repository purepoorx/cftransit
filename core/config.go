package transit

import "sync"

var (
	apiBaseURL string
	apiKey     string
	configMu   sync.RWMutex
)

// SetAPIConfig 设置 API 配置（Android 端调用）
func SetAPIConfig(baseURL, key string) {
	configMu.Lock()
	apiBaseURL = baseURL
	apiKey = key
	configMu.Unlock()
}

func getAPIBaseURL() string {
	configMu.RLock()
	defer configMu.RUnlock()
	return apiBaseURL
}

func getAPIKey() string {
	configMu.RLock()
	defer configMu.RUnlock()
	return apiKey
}
