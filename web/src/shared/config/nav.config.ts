import type { ReactNode } from "react";

export type NavItem = {
  label: string;
  href: string;
  icon?: ReactNode;
  perm?: string; // future RBAC hook
};

export const NAV: NavItem[] = [
  { label: "Dashboard", href: "/dashboard" },
  { label: "Customers", href: "/examples/forms/customer" },
  { label: "Users", href: "/users" },
  { label: "Profile", href: "/profile" },
];
