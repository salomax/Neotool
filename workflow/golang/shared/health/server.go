package health

import (
	"context"
	"fmt"
	"log/slog"
	"net/http"
	"sync/atomic"
	"time"
)

// HealthCheck defines an interface for health checks
type HealthCheck interface {
	Name() string
	Check(ctx context.Context) error
}

// HealthServer provides health check endpoints for Kubernetes probes
type HealthServer struct {
	port     int
	server   *http.Server
	isLive   atomic.Bool
	isReady  atomic.Bool
	checks   []HealthCheck
	logger   *slog.Logger
}

// NewHealthServer creates a new health check server
func NewHealthServer(
	port int,
	checks []HealthCheck,
	logger *slog.Logger,
) *HealthServer {
	hs := &HealthServer{
		port:   port,
		checks: checks,
		logger: logger,
	}

	// Initially not live or ready
	hs.isLive.Store(false)
	hs.isReady.Store(false)

	// Create HTTP server
	mux := http.NewServeMux()
	mux.HandleFunc("/health", hs.handleHealth)
	mux.HandleFunc("/live", hs.handleLiveness)
	mux.HandleFunc("/ready", hs.handleReadiness)

	hs.server = &http.Server{
		Addr:         fmt.Sprintf(":%d", port),
		Handler:      mux,
		ReadTimeout:  5 * time.Second,
		WriteTimeout: 5 * time.Second,
	}

	return hs
}

// Start starts the health check server
func (hs *HealthServer) Start(ctx context.Context) error {
	hs.logger.Info("Starting health check server", "port", hs.port)

	// Start server in goroutine
	go func() {
		if err := hs.server.ListenAndServe(); err != nil && err != http.ErrServerClosed {
			hs.logger.Error("Health check server error", "error", err)
		}
	}()

	// Wait for server to start
	time.Sleep(100 * time.Millisecond)

	hs.logger.Info("Health check server started", "port", hs.port)
	return nil
}

// Shutdown gracefully shuts down the health check server
func (hs *HealthServer) Shutdown(ctx context.Context) error {
	hs.logger.Info("Shutting down health check server")

	// Mark as not live/ready
	hs.isLive.Store(false)
	hs.isReady.Store(false)

	// Shutdown server
	if err := hs.server.Shutdown(ctx); err != nil {
		return fmt.Errorf("failed to shutdown health server: %w", err)
	}

	hs.logger.Info("Health check server stopped")
	return nil
}

// SetLive marks the application as live
func (hs *HealthServer) SetLive(live bool) {
	hs.isLive.Store(live)
	hs.logger.Info("Liveness status changed", "live", live)
}

// SetReady marks the application as ready
func (hs *HealthServer) SetReady(ready bool) {
	hs.isReady.Store(ready)
	hs.logger.Info("Readiness status changed", "ready", ready)
}

// handleHealth is a basic health check (always returns 200 if server is running)
func (hs *HealthServer) handleHealth(w http.ResponseWriter, r *http.Request) {
	w.WriteHeader(http.StatusOK)
	w.Write([]byte("OK"))
}

// handleLiveness checks if the application is alive (consumer loop is running)
func (hs *HealthServer) handleLiveness(w http.ResponseWriter, r *http.Request) {
	if hs.isLive.Load() {
		w.WriteHeader(http.StatusOK)
		w.Write([]byte("OK"))
	} else {
		w.WriteHeader(http.StatusServiceUnavailable)
		w.Write([]byte("NOT LIVE"))
	}
}

// handleReadiness checks if the application is ready to receive traffic
// Runs all registered health checks
func (hs *HealthServer) handleReadiness(w http.ResponseWriter, r *http.Request) {
	if !hs.isReady.Load() {
		w.WriteHeader(http.StatusServiceUnavailable)
		w.Write([]byte("NOT READY"))
		return
	}

	// Run all health checks
	ctx, cancel := context.WithTimeout(r.Context(), 2*time.Second)
	defer cancel()

	for _, check := range hs.checks {
		if err := check.Check(ctx); err != nil {
			hs.logger.Warn("Health check failed in readiness probe",
				"check", check.Name(),
				"error", err)
			w.WriteHeader(http.StatusServiceUnavailable)
			w.Write([]byte(fmt.Sprintf("%s UNAVAILABLE", check.Name())))
			return
		}
	}

	w.WriteHeader(http.StatusOK)
	w.Write([]byte("OK"))
}


