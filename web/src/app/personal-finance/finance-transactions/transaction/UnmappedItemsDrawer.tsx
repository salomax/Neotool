'use client';

import { useDeferredValue, useEffect, useMemo, useState } from 'react';
import { useMutation, useQuery } from '@apollo/client/react';
import {
  Alert,
  Box,
  Button,
  Chip,
  CircularProgress,
  Divider,
  List,
  ListItemButton,
  ListItemText,
  Paper,
  Stack,
  TextField,
  Typography,
} from '@mui/material';
import PlaylistAddCheckCircleOutlinedIcon from '@mui/icons-material/PlaylistAddCheckCircleOutlined';
import AddCircleOutlineIcon from '@mui/icons-material/AddCircleOutline';
import { Drawer } from '@/shared/components/ui/layout/Drawer';
import { useTranslation } from '@/shared/i18n';
import type {
  CreateProductInput,
  CreateProductVariantInput,
} from '@/lib/graphql/types/__generated__/graphql';
import { personalFinanceTranslations } from '../../i18n';
import {
  ProductVariantsDocument,
  type ProductVariantsQuery,
  type ProductVariantsQueryVariables,
  UnmappedItemsDocument,
  type UnmappedItemsQuery,
  type UnmappedItemsQueryVariables,
} from '@/lib/graphql/operations/banking/queries.generated';
import {
  CREATE_PRODUCT,
  CREATE_PRODUCT_VARIANT,
  MAP_ITEM_TO_VARIANT,
} from '@/lib/graphql/operations/banking/mutations';
import { extractErrorMessage } from '@/shared/utils/error';

interface UnmappedItemsDrawerProps {
  open: boolean;
  onClose: () => void;
  onMapped?: () => void;
}

type UnmappedItemGroup = UnmappedItemsQuery['unmappedItems'][number];
type VariantSearchResult = ProductVariantsQuery['productVariants'][number];
type CreateProductMutationData = { createProduct: { id: string } };
type CreateProductVariantMutationData = { createProductVariant: { id: string } };
type MapItemToVariantMutationData = { mapItemToVariant: { id: string } };
type CreateProductMutationVariables = { input: CreateProductInput };
type CreateProductVariantMutationVariables = { input: CreateProductVariantInput };
type MapItemToVariantMutationVariables = { rawDescription: string; variantId: string };

const currencyFormatter = new Intl.NumberFormat('pt-BR', {
  style: 'currency',
  currency: 'BRL',
});

const emptyCreateForm = {
  productName: '',
  category: '',
  defaultUnit: '',
  variantName: '',
  brand: '',
  ean: '',
  quantityValue: '',
  quantityUnit: '',
};

