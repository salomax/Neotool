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

from tasks.cnpj_utils import construct_cnpj, validate_cnpj_format
from tasks.cnpja_client import query_cnpja
from tasks.db_connection import (
    get_db_connection,
    return_db_connection,
)
from tasks.message import InstitutionUpsertedMessage

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

# Default User-Agent for HTTP requests
DEFAULT_USER_AGENT = (
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
    "AppleWebKit/537.36 (KHTML, like Gecko) "
    "Chrome/91.0.4472.124 Safari/537.36"
)

# Field names that might contain website URL in API response
WEBSITE_FIELD_NAMES = ["website", "site", "url", "homepage", "web"]


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


def extract_website_from_receita_data(receita_data: Dict[str, Any], domain: Optional[str]) -> Optional[str]:
    """
    Extract website URL from CNPJá API response or construct from domain.

    Args:
        receita_data: CNPJá API response data
        domain: Extracted domain from emails

    Returns:
        Website URL string, or None if not available
    """
    if not receita_data:
        return None

    # Special rules: keywords mapped to their websites (checked in priority order)
    SPECIAL_WEBSITES = {
        "sicoob": "https://www.sicoob.com.br",
        "cresol": "https://cresol.com.br",
        "sicredi": "https://sicredi.com.br",
        "unicred": "https://www.unicred.com.br/",
    }

    alias = receita_data.get("alias", "") or ""
    company_name = receita_data.get("company", {}).get("name", "") or ""
    
    # Check special rules: if keyword is found in alias or company_name, return corresponding website
    alias_lower = alias.lower()
    company_name_lower = company_name.lower()
    
    for keyword, website in SPECIAL_WEBSITES.items():
        if keyword in alias_lower or keyword in company_name_lower:
            return website

    # Try to get website directly from API response
    # Check common field names that might contain website
    website = next(
        (receita_data.get(field) for field in WEBSITE_FIELD_NAMES if receita_data.get(field)),
        None
    )
    if website:
        website = website.strip()
        if website:
            return _normalize_url(website)

    # Fallback: construct from domain
    if domain:
        return f"https://{domain}"

    # Fallback: if domain is null but trade_name (alias) is not null, construct from trade_name
    if not domain and alias:
        trade_name_normalized = alias.lower().strip().replace(" ", "")
        if trade_name_normalized:
            return f"https://{trade_name_normalized}.com.br"

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


def validate_website_reachable(website: Optional[str]) -> bool:
    """
    Validate that a website URL is reachable.
    
    Uses cloudscraper to handle Cloudflare protection and other anti-bot measures.
    
    Args:
        website: Website URL to validate
        
    Returns:
        True if website is reachable (returns 2xx or 3xx status), False otherwise
    """
    if not website:
        return False
    
    try:
        # Use cloudscraper to bypass Cloudflare protection
        scraper = cloudscraper.create_scraper(
            browser={
                'browser': 'chrome',
                'platform': 'windows',
                'desktop': True
            }
        )
        
        headers = {
            "User-Agent": DEFAULT_USER_AGENT,
            "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
            "Accept-Language": "en-US,en;q=0.9,pt-BR;q=0.8",
        }
        
        # Use HEAD request first (faster, less bandwidth)
        # If HEAD is not supported, fall back to GET
        try:
            response = scraper.head(website, headers=headers, timeout=10, allow_redirects=True)
            # If HEAD returns 405 (Method Not Allowed), try GET
            if response.status_code == 405:
                response = scraper.get(website, headers=headers, timeout=10, allow_redirects=True)
        except requests.exceptions.RequestException:
            # If HEAD fails, try GET
            response = scraper.get(website, headers=headers, timeout=10, allow_redirects=True)
        
        # Consider 2xx and 3xx as reachable
        # Also check if we got a Cloudflare challenge page (403 with specific content)
        status_code = response.status_code
        is_reachable = 200 <= status_code < 400
        
        # If we got 403, check if it's a Cloudflare challenge page
        if status_code == 403:
            content = response.text.lower()
            # Cloudflare challenge pages often contain these indicators
            if 'cloudflare' in content or 'checking your browser' in content:
                logger.warning(
                    f"Cloudflare challenge detected for {website}, "
                    f"but cloudscraper should handle it. Status: {status_code}"
                )
                # Try one more time with GET to see if cloudscraper can solve it
                try:
                    response = scraper.get(website, headers=headers, timeout=15, allow_redirects=True)
                    is_reachable = 200 <= response.status_code < 400
                except Exception:
                    pass
        
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
        
        # Add timeout and proper headers to avoid being blocked
        headers = {
            "User-Agent": DEFAULT_USER_AGENT,
            "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
            "Accept-Language": "en-US,en;q=0.9,pt-BR;q=0.8",
        }

        response = scraper.get(website, headers=headers, timeout=15, allow_redirects=True)
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


def process_message(message: InstitutionUpsertedMessage) -> Dict[str, Any]:
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

        # Extract website from CNPJá API response or construct from domain
        website = extract_website_from_receita_data(receita_data, domain)
        
        # Extract legal name and trade name
        legal_name = extract_legal_name(receita_data)
        trade_name = extract_trade_name(receita_data)

        # Validate website is reachable before saving
        if website:
            if not validate_website_reachable(website):
                logger.warning(
                    f"Website not reachable for {parent_cnpj}: {website}, "
                    f"not saving website to database"
                )
                website = None  # Don't save unreachable websites
            else:
                # Website is reachable, scrape social networks
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
                INSERT INTO financial_data.institution_enhancement (
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
        "error": None,
    }
