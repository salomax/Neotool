"""
Institution Enhancement Processor

Business logic for processing institution enhancement messages.
"""

import logging
import time
from typing import Any, Dict, List, Optional

import psycopg2
import requests
import cloudscraper
from bs4 import BeautifulSoup
from psycopg2.extras import Json
from tenacity import (
    retry,
    retry_if_exception_type,
    stop_after_attempt,
    wait_incrementing,
    before_sleep_log,
    after_log,
)

from institution_enhancement.tasks.cnpj_utils import construct_cnpj, validate_cnpj_format
from institution_enhancement.tasks.cnpja_client import query_cnpja
from institution_enhancement.tasks.db_connection import (
    get_db_connection,
    return_db_connection,
)
from institution_enhancement.tasks.message import InstitutionUpsertedMessage
from institution_enhancement.tasks.serper_client import SerperClient

logger = logging.getLogger(__name__)

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

# Field names that might contain website URL in API response
WEBSITE_FIELD_NAMES = ["website", "site", "url", "homepage", "web"]

# Special rules: keywords mapped to their websites (checked in priority order)
# These websites are trusted and don't need validation
SPECIAL_WEBSITES = {
    "sicoob": "https://www.sicoob.com.br",
    "cresol": "https://cresol.com.br",
    "sicredi": "https://sicredi.com.br",
    "unicred": "https://www.unicred.com.br/",
}


