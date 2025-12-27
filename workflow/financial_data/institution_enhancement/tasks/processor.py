"""
Institution Enhancement Processor

Business logic for processing institution enhancement messages.
"""

import logging
import time
from typing import Any, Dict, List, Optional

import psycopg2
from psycopg2.extras import Json

from tasks.cnpj_utils import construct_cnpj, validate_cnpj_format
from tasks.cnpja_client import query_cnpja
from tasks.db_connection import (
    get_db_connection,
    return_db_connection,
)
from tasks.message import InstitutionUpsertedMessage

logger = logging.getLogger(__name__)


def extract_domain_from_emails(emails: List[Dict[str, Any]]) -> Optional[str]:
    """
    Extract domain from emails list in CNPJá response.

    Args:
        emails: List of email dictionaries with 'domain' field

    Returns:
        Domain string, or None if no emails found
    """
    if not emails:
        return None

    domain = emails[0].get("domain")
    return domain.strip() if domain and domain.strip() else None


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
    4. Stores enhancement data in database

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

    if cnpja_result["found"] and cnpja_result["data"]:
        receita_data = cnpja_result["data"]
        emails = receita_data.get("emails", [])
        domain = extract_domain_from_emails(emails)

    # TODO: Implement logo download from asset management service
    # TODO: Extract website URL from CNPJá API response or resolve from domain
    # TODO: Scrape social network links from website

    # Store enhancement data in database
    start_time = time.time()
    conn = get_db_connection()
    try:
        with conn.cursor() as cur:
            cur.execute(
                """
                INSERT INTO financial_data.institution_enhancement (
                    parent_cnpj, domain, website, logo_storage_key,
                    social_network, receita_data, created_at, updated_at
                ) VALUES (
                    %s, %s, %s, %s, %s, %s, NOW(), NOW()
                )
                ON CONFLICT (parent_cnpj) DO UPDATE SET
                    domain = EXCLUDED.domain,
                    website = EXCLUDED.website,
                    logo_storage_key = EXCLUDED.logo_storage_key,
                    social_network = EXCLUDED.social_network,
                    receita_data = EXCLUDED.receita_data,
                    updated_at = NOW()
                """,
                (
                    parent_cnpj,
                    domain,
                    None,  # website (TODO: extract from CNPJá API or resolve from domain)
                    None,  # logo_storage_key (TODO: download from asset management service)
                    None,  # social_network (TODO: scrape from website)
                    Json(receita_data) if receita_data else None,
                ),
            )
        conn.commit()
        processing_time = time.time() - start_time
        logger.info(
            f"Successfully stored enhancement data: parent_cnpj={parent_cnpj}, "
            f"domain={domain}, processing_time={processing_time:.2f}s"
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

