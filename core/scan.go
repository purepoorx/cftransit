package transit

import (
	"encoding/json"
	"fmt"
	"time"
)

// ScanResult 扫描结果
type ScanResult struct {
	IP            string `json:"ip"`
	Port          int    `json:"port"`
	TLS           bool   `json:"tls"`
	Bandwidth     int    `json:"bandwidth"`
	RealBandwidth int    `json:"realBandwidth"`
	MaxSpeed      int    `json:"maxSpeed"`
	LatencyMs     int    `json:"latencyMs"`
	DC            string `json:"dc"`
	City          string `json:"city"`
	Region        string `json:"region"`
	Country       string `json:"country"`
	Elapsed       int    `json:"elapsed"`
	Error         string `json:"error"`
}

const maxRounds = 5

// GetIPs 运行 IP 优选扫描，返回结果 JSON
// bandwidth: 期望带宽（Mbps），设为 0 则使用默认 1 Mbps
// country: 按国家筛选（如 "US"），空字符串表示全部
// dc: 按数据中心筛选（如 "SJC"），空字符串表示全部
// tlsMode: 0=全部, 1=仅TLS, 2=仅非TLS
func GetIPs(bandwidth int, country string, dc string, tlsMode int) string {
	setProgress("正在初始化...")
	resetCancel()

	if bandwidth <= 0 {
		bandwidth = 1
	}
	speedTarget := bandwidth * 128 // 转为 kB/s

	startTime := time.Now()

	// 获取测速路径（不含协议）
	setProgress("正在获取测速路径...")
	speedTestPath, err := fetchSpeedTestPath()
	if err != nil {
		return errorResult(bandwidth, startTime, err.Error())
	}

	var bestResult *ScanResult

	for round := 1; round <= maxRounds; round++ {
		if isCancelled() {
			return errorResult(bandwidth, startTime, "扫描已取消")
		}

		setProgress(fmt.Sprintf("第 %d/%d 轮：正在获取代理列表...", round, maxRounds))
		proxies, err := fetchProxies(50, country, dc, tlsMode)
		if err != nil {
			if round == maxRounds {
				return errorResult(bandwidth, startTime, err.Error())
			}
			setProgress(fmt.Sprintf("获取代理失败: %s，进入下一轮", err.Error()))
			continue
		}
		if len(proxies) == 0 {
			setProgress("代理列表为空，进入下一轮...")
			continue
		}

		setProgress(fmt.Sprintf("第 %d 轮：已获取 %d 个代理，开始 RTT 测试...", round, len(proxies)))
		rttResults := runRTTTest(proxies, 20)
		if isCancelled() {
			return errorResult(bandwidth, startTime, "扫描已取消")
		}
		if len(rttResults) == 0 {
			setProgress(fmt.Sprintf("第 %d 轮：所有代理 RTT 失败，进入下一轮", round))
			continue
		}

		// 逐个速度测试
		for _, r := range rttResults {
			if isCancelled() {
				return errorResult(bandwidth, startTime, "扫描已取消")
			}

			setProgress(fmt.Sprintf("正在测速 %s:%d (延迟 %dms)", r.IP, r.Port, r.LatencyMs))
			maxSpeed, tcpMs, speedDC := runSpeedTest(r.IP, r.Port, r.TLS, speedTestPath)

			// 查找代理的地理信息
			var pCity, pRegion, pCountry string
			for _, p := range proxies {
				if p.IP == r.IP {
					pCity = p.City
					pRegion = p.Region
					pCountry = p.Country
					break
				}
			}

			result := &ScanResult{
				IP:            r.IP,
				Port:          r.Port,
				TLS:           r.TLS,
				Bandwidth:     bandwidth,
				RealBandwidth: maxSpeed / 128,
				MaxSpeed:      maxSpeed,
				LatencyMs:     tcpMs,
				DC:            speedDC,
				City:          pCity,
				Region:        pRegion,
				Country:       pCountry,
				Elapsed:       int(time.Since(startTime).Seconds()),
			}

			setProgress(fmt.Sprintf("%s:%d 峰值 %d kB/s", r.IP, r.Port, maxSpeed))

			// 记录最优结果
			if bestResult == nil || maxSpeed > bestResult.MaxSpeed {
				bestResult = result
			}

			// 达标立即返回
			if maxSpeed >= speedTarget {
				setProgress(fmt.Sprintf("找到优选 IP: %s:%d, 速度 %d kB/s", r.IP, r.Port, maxSpeed))
				result.Elapsed = int(time.Since(startTime).Seconds())
				b, _ := json.Marshal(result)
				setProgress(fmt.Sprintf("扫描完成，用时 %d 秒", result.Elapsed))
				return string(b)
			}
		}

		setProgress(fmt.Sprintf("第 %d 轮未找到达标 IP，继续...", round))
	}

	// 所有轮次结束，返回最优结果（即使未达标）
	if bestResult != nil {
		bestResult.Elapsed = int(time.Since(startTime).Seconds())
		bestResult.Error = fmt.Sprintf("经过 %d 轮测试，未找到符合 %d Mbps 的 IP，以下为最优结果", maxRounds, bandwidth)
		setProgress(fmt.Sprintf("扫描完成（未达标），用时 %d 秒", bestResult.Elapsed))
		b, _ := json.Marshal(bestResult)
		return string(b)
	}

	return errorResult(bandwidth, startTime, fmt.Sprintf("经过 %d 轮测试，所有代理均无法连接", maxRounds))
}

func errorResult(bandwidth int, startTime time.Time, errMsg string) string {
	elapsed := int(time.Since(startTime).Seconds())
	setProgress(fmt.Sprintf("扫描失败: %s（用时 %d 秒）", errMsg, elapsed))
	result := ScanResult{
		Bandwidth: bandwidth,
		Elapsed:   elapsed,
		Error:     errMsg,
	}
	b, _ := json.Marshal(result)
	return string(b)
}
