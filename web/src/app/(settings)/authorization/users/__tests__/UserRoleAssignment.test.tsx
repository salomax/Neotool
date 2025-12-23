import React from "react";
import { describe, it, expect, vi, afterEach } from "vitest";
import { render, screen, cleanup } from "@testing-library/react";
import { UserRoleAssignment } from "../UserRoleAssignment";
import { AppThemeProvider } from "@/styles/themes/AppThemeProvider";

// Mock translations
vi.mock("@/shared/i18n", () => ({
  useTranslation: () => ({
    t: (key: string) => {
      const translations: Record<string, string> = {
        "userManagement.roles.noRoles": "No roles assigned",
      };
      return translations[key] || key;
    },
  }),
}));

// Mock minimal MUI components
vi.mock("@mui/material", () => ({
  Box: ({ children, ...props }: any) => <div {...props}>{children}</div>,
  Typography: ({ children, ...props }: any) => <p {...props}>{children}</p>,
  Chip: ({ label, ...props }: any) => (
    <div {...props}>
      <span>{label}</span>
    </div>
  ),
}));

const renderUserRoleAssignment = (props = {}) => {
  const defaultProps = {
    userId: "user-1",
    assignedRoles: [],
    ...props,
  };

  return render(
    <AppThemeProvider>
      <UserRoleAssignment {...defaultProps} />
    </AppThemeProvider>
  );
};

describe.sequential("UserRoleAssignment", () => {
  afterEach(() => {
    cleanup();
  });

  it("shows empty state when no roles are assigned", () => {
    renderUserRoleAssignment({ assignedRoles: [] });

    expect(screen.getByTestId("user-role-assignment-empty")).toHaveTextContent(
      "No roles assigned"
    );
  });

  it("renders chips for assigned roles", () => {
    renderUserRoleAssignment({
      assignedRoles: [
        { id: "1", name: "Admin Role" },
        { id: "2", name: "User Role" },
      ],
    });

    expect(screen.getByTestId("user-role-chip-1")).toHaveTextContent("Admin Role");
    expect(screen.getByTestId("user-role-chip-2")).toHaveTextContent("User Role");
  });

  it("applies outlined chips in readonly mode by default", () => {
    renderUserRoleAssignment({
      assignedRoles: [{ id: "1", name: "Admin Role" }],
    });

    const chip = screen.getByTestId("user-role-chip-1");
    expect(chip).toBeInTheDocument();
  });
});
