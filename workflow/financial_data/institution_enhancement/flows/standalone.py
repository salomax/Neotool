"""
Standalone Institution Enhancement CLI

Command-line script to process institution enhancement for a single CNPJ parent.
"""

import argparse
import logging
import sys
from datetime import datetime
from uuid import uuid4

from tasks.config import Config
from tasks.cnpj_utils import validate_cnpj_format
from tasks.db_connection import close_pool, initialize_pool
from tasks.message import InstitutionUpsertedMessage
from tasks.processor import process_message

# Configure logging
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s - %(name)s - %(levelname)s - %(message)s",
)
logger = logging.getLogger(__name__)


def create_minimal_message(cnpj_parent: str) -> InstitutionUpsertedMessage:
    """
    Create a minimal InstitutionUpsertedMessage with the provided CNPJ parent.
    
    Args:
        cnpj_parent: CNPJ parent string
        
    Returns:
        InstitutionUpsertedMessage instance
    """
    return InstitutionUpsertedMessage(
        schema_version="1.0",
        event_id=str(uuid4()),
        occurred_at=datetime.utcnow().isoformat() + "Z",
        source="standalone-cli",
        bacen_cod_inst="00000000",  # Placeholder, not used when cnpj_instituicao_lider is set
        change_type="UPDATED",
        cnpj_instituicao_lider=cnpj_parent,
    )


def main() -> int:
    """
    Main entry point for standalone CLI.
    
    Returns:
        Exit code (0 for success, 1 for failure)
    """
    parser = argparse.ArgumentParser(
        description="Process institution enhancement for a CNPJ parent"
    )
    parser.add_argument(
        "cnpj_parent",
        type=str,
        help="CNPJ parent (14 digits)",
    )
    
    args = parser.parse_args()
    cnpj_parent = args.cnpj_parent.strip()
    
    # Validate CNPJ format
    if not validate_cnpj_format(cnpj_parent):
        print(f"Error: Invalid CNPJ format: {cnpj_parent}")
        print("CNPJ must be exactly 14 digits (numbers only)")
        return 1
    
    # Initialize database connection pool
    try:
        config = Config.from_env()
        initialize_pool(config.database)
        logger.info(f"Database connection pool initialized")
    except Exception as e:
        print(f"Error: Failed to initialize database connection pool: {e}")
        logger.error(f"Database initialization error: {e}", exc_info=True)
        return 1
    
    # Create message and process
    try:
        message = create_minimal_message(cnpj_parent)
        logger.info(f"Processing CNPJ: {cnpj_parent}")
        
        result = process_message(message)
        
        if result["success"]:
            print(f"Successfully processed CNPJ: {cnpj_parent}")
            if result.get("trade_name"):
                print(f"  Trade Name: {result['trade_name']}")
            if result.get("legal_name"):
                print(f"  Legal Name: {result['legal_name']}")
            if result.get("website"):
                print(f"  Website: {result['website']}")
            if result.get("domain"):
                print(f"  Domain: {result['domain']}")
            return 0
        else:
            error_msg = result.get("error", "Unknown error")
            print(f"Error processing CNPJ: {cnpj_parent} - {error_msg}")
            return 1
            
    except ValueError as e:
        print(f"Error: {e}")
        logger.error(f"Validation error: {e}", exc_info=True)
        return 1
    except Exception as e:
        print(f"Error processing CNPJ: {cnpj_parent} - {e}")
        logger.error(f"Processing error: {e}", exc_info=True)
        return 1
    finally:
        # Cleanup database connection pool
        try:
            close_pool()
            logger.info("Database connection pool closed")
        except Exception as e:
            logger.warning(f"Error closing connection pool: {e}")


if __name__ == "__main__":
    sys.exit(main())

