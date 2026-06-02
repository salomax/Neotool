from __future__ import annotations

from cleaner import clean_extracted_text


def test_cleaner_removes_boilerplate_and_normalizes_whitespace() -> None:
    raw = """
Central de atendimento 0800
| Data |   Descricao    | Valor |
| 15/01 | MERCADO   EXTRA | 45,90 |


Texto   comum   com   espacos
""".strip()

    cleaned = clean_extracted_text(raw)

    assert "Central de atendimento" not in cleaned
    assert "| Data | Descricao | Valor |" in cleaned
    assert "Texto comum com espacos" in cleaned


def test_cleaner_caps_text_without_trimming_table_rows() -> None:
    table = "\n".join(
        [
            "| Data | Descricao | Valor |",
            "| --- | --- | --- |",
            "| 15/01 | MERCADO EXTRA | 45,90 |",
        ]
    )
    long_tail = "\n".join(["linha sem tabela"] * 5000)
    raw = table + "\n" + long_tail

    cleaned = clean_extracted_text(raw, max_chars=800)

    assert "| 15/01 | MERCADO EXTRA | 45,90 |" in cleaned
    assert len(cleaned) <= 800


def test_cleaner_preserves_transaction_amounts() -> None:
    raw = "\n".join(
        [
            "| Data | Descricao | Valor |",
            "| --- | --- | --- |",
            "| 15/01 | MERCADO EXTRA | 45,90 |",
            "| 16/01 | IFOOD | 123,45 |",
        ]
    )

    cleaned = clean_extracted_text(raw)

    assert "45,90" in raw and "45,90" in cleaned
    assert "123,45" in raw and "123,45" in cleaned


def test_cleaner_is_idempotent() -> None:
    raw = """
Central de atendimento 0800
| Data |  Descricao | Valor |
| 15/01 |  MERCADO EXTRA  | 45,90 |
""".strip()

    once = clean_extracted_text(raw)
    twice = clean_extracted_text(once)

    assert twice == once
