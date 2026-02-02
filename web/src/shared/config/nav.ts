export type NavItem = {
  id: string;
  i18nKey: string; // ex.: "routes.dashboard"
  href: string; // ex.: "/dashboard"
  icon?: string; // opcional (nome do ícone MUI)
  permission?: string;
};

export const NAV: NavItem[] = [
  {
    id: "profile",
    i18nKey: "routes.profile",
    href: "/profile",
    icon: "Person",
  },
  {
    id: "settings",
    i18nKey: "routes.settings",
    href: "/settings",
    icon: "Settings",
    permission: "authorization:manage",
  },
];
