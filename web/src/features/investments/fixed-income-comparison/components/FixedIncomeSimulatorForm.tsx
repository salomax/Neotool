"use client";

import { useEffect, useState, useRef } from "react";
import {
  useForm,
  FormProvider,
  Controller,
  useWatch,
  useController,
  type Control,
  type UseFormSetError,
  type UseFormClearErrors,
} from "react-hook-form";
import { useLazyQuery } from "@apollo/client/react";
import {
  Box,
  Divider,
  Typography,
  TextField,
  Button,
  ToggleButton,
  ToggleButtonGroup,
  IconButton,
  InputAdornment,
  Slider as MuiSlider,
} from "@mui/material";
import dayjs from "dayjs";
import "dayjs/locale/pt-br";
import DoneIcon from "@mui/icons-material/Done";
import CancelIcon from "@mui/icons-material/Cancel";
import RestartAltIcon from "@mui/icons-material/RestartAlt";
import { NumericFormat } from "react-number-format";
import { useTranslation } from "@/shared/i18n";
import { investmentsTranslations } from "@/shared/i18n/domains/investments";
import i18n from "@/shared/i18n/config";
import { getLocaleSeparators } from "@/shared/utils/locale";
import { useEconomicIndicatorsForSimulator } from "../hooks/useEconomicIndicatorsForSimulator";
import { PrincipalInput } from "./PrincipalInput";
import { FormSectionLabel } from "./FormSectionLabel";
import { FixedIncomeComparisonDocument } from "../api/queries.generated";
import type { FixedIncomeProductGroup, FixedIncomeRateModel } from "@/lib/graphql/types/__generated__/graphql";
import type { FixedIncomeComparisonQuery } from "../api/queries.generated";

const DEFAULT_PRINCIPAL = 50000;

function percentToBps(value: number): number {
  return Math.round(value * 100);
}

function defaultEndDate(startDate: string): string {
  return startDate ? dayjs(startDate).add(1, "year").format("YYYY-MM-DD") : "";
}

function SelectedBadge() {
  return (
    <Box
      sx={{
        position: "absolute",
        top: 8,
        right: 8,
        width: 18,
        height: 18,
        borderRadius: "50%",
        bgcolor: "background.paper",
        border: "1px solid",
        borderColor: "primary.main",
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        zIndex: 1,
      }}
    >
      <DoneIcon sx={{ fontSize: 14, color: "primary.main" }} />
    </Box>
  );
}
const PROJECTION_MIN = -100;
const PROJECTION_MAX = 100;
const PROJECTION_SLIDER_MIN = 0;
const PROJECTION_SLIDER_MAX = 20;
const PROJECTION_DECIMALS = 2;
const RATE_MODEL_OPTIONS: { value: FixedIncomeRateModel; labelKey: string }[] = [
  { value: "CDI" as FixedIncomeRateModel, labelKey: "rateModelCdi" },
  { value: "FIXED" as FixedIncomeRateModel, labelKey: "rateModelFixed" },
  { value: "IPCA_PLUS" as FixedIncomeRateModel, labelKey: "rateModelIpca" },
];

export interface FixedIncomeSimulatorFormValues {
  productGroup: FixedIncomeProductGroup;
  principal: number | "";
  rateModel: FixedIncomeRateModel;
  contractedRateValue: number | "";
  startDate: string;
  endDate: string;
  cdiAnnualPercent: number;
  ipcaAnnualPercent: number;
}

export interface FixedIncomeSimulatorFormProps {
  onSubmit?: (values: FixedIncomeSimulatorFormValues) => void;
  onComparisonResult?: (data: FixedIncomeComparisonQuery["fixedIncomeComparison"]) => void;
  onComparisonLoadingChange?: (loading: boolean) => void;
}

function formatDate(d: Date): string {
  return d.toISOString().slice(0, 10);
}

/** Map i18n language to number-format locale (for decimal/thousand separators). */
function getNumberFormatLocale(): string {
  const lang = i18n.language?.toLowerCase() ?? "pt";
  return lang.startsWith("pt") ? "pt-BR" : "en-US";
}

function getDateInputOrder(): "DMY" | "MDY" {
  const lang = i18n.language?.toLowerCase() ?? "pt";
  return lang.startsWith("pt") ? "DMY" : "MDY";
}

function pad2(n: number): string {
  return String(n).padStart(2, "0");
}

