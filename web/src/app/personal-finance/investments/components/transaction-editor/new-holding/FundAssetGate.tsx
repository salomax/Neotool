"use client";

import * as React from "react";
import { zodResolver } from "@hookform/resolvers/zod";
import { Divider, Stack, Tab, TextField, Typography } from "@mui/material";
import { Controller, FormProvider, useForm } from "react-hook-form";
import { z } from "zod";
import { AssetType } from "@/lib/graphql/types/__generated__/graphql";
import { FormStack } from "@/shared/components/ui/forms/components/FormStack";
import { FormTabs } from "@/shared/components/ui/forms/components/FormTabs";
import { Drawer } from "@/shared/components/ui/layout/Drawer";
import { Label } from "@/shared/components/ui/primitives/Label";
import { useTranslation } from "@/shared/i18n";
import { SegmentedControl } from "@/shared/components/ui/inputs/SegmentedControl";
import { personalFinanceTranslations } from "../../../../i18n";
import { FundAutocomplete, type FundAutocompleteValue } from "../../FundAutocomplete";
import { getInvestmentIconKindFromAssetType } from "../../InvestmentAssetIcon";
import type { FundGateResult, NewHoldingSelectGateProps } from "./newHoldingFlow.types";
import { NewHoldingStepAssetAvatar } from "./NewHoldingAssetIdentity";
import { NewHoldingRecentSuggestions, recentSuggestionTileFromName } from "./NewHoldingRecentSuggestions";
import {
  pushRecentFundCvm,
  pushRecentFundManual,
  readRecentFundCvm,
  readRecentFundManual,
  type NewHoldingRecentFundManual,
} from "./newHoldingFlowRecentStorage";
import { formatCnpj } from "@/lib/utils/formatCnpj";

type ListingMode = "CVM" | "MANUAL";

type FundGateFormValues = {
  listingMode: ListingMode;
  fund: FundAutocompleteValue | null;
  manualFundName: string;
  manualFundCnpj: string;
  manualAssetType: AssetType;
};

const defaultValues: FundGateFormValues = {
  listingMode: "CVM",
  fund: null,
  manualFundName: "",
  manualFundCnpj: "",
  manualAssetType: AssetType.MutualFund,
};

function toGateResult(data: FundGateFormValues): FundGateResult {
  if (data.listingMode === "CVM") {
    return { kind: "fund_cvm", listingMode: "CVM", fund: data.fund! };
  }
  return {
    kind: "fund_manual",
    listingMode: "MANUAL",
    manualFundName: data.manualFundName.trim(),
    manualFundCnpj: data.manualFundCnpj.trim(),
    manualAssetType: data.manualAssetType,
  };
}

