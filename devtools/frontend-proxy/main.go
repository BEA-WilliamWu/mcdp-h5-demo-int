package main

import (
	"bytes"
	"compress/gzip"
	"compress/zlib"
	"crypto/rand"
	"crypto/tls"
	"crypto/x509"
	"encoding/hex"
	"encoding/json"
	"fmt"
	"io"
	"log"
	"net/http"
	"net/url"
	"os"

	//"net/http/cookiejar"
	"strings"
	"sync"
	"time"
)

////////////////////////////////////////////////////////////
// CONFIG STRUCT
////////////////////////////////////////////////////////////

type Config struct {
	BCOBaseURL          string
	ListenPort          string
	UserAgent           string
	DebugDisableBCOAuth bool
	// BCM proxy config
	BCMBaseURL      string
	IBMClientID     string
	IBMClientSecret string
	MTLSCertFile    string
	MTLSKeyFile     string
}

func envBool(name string, defaultValue bool) bool {
	value := strings.ToLower(strings.TrimSpace(os.Getenv(name)))
	if value == "" {
		return defaultValue
	}

	switch value {
	case "1", "true", "yes", "on":
		return true
	case "0", "false", "no", "off":
		return false
	default:
		log.Printf("Invalid boolean value for %s=%q; using default %t", name, value, defaultValue)
		return defaultValue
	}
}

////////////////////////////////////////////////////////////
// HEADER CONSTANTS - Keep original case for comparison
////////////////////////////////////////////////////////////

const (
	HeaderRSAKeyIndicator = "RSAKeyIndicator"
	HeaderLoginType       = "loginType"
	// HeaderToken is no longer forwarded in proxy handler, but may be used in login
	HeaderToken              = "token"
	HeaderCookie             = "Cookie"
	HeaderContentType        = "Content-Type"
	HeaderAccept             = "Accept"
	HeaderAcceptEncoding     = "Accept-Encoding"
	HeaderConnection         = "Connection"
	HeaderLocale             = "Locale"
	HeaderXUserID            = "X-User-Id"
	HeaderXNonceCount        = "x-noncecount"
	HeaderXNonce             = "x-nonce"
	HeaderLoginAuthType      = "Login-Auth-Type"
	HeaderLoginAuthData      = "Login-Auth-Data"
	HeaderLoginChannel       = "Login-Channel"
	HeaderXChallenge         = "X-Challenge"
	HeaderXChallengeResponse = "X-Challenge_Response"
	HeaderMacKey             = "MacKey"
	HeaderMacModulus         = "MacModulus"
	HeaderMacEncryptedData   = "MacEncryptedData"
	HeaderMacRSAIndicator    = "MacRSAIndicator"
	HeaderPublicExponent     = "PublicExponent"
	HeaderXTargetUnit        = "X-Target-Unit"
	HeaderPublicKey          = "PublicKey"
	HeaderItokenAuthType     = "Itoken-Auth-Type"
	HeaderUserRole           = "User-Role"
	HeaderBioType            = "Bio-Type"
	HeaderRegMethod          = "Reg-Method"
	HeaderAuthMethod         = "Auth-Method"
	HeaderAppVersion         = "App-Version"
	HeaderXDeviceID          = "X-Device-Id"
	HeaderDeviceID           = "Device-Id"
	HeaderXDeviceModel       = "X-Device-Model"
	HeaderXDeviceBrand       = "x-device-brand"
	HeaderXDeviceOS          = "x-device-os"
	HeaderXDeviceOSVersion   = "x-device-os-version"
	HeaderPlatform           = "Platform"
)

// AllowedHeaders maps lowercase header names to their original case format
// token is intentionally omitted from proxy allowed list because it is used for session lookup.
// However, login handler still needs it, so we will handle token specially in proxy.
var AllowedHeaders = map[string]string{
	"rsakeyindicator":      HeaderRSAKeyIndicator,
	"logintype":            HeaderLoginType,
	"cookie":               HeaderCookie,
	"token":                HeaderToken,
	"content-type":         HeaderContentType,
	"accept":               HeaderAccept,
	"accept-encoding":      HeaderAcceptEncoding,
	"connection":           HeaderConnection,
	"locale":               HeaderLocale,
	"x-noncecount":         HeaderXNonceCount,
	"x-nonce":              HeaderXNonce,
	"login-auth-type":      HeaderLoginAuthType,
	"login-auth-data":      HeaderLoginAuthData,
	"login-channel":        HeaderLoginChannel,
	"x-challenge":          HeaderXChallenge,
	"x-challenge_response": HeaderXChallengeResponse,
	"mackey":               HeaderMacKey,
	"macmodulus":           HeaderMacModulus,
	"macencrypteddata":     HeaderMacEncryptedData,
	"macrsaindicator":      HeaderMacRSAIndicator,
	"publicexponent":       HeaderPublicExponent,
	"x-target-unit":        HeaderXTargetUnit,
	"publickey":            HeaderPublicKey,
	"itoken-auth-type":     HeaderItokenAuthType,
	"user-role":            HeaderUserRole,
	"bio-type":             HeaderBioType,
	"reg-method":           HeaderRegMethod,
	"auth-method":          HeaderAuthMethod,
	"app-version":          HeaderAppVersion,
	"x-device-id":          HeaderXDeviceID,
	"device-id":            HeaderDeviceID,
	"x-device-model":       HeaderXDeviceModel,
	"x-device-brand":       HeaderXDeviceBrand,
	"x-device-os":          HeaderXDeviceOS,
	"x-device-os-version":  HeaderXDeviceOSVersion,
	"platform":             HeaderPlatform,
}

const maxLoggedBodyBytes = 8 * 1024

////////////////////////////////////////////////////////////
// SESSION STORE INTERFACE & MEMORY IMPLEMENTATION
////////////////////////////////////////////////////////////

type SessionData struct {
	CookieStr string
	Username  string
}

type SessionStore interface {
	Set(key string, value SessionData) error
	Get(key string) (SessionData, bool)
	Delete(key string) error
}

type MemoryStore struct {
	data sync.Map
}

func (m *MemoryStore) Set(key string, value SessionData) error {
	m.data.Store(key, value)
	return nil
}

func (m *MemoryStore) Get(key string) (SessionData, bool) {
	val, ok := m.data.Load(key)
	if !ok {
		return SessionData{}, false
	}
	session, ok := val.(SessionData)
	if !ok {
		return SessionData{}, false
	}
	return session, true
}

