'use client';

import React, { useState, useCallback } from 'react';
import { useQuery, useLazyQuery, useMutation } from '@apollo/client/react';
import {
  Box,
  Typography,
  TextField,
  Select,
  MenuItem,
  FormControl,
  InputLabel,
  FormHelperText,
  InputAdornment,
  RadioGroup,
  FormControlLabel,
  Radio,
  Divider,
  Button,
  Alert,
  CircularProgress,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogContentText,
  DialogActions,
  Autocomplete,
  Chip,
  Stack,
  alpha,
  useTheme,
} from '@mui/material';
import { Drawer } from '@/shared/components/ui/layout/Drawer';
import { TagsInput } from '@/shared/components/ui/forms/form/TagsInput';
import {
  TRANSACTION_CATEGORIES,
  MERCHANTS,
  TRANSACTION_TAGS,
} from '@/lib/graphql/operations/banking/queries';
import {
  PreviewTransactionRuleDocument,
  type PreviewTransactionRuleQuery,
  type PreviewTransactionRuleQueryVariables,
} from '@/lib/graphql/operations/banking/queries.generated';
import {
  CREATE_TRANSACTION_RULE,
  UPDATE_TRANSACTION_RULE,
} from '@/lib/graphql/operations/banking/mutations';
import { CategoryApplicableTo, PatternType } from '@/lib/graphql/types/__generated__/graphql';
import { extractErrorMessage } from '@/shared/utils/error';
import { useToast } from '@/shared/providers';
import type { TransactionRule } from './types';
import { CategoryIcon } from '../category/CategoryIcon';
import { CategoryPicker } from '../category/CategoryPicker';
import { Logo } from '@/shared/components/ui/primitives';

interface TransactionCategory {
  id: string;
  name: string;
  icon?: string | null;
  color?: string | null;
  applicableTo: CategoryApplicableTo;
}

interface CategoriesData {
  transactionCategories: TransactionCategory[];
}

interface Merchant {
  id: string;
  name: string;
  domain?: string | null;
  isSystem: boolean;
  logoAsset?: { id: string; publicUrl?: string | null } | null;
}

interface MerchantsData {
  merchants: Merchant[];
}

interface TransactionTagsData {
  transactionTags: { tag: string }[];
}

interface TransactionRuleDrawerProps {
  open: boolean;
  rule: TransactionRule | null;
  initialPattern?: string;
  initialPatternType?: PatternType;
  initialApplicableTo?: CategoryApplicableTo;
  onClose: () => void;
  onSaved: () => void;
}

const PATTERN_TYPE_OPTIONS = [
  { value: PatternType.Contains, label: 'Contém' },
  { value: PatternType.StartsWith, label: 'Começa com' },
  { value: PatternType.Exact, label: 'Exato' },
  { value: PatternType.Regex, label: 'Regex' },
];

const APPLICABLE_TO_OPTIONS = [
  { value: CategoryApplicableTo.Any, label: 'Qualquer' },
  { value: CategoryApplicableTo.Income, label: 'Receita' },
  { value: CategoryApplicableTo.Expense, label: 'Despesa' },
  { value: CategoryApplicableTo.Transfer, label: 'Transferência' },
];

interface FormState {
  pattern: string;
  patternType: PatternType;
  applicableTo: CategoryApplicableTo;
  categoryId: string | null;
  merchantId: string | null;
  descriptionOverride: string;
  tagsOverride: string[];
  priority: string;
}

const defaultForm = (): FormState => ({
  pattern: '',
  patternType: PatternType.Contains,
  applicableTo: CategoryApplicableTo.Any,
  categoryId: null,
  merchantId: null,
  descriptionOverride: '',
  tagsOverride: [],
  priority: '40',
});

function normalizeTags(values: string[]): string[] {
  return [...new Set(values.map((value) => value.trim()).filter(Boolean))].sort();
}

function upperFirst(value: string): string {
  if (!value) return value;
  const trimmedStart = value.trimStart();
  if (!trimmedStart) return value;
  const prefixLength = value.length - trimmedStart.length;
  return `${value.slice(0, prefixLength)}${trimmedStart.charAt(0).toLocaleUpperCase()}${trimmedStart.slice(1)}`;
}

function formFromRule(rule: TransactionRule): FormState {
  return {
    pattern: rule.pattern,
    patternType: rule.patternType,
    applicableTo: rule.applicableTo,
    categoryId: rule.categoryId ?? null,
    merchantId: rule.merchantId ?? null,
    descriptionOverride: rule.descriptionOverride ?? '',
    tagsOverride: normalizeTags(rule.tagsOverride ?? []),
    priority: String(rule.priority),
  };
}

