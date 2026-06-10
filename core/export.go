package transit

// SetCacheDir 设置缓存目录（Android 应用数据目录）
func SetCacheDir(dir string) {
	dataDir = dir
}

// ClearCache 清除缓存
func ClearCache() {
	setProgress("缓存已清除")
}

var dataDir string
