#!/usr/bin/env python3
"""
PoC: CNPJá Social Network Discovery

Temporary script to discover social network information for institutions
by querying CNPJá API and scraping their websites.

This script:
1. Constructs matrix CNPJ from bacen_cod_inst (using construct_cnpj)
2. Queries CNPJá API for company information
3. Extracts domain from emails list
4. Scrapes the website homepage for social network links
5. Downloads logos from logo.dev API (saves to user's Downloads folder)
6. Implements intelligent rate limiting (15 seconds between CNPJá queries)

Usage:
    python scripts/poc_cnpja_social_discovery.py [--test-code CODE]

    --test-code CODE: Test with a specific institution code (e.g., '60872504')

Environment Variables:
    - POSTGRES_HOST (default: localhost)
    - POSTGRES_PORT (default: 6432)
    - POSTGRES_DB (default: neotool_db)
    - POSTGRES_USER (default: neotool)
    - POSTGRES_PASSWORD (default: neotool)
"""
import argparse
import os
import re
import sys
import time
from pathlib import Path
from urllib.parse import urljoin, urlparse

import psycopg2
import requests
from requests.adapters import HTTPAdapter
from urllib3.util.retry import Retry
from urllib3.exceptions import InsecureRequestWarning
from bs4 import BeautifulSoup

# Suppress SSL warnings when verify=False is used
requests.packages.urllib3.disable_warnings(InsecureRequestWarning)

# Add the parent directory to the path so we can import tasks if needed
script_dir = Path(__file__).parent
project_root = script_dir.parent
sys.path.insert(0, str(project_root))

# Constants
CNPJA_BASE_URL = "https://open.cnpja.com/office"
API_TIMEOUT = 30  # seconds
CNPJA_RATE_LIMIT_SECONDS = 15  # 15 seconds between queries (5 per minute = 12 sec, with margin)
SCRAPING_TIMEOUT = 10  # seconds for website scraping
MAX_RETRIES = 3  # Number of retries for connection attempts
MAX_REDIRECTS = 10  # Maximum redirects to follow
DEBUG = True  # Set to True to enable debug logging
LOGO_API_TOKEN = "pk_D6HFbYd8TI2ZD3BEBPx1gg"
LOGO_API_BASE_URL = "https://img.logo.dev"

# HTTP Status codes
HTTP_OK = 200
HTTP_REDIRECT_CODES = (301, 302, 303, 307, 308)
HTTP_FORBIDDEN = 403
HTTP_UNAUTHORIZED = 401
HTTP_NOT_FOUND = 404
HTTP_SERVER_ERROR_CODES = (500, 502, 503, 504)

# Rate limiting state
_last_cnpja_query_time = None


def debug_print(message: str) -> None:
    """Print debug message only if DEBUG is enabled."""
    if DEBUG:
        print(message)


def get_db_connection() -> psycopg2.extensions.connection:
    """
    Get PostgreSQL database connection using environment variables.

    Returns:
        psycopg2 connection object

    Raises:
        psycopg2.OperationalError: If connection fails
    """
    host = os.getenv("POSTGRES_HOST", "localhost")
    port = int(os.getenv("POSTGRES_PORT", "6432"))
    database = os.getenv("POSTGRES_DB", "neotool_db")
    user = os.getenv("POSTGRES_USER", "neotool")
    password = os.getenv("POSTGRES_PASSWORD", "neotool")

    return psycopg2.connect(
        host=host,
        port=port,
        database=database,
        user=user,
        password=password,
        connect_timeout=5,
    )


