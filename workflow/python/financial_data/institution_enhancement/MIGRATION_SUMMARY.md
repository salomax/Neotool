# Institution Enhancement Python Migration Summary

## Overview
Updated the Python `institution_enhancement` implementation to match the Go implementation with new retry logic, www. prefix handling, and Serper API fallback.

## Changes Implemented

### 1. New Serper API Client
**File**: [src/institution_enhancement/tasks/serper_client.py](src/institution_enhancement/tasks/serper_client.py)

- Created new `SerperClient` class for Google Search API integration
- Implements website search with scoring algorithm based on relevance
- Excludes social media and government domains from results
- Configurable via environment variables:
  - `SERPER_ENABLED`: Enable/disable Serper API (default: false)
  - `SERPER_API_KEY`: API key for Serper
  - `SERPER_TIMEOUT_SECONDS`: Request timeout (default: 10)

### 2. Updated Configuration
**File**: [src/institution_enhancement/tasks/config.py](src/institution_enhancement/tasks/config.py)

Added new `SerperConfig` class:
```python
@dataclass
class SerperConfig:
    enabled: bool
    api_key: str
    timeout: int
```

### 3. Enhanced Retry Logic
**File**: [src/institution_enhancement/tasks/processor.py](src/institution_enhancement/tasks/processor.py)

#### Changed Retry Parameters (matching Go):
- **Old**: 3 retries with 40s, 60s, 80s backoff
- **New**: 2 retries with 30s, 60s backoff

```python
@retry(
    retry=retry_if_exception_type((requests.exceptions.Timeout, requests.exceptions.ConnectionError)),
    stop=stop_after_attempt(2),  # Changed from 3 to 2
    wait=wait_incrementing(start=30, increment=30, max=60),  # Changed from (40, 20, 80)
    ...
)
```

### 4. New www. Prefix Logic
**File**: [src/institution_enhancement/tasks/processor.py](src/institution_enhancement/tasks/processor.py)

Implemented two-attempt validation strategy:

#### New Helper Functions:
- `_has_www_prefix(url)`: Check if URL has www. subdomain
- `_add_www_prefix(url)`: Add www. to URL
- `_remove_www_prefix(url)`: Remove www. from URL

#### New Validation Flow:
```
1st Attempt: Try with www. prefix (if not already present)
   ├─ Success → Return validated URL with www.
   └─ Fail → Continue to 2nd attempt

2nd Attempt: Try without www. prefix (if 1st had it)
   ├─ Success → Return validated URL without www.
   └─ Fail → Return None
```

**Changed Return Type**: `validate_website_reachable()` now returns `Optional[str]` (the validated URL) instead of `bool`

### 5. Enhanced Website Extraction with Serper Fallback
**File**: [src/institution_enhancement/tasks/processor.py](src/institution_enhancement/tasks/processor.py)

#### Updated Function Signature:
```python
# OLD
def extract_website_from_receita_data(
    receita_data: Dict[str, Any],
    domain: Optional[str]
) -> Optional[str]

# NEW
def extract_website_from_receita_data(
    receita_data: Dict[str, Any],
    domain: Optional[str],
    institution_name: Optional[str],
    parent_cnpj: str,
    serper_client: Optional[SerperClient],
) -> Optional[str]
```

#### New Priority Order (matching Go):
1. **Special institution keywords** (SICOOB, CRESOL, SICREDI, UNICRED) - no validation needed
2. **CNPJá API response** (website, site, url, homepage, web fields) - **validated with www. logic**
3. **Email domain** - **validated with www. logic**
4. **Serper API search** (if enabled) - **validated with www. logic**

Each non-special website goes through the new two-attempt validation (www./no-www.).

### 6. Updated Process Flow
**File**: [src/institution_enhancement/tasks/processor.py](src/institution_enhancement/tasks/processor.py)

#### Changed in `process_message()`:
```python
# Determine institution name for website search
# Priority: message name, trade name, legal name
institution_name = message.nome_instituicao
if not institution_name:
    if trade_name:
        institution_name = trade_name
    elif legal_name:
        institution_name = legal_name

# Extract and validate website (includes Serper fallback)
website = extract_website_from_receita_data(
    receita_data,
    domain,
    institution_name,
    parent_cnpj,
    serper_client,
)
```

Removed separate validation step since validation is now integrated into extraction.

### 7. Updated Consumer Integration
**File**: [src/institution_enhancement/flows/consumer.py](src/institution_enhancement/flows/consumer.py)

```python
# Initialize Serper client (if enabled)
_serper_client = SerperClient.from_env()

# Wrapper to pass Serper client to process_message
def _process_message_with_serper(message: InstitutionUpsertedMessage) -> dict:
    return process_message(message, serper_client=_serper_client)

# Use wrapper in consumer runner
runner = KafkaConsumerRunner(
    ...
    process_message=_process_message_with_serper,
    ...
)
```

