"use client";

import { useEffect, useState, useRef } from "react";
import { useForm, FormProvider, Controller, useWatch } from "react-hook-form";
import { useLazyQuery } from "@apollo/client/react";
import {
  Box,
  Typography,
  TextField,
  Button,
  IconButton,
  InputAdornment,
  Slider as MuiSlider,
  useTheme,
} from "@mui/material";
import { DatePicker } from "@mui/x-date-pickers/DatePicker";
import { LocalizationProvider } from "@mui/x-date-pickers/LocalizationProvider";
import { AdapterDayjs } from "@mui/x-date-pickers/AdapterDayjs";
import dayjs, { Dayjs } from "dayjs";
import "dayjs/locale/pt-br";
import CheckCircleIcon from "@mui/icons-material/CheckCircle";
import CancelIcon from "@mui/icons-material/Cancel";
import RestartAltIcon from "@mui/icons-material/RestartAlt";
import { NumericFormat } from "react-number-format";
import { useTranslation } from "@/shared/i18n";
import { investmentsTranslations } from "@/shared/i18n/domains/investments";
import i18n from "@/shared/i18n/config";
import { getLocaleSeparators } from "@/shared/utils/locale";
import { useEconomicIndicatorsForSimulator } from "../hooks/useEconomicIndicatorsForSimulator";
import { PrincipalInput } from "./PrincipalInput";
import { FixedIncomeComparisonDocument } from "../api/queries.generated";
import type { FixedIncomeProductGroup, FixedIncomeRateModel } from "@/lib/graphql/types/__generated__/graphql";
import type { FixedIncomeComparisonQuery } from "../api/queries.generated";

const DEFAULT_PRINCIPAL = 50000;
const DEFAULT_CONTRACTED_RATE = 115;

function percentToBps(value: number): number {
  return Math.round(value * 100);
}

