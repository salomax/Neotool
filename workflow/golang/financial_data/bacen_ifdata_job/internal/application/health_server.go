package application

import (
	"context"
	"fmt"
	"log/slog"
	"net/http"
	"sync"
	"time"

	"github.com/IBM/sarama"
	"github.com/jackc/pgx/v5/pgxpool"
)

// HealthServer provides health check endpoints for Kubernetes
type HealthServer struct {
	port          int
	dbPool        *pgxpool.Pool
	kafkaProducer sarama.SyncProducer
	server        *http.Server
	logger        *slog.Logger
	isReady       bool
	isLive        bool
	mu            sync.RWMutex
}

// NewHealthServer creates a new health server
func NewHealthServer(port int, dbPool *pgxpool.Pool, kafkaProducer sarama.SyncProducer, logger *slog.Logger) *HealthServer {
	return &HealthServer{
		port:          port,
		dbPool:        dbPool,
		kafkaProducer: kafkaProducer,
		logger:        logger,
		isReady:       false,
		isLive:        true,
	}
}

// Start starts the health check server
func (hs *HealthServer) Start() error {
	mux := http.NewServeMux()

	// Health endpoint - always returns 200 (ping)
	mux.HandleFunc("/health", hs.handleHealth)

	// Liveness probe - returns 200 if app is running
	mux.HandleFunc("/live", hs.handleLiveness)

	// Readiness probe - returns 200 if DB is connected
	mux.HandleFunc("/ready", hs.handleReadiness)

	hs.server = &http.Server{
		Addr:         fmt.Sprintf(":%d", hs.port),
		Handler:      mux,
		ReadTimeout:  5 * time.Second,
		WriteTimeout: 5 * time.Second,
	}

	hs.logger.Info("starting health check server",
		"port", hs.port)

	go func() {
		if err := hs.server.ListenAndServe(); err != nil && err != http.ErrServerClosed {
			hs.logger.Error("health server error", "error", err)
		}
	}()

	return nil
}

// Stop stops the health check server
func (hs *HealthServer) Stop(ctx context.Context) error {
	if hs.server != nil {
		hs.logger.Info("stopping health check server")
		return hs.server.Shutdown(ctx)
	}
	return nil
}

// SetReady sets the ready status
func (hs *HealthServer) SetReady(ready bool) {
	hs.mu.Lock()
	defer hs.mu.Unlock()
	hs.isReady = ready
	hs.logger.Debug("health server ready status changed", "ready", ready)
}

// SetLive sets the live status
func (hs *HealthServer) SetLive(live bool) {
	hs.mu.Lock()
	defer hs.mu.Unlock()
	hs.isLive = live
	hs.logger.Debug("health server live status changed", "live", live)
}

// handleHealth handles the /health endpoint
func (hs *HealthServer) handleHealth(w http.ResponseWriter, r *http.Request) {
	w.WriteHeader(http.StatusOK)
	w.Write([]byte("OK"))
}

// handleLiveness handles the /live endpoint
func (hs *HealthServer) handleLiveness(w http.ResponseWriter, r *http.Request) {
	hs.mu.RLock()
	isLive := hs.isLive
	hs.mu.RUnlock()

	if isLive {
		w.WriteHeader(http.StatusOK)
		w.Write([]byte("Live"))
	} else {
		w.WriteHeader(http.StatusServiceUnavailable)
		w.Write([]byte("Not Live"))
	}
}

// handleReadiness handles the /ready endpoint
func (hs *HealthServer) handleReadiness(w http.ResponseWriter, r *http.Request) {
	hs.mu.RLock()
	isReady := hs.isReady
	hs.mu.RUnlock()

	if !isReady {
		w.WriteHeader(http.StatusServiceUnavailable)
		w.Write([]byte("Not Ready"))
		return
	}

	// Check database connection
	ctx, cancel := context.WithTimeout(r.Context(), 2*time.Second)
	defer cancel()

	if err := hs.dbPool.Ping(ctx); err != nil {
		hs.logger.Error("database ping failed", "error", err)
		w.WriteHeader(http.StatusServiceUnavailable)
		w.Write([]byte("Database not ready"))
		return
	}

	// Check Kafka connectivity (if producer is configured)
	if hs.kafkaProducer != nil {
		// Simple ping: check if producer is still functional
		// We don't send actual messages, just verify the connection is alive
		// Note: This is a best-effort check since sarama doesn't expose a direct "ping"
		// In production, monitor actual message send success rates via metrics
		hs.logger.Debug("kafka health check: producer is configured")
		// If producer creation succeeded, assume healthy unless we get send errors
		// Alternative: could track last successful send timestamp
	}

	w.WriteHeader(http.StatusOK)
	w.Write([]byte("Ready"))
}
