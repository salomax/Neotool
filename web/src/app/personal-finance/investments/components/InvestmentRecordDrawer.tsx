"use client";

import React, { useEffect } from "react";
import IconButton from "@mui/material/IconButton";
import ArrowBackIcon from "@mui/icons-material/ArrowBack";
import CloseIcon from "@mui/icons-material/Close";
import { Box, Typography } from "@mui/material";
import { Drawer } from "@/shared/components/ui/layout/Drawer";
import { FinancialAccount } from "@/lib/graphql/types/__generated__/graphql";
import { IssuerSelection } from "./IssuerSelection";

export interface InvestmentRecordDrawerProps {
  open: boolean;
  onClose: () => void;
  title: string;
  initialFinancialAccountId?: string | null;
  financialAccounts?: FinancialAccount[];
  issuerSelection: {
    description: string;
    emptyState: string;
    dataTestIdPrefix: string;
  };
  onResolvedChange?: (id: string | null) => void;
  children: (ctx: { resolvedFinancialAccountId: string | null }) => React.ReactNode;
  footer?: React.ReactNode | ((ctx: { resolvedFinancialAccountId: string | null }) => React.ReactNode);
}

export function InvestmentRecordDrawer({
  open,
  onClose,
  title,
  initialFinancialAccountId,
  financialAccounts = [],
  issuerSelection,
  onResolvedChange,
  children,
  footer,
}: InvestmentRecordDrawerProps) {
  const locale = React.useMemo(() => Intl.DateTimeFormat().resolvedOptions().locale || "pt-BR", []);
  const [userSelectedAccountId, setUserSelectedAccountId] = React.useState<string | null>(null);
  const resolvedFinancialAccountId = open ? (userSelectedAccountId ?? initialFinancialAccountId ?? null) : null;
  const needsAccountSelection = !resolvedFinancialAccountId;

  useEffect(() => {
    onResolvedChange?.(resolvedFinancialAccountId);
  }, [resolvedFinancialAccountId, onResolvedChange]);

  return (
    <Drawer open={open} onClose={onClose} anchor="right" size="md">
      {needsAccountSelection ? (
        <Drawer.Header title={title} />
      ) : (
        <Drawer.Header>
          <Box sx={{ display: "flex", alignItems: "center", justifyContent: "space-between", width: "100%" }}>
            <Box sx={{ display: "flex", alignItems: "center", gap: 1, minWidth: 0 }}>
              <IconButton
                onClick={() => {
                  setUserSelectedAccountId(null);
                }}
                size="small"
                data-testid="investment-record-back-btn"
              >
                <ArrowBackIcon />
              </IconButton>
              <Typography
                variant="h5"
                component="h2"
                fontWeight={400}
                noWrap
                sx={{ letterSpacing: "0.05em", lineHeight: 1.15 }}
              >
                {title.toLocaleUpperCase(locale)}
              </Typography>
            </Box>
            <IconButton onClick={onClose} size="small" data-testid="investment-record-close-btn">
              <CloseIcon />
            </IconButton>
          </Box>
        </Drawer.Header>
      )}
      <Drawer.Body>
        {needsAccountSelection ? (
          <IssuerSelection
            financialAccounts={financialAccounts}
            description={issuerSelection.description}
            emptyState={issuerSelection.emptyState}
            onSelect={(id) => {
              setUserSelectedAccountId(id);
            }}
            dataTestIdPrefix={issuerSelection.dataTestIdPrefix}
          />
        ) : (
          children({ resolvedFinancialAccountId })
        )}
      </Drawer.Body>
      {!needsAccountSelection && footer ? (
        <Drawer.Footer>
          {typeof footer === "function" ? footer({ resolvedFinancialAccountId }) : footer}
        </Drawer.Footer>
      ) : null}
    </Drawer>
  );
}
