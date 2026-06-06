-- Allow BILL_PAYMENT as a transaction type (credit card bill payment from checking account).

ALTER TABLE banking.transactions
    DROP CONSTRAINT IF EXISTS transactions_type_check;

ALTER TABLE banking.transactions
    ADD CONSTRAINT transactions_type_check
    CHECK (type IN (
        'PIX', 'TED', 'DOC', 'BOLETO', 'TRANSFER',
        'BILL_PAYMENT',
        'SALARY', 'DEPOSIT', 'CASHBACK',
        'INVESTMENT_RETURN', 'OTHER_INCOME',
        'CARD', 'WITHDRAWAL', 'FEE', 'INTEREST',
        'LOAN_PAYMENT', 'TAX', 'OTHER_EXPENSE',
        'BALANCE_ADJUSTMENT'
    ));
