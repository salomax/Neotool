"use client";

import { PendingActionsDrawer as InvestmentsPendingActionsDrawer } from "@/app/personal-finance/investments/PendingActionsDrawer";
import { usePendingActionsDrawer } from "@/shared/store/pendingActionsDrawer";

export function PendingActionsDrawer() {
  const open = usePendingActionsDrawer((state) => state.open);
  const closeDrawer = usePendingActionsDrawer((state) => state.closeDrawer);

  return <InvestmentsPendingActionsDrawer open={open} onClose={closeDrawer} />;
}