func (m *MemoryStore) Delete(key string) error {
	m.data.Delete(key)
	return nil
}

////////////////////////////////////////////////////////////
// APPLICATION STRUCT
////////////////////////////////////////////////////////////

type App struct {
	config    *Config
	bcoClient *http.Client // regular HTTP bcoClient for BCO
	bcmClient *http.Client // mTLS bcmClient for BCM proxy
	store     SessionStore
}

////////////////////////////////////////////////////////////
// DEBUG UTILITIES
////////////////////////////////////////////////////////////

// dumpHeaders prints headers in readable format with original case
func dumpHeaders(title string, headers http.Header) {
	log.Println(title)
	if len(headers) == 0 {
		log.Println("  <none>")
		return
	}
	// http.Header preserves case, so we can iterate directly
	for k, v := range headers {
		log.Printf("  %s: %s\n", k, strings.Join(v, ","))
	}
	log.Println("-----------------------------------")
}

// dumpBody reads and prints request body, then restores it
func dumpBody(title string, body io.ReadCloser) ([]byte, io.ReadCloser) {
	if body == nil {
		log.Println(title, ": <empty>")
		return nil, body
	}
	data, err := io.ReadAll(body)
	if err != nil {
		log.Println(title, "read error:", err)
		return nil, body
	}
	if len(data) == 0 {
		log.Println(title, ": <empty>")
	} else {
		log.Println(title, ":")
		log.Println(string(data))
	}
	log.Println("-----------------------------------")
	return data, io.NopCloser(strings.NewReader(string(data)))
}

func isTextLikeContentType(contentType string) bool {
	ct := strings.ToLower(strings.TrimSpace(contentType))
	if ct == "" {
		return true
	}

	return strings.HasPrefix(ct, "text/") ||
		strings.Contains(ct, "json") ||
		strings.Contains(ct, "xml") ||
		strings.Contains(ct, "javascript") ||
		strings.Contains(ct, "x-www-form-urlencoded")
}

func decodeBodyForLogging(body []byte, contentEncoding string) ([]byte, string, error) {
	encoding := strings.ToLower(strings.TrimSpace(contentEncoding))
	if encoding == "" || encoding == "identity" {
		return body, "", nil
	}

	if strings.Contains(encoding, ",") {
		parts := strings.Split(encoding, ",")
		encoding = strings.TrimSpace(parts[0])
	}

	var reader io.ReadCloser
	var err error

	switch encoding {
	case "gzip":
		reader, err = gzip.NewReader(bytes.NewReader(body))
	case "deflate":
		reader, err = zlib.NewReader(bytes.NewReader(body))
	default:
		return nil, encoding, fmt.Errorf("unsupported content-encoding")
	}
	if err != nil {
		return nil, encoding, err
	}
	defer reader.Close()

	decoded, err := io.ReadAll(reader)
	if err != nil {
		return nil, encoding, err
	}
	return decoded, encoding, nil
}

func logBodyPreview(tag, contentType, contentEncoding string, body []byte) {
	if len(body) == 0 {
		log.Printf("[%s] BCM_RESPONSE_BODY body=<empty>", tag)
		return
	}

	if !isTextLikeContentType(contentType) {
		log.Printf("[%s] BCM_RESPONSE_BODY skipped non-text content-type=%q content-encoding=%q body_length=%d", tag, contentType, contentEncoding, len(body))
		return
	}

	decodedBody, decodedFrom, err := decodeBodyForLogging(body, contentEncoding)
	if err != nil {
		log.Printf("[%s] BCM_RESPONSE_BODY skipped undecodable body content-type=%q content-encoding=%q body_length=%d error=%v", tag, contentType, contentEncoding, len(body), err)
		return
	}

	preview := decodedBody
	truncated := false
	if len(preview) > maxLoggedBodyBytes {
		preview = preview[:maxLoggedBodyBytes]
		truncated = true
	}

	log.Printf("[%s] BCM_RESPONSE_BODY BEGIN content-type=%q content-encoding=%q decoded-from=%q body_length=%d decoded_length=%d", tag, contentType, contentEncoding, decodedFrom, len(body), len(decodedBody))
	for _, line := range strings.Split(string(preview), "\n") {
		log.Printf("[%s] BCM_RESPONSE_BODY %s", tag, line)
	}
	if truncated {
		log.Printf("[%s] BCM_RESPONSE_BODY truncated showing_first_bytes=%d", tag, len(preview))
	}
	log.Printf("[%s] BCM_RESPONSE_BODY END", tag)
}

func isBCMSessionExpiredResponse(resp *http.Response, body []byte) bool {
	contentType := strings.ToLower(strings.TrimSpace(resp.Header.Get("Content-Type")))
	if !strings.Contains(contentType, "text/html") {
		return false
	}

	decodedBody, _, err := decodeBodyForLogging(body, resp.Header.Get("Content-Encoding"))
	if err != nil {
		log.Printf("[BCM_RESPONSE_BODY] session-expired detection skipped: decode error: %v", err)
		return false
	}

	bodyText := strings.ToLower(string(decodedBody))
	return strings.Contains(bodyText, "temporary suspension of service") ||
		strings.Contains(bodyText, "temporarily unavailable due to site maintenance") ||
		strings.Contains(bodyText, "support id:") ||
		strings.Contains(bodyText, "暫停服務")
}

func writeSessionExpiredResponse(w http.ResponseWriter) {
	response := map[string]string{
		"status":  "419",
		"code":    "419",
		"message": "Session Expired",
	}

	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(http.StatusOK)
	if err := json.NewEncoder(w).Encode(response); err != nil {
		log.Printf("Failed to write BCM session expired response: %v", err)
	}
}

// generateToken generates a random token for access token
func generateToken() (string, error) {
	bytes := make([]byte, 16) // 128-bit random number
	if _, err := rand.Read(bytes); err != nil {
		return "", err
	}
	return hex.EncodeToString(bytes), nil
}

func getLocale(r *http.Request) string {
	locale := strings.TrimSpace(r.Header.Get(HeaderLocale))
	if locale == "" {
		locale = "en"
	}
	return locale
}

func buildTargetWithLocale(baseURL, path, rawQuery, locale string) string {
	base := strings.TrimSuffix(baseURL, "/")
	u, err := url.Parse(base + path)
	if err != nil {
		target := base + path
		if rawQuery != "" {
			target += "?" + rawQuery
		}
		if strings.Contains(target, "?") {
			target += "&locale=" + url.QueryEscape(locale)
		} else {
			target += "?locale=" + url.QueryEscape(locale)
		}
		return target
	}

	q := u.Query()
	if rawQuery != "" {
		parsedRaw, err := url.ParseQuery(rawQuery)
		if err == nil {
			for k, values := range parsedRaw {
				for _, v := range values {
					q.Add(k, v)
				}
			}
		}
	}
	q.Set("locale", locale)
	u.RawQuery = q.Encode()
	return u.String()
}

