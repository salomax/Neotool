"""
CNPJ Construction Utilities

Utilities for constructing CNPJ from BACEN institution codes.
"""

import re
from typing import Optional, Tuple


def validate_cnpj_format(cnpj: str) -> bool:
    """
    Validate CNPJ format (must be exactly 14 digits).

    Args:
        cnpj: CNPJ string to validate

    Returns:
        True if CNPJ format is valid (14 digits), False otherwise
    """
    if not cnpj:
        return False

    # Extract only digits
    digits = re.sub(r"\D", "", cnpj)

    # Must be exactly 14 digits
    return len(digits) == 14 and digits.isdigit()


def calculate_cnpj_validator_digits(base_12_digits: str) -> Tuple[int, int]:
    """
    Calculate the two validator digits for a CNPJ.

    Args:
        base_12_digits: First 12 digits of CNPJ (8 base + 4 branch)

    Returns:
        Tuple of (first_validator_digit, second_validator_digit)

    Raises:
        ValueError: If base_12_digits is not exactly 12 digits
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


def construct_cnpj(bacen_cod_inst: str) -> Optional[str]:
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
        return None

