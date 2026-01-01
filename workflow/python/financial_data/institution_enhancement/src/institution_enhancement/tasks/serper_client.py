"""
Serper API client for searching websites.

Provides functions to search for institution websites using the Serper Google Search API.
"""

import logging
import os
import re
from typing import Optional, List, Dict, Any

import requests

logger = logging.getLogger(__name__)


class SerperClient:
    """Client for Serper API to search for institution websites."""

    # Patterns to exclude (social media, general portals, etc.)
    EXCLUDE_PATTERNS = [
        "facebook.com",
        "instagram.com",
        "linkedin.com",
        "twitter.com",
        "x.com",
        "youtube.com",
        "wikipedia.org",
        "gov.br",
        "bcb.gov.br",
        "receita.fazenda.gov.br",
        "reclameaqui.com.br",
    ]

    def __init__(self, api_key: str, timeout: int = 10):
        """
        Initialize Serper client.

        Args:
            api_key: Serper API key
            timeout: Request timeout in seconds
        """
        self.api_key = api_key
        self.timeout = timeout
        self.base_url = "https://google.serper.dev/search"

    @classmethod
    def from_env(cls) -> Optional["SerperClient"]:
        """
        Create Serper client from environment variables.

        Returns:
            SerperClient instance if enabled, None otherwise
        """
        enabled = os.getenv("SERPER_ENABLED", "false").lower() in ("true", "1", "yes")
        if not enabled:
            logger.info("Serper API disabled (SERPER_ENABLED=false)")
            return None

        api_key = os.getenv("SERPER_API_KEY")
        if not api_key:
            logger.warning("Serper API enabled but SERPER_API_KEY not set")
            return None

        timeout = int(os.getenv("SERPER_TIMEOUT_SECONDS", "10"))
        return cls(api_key=api_key, timeout=timeout)

    def search_website(self, institution_name: str, cnpj: str = "") -> Optional[str]:
        """
        Search for an institution's website using Serper API.

        Args:
            institution_name: Name of the institution
            cnpj: CNPJ of the institution (optional)

        Returns:
            Website URL or None if not found
        """
        logger.info(
            f"Searching for website using Serper: institution_name={institution_name}, cnpj={cnpj}"
        )

        # Build search query
        query = f"{institution_name} site oficial"
        if cnpj:
            query = f"{institution_name} {cnpj} site oficial"

        try:
            # Make request
            response = requests.post(
                self.base_url,
                json={
                    "q": query,
                    "gl": "br",  # Search in Brazil
                    "hl": "pt",  # Portuguese language
                },
                headers={
                    "X-API-KEY": self.api_key,
                    "Content-Type": "application/json",
                },
                timeout=self.timeout,
            )

            if response.status_code != 200:
                logger.error(
                    f"Serper API returned error: status={response.status_code}, "
                    f"body={response.text[:200]}"
                )
                return None

            data = response.json()
            organic_results = data.get("organic", [])

            if not organic_results:
                logger.warning(
                    f"No search results found for institution_name={institution_name}"
                )
                return None

            # Extract website from results
            website = self._extract_website_from_results(
                organic_results, institution_name
            )

            if website:
                logger.info(
                    f"Website found using Serper: institution_name={institution_name}, "
                    f"website={website}"
                )
            else:
                logger.warning(
                    f"No website found using Serper: institution_name={institution_name}, "
                    f"results_count={len(organic_results)}"
                )

            return website

        except requests.exceptions.Timeout as e:
            logger.warning(f"Serper API timeout: {e}")
            return None
        except requests.exceptions.RequestException as e:
            logger.warning(f"Serper API request failed: {e}")
            return None
        except Exception as e:
            logger.warning(f"Unexpected error calling Serper API: {e}", exc_info=True)
            return None

    def _extract_website_from_results(
        self, results: List[Dict[str, Any]], institution_name: str
    ) -> Optional[str]:
        """
        Extract the most likely website from search results.

        Args:
            results: List of search results from Serper
            institution_name: Name of the institution for relevance scoring

        Returns:
            Website URL or None if not found
        """
        if not results:
            return None

        institution_name_lower = institution_name.lower()

        # Try to find official website in the first few results
        for i, result in enumerate(results[:5]):  # Only check first 5 results
            link = result.get("link", "")
            title = result.get("title", "")
            snippet = result.get("snippet", "")

            # Skip excluded domains
            if self._is_excluded_domain(link):
                logger.info(f"Skipping excluded domain: link={link}, position={i+1}")
                continue

            # Extract domain from link
            domain = self._extract_domain(link)
            if not domain:
                continue

            # Score the result based on relevance indicators
            score = self._score_result(
                title, snippet, institution_name_lower
            )

            logger.info(
                f"Evaluating search result: position={i+1}, link={link}, "
                f"title={title}, score={score}"
            )

            # If first result or has good score, use it
            if i == 0 or score >= 3:
                return self._normalize_url(domain)

        # Fallback: use first non-excluded result
        for result in results:
            link = result.get("link", "")
            if not self._is_excluded_domain(link):
                domain = self._extract_domain(link)
                if domain:
                    logger.info(f"Using fallback result: link={link}")
                    return self._normalize_url(domain)

        return None

    def _is_excluded_domain(self, url: str) -> bool:
        """Check if URL contains excluded patterns."""
        url_lower = url.lower()
        return any(pattern in url_lower for pattern in self.EXCLUDE_PATTERNS)

    def _extract_domain(self, url: str) -> str:
        """
        Extract domain from URL.

        Args:
            url: Full URL

        Returns:
            Domain without path and query
        """
        # Remove protocol
        url = re.sub(r"^https?://", "", url)

        # Remove path and query
        url = url.split("/")[0].split("?")[0]

        return url

    def _score_result(
        self, title: str, snippet: str, institution_name_lower: str
    ) -> int:
        """
        Score search result based on relevance indicators.

        Args:
            title: Result title
            snippet: Result snippet
            institution_name_lower: Institution name (lowercase)

        Returns:
            Relevance score (higher is better)
        """
        score = 0
        title_lower = title.lower()
        snippet_lower = snippet.lower()

        # Check if title contains institution name
        if institution_name_lower in title_lower:
            score += 3

        # Check if title mentions "oficial" or "official"
        if "oficial" in title_lower or "official" in title_lower:
            score += 2

        # Check if snippet contains institution name
        if institution_name_lower in snippet_lower:
            score += 1

        # Check if snippet mentions "oficial" or "official"
        if "oficial" in snippet_lower or "official" in snippet_lower:
            score += 1

        return score

    def _normalize_url(self, url: str) -> str:
        """
        Normalize URL to ensure it has https:// protocol.

        Args:
            url: URL string (may or may not have protocol)

        Returns:
            URL with https:// protocol
        """
        url = url.strip()
        if not url:
            return url

        if not url.startswith(("http://", "https://")):
            return f"https://{url}"

        return url