func injectLocaleIntoBody(body []byte, contentType, locale string) ([]byte, error) {
	ct := strings.ToLower(contentType)

	switch {
	case strings.Contains(ct, "application/json"):
		var payload map[string]interface{}

		if len(bytes.TrimSpace(body)) == 0 {
			payload = make(map[string]interface{})
		} else {
			if err := json.Unmarshal(body, &payload); err != nil {
				return body, fmt.Errorf("failed to unmarshal json body: %w", err)
			}
		}

		payload["locale"] = locale
		newBody, err := json.Marshal(payload)
		if err != nil {
			return body, fmt.Errorf("failed to marshal json body: %w", err)
		}
		return newBody, nil

	case strings.Contains(ct, "application/x-www-form-urlencoded"):
		values, err := url.ParseQuery(string(body))
		if err != nil {
			return body, fmt.Errorf("failed to parse form body: %w", err)
		}
		values.Set("locale", locale)
		return []byte(values.Encode()), nil

	default:
		return body, nil
	}
}

////////////////////////////////////////////////////////////
// LOGGING TRANSPORT
////////////////////////////////////////////////////////////

type LoggingTransport struct {
	Transport http.RoundTripper
}

func (t *LoggingTransport) RoundTrip(req *http.Request) (*http.Response, error) {
	start := time.Now()

	log.Println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
	log.Println("OUTGOING REQUEST")
	log.Println("URL:", req.URL.String())
	log.Println("Method:", req.Method)

	// Print outgoing headers exactly as set (http.Header preserves case)
	dumpHeaders("OUTGOING HEADERS (case preserved):", req.Header)

	resp, err := t.Transport.RoundTrip(req)
	duration := time.Since(start)

	if err != nil {
		log.Println("REQUEST FAILED:", err)
		return nil, err
	}

	log.Println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
	log.Println("RESPONSE RECEIVED")
	log.Println("Status:", resp.Status)
	log.Println("Duration:", duration)

	dumpHeaders("RESPONSE HEADERS (case preserved):", resp.Header)
	log.Println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
	return resp, nil
}

////////////////////////////////////////////////////////////
// CREATE NEW APPLICATION
////////////////////////////////////////////////////////////

func NewApp(cfg *Config) (*App, error) {
	// regular HTTP BCO Client
	//jar, _ := cookiejar.New(nil)
	bcoClient := &http.Client{
		Timeout: 60 * time.Second,
		CheckRedirect: func(req *http.Request, via []*http.Request) error {
			log.Println("Redirect blocked:", req.URL.String())
			return http.ErrUseLastResponse
		},
		Transport: &LoggingTransport{
			Transport: &http.Transport{
				TLSClientConfig: &tls.Config{
					InsecureSkipVerify: true,
					VerifyPeerCertificate: func(rawCerts [][]byte, verifiedChains [][]*x509.Certificate) error {
						log.Println("TLS verification skipped")
						return nil
					},
				},
				MaxIdleConns:        100,
				MaxIdleConnsPerHost: 20,
				IdleConnTimeout:     90 * time.Second,
			},
		},
	}

	// mTLS client for BCM proxy
	cert, err := tls.LoadX509KeyPair(cfg.MTLSCertFile, cfg.MTLSKeyFile)
	if err != nil {
		return nil, fmt.Errorf("failed to load mTLS certificates: %v", err)
	}
	bcmClient := &http.Client{
		Transport: &http.Transport{
			TLSClientConfig: &tls.Config{
				Certificates:       []tls.Certificate{cert},
				InsecureSkipVerify: true, // for production, set proper CA
			},
		},
		Timeout: 30 * time.Second,
	}

	store := &MemoryStore{}
	return &App{
		config:    cfg,
		bcoClient: bcoClient,
		bcmClient: bcmClient,
		store:     store,
	}, nil
}

////////////////////////////////////////////////////////////
// HEADER FILTER - Preserve original case
////////////////////////////////////////////////////////////

// HeaderInfo stores the original header information
type HeaderInfo struct {
	OriginalKey string
	Values      []string
}