function TransactionRuleDrawerInner({
  open,
  rule,
  initialPattern,
  initialPatternType,
  initialApplicableTo,
  onClose,
  onSaved,
}: TransactionRuleDrawerProps) {
  const toast = useToast();
  const theme = useTheme();
  const isEdit = !!rule;

  const [form, setForm] = useState<FormState>(() => {
    if (rule) return formFromRule(rule);
    const next = defaultForm();
    if (typeof initialPattern === 'string') next.pattern = initialPattern;
    if (initialPatternType) next.patternType = initialPatternType;
    if (initialApplicableTo) next.applicableTo = initialApplicableTo;
    return next;
  });
  const [isDirty, setIsDirty] = useState(false);
  const [discardDialogOpen, setDiscardDialogOpen] = useState(false);
  const [previewSample, setPreviewSample] = useState('');
  const [showPreviewField, setShowPreviewField] = useState(false);
  const [categoryPickerAnchorEl, setCategoryPickerAnchorEl] = useState<HTMLElement | null>(null);
  const [submitAttempted, setSubmitAttempted] = useState(false);

  const { data: categoriesData } = useQuery<CategoriesData>(TRANSACTION_CATEGORIES, {
    skip: !open,
  });

  const { data: merchantsData } = useQuery<MerchantsData>(MERCHANTS, {
    skip: !open,
  });

  const { data: transactionTagsData } = useQuery<TransactionTagsData>(TRANSACTION_TAGS, {
    skip: !open,
  });

  const [runPreview, { loading: previewLoading, data: previewData, error: previewApolloError }] = useLazyQuery<
    PreviewTransactionRuleQuery,
    PreviewTransactionRuleQueryVariables
  >(PreviewTransactionRuleDocument, {
    fetchPolicy: 'network-only',
  });

  const [createRule, { loading: createLoading }] = useMutation(CREATE_TRANSACTION_RULE, {
    onCompleted: () => {
      toast.success('Regra salva.');
      onSaved();
    },
    onError: (err) => toast.error(extractErrorMessage(err, 'Não foi possível salvar a regra')),
  });

  const [updateRule, { loading: updateLoading }] = useMutation(UPDATE_TRANSACTION_RULE, {
    onCompleted: () => {
      toast.success('Regra salva.');
      onSaved();
    },
    onError: (err) => toast.error(extractErrorMessage(err, 'Não foi possível salvar a regra')),
  });

  const saving = createLoading || updateLoading;

  const setField = useCallback(<K extends keyof FormState>(key: K, value: FormState[K]) => {
    setForm((prev) => ({ ...prev, [key]: value }));
    setIsDirty(true);
  }, []);

  const handleTest = useCallback(() => {
    setShowPreviewField(true);
    if (previewSample.trim()) {
      runPreview({
        variables: { pattern: form.pattern, patternType: form.patternType, sample: previewSample },
      });
    }
  }, [form.pattern, form.patternType, previewSample, runPreview]);

  const trimmedPattern = form.pattern.trim();
  const trimmedDescriptionOverride = form.descriptionOverride.trim();
  const normalizedTagsOverride = normalizeTags(form.tagsOverride);

  const hasAction =
    !!form.categoryId ||
    !!form.merchantId ||
    trimmedDescriptionOverride.length > 0 ||
    normalizedTagsOverride.length > 0;

  const canSave = trimmedPattern.length > 0 && hasAction && !saving;

  const handleSave = useCallback(() => {
    setSubmitAttempted(true);
    if (saving) return;
    if (!trimmedPattern) {
      toast.error('Informe o padrão.');
      return;
    }
    if (!hasAction) {
      toast.error('Configure pelo menos uma ação.');
      return;
    }

    const priority = parseInt(form.priority, 10);
    const input = {
      pattern: trimmedPattern,
      patternType: form.patternType,
      applicableTo: form.applicableTo,
      categoryId: form.categoryId ?? undefined,
      merchantId: form.merchantId ?? undefined,
      descriptionOverride: trimmedDescriptionOverride || undefined,
      tagsOverride: normalizedTagsOverride.length > 0 ? normalizedTagsOverride : undefined,
      priority: isNaN(priority) || priority < 1 ? 40 : priority,
    };

    if (isEdit && rule) {
      updateRule({
        variables: {
          id: rule.id,
          input,
        },
      });
    } else {
      createRule({ variables: { input } });
    }
  }, [
    createRule,
    form,
    hasAction,
    isEdit,
    normalizedTagsOverride,
    rule,
    saving,
    toast,
    trimmedDescriptionOverride,
    trimmedPattern,
    updateRule,
  ]);

  const handleClose = useCallback(() => {
    if (isDirty) {
      setDiscardDialogOpen(true);
    } else {
      onClose();
    }
  }, [isDirty, onClose]);

  const { transactionCategories: categories = [] } = categoriesData ?? {};
  const selectedCategory = categories.find((c) => c.id === form.categoryId) ?? null;

  const merchants = merchantsData?.merchants ?? [];
  const selectedMerchant = merchants.find((m) => m.id === form.merchantId) ?? null;
  const tagOptions = transactionTagsData?.transactionTags?.map((t) => t.tag).filter(Boolean) ?? [];
  const shouldShowPreview = showPreviewField && !!previewSample.trim() && !!form.pattern.trim();
  const previewError = shouldShowPreview
    ? previewApolloError
      ? extractErrorMessage(previewApolloError, 'Não foi possível testar a regra')
      : null
    : null;
  const previewResult = shouldShowPreview ? previewData?.previewTransactionRule ?? null : null;
  const categoryPickerType =
    form.applicableTo === CategoryApplicableTo.Income
      ? 'CREDIT'
      : form.applicableTo === CategoryApplicableTo.Expense
        ? 'DEBIT'
        : form.applicableTo === CategoryApplicableTo.Transfer
          ? 'TRANSFER'
          : 'ANY';

  return (
    <>
      <Drawer open={open} onClose={handleClose} anchor="right" size="md">
        <Drawer.Header title={isEdit ? 'Editar Regra' : 'Nova Regra'} />

        <Drawer.Body>
          {isEdit && rule?.isImplicit && (
            <Alert severity="info" sx={{ mb: 3 }}>
              Esta regra foi aprendida automaticamente. Ao salvar as alterações, ela se tornará uma regra explícita.
            </Alert>
          )}

          <Typography variant="overline" color="text.secondary" sx={{ display: 'block', mb: 2 }}>
            SE a descrição…
          </Typography>

          <FormControl fullWidth sx={{ mb: 2 }}>
            <InputLabel>Tipo de comparação</InputLabel>
            <Select
              label="Tipo de comparação"
              value={form.patternType}
              onChange={(e) => setField('patternType', e.target.value as PatternType)}
            >
              {PATTERN_TYPE_OPTIONS.map((opt) => (
                <MenuItem key={opt.value} value={opt.value}>
                  {opt.label}
                </MenuItem>
              ))}
            </Select>
            {form.patternType === PatternType.Regex && (
              <FormHelperText>Use expressões regulares Java/Kotlin. Ex: ^SAL[\\s/]</FormHelperText>
            )}
          </FormControl>

          <Box sx={{ display: 'flex', gap: 1, mb: 1 }}>
            <TextField
              fullWidth
              label="Texto comparativo"
              value={form.pattern}
              onChange={(e) => setField('pattern', e.target.value)}
              placeholder="Ex: IFOOD"
              inputProps={{ sx: { fontFamily: 'monospace' } }}
              required
              error={submitAttempted && trimmedPattern.length === 0}
              helperText={submitAttempted && trimmedPattern.length === 0 ? 'Campo obrigatório' : undefined}
            />
            <Button
              variant="outlined"
              onClick={handleTest}
              disabled={!form.pattern.trim()}
              sx={{ flexShrink: 0 }}
            >
              Testar
            </Button>
          </Box>

          {showPreviewField && (
            <Box sx={{ mt: 2, mb: 3 }}>
              <TextField
                fullWidth
                label="Testar com"
                placeholder="Digite uma descrição de exemplo…"
                value={previewSample}
                onChange={(e) => setPreviewSample(e.target.value)}
                autoFocus
              />
              {previewLoading && (
                <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, mt: 1 }}>
                  <CircularProgress size={14} />
                  <Typography variant="caption" color="text.secondary">Testando…</Typography>
                </Box>
              )}
              {!previewLoading && previewError && (
                <Alert severity="error" sx={{ mt: 1, py: 0.5 }}>{previewError}</Alert>
              )}
              {!previewLoading && !previewError && previewResult !== null && previewSample.trim() && (
                <Alert
                  severity={previewResult ? 'success' : 'warning'}
                  sx={{ mt: 1, py: 0.5 }}
                  icon={false}
                >
                  {previewResult ? '✓ Corresponde' : '✗ Não corresponde'}
                </Alert>
              )}
            </Box>
          )}

          <Box sx={{ mb: 3 }}>
            <Typography variant="caption" color="text.secondary" sx={{ mb: 0.5, display: 'block' }}>
              Aplicar para
            </Typography>
            <RadioGroup
              row
              value={form.applicableTo}
              onChange={(e) => setField('applicableTo', e.target.value as CategoryApplicableTo)}
            >
              {APPLICABLE_TO_OPTIONS.map((opt) => (
                <FormControlLabel
                  key={opt.value}
                  value={opt.value}
                  control={<Radio />}
                  label={<Typography variant="body2">{opt.label}</Typography>}
                />
              ))}
            </RadioGroup>
          </Box>

          <Divider sx={{ mb: 3 }} />

          <Typography variant="overline" color="text.secondary" sx={{ display: 'block' }}>
            ENTÃO…
          </Typography>

          {!hasAction && (
            <Alert severity="info" sx={{ my: 2 }}>
              Configure pelo menos uma ação (nova descrição, categoria, tags ou comerciante).
            </Alert>
          )}

          <TextField
            fullWidth
            label="Nova descrição"
            placeholder="Deixe em branco para manter o original"
            value={form.descriptionOverride}
            onChange={(e) => setField('descriptionOverride', e.target.value)}
            sx={{ mb: 2 }}
          />

          <Box sx={{ mb: 2 }}>
            {selectedCategory ? (
              <Chip
                clickable
                onClick={(e) => setCategoryPickerAnchorEl(e.currentTarget)}
                label={
                  <Stack direction="row" spacing={1} alignItems="center" sx={{ minWidth: 0 }}>
                    <CategoryIcon
                      iconName={selectedCategory.icon}
                      color={selectedCategory.color ?? undefined}
                      size={18}
                      sx={{ flexShrink: 0 }}
                    />
                    <Typography
                      variant="body2"
                      title={selectedCategory.name}
                      sx={{
                        overflow: 'hidden',
                        textOverflow: 'ellipsis',
                        whiteSpace: 'nowrap',
                        minWidth: 0,
                        maxWidth: '40ch',
                      }}
                    >
                      {selectedCategory.name}
                    </Typography>
                  </Stack>
                }
                variant="filled"
                sx={{
                  height: 56,
                  width: '100%',
                  borderRadius: `${theme.shape.borderRadius}px`,
                  color: selectedCategory.color || 'text.primary',
                  bgcolor: selectedCategory.color ? alpha(selectedCategory.color, 0.1) : 'action.selected',
                  borderColor: selectedCategory.color ? alpha(selectedCategory.color, 0.3) : 'divider',
                  borderWidth: 1,
                  borderStyle: 'solid',
                  justifyContent: 'flex-start',
                  '&:hover': {
                    bgcolor: selectedCategory.color ? alpha(selectedCategory.color, 0.2) : 'action.hover',
                  },
                  transition: 'background-color 0.15s ease',
                  '& .MuiChip-label': { minWidth: 0, maxWidth: '100%', width: '100%', py: 0 },
                }}
              />
            ) : (
              <Chip
                clickable
                onClick={(e) => setCategoryPickerAnchorEl(e.currentTarget)}
                label={
                  <Typography variant="body2" sx={{ opacity: 0.65 }}>
                    Categoria
                  </Typography>
                }
                variant="outlined"
                sx={{
                  height: 56,
                  width: '100%',
                  borderRadius: `${theme.shape.borderRadius}px`,
                  borderStyle: 'dashed',
                  color: 'text.secondary',
                  bgcolor: alpha(theme.palette.text.primary, 0.02),
                  boxShadow: `inset 0 0 0 1px ${alpha(theme.palette.text.primary, 0.06)}`,
                  justifyContent: 'flex-start',
                  '&:hover': {
                    borderColor: 'primary.main',
                    color: 'primary.main',
                    bgcolor: alpha(theme.palette.primary.main, 0.05),
                  },
                  transition: 'background-color 0.15s ease',
                  '& .MuiChip-label': { minWidth: 0, maxWidth: '100%', width: '100%', py: 0 },
                }}
              />
            )}
          </Box>

          <CategoryPicker
            value={form.categoryId}
            onChange={(categoryId) => setField('categoryId', categoryId)}
            creditDebitType={categoryPickerType}
            anchorEl={categoryPickerAnchorEl}
            onClose={() => setCategoryPickerAnchorEl(null)}
          />

          <Box sx={{ mb: 2 }}>
            <TagsInput
              label="Tags a adicionar"
              value={normalizedTagsOverride}
              onChange={(tags) => setField('tagsOverride', normalizeTags(tags))}
              options={tagOptions}
              placeholder="Digite e pressione Enter"
              size="medium"
            />
          </Box>

          <Autocomplete
            options={merchants}
            value={selectedMerchant}
            onChange={(_, value) => setField('merchantId', value?.id ?? null)}
            getOptionLabel={(opt) => upperFirst(opt.name)}
            renderInput={(params) => (
              <TextField
                {...params}
                label="Comerciante"
                placeholder="Nenhum"
                InputProps={{
                  ...params.InputProps,
                  startAdornment: selectedMerchant?.logoAsset?.publicUrl ? (
                    <>
                      <InputAdornment position="start" sx={{ mr: 1 }}>
                        <Logo
                          src={selectedMerchant.logoAsset.publicUrl}
                          alt={upperFirst(selectedMerchant.name)}
                          size="sm"
                        />
                      </InputAdornment>
                      {params.InputProps.startAdornment}
                    </>
                  ) : (
                    params.InputProps.startAdornment
                  ),
                }}
              />
            )}
            renderOption={(props, option) => (
              <Box component="li" {...props} sx={{ gap: 1.5, alignItems: 'center', py: 1 }}>
                {option.logoAsset?.publicUrl ? (
                  <Logo
                    src={option.logoAsset.publicUrl}
                    alt={upperFirst(option.name)}
                    size="md"
                    sx={{ flexShrink: 0 }}
                  />
                ) : (
                  <Box sx={{ width: 30, height: 30, flexShrink: 0 }} />
                )}
                <Box sx={{ minWidth: 0, flex: 1 }}>
                  <Typography
                    variant="body2"
                    sx={{
                      fontSize: '0.95rem',
                      fontWeight: 600,
                      lineHeight: 1.2,
                      overflow: 'hidden',
                      textOverflow: 'ellipsis',
                      whiteSpace: 'nowrap',
                    }}
                    title={upperFirst(option.name)}
                  >
                    {upperFirst(option.name)}
                  </Typography>
                  {option.domain && (
                    <Typography
                      variant="caption"
                      color="text.secondary"
                      sx={{
                        display: 'block',
                        fontSize: '0.8rem',
                        lineHeight: 1.2,
                        overflow: 'hidden',
                        textOverflow: 'ellipsis',
                        whiteSpace: 'nowrap',
                      }}
                      title={option.domain}
                    >
                      {option.domain}
                    </Typography>
                  )}
                </Box>
              </Box>
            )}
            sx={{ mb: 2 }}
          />

          <TextField
            label="Prioridade"
            type="number"
            value={form.priority}
            onChange={(e) => setField('priority', e.target.value)}
            helperText="Menor número = aplicada primeiro"
            inputProps={{ min: 1 }}
            sx={{ width: 160 }}
          />

          <Divider sx={{ my: 2 }} />

            <Drawer.ActionButtons
              cancelText="Cancelar"
              submitText={saving ? 'Salvando…' : 'Salvar Regra'}
              onCancel={handleClose}
              onSubmit={handleSave}
              submitDisabled={!canSave}
              submitting={saving}
            />

        </Drawer.Body>

      </Drawer>

      <Dialog
        open={discardDialogOpen}
        onClose={() => setDiscardDialogOpen(false)}
        maxWidth={false}
        PaperProps={{ sx: { minHeight: 180, width: 340, maxWidth: 'calc(100% - 32px)', p: 0, display: 'flex', justifyContent: 'center' } }}
      >
        <DialogTitle sx={{ textAlign: 'center', pt: 2, pb: 0.5 }}>Descartar alterações?</DialogTitle>
        <DialogContent sx={{ textAlign: 'center', pb: 2 }}>
          <DialogContentText>As mudanças serão perdidas.</DialogContentText>
        </DialogContent>
        <DialogActions
          sx={{
            flexDirection: 'row',
            gap: 1,
            px: 2,
            pb: 2,
            justifyContent: 'center',
            alignItems: 'center',
          }}
        >
          <Button
            variant="outlined"
            onClick={() => setDiscardDialogOpen(false)}
          >
            Continuar editando
          </Button>
          <Button
            variant="contained"
            color="error"
            onClick={() => {
              setDiscardDialogOpen(false);
              onClose();
            }}
          >
            Descartar
          </Button>
        </DialogActions>
      </Dialog>
    </>
  );
}

export const TransactionRuleDrawer: React.FC<TransactionRuleDrawerProps> = (props) => {
  const drawerKey = `${props.open ? 'open' : 'closed'}:${props.rule?.id ?? 'new'}:${props.initialApplicableTo ?? 'none'}:${props.initialPatternType ?? 'none'}:${props.initialPattern ?? ''}`;
  return <TransactionRuleDrawerInner key={drawerKey} {...props} />;
};
