# Neotool Common

Shared utilities for Neotool workflow components.

## Features

- **KafkaConsumerRunner**: Base class for Kafka consumers with retry logic, DLQ, and health checks
- **Error Classification**: Retryable vs non-retryable error detection
- **Retry Utilities**: Exponential backoff calculation

## Installation

### Development (editable mode)
```bash
cd workflow/common
pip install -e ".[dev]"
```

### Production
```bash
pip install ./workflow/common
```

## Usage

```python
from neotool_common.consumer_base import KafkaConsumerRunner
from neotool_common.error_classification import is_retryable_error
from neotool_common.retry_utils import calculate_backoff
```