func filterHeaders(src http.Header, allowCookie bool, isPublicEndpoint bool) http.Header {
	// First, capture original headers (directly from the request)
	log.Println("=== HEADER FILTERING ===")

	// Create a map to store original header information
	originalHeaders := make(map[string]HeaderInfo)

	// Read directly from the original header map to avoid Go canonicalization
	for k, v := range src {
		originalHeaders[strings.ToLower(k)] = HeaderInfo{
			OriginalKey: k, // Save the original key case
			Values:      v,
		}
	}

	// Print original headers (preserve original case)
	log.Println("ORIGINAL HEADERS (before filter, case preserved):")
	for _, info := range originalHeaders {
		log.Printf("  %s: %s\n", info.OriginalKey, strings.Join(info.Values, ","))
	}
	log.Println("-----------------------------------")

	dst := make(http.Header)

	// Filter based on lowercase key, but preserve original key case
	for lowerKey, info := range originalHeaders {
		switch lowerKey {
		case "rsakeyindicator":
			// Use original case "RSAKeyIndicator"
			dst[AllowedHeaders["rsakeyindicator"]] = info.Values
			log.Printf("  ✓ Kept: %s -> %s (case restored)\n", info.OriginalKey, AllowedHeaders["rsakeyindicator"])
		case "logintype":
			// Use original case "loginType"
			dst[AllowedHeaders["logintype"]] = info.Values
			log.Printf("  ✓ Kept: %s -> %s (case restored)\n", info.OriginalKey, AllowedHeaders["logintype"])
		case "token":
			// Use original case "token"
			dst[AllowedHeaders["token"]] = info.Values
			log.Printf("  ✓ Kept: %s -> %s (case restored)\n", info.OriginalKey, AllowedHeaders["token"])
		case "content-type":
			// Use original case "Content-Type"
			dst[AllowedHeaders["content-type"]] = info.Values
			log.Printf("  ✓ Kept: %s -> %s (case restored)\n", info.OriginalKey, AllowedHeaders["content-type"])
		case "locale":
			// Locale must always be preserved, including public endpoints
			dst[AllowedHeaders["locale"]] = info.Values
			log.Printf("  ✓ Kept: %s -> %s (case restored)\n", info.OriginalKey, AllowedHeaders["locale"])
		case "x-noncecount":
			dst[AllowedHeaders["x-noncecount"]] = info.Values
			log.Printf("  ✓ Kept: %s -> %s (case restored)\n", info.OriginalKey, AllowedHeaders["x-noncecount"])
		case "x-nonce":
			dst[AllowedHeaders["x-nonce"]] = info.Values
			log.Printf("  ✓ Kept: %s -> %s (case restored)\n", info.OriginalKey, AllowedHeaders["x-nonce"])
		case "login-auth-type":
			// Use original case "Login-Auth-Type"
			dst[AllowedHeaders["login-auth-type"]] = info.Values
			log.Printf("  ✓ Kept: %s -> %s (case restored)\n", info.OriginalKey, AllowedHeaders["login-auth-type"])
		case "login-auth-data":
			// Use original case "Login-Auth-Data"
			dst[AllowedHeaders["login-auth-data"]] = info.Values
			log.Printf("  ✓ Kept: %s -> %s (case restored)\n", info.OriginalKey, AllowedHeaders["login-auth-data"])
		case "login-channel":
			// Use original case "Login-Channel"
			dst[AllowedHeaders["login-channel"]] = info.Values
			log.Printf("  ✓ Kept: %s -> %s (case restored)\n", info.OriginalKey, AllowedHeaders["login-channel"])
		case "accept":
			if isPublicEndpoint {
				log.Printf("  ✗ Filtered: %s (Connection header not allowed in public endpoints)\n", info.OriginalKey)
				continue
			}
			// Use original case "Accept"
			//dst[AllowedHeaders["accept"]] = info.Values
			log.Printf("  ✓ Kept: %s -> %s (case restored)\n", info.OriginalKey, AllowedHeaders["accept"])
		case "accept-encoding":
			if isPublicEndpoint {
				log.Printf("  ✗ Filtered: %s (Connection header not allowed in public endpoints)\n", info.OriginalKey)
				continue
			}
			// Use original case "Accept-Encoding"
			//dst[AllowedHeaders["accept-encoding"]] = info.Values
			log.Printf("  ✓ Kept: %s -> %s (case restored)\n", info.OriginalKey, AllowedHeaders["accept-encoding"])
		case "connection":
			if isPublicEndpoint {
				log.Printf("  ✗ Filtered: %s (Connection header not allowed in public endpoints)\n", info.OriginalKey)
				continue
			}
			// Use original case "Connection"
			//dst[AllowedHeaders["connection"]] = info.Values
			log.Printf("  ✓ Kept: %s -> %s (case restored)\n", info.OriginalKey, AllowedHeaders["connection"])
		case "cookie":
			if allowCookie {
				dst[AllowedHeaders["cookie"]] = info.Values
				log.Printf("  ✓ Kept: %s -> %s (case restored)\n", info.OriginalKey, AllowedHeaders["cookie"])
			} else {
				log.Printf("  ✗ Filtered: %s (cookies not allowed in login)\n", info.OriginalKey)
			}
		case "x-challenge":
			// Use original case "X-Challenge"
			dst[AllowedHeaders["x-challenge"]] = info.Values
			log.Printf("  ✓ Kept: %s -> %s (case restored)\n", info.OriginalKey, AllowedHeaders["x-challenge"])
		case "x-challenge_response":
			// Use original case "X-Challenge_Response"
			dst[AllowedHeaders["x-challenge_response"]] = info.Values
			log.Printf("  ✓ Kept: %s -> %s (case restored)\n", info.OriginalKey, AllowedHeaders["x-challenge_response"])
		case "mackey":
			dst[AllowedHeaders["mackey"]] = info.Values
			log.Printf("  ✓ Kept: %s -> %s (case restored)\n", info.OriginalKey, AllowedHeaders["mackey"])
		case "macmodulus":
			dst[AllowedHeaders["macmodulus"]] = info.Values
			log.Printf("  ✓ Kept: %s -> %s (case restored)\n", info.OriginalKey, AllowedHeaders["macmodulus"])
		case "macencrypteddata":
			dst[AllowedHeaders["macencrypteddata"]] = info.Values
			log.Printf("  ✓ Kept: %s -> %s (case restored)\n", info.OriginalKey, AllowedHeaders["macencrypteddata"])
		case "macrsaindicator":
			dst[AllowedHeaders["macrsaindicator"]] = info.Values
			log.Printf("  ✓ Kept: %s -> %s (case restored)\n", info.OriginalKey, AllowedHeaders["macrsaindicator"])
		case "publicexponent":
			dst[AllowedHeaders["publicexponent"]] = info.Values
			log.Printf("  ✓ Kept: %s -> %s (case restored)\n", info.OriginalKey, AllowedHeaders["publicexponent"])
		case "x-target-unit":
			dst[AllowedHeaders["x-target-unit"]] = info.Values
			log.Printf("  ✓ Kept: %s -> %s (case restored)\n", info.OriginalKey, AllowedHeaders["x-target-unit"])
		case "publickey":
			dst[AllowedHeaders["publickey"]] = info.Values
			log.Printf("  ✓ Kept: %s -> %s (case restored)\n", info.OriginalKey, AllowedHeaders["publickey"])
		case "itoken-auth-type":
			dst[AllowedHeaders["itoken-auth-type"]] = info.Values
			log.Printf("  ✓ Kept: %s -> %s (case restored)\n", info.OriginalKey, AllowedHeaders["itoken-auth-type"])
		case "user-role":
			dst[AllowedHeaders["user-role"]] = info.Values
			log.Printf("  ✓ Kept: %s -> %s (case restored)\n", info.OriginalKey, AllowedHeaders["user-role"])
		case "bio-type":
			dst[AllowedHeaders["bio-type"]] = info.Values
			log.Printf("  ✓ Kept: %s -> %s (case restored)\n", info.OriginalKey, AllowedHeaders["bio-type"])
		case "reg-method":
			dst[AllowedHeaders["reg-method"]] = info.Values
			log.Printf("  ✓ Kept: %s -> %s (case restored)\n", info.OriginalKey, AllowedHeaders["reg-method"])
		case "auth-method":
			dst[AllowedHeaders["auth-method"]] = info.Values
			log.Printf("  ✓ Kept: %s -> %s (case restored)\n", info.OriginalKey, AllowedHeaders["auth-method"])
		case "app-version":
			dst[AllowedHeaders["app-version"]] = info.Values
			log.Printf("  ✓ Kept: %s -> %s (case restored)\n", info.OriginalKey, AllowedHeaders["app-version"])
		case "x-device-id":
			dst[AllowedHeaders["x-device-id"]] = info.Values
			log.Printf("  ✓ Kept: %s -> %s (case restored)\n", info.OriginalKey, AllowedHeaders["x-device-id"])
		case "device-id":
			dst[AllowedHeaders["device-id"]] = info.Values
			log.Printf("  ✓ Kept: %s -> %s (case restored)\n", info.OriginalKey, AllowedHeaders["device-id"])
		case "x-device-model":
			dst[AllowedHeaders["x-device-model"]] = info.Values
			log.Printf("  ✓ Kept: %s -> %s (case restored)\n", info.OriginalKey, AllowedHeaders["x-device-model"])
		case "x-device-brand":
			dst[AllowedHeaders["x-device-brand"]] = info.Values
			log.Printf("  ✓ Kept: %s -> %s (case restored)\n", info.OriginalKey, AllowedHeaders["x-device-brand"])
		case "x-device-os":
			dst[AllowedHeaders["x-device-os"]] = info.Values
			log.Printf("  ✓ Kept: %s -> %s (case restored)\n", info.OriginalKey, AllowedHeaders["x-device-os"])
		case "x-device-os-version":
			dst[AllowedHeaders["x-device-os-version"]] = info.Values
			log.Printf("  ✓ Kept: %s -> %s (case restored)\n", info.OriginalKey, AllowedHeaders["x-device-os-version"])
		case "platform":
			dst[AllowedHeaders["platform"]] = info.Values
			log.Printf("  ✓ Kept: %s -> %s (case restored)\n", info.OriginalKey, AllowedHeaders["platform"])
		default:
			log.Printf("  ✗ Filtered: %s (not in allowed list)\n", info.OriginalKey)
		}
	}

	// Print filtered headers
	log.Println("FILTERED HEADERS RESULT (case restored):")
	for k, v := range dst {
		log.Printf("  %s: %s\n", k, strings.Join(v, ","))
	}
	log.Println("-----------------------------------")
	log.Println("=== HEADER FILTERING COMPLETE ===")

	return dst
}

