package transit

import (
	"crypto/tls"
	"fmt"
	"net"
	"sort"
	"strconv"
	"sync"
	"time"
)

// RTTResult RTT 测试结果
type RTTResult struct {
	IP        string
	Port      int
	TLS       bool
	LatencyMs int
}

// testRTT 测试单个代理的 RTT（TCP 3 次握手取平均）
func testRTT(ip string, port int, useTLS bool) int {
	var totalMs int
	for range 3 {
		start := time.Now()
		d := net.Dialer{Timeout: 1 * time.Second}
		conn, err := d.DialContext(scanCtx(), "tcp", net.JoinHostPort(ip, strconv.Itoa(port)))
		if err != nil {
			return 0
		}
		tcpDuration := time.Since(start)

		conn.SetDeadline(start.Add(2 * time.Second))

		if useTLS {
			tlsConn := tls.Client(conn, &tls.Config{InsecureSkipVerify: true})
			if err := tlsConn.Handshake(); err != nil {
				conn.Close()
				return 0
			}
			tlsConn.Close()
		} else {
			conn.Close()
		}

		totalMs += int(tcpDuration.Milliseconds())
	}
	return totalMs / 3
}

// runRTTTest 并发 RTT 测试，保留延迟最低的前 10 个
func runRTTTest(proxies []ProxyInfo, taskNum int) []RTTResult {
	if len(proxies) < taskNum {
		taskNum = len(proxies)
	}

	var wg sync.WaitGroup
	resultChan := make(chan RTTResult, len(proxies))
	sem := make(chan struct{}, taskNum)
	var count int
	var mu sync.Mutex
	total := len(proxies)

	for _, p := range proxies {
		if isCancelled() {
			break
		}
		wg.Add(1)
		sem <- struct{}{}
		go func(p ProxyInfo) {
			defer func() {
				<-sem
				wg.Done()
				mu.Lock()
				count++
				cur := count
				mu.Unlock()
				if cur%10 == 0 || cur == total {
					setProgress(fmt.Sprintf("RTT 测试进度: %d/%d", cur, total))
				}
			}()

			if isCancelled() {
				return
			}
			avgMs := testRTT(p.IP, p.Port, p.TLS)
			if avgMs > 0 {
				resultChan <- RTTResult{IP: p.IP, Port: p.Port, TLS: p.TLS, LatencyMs: avgMs}
			}
		}(p)
	}

	go func() {
		wg.Wait()
		close(resultChan)
	}()

	var results []RTTResult
	for r := range resultChan {
		results = append(results, r)
	}

	if isCancelled() {
		return nil
	}

	sort.Slice(results, func(i, j int) bool {
		return results[i].LatencyMs < results[j].LatencyMs
	})

	if len(results) > 10 {
		setProgress(fmt.Sprintf("RTT 测试完成，%d/%d 有效，保留延迟最低的 10 个", len(results), total))
		results = results[:10]
	} else {
		setProgress(fmt.Sprintf("RTT 测试完成，%d/%d 有效", len(results), total))
	}
	return results
}
