# Concurrency Architecture Comparison: Go vs Python

## Overview

Both implementations consume from Kafka and process messages with rate limiting for CNPJá API, but use fundamentally different concurrency models.

---

## Go Implementation: Worker Pool Pattern

### Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    Kafka Consumer Group                      │
│                  (Sarama ConsumerGroup)                      │
└──────────────────┬──────────────────────────────────────────┘
                   │
                   │ Dispatches messages to buffered channel
                   ▼
         ┌─────────────────────┐
         │   Work Queue         │
         │ (Buffered Channel)   │
         │  Size: N_WORKERS × 2 │
         └─────────────────────┘
                   │
                   │ Multiple workers pull from queue
                   │
    ┌──────────────┼──────────────┬──────────────┐
    ▼              ▼              ▼              ▼
┌─────────┐  ┌─────────┐    ┌─────────┐    ┌─────────┐
│ Worker 1│  │ Worker 2│ .. │ Worker N│    │ Worker N│
└────┬────┘  └────┬────┘    └────┬────┘    └────┬────┘
     │            │              │              │
     │   All workers compete for CNPJá rate limiter
     │            │              │              │
     └────────────┴──────────────┴──────────────┘
                   │
                   ▼
         ┌─────────────────────┐
         │  CNPJá Rate Limiter  │
         │   (Mutex + Timer)    │
         │  15 sec min delay    │
         └─────────────────────┘
                   │
                   ▼
            CNPJá API Call
```

### Key Components

#### 1. Worker Pool ([consumer_runner.go:214-237](workflow/go/financial_data/institution_enhancement/internal/application/consumer_runner.go#L214-L237))

```go
func (cr *ConsumerRunner) startWorkerPool(ctx context.Context) {
    // Start N worker goroutines
    for i := 0; i < cr.config.WorkerPool.Size; i++ {
        cr.workersWg.Add(1)
        workerID := i + 1
        go func(id int) {
            defer cr.workersWg.Done()
            cr.worker(ctx, id)
        }(workerID)
    }
}
```

**Parameters:**
- `WORKER_POOL_SIZE`: Number of concurrent workers (default: 5)

#### 2. Work Queue ([consumer_runner.go:56](workflow/go/financial_data/institution_enhancement/internal/application/consumer_runner.go#L56))

```go
workQueue: make(chan workItem, config.WorkerPool.Size*2)
```

**Characteristics:**
- **Buffered channel**: Size = `N_WORKERS × 2`
- **Non-blocking dispatch**: Messages queue up if all workers busy
- **Fair distribution**: Any available worker can pick up any message

#### 3. CNPJá Rate Limiter ([rate_limiter.go:24-56](workflow/go/financial_data/institution_enhancement/internal/infrastructure/cnpja/rate_limiter.go#L24-L56))

```go
type RateLimiter struct {
    mu              sync.Mutex      // Protects shared state
    lastQueryTime   time.Time       // Last API call timestamp
    minDelaySeconds int             // 15 seconds
}

func (rl *RateLimiter) Wait(ctx context.Context) error {
    rl.mu.Lock()
    defer rl.mu.Unlock()

    elapsed := time.Since(rl.lastQueryTime)
    if elapsed < minDelay {
        waitTime := minDelay - elapsed
        // Wait with context cancellation support
        select {
        case <-time.After(waitTime):
        case <-ctx.Done():
            return ctx.Err()
        }
    }

    rl.lastQueryTime = time.Now()
    return nil
}
```

**Characteristics:**
- **Thread-safe**: Mutex ensures only one worker calls API at a time
- **Shared state**: All workers compete for the same rate limiter
- **Precise timing**: Guarantees exactly 15 seconds between API calls
- **Context-aware**: Can be cancelled during wait

### Concurrency Flow Example

```
Time    Worker 1              Worker 2              Worker 3              CNPJá Rate Limiter
────────────────────────────────────────────────────────────────────────────────────────────
0s      Pull msg A from queue
        ↓
        Lock rate limiter
        ↓
        No wait (first call)
        ↓
        Call CNPJá API ──────────────────────────────────────────────→  lastQueryTime = 0s

5s                            Pull msg B from queue
                              ↓
                              Lock rate limiter
                              ↓
                              Wait 10s (15s - 5s)
                              ↓
                              [BLOCKED]

10s                                                 Pull msg C from queue
                                                    ↓
                                                    Try lock (blocked by Worker 2)
                                                    ↓
                                                    [BLOCKED]

15s                           Wait complete
                              ↓
                              Call CNPJá API ────────────────────────→  lastQueryTime = 15s
                              ↓
                              Unlock rate limiter

15s                                                 Acquire lock
                                                    ↓
                                                    Wait 15s (30s - 15s)
                                                    ↓
                                                    [BLOCKED]

