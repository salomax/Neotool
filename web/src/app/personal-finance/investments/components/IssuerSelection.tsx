import React from "react";
import { Avatar, Box, List, ListItemButton, ListItemIcon, ListItemText, Paper, Stack, Typography, alpha, useTheme } from "@mui/material";
import type { FinancialAccountsQuery } from "@/lib/graphql/operations/banking/queries.generated";

export type IssuerSelectionAccount = NonNullable<FinancialAccountsQuery["financialAccounts"]>[number];

export interface IssuerSelectionProps {
  financialAccounts: IssuerSelectionAccount[];
  description: React.ReactNode;
  emptyState: React.ReactNode;
  onSelect: (accountId: string) => void;
  dataTestIdPrefix?: string;
}

export function IssuerSelection({
  financialAccounts,
  description,
  emptyState,
  onSelect,
  dataTestIdPrefix = "issuer-selection-account-",
}: IssuerSelectionProps) {
  const theme = useTheme();

  return (
    <Stack spacing={2}>
      <Typography variant="body2" color="text.secondary">
        {description}
      </Typography>

      {financialAccounts.length === 0 ? (
        <Typography variant="body2" color="text.secondary">
          {emptyState}
        </Typography>
      ) : (
        <Paper variant="outlined" sx={{ borderRadius: 2, overflow: "hidden" }}>
          <List disablePadding>
            {financialAccounts.map((account) => (
              <ListItemButton
                key={account.id}
                onClick={() => onSelect(account.id)}
                sx={{
                  py: 1.5,
                  "&:not(:last-of-type)": { borderBottom: `1px solid ${alpha(theme.palette.divider, 0.6)}` },
                }}
                data-testid={`${dataTestIdPrefix}${account.id}`}
              >
                <ListItemIcon sx={{ minWidth: 44 }}>
                  <Avatar
                    variant="rounded"
                    src={account.issuer?.logoAsset?.publicUrl || undefined}
                    slotProps={{ img: { style: { objectFit: "contain" } } }}
                    sx={{
                      bgcolor: account.issuer?.logoAsset?.publicUrl ? "transparent" : (account.themeColor || "primary.main"),
                      width: 32,
                      height: 32,
                      borderRadius: 1.5,
                    }}
                  >
                    {account.name?.slice(0, 1).toUpperCase()}
                  </Avatar>
                </ListItemIcon>
                <ListItemText
                  primary={
                    <Typography variant="body2" fontWeight={600} noWrap>
                      {account.name}
                    </Typography>
                  }
                  secondary={
                    <Typography variant="caption" color="text.secondary" noWrap>
                      {account.issuer?.name || account.issuerName}
                    </Typography>
                  }
                />
              </ListItemButton>
            ))}
          </List>
        </Paper>
      )}
    </Stack>
  );
}

