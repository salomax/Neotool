#!/usr/bin/env python3
"""
PoC: Wikidata Social Network Discovery

Temporary script to discover social network information for institutions
in financial_data.institution by querying Wikidata.

This script:
1. Queries distinct nome_instituicao values from active institutions
2. For each institution name, queries Wikidata using SPARQL
3. Extracts social network information (website, LinkedIn, Instagram, Facebook, Twitter, Wikipedia)
4. Reports success/failure percentages and lists institutions with/without social networks

Usage:
    python scripts/poc_wikidata_social_discovery.py

Environment Variables:
    - POSTGRES_HOST (default: localhost)
    - POSTGRES_PORT (default: 6432)
    - POSTGRES_DB (default: neotool_db)
    - POSTGRES_USER (default: neotool)
    - POSTGRES_PASSWORD (default: neotool)
"""
import os
import sys
import time
from pathlib import Path
from urllib.parse import quote

import psycopg2
import requests

# Add the parent directory to the path so we can import tasks if needed
script_dir = Path(__file__).parent
project_root = script_dir.parent
sys.path.insert(0, str(project_root))

# Wikidata SPARQL endpoint
WIKIDATA_SPARQL_URL = "https://query.wikidata.org/sparql"
API_TIMEOUT = 30  # seconds
REQUEST_DELAY = 1  # seconds between requests