def extract_domain_from_emails(emails: List[Dict[str, Any]]) -> Optional[str]:
    """
    Extract domain from emails list in CNPJá response.
    
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


def _extract_string_field(receita_data: Dict[str, Any], path: List[str]) -> Optional[str]:
    """
    Extract a string field from nested dictionary using a path.
    
    Args:
        receita_data: CNPJá API response data
        path: List of keys to navigate (e.g., ["alias"] or ["company", "name"])
    
    Returns:
        Stripped string value, or None if not available
    """
    if not receita_data:
        return None
    
    value = receita_data
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


def extract_website_from_receita_data(
    receita_data: Dict[str, Any],
    domain: Optional[str],
    institution_name: Optional[str],
    parent_cnpj: str,
    serper_client: Optional[SerperClient],
) -> Optional[str]:
    """
    Extract and validate website URL using multiple strategies.

    Priority order (matching Go implementation):
    1. Check for special institution keywords (SICOOB, CRESOL, etc.) - no validation needed
    2. Extract from CNPJá API response (website, site, url, homepage, web fields)
    3. Construct from email domain if available
    4. Search using Serper API (if enabled)

    Validation (only for non-special websites):
    - First attempt: Add "www." prefix if not present (HTTP client retries: 2 attempts with 30s, 60s backoff)
    - Second attempt: Remove "www." prefix if first attempt failed
    - Accept 2xx and 3xx status codes as "reachable"
    - Timeout: 20 seconds per request

    Args:
        receita_data: CNPJá API response data
        domain: Extracted domain from emails
        institution_name: Name of the institution (for Serper search)
        parent_cnpj: Parent CNPJ (for Serper search)
        serper_client: Serper client instance (optional)

    Returns:
        Validated website URL string, or None if not available
    """
    logger.info(
        f"Starting website extraction: has_receita_data={receita_data is not None}, "
        f"has_domain={domain is not None}, has_institution_name={institution_name is not None}, "
        f"has_serper_client={serper_client is not None}"
    )

    # Priority 1: Check for special institution keywords
    if institution_name:
        logger.info(f"Checking for special institution website: institution_name={institution_name}")
        alias_lower = institution_name.lower()

        for keyword, website in SPECIAL_WEBSITES.items():
            if keyword in alias_lower:
                logger.info(
                    f"Using special website (no validation needed): "
                    f"institution_name={institution_name}, website={website}"
                )
                return website

        logger.info(f"No special website found for institution: institution_name={institution_name}")

    # Priority 2: Extract from CNPJá API response
    if receita_data:
        # Check common field names that might contain website
        api_website = next(
            (receita_data.get(field) for field in WEBSITE_FIELD_NAMES if receita_data.get(field)),
            None
        )

        logger.info(
            f"Extracting website from CNPJá API: has_website={api_website is not None}, "
            f"raw_website={api_website}"
        )

        if api_website:
            api_website = api_website.strip()
            if api_website:
                normalized = _normalize_url(api_website)
                logger.info(f"Normalized website from CNPJá API: original={api_website}, normalized={normalized}")

                validated_url = validate_website_reachable(normalized)
                if validated_url:
                    logger.info(f"Website validated from CNPJá API: url={validated_url}")
                    return validated_url
                logger.warning(f"Website from CNPJá API failed validation: url={normalized}")
        else:
            logger.info("No website found in CNPJá API response")

    # Priority 3: Construct from email domain
    if domain:
        logger.info(f"Attempting to construct website from email domain: email_domain={domain}")
        domain_website = _normalize_url(domain)
        logger.info(f"Normalized email domain to URL: original={domain}, normalized={domain_website}")

        validated_url = validate_website_reachable(domain_website)
        if validated_url:
            logger.info(f"Website validated from email domain: url={validated_url}")
            return validated_url
        logger.warning(f"Website from email domain failed validation: url={domain_website}")
    else:
        logger.info("No email domain available for website construction")

    # Priority 4: Search using Serper API (if enabled)
    if serper_client and institution_name:
        logger.info(
            f"Attempting to find website using Serper API: "
            f"institution_name={institution_name}, cnpj={parent_cnpj}"
        )

        serper_website = serper_client.search_website(institution_name, parent_cnpj)
        if serper_website:
            logger.info(
                f"Website found using Serper API: url={serper_website}, "
                f"institution_name={institution_name}"
            )

            # Validate the website found by Serper
            validated_url = validate_website_reachable(serper_website)
            if validated_url:
                logger.info(f"Website from Serper validated successfully: url={validated_url}")
                return validated_url
            logger.warning(f"Website from Serper failed validation: url={serper_website}")
        else:
            logger.info("No website found using Serper API")
    else:
        if not serper_client:
            logger.info("Serper API not enabled (client is None)")
        else:
            logger.info("Serper API skipped (no institution name)")

    # No valid website found
    logger.warning("No valid website found after all attempts")
    return None


def extract_trade_name(receita_data: Dict[str, Any]) -> Optional[str]:
    """
    Extract trade name from CNPJá API response.
    
    Args:
        receita_data: CNPJá API response data
        
    Returns:
        Trade name string, or None if not available
    """
    return _extract_string_field(receita_data, ["alias"])


def extract_legal_name(receita_data: Dict[str, Any]) -> Optional[str]:
    """
    Extract legal name from CNPJá API response.
    
    Args:
        receita_data: CNPJá API response data
        
    Returns:
        Legal name string, or None if not available
    """
    return _extract_string_field(receita_data, ["company", "name"])


@retry(
    retry=retry_if_exception_type((requests.exceptions.Timeout, requests.exceptions.ConnectionError)),
    stop=stop_after_attempt(2),  # Changed from 3 to 2 retries (total 2 attempts)
    wait=wait_incrementing(start=30, increment=30, max=60),  # Changed: 30s, 60s backoff
    before_sleep=before_sleep_log(logger, logging.WARNING),
    after=after_log(logger, logging.INFO),
    reraise=True,  # Re-raise to let retry mechanism work, then catch in outer try/except
)
def _validate_website_reachable_attempt(website: str) -> bool:
    """
    Single attempt to validate that a website URL is reachable.
    
    This is the inner function that performs the actual HTTP request.
    The retry logic is handled by the decorator.
    
    Args:
        website: Website URL to validate
        
    Returns:
        True if website is reachable (returns 2xx or 3xx status), False otherwise
        
    Raises:
        requests.exceptions.Timeout: If request times out
        requests.exceptions.ConnectionError: If connection fails
    """
    # Use cloudscraper to bypass Cloudflare protection
    # Let cloudscraper set its own User-Agent and headers for better compatibility
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


def _has_www_prefix(url: str) -> bool:
    """
    Check if URL already has www. subdomain.

    Args:
        url: URL to check

    Returns:
        True if URL has www. prefix, False otherwise
    """
    # Remove protocol
    without_protocol = url
    if "://" in url:
        without_protocol = url.split("://", 1)[1]

    return without_protocol.startswith("www.")


def _add_www_prefix(url: str) -> str:
    """
    Add www. to the URL if it doesn't have it.

    Args:
        url: URL to modify

    Returns:
        URL with www. prefix
    """
    # Extract protocol
    protocol = "https://"
    domain = url

    if "://" in url:
        protocol, domain = url.split("://", 1)
        protocol += "://"

    # Add www. to domain
    return f"{protocol}www.{domain}"


def _remove_www_prefix(url: str) -> str:
    """
    Remove www. from the URL if it has it.

    Args:
        url: URL to modify

    Returns:
        URL without www. prefix
    """
    # Extract protocol
    protocol = "https://"
    domain = url

    if "://" in url:
        protocol, domain = url.split("://", 1)
        protocol += "://"

    # Remove www. from domain if present
    if domain.startswith("www."):
        domain = domain[4:]

    return f"{protocol}{domain}"


def validate_website_reachable(website: Optional[str]) -> Optional[str]:
    """
    Validate that a website URL is reachable.

    Uses cloudscraper to handle Cloudflare protection and other anti-bot measures.
    Trusted websites (from SPECIAL_WEBSITES) are skipped from validation.

    New logic (matching Go implementation):
    - First attempt: Try with www. prefix if not present (retries: 2 attempts with 30s, 60s backoff)
    - Second attempt: Try without www. prefix if first attempt failed
    - Returns the validated URL (with or without www.) or None if unreachable

    Args:
        website: Website URL to validate

    Returns:
        Validated URL (with or without www. prefix), or None if not reachable
    """
    if not website:
        return None

    # Skip validation for trusted websites (manually set from SPECIAL_WEBSITES)
    trusted_websites = set(SPECIAL_WEBSITES.values())
    if website in trusted_websites:
        logger.info(f"Skipping validation for trusted website: {website}")
        return website

    logger.info(f"Starting website validation: url={website}")

    # First attempt: try with www. prefix (if not already present)
    if not _has_www_prefix(website):
        first_attempt_url = _add_www_prefix(website)
        logger.info(
            f"First attempt: trying with www prefix - "
            f"original_url={website}, attempt_url={first_attempt_url}"
        )
    else:
        first_attempt_url = website
        logger.info(f"First attempt: URL already has www prefix - attempt_url={first_attempt_url}")

    try:
        if _validate_website_reachable_attempt(first_attempt_url):
            logger.info(f"Website validated successfully on first attempt: url={first_attempt_url}")
            return first_attempt_url
        else:
            logger.warning(f"Website not reachable on first attempt: url={first_attempt_url}")
    except (requests.exceptions.Timeout, requests.exceptions.ConnectionError) as e:
        logger.warning(
            f"First attempt failed: url={first_attempt_url}, error={e}"
        )
    except requests.exceptions.RequestException as e:
        logger.warning(f"First attempt failed: url={first_attempt_url}, error={e}")
    except Exception as e:
        logger.warning(
            f"First attempt failed: url={first_attempt_url}, error={e}",
            exc_info=True
        )

    # Second attempt: try without www. prefix (if first attempt had it)
    if _has_www_prefix(first_attempt_url):
        website_without_www = _remove_www_prefix(first_attempt_url)
        logger.info(
            f"Second attempt: trying without www prefix - "
            f"original_url={first_attempt_url}, attempt_url={website_without_www}"
        )

        try:
            if _validate_website_reachable_attempt(website_without_www):
                logger.info(
                    f"Website validated successfully on second attempt: url={website_without_www}"
                )
                return website_without_www
            else:
                logger.warning(
                    f"Website not reachable on second attempt: url={website_without_www}"
                )
        except (requests.exceptions.Timeout, requests.exceptions.ConnectionError) as e:
            logger.warning(
                f"Second attempt failed: url={website_without_www}, error={e}"
            )
        except requests.exceptions.RequestException as e:
            logger.warning(f"Second attempt failed: url={website_without_www}, error={e}")
        except Exception as e:
            logger.warning(
                f"Second attempt failed: url={website_without_www}, error={e}",
                exc_info=True
            )
    else:
        logger.info(
            f"Skipping second attempt (first attempt didn't have www): url={first_attempt_url}"
        )

    logger.warning(f"All website validation attempts failed: original_url={website}")
    return None


@retry(
    retry=retry_if_exception_type((requests.exceptions.Timeout, requests.exceptions.ConnectionError)),
    stop=stop_after_attempt(3),
    wait=wait_incrementing(start=40, increment=20, max=80),
    before_sleep=before_sleep_log(logger, logging.WARNING),
    after=after_log(logger, logging.INFO),
    reraise=True,  # Re-raise to let retry mechanism work, then catch in outer try/except
)
def _scrape_social_networks_attempt(website: str) -> Dict[str, str]:
    """
    Single attempt to scrape social network links from website.
    
    This is the inner function that performs the actual HTTP request and parsing.
    The retry logic is handled by the decorator.
    
    Args:
        website: Website URL to scrape
        
    Returns:
        Dictionary with social network links
        
    Raises:
        requests.exceptions.Timeout: If request times out
        requests.exceptions.ConnectionError: If connection fails
        requests.exceptions.HTTPError: If HTTP error occurs
    """
    # Use cloudscraper to bypass Cloudflare protection
    # Let cloudscraper set its own headers for better compatibility
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

    return social_networks


def scrape_social_networks(website: Optional[str]) -> Optional[Dict[str, str]]:
    """
    Scrape social network links from website.
    
    Uses cloudscraper to handle Cloudflare protection and other anti-bot measures.
    Retries up to 3 times with backoff delays of 40s, 60s, and 80s for timeout/connection errors.

    Args:
        website: Website URL to scrape

    Returns:
        Dictionary with social network links, or None if scraping fails
    """
    if not website:
        return None

    try:
        social_networks = _scrape_social_networks_attempt(website)
        return social_networks if social_networks else None
    except (requests.exceptions.Timeout, requests.exceptions.ConnectionError) as e:
        logger.warning(f"Network error scraping social networks from {website} after retries: {e}")
        return None
    except requests.exceptions.RequestException as e:
        logger.warning(f"Failed to scrape social networks from {website}: {e}")
        return None
    except Exception as e:
        logger.warning(f"Unexpected error scraping social networks from {website}: {e}", exc_info=True)
        return None


def _is_connection_closed(conn: psycopg2.extensions.connection) -> bool:
    """
    Check if connection is closed, handling both real connections and mocks.

    Args:
        conn: Database connection to check

    Returns:
        True if connection is closed, False otherwise
    """
    try:
        is_closed = conn.closed
        # If closed is a mock object (not a real bool), assume not closed
        if not isinstance(is_closed, bool):
            return False
        return is_closed
    except AttributeError:
        # Connection without 'closed' attribute - assume not closed
        return False


def get_parent_cnpj(message: InstitutionUpsertedMessage) -> str:
    """
    Get parent CNPJ for the institution.

    Uses cnpj_instituicao_lider if available, otherwise constructs CNPJ from bacen_cod_inst.

    Args:
        message: Institution upserted message

    Returns:
        Parent CNPJ string

    Raises:
        ValueError: If CNPJ cannot be determined or is invalid
    """
    if message.cnpj_instituicao_lider:
        cnpj = message.cnpj_instituicao_lider
        if not validate_cnpj_format(cnpj):
            raise ValueError(f"Invalid CNPJ format: {cnpj}")
        return cnpj

    constructed = construct_cnpj(message.bacen_cod_inst)
    if not constructed:
        raise ValueError(f"Cannot construct CNPJ from bacen_cod_inst: {message.bacen_cod_inst}")

    if not validate_cnpj_format(constructed):
        raise ValueError(f"Constructed CNPJ has invalid format: {constructed}")

    return constructed


def process_message(message: InstitutionUpsertedMessage, serper_client: Optional[SerperClient] = None) -> Dict[str, Any]:
    """
    Process institution enhancement message.

    This function:
    1. Constructs CNPJ from bacen_cod_inst
    2. Queries CNPJá API for company information
    3. Extracts domain from emails
    4. Extracts website URL from API response or constructs from domain
    5. Validates website is reachable before saving
    6. Scrapes social network links from website (if reachable)
    7. Stores enhancement data in database

    Args:
        message: Institution upserted message

    Returns:
        Dictionary with processing results:
        - success: bool
        - parent_cnpj: str
        - domain: Optional[str]
        - legal_name: Optional[str]
        - trade_name: Optional[str]
        - website: Optional[str]
        - error: Optional[str]

    Raises:
        ValueError: If message is invalid or CNPJ construction fails
    """
    # Validate message
    message.validate()

    # Get parent CNPJ
    parent_cnpj = get_parent_cnpj(message)

    logger.info(
        f"Processing message: bacen_cod_inst={message.bacen_cod_inst}, "
        f"parent_cnpj={parent_cnpj}, event_id={message.event_id}"
    )

    # Query CNPJá API
    cnpja_result = query_cnpja(parent_cnpj)

    if cnpja_result["found"] is None:
        # Error querying API
        error_msg = cnpja_result.get("error", "Unknown error")
        logger.error(
            f"CNPJá API error for parent_cnpj={parent_cnpj}, "
            f"bacen_cod_inst={message.bacen_cod_inst}: {error_msg}"
        )
        return {
            "success": False,
            "parent_cnpj": parent_cnpj,
            "domain": None,
            "legal_name": None,
            "trade_name": None,
            "website": None,
            "error": error_msg,
        }

    # Extract domain if data found
    domain = None
    receita_data = None
    website = None
    social_network = None
    legal_name = None
    trade_name = None

    if cnpja_result["found"] and cnpja_result["data"]:
        receita_data = cnpja_result["data"]
        emails = receita_data.get("emails", [])
        domain = extract_domain_from_emails(emails)

        # Extract legal name and trade name
        legal_name = extract_legal_name(receita_data)
        trade_name = extract_trade_name(receita_data)

        # Determine institution name for website search
        # Priority: message name, trade name, legal name
        institution_name = message.nome_instituicao
        name_source = "message"
        if not institution_name:
            if trade_name:
                institution_name = trade_name
                name_source = "trade_name"
            elif legal_name:
                institution_name = legal_name
                name_source = "legal_name"

        if institution_name:
            logger.info(
                f"Using institution name for website search: name={institution_name}, source={name_source}"
            )

        # Extract and validate website (includes Serper fallback if enabled)
        website = extract_website_from_receita_data(
            receita_data,
            domain,
            institution_name,
            parent_cnpj,
            serper_client,
        )

        # Scrape social networks (if website is available)
        if website:
            social_network = scrape_social_networks(website)
            if social_network:
                logger.info(
                    f"Found social networks for {parent_cnpj}: {list(social_network.keys())}"
                )

    # TODO: Implement logo download from asset management service

    # Store enhancement data in database
    start_time = time.time()
    conn = get_db_connection()
    try:
        with conn.cursor() as cur:
            cur.execute(
                """
                INSERT INTO financial_data.parent_institution (
                    parent_cnpj, email_domain, website, logo_storage_key,
                    social_network, receita_data, legal_name, trade_name,
                    created_at, updated_at
                ) VALUES (
                    %s, %s, %s, %s, %s, %s, %s, %s, NOW(), NOW()
                )
                ON CONFLICT (parent_cnpj) DO UPDATE SET
                    email_domain = EXCLUDED.email_domain,
                    website = EXCLUDED.website,
                    logo_storage_key = EXCLUDED.logo_storage_key,
                    social_network = EXCLUDED.social_network,
                    receita_data = EXCLUDED.receita_data,
                    legal_name = EXCLUDED.legal_name,
                    trade_name = EXCLUDED.trade_name,
                    updated_at = NOW()
                """,
                (
                    parent_cnpj,
                    domain,
                    website,
                    None,  # logo_storage_key (TODO: download from asset management service)
                    Json(social_network) if social_network else None,
                    Json(receita_data) if receita_data else None,
                    legal_name,
                    trade_name,
                ),
            )
        conn.commit()
        processing_time = time.time() - start_time
        logger.info(
            f"Successfully stored enhancement data: parent_cnpj={parent_cnpj}, "
            f"domain={domain}, website={website}, "
            f"legal_name={legal_name}, trade_name={trade_name}, "
            f"social_networks={list(social_network.keys()) if social_network else None}, "
            f"processing_time={processing_time:.2f}s"
        )
    except Exception as e:
        # Rollback on error if connection is still open
        if not _is_connection_closed(conn):
            conn.rollback()
        logger.error(
            f"Database error storing enhancement data: parent_cnpj={parent_cnpj}, "
            f"bacen_cod_inst={message.bacen_cod_inst}, error={e}",
            exc_info=True,
        )
        raise
    finally:
        # Return connection to pool instead of closing it
        return_db_connection(conn)

    return {
        "success": True,
        "parent_cnpj": parent_cnpj,
        "domain": domain,
        "legal_name": legal_name,
        "trade_name": trade_name,
        "website": website,
        "error": None,
    }
