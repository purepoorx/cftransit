package transit

import (
	"context"
	"crypto/tls"
	"net"
	"net/http"
	"strconv"
	"strings"
	"time"
)

// runSpeedTest 对目标 IP 进行 HTTP 下载速度测试
// 返回 (峰值速度 kB/s, TCP延迟ms, DC三字码)
func runSpeedTest(ip string, port int, useTLS bool, speedTestURL string) (int, int, string) {
	var tcpMs int
	transport := &http.Transport{
		DialContext: func(ctx context.Context, network, addr string) (net.Conn, error) {
			start := time.Now()
			conn, err := (&net.Dialer{Timeout: 3 * time.Second}).DialContext(ctx, "tcp", net.JoinHostPort(ip, strconv.Itoa(port)))
			if err == nil {
				tcpMs = int(time.Since(start).Milliseconds())
			}
			return conn, err
		},
	}
	if useTLS {
		transport.TLSClientConfig = &tls.Config{InsecureSkipVerify: true}
	}
	client := &http.Client{
		Transport: transport,
		Timeout:   8 * time.Second,
	}

	req, _ := http.NewRequestWithContext(scanCtx(), "GET", speedTestURL, nil)
	resp, err := client.Do(req)
	if err != nil {
		return 0, 0, ""
	}
	defer resp.Body.Close()

	// 从 CF-RAY 提取 DC（如果有）
	dc := extractDataCenter(resp.Header.Get("CF-RAY"))

	buf := make([]byte, 32*1024)
	var windowBytes int64
	windowStart := time.Now()
	maxSpeed := 0

	for {
		n, err := resp.Body.Read(buf)
		windowBytes += int64(n)
		if err != nil {
			break
		}
		elapsed := time.Since(windowStart).Seconds()
		if elapsed >= 1.0 {
			speedKB := int(float64(windowBytes) / 1024 / elapsed)
			if speedKB > maxSpeed {
				maxSpeed = speedKB
			}
			windowBytes = 0
			windowStart = time.Now()
		}
	}

	return maxSpeed, tcpMs, dc
}

// extractDataCenter 从 CF-RAY 头提取三字码头
func extractDataCenter(cfRay string) string {
	if cfRay == "" {
		return ""
	}
	parts := strings.Split(cfRay, "-")
	if len(parts) < 2 {
		return ""
	}
	return strings.TrimSpace(parts[len(parts)-1])
}