def calculate_cnpj_validator_digits(base_12_digits: str) -> tuple[int, int]:
    """
    Calculate the two validator digits for a CNPJ.

    Args:
        base_12_digits: First 12 digits of CNPJ (8 base + 4 branch)

    Returns:
        Tuple of (first_validator_digit, second_validator_digit)
    """
    if len(base_12_digits) != 12:
        raise ValueError(f"Expected 12 digits, got {len(base_12_digits)}")

    # First validator digit: weights [5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2]
    weights_1 = [5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2]
    sum_1 = sum(int(base_12_digits[i]) * weights_1[i] for i in range(12))
    mod_1 = sum_1 % 11
    digit_1 = 0 if mod_1 < 2 else 11 - mod_1

    # Second validator digit: weights [6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2]
    base_13 = base_12_digits + str(digit_1)
    weights_2 = [6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2]
    sum_2 = sum(int(base_13[i]) * weights_2[i] for i in range(13))
    mod_2 = sum_2 % 11
    digit_2 = 0 if mod_2 < 2 else 11 - mod_2

    return (digit_1, digit_2)


def construct_cnpj(bacen_cod_inst: str) -> str | None:
    """
    Construct a full CNPJ from bacen_cod_inst.

    Process:
    1. Extract digits only
    2. Pad to 8 digits with leading zeros
    3. Append "0001" (matrix branch code) → 12 digits
    4. Calculate 2 validator digits
    5. Return full 14-digit CNPJ

    Args:
        bacen_cod_inst: BACEN institution code

    Returns:
        Full 14-digit CNPJ string, or None if construction fails
    """
    digits = re.sub(r"\D", "", bacen_cod_inst)
    if not digits:
        return None

    base_8 = digits.zfill(8)[:8]  # Pad to 8 and take first 8
    base_12 = base_8 + "0001"

    try:
        digit_1, digit_2 = calculate_cnpj_validator_digits(base_12)
        return base_12 + str(digit_1) + str(digit_2)
    except (ValueError, IndexError) as e:
        print(f"Error constructing CNPJ for {bacen_cod_inst}: {e}")
        return None


def enforce_cnpja_rate_limit() -> None:
    """Enforce rate limiting for CNPJá API calls."""
    global _last_cnpja_query_time

    if _last_cnpja_query_time is not None:
        elapsed = time.time() - _last_cnpja_query_time
        if elapsed < CNPJA_RATE_LIMIT_SECONDS:
            wait_time = CNPJA_RATE_LIMIT_SECONDS - elapsed
            print(f"  ⏳ Rate limiting: waiting {wait_time:.1f}s before next CNPJá query...")
            time.sleep(wait_time)

    _last_cnpja_query_time = time.time()


def query_cnpja(cnpj: str) -> dict:
    """
    Query CNPJá API for company information.

    Args:
        cnpj: 14-digit CNPJ string

    Returns:
        Dictionary with found (bool|None), error (str|None), data (dict|None)
    """
    enforce_cnpja_rate_limit()

    url = f"{CNPJA_BASE_URL}/{cnpj}"
    headers = {
        "User-Agent": "Invistus PoC Script (https://github.com/your-org/invistus)",
        "Accept": "application/json",
    }

    try:
        response = requests.get(url, headers=headers, timeout=API_TIMEOUT)

        if response.status_code == HTTP_OK:
            return {"found": True, "error": None, "data": response.json()}
        elif response.status_code == HTTP_NOT_FOUND:
            return {"found": False, "error": None, "data": None}
        else:
            return {
                "found": None,
                "error": f"HTTP {response.status_code}: {response.text[:200]}",
                "data": None,
            }
    except requests.exceptions.Timeout:
        return {"found": None, "error": "Request timeout", "data": None}
    except requests.exceptions.RequestException as e:
        return {"found": None, "error": str(e), "data": None}


def extract_domain_from_emails(emails: list) -> str | None:
    """
    Extract domain from emails list.

    Args:
        emails: List of email dictionaries with 'domain' field

    Returns:
        Domain string, or None if no emails found
    """
    if not emails:
        return None

    domain = emails[0].get("domain")
    return domain.strip() if domain and domain.strip() else None


def create_http_session() -> requests.Session:
    """Create a requests session with retry strategy."""
    retry_strategy = Retry(
        total=MAX_RETRIES,
        backoff_factor=1,
        status_forcelist=list(HTTP_SERVER_ERROR_CODES),
        allowed_methods=["HEAD", "GET"],
    )
    adapter = HTTPAdapter(max_retries=retry_strategy)
    session = requests.Session()
    session.mount("http://", adapter)
    session.mount("https://", adapter)
    return session


