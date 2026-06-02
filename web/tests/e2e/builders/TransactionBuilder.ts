import {
  createTransaction,
  CreditDebitType,
  today,
  TransactionStatus,
  type CreateTransactionInput,
  type Transaction,
} from '../helpers/banking-graphql';

function uniqueDesc(prefix: string): string {
  return `${prefix} ${Date.now()}-${Math.random().toString(36).slice(2, 7)}`;
}

export class TransactionBuilder {
  private input: Partial<CreateTransactionInput> = {
    amount: 100,
    creditDebitType: CreditDebitType.Debit,
    status: TransactionStatus.Posted,
  };

  forAccount(id: string): this {
    this.input.financialAccountId = id;
    return this;
  }

  amount(v: number): this {
    this.input.amount = v;
    return this;
  }

  credit(): this {
    this.input.creditDebitType = CreditDebitType.Credit;
    return this;
  }

  debit(): this {
    this.input.creditDebitType = CreditDebitType.Debit;
    return this;
  }

  pending(): this {
    this.input.status = TransactionStatus.Pending;
    return this;
  }

  future(): this {
    this.input.status = TransactionStatus.Future;
    return this;
  }

  on(date: string): this {
    this.input.date = date;
    return this;
  }

  tagged(...tags: string[]): this {
    this.input.tags = tags;
    return this;
  }

  described(description: string): this {
    this.input.description = description;
    return this;
  }

  async create(token: string): Promise<Transaction> {
    if (!this.input.financialAccountId) {
      throw new Error('TransactionBuilder requires financialAccountId. Call forAccount(id).');
    }

    const input: CreateTransactionInput = {
      financialAccountId: this.input.financialAccountId,
      description: this.input.description ?? uniqueDesc('TX'),
      amount: this.input.amount ?? 100,
      creditDebitType: this.input.creditDebitType ?? CreditDebitType.Debit,
      status: this.input.status ?? TransactionStatus.Posted,
      date: this.input.date ?? today(),
      ...(this.input.tags ? { tags: this.input.tags } : {}),
    };

    return createTransaction(input, token);
  }
}
