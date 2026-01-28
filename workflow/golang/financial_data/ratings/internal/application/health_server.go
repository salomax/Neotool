package application

import (
	"context"
	"fmt"
	"log/slog"
	"net/http"
	"time"

	"github.com/jackc/pgx/v5/pgxpool"
)

// HealthServer provides Kubernetes health check endpoints
type HealthServer struct {
	server *http.Server
	pool   *pgxpool.Pool
	logger *slog.Logger
}

// NewHealthServer creates a new health server
func NewHealthServer(port int, pool *pgxpool.Pool, logger *slog.Logger) *HealthServer {
	hs := &HealthServer{
		pool:   pool,
		logger: logger,
	}

	mux := http.NewServeMux()
	mux.HandleFunc("/health", hs.handleHealth)
	mux.HandleFunc("/live", hs.handleLive)
	mux.HandleFunc("/ready", hs.handleReady)

	hs.server = &http.Server{
		Addr:         fmt.Sprintf(":%d", port),
		Handler:      mux,
		ReadTimeout:  5 * time.Second,
		WriteTimeout: 5 * time.Second,
	}

	return hs
}

// Start starts the health server in a goroutine
func (hs *HealthServer) Start() {
	go func() {
		hs.logger.Info("starting health server", "addr", hs.server.Addr)
		if err := hs.server.ListenAndServe(); err != nil && err != http.ErrServerClosed {
			hs.logger.Error("health server error", "error", err)
		}
	}()
}

// Stop gracefully stops the health server
func (hs *HealthServer) Stop(ctx context.Context) error {
	hs.logger.Info("stopping health server")
	return hs.server.Shutdown(ctx)
}

// handleHealth is a simple ping endpoint
func (hs *HealthServer) handleHealth(w http.ResponseWriter, r *http.Request) {
	w.WriteHeader(http.StatusOK)
	w.Write([]byte("OK"))
}

// handleLive checks if the application is alive
func (hs *HealthServer) handleLive(w http.ResponseWriter, r *http.Request) {
	w.WriteHeader(http.StatusOK)
	w.Write([]byte("OK"))
}

// handleReady checks if the application is ready to serve (DB connection is healthy)
func (hs *HealthServer) handleReady(w http.ResponseWriter, r *http.Request) {
	ctx, cancel := context.WithTimeout(r.Context(), 5*time.Second)
	defer cancel()

	if err := hs.pool.Ping(ctx); err != nil {
		hs.logger.Warn("readiness check failed", "error", err)
		w.WriteHeader(http.StatusServiceUnavailable)
		w.Write([]byte("Database unavailable"))
		return
	}

	w.WriteHeader(http.StatusOK)
	w.Write([]byte("OK"))
}