def make_request_with_ssl_fallback(
    session: requests.Session,
    url: str,
    headers: dict,
    method: str = "get",
    allow_redirects: bool = False,
) -> requests.Response | None:
    """
    Make HTTP request with SSL verification, fallback to verify=False on SSL error.

    Args:
        session: requests.Session object
        url: URL to request
        headers: Request headers
        method: HTTP method ('get' or 'head')
        allow_redirects: Whether to follow redirects

    Returns:
        Response object or None if all attempts failed
    """
    request_func = session.get if method == "get" else session.head
    kwargs = {
        "headers": headers,
        "timeout": SCRAPING_TIMEOUT,
        "allow_redirects": allow_redirects,
    }
    if method == "get":
        kwargs["stream"] = True

    try:
        return request_func(url, **kwargs)
    except requests.exceptions.SSLError:
        debug_print(f"    [DEBUG] SSL error, retrying with verify=False")
        kwargs["verify"] = False
        try:
            return request_func(url, **kwargs)
        except requests.exceptions.RequestException:
            return None
    except requests.exceptions.RequestException:
        return None


def resolve_final_site(domain: str) -> str | None:
    """
    Resolve the final site URL by trying HTTPS/HTTP and following redirects.

    Algorithm:
    1. Try https://domain first
    2. If SSL error, try http://domain
    3. If successful, check for redirects and follow them in a loop
    4. Return final URL or None if both protocols fail

    Args:
        domain: Domain name without protocol

    Returns:
        Final URL after resolving protocol and redirects, or None if failed
    """
    if not domain:
        return None

    domain = domain.strip()
    debug_print(f"    [DEBUG resolve_final_site] Starting with domain: {domain}")

    headers = {
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
        "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
        "Accept-Language": "pt-BR,pt;q=0.9,en-US;q=0.8,en;q=0.7",
    }

    session = create_http_session()

    for protocol in ["https", "http"]:
        current_url = f"{protocol}://{domain}"
        debug_print(f"    [DEBUG resolve_final_site] Trying {protocol}: {current_url}")

        redirect_count = 0
        while redirect_count < MAX_REDIRECTS:
            # Try GET first, fallback to HEAD
            response = make_request_with_ssl_fallback(
                session, current_url, headers, method="get", allow_redirects=False
            )

            if not response:
                response = make_request_with_ssl_fallback(
                    session, current_url, headers, method="head", allow_redirects=False
                )

            if not response:
                break  # Try next protocol

            status = response.status_code
            debug_print(f"    [DEBUG resolve_final_site] Response status: {status}")

            # Handle redirects
            if status in HTTP_REDIRECT_CODES:
                location = response.headers.get("Location")
                if location:
                    current_url = urljoin(current_url, location)
                    redirect_count += 1
                    debug_print(
                        f"    [DEBUG resolve_final_site] Redirect #{redirect_count}: -> {current_url}"
                    )
                    continue
                return current_url

            # Success
            if status == HTTP_OK:
                debug_print(f"    [DEBUG resolve_final_site] Success, returning: {current_url}")
                return current_url

            # Forbidden/Unauthorized
            if status in (HTTP_FORBIDDEN, HTTP_UNAUTHORIZED):
                location = response.headers.get("Location")
                if location:
                    current_url = urljoin(current_url, location)
                    redirect_count += 1
                    continue

                # If HTTPS returned 403, try HTTP
                if protocol == "https":
                    debug_print(f"    [DEBUG resolve_final_site] HTTPS returned {status}, trying HTTP")
                    break

                # HTTP also returned 403 - try with allow_redirects
                debug_print(f"    [DEBUG resolve_final_site] Trying with allow_redirects=True")
                try:
                    final_response = session.get(
                        current_url,
                        headers=headers,
                        timeout=SCRAPING_TIMEOUT,
                        allow_redirects=True,
                        verify=False,
                    )
                    return final_response.url or current_url
                except Exception:
                    return current_url

            # Other status codes
            if protocol == "https":
                debug_print(f"    [DEBUG resolve_final_site] HTTPS failed with {status}, trying HTTP")
                break
            return None

        # If we got here with redirects, return the URL
        if redirect_count > 0:
            debug_print(f"    [DEBUG resolve_final_site] Completed with {redirect_count} redirects")
            return current_url

    debug_print(f"    [DEBUG resolve_final_site] Both protocols failed")
    return None