30s                                                 Wait complete
                                                    ↓
                                                    Call CNPJá API ──→  lastQueryTime = 30s
```

### Benefits

✅ **True parallelism**: Multiple workers process messages concurrently
✅ **Better throughput**: Workers can process other operations (DB writes, scraping) while one waits for rate limit
✅ **Graceful shutdown**: Workers can finish in-flight messages
✅ **Fair scheduling**: Any worker can pick up any message
✅ **Precise rate limiting**: Mutex ensures exact 15-second intervals

### Configuration

```bash
WORKER_POOL_SIZE=5           # Number of concurrent workers (default: 5)
CNPJA_RATE_LIMIT_SECONDS=15  # Min seconds between API calls (default: 15)
```

---

## Python Implementation: Single-Threaded Sequential

### Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    Kafka Consumer                            │
│                  (kafka-python)                              │
└──────────────────┬──────────────────────────────────────────┘
                   │
                   │ Synchronous iteration (for loop)
                   ▼
         ┌─────────────────────┐
         │  Consumer Loop       │
         │  (Single Thread)     │
         └─────────────────────┘
                   │
                   │ Process one message at a time
                   ▼
         ┌─────────────────────┐
         │ Retry Loop           │
         │ (max 3 attempts)     │
         └─────────────────────┘
                   │
                   │ Sequential execution
                   ▼
         ┌─────────────────────┐
         │ Rate Limit Check     │
         │ (Simple delay)       │
         │ 15 sec min delay     │
         └─────────────────────┘
                   │
                   ▼
            CNPJá API Call
```

### Key Components

#### 1. Consumer Loop ([consumer_base.py:102-106](workflow/python/common/src/neotool_common/consumer_base.py#L102-L106))

```python
for record in self._consumer:
    if not self._running:
        break
    self._handle_record(record)
```

**Characteristics:**
- **Synchronous**: Processes one message at a time
- **Blocking**: Next message waits until current completes
- **No concurrency**: Single-threaded execution

#### 2. Rate Limiting ([cnpja_client.py:21-36](workflow/python/financial_data/institution_enhancement/src/institution_enhancement/tasks/cnpja_client.py#L21-L36))

```python
_last_cnpja_query_time: Optional[float] = None

def enforce_rate_limit() -> None:
    global _last_cnpja_query_time

    if _last_cnpja_query_time is not None:
        elapsed = time.time() - _last_cnpja_query_time
        if elapsed < CNPJA_RATE_LIMIT_SECONDS:
            wait_time = CNPJA_RATE_LIMIT_SECONDS - elapsed
            time.sleep(wait_time)

    _last_cnpja_query_time = time.time()
```

**Characteristics:**
- **Global variable**: Shared state (not thread-safe)
- **Simple sleep**: Blocks entire process during wait
- **No mutex**: Would fail if used with threads
- **Sequential only**: Assumes single-threaded execution

#### 3. Retry Loop ([consumer_base.py:148-233](workflow/python/common/src/neotool_common/consumer_base.py#L148-L233))

```python
def _process_with_retry(self, message: Any, message_data: Dict[str, Any]):
    retry_count = 0
    max_retries = self._retry_config.max_retries

    while retry_count <= max_retries:
        try:
            result = self._process_message(message)
            if result.get("success"):
                return result

            # Calculate backoff with rate limit floor
            delay_ms = self._calculate_backoff(retry_count)
            time.sleep(delay_ms / 1000.0)
            retry_count += 1
        except Exception as exc:
            # Handle exception...
```

**Characteristics:**
- **Sequential retries**: Each retry blocks the entire consumer
- **Rate-limited backoff**: Min delay = max(backoff, rate_limit)
- **No parallelism**: Cannot process other messages during retry

### Concurrency Flow Example

```
Time    Main Thread           CNPJá Rate Limiter
────────────────────────────────────────────────
0s      Pull msg A
        ↓
        Process msg A
        ↓
        Check rate limit
        ↓
        No wait (first call)
        ↓
        Call CNPJá API ────→  lastQueryTime = 0s
        ↓
        Complete msg A
        ↓
        Commit msg A

5s      Pull msg B
        ↓
        Process msg B
        ↓
        Check rate limit
        ↓
        Wait 10s (15s - 5s)
        ↓
        [BLOCKED - cannot process msg C]

15s     Wait complete
        ↓
        Call CNPJá API ────→  lastQueryTime = 15s
        ↓
        Complete msg B
        ↓
        Commit msg B

16s     Pull msg C
        ↓
        Process msg C
        ↓
        Check rate limit
        ↓
        Wait 14s (30s - 16s)
        ↓
        [BLOCKED]

30s     Wait complete
        ↓
        Call CNPJá API ────→  lastQueryTime = 30s
```

### Limitations