export function UnmappedItemsDrawer({
  open,
  onClose,
  onMapped,
}: UnmappedItemsDrawerProps) {
  const { t } = useTranslation(personalFinanceTranslations);
  const [selectedRawDescription, setSelectedRawDescription] = useState<string | null>(null);
  const [variantSearch, setVariantSearch] = useState('');
  const deferredVariantSearch = useDeferredValue(variantSearch);
  const [createForm, setCreateForm] = useState(emptyCreateForm);
  const [feedbackMessage, setFeedbackMessage] = useState<string | null>(null);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  const { data, loading, error, refetch } = useQuery<UnmappedItemsQuery, UnmappedItemsQueryVariables>(
    UnmappedItemsDocument,
    {
    variables: { limit: 25 },
    skip: !open,
    fetchPolicy: 'cache-and-network',
    },
  );

  const groups = useMemo<UnmappedItemGroup[]>(() => data?.unmappedItems ?? [], [data?.unmappedItems]);

  useEffect(() => {
    if (!open) return;
    if (groups.length === 0) {
      setSelectedRawDescription(null);
      return;
    }
    if (!selectedRawDescription || !groups.some((group) => group.rawDescription === selectedRawDescription)) {
      setSelectedRawDescription(groups[0]?.rawDescription ?? null);
    }
  }, [groups, open, selectedRawDescription]);

  const selectedGroup = useMemo(
    () => groups.find((group) => group.rawDescription === selectedRawDescription) ?? null,
    [groups, selectedRawDescription],
  );

  useEffect(() => {
    if (!selectedGroup) return;
    setVariantSearch(selectedGroup.rawDescription);
    setCreateForm({
      productName: selectedGroup.rawDescription,
      category: '',
      defaultUnit: '',
      variantName: selectedGroup.rawDescription,
      brand: '',
      ean: '',
      quantityValue: '',
      quantityUnit: selectedGroup.sampleItems[0]?.unit ?? '',
    });
    setFeedbackMessage(null);
    setErrorMessage(null);
  }, [selectedGroup]);

  const {
    data: variantsData,
    loading: variantsLoading,
  } = useQuery<ProductVariantsQuery, ProductVariantsQueryVariables>(ProductVariantsDocument, {
    variables: {
      search: deferredVariantSearch.trim() || selectedGroup?.rawDescription,
      limit: 12,
    },
    skip: !open || !selectedGroup,
  });

  const variantResults = useMemo<VariantSearchResult[]>(
    () => variantsData?.productVariants ?? [],
    [variantsData?.productVariants],
  );

  const [createProduct, { loading: creatingProduct }] = useMutation<
    CreateProductMutationData,
    CreateProductMutationVariables
  >(CREATE_PRODUCT);
  const [createProductVariant, { loading: creatingVariant }] = useMutation<
    CreateProductVariantMutationData,
    CreateProductVariantMutationVariables
  >(CREATE_PRODUCT_VARIANT);
  const [mapItemToVariant, { loading: mappingVariant }] = useMutation<
    MapItemToVariantMutationData,
    MapItemToVariantMutationVariables
  >(MAP_ITEM_TO_VARIANT);

  const busy = creatingProduct || creatingVariant || mappingVariant;

  if (!open) return null;

  const handleRefreshAfterMap = async () => {
    await refetch();
    onMapped?.();
  };

  const handleMapExistingVariant = async (variantId: string) => {
    if (!selectedGroup) return;
    setErrorMessage(null);
    setFeedbackMessage(null);
    try {
      await mapItemToVariant({
        variables: {
          rawDescription: selectedGroup.rawDescription,
          variantId,
        },
      });
      setFeedbackMessage(t('financeTransactions.unmappedItems.feedback.mapped'));
      await handleRefreshAfterMap();
    } catch (err) {
      setErrorMessage(extractErrorMessage(err, t('financeTransactions.unmappedItems.feedback.mapError')));
    }
  };

  const handleCreateAndMap = async () => {
    if (!selectedGroup) return;
    if (!createForm.productName.trim() || !createForm.variantName.trim()) {
      setErrorMessage(t('financeTransactions.unmappedItems.feedback.validation'));
      return;
    }

    setErrorMessage(null);
    setFeedbackMessage(null);
    try {
      const createdProduct = await createProduct({
        variables: {
          input: {
            name: createForm.productName.trim(),
            category: createForm.category.trim() || undefined,
            defaultUnit: createForm.defaultUnit.trim() || undefined,
          },
        },
      });

      const productId = createdProduct.data?.createProduct?.id;
      if (!productId) {
        throw new Error('Missing product id');
      }

      const createdVariant = await createProductVariant({
        variables: {
          input: {
            productId,
            name: createForm.variantName.trim(),
            brand: createForm.brand.trim() || undefined,
            ean: createForm.ean.trim() || undefined,
            quantityValue: createForm.quantityValue.trim() ? Number(createForm.quantityValue) : undefined,
            quantityUnit: createForm.quantityUnit.trim() || undefined,
          },
        },
      });

      const variantId = createdVariant.data?.createProductVariant?.id;
      if (!variantId) {
        throw new Error('Missing variant id');
      }

      await mapItemToVariant({
        variables: {
          rawDescription: selectedGroup.rawDescription,
          variantId,
        },
      });
      setFeedbackMessage(t('financeTransactions.unmappedItems.feedback.createdAndMapped'));
      await handleRefreshAfterMap();
    } catch (err) {
      setErrorMessage(extractErrorMessage(err, t('financeTransactions.unmappedItems.feedback.createError')));
    }
  };

  const renderSampleItems = () => {
    if (!selectedGroup) return null;
    return (
      <Stack spacing={1.25}>
        {selectedGroup.sampleItems.map((item) => (
          <Paper key={item.id} variant="outlined" sx={{ p: 1.5 }}>
            <Stack direction="row" justifyContent="space-between" spacing={2}>
              <Box>
                <Typography variant="body2" fontWeight={600}>
                  {item.quantity} {item.unit}
                </Typography>
                <Typography variant="caption" color="text.secondary">
                  {item.ncmCode || item.ean || t('financeTransactions.unmappedItems.sample.noExtraData')}
                </Typography>
              </Box>
              <Typography variant="body2" fontWeight={700}>
                {currencyFormatter.format(item.effectivePrice)}
              </Typography>
            </Stack>
          </Paper>
        ))}
      </Stack>
    );
  };

  return (
    <Drawer open={open} onClose={onClose} anchor="right" size="lg" ariaLabel={t('financeTransactions.unmappedItems.title')}>
      <Drawer.Header title={t('financeTransactions.unmappedItems.title')} />
      <Drawer.Body>
        <Stack spacing={2}>
          {error && (
            <Alert severity="warning">
              {extractErrorMessage(error, t('financeTransactions.unmappedItems.feedback.loadError'))}
            </Alert>
          )}
          {errorMessage && <Alert severity="error">{errorMessage}</Alert>}
          {feedbackMessage && <Alert severity="success">{feedbackMessage}</Alert>}
          {loading ? (
            <Box sx={{ display: 'flex', justifyContent: 'center', py: 6 }}>
              <CircularProgress size={28} />
            </Box>
          ) : groups.length === 0 ? (
            <Alert severity="success">{t('financeTransactions.unmappedItems.empty')}</Alert>
          ) : (
            <Stack direction={{ xs: 'column', md: 'row' }} spacing={2} alignItems="stretch">
              <Paper variant="outlined" sx={{ width: { xs: '100%', md: 280 }, maxHeight: 620, overflow: 'auto' }}>
                <List disablePadding>
                  {groups.map((group) => (
                    <ListItemButton
                      key={group.rawDescription}
                      selected={group.rawDescription === selectedGroup?.rawDescription}
                      onClick={() => setSelectedRawDescription(group.rawDescription)}
                    >
                      <ListItemText
                        primary={group.rawDescription}
                        secondary={t('financeTransactions.unmappedItems.count', { count: group.count })}
                      />
                    </ListItemButton>
                  ))}
                </List>
              </Paper>

              <Stack spacing={2} sx={{ flex: 1, minWidth: 0 }}>
                {selectedGroup && (
                  <>
                    <Paper variant="outlined" sx={{ p: 2 }}>
                      <Stack spacing={1.5}>
                        <Stack direction="row" justifyContent="space-between" alignItems="flex-start" spacing={2}>
                          <Box>
                            <Typography variant="overline" color="text.secondary">
                              {t('financeTransactions.unmappedItems.currentGroup')}
                            </Typography>
                            <Typography variant="h6">{selectedGroup.rawDescription}</Typography>
                          </Box>
                          <Chip
                            color="warning"
                            icon={<PlaylistAddCheckCircleOutlinedIcon />}
                            label={t('financeTransactions.unmappedItems.count', { count: selectedGroup.count })}
                          />
                        </Stack>
                        <Typography variant="body2" color="text.secondary">
                          {t('financeTransactions.unmappedItems.sample.title')}
                        </Typography>
                        {renderSampleItems()}
                      </Stack>
                    </Paper>

                    <Paper variant="outlined" sx={{ p: 2 }}>
                      <Stack spacing={1.5}>
                        <Typography variant="subtitle1" fontWeight={700}>
                          {t('financeTransactions.unmappedItems.searchTitle')}
                        </Typography>
                        <TextField
                          label={t('financeTransactions.unmappedItems.searchLabel')}
                          value={variantSearch}
                          onChange={(event) => setVariantSearch(event.target.value)}
                          size="small"
                        />
                        {variantsLoading ? (
                          <Box sx={{ display: 'flex', justifyContent: 'center', py: 2 }}>
                            <CircularProgress size={22} />
                          </Box>
                        ) : variantResults.length === 0 ? (
                          <Alert severity="info">{t('financeTransactions.unmappedItems.searchEmpty')}</Alert>
                        ) : (
                          <Stack spacing={1}>
                            {variantResults.map((variant) => (
                              <Paper key={variant.id} variant="outlined" sx={{ p: 1.5 }}>
                                <Stack
                                  direction={{ xs: 'column', sm: 'row' }}
                                  spacing={1.5}
                                  justifyContent="space-between"
                                  alignItems={{ xs: 'stretch', sm: 'center' }}
                                >
                                  <Box sx={{ minWidth: 0 }}>
                                    <Typography variant="body2" fontWeight={700} noWrap>
                                      {variant.name}
                                    </Typography>
                                    <Typography variant="caption" color="text.secondary">
                                      {[variant.brand, variant.ean, variant.quantityValue ? `${variant.quantityValue} ${variant.quantityUnit ?? ''}` : null]
                                        .filter(Boolean)
                                        .join(' · ') || t('financeTransactions.unmappedItems.sample.noExtraData')}
                                    </Typography>
                                  </Box>
                                  <Button
                                    variant="outlined"
                                    size="small"
                                    disabled={busy}
                                    onClick={() => handleMapExistingVariant(variant.id)}
                                  >
                                    {t('financeTransactions.unmappedItems.actions.map')}
                                  </Button>
                                </Stack>
                              </Paper>
                            ))}
                          </Stack>
                        )}
                      </Stack>
                    </Paper>

                    <Paper variant="outlined" sx={{ p: 2 }}>
                      <Stack spacing={1.5}>
                        <Stack direction="row" spacing={1} alignItems="center">
                          <AddCircleOutlineIcon fontSize="small" color="primary" />
                          <Typography variant="subtitle1" fontWeight={700}>
                            {t('financeTransactions.unmappedItems.createTitle')}
                          </Typography>
                        </Stack>
                        <Stack direction={{ xs: 'column', md: 'row' }} spacing={1.5}>
                          <TextField
                            label={t('financeTransactions.unmappedItems.form.productName')}
                            value={createForm.productName}
                            onChange={(event) => setCreateForm((prev) => ({ ...prev, productName: event.target.value }))}
                            size="small"
                            fullWidth
                          />
                          <TextField
                            label={t('financeTransactions.unmappedItems.form.variantName')}
                            value={createForm.variantName}
                            onChange={(event) => setCreateForm((prev) => ({ ...prev, variantName: event.target.value }))}
                            size="small"
                            fullWidth
                          />
                        </Stack>
                        <Stack direction={{ xs: 'column', md: 'row' }} spacing={1.5}>
                          <TextField
                            label={t('financeTransactions.unmappedItems.form.category')}
                            value={createForm.category}
                            onChange={(event) => setCreateForm((prev) => ({ ...prev, category: event.target.value }))}
                            size="small"
                            fullWidth
                          />
                          <TextField
                            label={t('financeTransactions.unmappedItems.form.defaultUnit')}
                            value={createForm.defaultUnit}
                            onChange={(event) => setCreateForm((prev) => ({ ...prev, defaultUnit: event.target.value }))}
                            size="small"
                            fullWidth
                          />
                        </Stack>
                        <Stack direction={{ xs: 'column', md: 'row' }} spacing={1.5}>
                          <TextField
                            label={t('financeTransactions.unmappedItems.form.brand')}
                            value={createForm.brand}
                            onChange={(event) => setCreateForm((prev) => ({ ...prev, brand: event.target.value }))}
                            size="small"
                            fullWidth
                          />
                          <TextField
                            label={t('financeTransactions.unmappedItems.form.ean')}
                            value={createForm.ean}
                            onChange={(event) => setCreateForm((prev) => ({ ...prev, ean: event.target.value }))}
                            size="small"
                            fullWidth
                          />
                        </Stack>
                        <Stack direction={{ xs: 'column', md: 'row' }} spacing={1.5}>
                          <TextField
                            label={t('financeTransactions.unmappedItems.form.quantityValue')}
                            value={createForm.quantityValue}
                            onChange={(event) => setCreateForm((prev) => ({ ...prev, quantityValue: event.target.value }))}
                            size="small"
                            fullWidth
                          />
                          <TextField
                            label={t('financeTransactions.unmappedItems.form.quantityUnit')}
                            value={createForm.quantityUnit}
                            onChange={(event) => setCreateForm((prev) => ({ ...prev, quantityUnit: event.target.value }))}
                            size="small"
                            fullWidth
                          />
                        </Stack>
                        <Divider />
                        <Box sx={{ display: 'flex', justifyContent: 'flex-end' }}>
                          <Button variant="contained" onClick={handleCreateAndMap} disabled={busy}>
                            {t('financeTransactions.unmappedItems.actions.createAndMap')}
                          </Button>
                        </Box>
                      </Stack>
                    </Paper>
                  </>
                )}
              </Stack>
            </Stack>
          )}
        </Stack>
      </Drawer.Body>
    </Drawer>
  );
}