def scrape_social_networks(domain: str) -> tuple[dict, str | None]:
    """
    Scrape website homepage for social network links.

    Args:
        domain: Domain name

    Returns:
        Tuple of (social_networks dict, final_url)
    """
    social_networks = {
        "linkedin": None,
        "instagram": None,
        "facebook": None,
        "twitter": None,
        "x": None,
    }

    if not domain:
        return social_networks, None

    final_url = resolve_final_site(domain)
    if not final_url:
        return social_networks, None

    headers = {
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
        "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
        "Accept-Language": "pt-BR,pt;q=0.9,en-US;q=0.8,en;q=0.7",
    }

    session = create_http_session()

    try:
        response = session.get(
            final_url, headers=headers, timeout=SCRAPING_TIMEOUT, allow_redirects=True, verify=False
        )

        if response.status_code != HTTP_OK:
            return social_networks, final_url

        soup = BeautifulSoup(response.text, "html.parser")

        # Patterns for social networks
        patterns = {
            "linkedin": [r"linkedin\.com/company/", r"linkedin\.com/in/", r"linkedin\.com/"],
            "instagram": [r"instagram\.com/"],
            "facebook": [r"facebook\.com/", r"fb\.com/"],
            "twitter": [r"twitter\.com/"],
            "x": [r"x\.com/"],
        }

        # Check links
        for link in soup.find_all("a", href=True):
            href = link.get("href", "")
            if not href:
                continue

            full_url = urljoin(final_url, href)

            for network, network_patterns in patterns.items():
                if social_networks[network] is not None:
                    continue

                for pattern in network_patterns:
                    if re.search(pattern, full_url, re.IGNORECASE):
                        social_networks[network] = full_url
                        break

        # Check meta tags
        for meta in soup.find_all("meta", property=True):
            property_attr = meta.get("property", "").lower()
            content = meta.get("content", "")

            if "linkedin" in property_attr and content and not social_networks["linkedin"]:
                social_networks["linkedin"] = content
            elif "instagram" in property_attr and content and not social_networks["instagram"]:
                social_networks["instagram"] = content
            elif "facebook" in property_attr and content and not social_networks["facebook"]:
                social_networks["facebook"] = content
            elif "twitter" in property_attr and content and not social_networks["twitter"]:
                social_networks["twitter"] = content

    except Exception:
        pass  # Silently fail on errors

    return social_networks, final_url


def normalize_domain_from_url(url: str) -> str | None:
    """
    Normalize a URL to extract just the domain (remove protocol and path).

    Args:
        url: Full URL or domain string

    Returns:
        Normalized domain string (e.g., "bb.com.br"), or None if invalid
    """
    if not url:
        return None

    url = url.strip()

    # Remove protocol if present
    if url.startswith("http://"):
        url = url[7:]
    elif url.startswith("https://"):
        url = url[8:]

    # Remove path and query parameters
    if "/" in url:
        url = url.split("/")[0]
    if "?" in url:
        url = url.split("?")[0]
    if "#" in url:
        url = url.split("#")[0]

    # Remove port if present
    if ":" in url:
        url = url.split(":")[0]

    return url.strip() if url.strip() else None