// copyHeaders copies headers from src to dst, skipping the "Host" header.
func copyHeaders(src, dst http.Header) {
	for k, vv := range src {
		if strings.EqualFold(k, "Host") {
			continue
		}
		for _, v := range vv {
			dst.Add(k, v)
		}
	}
}

////////////////////////////////////////////////////////////
// COPY RESPONSE TO CLIENT (used for error passthrough)
////////////////////////////////////////////////////////////

func copyResponse(w http.ResponseWriter, resp *http.Response) {
	if resp.StatusCode == http.StatusSeeOther {
		location := resp.Header.Get("Location")
		log.Println("Convert 303 → 200, Location:", location)

		// copy all headers
		for k, v := range resp.Header {
			for _, vv := range v {
				w.Header().Add(k, vv)
			}
		}

		// preserve redirect info in header
		w.Header().Set("X-Redirect-Location", location)
		w.Header().Set("X-Original-Status", "303")

		// convert to 200
		w.WriteHeader(http.StatusOK)
		io.Copy(w, resp.Body)
		return
	}

	// normal flow
	for k, v := range resp.Header {
		for _, vv := range v {
			w.Header().Add(k, vv)
		}
	}
	w.WriteHeader(resp.StatusCode)
	io.Copy(w, resp.Body)
}

////////////////////////////////////////////////////////////
// LOGIN HANDLER (modified to extract cookies and return accessToken)
////////////////////////////////////////////////////////////

