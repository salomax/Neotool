import { create } from "zustand";

type PendingActionsDrawerState = {
  open: boolean;
  openDrawer: () => void;
  closeDrawer: () => void;
};

export const usePendingActionsDrawer = create<PendingActionsDrawerState>((set) => ({
  open: false,
  openDrawer: () => set({ open: true }),
  closeDrawer: () => set({ open: false }),
}));
