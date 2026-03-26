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
} from '@mui/material';
import { Drawer } from '@/shared/components/ui/layout/Drawer';
import { TagsInput } from '@/shared/components/ui/forms/form/TagsInput';
import {
  TRANSACTION_CATEGORIES,
  MERCHANTS,
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
import { useToast } from '@/shared/providers';
import type { TransactionRule } from './types';

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

interface TransactionRuleDrawerProps {
  open: boolean;
  rule: TransactionRule | null;
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

function formFromRule(rule: TransactionRule): FormState {
  return {
    pattern: rule.pattern,
    patternType: rule.patternType,
    applicableTo: rule.applicableTo,
    categoryId: rule.categoryId ?? null,
    merchantId: rule.merchantId ?? null,
    descriptionOverride: rule.descriptionOverride ?? '',
    tagsOverride: rule.tagsOverride ?? [],
    priority: String(rule.priority),
  };
}

export const TransactionRuleDrawer: React.FC<TransactionRuleDrawerProps> = ({
  open,
  rule,
  onClose,
  onSaved,
}) => {
  const toast = useToast();
  const isEdit = !!rule;

  const [form, setForm] = useState<FormState>(() => (rule ? formFromRule(rule) : defaultForm()));
  const [isDirty, setIsDirty] = useState(false);
  const [discardDialogOpen, setDiscardDialogOpen] = useState(false);
  const [previewSample, setPreviewSample] = useState('');
  const [showPreviewField, setShowPreviewField] = useState(false);

  const { data: categoriesData } = useQuery<CategoriesData>(TRANSACTION_CATEGORIES, {
    skip: !open,
  });

  const { data: merchantsData } = useQuery<MerchantsData>(MERCHANTS, {
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
    onError: (err) => toast.error(err.message),
  });

  const [updateRule, { loading: updateLoading }] = useMutation(UPDATE_TRANSACTION_RULE, {
    onCompleted: () => {
      toast.success('Regra salva.');
      onSaved();
    },
    onError: (err) => toast.error(err.message),
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

  const hasAction =
    !!form.categoryId ||
    !!form.merchantId ||
    form.descriptionOverride.trim().length > 0 ||
    form.tagsOverride.length > 0;

  const canSave = form.pattern.trim().length > 0 && hasAction && !saving;

  const handleSave = useCallback(() => {
    const priority = parseInt(form.priority, 10);
    const input = {
      pattern: form.pattern.trim(),
      patternType: form.patternType,
      applicableTo: form.applicableTo,
      categoryId: form.categoryId ?? undefined,
      merchantId: form.merchantId ?? undefined,
      descriptionOverride: form.descriptionOverride.trim() || undefined,
      tagsOverride: form.tagsOverride.length > 0 ? form.tagsOverride : undefined,
      priority: isNaN(priority) || priority < 1 ? 40 : priority,
    };

    if (isEdit && rule) {
      updateRule({
        variables: {
          id: rule.id,
          input: {
            ...input,
            merchantIdProvided: true,
            descriptionOverrideProvided: true,
            tagsOverrideProvided: true,
          },
        },
      });
    } else {
      createRule({ variables: { input } });
    }
  }, [form, isEdit, rule, createRule, updateRule]);

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
  const shouldShowPreview = showPreviewField && !!previewSample.trim() && !!form.pattern.trim();
  const previewError = shouldShowPreview ? previewApolloError?.message ?? null : null;
  const previewResult = shouldShowPreview ? previewData?.previewTransactionRule ?? null : null;

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

          <FormControl fullWidth size="small" sx={{ mb: 2 }}>
            <InputLabel>Tipo de padrão</InputLabel>
            <Select
              label="Tipo de padrão"
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
              size="small"
              label="Padrão"
              value={form.pattern}
              onChange={(e) => setField('pattern', e.target.value)}
              placeholder="Ex: IFOOD"
              inputProps={{ sx: { fontFamily: 'monospace' } }}
            />
            <Button
              size="small"
              variant="outlined"
              onClick={handleTest}
              disabled={!form.pattern.trim()}
              sx={{ flexShrink: 0 }}
            >
              Testar
            </Button>
          </Box>

          {showPreviewField && (
            <Box sx={{ mb: 2 }}>
              <TextField
                fullWidth
                size="small"
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
                  control={<Radio size="small" />}
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
            <Alert severity="warning" sx={{ my: 2 }}>
              Configure pelo menos uma ação (categoria, renomear ou tags).
            </Alert>
          )}

          <Autocomplete
            size="small"
            options={categories}
            value={selectedCategory}
            onChange={(_, value) => setField('categoryId', value?.id ?? null)}
            getOptionLabel={(opt) => opt.name}
            groupBy={(opt) => {
              if (opt.applicableTo === CategoryApplicableTo.Income) return 'Receitas';
              if (opt.applicableTo === CategoryApplicableTo.Expense) return 'Despesas';
              return 'Qualquer';
            }}
            renderInput={(params) => <TextField {...params} label="Categoria" placeholder="Nenhuma" size="small" />}
            renderOption={(props, option) => (
              <Box component="li" {...props} sx={{ gap: 1 }}>
                {option.color && (
                  <Box
                    sx={{
                      width: 10,
                      height: 10,
                      borderRadius: '50%',
                      bgcolor: option.color,
                      flexShrink: 0,
                    }}
                  />
                )}
                <Typography variant="body2">{option.name}</Typography>
              </Box>
            )}
            sx={{ mb: 2 }}
          />

          <Autocomplete
            size="small"
            options={merchants}
            value={selectedMerchant}
            onChange={(_, value) => setField('merchantId', value?.id ?? null)}
            getOptionLabel={(opt) => opt.name}
            renderInput={(params) => <TextField {...params} label="Comerciante" placeholder="Nenhum" size="small" />}
            renderOption={(props, option) => (
              <Box component="li" {...props} sx={{ gap: 1, alignItems: 'center' }}>
                {option.logoAsset?.publicUrl ? (
                  <Box
                    component="img"
                    src={option.logoAsset.publicUrl}
                    alt={option.name}
                    sx={{ width: 20, height: 20, objectFit: 'contain', flexShrink: 0 }}
                  />
                ) : (
                  <Box sx={{ width: 20, height: 20, flexShrink: 0 }} />
                )}
                <Typography variant="body2">{option.name}</Typography>
                {option.domain && (
                  <Typography variant="caption" color="text.secondary">
                    {option.domain}
                  </Typography>
                )}
              </Box>
            )}
            sx={{ mb: 2 }}
          />

          <TextField
            fullWidth
            size="small"
            label="Renomear descrição para (opcional)"
            placeholder="Deixe em branco para manter o original"
            value={form.descriptionOverride}
            onChange={(e) => setField('descriptionOverride', e.target.value)}
            sx={{ mb: 2 }}
          />

          <Box sx={{ mb: 3 }}>
            <TagsInput
              label="Tags a adicionar"
              value={form.tagsOverride}
              onChange={(tags) => setField('tagsOverride', tags)}
              options={[]}
              placeholder="Digite e pressione Enter"
            />
          </Box>

          <Divider sx={{ mb: 2 }} />

          <TextField
            size="small"
            label="Prioridade"
            type="number"
            value={form.priority}
            onChange={(e) => setField('priority', e.target.value)}
            helperText="Menor número = aplicada primeiro"
            inputProps={{ min: 1 }}
            sx={{ width: 160 }}
          />
        </Drawer.Body>

        <Drawer.Footer>
          <Drawer.ActionButtons
            cancelText="Cancelar"
            submitText={saving ? 'Salvando…' : 'Salvar Regra'}
            onCancel={handleClose}
            onSubmit={handleSave}
            submitDisabled={!canSave}
            submitting={saving}
          />
        </Drawer.Footer>
      </Drawer>

      <Dialog open={discardDialogOpen} onClose={() => setDiscardDialogOpen(false)} maxWidth="xs">
        <DialogTitle>Descartar alterações?</DialogTitle>
        <DialogContent>
          <DialogContentText>As mudanças não salvas serão perdidas.</DialogContentText>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setDiscardDialogOpen(false)}>Continuar editando</Button>
          <Button
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
};
