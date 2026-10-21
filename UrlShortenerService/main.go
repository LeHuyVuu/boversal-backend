package main

import (
	"context"
	"crypto/rand"
	"database/sql"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"log/slog"
	"net/http"
	"net/url"
	"os"
	"regexp"
	"strings"
	"time"

	"github.com/go-sql-driver/mysql"
)

const (
	maxTargetURLLength = 2048
	codeLength         = 8
)

var (
	errCodeConflict = errors.New("short code already exists")
	errLinkExpired  = errors.New("short link has expired")
	codePattern     = regexp.MustCompile(`^[a-zA-Z0-9_-]{3,64}$`)
)

type Link struct {
	Code          string     `json:"code"`
	TargetURL     string     `json:"targetUrl"`
	CreatedAt     time.Time  `json:"createdAt"`
	ExpiresAt     *time.Time `json:"expiresAt,omitempty"`
	Clicks        int64      `json:"clicks"`
	LastClickedAt *time.Time `json:"lastClickedAt,omitempty"`
}

type linkStore interface {
	Create(context.Context, string, string, *time.Time) (Link, error)
	Get(context.Context, string) (Link, error)
	RecordClick(context.Context, string) error
}

type handler struct {
	store linkStore
}

type createLinkRequest struct {
	URL       string     `json:"url"`
	Alias     string     `json:"alias"`
	ExpiresAt *time.Time `json:"expiresAt"`
}

type createLinkResponse struct {
	Code      string     `json:"code"`
	ShortPath string     `json:"shortPath"`
	TargetURL string     `json:"targetUrl"`
	ExpiresAt *time.Time `json:"expiresAt,omitempty"`
}

func main() {
	logger := slog.New(slog.NewJSONHandler(os.Stdout, nil))
	connectionString := os.Getenv("DATABASE_URL")
	if connectionString == "" {
		logger.Error("DATABASE_URL is required")
		os.Exit(1)
	}

	db, err := openDatabase(connectionString)
	if err != nil {
		logger.Error("could not connect to MySQL", "error", err)
		os.Exit(1)
	}
	defer db.Close()

	if err := createSchema(context.Background(), db); err != nil {
		logger.Error("could not initialize database schema", "error", err)
		os.Exit(1)
	}

	port := os.Getenv("PORT")
	if port == "" {
		port = "8080"
	}

	server := &http.Server{
		Addr:              ":" + port,
		Handler:           (&handler{store: &mysqlLinkStore{db: db}}).routes(),
		ReadHeaderTimeout: 5 * time.Second,
		ReadTimeout:       10 * time.Second,
		WriteTimeout:      15 * time.Second,
		IdleTimeout:       60 * time.Second,
	}

	logger.Info("URL shortener service listening", "port", port)
	if err := server.ListenAndServe(); err != nil && !errors.Is(err, http.ErrServerClosed) {
		logger.Error("HTTP server stopped", "error", err)
		os.Exit(1)
	}
}

func openDatabase(connectionString string) (*sql.DB, error) {
	values := make(map[string]string)
	for _, segment := range strings.Split(connectionString, ";") {
		key, value, ok := strings.Cut(segment, "=")
		if ok {
			values[strings.ToLower(strings.TrimSpace(key))] = strings.TrimSpace(value)
		}
	}

	value := func(keys ...string) string {
		for _, key := range keys {
			if found := values[key]; found != "" {
				return found
			}
		}
		return ""
	}

	host := value("server", "host", "data source")
	database := value("database", "initial catalog")
	user := value("user id", "uid", "user")
	password := value("password", "pwd")
	if host == "" || database == "" || user == "" {
		return nil, errors.New("DATABASE_URL must include Server, Database, and User ID")
	}
	port := value("port")
	if port == "" {
		port = "3306"
	}

	config := mysql.NewConfig()
	config.User = user
	config.Passwd = password
	config.Net = "tcp"
	config.Addr = host + ":" + port
	config.DBName = database
	config.ParseTime = true
	config.Loc = time.UTC
	config.Params = map[string]string{"charset": "utf8mb4"}
	if strings.EqualFold(value("sslmode", "ssl mode"), "required") {
		config.TLSConfig = "true"
	} else if strings.EqualFold(value("sslmode", "ssl mode"), "disabled") {
		config.TLSConfig = ""
	} else {
		config.TLSConfig = "preferred"
	}

	db, err := sql.Open("mysql", config.FormatDSN())
	if err != nil {
		return nil, fmt.Errorf("configure MySQL: %w", err)
	}
	db.SetMaxOpenConns(10)
	db.SetMaxIdleConns(5)
	db.SetConnMaxLifetime(5 * time.Minute)

	ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()
	if err := db.PingContext(ctx); err != nil {
		db.Close()
		return nil, fmt.Errorf("ping MySQL: %w", err)
	}
	return db, nil
}