function isValidDateParts(year: number, month: number, day: number): boolean {
  if (year < 1900 || year > 2200) return false;
  if (month < 1 || month > 12) return false;
  if (day < 1 || day > 31) return false;
  const dt = new Date(Date.UTC(year, month - 1, day));
  return (
    dt.getUTCFullYear() === year &&
    dt.getUTCMonth() === month - 1 &&
    dt.getUTCDate() === day
  );
}

function isValidIsoDate(iso: string): boolean {
  const m = iso.match(/^(\d{4})-(\d{2})-(\d{2})$/);
  if (!m) return false;
  const year = Number(m[1]);
  const month = Number(m[2]);
  const day = Number(m[3]);
  return isValidDateParts(year, month, day);
}

function parseDateInputToIso(value: string): string | null {
  const trimmed = value.trim();
  const m = trimmed.match(/^(\d{1,2})[/-](\d{1,2})[/-](\d{4})$/);
  if (!m) return null;
  const a = Number(m[1]);
  const b = Number(m[2]);
  const year = Number(m[3]);
  const order = getDateInputOrder();
  const day = order === "DMY" ? a : b;
  const month = order === "DMY" ? b : a;
  if (!isValidDateParts(year, month, day)) return null;
  return `${year}-${pad2(month)}-${pad2(day)}`;
}

function formatDateInputWhileTyping(value: string): string {
  const digits = value.replace(/\D/g, "").slice(0, 8);
  const a = digits.slice(0, 2);
  const b = digits.slice(2, 4);
  const c = digits.slice(4, 8);
  if (digits.length <= 2) return a;
  if (digits.length <= 4) return `${a}/${b}`;
  return `${a}/${b}/${c}`;
}

function formatIsoToDateInput(iso: string): string {
  if (!iso) return "";
  if (!isValidIsoDate(iso)) return "";
  const order = getDateInputOrder();
  const d = dayjs(iso);
  return order === "DMY" ? d.format("DD/MM/YYYY") : d.format("MM/DD/YYYY");
}

function DateInputField({
  control,
  name,
  label,
  placeholder,
  requiredMessage,
  invalidMessage,
  minIso,
  clearAriaLabel,
  setError,
  clearErrors,
}: {
  control: Control<FixedIncomeSimulatorFormValues>;
  name: "startDate" | "endDate";
  label: string;
  placeholder: string;
  requiredMessage: string;
  invalidMessage: string;
  minIso?: string;
  clearAriaLabel: string;
  setError: UseFormSetError<FixedIncomeSimulatorFormValues>;
  clearErrors: UseFormClearErrors<FixedIncomeSimulatorFormValues>;
}) {
  const { field, fieldState } = useController({
    control,
    name,
    rules: {
      required: requiredMessage,
      validate: (v) => {
        if (!v) return requiredMessage;
        if (!isValidIsoDate(v)) return invalidMessage;
        if (minIso && isValidIsoDate(minIso)) {
          const end = dayjs(v);
          const minEnd = dayjs(minIso);
          return end.isBefore(minEnd) ? requiredMessage : true;
        }
        return true;
      },
    },
  });

  const [localValue, setLocalValue] = useState(() => formatIsoToDateInput(field.value));

  return (
    <Box sx={{ minWidth: 0 }}>
      <FormSectionLabel>{label}</FormSectionLabel>

      <TextField
        fullWidth
        size="small"
        value={localValue}
        onChange={(e) => {
          clearErrors(name);
          setLocalValue(formatDateInputWhileTyping(e.target.value));
        }}
        onBlur={() => {
          const iso = localValue ? parseDateInputToIso(localValue) : null;
          if (!iso) {
            field.onChange("");
            setError(name, {
              type: "validate",
              message: localValue ? invalidMessage : requiredMessage,
            });
            return;
          }
          field.onChange(iso);
          clearErrors(name);
          setLocalValue(formatIsoToDateInput(iso));
        }}
        error={!!fieldState.error}
        helperText={fieldState.error?.message}
        placeholder={placeholder}
        inputProps={{
          inputMode: "numeric",
          placeholder,
          maxLength: 10,
        }}
        InputProps={{
          endAdornment: localValue ? (
            <InputAdornment position="end">
              <IconButton
                aria-label={clearAriaLabel}
                tabIndex={-1}
                onClick={() => {
                  setLocalValue("");
                  field.onChange("");
                  clearErrors(name);
                }}
                edge="end"
                size="small"
                sx={{ mr: -0.5, p: 0.25, color: "text.disabled" }}
              >
                <CancelIcon fontSize="inherit" sx={{ fontSize: 16 }} />
              </IconButton>
            </InputAdornment>
          ) : null,
        }}
        sx={{ minWidth: 0 }}
      />
    </Box>
  );
}

