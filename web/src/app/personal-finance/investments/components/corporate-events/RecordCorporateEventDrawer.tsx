"use client";

import * as React from "react";
import { zodResolver } from "@hookform/resolvers/zod";
import {
  Box,
  Divider,
  Paper,
  ToggleButton,
  ToggleButtonGroup,
  Typography,
} from "@mui/material";
import { FormProvider, useForm, useWatch } from "react-hook-form";
import { z } from "zod";
import { DatePickerField, MoneyInput, NumberField, PercentField, SelectField } from "@/shared/components/ui/forms/form";
import { Drawer } from "@/shared/components/ui/layout/Drawer";
import { useToast } from "@/shared/providers";
import { useTranslation } from "@/shared/i18n";
import { personalFinanceTranslations } from "../../../i18n";
import { HoldingIdentity, resolveHoldingIdentity } from "../HoldingIdentity";
import type { HoldingRow } from "../table/investmentTableHelpers";
import type { IssuerSelectionAccount } from "../IssuerSelection";

export type CorporateEventType = "PROVENTO" | "SPLIT" | "REVERSE_SPLIT" | "BONUS" | "SUBSCRIPTION";

interface RecordCorporateEventDrawerProps {
  open: boolean;
  onClose: () => void;
  holding: HoldingRow | null;
  eventType: CorporateEventType | null;
  financialAccounts?: IssuerSelectionAccount[];
  onRecorded?: () => void;
}

type CorporateEventForm = {
  paymentDate?: Date;
  proventoType?: string;
  valuePerUnit?: number;
  quantity?: number;
  grossTotal?: number;
  irWithheld?: number;
  destinationAccountId?: string;
  splitNumerator?: number;
  splitDenominator?: number;
  bonusPercent?: number;
  issuePrice?: number;
  exerciseDate?: Date;
  exercisePrice?: number;
  subscribedQuantity?: number;
};

const SCHEMA = z.object({
  paymentDate: z.date().optional(),
  proventoType: z.string().optional(),
  valuePerUnit: z.number().optional(),
  quantity: z.number().optional(),
  grossTotal: z.number().optional(),
  irWithheld: z.number().optional(),
  destinationAccountId: z.string().optional(),
  splitNumerator: z.number().optional(),
  splitDenominator: z.number().optional(),
  bonusPercent: z.number().optional(),
  issuePrice: z.number().optional(),
  exerciseDate: z.date().optional(),
  exercisePrice: z.number().optional(),
  subscribedQuantity: z.number().optional(),
});