def download_logo(domain: str, bacen_cod_inst: str) -> str | None:
    """
    Download logo from logo.dev API and save to user's Downloads folder.

    Args:
        domain: Domain name (normalized, e.g., "bb.com.br")
        bacen_cod_inst: BACEN institution code for filename

    Returns:
        Path to downloaded logo file, or None if download failed
    """
    if not domain:
        return None

    # Build logo API URL
    logo_url = (
        f"{LOGO_API_BASE_URL}/{domain}"
        f"?token={LOGO_API_TOKEN}&format=png&retina=true"
    )

    # Get user's Downloads folder
    downloads_folder = Path.home() / "Downloads"
    downloads_folder.mkdir(exist_ok=True)

    # Create filename: sanitize bacen_cod_inst for filesystem
    safe_code = re.sub(r'[^\w\-_\.]', '_', str(bacen_cod_inst))
    filename = f"{safe_code}_{domain.replace('.', '_')}.png"
    filepath = downloads_folder / filename

    try:
        response = requests.get(logo_url, timeout=SCRAPING_TIMEOUT, stream=True)

        if response.status_code == HTTP_OK:
            # Check if response is actually an image
            content_type = response.headers.get("Content-Type", "")
            if not content_type.startswith("image/"):
                debug_print(f"    [DEBUG] Logo API returned non-image: {content_type}")
                return None

            # Save the image
            with open(filepath, "wb") as f:
                for chunk in response.iter_content(chunk_size=8192):
                    f.write(chunk)

            return str(filepath)
        else:
            debug_print(f"    [DEBUG] Logo API returned status {response.status_code}")
            return None

    except requests.exceptions.RequestException as e:
        debug_print(f"    [DEBUG] Error downloading logo: {e}")
        return None