### 8. Updated Standalone CLI
**File**: [src/institution_enhancement/flows/standalone.py](src/institution_enhancement/flows/standalone.py)

```python
# Initialize Serper client (if enabled)
serper_client = SerperClient.from_env()

# Pass to process_message
result = process_message(message, serper_client=serper_client)
```

## Environment Variables

### New Variables:
- `SERPER_ENABLED`: Enable Serper API fallback (default: `false`)
- `SERPER_API_KEY`: Serper API key (required if enabled)
- `SERPER_TIMEOUT_SECONDS`: Serper request timeout (default: `10`)

### Unchanged Variables:
All existing environment variables remain the same.

## Behavioral Changes

### Website Validation:
1. **Timeout per request**: 20 seconds (unchanged)
2. **Retry attempts**: 2 attempts with 30s, 60s backoff (was: 3 attempts with 40s, 60s, 80s)
3. **New www. logic**: Automatically tries both with and without www. prefix
4. **Return value**: Now returns the validated URL (with or without www.) instead of boolean

### Website Extraction Priority:
1. Special websites (SICOOB, etc.) - **no validation** (unchanged)
2. CNPJá API - **now validated with www. logic**
3. Email domain - **now validated with www. logic**
4. **NEW**: Serper API fallback - **validated with www. logic**

### Example Flow:
```
Input: CNPJá returns "example.com.br"

1. Normalize to: "https://example.com.br"
2. First attempt: "https://www.example.com.br" (with www.)
   - If reachable → Return "https://www.example.com.br"
   - If not → Continue
3. Second attempt: "https://example.com.br" (without www.)
   - If reachable → Return "https://example.com.br"
   - If not → Try next source

If all sources fail and Serper is enabled:
4. Search Serper: "Institution Name CNPJ site oficial"
5. Score results and extract domain
6. Validate with www. logic
7. Return validated URL or None
```

## Breaking Changes

### Function Signatures Changed:
1. `validate_website_reachable(website)` → Returns `Optional[str]` instead of `bool`
2. `extract_website_from_receita_data()` → Added 3 new parameters
3. `process_message()` → Added optional `serper_client` parameter

### Test Updates Required:
All tests calling `extract_website_from_receita_data()` need to be updated to pass the new parameters:
```python
# OLD
result = extract_website_from_receita_data(receita_data, domain)

# NEW
result = extract_website_from_receita_data(
    receita_data,
    domain,
    institution_name=None,  # or actual name
    parent_cnpj="12345678901234",
    serper_client=None,  # or mock client
)
```

## Migration Checklist

- [x] Create Serper client implementation
- [x] Update configuration with Serper settings
- [x] Update retry logic (3→2 attempts, 40/60/80→30/60 backoff)
- [x] Implement www. prefix logic
- [x] Integrate Serper as fallback in website extraction
- [x] Update consumer flow
- [x] Update standalone CLI
- [ ] Update all tests to match new signatures
- [ ] Test with real Serper API key
- [ ] Update deployment configuration with Serper env vars

## Testing Recommendations

1. **Unit Tests**: Update test signatures for `extract_website_from_receita_data()`
2. **Integration Tests**: Test Serper integration with mock API
3. **Manual Testing**:
   - Test with `SERPER_ENABLED=false` (default behavior)
   - Test with `SERPER_ENABLED=true` and valid API key
   - Test www. prefix logic with various domains
   - Verify retry timing (30s, 60s backoff)

## Deployment Notes

1. **Backward Compatible**: Works without Serper (default: disabled)
2. **No Database Changes**: Schema remains unchanged
3. **New Dependency**: Requires `requests` library (already in requirements)
4. **Optional Feature**: Serper integration is opt-in via environment variable

## Performance Impact

- **Without Serper**: Slightly faster due to reduced retry attempts (2 vs 3)
- **With Serper**: Additional fallback may increase processing time for institutions without websites in CNPJá/email
- **www. logic**: Minimal impact, only adds one extra request per website validation attempt

## Comparison with Go Implementation

| Feature | Go | Python |
|---------|-----|---------|
| Retry attempts | 2 | 2 ✓ |
| Retry backoff | 30s, 60s | 30s, 60s ✓ |
| www. prefix logic | Yes | Yes ✓ |
| Serper fallback | Yes | Yes ✓ |
| Special websites | Yes | Yes ✓ |
| Error classification | Comprehensive | Basic (timeouts/connections) |
| Circuit breaker | Yes | No |
| Rate limiter | Token bucket | Simple delay |

## Future Enhancements

1. **Error Classification**: Port Go's comprehensive error classification logic
2. **Circuit Breaker**: Add circuit breaker for external APIs
3. **Rate Limiter**: Upgrade to token bucket algorithm
4. **Metrics**: Add Prometheus metrics for monitoring
5. **Caching**: Add Redis cache for validated websites