func createSchema(ctx context.Context, db *sql.DB) error {
	const schema = `
CREATE TABLE IF NOT EXISTS short_links (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL UNIQUE,
    target_url VARCHAR(2048) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    expires_at DATETIME(6) NULL,
    clicks BIGINT NOT NULL DEFAULT 0,
    last_clicked_at DATETIME(6) NULL
) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci`
	_, err := db.ExecContext(ctx, schema)
	return err
}

type mysqlLinkStore struct {
	db *sql.DB
}

func (s *mysqlLinkStore) Create(ctx context.Context, code, targetURL string, expiresAt *time.Time) (Link, error) {
	result, err := s.db.ExecContext(ctx,
		"INSERT INTO short_links (code, target_url, expires_at) VALUES (?, ?, ?)",
		code, targetURL, expiresAt)
	if err != nil {
		var mysqlError *mysql.MySQLError
		if errors.As(err, &mysqlError) && mysqlError.Number == 1062 {
			return Link{}, errCodeConflict
		}
		return Link{}, fmt.Errorf("insert short link: %w", err)
	}

	id, err := result.LastInsertId()
	if err != nil {
		return Link{}, fmt.Errorf("get inserted link id: %w", err)
	}
	var link Link
	var expiry, lastClicked sql.NullTime
	err = s.db.QueryRowContext(ctx,
		"SELECT code, target_url, created_at, expires_at, clicks, last_clicked_at FROM short_links WHERE id = ?",
		id).Scan(&link.Code, &link.TargetURL, &link.CreatedAt, &expiry, &link.Clicks, &lastClicked)
	if err != nil {
		return Link{}, fmt.Errorf("read inserted link: %w", err)
	}
	if expiry.Valid {
		link.ExpiresAt = &expiry.Time
	}
	if lastClicked.Valid {
		link.LastClickedAt = &lastClicked.Time
	}
	return link, nil
}

func (s *mysqlLinkStore) Get(ctx context.Context, code string) (Link, error) {
	var link Link
	var expiry, lastClicked sql.NullTime
	err := s.db.QueryRowContext(ctx,
		"SELECT code, target_url, created_at, expires_at, clicks, last_clicked_at FROM short_links WHERE code = ?",
		code).Scan(&link.Code, &link.TargetURL, &link.CreatedAt, &expiry, &link.Clicks, &lastClicked)
	if expiry.Valid {
		link.ExpiresAt = &expiry.Time
	}
	if lastClicked.Valid {
		link.LastClickedAt = &lastClicked.Time
	}
	return link, err
}

func (s *mysqlLinkStore) RecordClick(ctx context.Context, code string) error {
	result, err := s.db.ExecContext(ctx,
		"UPDATE short_links SET clicks = clicks + 1, last_clicked_at = UTC_TIMESTAMP(6) WHERE code = ? AND (expires_at IS NULL OR expires_at > UTC_TIMESTAMP(6))",
		code)
	if err != nil {
		return err
	}
	updated, err := result.RowsAffected()
	if err != nil {
		return err
	}
	if updated == 0 {
		return errLinkExpired
	}
	return nil
}

func (h *handler) routes() http.Handler {
	mux := http.NewServeMux()
	mux.HandleFunc("/health", h.health)
	mux.HandleFunc("/api/links", h.createLink)
	mux.HandleFunc("/api/links/", h.getStats)
	mux.HandleFunc("/r/", h.redirect)
	return mux
}

func (h *handler) health(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodGet {
		writeError(w, http.StatusMethodNotAllowed, "method not allowed")
		return
	}
	writeJSON(w, http.StatusOK, map[string]string{"status": "healthy", "service": "url-shortener"})
}