export function FixedIncomeSimulatorForm({
  onSubmit,
  onComparisonResult,
  onComparisonLoadingChange,
}: FixedIncomeSimulatorFormProps) {
  const { t } = useTranslation(investmentsTranslations);
  const ns = "fixedIncomeComparison" as const;
  const today = formatDate(new Date());
  const reqMsg = t(`${ns}.validationRequired`);

  const {
    cdiPercent,
    ipcaPercent,
    loading: indicatorsLoading,
  } = useEconomicIndicatorsForSimulator();

  const containerRef = useRef<HTMLElement>(null);

  const methods = useForm<FixedIncomeSimulatorFormValues>({
    defaultValues: {
      productGroup: "TAXABLE_IR" as FixedIncomeProductGroup,
      principal: DEFAULT_PRINCIPAL,
      rateModel: "CDI" as FixedIncomeRateModel,
      contractedRateValue: 100.0,
      startDate: today,
      endDate: defaultEndDate(today),
      cdiAnnualPercent: 0,
      ipcaAnnualPercent: 0,
    },
    mode: "onChange",
  });

  const { setValue, handleSubmit, control } = methods;
  const productGroup = useWatch({ control, name: "productGroup" });
  const rateModel = useWatch({ control, name: "rateModel" });
  const startDate = useWatch({ control, name: "startDate" });
  const numberFormatLocale = getNumberFormatLocale();
  const { decimal: contractedRateDecimal, group: contractedRateGroup } =
    getLocaleSeparators(numberFormatLocale);

  useEffect(() => {
    if (indicatorsLoading) return;
    setValue("cdiAnnualPercent", cdiPercent);
    setValue("ipcaAnnualPercent", ipcaPercent);
  }, [indicatorsLoading, cdiPercent, ipcaPercent, setValue]);

  const endDate = useWatch({ control, name: "endDate" });
  useEffect(() => {
    if (!startDate || !endDate) return;
    const minEnd = dayjs(startDate).add(1, "day");
    if (dayjs(endDate).isBefore(minEnd)) {
      setValue("endDate", "");
    }
  }, [startDate, setValue, endDate]);

  const [runComparison, { data: comparisonData, loading: comparisonLoading }] = useLazyQuery(
    FixedIncomeComparisonDocument,
    { fetchPolicy: "network-only" }
  );
  
  useEffect(() => {
    if (comparisonData?.fixedIncomeComparison) {
      onComparisonResult?.(comparisonData.fixedIncomeComparison);
    }
  }, [comparisonData, onComparisonResult]);

  useEffect(() => {
    onComparisonLoadingChange?.(comparisonLoading);
  }, [comparisonLoading, onComparisonLoadingChange]);

  const handleFormSubmit = (values: FixedIncomeSimulatorFormValues) => {
    onSubmit?.(values);
    const principal =
      typeof values.principal === "number" && values.principal > 0
        ? values.principal
        : DEFAULT_PRINCIPAL;
    runComparison({
      variables: {
        input: {
          productGroup: values.productGroup,
          principal,
          startDate: values.startDate,
          endDate: values.endDate,
          rateModel: values.rateModel,
          contractedRateBps: percentToBps(
            values.contractedRateValue === ""
              ? 0
              : Number(values.contractedRateValue)
          ),
          cdiAnnualRateBps: percentToBps(values.cdiAnnualPercent),
          ipcaAnnualRateBps: percentToBps(values.ipcaAnnualPercent),
        },
      },
    });
  };

  return (
    <Box
      ref={containerRef}
      component="section"
      sx={{
        bgcolor: "background.paper",
        p: 3,
        borderRadius: 2,
        border: "1px solid",
        borderColor: "divider",
        width: "100%",
        minWidth: 0,
        maxWidth: { xs: "100%", md: 440 },
        fontSize: 14,
        "& .MuiTypography-root:not(.FormSectionLabel-root)": { fontSize: 14 },
        "& .MuiButton-root": { fontSize: 14 },
        "& .MuiToggleButton-root": { fontSize: 14 },
        "& .MuiInputBase-input": { fontSize: 14 },
        "& .MuiInputAdornment-root": { fontSize: 14 },
        "& .MuiFormHelperText-root": { fontSize: 14 },
      }}
      data-purpose="simulador-form"
    >

      <FormProvider {...methods}>
        <Box
          component="form"
          onSubmit={handleSubmit(handleFormSubmit)}
          sx={{ display: "flex", flexDirection: "column", gap: 3 }}
        >
          {/* Product group - Tipo de Ativo */}
          <Box>
            <FormSectionLabel mb={1}>{t(`${ns}.productGroup`)}</FormSectionLabel>
            <Box sx={{ display: "flex", flexDirection: "column", gap: 1 }}>
              <Box>
                <Button
                  type="button"
                  fullWidth
                  variant={productGroup === "TAXABLE_IR" ? "contained" : "outlined"}
                  onClick={() => methods.setValue("productGroup", "TAXABLE_IR" as FixedIncomeProductGroup)}
                  sx={{
                    py: 2,
                    px: 2,
                    justifyContent: "space-between",
                    gap: 1.5,
                    textTransform: "none",
                    textAlign: "left",
                    alignItems: "center",
                    position: "relative",
                  }}
                >
                  <Typography variant="body2">
                    {t(`${ns}.taxableProductLabel`)}
                  </Typography>
                  {productGroup === "TAXABLE_IR" && <SelectedBadge />}
                </Button>
              </Box>
              <Box>
                <Button
                  type="button"
                  fullWidth
                  variant={productGroup === "EXEMPT_IR" ? "contained" : "outlined"}
                  onClick={() => methods.setValue("productGroup", "EXEMPT_IR" as FixedIncomeProductGroup)}
                  sx={{
                    py: 2,
                    px: 2,
                    justifyContent: "space-between",
                    gap: 1.5,
                    textTransform: "none",
                    textAlign: "left",
                    alignItems: "center",
                    position: "relative",
                  }}
                >
                  <Typography variant="body2">
                    {t(`${ns}.exemptProductLabel`)}
                  </Typography>
                  {productGroup === "EXEMPT_IR" && <SelectedBadge />}
                </Button>
              </Box>
            </Box>

            <Typography variant="body2" sx={{ color: "text.secondary", mt: 2, pl: 2 }}>
              {productGroup === "TAXABLE_IR"
                ? t(`${ns}.incidesIrDescription`)
                : t(`${ns}.exemptIrDescription`)}
            </Typography>
          </Box>

          <Divider />

          {/* Indexador - Rate model (same style as Tipo de Ativo, single line) */}
          <Box>
            <FormSectionLabel mb={1}>{t(`${ns}.rateModel`)}</FormSectionLabel>
            <ToggleButtonGroup
              exclusive
              value={rateModel}
              onChange={(_, nextValue: FixedIncomeRateModel | null) => {
                if (!nextValue) return;
                methods.setValue("rateModel", nextValue);
              }}
              aria-label={t(`${ns}.rateModel`) ?? "rate model"}
              sx={{
                width: "100%",
                display: "flex",
                flexDirection: "row",
                flexWrap: "nowrap",
                "& .MuiToggleButton-root": {
                  flex: 1,
                  minWidth: 0,
                  py: 1,
                  px: 2,
                  justifyContent: "flex-start",
                  textTransform: "none",
                  borderColor: "primary.main",
                  color: "primary.main",
                  textAlign: "left",
                  alignItems: "center",
                  typography: "caption",
                  lineHeight: 1.2,
                  position: "relative",
                  "&.Mui-selected": {
                    bgcolor: "primary.main",
                    color: "primary.contrastText",
                    borderColor: "primary.main",
                    "&:hover": { bgcolor: "primary.dark" },
                  },
                  "&:hover": { borderColor: "primary.main" },
                  "&:first-of-type": {
                    borderRight: "none",
                  },
                  "&:last-of-type": {
                    borderLeft: "none",
                  },
                },
              }}
            >
              {RATE_MODEL_OPTIONS.map((o) => (
                <ToggleButton key={o.value} value={o.value} aria-label={o.value}>
                  {rateModel === o.value && <SelectedBadge />}
                  <Box sx={{ display: "flex", alignItems: "center", width: "100%" }}>
                    <Typography
                      variant="caption"
                      noWrap
                      sx={{ minWidth: 0, flex: 1 }}
                    >
                      {t(`${ns}.${o.labelKey}` as any)}
                    </Typography>
                  </Box>
                </ToggleButton>
              ))}
            </ToggleButtonGroup>
          </Box>

          <Box
            sx={{
              display: "grid",
              gridTemplateColumns: "1fr 1fr",
              gap: 2,
              minWidth: 0,
            }}
          >
            {/* Principal - Valor inicial: local state to avoid focus loss, sync on blur */}
            <PrincipalInput
              name="principal"
              control={methods.control}
              requiredMessage={reqMsg}
              ns={ns}
              t={t}
            />

            {/* Taxa do título (%): default empty, right-aligned, max 2 decimals, no spinner, reset (circle-x) */}
            <Box sx={{ minWidth: 0 }}>
              <FormSectionLabel>{t(`${ns}.contractedRate`)}</FormSectionLabel>
              <Controller
                name="contractedRateValue"
                control={methods.control}
                rules={{
                  required: reqMsg,
                  validate: (v) =>
                    v === "" || (typeof v === "number" && v < 0) ? reqMsg : true,
                }}
                render={({ field, fieldState }) => (
                  <NumericFormat
                    customInput={TextField}
                    fullWidth
                    size="small"
                    error={!!fieldState.error}
                    helperText={fieldState.error?.message}
                    value={field.value === "" ? "" : field.value}
                    onValueChange={(values) =>
                      field.onChange(
                        values.floatValue !== undefined && values.floatValue !== null
                          ? values.floatValue
                          : ""
                      )
                    }
                    decimalScale={2}
                    allowNegative={false}
                    decimalSeparator={contractedRateDecimal}
                    thousandSeparator={contractedRateGroup ?? false}
                    suffix=" %"
                    InputProps={{
                      inputProps: { style: { textAlign: "right" as const } },
                      endAdornment: (
                        <InputAdornment position="end">
                          <IconButton
                            type="button"
                            size="small"
                            aria-label="reset"
                            tabIndex={-1}
                            onClick={() =>
                              methods.setValue("contractedRateValue", "")
                            }
                            sx={{ mr: -0.5, p: 0.25, color: "text.disabled" }}
                          >
                            <CancelIcon fontSize="inherit" sx={{ fontSize: 16 }} />
                          </IconButton>
                        </InputAdornment>
                      ),
                    }}
                  />
                )}
              />
            </Box>
          </Box>

          {/* Dates: MUI DatePicker with app locale (pt-br / en) */}
          <Box
            sx={{
              display: "grid",
              gridTemplateColumns: "1fr 1fr",
              gap: 2,
              minWidth: 0,
            }}
          >
            <DateInputField
              key={startDate || "__empty__"}
              control={control}
              name="startDate"
              label={t(`${ns}.startDate`)}
              placeholder={t(`${ns}.datePlaceholder`)}
              requiredMessage={reqMsg}
              invalidMessage={t(`${ns}.invalidDate`)}
              clearAriaLabel={t(`${ns}.clearDate`)}
              setError={methods.setError}
              clearErrors={methods.clearErrors}
            />

            <DateInputField
              key={endDate || "__empty__"}
              control={control}
              name="endDate"
              label={t(`${ns}.endDate`)}
              placeholder={t(`${ns}.datePlaceholder`)}
              requiredMessage={reqMsg}
              invalidMessage={t(`${ns}.invalidDate`)}
              clearAriaLabel={t(`${ns}.clearDate`)}
              minIso={
                isValidIsoDate(startDate)
                  ? dayjs(startDate).add(1, "day").format("YYYY-MM-DD")
                  : undefined
              }
              setError={methods.setError}
              clearErrors={methods.clearErrors}
            />
          </Box>

          {/* Projeções de mercado: input + slider tied to same value (-100 to 100, 2 decimals) */}
          <Box
            sx={{
              p: 2,
              bgcolor: "action.hover",
              borderRadius: 1,
              border: "1px solid",
              borderColor: "divider",
            }}
          >
            <Box
              sx={{
                display: "flex",
                alignItems: "center",
                justifyContent: "space-between",
                mb: 2,
              }}
            >
              <FormSectionLabel color="text.primary">
                {t(`${ns}.marketProjections`)}
              </FormSectionLabel>
              <Button
                type="button"
                size="small"
                variant="outlined"
                onClick={() => {
                  setValue("cdiAnnualPercent", cdiPercent);
                  setValue("ipcaAnnualPercent", ipcaPercent);
                }}
                startIcon={<RestartAltIcon sx={{ fontSize: 16 }} />}
                sx={{ textTransform: "none" }}
              >
                {t(`${ns}.resetProjectionsToDefault`)}
              </Button>
            </Box>
            <Box sx={{ display: "flex", flexDirection: "column", gap: 2.5 }}>
              <Controller
                name="cdiAnnualPercent"
                control={methods.control}
                rules={{
                  required: reqMsg,
                  min: { value: PROJECTION_MIN, message: reqMsg },
                  max: { value: PROJECTION_MAX, message: reqMsg },
                }}
                render={({ field, fieldState }) => (
                  <Box>
                    <Box
                      sx={{
                        display: "flex",
                        justifyContent: "space-between",
                        alignItems: "center",
                        mb: 0.5,
                      }}
                    >
                      <Typography variant="caption" color="text.secondary">
                        {t(`${ns}.expectationCdi`)}
                      </Typography>
                      <NumericFormat
                        customInput={TextField}
                        fullWidth={false}
                        size="small"
                        error={!!fieldState.error}
                        value={field.value}
                        onValueChange={(values) =>
                          field.onChange(values.floatValue ?? 0)
                        }
                        decimalScale={PROJECTION_DECIMALS}
                        allowNegative
                        suffix=" %"
                        sx={{ width: 100 }}
                        InputProps={{
                          inputProps: {
                            style: { textAlign: "right" as const },
                          },
                        }}
                      />
                    </Box>
                    <MuiSlider
                      value={Math.min(
                        PROJECTION_SLIDER_MAX,
                        Math.max(PROJECTION_SLIDER_MIN, field.value)
                      )}
                      min={PROJECTION_SLIDER_MIN}
                      max={PROJECTION_SLIDER_MAX}
                      step={0.25}
                      valueLabelDisplay="auto"
                      valueLabelFormat={(v) => `${v}%`}
                      onChange={(_, value) =>
                        field.onChange(
                          Array.isArray(value) ? value[0] : value
                        )
                      }
                      sx={{ color: "primary.main", mt: 0.5 }}
                    />
                  </Box>
                )}
              />
              <Controller
                name="ipcaAnnualPercent"
                control={methods.control}
                rules={{
                  required: reqMsg,
                  min: { value: PROJECTION_MIN, message: reqMsg },
                  max: { value: PROJECTION_MAX, message: reqMsg },
                }}
                render={({ field, fieldState }) => (
                  <Box>
                    <Box
                      sx={{
                        display: "flex",
                        justifyContent: "space-between",
                        alignItems: "center",
                        mb: 0.5,
                      }}
                    >
                      <Typography variant="caption" color="text.secondary">
                        {t(`${ns}.expectationIpca`)}
                      </Typography>
                      <NumericFormat
                        customInput={TextField}
                        fullWidth={false}
                        size="small"
                        error={!!fieldState.error}
                        value={field.value}
                        onValueChange={(values) =>
                          field.onChange(values.floatValue ?? 0)
                        }
                        decimalScale={PROJECTION_DECIMALS}
                        allowNegative
                        suffix=" %"
                        sx={{ width: 100 }}
                        InputProps={{
                          inputProps: {
                            style: { textAlign: "right" as const },
                          },
                        }}
                      />
                    </Box>
                    <MuiSlider
                      value={Math.min(
                        PROJECTION_SLIDER_MAX,
                        Math.max(PROJECTION_SLIDER_MIN, field.value)
                      )}
                      min={PROJECTION_SLIDER_MIN}
                      max={PROJECTION_SLIDER_MAX}
                      step={0.25}
                      valueLabelDisplay="auto"
                      valueLabelFormat={(v) => `${v}%`}
                      onChange={(_, value) =>
                        field.onChange(
                          Array.isArray(value) ? value[0] : value
                        )
                      }
                      sx={{ color: "primary.main", mt: 0.5 }}
                    />
                  </Box>
                )}
              />
            </Box>
          </Box>

          <Button
            type="submit"
            fullWidth
            variant="contained"
            size="large"
            sx={{
              py: 2,
              fontWeight: 800,
              textTransform: "uppercase",
                fontSize: 12,
              mt: 1,
            }}
          >
            {t(`${ns}.simulate`)}
          </Button>
          </Box>
      </FormProvider>
    </Box>
  );
}
