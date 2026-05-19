"use client";

import * as React from "react";
import { zodResolver } from "@hookform/resolvers/zod";
import { Box, Divider, Stack, Typography } from "@mui/material";
import { FormProvider, useForm } from "react-hook-form";
import { z } from "zod";
import { B3AssetType } from "@/lib/graphql/types/__generated__/graphql";
import { FormStack } from "@/shared/components/ui/forms/components/FormStack";
import { Drawer } from "@/shared/components/ui/layout/Drawer";
import { useTranslation } from "@/shared/i18n";
import { personalFinanceTranslations } from "../../../../i18n";
import { mapB3AssetType } from "@/lib/utils/mapB3AssetType";
import { EquityAutocomplete, type EquityAutocompleteValue } from "../../EquityAutocomplete";
import { getInvestmentIconKindFromAssetType } from "../../InvestmentAssetIcon";
import type { EquityGateResult, NewHoldingSelectGateProps } from "./newHoldingFlow.types";
import { NewHoldingStepAssetAvatar } from "./NewHoldingAssetIdentity";
import { NewHoldingRecentSuggestions } from "./NewHoldingRecentSuggestions";
import {
  pushRecentEquityReit,
  pushRecentEquityStock,
  readRecentEquityReit,
  readRecentEquityStock,
} from "./newHoldingFlowRecentStorage";

type EquityGateFormValues = {
  asset: EquityAutocompleteValue | null;
};

export type EquityRecordVariantGate = "stock" | "fii";

export function EquityAssetGate({
  onContinue,
  onCancel,
  equityRecordVariant,
}: NewHoldingSelectGateProps & { equityRecordVariant: EquityRecordVariantGate }) {
  const { t } = useTranslation(personalFinanceTranslations);
  const assetTypeFilter = equityRecordVariant === "fii" ? B3AssetType.Fii : B3AssetType.Equity;

  const schema = React.useMemo(
    () =>
      z.object({
        asset: z
          .object({
            id: z.string(),
            ticker: z.string(),
            name: z.string(),
            assetType: z.string(),
            isActive: z.boolean(),
            /** Preserved from autocomplete (financial-data logo); do not strip or step 2 loses the asset photo. */
            logoUrl: z.string().nullable().optional(),
          })
          .nullable()
          .refine((v) => v != null, { message: t("investments.recordEquity.validation.assetRequired") }),
      }),
    [t],
  );

  const methods = useForm<EquityGateFormValues>({
    resolver: zodResolver(schema),
    defaultValues: { asset: null },
  });

  const formId = "equity-asset-gate-form";

  const [recentAssets, setRecentAssets] = React.useState<EquityAutocompleteValue[]>([]);

  React.useEffect(() => {
    setRecentAssets(equityRecordVariant === "fii" ? readRecentEquityReit() : readRecentEquityStock());
  }, [equityRecordVariant]);

  const handleSubmit = methods.handleSubmit((data) => {
    const asset = data.asset!;
    if (equityRecordVariant === "fii") {
      pushRecentEquityReit(asset);
    } else {
      pushRecentEquityStock(asset);
    }
    const result: EquityGateResult = { kind: "equity", asset };
    onContinue(result);
  });

  return (
    <FormProvider {...methods}>
      <form id={formId} onSubmit={handleSubmit}>
        <FormStack spacing={2}>
          <Typography variant="body2" color="text.secondary">
            {t("investments.newHoldingFlow.selectEquitySubtitle")}
          </Typography>

          <Stack spacing={2}>
            <Box
              sx={{
                display: "grid",
                gridTemplateColumns: { xs: "1fr", sm: "1fr" },
                gap: 2,
              }}
            >
              <EquityAutocomplete
                name="asset"
                assetTypeFilter={assetTypeFilter}
                size="medium"
                autoFocus
                data-testid="new-holding-equity-autocomplete"
              />
            </Box>

            <NewHoldingRecentSuggestions
              title={t("investments.newHoldingFlow.recentSuggestions")}
              items={recentAssets.map((asset) => ({
                key: asset.id,
                tile: asset.ticker,
                avatar: (
                  <NewHoldingStepAssetAvatar
                    logoSrc={asset.logoUrl ?? undefined}
                    logoAlt={asset.name}
                    iconKind={getInvestmentIconKindFromAssetType(mapB3AssetType(asset.assetType))}
                    disablePhotoShadow
                  />
                ),
                primary: asset.ticker,
                secondary: asset.name,
                onPick: () => {
                  if (equityRecordVariant === "fii") {
                    pushRecentEquityReit(asset);
                  } else {
                    pushRecentEquityStock(asset);
                  }
                  onContinue({ kind: "equity", asset });
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
