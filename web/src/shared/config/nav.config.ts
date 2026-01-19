import type { ReactNode } from "react";

export type NavItem = {
  label: string;
  href: string;
  icon?: ReactNode;
  perm?: string; // future RBAC hook
};

export const NAV: NavItem[] = [
  { label: "Users", href: "/users" },
  { label: "Profile", href: "/profile" },
];