export function RecordCorporateEventDrawer({
  open,
  onClose,
  holding,
  eventType,
  financialAccounts = [],
  onRecorded,
}: RecordCorporateEventDrawerProps) {
  const { t } = useTranslation(personalFinanceTranslations);
  const { success } = useToast();
  const methods = useForm<CorporateEventForm>({
    resolver: zodResolver(SCHEMA),
    defaultValues: {},
  });

  React.useEffect(() => {
    if (!open) {
      methods.reset({});
      return;
    }
    methods.reset({
      quantity: holding?.quantity ?? undefined,
      splitNumerator: 1,
      splitDenominator: 1,
    });
  }, [holding, methods, open, eventType]);

  const watchedValuePerUnit = useWatch({ control: methods.control, name: "valuePerUnit" });
  const watchedQuantity = useWatch({ control: methods.control, name: "quantity" });
  const watchedSplitNumerator = useWatch({ control: methods.control, name: "splitNumerator" });
  const watchedSplitDenominator = useWatch({ control: methods.control, name: "splitDenominator" });
  const watchedBonusPercent = useWatch({ control: methods.control, name: "bonusPercent" });
  const watchedExercisePrice = useWatch({ control: methods.control, name: "exercisePrice" });
  const watchedSubscribedQuantity = useWatch({ control: methods.control, name: "subscribedQuantity" });

  const grossTotal = React.useMemo(() => {
    if (!watchedValuePerUnit || !watchedQuantity) return null;
    return watchedValuePerUnit * watchedQuantity;
  }, [watchedQuantity, watchedValuePerUnit]);

  React.useEffect(() => {
    methods.setValue("grossTotal", grossTotal ?? undefined, { shouldDirty: false, shouldValidate: false });
  }, [grossTotal, methods]);

  const splitResultQuantity = React.useMemo(() => {
    if (!holding?.quantity || !watchedSplitNumerator || !watchedSplitDenominator) return null;
    if (watchedSplitDenominator <= 0) return null;
    return (holding.quantity * watchedSplitNumerator) / watchedSplitDenominator;
  }, [holding, watchedSplitDenominator, watchedSplitNumerator]);

  const bonusQuantity = React.useMemo(() => {
    if (!holding?.quantity || !watchedBonusPercent) return null;
    return holding.quantity * (watchedBonusPercent / 100);
  }, [holding, watchedBonusPercent]);

  const subscriptionTotal = React.useMemo(() => {
    if (!watchedExercisePrice || !watchedSubscribedQuantity) return null;
    return watchedExercisePrice * watchedSubscribedQuantity;
  }, [watchedExercisePrice, watchedSubscribedQuantity]);

  const onSubmit = (data: CorporateEventForm) => {
    if (!eventType) return;
    if (eventType === "PROVENTO") {
      if (!data.paymentDate) {
        methods.setError("paymentDate", { message: t("investments.corporateEvents.validation.required") });
        return;
      }
      if (!data.proventoType) {
        methods.setError("proventoType", { message: t("investments.corporateEvents.validation.required") });
        return;
      }
      if (!data.valuePerUnit || data.valuePerUnit <= 0) {
        methods.setError("valuePerUnit", { message: t("investments.corporateEvents.validation.positiveValue") });
        return;
      }
    }
    success(t("investments.corporateEvents.success"));
    onRecorded?.();
    onClose();
  };

  const identity = holding ? resolveHoldingIdentity(holding) : null;

  return (
    <Drawer open={open} onClose={onClose} anchor="right">
      <Drawer.Header title={t("investments.corporateEvents.drawerTitle")} />
      <Drawer.Body>
        <FormProvider {...methods}>
          <form id="record-corporate-event-form" onSubmit={methods.handleSubmit(onSubmit)}>
            <Box sx={{ display: "flex", flexDirection: "column", gap: 2 }}>
              {identity ? (
                <Paper variant="outlined" sx={{ p: 1.5, borderRadius: 2 }}>
                  <HoldingIdentity
                    assetName={identity.assetName}
                    issuerName={identity.issuerName}
                    logoUrl={identity.logoUrl}
                    size={28}
                  />
                </Paper>
              ) : null}

              {eventType === "PROVENTO" ? (
                <>
                  <DatePickerField name="paymentDate" label={t("investments.corporateEvents.form.paymentDate")} />
                  <SelectField
                    name="proventoType"
                    label={t("investments.corporateEvents.form.proventoType")}
                    options={[
                      { label: "Dividendo", value: "DIVIDEND" },
                      { label: "JCP", value: "JCP" },
                      { label: "Rendimento FII", value: "FII" },
                      { label: "Outros", value: "OTHER" },
                    ]}
                  />
                  <MoneyInput name="valuePerUnit" label={t("investments.corporateEvents.form.valuePerUnit")} currency="BRL" />
                  <NumberField name="quantity" label={t("investments.corporateEvents.form.quantity")} allowNegative={false} />
                  <MoneyInput
                    name="grossTotal"
                    label={t("investments.corporateEvents.form.grossTotal")}
                    currency="BRL"
                    disabled
                  />
                  <MoneyInput name="irWithheld" label={t("investments.corporateEvents.form.irWithheld")} currency="BRL" />
                  <SelectField
                    name="destinationAccountId"
                    label={t("investments.corporateEvents.form.destinationAccount")}
                    options={financialAccounts.map((account) => ({ label: account.name, value: account.id }))}
                  />
                </>
              ) : null}

              {eventType === "SPLIT" || eventType === "REVERSE_SPLIT" ? (
                <>
                  <DatePickerField name="paymentDate" label={t("investments.corporateEvents.form.paymentDate")} />
                  <ToggleButtonGroup exclusive fullWidth value={eventType}>
                    <ToggleButton value="SPLIT">{t("investments.corporateEvents.splitType.split")}</ToggleButton>
                    <ToggleButton value="REVERSE_SPLIT">{t("investments.corporateEvents.splitType.reverseSplit")}</ToggleButton>
                  </ToggleButtonGroup>
                  <Box sx={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 1 }}>
                    <NumberField name="splitNumerator" label={t("investments.corporateEvents.form.ratioNumerator")} allowNegative={false} />
                    <NumberField name="splitDenominator" label={t("investments.corporateEvents.form.ratioDenominator")} allowNegative={false} />
                  </Box>
                  <Paper variant="outlined" sx={{ p: 1.5 }}>
                    <Typography variant="body2" color="text.secondary">
                      {t("investments.corporateEvents.form.resultQuantity")}
                    </Typography>
                    <Typography variant="body2" fontWeight={700}>
                      {splitResultQuantity ?? "—"}
                    </Typography>
                  </Paper>
                </>
              ) : null}

              {eventType === "BONUS" ? (
                <>
                  <DatePickerField name="paymentDate" label={t("investments.corporateEvents.form.paymentDate")} />
                  <PercentField name="bonusPercent" label={t("investments.corporateEvents.form.bonusPercent")} />
                  <MoneyInput name="issuePrice" label={t("investments.corporateEvents.form.issuePrice")} currency="BRL" />
                  <Paper variant="outlined" sx={{ p: 1.5 }}>
                    <Typography variant="body2" color="text.secondary">
                      {t("investments.corporateEvents.form.bonusQuantity")}
                    </Typography>
                    <Typography variant="body2" fontWeight={700}>
                      {bonusQuantity ?? "—"}
                    </Typography>
                  </Paper>
                </>
              ) : null}

              {eventType === "SUBSCRIPTION" ? (
                <>
                  <DatePickerField name="exerciseDate" label={t("investments.corporateEvents.form.exerciseDate")} />
                  <MoneyInput name="exercisePrice" label={t("investments.corporateEvents.form.exercisePrice")} currency="BRL" />
                  <NumberField
                    name="subscribedQuantity"
                    label={t("investments.corporateEvents.form.subscribedQuantity")}
                    allowNegative={false}
                  />
                  <Paper variant="outlined" sx={{ p: 1.5 }}>
                    <Typography variant="body2" color="text.secondary">
                      {t("investments.corporateEvents.form.grossTotal")}
                    </Typography>
                    <Typography variant="body2" fontWeight={700}>
                      {subscriptionTotal ?? "—"}
                    </Typography>
                  </Paper>
                </>
              ) : null}

              <Divider />
              <Drawer.ActionButtons
                cancelText={t("actions.cancel")}
                submitText={t("actions.save")}
                submittingText={t("actions.saving")}
                onCancel={onClose}
                submitForm="record-corporate-event-form"
              />
            </Box>
          </form>
        </FormProvider>
      </Drawer.Body>
    </Drawer>
  );
}
