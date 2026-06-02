import { useCallback, useState } from 'react';
import { CategorySource } from '@/lib/graphql/types/__generated__/graphql';
import { txSelectionKey } from '../billImportUtils';
import type { ReviewCard, TxCategorySelections, TxSelections } from '../billImportTypes';

export function useTxReviewState() {
  const [txSelections, setTxSelections] = useState<TxSelections>({});
  const [txCategorySelections, setTxCategorySelections] = useState<TxCategorySelections>({});

  const initFromReviewCards = useCallback((cards: ReviewCard[], isFlatMode: boolean) => {
    const selections: TxSelections = {};
    const categorySelections: TxCategorySelections = {};

    if (isFlatMode) {
      cards[0]?.transactions.forEach((tx, txIndex) => {
        selections[txSelectionKey(cards[0]!.reviewCardId, txIndex)] = true;
        categorySelections[`flat-${txIndex}`] = {
          categoryId: tx.categoryId ?? null,
          categorySource: tx.categorySource ?? null,
        };
      });
    } else {
      cards.forEach((card) => {
        card.transactions.forEach((tx, ti) => {
          const key = txSelectionKey(card.reviewCardId, ti);
          selections[key] = true;
          categorySelections[key] = {
            categoryId: tx.categoryId ?? null,
            categorySource: tx.categorySource ?? null,
          };
        });
      });
    }

    setTxSelections(selections);
    setTxCategorySelections(categorySelections);
  }, []);

  const updateCategory = useCallback((key: string, categoryId: string | null) => {
    setTxCategorySelections((prev) => ({
      ...prev,
      [key]: {
        categoryId,
        categorySource: categoryId ? CategorySource.Manual : null,
      },
    }));
  }, []);

  const reset = useCallback(() => {
    setTxSelections({});
    setTxCategorySelections({});
  }, []);

  return {
    txSelections,
    setTxSelections,
    txCategorySelections,
    updateCategory,
    initFromReviewCards,
    reset,
  };
}
