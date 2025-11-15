export type NavItem = {
  id: string;
  i18nKey: string; // ex.: "routes.dashboard"
  href: string; // ex.: "/dashboard"
  icon?: string; // opcional (nome do ícone MUI)
  permission?: string;
};

export const NAV: NavItem[] = [
  {
    id: "dashboard",
    i18nKey: "routes.dashboard",
    href: "/dashboard",
    icon: "Dashboard",
  },
  {
    id: "profile",
    i18nKey: "routes.profile",
    href: "/profile",
    icon: "Person",
  },
  {
    id: "examples",
    i18nKey: "routes.examples",
    href: "/examples",
    icon: "Science",
  },
  {
    id: "customer",
    i18nKey: "routes.forms",
    href: "/examples/forms/customer",
    icon: "Assignment",
  },
];
