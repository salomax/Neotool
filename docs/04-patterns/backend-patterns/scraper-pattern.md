---
title: Web Scraper Pattern
type: pattern
category: backend
status: current
version: 1.0.0
tags: [pattern, backend, scraper, web-scraping, python]
ai_optimized: true
search_keywords: [scraper, web-scraping, beautifulsoup, cloudscraper, python, anti-bot]
related:
  - 04-patterns/backend-patterns/kafka-consumer-pattern.md
  - 03-features/financial_data/institution-enhancement/institution-enhancement.md
---

# Web Scraper Pattern

> **Purpose**: Standard pattern for implementing web scrapers in Python for extracting data from websites, handling anti-bot protection, and validating content.

## Overview

This pattern defines the structure and best practices for implementing web scrapers that extract data from websites. It includes handling Cloudflare and other anti-bot protection, URL normalization, content validation, and error handling.

## Architecture

```
┌─────────────┐
│   Source    │
│   (URL)     │
└──────┬──────┘
       │
       ▼
┌─────────────────┐
│  URL Normalizer │
│  (add protocol)  │
└──────┬──────────┘
       │
       ▼
┌─────────────────┐
│  Cloudscraper   │
│  (bypass CF)    │
└──────┬──────────┘
       │
       ├─── Success ───► BeautifulSoup ───► Data Extraction ───► Validation ───► Result
       │
       └─── Failure ───► Error Handling ───► Logging ───► Return None
```