func (app *App) LoginHandler(w http.ResponseWriter, r *http.Request) {
	start := time.Now()

	log.Println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
	log.Println("LOGIN REQUEST RECEIVED")
	log.Println("Client IP:", r.RemoteAddr)
	log.Println("Method:", r.Method)
	log.Println("Path:", r.URL.Path)

	// Print original headers (http.Header preserves case)
	dumpHeaders("INCOMING REQUEST HEADERS (as received):", r.Header)

	originalBody, newBody := dumpBody("ORIGINAL BODY", r.Body)
	r.Body = newBody
	defer r.Body.Close()
	_ = r.ParseForm()

	username := strings.TrimSpace(r.FormValue("j_username"))
	log.Println("Username:", username)

	locale := getLocale(r)
	target := buildTargetWithLocale(app.config.BCOBaseURL, "/digx/j_security_check", "", locale)
	log.Println("Forward target:", target)

	formValues, err := url.ParseQuery(string(originalBody))
	if err != nil {
		log.Println("Failed to parse login form body:", err)
		http.Error(w, "invalid login request body", http.StatusBadRequest)
		return
	}
	formValues.Set("locale", locale)
	encodedBody := formValues.Encode()

	req, _ := http.NewRequest("POST", target, strings.NewReader(encodedBody))
	req.Header.Set("Content-Type", "application/x-www-form-urlencoded")

	// Pass through User-Agent from original request, don't set default
	if ua := r.Header.Get("User-Agent"); ua != "" {
		req.Header.Set("User-Agent", ua)
	} else {
		req.Header.Set("User-Agent", app.config.UserAgent)
	}

	// Filter headers (cookies not allowed in login)
	filtered := filterHeaders(r.Header, false, true)

	// Apply filtered headers to new request
	for k, v := range filtered {
		req.Header[k] = append([]string(nil), v...)
	}

	// ensure locale header present
	req.Header.Set(HeaderLocale, locale)

	// Log final request headers before sending
	log.Println("FINAL REQUEST HEADERS (before sending):")
	dumpHeaders("FINAL HEADERS:", req.Header)
	log.Println("FINAL LOGIN BODY:")
	log.Println(encodedBody)
	log.Println("-----------------------------------")

	resp, err := app.bcoClient.Do(req)
	if err != nil {
		http.Error(w, err.Error(), 500)
		return
	}
	defer resp.Body.Close()

	// Login successful (backend returned 303)
	if resp.StatusCode != http.StatusSeeOther {
		// Read backend response body
		bodyBytes, err := io.ReadAll(resp.Body)
		if err != nil {
			log.Println("Failed to read response body:", err)
			http.Error(w, "internal error", http.StatusInternalServerError)
			return
		}

		// Build unified error response
		response := map[string]interface{}{
			"success":    false,
			"statusCode": resp.StatusCode,
			"headers":    resp.Header,
		}

		if len(bodyBytes) > 0 {
			response["body"] = string(bodyBytes)
		}

		w.Header().Set("Content-Type", "application/json")
		w.WriteHeader(http.StatusOK) //this suggestion is to still return 200, allowing the frontend to handle it uniformly
		if err := json.NewEncoder(w).Encode(response); err != nil {
			log.Println("JSON encode error:", err)
		}

		return
	}

	// Read the backend response body (if any)
	bodyBytes, err := io.ReadAll(resp.Body)
	if err != nil {
		log.Println("Failed to read response body:", err)
		http.Error(w, "internal error", http.StatusInternalServerError)
		return
	}

	// Extract required cookies
	cookies := resp.Cookies()
	var jsessionid, wlAuthCookie string
	for _, c := range cookies {
		switch c.Name {
		case "JSESSIONID":
			jsessionid = c.Value
		case "_WL_AUTHCOOKIE_JSESSIONID":
			wlAuthCookie = c.Value
		}
	}

	if jsessionid == "" || wlAuthCookie == "" {
		log.Println("Missing required cookies in login response")
		http.Error(w, "login failed: missing session cookies", http.StatusInternalServerError)
		return
	}

	// Build cookie string
	cookieStr := "JSESSIONID=" + jsessionid + "; _WL_AUTHCOOKIE_JSESSIONID=" + wlAuthCookie

	// Generate random access token (not using jsessionid directly)
	accessToken, err := generateToken()
	if err != nil {
		log.Println("Failed to generate access token:", err)
		http.Error(w, "internal error", http.StatusInternalServerError)
		return
	}

	// Store accessToken -> session mapping
	session := SessionData{
		CookieStr: cookieStr,
		Username:  username,
	}
	if err := app.store.Set(accessToken, session); err != nil {
		log.Println("Failed to store session:", err)
		http.Error(w, "internal error", http.StatusInternalServerError)
		return
	}
	log.Printf("Session stored: token=%s username=%s\n", accessToken, username)

	// Get current Hong Kong time (Asia/Hong_Kong)
	loc, _ := time.LoadLocation("Asia/Hong_Kong")
	hkTime := time.Now().In(loc)

	// Construct JSON response for client, containing accessToken, loginTime, and all backend headers
	response := map[string]interface{}{
		"accessToken": accessToken,
		"loginTime": map[string]string{
			"iso":     hkTime.Format(time.RFC3339),          // 2026-03-05T15:04:05+08:00
			"display": hkTime.Format("2006-01-02 15:04:05"), // 2026-03-05 15:04:05
			"unix":    fmt.Sprintf("%d", hkTime.Unix()),     // "1646468645"
		},
		"headers": resp.Header,
	}
	if len(bodyBytes) > 0 {
		response["body"] = string(bodyBytes)
	}

	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(http.StatusOK)
	if err := json.NewEncoder(w).Encode(response); err != nil {
		log.Println("JSON encode error:", err)
	}

	log.Println("Login completed in:", time.Since(start))
	log.Println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
}

////////////////////////////////////////////////////////////
// GENERIC BCO HANDLER (modified with public endpoints exception)
////////////////////////////////////////////////////////////