❌ **No parallelism**: Only one message processed at a time
❌ **Poor throughput**: Entire process blocks during rate limit wait
❌ **Inefficient**: Cannot do other work (DB, scraping) while waiting
❌ **Not thread-safe**: Global variable, no mutex
❌ **Slower recovery**: Retries block entire consumer

### Configuration

```bash
CNPJA_RATE_LIMIT_SECONDS=15  # Min seconds between API calls (default: 15)
MAX_RETRIES=3                # Number of retry attempts (default: 3)
```

---

## Side-by-Side Comparison

| Feature | Go (Worker Pool) | Python (Sequential) |
|---------|------------------|---------------------|
| **Concurrency Model** | N workers (goroutines) | Single-threaded |
| **Message Processing** | Parallel | Sequential |
| **Rate Limiter** | Mutex-protected shared state | Global variable + sleep |
| **Thread Safety** | ✅ Yes (sync.Mutex) | ❌ No (global var) |
| **Throughput** | High (workers do other work while one waits) | Low (entire process blocks) |
| **Work Queue** | Buffered channel (N×2) | None (direct iteration) |
| **Kafka Offset Commit** | Per-message after processing | Per-message after processing |
| **Graceful Shutdown** | Workers finish in-flight messages | Current message completes |
| **Resource Usage** | Higher (N goroutines) | Lower (1 thread) |
| **Complexity** | Higher | Lower |

---

## Performance Analysis

### Scenario: 100 messages to process

**Assumptions:**
- CNPJá API call: 15s rate limit
- Processing time (DB, scraping): 5s
- Workers (Go): 5

#### Go (Worker Pool) - Timeline

```
0s:   W1 starts msg 1 → CNPJá at 0s
      W2 starts msg 2 → waits
      W3 starts msg 3 → waits
      W4 starts msg 4 → waits
      W5 starts msg 5 → waits

5s:   W1 finishes msg 1 (5s processing)
      W1 starts msg 6 (no CNPJá needed yet)

15s:  W2 calls CNPJá for msg 2

20s:  W2 finishes msg 2
      W2 starts msg 7

30s:  W3 calls CNPJá for msg 3

35s:  W3 finishes msg 3
      W3 starts msg 8

Total: ~300 seconds (5 minutes) for 100 messages
Throughput: ~20 messages/minute
```

**Workers do useful work (DB writes, scraping) while waiting for rate limit**

#### Python (Sequential) - Timeline

```
0s:   Start msg 1 → CNPJá at 0s
5s:   Finish msg 1 processing
      Start msg 2
      Wait 10s for rate limit

15s:  CNPJá call for msg 2
20s:  Finish msg 2 processing
      Start msg 3
      Wait 10s for rate limit

30s:  CNPJá call for msg 3
35s:  Finish msg 3 processing
      Start msg 4
      Wait 10s for rate limit

Total: ~2000 seconds (33 minutes) for 100 messages
Throughput: ~3 messages/minute
```

**Entire process blocks during rate limit wait**

### Performance Difference

- **Go**: ~300 seconds (5 minutes)
- **Python**: ~2000 seconds (33 minutes)
- **Speedup**: **~6.7x faster** with 5 workers

---

## Recommendations

### For Python Implementation

#### Option 1: Add Thread Pool (Quick Win)

```python
from concurrent.futures import ThreadPoolExecutor
from threading import Lock

class CNPJAClient:
    _rate_limiter_lock = Lock()
    _last_query_time = None

    def enforce_rate_limit(self):
        with self._rate_limiter_lock:
            # Thread-safe rate limiting
            ...

# In consumer
with ThreadPoolExecutor(max_workers=5) as executor:
    for record in consumer:
        executor.submit(process_message, record)
```

**Pros:**
- Minimal code changes
- Thread-safe rate limiting
- Better throughput

**Cons:**
- Python GIL limits CPU-bound parallelism
- More complex error handling

#### Option 2: Keep Sequential (Current)

**When to use:**
- Low message volume (<1000/day)
- Simplicity is priority
- Resource constraints (limited CPU/memory)

**Optimizations:**
- Reduce CNPJá rate limit if possible
- Cache website validation results
- Optimize Serper usage (only when needed)

#### Option 3: Migrate to Go (Long-term)

**Benefits:**
- True parallelism (no GIL)
- Better performance (6-7x faster)
- More efficient resource usage
- Production-ready patterns

**Costs:**
- Code rewrite effort
- Team Go expertise needed
- Testing/validation effort

---

## Current Status

### Go Implementation
✅ **Production-ready concurrency**
- 5 workers (configurable)
- Thread-safe rate limiting
- Graceful shutdown
- High throughput

### Python Implementation
⚠️ **Sequential processing**
- Single-threaded
- Simple rate limiting
- Lower throughput
- Easier to reason about

**Recommendation**: For production workloads with >1000 messages/day, consider adding thread pool to Python or using Go implementation.