export function FundAssetGate({ onContinue, onCancel }: NewHoldingSelectGateProps) {
  const { t } = useTranslation(personalFinanceTranslations);
  const schema = React.useMemo(
    () =>
      z
        .object({
          listingMode: z.enum(["CVM", "MANUAL"]),
          fund: z
            .object({
              id: z.string(),
              name: z.string(),
              cnpj: z.string(),
              assetType: z.string(),
              isActive: z.boolean(),
            })
            .nullable(),
          manualFundName: z.string(),
          manualFundCnpj: z.string(),
          manualAssetType: z.nativeEnum(AssetType),
        })
        .superRefine((data, ctx) => {
          if (data.listingMode === "CVM") {
            if (data.fund == null) {
              ctx.addIssue({
                code: z.ZodIssueCode.custom,
                message: t("investments.recordFund.validation.fundRequired"),
                path: ["fund"],
              });
            }
          } else if (!data.manualFundName.trim()) {
            ctx.addIssue({
              code: z.ZodIssueCode.custom,
              message: t("investments.recordFund.validation.manualFundNameRequired"),
              path: ["manualFundName"],
            });
          }
        }),
    [t],
  );

  const methods = useForm<FundGateFormValues>({
    resolver: zodResolver(schema),
    defaultValues,
  });

  const listingMode = methods.watch("listingMode");

  const formId = "fund-asset-gate-form";

  const [recentCvmFunds, setRecentCvmFunds] = React.useState<FundAutocompleteValue[]>([]);
  const [recentManualFunds, setRecentManualFunds] = React.useState<NewHoldingRecentFundManual[]>([]);

  React.useEffect(() => {
    setRecentCvmFunds(readRecentFundCvm());
    setRecentManualFunds(readRecentFundManual());
  }, []);

  const handleSubmit = methods.handleSubmit((data) => {
    const result = toGateResult(data);
    if (result.kind === "fund_cvm") {
      pushRecentFundCvm(result.fund);
    } else {
      pushRecentFundManual({
        manualFundName: result.manualFundName,
        manualFundCnpj: result.manualFundCnpj,
        manualAssetType: result.manualAssetType,
      });
    }
    onContinue(result);
  });

  return (
    <FormProvider {...methods}>
      <form id={formId} onSubmit={handleSubmit}>
        <FormStack spacing={2}>
          <Typography variant="body2" color="text.secondary">
            {t("investments.newHoldingFlow.selectFundSubtitle")}
          </Typography>

          <FormStack spacing={0.5}>
            <Label sx={{ mb: 0 }}>{t("investments.recordFund.sections.listing")}</Label>
            <FormTabs
              value={listingMode}
              onChange={(_, next: ListingMode) => {
                methods.setValue("listingMode", next, { shouldDirty: true, shouldValidate: true });
              }}
            >
              <Tab value="CVM" label={t("investments.recordFund.modes.cvmListed")} />
              <Tab value="MANUAL" label={t("investments.recordFund.modes.notListed")} />
            </FormTabs>
          </FormStack>

          {listingMode === "CVM" ? (
            <Stack spacing={2}>
              <FundAutocomplete
                name="fund"
                size="medium"
                autoFocus
                data-testid="new-holding-fund-autocomplete"
              />
              <NewHoldingRecentSuggestions
                title={t("investments.newHoldingFlow.recentSuggestions")}
                items={recentCvmFunds.map((fund) => ({
                  key: fund.id,
                  tile: recentSuggestionTileFromName(fund.name),
                  avatar: (
                    <NewHoldingStepAssetAvatar
                      logoAlt={fund.name}
                      iconKind={getInvestmentIconKindFromAssetType(fund.assetType)}
                      disablePhotoShadow
                    />
                  ),
                  primary: fund.name,
                  secondary: formatCnpj(fund.cnpj),
                  onPick: () => {
                    pushRecentFundCvm(fund);
                    onContinue({ kind: "fund_cvm", listingMode: "CVM", fund });
                  },
                }))}
              />
            </Stack>
          ) : (
            <FormStack spacing={1.5}>
              <NewHoldingRecentSuggestions
                title={t("investments.newHoldingFlow.recentSuggestions")}
                items={recentManualFunds.map((row) => ({
                  key: `${row.manualFundName}|${row.manualFundCnpj}|${row.manualAssetType}`,
                  tile: recentSuggestionTileFromName(row.manualFundName),
                  avatar: (
                    <NewHoldingStepAssetAvatar
                      logoAlt={row.manualFundName.trim()}
                      iconKind={getInvestmentIconKindFromAssetType(row.manualAssetType)}
                      disablePhotoShadow
                    />
                  ),
                  primary: row.manualFundName,
                  secondary: formatCnpj(row.manualFundCnpj),
                  onPick: () => {
                    pushRecentFundManual({
                      manualFundName: row.manualFundName,
                      manualFundCnpj: row.manualFundCnpj,
                      manualAssetType: row.manualAssetType,
                    });
                    onContinue({
                      kind: "fund_manual",
                      listingMode: "MANUAL",
                      manualFundName: row.manualFundName,
                      manualFundCnpj: row.manualFundCnpj,
                      manualAssetType: row.manualAssetType,
                    });
                  },
                }))}
              />
              <Controller
                name="manualFundName"
                control={methods.control}
                render={({ field, fieldState }) => (
                  <TextField
                    {...field}
                    label={t("investments.recordFund.fields.manualFundName")}
                    size="medium"
                    autoFocus
                    error={!!fieldState.error}
                    helperText={fieldState.error?.message}
                    data-testid="new-holding-fund-manual-name"
                  />
                )}
              />
              <Controller
                name="manualFundCnpj"
                control={methods.control}
                render={({ field, fieldState }) => (
                  <TextField
                    {...field}
                    label={t("investments.recordFund.fields.manualFundCnpj")}
                    size="medium"
                    error={!!fieldState.error}
                    helperText={fieldState.error?.message}
                    data-testid="new-holding-fund-manual-cnpj"
                  />
                )}
              />
              <FormStack spacing={0.5}>
                <Label sx={{ mb: 0 }}>{t("investments.recordFund.fields.manualAssetType")}</Label>
                <Controller
                  name="manualAssetType"
                  control={methods.control}
                  render={({ field }) => (
                    <SegmentedControl
                      fullWidth
                      value={field.value ?? AssetType.MutualFund}
                      onChange={(value) => field.onChange(value)}
                      options={[
                        { value: AssetType.MutualFund, label: t("investments.recordFund.assetTypes.MUTUAL_FUND") },
                        { value: AssetType.Pgbl, label: t("investments.recordFund.assetTypes.PGBL") },
                        { value: AssetType.Vgbl, label: t("investments.recordFund.assetTypes.VGBL") },
                      ]}
                    />
                  )}
                />
              </FormStack>
            </FormStack>
          )}

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
