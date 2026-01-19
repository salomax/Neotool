package workerpool

import (
	"context"
	"log/slog"
	"sync"
)

// WorkItem represents an item to be processed by a worker
type WorkItem interface {
	Process(ctx context.Context) error
}

// WorkerPool manages a pool of workers that process work items
type WorkerPool struct {
	size      int
	workQueue chan WorkItem
	workersWg sync.WaitGroup
	workersDone chan bool
	logger    *slog.Logger
}

// NewWorkerPool creates a new worker pool
func NewWorkerPool(size int, queueBufferMultiplier int, logger *slog.Logger) *WorkerPool {
	if queueBufferMultiplier <= 0 {
		queueBufferMultiplier = 2 // Default: 2x worker count
	}
	return &WorkerPool{
		size:       size,
		workQueue:  make(chan WorkItem, size*queueBufferMultiplier),
		workersDone: make(chan bool, 1),
		logger:     logger,
	}
}

// Start starts the worker pool with the given worker function
func (wp *WorkerPool) Start(ctx context.Context, workerFunc func(ctx context.Context, item WorkItem) error) {
	wp.logger.Info("Starting worker pool", "size", wp.size)

	// Start worker goroutines
	for i := 0; i < wp.size; i++ {
		wp.workersWg.Add(1)
		workerID := i + 1
		go func(id int) {
			defer wp.workersWg.Done()
			wp.worker(ctx, id, workerFunc)
		}(workerID)
	}

	// Monitor worker completion
	go func() {
		// Wait for all workers to actually finish processing
		wp.workersWg.Wait()
		wp.logger.Info("All workers completed")

		// Signal that all workers are done
		wp.workersDone <- true
	}()
}

// Submit submits a work item to the pool (non-blocking with context support)
func (wp *WorkerPool) Submit(ctx context.Context, item WorkItem) error {
	select {
	case wp.workQueue <- item:
		return nil
	case <-ctx.Done():
		return ctx.Err()
	}
}

// Shutdown gracefully shuts down the worker pool
func (wp *WorkerPool) Shutdown() {
	wp.logger.Info("Shutting down worker pool")
	close(wp.workQueue)
}

// Wait waits for all workers to complete
func (wp *WorkerPool) Wait() {
	<-wp.workersDone
}

// worker processes work items from the queue
func (wp *WorkerPool) worker(ctx context.Context, workerID int, workerFunc func(ctx context.Context, item WorkItem) error) {
	wp.logger.Info("Worker started", "worker_id", workerID)
	defer wp.logger.Info("Worker stopped", "worker_id", workerID)

	for {
		select {
		case <-ctx.Done():
			// Context cancelled - exit immediately
			return

		case item, ok := <-wp.workQueue:
			if !ok {
				// Work queue closed - exit gracefully
				return
			}

			// Process the work item
			if err := workerFunc(ctx, item); err != nil {
				wp.logger.Error("Worker failed to process item",
					"worker_id", workerID,
					"error", err)
				// Continue processing other items
			}
		}
	}
}