**Key Design Principles:**
- Use `cloudscraper` to bypass Cloudflare and other anti-bot protection
- Normalize URLs before scraping (ensure protocol is present)
- Validate content before saving (e.g., check if website is reachable)
- Handle timeouts and network errors gracefully
- Use structured logging for observability
- Return `None` on failure (don't raise exceptions unless critical)

## Implementation Structure

### Package Organization

```
workflow/<domain>/<feature>/tasks/
├── processor.py              # Main processor (may include scraping)
├── scraper.py                # Web scraping utilities (optional)
├── url_utils.py              # URL normalization and validation
└── validation.py              # Content validation utilities
```

## Code Pattern

### URL Normalization

Always normalize URLs before scraping to ensure they have a protocol:

```python
"""
URL Normalization Utilities
"""

def _normalize_url(url: str) -> str:
    """
    Normalize URL to ensure it has a protocol.
    
    Args:
        url: URL string (may or may not have protocol)
    
    Returns:
        URL with https:// protocol
    """
    url = url.strip()
    if not url.startswith(("http://", "https://")):
        return f"https://{url}"
    return url


def _normalize_social_url(href: str, base_url: str) -> str:
    """
    Normalize social network URL to absolute URL.
    
    Args:
        href: URL from href attribute
        base_url: Base website URL for relative URLs
    
    Returns:
        Absolute URL with protocol
    """
    if href.startswith(("http://", "https://")):
        return href
    elif href.startswith("//"):
        return f"https:{href}"
    elif href.startswith("/"):
        return f"{base_url.rstrip('/')}{href}"
    else:
        return f"https://{href}"
```

**Key Points:**
- Always strip whitespace from URLs
- Default to `https://` if no protocol is present
- Handle relative URLs by combining with base URL
- Handle protocol-relative URLs (`//example.com`)

### Website Validation

Validate that a website is reachable before saving or using it:

```python
"""
Website Validation

Validates that a website URL is reachable.
"""

import logging
import requests
import cloudscraper

logger = logging.getLogger(__name__)

# Trusted websites that don't need validation
TRUSTED_WEBSITES = {
    "https://www.sicoob.com.br",
    "https://cresol.com.br",
    "https://sicredi.com.br",
    "https://www.unicred.com.br/",
}


def validate_website_reachable(website: Optional[str]) -> bool:
    """
    Validate that a website URL is reachable.
    
    Uses cloudscraper to handle Cloudflare protection and other anti-bot measures.
    Trusted websites are skipped from validation.
    
    Args:
        website: Website URL to validate
        
    Returns:
        True if website is reachable (returns 2xx or 3xx status), False otherwise
    """
    if not website:
        return False
    
    # Skip validation for trusted websites
    if website in TRUSTED_WEBSITES:
        logger.info(f"Skipping validation for trusted website: {website}")
        return True
    
    try:
        # Use cloudscraper to bypass Cloudflare protection
        scraper = cloudscraper.create_scraper(
            browser={
                'browser': 'chrome',
                'platform': 'windows',
                'desktop': True
            }
        )
        
        # Give cloudscraper enough time to solve challenges (up to 20 seconds)
        response = scraper.get(website, timeout=20, allow_redirects=True)
        
        # Consider 2xx and 3xx as reachable
        is_reachable = 200 <= response.status_code < 400
        
        if not is_reachable:
            logger.warning(
                f"Website not reachable: {website} (status: {response.status_code})"
            )
        return is_reachable
        
    except (requests.exceptions.Timeout, requests.exceptions.ConnectionError) as e:
        logger.warning(f"Network error validating website {website}: {e}")
        return False
    except requests.exceptions.RequestException as e:
        logger.warning(f"Failed to validate website {website}: {e}")
        return False
    except Exception as e:
        logger.warning(f"Unexpected error validating website {website}: {e}", exc_info=True)
        return False
```

**Key Points:**
- Use `cloudscraper` instead of `requests` for Cloudflare-protected sites
- Configure browser fingerprint to match real browser
- Set appropriate timeout (20 seconds for Cloudflare challenges)
- Consider 2xx and 3xx status codes as reachable
- Log warnings for failures, not errors (expected behavior)
- Return `False` on any error (don't raise exceptions)

### Social Network Scraping

Extract social network links from websites:

```python
"""
Social Network Scraping

Scrapes social network links from website.
"""

import logging
from typing import Dict, Optional

import requests
import cloudscraper
from bs4 import BeautifulSoup

logger = logging.getLogger(__name__)


def scrape_social_networks(website: Optional[str]) -> Optional[Dict[str, str]]:
    """
    Scrape social network links from website.
    
    Uses cloudscraper to handle Cloudflare protection and other anti-bot measures.
    
    Args:
        website: Website URL to scrape
    
    Returns:
        Dictionary with social network links, or None if scraping fails
    """
    if not website:
        return None
    
    try:
        # Use cloudscraper to bypass Cloudflare protection
        scraper = cloudscraper.create_scraper(
            browser={
                'browser': 'chrome',
                'platform': 'windows',
                'desktop': True
            }
        )
        
        response = scraper.get(website, timeout=20, allow_redirects=True)
        response.raise_for_status()
        
        soup = BeautifulSoup(response.content, "html.parser")
        social_networks = {}
        
        # Common social network patterns
        social_patterns = {
            "facebook": ["facebook.com", "fb.com"],
            "instagram": ["instagram.com"],
            "linkedin": ["linkedin.com"],
            "twitter": ["twitter.com", "x.com"],
            "youtube": ["youtube.com", "youtu.be"],
            "whatsapp": ["whatsapp.com", "wa.me"],
        }
        
        # Find all links
        for link in soup.find_all("a", href=True):
            href = link.get("href", "").strip()
            if not href:
                continue
            
            href_lower = href.lower()
            
            # Check for social network URLs
            for platform, patterns in social_patterns.items():
                if platform not in social_networks:  # Only take first match per platform
                    for pattern in patterns:
                        if pattern in href_lower:
                            social_networks[platform] = _normalize_social_url(href, website)
                            break
        
        return social_networks if social_networks else None
        
    except requests.exceptions.Timeout:
        logger.warning(f"Timeout scraping social networks from {website}")
        return None
    except requests.exceptions.RequestException as e:
        logger.warning(f"Failed to scrape social networks from {website}: {e}")
        return None
    except Exception as e:
        logger.warning(f"Unexpected error scraping social networks from {website}: {e}", exc_info=True)
        return None
```

**Key Points:**
- Use `cloudscraper` for Cloudflare-protected sites
- Parse HTML with `BeautifulSoup`
- Use pattern matching to find social network links
- Only take first match per platform (avoid duplicates)
- Normalize URLs to absolute URLs
- Return `None` on failure (don't raise exceptions)
- Log warnings for failures (expected behavior)

### Data Extraction Helpers

Extract structured data from nested dictionaries or HTML:

```python
"""
Data Extraction Utilities
"""

from typing import Any, Dict, List, Optional


def _extract_string_field(data: Dict[str, Any], path: List[str]) -> Optional[str]:
    """
    Extract a string field from nested dictionary using a path.
    
    Args:
        data: Nested dictionary
        path: List of keys to navigate (e.g., ["alias"] or ["company", "name"])
    
    Returns:
        Stripped string value, or None if not available
    """
    if not data:
        return None
    
    value = data
    for key in path:
        if not isinstance(value, dict):
            return None
        value = value.get(key)
        if not value:
            return None
    
    if not isinstance(value, str):
        return None
    
    value = value.strip()
    return value if value else None
```

**Key Points:**
- Use path-based extraction for nested data
- Return `None` for missing or invalid values
- Always strip whitespace from string values
- Type-check at each level of navigation

### Domain Extraction with Blacklist

Extract domains from email lists, filtering out common email providers:

```python
"""
Domain Extraction

Extract domain from emails list, filtering out blacklisted email provider domains.
"""

# Blacklist of common email provider domains that are not company domains
DOMAIN_BLACKLIST = {
    "gmail.com",
    "hotmail.com",
    "outlook.com",
    "live.com",
    "yahoo.com",
    "icloud.com",
    "bol.com.br",
    "ig.com.br",
    "terra.com.br",
    "uol.com.br",
    "globo.com",
    "r7.com",
    "superig.com.br",
    "zipmail.com.br",
    "tutanota.com",
    "protonmail.com",
    "uai.com.br",
}


def extract_domain_from_emails(emails: List[Dict[str, Any]]) -> Optional[str]:
    """
    Extract domain from emails list in API response.
    
    Filters out blacklisted email provider domains (e.g., gmail.com, hotmail.com)
    and returns the first non-blacklisted domain found.
    
    Args:
        emails: List of email dictionaries with 'domain' field
    
    Returns:
        Domain string, or None if no valid (non-blacklisted) emails found
    """
    if not emails:
        return None
    
    # Iterate through emails to find first non-blacklisted domain
    for email in emails:
        domain = email.get("domain")
        if not domain:
            continue
        
        domain = domain.strip()
        if not domain:
            continue
        
        # Check if domain is blacklisted (case-insensitive)
        if domain.lower() not in DOMAIN_BLACKLIST:
            return domain
    
    # All domains were blacklisted or invalid
    return None
```

**Key Points:**
- Maintain a blacklist of common email provider domains
- Use case-insensitive comparison
- Return first non-blacklisted domain found
- Return `None` if all domains are blacklisted

### Special Rules and Trusted Sources

Implement special rules for known websites or trusted sources:

```python
"""
Special Rules: keywords mapped to their websites (checked in priority order)
These websites are trusted and don't need validation
"""
SPECIAL_WEBSITES = {
    "sicoob": "https://www.sicoob.com.br",
    "cresol": "https://cresol.com.br",
    "sicredi": "https://sicredi.com.br",
    "unicred": "https://www.unicred.com.br/",
}


def extract_website_from_data(data: Dict[str, Any], domain: Optional[str]) -> Optional[str]:
    """
    Extract website URL from API response or construct from domain.
    
    Checks special rules first, then API response, then constructs from domain.
    """
    if not data:
        return None
    
    alias = data.get("alias", "") or ""
    company_name = data.get("company", {}).get("name", "") or ""
    
    # Check special rules: if keyword is found in alias or company_name, return corresponding website
    alias_lower = alias.lower()
    company_name_lower = company_name.lower()
    
    for keyword, website in SPECIAL_WEBSITES.items():
        if keyword in alias_lower or keyword in company_name_lower:
            return website
    
    # Try to get website directly from API response
    website = next(
        (data.get(field) for field in ["website", "site", "url", "homepage", "web"] if data.get(field)),
        None
    )
    if website:
        website = website.strip()
        if website:
            return _normalize_url(website)
    
    # Fallback: construct from domain
    if domain:
        return f"https://{domain}"
    
    # Fallback: construct from trade name
    if alias:
        trade_name_normalized = alias.lower().strip().replace(" ", "")
        if trade_name_normalized:
            return f"https://{trade_name_normalized}.com.br"
    
    return None
```

**Key Points:**
- Check special rules first (highest priority)
- Try multiple field names for website URL
- Fallback to domain-based construction
- Fallback to trade name-based construction
- Always normalize URLs before returning

## Configuration

### Cloudscraper Configuration

```python
import cloudscraper

# Create scraper with browser fingerprint
scraper = cloudscraper.create_scraper(
    browser={
        'browser': 'chrome',
        'platform': 'windows',
        'desktop': True
    }
)

# Use scraper for requests
response = scraper.get(url, timeout=20, allow_redirects=True)
```

**Key Configuration:**
- Use `chrome` browser fingerprint (most common)
- Use `windows` platform (most common)
- Set `desktop=True` for desktop browser fingerprint
- Set timeout to 20 seconds (Cloudflare challenges can take time)
- Enable `allow_redirects=True` to follow redirects

### Timeout Configuration

```python
# Recommended timeouts
SCRAPER_TIMEOUT = 20  # seconds - enough for Cloudflare challenges
VALIDATION_TIMEOUT = 20  # seconds - same as scraper timeout
```

**Key Points:**
- Use 20 seconds for Cloudflare-protected sites
- Use shorter timeouts (5-10 seconds) for simple sites
- Consider retry logic for timeouts (may be transient)

## Error Handling

### Error Handling Strategy

```python
def scrape_data(url: str) -> Optional[Dict[str, Any]]:
    """
    Scrape data from URL.
    
    Returns:
        Dictionary with scraped data, or None if scraping fails
    """
    try:
        # Scraping logic
        return result
    except requests.exceptions.Timeout:
        logger.warning(f"Timeout scraping {url}")
        return None
    except requests.exceptions.RequestException as e:
        logger.warning(f"Failed to scrape {url}: {e}")
        return None
    except Exception as e:
        logger.warning(f"Unexpected error scraping {url}: {e}", exc_info=True)
        return None
```

**Key Points:**
- Return `None` on failure (don't raise exceptions unless critical)
- Log warnings for expected failures (timeouts, network errors)
- Log errors with `exc_info=True` for unexpected exceptions
- Use structured logging with context (URL, error type)

## Best Practices

1. **Use Cloudscraper for Protected Sites**: Always use `cloudscraper` instead of `requests` for Cloudflare-protected sites
2. **Normalize URLs**: Always normalize URLs before scraping (ensure protocol is present)
3. **Validate Before Saving**: Validate that websites are reachable before saving to database
4. **Handle Timeouts Gracefully**: Set appropriate timeouts and handle timeout errors gracefully
5. **Return None on Failure**: Return `None` instead of raising exceptions for expected failures
6. **Structured Logging**: Use structured logging with context (URL, error type, status code)
7. **Blacklist Common Domains**: Maintain blacklists for common email providers or invalid domains
8. **Special Rules**: Implement special rules for known websites or trusted sources
9. **Pattern Matching**: Use pattern matching to find social network links or other structured data
10. **First Match Only**: Only take first match per platform/category to avoid duplicates

## Common Pitfalls

1. **Not Using Cloudscraper**: Using `requests` directly on Cloudflare-protected sites will fail
2. **Missing Protocol**: Not normalizing URLs can cause connection errors
3. **Too Short Timeouts**: Timeouts shorter than 20 seconds may fail on Cloudflare challenges
4. **Raising Exceptions**: Raising exceptions for expected failures (timeouts, 404s) makes error handling complex
5. **Not Validating Before Saving**: Saving unreachable URLs to database wastes storage and causes issues later
6. **Not Handling Relative URLs**: Not converting relative URLs to absolute URLs breaks links
7. **No Blacklist**: Not filtering out common email providers can result in invalid domains
8. **Duplicate Matches**: Taking all matches instead of first match can create duplicate entries

## Testing

### Unit Tests

```python
import pytest
from tasks.scraper import validate_website_reachable, scrape_social_networks


def test_validate_website_reachable_success():
    """Test successful website validation."""
    result = validate_website_reachable("https://example.com")
    assert result is True


def test_validate_website_reachable_timeout():
    """Test website validation with timeout."""
    result = validate_website_reachable("https://timeout.example.com")
    assert result is False


def test_scrape_social_networks_success():
    """Test successful social network scraping."""
    result = scrape_social_networks("https://example.com")
    assert result is not None
    assert "facebook" in result or "instagram" in result


def test_scrape_social_networks_failure():
    """Test social network scraping failure."""
    result = scrape_social_networks("https://invalid-url-that-does-not-exist.com")
    assert result is None
```

**Key Testing Points:**
- Test successful scraping
- Test timeout scenarios
- Test network error scenarios
- Test invalid URL scenarios
- Test empty result scenarios
- Mock `cloudscraper` responses for unit tests

## Related Patterns

- [Kafka Consumer Pattern](./kafka-consumer-pattern.md) - Scrapers are often used in Kafka consumers
- [Service Pattern](./service-pattern.md) - Scrapers can be part of service layer
- [Repository Pattern](./repository-pattern.md) - Scraped data is often stored via repositories

## Examples

- [Institution Enhancement Processor](../../../workflow/python/financial_data/institution_enhancement/tasks/processor.py) - Example of website validation and social network scraping