func (h *handler) createLink(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodPost {
		writeError(w, http.StatusMethodNotAllowed, "method not allowed")
		return
	}

	var request createLinkRequest
	decoder := json.NewDecoder(io.LimitReader(r.Body, 8193))
	decoder.DisallowUnknownFields()
	if err := decoder.Decode(&request); err != nil {
		writeError(w, http.StatusBadRequest, "invalid JSON body")
		return
	}
	if err := decoder.Decode(&struct{}{}); !errors.Is(err, io.EOF) {
		writeError(w, http.StatusBadRequest, "request body must contain one JSON object")
		return
	}

	target, err := validateTargetURL(request.URL)
	if err != nil {
		writeError(w, http.StatusBadRequest, err.Error())
		return
	}
	if request.Alias != "" && !codePattern.MatchString(request.Alias) {
		writeError(w, http.StatusBadRequest, "alias must be 3-64 characters using letters, numbers, hyphens, or underscores")
		return
	}
	if request.ExpiresAt != nil && !request.ExpiresAt.After(time.Now().UTC()) {
		writeError(w, http.StatusBadRequest, "expiresAt must be in the future")
		return
	}

	isCustom := request.Alias != ""
	for attempts := 0; attempts < 5; attempts++ {
		code := request.Alias
		if !isCustom {
			code, err = generateCode()
			if err != nil {
				writeError(w, http.StatusInternalServerError, "could not generate short code")
				return
			}
		}

		link, err := h.store.Create(r.Context(), code, target, request.ExpiresAt)
		if errors.Is(err, errCodeConflict) {
			if isCustom {
				writeError(w, http.StatusConflict, "alias is already in use")
				return
			}
			continue
		}
		if err != nil {
			slog.Error("create short link failed", "error", err)
			writeError(w, http.StatusInternalServerError, "could not create short link")
			return
		}

		writeJSON(w, http.StatusCreated, createLinkResponse{
			Code:      link.Code,
			ShortPath: "/shortener-service/r/" + link.Code,
			TargetURL: link.TargetURL,
			ExpiresAt: link.ExpiresAt,
		})
		return
	}
	writeError(w, http.StatusInternalServerError, "could not generate a unique short code")
}

func (h *handler) getStats(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodGet {
		writeError(w, http.StatusMethodNotAllowed, "method not allowed")
		return
	}
	parts := strings.Split(strings.Trim(r.URL.Path, "/"), "/")
	if len(parts) != 4 || parts[0] != "api" || parts[1] != "links" || parts[3] != "stats" || !codePattern.MatchString(parts[2]) {
		http.NotFound(w, r)
		return
	}

	link, err := h.store.Get(r.Context(), parts[2])
	if errors.Is(err, sql.ErrNoRows) {
		http.NotFound(w, r)
		return
	}
	if err != nil {
		slog.Error("get short link stats failed", "error", err)
		writeError(w, http.StatusInternalServerError, "could not get link statistics")
		return
	}
	writeJSON(w, http.StatusOK, link)
}

func (h *handler) redirect(w http.ResponseWriter, r *http.Request) {
	if r.Method != http.MethodGet {
		writeError(w, http.StatusMethodNotAllowed, "method not allowed")
		return
	}
	code := strings.TrimPrefix(r.URL.Path, "/r/")
	if strings.Contains(code, "/") || !codePattern.MatchString(code) {
		http.NotFound(w, r)
		return
	}

	link, err := h.store.Get(r.Context(), code)
	if errors.Is(err, sql.ErrNoRows) {
		http.NotFound(w, r)
		return
	}
	if err != nil {
		slog.Error("resolve short link failed", "error", err)
		writeError(w, http.StatusInternalServerError, "could not resolve short link")
		return
	}
	if link.ExpiresAt != nil && !link.ExpiresAt.After(time.Now().UTC()) {
		writeError(w, http.StatusGone, "short link has expired")
		return
	}
	if err := h.store.RecordClick(r.Context(), code); err != nil {
		if errors.Is(err, errLinkExpired) {
			writeError(w, http.StatusGone, "short link has expired")
			return
		}
		slog.Error("record short link click failed", "error", err)
		writeError(w, http.StatusInternalServerError, "could not resolve short link")
		return
	}
	http.Redirect(w, r, link.TargetURL, http.StatusFound)
}

func validateTargetURL(raw string) (string, error) {
	raw = strings.TrimSpace(raw)
	if len(raw) == 0 || len(raw) > maxTargetURLLength {
		return "", errors.New("url is required and must be at most 2048 characters")
	}
	parsed, err := url.ParseRequestURI(raw)
	if err != nil || parsed.Host == "" || parsed.User != nil ||
		(parsed.Scheme != "http" && parsed.Scheme != "https") {
		return "", errors.New("url must be a valid http or https URL without embedded credentials")
	}
	return parsed.String(), nil
}

func generateCode() (string, error) {
	const alphabet = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"
	randomBytes := make([]byte, codeLength)
	if _, err := rand.Read(randomBytes); err != nil {
		return "", err
	}
	code := make([]byte, codeLength)
	for i, value := range randomBytes {
		code[i] = alphabet[int(value)%len(alphabet)]
	}
	return string(code), nil
}

func writeJSON(w http.ResponseWriter, status int, value any) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(status)
	if err := json.NewEncoder(w).Encode(value); err != nil {
		slog.Error("write JSON response failed", "error", err)
	}
}

func writeError(w http.ResponseWriter, status int, message string) {
	writeJSON(w, status, map[string]string{"error": message})
}
