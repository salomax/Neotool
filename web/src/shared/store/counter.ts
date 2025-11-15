import { create } from "zustand";

type State = { count: number };
type Actions = { increment: () => void };

export const useCounter = create<State & Actions>((set) => ({
  count: 0,
  increment: () => set((s) => ({ count: s.count + 1 })),
}));