def main() -> None:
    """Main execution function."""
    print("CNPJá Social Network Discovery PoC")
    print("=" * 60)

    # Parse command line arguments
    parser = argparse.ArgumentParser(description="CNPJá Social Network Discovery PoC")
    parser.add_argument(
        "--test-code",
        type=str,
        help="Test with a specific institution code (e.g., '60872504')",
    )
    args = parser.parse_args()

    # Connect to database
    try:
        conn = get_db_connection()
        cur = conn.cursor()
    except psycopg2.OperationalError as e:
        print(f"❌ Error connecting to database: {e}")
        sys.exit(1)

    # Query institution codes
    try:
        if args.test_code:
            print(f"🧪 TEST MODE: Testing with code '{args.test_code}'")
            bacen_codes = [args.test_code]
        else:
            print("Querying database for distinct institution codes...")
            query = """
                SELECT DISTINCT 
                    CASE 
                        WHEN cnpj_instituicao_lider IS NOT NULL 
                             AND cnpj_instituicao_lider != '' 
                        THEN cnpj_instituicao_lider
                        WHEN bacen_cod_inst NOT LIKE 'C%' 
                        THEN bacen_cod_inst
                    END AS codigo_instituicao
                FROM financial_data.institution
                WHERE situacao = 'A'
                  AND (
                      (cnpj_instituicao_lider IS NOT NULL AND cnpj_instituicao_lider != '')
                      OR (bacen_cod_inst NOT LIKE 'C%')
                  )
                ORDER BY codigo_instituicao
            """
            cur.execute(query)
            rows = cur.fetchall()
            bacen_codes = [row[0] for row in rows if row[0] is not None]
            print(f"Found {len(bacen_codes)} distinct institution codes")
    except Exception as e:
        print(f"❌ Error querying database: {e}")
        cur.close()
        conn.close()
        sys.exit(1)
    finally:
        cur.close()
        conn.close()

    if not bacen_codes:
        print("No institution codes found. Exiting.")
        sys.exit(0)

    print(f"\nProcessing {len(bacen_codes)} codes...")
    print(f"(Rate limiting: {CNPJA_RATE_LIMIT_SECONDS}s between CNPJá queries)")
    print("-" * 60)

    # Statistics tracking
    stats = {
        "total_processed": 0,
        "successfully_constructed": 0,
        "found_in_cnpja": 0,
        "not_found_in_cnpja": 0,
        "cnpja_errors": 0,
        "with_domain": 0,
        "with_linkedin": 0,
        "with_instagram": 0,
        "with_facebook": 0,
        "with_twitter": 0,
        "with_x": 0,
        "with_any_social": 0,
        "logos_downloaded": 0,
    }

    results = []
    not_found_institutions = []

    # Process each institution code
    for i, bacen_cod_inst in enumerate(bacen_codes, 1):
        stats["total_processed"] += 1
        progress_pct = (i / len(bacen_codes)) * 100
        print(f"\n[{i}/{len(bacen_codes)} ({progress_pct:.1f}%)] Processing: {bacen_cod_inst}")

        # Construct CNPJ
        cnpj = construct_cnpj(bacen_cod_inst)
        if cnpj is None:
            stats["cnpja_errors"] += 1
            print(f"  ✗ Failed to construct CNPJ")
            results.append(
                {
                    "bacen_cod_inst": bacen_cod_inst,
                    "constructed_cnpj": None,
                    "found": None,
                    "error": "Failed to construct CNPJ",
                }
            )
            continue

        stats["successfully_constructed"] += 1
        print(f"  CNPJ: {cnpj}")

        # Query CNPJá API
        cnpja_result = query_cnpja(cnpj)

        if cnpja_result["found"] is True:
            stats["found_in_cnpja"] += 1
            cnpja_data = cnpja_result.get("data", {})
            emails = cnpja_data.get("emails", [])
            domain = extract_domain_from_emails(emails)

            social_networks = {
                "linkedin": None,
                "instagram": None,
                "facebook": None,
                "twitter": None,
                "x": None,
            }

            logo_path = None
            logo_domain_used = None

            if domain:
                stats["with_domain"] += 1
                print(f"  Domain: {domain}")

                social_networks, final_url = scrape_social_networks(domain)

                if final_url:
                    print(f"  Site accessed: {final_url}")
                else:
                    print(f"  ⚠ Could not resolve site (both HTTPS and HTTP failed)")

                social_count = sum(1 for v in social_networks.values() if v is not None)

                if social_count > 0:
                    stats["with_any_social"] += 1
                    print(f"  ✓ Found {social_count} social network(s):")

                    for network in ["linkedin", "instagram", "facebook", "twitter", "x"]:
                        if social_networks[network]:
                            stats[f"with_{network}"] += 1
                            print(f"    - {network.capitalize()}: {social_networks[network]}")
                else:
                    print(f"  ⚠ No social networks found on website")

                # Download logo - use final_url if available, otherwise fallback to domain
                logo_domain_used = normalize_domain_from_url(final_url) if final_url else domain
                if logo_domain_used:
                    logo_path = download_logo(logo_domain_used, bacen_cod_inst)
                    if logo_path:
                        stats["logos_downloaded"] += 1
                        print(f"  ✓ Logo downloaded: {logo_path}")
                    else:
                        print(f"  ⚠ Could not download logo for {logo_domain_used}")
            else:
                print(f"  ⚠ No domain found in emails")

            results.append(
                {
                    "bacen_cod_inst": bacen_cod_inst,
                    "constructed_cnpj": cnpj,
                    "found": True,
                    "domain": domain,
                    "social_networks": social_networks,
                    "logo_path": logo_path,
                    "logo_domain_used": logo_domain_used,
                    "error": None,
                }
            )
        elif cnpja_result["found"] is False:
            stats["not_found_in_cnpja"] += 1
            not_found_institutions.append(bacen_cod_inst)
            print(f"  ✗ Not found in CNPJá")
            results.append(
                {
                    "bacen_cod_inst": bacen_cod_inst,
                    "constructed_cnpj": cnpj,
                    "found": False,
                    "error": None,
                }
            )
        else:
            stats["cnpja_errors"] += 1
            error_msg = cnpja_result.get("error", "Unknown error")
            print(f"  ✗ CNPJá API Error: {error_msg}")
            results.append(
                {
                    "bacen_cod_inst": bacen_cod_inst,
                    "constructed_cnpj": cnpj,
                    "found": None,
                    "error": error_msg,
                }
            )

    # Calculate and print results
    if stats["found_in_cnpja"] > 0:
        domain_pct = (stats["with_domain"] / stats["found_in_cnpja"]) * 100
        linkedin_pct = (stats["with_linkedin"] / stats["found_in_cnpja"]) * 100
        instagram_pct = (stats["with_instagram"] / stats["found_in_cnpja"]) * 100
        facebook_pct = (stats["with_facebook"] / stats["found_in_cnpja"]) * 100
        twitter_pct = (stats["with_twitter"] / stats["found_in_cnpja"]) * 100
        x_pct = (stats["with_x"] / stats["found_in_cnpja"]) * 100
        any_social_pct = (stats["with_any_social"] / stats["found_in_cnpja"]) * 100
    else:
        domain_pct = linkedin_pct = instagram_pct = facebook_pct = twitter_pct = x_pct = any_social_pct = 0.0

    if stats["successfully_constructed"] > 0:
        found_rate = (stats["found_in_cnpja"] / stats["successfully_constructed"]) * 100
        not_found_rate = (stats["not_found_in_cnpja"] / stats["successfully_constructed"]) * 100
    else:
        found_rate = not_found_rate = 0.0

    # Print summary
    print("\n" + "=" * 60)
    print("CNPJá Social Network Discovery PoC Results")
    print("=" * 60)
    print(f"Total codes processed: {stats['total_processed']}")
    print(f"Successfully constructed CNPJs: {stats['successfully_constructed']}")
    print(f"CNPJs found in CNPJá: {stats['found_in_cnpja']}")
    print(f"CNPJs not found: {stats['not_found_in_cnpja']}")
    print(f"CNPJá API errors: {stats['cnpja_errors']}")
    print()
    print(f"Found rate: {found_rate:.2f}% ({stats['found_in_cnpja']}/{stats['successfully_constructed']})")
    print(f"Not found rate: {not_found_rate:.2f}% ({stats['not_found_in_cnpja']}/{stats['successfully_constructed']})")
    print()
    print("Social Network Coverage (of found institutions):")
    print(f"  With domain: {domain_pct:.2f}% ({stats['with_domain']}/{stats['found_in_cnpja']})")
    print(f"  LinkedIn: {linkedin_pct:.2f}% ({stats['with_linkedin']}/{stats['found_in_cnpja']})")
    print(f"  Instagram: {instagram_pct:.2f}% ({stats['with_instagram']}/{stats['found_in_cnpja']})")
    print(f"  Facebook: {facebook_pct:.2f}% ({stats['with_facebook']}/{stats['found_in_cnpja']})")
    print(f"  Twitter: {twitter_pct:.2f}% ({stats['with_twitter']}/{stats['found_in_cnpja']})")
    print(f"  X: {x_pct:.2f}% ({stats['with_x']}/{stats['found_in_cnpja']})")
    print()
    print(f"With any social network: {any_social_pct:.2f}% ({stats['with_any_social']}/{stats['found_in_cnpja']})")
    print()
    print("Logo Downloads:")
    if stats["found_in_cnpja"] > 0:
        logo_pct = (stats["logos_downloaded"] / stats["found_in_cnpja"]) * 100
        print(f"  Logos downloaded: {logo_pct:.2f}% ({stats['logos_downloaded']}/{stats['found_in_cnpja']})")
    else:
        print(f"  Logos downloaded: 0")
    print()

    if not_found_institutions:
        print(f"Institutions not found in CNPJá ({len(not_found_institutions)}):")
        for code in not_found_institutions[:20]:
            print(f"  - {code}")
        if len(not_found_institutions) > 20:
            print(f"  ... and {len(not_found_institutions) - 20} more")
    else:
        print("All institutions were found in CNPJá!")

    print("=" * 60)
    print("PoC completed successfully!")


if __name__ == "__main__":
    main()