// BCOHandler handles all BCO requests, forwards them to the target server,
// injects the session cookie based on the access token (extracted from Authorization: Bearer header),
// and returns a unified JSON response containing the backend headers and body.
// Public endpoints are exempt from token validation. This development gateway permits
// non-public requests without a BFF token by default. Set
// BFF_DEBUG_DISABLE_BCO_AUTH=false when token enforcement is required.
func (app *App) BCOHandler(w http.ResponseWriter, r *http.Request) {
	start := time.Now()

	log.Println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
	log.Println("PROXY REQUEST RECEIVED")
	log.Println("Client IP:", r.RemoteAddr)
	log.Println("Method:", r.Method)
	log.Println("Path:", r.URL.Path)
	log.Println("Query:", r.URL.RawQuery)

	locale := getLocale(r)

	// ----- Check if this is a public endpoint that doesn't require token -----
	isPublicEndpoint := strings.HasSuffix(r.URL.Path, "/cz/v1/publicKey") ||
		strings.HasSuffix(r.URL.Path, "/v1/bankConfiguration") ||
		strings.HasSuffix(r.URL.Path, "/bcmApprove") ||
		strings.HasSuffix(r.URL.Path, "/ambushLoginOtp/resend")

	var session SessionData
	var accessToken string

	if isPublicEndpoint {
		log.Println("Public endpoint detected - skipping token validation")
		// For public endpoints, we don't need a token or cookie
		// Remove Authorization header if present to avoid forwarding
		r.Header.Del("Authorization")
		r.Header.Del("Cookie")
	} else if app.config.DebugDisableBCOAuth {
		// Development bypass: an anonymous request is forwarded without a backend
		// session. If a valid BFF token is present, retain the normal session-cookie
		// injection so authenticated pages continue to work after login.
		log.Println("WARNING: BCO token validation is disabled (debug mode)")
		authHeader := r.Header.Get("Authorization")
		if strings.HasPrefix(authHeader, "Bearer ") {
			accessToken = strings.TrimSpace(strings.TrimPrefix(authHeader, "Bearer "))
			if storedSession, ok := app.store.Get(accessToken); ok {
				session = storedSession
				log.Printf("Debug bypass found session for username=%s", session.Username)
			} else {
				log.Println("Debug bypass ignored unknown or expired Bearer token")
			}
		}

		// Never forward the BFF token or a client-supplied Cookie to the real backend.
		r.Header.Del("Authorization")
		r.Header.Del("Cookie")
	} else {
		// ----- Extract access token from Authorization: Bearer header -----
		authHeader := r.Header.Get("Authorization")
		if !strings.HasPrefix(authHeader, "Bearer ") {
			log.Println("Missing or invalid Authorization header (Bearer token required)")
			http.Error(w, "unauthorized", http.StatusUnauthorized)
			return
		}
		accessToken = strings.TrimPrefix(authHeader, "Bearer ")
		log.Printf("Access token extracted: %s\n", accessToken)

		// ----- Look up session from store -----
		var ok bool
		session, ok = app.store.Get(accessToken)
		if !ok {
			log.Println("Invalid or expired token:", accessToken)
			writeSessionExpiredResponse(w)
			return
		}
		log.Printf("Session found for token, cookie string: %s, username: %s\n", session.CookieStr, session.Username)

		// ----- Remove Authorization header so it is not forwarded to the backend -----
		r.Header.Del("Authorization")
		r.Header.Del("Cookie")
	}

	// ----- Dump original headers after removal -----
	dumpHeaders("HEADERS AFTER AUTHORIZATION REMOVAL:", r.Header)

	// Override Content-Type if Override-Content-Type header is present (used for public endpoints where original Content-Type might be filtered out)
	if overrideCT := r.Header.Get("Override-Content-Type"); overrideCT != "" {
		r.Header.Set("Content-Type", overrideCT)
		log.Printf("Content-Type overridden to: %s\n", overrideCT)
	}

	// ----- Read and restore request body -----
	bodyBytes, newBody := dumpBody("ORIGINAL REQUEST BODY", r.Body)
	r.Body = newBody
	defer r.Body.Close()

	contentType := r.Header.Get("Content-Type")

	// Special Handling for /approve api body
	if strings.HasSuffix(r.URL.Path, "/approve") {
		if strings.Contains(strings.ToLower(contentType), "text/plain") {
			if len(bodyBytes) >= 2 && bodyBytes[0] == '"' && bodyBytes[len(bodyBytes)-1] == '"' {
				log.Printf("Content-Type text/plain and body contains quotes, trimming quotes")
				bodyBytes = bodyBytes[1 : len(bodyBytes)-1]
				log.Printf("Trimmed body: %s", string(bodyBytes))
			}
		}
	}

	// ----- Build target URL -----
	target := buildTargetWithLocale(app.config.BCOBaseURL, r.URL.Path, r.URL.RawQuery, locale)
	log.Println("Forward target:", target)

	// ----- Create new request to backend -----
	req, err := http.NewRequest(r.Method, target, bytes.NewReader(bodyBytes))
	if err != nil {
		log.Println("Failed to create request:", err)
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}

	// ----- Set headers -----
	// Pass through original User-Agent, use default only if not provided
	if ua := r.Header.Get("User-Agent"); ua != "" {
		req.Header.Set("User-Agent", ua)
	} else {
		req.Header.Set("User-Agent", app.config.UserAgent)
	}

	// ----- Filter and forward allowed headers -----
	filtered := filterHeaders(r.Header, false, isPublicEndpoint)
	for k, v := range filtered {
		req.Header[k] = append([]string(nil), v...)
	}

	// ensure locale header present
	req.Header.Set(HeaderLocale, locale)

	// ----- Inject the stored cookie only for non-public endpoints -----
	if !isPublicEndpoint && session.CookieStr != "" {
		req.Header.Set("Cookie", session.CookieStr)
		log.Println("Cookie injected for authenticated request")
	} else if isPublicEndpoint {
		log.Println("Public endpoint - no cookie injected")
	}

	// ----- Log final outgoing request -----
	log.Println("FINAL REQUEST HEADERS (before sending):")
	dumpHeaders("FINAL HEADERS:", req.Header)
	log.Println("FORWARD BODY:")
	log.Println(string(bodyBytes))
	log.Println("-----------------------------------")

	// ----- Execute request -----
	resp, err := app.bcoClient.Do(req)
	if err != nil {
		log.Println("Proxy error:", err)
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}
	defer resp.Body.Close()

	// ----- Read backend response body -----
	respBody, err := io.ReadAll(resp.Body)
	if err != nil {
		log.Println("Failed to read response body:", err)
		http.Error(w, "internal error", http.StatusInternalServerError)
		return
	}

	// ----- Construct unified JSON response -----
	response := map[string]interface{}{
		"status":  resp.StatusCode,
		"headers": resp.Header,
	}
	if len(respBody) > 0 {
		response["body"] = string(respBody)
	}

	// ----- Return JSON with HTTP 200 -----
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(http.StatusOK)
	if err := json.NewEncoder(w).Encode(response); err != nil {
		log.Println("JSON encode error:", err)
	}

	log.Println("Request completed in:", time.Since(start))
	log.Println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
}

/////////////////////////////////////////////////////////////
// GENERIC BCM HANDLER
////////////////////////////////////////////////////////////