def get_db_connection():
    """
    Get PostgreSQL database connection using environment variables.

    Environment variables:
        POSTGRES_HOST: Database host (default: localhost)
        POSTGRES_PORT: Database port (default: 6432 for pgbouncer)
        POSTGRES_DB: Database name (default: neotool_db)
        POSTGRES_USER: Database user (default: neotool)
        POSTGRES_PASSWORD: Database password (default: neotool)

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


def query_wikidata(institution_name: str) -> dict:
    """
    Query Wikidata for social network information about an institution.

    Args:
        institution_name: Name of the institution to search for

    Returns:
        Dictionary with:
            - found: bool (True if institution found, False if not found, None if error)
            - error: str | None (error message if any)
            - data: dict | None (extracted social network data if found)
    """
    # Build SPARQL query
    # Search for items with the institution name in Portuguese label
    # Extract social network properties
    sparql_query = f"""
    SELECT ?item ?itemLabel ?website ?linkedin ?instagram ?facebook ?twitter ?wikipedia
    WHERE {{
      ?item rdfs:label "{institution_name}"@pt.

      OPTIONAL {{ ?item wdt:P856 ?website. }}
      OPTIONAL {{ ?item wdt:P4264 ?linkedin. }}
      OPTIONAL {{ ?item wdt:P2003 ?instagram. }}
      OPTIONAL {{ ?item wdt:P2013 ?facebook. }}
      OPTIONAL {{ ?item wdt:P2002 ?twitter. }}

      OPTIONAL {{
        ?wikipedia schema:about ?item ;
                   schema:isPartOf <https://en.wikipedia.org/> .
      }}

      SERVICE wikibase:label {{ bd:serviceParam wikibase:language "pt,en". }}
    }}
    LIMIT 1
    """

    headers = {
        "User-Agent": "Invistus PoC Script (https://github.com/your-org/invistus)",
        "Accept": "application/sparql-results+json",
    }

    try:
        response = requests.get(
            WIKIDATA_SPARQL_URL,
            params={"query": sparql_query, "format": "json"},
            headers=headers,
            timeout=API_TIMEOUT,
        )

        # Add delay after each API call for backpressure
        time.sleep(REQUEST_DELAY)

        if response.status_code == 200:
            data = response.json()
            results = data.get("results", {}).get("bindings", [])

            if not results:
                return {
                    "found": False,
                    "error": None,
                    "data": None,
                }

            # Extract the first result
            result = results[0]

            # Extract social network data
            social_data = {
                "item": result.get("item", {}).get("value"),
                "itemLabel": result.get("itemLabel", {}).get("value"),
                "website": result.get("website", {}).get("value") if "website" in result else None,
                "linkedin": result.get("linkedin", {}).get("value") if "linkedin" in result else None,
                "instagram": result.get("instagram", {}).get("value") if "instagram" in result else None,
                "facebook": result.get("facebook", {}).get("value") if "facebook" in result else None,
                "twitter": result.get("twitter", {}).get("value") if "twitter" in result else None,
                "wikipedia": result.get("wikipedia", {}).get("value") if "wikipedia" in result else None,
            }

            return {
                "found": True,
                "error": None,
                "data": social_data,
            }
        else:
            return {
                "found": None,
                "error": f"HTTP {response.status_code}: {response.text[:200]}",
                "data": None,
            }
    except requests.exceptions.Timeout:
        # Sleep even on timeout to maintain backpressure
        time.sleep(REQUEST_DELAY)
        return {
            "found": None,
            "error": "Request timeout",
            "data": None,
        }
    except requests.exceptions.RequestException as e:
        # Still sleep even on error to maintain backpressure
        time.sleep(REQUEST_DELAY)
        return {
            "found": None,
            "error": str(e),
            "data": None,
        }


def count_social_networks(social_data: dict) -> int:
    """
    Count how many social networks are present in the data.

    Args:
        social_data: Dictionary with social network fields

    Returns:
        Number of non-null social network fields
    """
    if not social_data:
        return 0

    networks = [
        social_data.get("website"),
        social_data.get("linkedin"),
        social_data.get("instagram"),
        social_data.get("facebook"),
        social_data.get("twitter"),
        social_data.get("wikipedia"),
    ]

    return sum(1 for net in networks if net is not None)


def main():
    """Main execution function."""
    print("Wikidata Social Network Discovery PoC")
    print("=" * 60)
    print("Querying database for distinct institution names...")

    # Connect to database
    try:
        conn = get_db_connection()
        cur = conn.cursor()
    except psycopg2.OperationalError as e:
        print(f"❌ Error connecting to database: {e}")
        sys.exit(1)

    # Query distinct institution names from active institutions
    try:
        query = """
            SELECT DISTINCT nome_instituicao
            FROM financial_data.institution
            WHERE nome_instituicao IS NOT NULL
              AND nome_instituicao != ''
              AND situacao = 'A'
              AND bacen_cod_inst NOT LIKE 'C%'
            ORDER BY nome_instituicao
        """
        cur.execute(query)
        rows = cur.fetchall()
        institution_names = [row[0] for row in rows]
        print(f"Found {len(institution_names)} distinct institution names")
    except Exception as e:
        print(f"❌ Error querying database: {e}")
        cur.close()
        conn.close()
        sys.exit(1)
    finally:
        cur.close()
        conn.close()

    if not institution_names:
        print("No institution names found. Exiting.")
        sys.exit(0)

    print(f"\nProcessing {len(institution_names)} institutions...")
    print("(This may take a while due to 1 second delay between API calls)")
    print("-" * 60)

    # Statistics tracking
    stats = {
        "total_processed": 0,
        "found": 0,
        "not_found": 0,
        "errors": 0,
        "with_website": 0,
        "with_linkedin": 0,
        "with_instagram": 0,
        "with_facebook": 0,
        "with_twitter": 0,
        "with_wikipedia": 0,
        "with_any_social": 0,
        "with_multiple_social": 0,
    }

    # Track results
    results = []
    not_found_institutions = []
    institutions_with_social = []

    # Process each institution name
    for i, institution_name in enumerate(institution_names, 1):
        stats["total_processed"] += 1

        # Show progress and institution name for each request
        progress_pct = (i / len(institution_names)) * 100
        print(f"[{i}/{len(institution_names)} ({progress_pct:.1f}%)] Processing: {institution_name}")

        # Query Wikidata
        wikidata_result = query_wikidata(institution_name)

        if wikidata_result["found"] is True:
            stats["found"] += 1
            social_data = wikidata_result.get("data", {})

            # Count social networks
            social_count = count_social_networks(social_data)

            # Update statistics for each social network
            social_networks_found = []
            if social_data.get("website"):
                stats["with_website"] += 1
                social_networks_found.append("Website")
            if social_data.get("linkedin"):
                stats["with_linkedin"] += 1
                social_networks_found.append("LinkedIn")
            if social_data.get("instagram"):
                stats["with_instagram"] += 1
                social_networks_found.append("Instagram")
            if social_data.get("facebook"):
                stats["with_facebook"] += 1
                social_networks_found.append("Facebook")
            if social_data.get("twitter"):
                stats["with_twitter"] += 1
                social_networks_found.append("Twitter")
            if social_data.get("wikipedia"):
                stats["with_wikipedia"] += 1
                social_networks_found.append("Wikipedia")

            if social_count > 0:
                stats["with_any_social"] += 1
                institutions_with_social.append(institution_name)

            if social_count > 1:
                stats["with_multiple_social"] += 1

            # Log the information
            print(f"  ✓ Found in Wikidata: {social_data.get('itemLabel', 'N/A')}")
            if social_count > 0:
                print(f"  ✓ Social Networks ({social_count}): {', '.join(social_networks_found)}")
            else:
                print(f"  ⚠ No social networks found")
            print()  # Empty line for readability

            results.append(
                {
                    "institution_name": institution_name,
                    "found": True,
                    "error": None,
                    "social_data": social_data,
                    "social_count": social_count,
                }
            )
        elif wikidata_result["found"] is False:
            stats["not_found"] += 1
            not_found_institutions.append(institution_name)
            print(f"  ✗ Not found in Wikidata")
            print()  # Empty line for readability
            results.append(
                {
                    "institution_name": institution_name,
                    "found": False,
                    "error": None,
                    "social_data": None,
                    "social_count": 0,
                }
            )
        else:
            # Error case
            stats["errors"] += 1
            error_msg = wikidata_result.get("error", "Unknown error")
            print(f"  ✗ Error: {error_msg}")
            print()  # Empty line for readability
            results.append(
                {
                    "institution_name": institution_name,
                    "found": None,
                    "error": error_msg,
                    "social_data": None,
                    "social_count": 0,
                }
            )

    # Calculate percentages
    if stats["found"] > 0:
        website_percentage = (stats["with_website"] / stats["found"]) * 100
        linkedin_percentage = (stats["with_linkedin"] / stats["found"]) * 100
        instagram_percentage = (stats["with_instagram"] / stats["found"]) * 100
        facebook_percentage = (stats["with_facebook"] / stats["found"]) * 100
        twitter_percentage = (stats["with_twitter"] / stats["found"]) * 100
        wikipedia_percentage = (stats["with_wikipedia"] / stats["found"]) * 100
        any_social_percentage = (stats["with_any_social"] / stats["found"]) * 100
        multiple_social_percentage = (stats["with_multiple_social"] / stats["found"]) * 100
    else:
        website_percentage = 0.0
        linkedin_percentage = 0.0
        instagram_percentage = 0.0
        facebook_percentage = 0.0
        twitter_percentage = 0.0
        wikipedia_percentage = 0.0
        any_social_percentage = 0.0
        multiple_social_percentage = 0.0

    if stats["total_processed"] > 0:
        found_rate = (stats["found"] / stats["total_processed"]) * 100
        not_found_rate = (stats["not_found"] / stats["total_processed"]) * 100
    else:
        found_rate = 0.0
        not_found_rate = 0.0

    # Print results
    print("\n" + "=" * 60)
    print("Wikidata Social Network Discovery PoC Results")
    print("=" * 60)
    print(f"Total institutions processed: {stats['total_processed']}")
    print(f"Institutions found in Wikidata: {stats['found']}")
    print(f"Institutions not found: {stats['not_found']}")
    print(f"Errors: {stats['errors']}")
    print()
    print(f"Found rate: {found_rate:.2f}% ({stats['found']}/{stats['total_processed']})")
    print(f"Not found rate: {not_found_rate:.2f}% ({stats['not_found']}/{stats['total_processed']})")
    print()
    print("Social Network Coverage (of found institutions):")
    print(f"  Website: {website_percentage:.2f}% ({stats['with_website']}/{stats['found']})")
    print(f"  LinkedIn: {linkedin_percentage:.2f}% ({stats['with_linkedin']}/{stats['found']})")
    print(f"  Instagram: {instagram_percentage:.2f}% ({stats['with_instagram']}/{stats['found']})")
    print(f"  Facebook: {facebook_percentage:.2f}% ({stats['with_facebook']}/{stats['found']})")
    print(f"  Twitter: {twitter_percentage:.2f}% ({stats['with_twitter']}/{stats['found']})")
    print(f"  Wikipedia: {wikipedia_percentage:.2f}% ({stats['with_wikipedia']}/{stats['found']})")
    print()
    print(f"With any social network: {any_social_percentage:.2f}% ({stats['with_any_social']}/{stats['found']})")
    print(f"With multiple social networks: {multiple_social_percentage:.2f}% ({stats['with_multiple_social']}/{stats['found']})")
    print()

    # List institutions not found
    if not_found_institutions:
        print(f"Institutions not found in Wikidata ({len(not_found_institutions)}):")
        for name in not_found_institutions[:20]:  # Show first 20
            print(f"  - {name}")
        if len(not_found_institutions) > 20:
            print(f"  ... and {len(not_found_institutions) - 20} more")
    else:
        print("All institutions were found in Wikidata!")

    print()
    if institutions_with_social:
        print(f"Institutions with social networks ({len(institutions_with_social)}):")
        for name in institutions_with_social[:20]:  # Show first 20
            print(f"  - {name}")
        if len(institutions_with_social) > 20:
            print(f"  ... and {len(institutions_with_social) - 20} more")

    print("=" * 60)
    print("PoC completed successfully!")


if __name__ == "__main__":
    main()

