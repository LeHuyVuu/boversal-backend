package main

import (
	"context"
	"database/sql"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
	"time"
)

type memoryLinkStore struct {
	links map[string]Link
}

func (s *memoryLinkStore) Create(_ context.Context, code, targetURL string, expiresAt *time.Time) (Link, error) {
	if s.links == nil {
		s.links = make(map[string]Link)
	}
	if _, exists := s.links[code]; exists {
		return Link{}, errCodeConflict
	}
	link := Link{Code: code, TargetURL: targetURL, CreatedAt: time.Now().UTC(), ExpiresAt: expiresAt}
	s.links[code] = link
	return link, nil
}

func (s *memoryLinkStore) Get(_ context.Context, code string) (Link, error) {
	link, exists := s.links[code]
	if !exists {
		return Link{}, sql.ErrNoRows
	}
	return link, nil
}

func (s *memoryLinkStore) RecordClick(_ context.Context, code string) error {
	link, exists := s.links[code]
	if !exists {
		return sql.ErrNoRows
	}
	if link.ExpiresAt != nil && !link.ExpiresAt.After(time.Now().UTC()) {
		return errLinkExpired
	}
	link.Clicks++
	now := time.Now().UTC()
	link.LastClickedAt = &now
	s.links[code] = link
	return nil
}

func TestValidateTargetURL(t *testing.T) {
	tests := []struct {
		name    string
		input   string
		wantErr bool
	}{
		{name: "https URL", input: "https://example.com/path?q=1"},
		{name: "http URL", input: "http://example.com"},
		{name: "unsupported scheme", input: "ftp://example.com", wantErr: true},
		{name: "relative URL", input: "/path", wantErr: true},
		{name: "embedded credentials", input: "https://user:password@example.com", wantErr: true},
		{name: "empty URL", input: "", wantErr: true},
	}

	for _, test := range tests {
		t.Run(test.name, func(t *testing.T) {
			_, err := validateTargetURL(test.input)
			if (err != nil) != test.wantErr {
				t.Fatalf("validateTargetURL(%q) error = %v, wantErr %v", test.input, err, test.wantErr)
			}
		})
	}
}

func TestCreateLink(t *testing.T) {
	store := &memoryLinkStore{}
	server := (&handler{store: store}).routes()
	request := httptest.NewRequest(http.MethodPost, "/api/links",
		strings.NewReader(`{"url":"https://example.com","alias":"docs"}`))
	response := httptest.NewRecorder()
	server.ServeHTTP(response, request)

	if response.Code != http.StatusCreated {
		t.Fatalf("status = %d, want %d: %s", response.Code, http.StatusCreated, response.Body.String())
	}
	var body createLinkResponse
	if err := json.Unmarshal(response.Body.Bytes(), &body); err != nil {
		t.Fatalf("decode response: %v", err)
	}
	if body.Code != "docs" || body.ShortPath != "/shortener-service/r/docs" {
		t.Fatalf("unexpected create response: %+v", body)
	}
}

func TestCreateLinkRejectsInvalidURL(t *testing.T) {
	server := (&handler{store: &memoryLinkStore{}}).routes()
	request := httptest.NewRequest(http.MethodPost, "/api/links", strings.NewReader(`{"url":"javascript:alert(1)"}`))
	response := httptest.NewRecorder()
	server.ServeHTTP(response, request)

	if response.Code != http.StatusBadRequest {
		t.Fatalf("status = %d, want %d", response.Code, http.StatusBadRequest)
	}
}

func TestRedirectRecordsClick(t *testing.T) {
	store := &memoryLinkStore{links: map[string]Link{
		"docs": {Code: "docs", TargetURL: "https://example.com"},
	}}
	server := (&handler{store: store}).routes()
	request := httptest.NewRequest(http.MethodGet, "/r/docs", nil)
	response := httptest.NewRecorder()
	server.ServeHTTP(response, request)

	if response.Code != http.StatusFound {
		t.Fatalf("status = %d, want %d", response.Code, http.StatusFound)
	}
	if response.Header().Get("Location") != "https://example.com" {
		t.Fatalf("Location = %q, want https://example.com", response.Header().Get("Location"))
	}
	if store.links["docs"].Clicks != 1 {
		t.Fatalf("clicks = %d, want 1", store.links["docs"].Clicks)
	}
}

func TestRedirectRejectsExpiredLink(t *testing.T) {
	expired := time.Now().UTC().Add(-time.Minute)
	store := &memoryLinkStore{links: map[string]Link{
		"docs": {Code: "docs", TargetURL: "https://example.com", ExpiresAt: &expired},
	}}
	request := httptest.NewRequest(http.MethodGet, "/r/docs", nil)
	response := httptest.NewRecorder()
	(&handler{store: store}).routes().ServeHTTP(response, request)

	if response.Code != http.StatusGone {
		t.Fatalf("status = %d, want %d", response.Code, http.StatusGone)
	}
	if store.links["docs"].Clicks != 0 {
		t.Fatalf("expired link clicks = %d, want 0", store.links["docs"].Clicks)
	}
}

func TestGetLinkStats(t *testing.T) {
	store := &memoryLinkStore{links: map[string]Link{
		"docs": {Code: "docs", TargetURL: "https://example.com", Clicks: 3},
	}}
	request := httptest.NewRequest(http.MethodGet, "/api/links/docs/stats", nil)
	response := httptest.NewRecorder()
	(&handler{store: store}).routes().ServeHTTP(response, request)

	if response.Code != http.StatusOK {
		t.Fatalf("status = %d, want %d", response.Code, http.StatusOK)
	}
	var link Link
	if err := json.Unmarshal(response.Body.Bytes(), &link); err != nil {
		t.Fatalf("decode response: %v", err)
	}
	if link.Code != "docs" || link.Clicks != 3 {
		t.Fatalf("unexpected link stats: %+v", link)
	}
}

func TestCreateLinkReturnsConflictForDuplicateAlias(t *testing.T) {
	store := &memoryLinkStore{links: map[string]Link{
		"docs": {Code: "docs", TargetURL: "https://example.com"},
	}}
	request := httptest.NewRequest(http.MethodPost, "/api/links",
		strings.NewReader(`{"url":"https://another.example","alias":"docs"}`))
	response := httptest.NewRecorder()
	(&handler{store: store}).routes().ServeHTTP(response, request)

	if response.Code != http.StatusConflict {
		t.Fatalf("status = %d, want %d", response.Code, http.StatusConflict)
	}
}