// BCMHandler handles all requests (except /digx/ and login paths) and forwards them to the BCM backend,
// requiring a valid Bearer token and injecting the session cookie.
func (app *App) BCMHandler(w http.ResponseWriter, r *http.Request) {
	start := time.Now()

	log.Println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
	log.Println("BCM PROXY REQUEST RECEIVED")
	log.Println("Client IP:", r.RemoteAddr)
	log.Println("Method:", r.Method)
	log.Println("Path:", r.URL.Path)
	log.Println("Query:", r.URL.RawQuery)

	// ----- 1. Extract and validate Bearer token -----
	authHeader := r.Header.Get("Authorization")
	if !strings.HasPrefix(authHeader, "Bearer ") {
		log.Println("Missing or invalid Authorization header (Bearer token required)")
		http.Error(w, "unauthorized", http.StatusUnauthorized)
		return
	}
	accessToken := strings.TrimPrefix(authHeader, "Bearer ")
	log.Printf("Access token extracted: %s\n", accessToken)

	// ----- 2. Retrieve session from SessionStore -----
	session, ok := app.store.Get(accessToken)
	if !ok {
		log.Println("Invalid or expired token:", accessToken)
		writeSessionExpiredResponse(w)
		return
	}
	log.Printf("Session found for token, cookie string: %s, username: %s\n", session.CookieStr, session.Username)

	// ----- 3. Remove Authorization header to avoid forwarding -----
	r.Header.Del("Authorization")
	r.Header.Del("Cookie")

	// Optional: print headers after removal
	dumpHeaders("HEADERS AFTER AUTHORIZATION REMOVAL:", r.Header)

	// ----- 4. Read request body (if any) and print it -----
	bodyBytes, newBody := dumpBody("ORIGINAL REQUEST BODY", r.Body)
	r.Body = newBody
	defer r.Body.Close()

	// ----- 5. Build target URL (BCMBaseURL + request path) -----
	targetURL := app.config.BCMBaseURL + r.URL.Path
	if r.URL.RawQuery != "" {
		targetURL += "?" + r.URL.RawQuery
	}
	log.Println("Forward target:", targetURL)

	// ----- 6. Create forwarding request -----
	req, err := http.NewRequest(r.Method, targetURL, bytes.NewReader(bodyBytes))
	if err != nil {
		log.Println("Failed to create request:", err)
		http.Error(w, err.Error(), http.StatusInternalServerError)
		return
	}

	// ----- 7. Set necessary headers -----
	// Copy original request headers (filter out Host)
	copyHeaders(r.Header, req.Header)

	// Pass through original User-Agent, use default only if not provided
	if ua := r.Header.Get("User-Agent"); ua != "" {
		req.Header.Set("User-Agent", ua)
	} else {
		req.Header.Set("User-Agent", app.config.UserAgent)
	}

	// Set IBM client credentials
	req.Header.Set("x-ibm-client-id", app.config.IBMClientID)
	req.Header.Set("x-ibm-client-secret", app.config.IBMClientSecret)

	// Add user id header from session
	if session.Username != "" {
		req.Header.Set(HeaderXUserID, session.Username)
		log.Printf("%s header set with username: %s\n", HeaderXUserID, session.Username)
	}

	// For POST/PUT, set default Content-Type if missing
	if req.Header.Get("Content-Type") == "" && (r.Method == http.MethodPost || r.Method == http.MethodPut) {
		req.Header.Set("Content-Type", "application/json")
	}

	// ----- Inject the stored cookie as Authorization: Bearer header -----
	// Changed from req.Header.Set("Cookie", cookieStr) to Authorization Bearer
	//req.Header.Set("Authorization", "Bearer "+cookieStr)
	// Parse the cookie string to extract only JSESSIONID
	var bearerToken string
	if session.CookieStr != "" {
		// Split the cookie string to get individual cookies
		cookies := strings.Split(session.CookieStr, "; ")
		for _, cookie := range cookies {
			if strings.HasPrefix(cookie, "JSESSIONID=") {
				// Extract just the JSESSIONID value
				bearerToken = strings.TrimPrefix(cookie, "JSESSIONID=")
				break
			}
		}
	}

	if bearerToken != "" {
		req.Header.Set("Authorization", "Bearer "+bearerToken)
		log.Printf("Authorization header set with Bearer token (JSESSIONID only)")
	} else {
		log.Println("Warning: No JSESSIONID found in cookie string")
	}

	// Optional: print final request headers
	log.Println("FINAL REQUEST HEADERS (before sending):")
	dumpHeaders("FINAL HEADERS:", req.Header)
	log.Println("FORWARD BODY:")
	log.Println(string(bodyBytes))
	log.Println("-----------------------------------")

	// ----- 8. Send request using mTLS client (bcmClient) -----
	resp, err := app.bcmClient.Do(req)
	if err != nil {
		log.Printf("BCM proxy error: %v", err)
		http.Error(w, fmt.Sprintf("Failed to call BCM service: %v", err), http.StatusBadGateway)
		return
	}
	defer resp.Body.Close()

	// ----- 9. Read backend response body -----
	respBody, err := io.ReadAll(resp.Body)
	if err != nil {
		log.Printf("Failed to read BCM response body: %v", err)
		http.Error(w, "internal error", http.StatusInternalServerError)
		return
	}

	log.Printf("[BCM_RESPONSE] path=%s status=%d body_length=%d content-type=%q content-encoding=%q", r.URL.Path, resp.StatusCode, len(respBody), resp.Header.Get("Content-Type"), resp.Header.Get("Content-Encoding"))
	logBodyPreview("BCM_RESPONSE_BODY", resp.Header.Get("Content-Type"), resp.Header.Get("Content-Encoding"), respBody)

	if isBCMSessionExpiredResponse(resp, respBody) {
		log.Printf("[BCM_RESPONSE] session-expired detected for path=%s token=%s", r.URL.Path, accessToken)
		if err := app.store.Delete(accessToken); err != nil {
			log.Printf("[BCM_RESPONSE] failed to delete expired session for token=%s: %v", accessToken, err)
		}
		writeSessionExpiredResponse(w)
		log.Println("Request completed in:", time.Since(start))
		log.Println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
		return
	}

	// ----- 10. Write backend response directly back to client -----
	// Copy response headers
	for k, v := range resp.Header {
		for _, vv := range v {
			w.Header().Add(k, vv)
		}
	}
	// Set status code and write body
	w.WriteHeader(resp.StatusCode)
	if _, err := w.Write(respBody); err != nil {
		log.Printf("Error writing response: %v", err)
	}

	log.Println("Request completed in:", time.Since(start))
	log.Println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
}

////////////////////////////////////////////////////////////
// MAIN ENTRY POINT
////////////////////////////////////////////////////////////

func main() {
	log.SetFlags(log.LstdFlags | log.Lmicroseconds | log.Lshortfile)

	cfg := &Config{
		ListenPort:          ":8850",
		UserAgent:           "Mozilla/5.0 Chrome/144.0.0.0",
		DebugDisableBCOAuth: envBool("BFF_DEBUG_DISABLE_BCO_AUTH", true),
		// BCO config
		BCOBaseURL: "https://cdc.uat.hkbea.com",
		// BCM config
		BCMBaseURL:      "https://apisandbox.hkbea.com/hkbea/uat-partner",
		IBMClientID:     "9f4df6869d0cd405a716481105e27007",
		IBMClientSecret: "91288943cab97a57caa99045b5c01b86",
		MTLSCertFile:    "/opt/bcm/mtls/client-cert.pem",
		MTLSKeyFile:     "/opt/bcm/mtls/client-key.pem",
	}

	// Ensure base URL does not end with slash
	cfg.BCOBaseURL = strings.TrimSuffix(cfg.BCOBaseURL, "/")
	cfg.BCMBaseURL = strings.TrimSuffix(cfg.BCMBaseURL, "/")

	app, err := NewApp(cfg)
	if err != nil {
		log.Fatal(err)
	}

	mux := http.NewServeMux()
	mux.HandleFunc("/api/login", app.LoginHandler)
	mux.HandleFunc("/digx/j_security_check", app.LoginHandler)
	mux.HandleFunc("/digx/", app.BCOHandler) // BCO proxy
	mux.HandleFunc("/", app.BCMHandler)      // BCM proxy -- root

	log.Println("===================================")
	log.Println("Debug Gateway Started (with session store)")
	log.Println("Listening Port:", cfg.ListenPort)
	log.Println("BCOBaseURL Server:", cfg.BCOBaseURL)
	log.Println("BCO token validation disabled:", cfg.DebugDisableBCOAuth)
	log.Println("===================================")

	server := &http.Server{
		Addr:         cfg.ListenPort,
		Handler:      mux,
		ReadTimeout:  60 * time.Second,
		WriteTimeout: 60 * time.Second,
	}

	log.Fatal(server.ListenAndServe())
}