function defaultEndDate(startDate: string): string {
  return startDate ? dayjs(startDate).add(1, "year").format("YYYY-MM-DD") : "";
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

/** Map i18n language to dayjs adapter locale (pt / pt-BR -> pt-br, default en). */
function getDateAdapterLocale(): string {
  const lang = i18n.language?.toLowerCase() ?? "pt";
  return lang.startsWith("pt") ? "pt-br" : "en";
}

/** Map i18n language to number-format locale (for decimal/thousand separators). */
function getNumberFormatLocale(): string {
  const lang = i18n.language?.toLowerCase() ?? "pt";
  return lang.startsWith("pt") ? "pt-BR" : "en-US";
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
  const theme = useTheme();

  const {
    cdiPercent,
    ipcaPercent,
    loading: indicatorsLoading,
  } = useEconomicIndicatorsForSimulator();

  const containerRef = useRef<HTMLElement>(null);
  const [isCompact, setIsCompact] = useState(false);

  useEffect(() => {
    if (!containerRef.current) return;
    const ro = new ResizeObserver((entries) => {
      for (const entry of entries) {
        setIsCompact(entry.contentRect.width < 390);
      }
    });
    ro.observe(containerRef.current);
    return () => ro.disconnect();
  }, []);

  const methods = useForm<FixedIncomeSimulatorFormValues>({
    defaultValues: {
      productGroup: "TAXABLE_IR" as FixedIncomeProductGroup,
      principal: DEFAULT_PRINCIPAL,
      rateModel: "CDI" as FixedIncomeRateModel,
      contractedRateValue: DEFAULT_CONTRACTED_RATE,
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

  // Disable automatic search on initial load
  // const initialRunDone = useRef(false);
  // useEffect(() => {
  //   if (indicatorsLoading || initialRunDone.current) return;
  //   const values = getValues();
  //   const principal =
  //     typeof values.principal === "number" && values.principal > 0
  //       ? values.principal
  //       : DEFAULT_PRINCIPAL;
  //   const endDate = values.endDate || defaultEndDate(values.startDate || today);
  //   if (!endDate) return;
  //   initialRunDone.current = true;
  //   runComparison({
  //     variables: {
  //       input: {
  //         productGroup: values.productGroup,
  //         principal,
  //         startDate: values.startDate || today,
  //         endDate,
  //         rateModel: values.rateModel,
  //         contractedRateBps: percentToBps(
  //           values.contractedRateValue === ""
  //             ? DEFAULT_CONTRACTED_RATE
  //             : Number(values.contractedRateValue)
  //         ),
  //         cdiAnnualRateBps: percentToBps(cdiPercent),
  //         ipcaAnnualRateBps: percentToBps(ipcaPercent),
  //       },
  //     },
  //   });
  // }, [
  //   indicatorsLoading,
  //   cdiPercent,
  //   ipcaPercent,
  //   today,
  //   getValues,
  //   runComparison,
  // ]);

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
        boxShadow: 2,
        border: "1px solid",
        borderColor: "divider",
        width: "100%",
        maxWidth: 440,
      }}
      data-purpose="simulador-form"
    >
      <Typography variant="h6" component="h2" sx={{ mb: 3 }}>
        {t(`${ns}.simulatorTitle`)}
      </Typography>

      <FormProvider {...methods}>
        <LocalizationProvider
          dateAdapter={AdapterDayjs}
          adapterLocale={getDateAdapterLocale()}
        >
          <Box
            component="form"
            onSubmit={handleSubmit(handleFormSubmit)}
            sx={{ display: "flex", flexDirection: "column", gap: 3 }}
          >
          {/* Product group - Tipo de Ativo */}
          <Box>
            <Typography
              variant="caption"
              sx={{
                display: "block",
                fontWeight: 700,
                textTransform: "uppercase",
                letterSpacing: "0.05em",
                color: "text.secondary",
                mb: 2,
              }}
            >
              {t(`${ns}.productGroup`)}
            </Typography>
            <Box sx={{ display: "flex", flexDirection: "column", gap: 3 }}>
              <Box>
                <Typography
                  variant="body2"
                  sx={{ color: "text.secondary", display: "block", mb: 0.5 }}
                >
                  {t(`${ns}.incidesIrDescription`)}
                </Typography>
                <Button
                  type="button"
                  fullWidth
                  variant={productGroup === "TAXABLE_IR" ? "contained" : "outlined"}
                  onClick={() => methods.setValue("productGroup", "TAXABLE_IR" as FixedIncomeProductGroup)}
                  sx={{
                    py: 2,
                    px: 2,
                    justifyContent: "flex-start",
                    gap: 1.5,
                    textTransform: "none",
                    fontWeight: 700,
                    borderWidth: 2,
                    textAlign: "left",
                    alignItems: "center",
                    "&:hover": { borderWidth: 2 },
                  }}
                >
                  {t(`${ns}.taxableProductLabel`)}
                  {productGroup === "TAXABLE_IR" && <CheckCircleIcon sx={{ fontSize: 20 }} />}
                </Button>
              </Box>
              <Box>
                <Typography
                  variant="body2"
                  sx={{ color: "text.secondary", display: "block", mb: 0.5 }}
                >
                  {t(`${ns}.exemptIrDescription`)}
                </Typography>
                <Button
                  type="button"
                  fullWidth
                  variant={productGroup === "EXEMPT_IR" ? "contained" : "outlined"}
                  onClick={() => methods.setValue("productGroup", "EXEMPT_IR" as FixedIncomeProductGroup)}
                  sx={{
                    py: 2,
                    px: 2,
                    justifyContent: "flex-start",
                    gap: 1.5,
                    textTransform: "none",
                    fontWeight: 700,
                    borderWidth: 2,
                    textAlign: "left",
                    alignItems: "center",
                    "&:hover": { borderWidth: 2 },
                  }}
                >
                  {t(`${ns}.exemptProductLabel`)}
                  {productGroup === "EXEMPT_IR" && <CheckCircleIcon sx={{ fontSize: 20 }} />}
                </Button>
              </Box>
            </Box>
          </Box>

          {/* Principal - Valor inicial: local state to avoid focus loss, sync on blur */}
          <PrincipalInput
            name="principal"
            control={methods.control}
            requiredMessage={reqMsg}
            ns={ns}
            t={t}
          />

          {/* Indexador - Rate model (same style as Tipo de Ativo, single line) */}
          <Box>
            <Typography
              variant="caption"
              sx={{
                display: "block",
                fontWeight: 700,
                textTransform: "uppercase",
                letterSpacing: "0.05em",
                color: "text.secondary",
                mb: 2,
              }}
            >
              {t(`${ns}.rateModel`)}
            </Typography>
            <Box
              sx={{
                display: "flex",
                flexDirection: isCompact ? "column" : "row",
                flexWrap: "wrap",
                gap: 1.5,
              }}
            >
              {RATE_MODEL_OPTIONS.map((o) => (
                <Button
                  key={o.value}
                  type="button"
                  variant={rateModel === o.value ? "contained" : "outlined"}
                  onClick={() => methods.setValue("rateModel", o.value)}
                  sx={{
                    flex: 1,
                    minWidth: 0,
                    py: 2,
                    px: 2,
                    justifyContent: "flex-start",
                    gap: 1.5,
                    textTransform: "none",
                    fontWeight: 700,
                    borderWidth: 2,
                    textAlign: "left",
                    alignItems: "center",
                    "&:hover": { borderWidth: 2 },
                  }}
                >
                  {t(`${ns}.${o.labelKey}` as any)}
                  {rateModel === o.value && (
                    <CheckCircleIcon sx={{ fontSize: 20 }} />
                  )}
                </Button>
              ))}
            </Box>
          </Box>

          {/* Taxa do título (%): default empty, right-aligned, max 2 decimals, no spinner, reset (circle-x) */}
          <Box>
            <Typography
              variant="caption"
              sx={{
                display: "block",
                fontWeight: 700,
                textTransform: "uppercase",
                letterSpacing: "0.05em",
                color: "text.secondary",
                mb: 1,
              }}
            >
              {t(`${ns}.contractedRate`)}
            </Typography>
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
                          onClick={() =>
                            methods.setValue("contractedRateValue", "")
                          }
                          sx={{ mr: -0.5 }}
                        >
                          <CancelIcon fontSize="small" />
                        </IconButton>
                      </InputAdornment>
                    ),
                  }}
                />
              )}
            />
          </Box>

          {/* Dates: MUI DatePicker with app locale (pt-br / en) */}
          <Box
            sx={{
              display: "grid",
              gridTemplateColumns: isCompact ? "1fr" : "1fr 1fr",
              gap: 2,
              minWidth: 0,
            }}
          >
            <Box sx={{ minWidth: 0 }}>
              <Typography
                variant="caption"
                sx={{
                  display: "block",
                  fontWeight: 700,
                  textTransform: "uppercase",
                  letterSpacing: "0.05em",
                  color: "text.secondary",
                  mb: 1,
                }}
              >
                {t(`${ns}.startDate`)}
              </Typography>
              <Controller
                name="startDate"
                control={methods.control}
                rules={{ required: reqMsg }}
                render={({ field, fieldState }) => (
                  <DatePicker
                    value={field.value ? dayjs(field.value) : null}
                    onChange={(v: Dayjs | null) =>
                      field.onChange(v ? v.format("YYYY-MM-DD") : "")
                    }
                    slotProps={{
                      textField: {
                        fullWidth: true,
                        size: "small",
                        error: !!fieldState.error,
                        helperText: fieldState.error?.message,
                        sx: { minWidth: 0 },
                      },
                    }}
                  />
                )}
              />
            </Box>
            <Box sx={{ minWidth: 0 }}>
              <Typography
                variant="caption"
                sx={{
                  display: "block",
                  fontWeight: 700,
                  textTransform: "uppercase",
                  letterSpacing: "0.05em",
                  color: "text.secondary",
                  mb: 1,
                }}
              >
                {t(`${ns}.endDate`)}
              </Typography>
              <Controller
                name="endDate"
                control={methods.control}
                rules={{
                  required: reqMsg,
                  validate: (v) => {
                    if (!v || !startDate) return true;
                    const end = dayjs(v);
                    const minEnd = dayjs(startDate).add(1, "day");
                    return end.isBefore(minEnd) ? reqMsg : true;
                  },
                }}
                render={({ field, fieldState }) => (
                  <DatePicker
                    value={field.value ? dayjs(field.value) : null}
                    onChange={(v: Dayjs | null) =>
                      field.onChange(v ? v.format("YYYY-MM-DD") : "")
                    }
                    minDate={
                      startDate
                        ? dayjs(startDate).add(1, "day")
                        : undefined
                    }
                    slotProps={{
                      textField: {
                        fullWidth: true,
                        size: "small",
                        error: !!fieldState.error,
                        helperText: fieldState.error?.message,
                        sx: { minWidth: 0 },
                      },
                    }}
                  />
                )}
              />
            </Box>
          </Box>

          {/* Projeções de mercado: input + slider tied to same value (-100 to 100, 2 decimals) */}
          <Box
            sx={{
              p: 2,
              bgcolor: "action.hover",
              borderRadius: 2,
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
              <Typography
                variant="caption"
                sx={{
                  fontWeight: 800,
                  textTransform: "uppercase",
                  color: "text.primary",
                }}
              >
                {t(`${ns}.marketProjections`)}
              </Typography>
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
              mt: 1,
              boxShadow: theme.shadows[4],
            }}
          >
            {t(`${ns}.simulate`)}
          </Button>
        </Box>
        </LocalizationProvider>
      </FormProvider>
    </Box>
  );
}
