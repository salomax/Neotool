"use client";

import * as React from "react";
import { zodResolver } from "@hookform/resolvers/zod";
import { Divider, Stack, Typography } from "@mui/material";
import { FormProvider, useForm } from "react-hook-form";
import { z } from "zod";
import { FormStack } from "@/shared/components/ui/forms/components/FormStack";
import { Drawer } from "@/shared/components/ui/layout/Drawer";
import { FormTextField } from "@/shared/components/ui/forms/form";
import { useTranslation } from "@/shared/i18n";
import { personalFinanceTranslations } from "../../../../i18n";
import type { CryptoGateResult, NewHoldingSelectGateProps } from "./newHoldingFlow.types";
import { NewHoldingRecentSuggestions } from "./NewHoldingRecentSuggestions";
import { pushRecentCrypto, readRecentCrypto, type NewHoldingRecentCrypto } from "./newHoldingFlowRecentStorage";

type CryptoGateFormValues = {
  name: string;
  ticker: string;
  network: string;
};

export function CryptoAssetGate({ onContinue, onCancel }: NewHoldingSelectGateProps) {
  const { t } = useTranslation(personalFinanceTranslations);
  const schema = React.useMemo(
    () =>
      z.object({
        name: z.string().min(1, { message: t("investments.recordCrypto.validation.nameRequired") }),
        ticker: z.string().min(1, { message: t("investments.recordCrypto.validation.tickerRequired") }),
        network: z.string().optional(),
      }),
    [t],
  );

  const methods = useForm<CryptoGateFormValues>({
    resolver: zodResolver(schema),
    defaultValues: { name: "", ticker: "", network: "" },
  });

  const formId = "crypto-asset-gate-form";

  const [recentCryptos, setRecentCryptos] = React.useState<NewHoldingRecentCrypto[]>([]);

  React.useEffect(() => {
    setRecentCryptos(readRecentCrypto());
  }, []);

  const handleSubmit = methods.handleSubmit((data) => {
    const result: CryptoGateResult = {
      kind: "crypto",
      name: data.name.trim(),
      ticker: data.ticker.trim(),
      network: data.network?.trim() ?? "",
    };
    pushRecentCrypto({
      name: result.name,
      ticker: result.ticker,
      network: result.network,
    });
    onContinue(result);
  });

  return (
    <FormProvider {...methods}>
      <form id={formId} onSubmit={handleSubmit}>
        <FormStack spacing={2}>
          <Typography variant="body2" color="text.secondary">
            {t("investments.newHoldingFlow.selectCryptoSubtitle")}
          </Typography>

          <Stack spacing={2}>
            <FormTextField
              name="name"
              label={t("investments.recordCrypto.fields.name")}
              size="medium"
              required
              autoFocus
            />
            <FormTextField name="ticker" label={t("investments.recordCrypto.fields.ticker")} size="medium" required />
            <FormTextField name="network" label={t("investments.recordCrypto.fields.network")} size="medium" />

            <NewHoldingRecentSuggestions
              title={t("investments.newHoldingFlow.recentSuggestions")}
              items={recentCryptos.map((row) => ({
                key: `${row.ticker}|${row.name}`,
                tile: row.ticker,
                primary: row.ticker.toUpperCase(),
                secondary: [row.name, row.network.trim() || null].filter(Boolean).join(" · "),
                onPick: () => {
                  const entry = {
                    name: row.name,
                    ticker: row.ticker,
                    network: row.network ?? "",
                  };
                  pushRecentCrypto(entry);
                  onContinue({
                    kind: "crypto",
                    name: entry.name,
                    ticker: entry.ticker,
                    network: entry.network.trim(),
                  });
                },
              }))}
            />
          </Stack>

          <Divider />

          <Drawer.ActionButtons
            cancelText={t("actions.cancel")}
            submitText={t("investments.newHoldingFlow.continue")}
            submittingText={t("investments.newHoldingFlow.continue")}
            onCancel={onCancel}
            submitForm={formId}
            submitting={false}
          />
        </FormStack>
      </form>
    </FormProvider>
  );
}
