"use client";

import React, { useState } from "react";
import Container from "@mui/material/Container";
import Typography from "@mui/material/Typography";
import Box from "@mui/material/Box";
import MUIButton from "@mui/material/Button";
import TextField from "@mui/material/TextField";
import Table from "@mui/material/Table";
import TableHead from "@mui/material/TableHead";
import TableBody from "@mui/material/TableBody";
import TableRow from "@mui/material/TableRow";
import TableCell from "@mui/material/TableCell";
import Paper from "@mui/material/Paper";
import Dialog from "@mui/material/Dialog";
import DialogTitle from "@mui/material/DialogTitle";
import DialogContent from "@mui/material/DialogContent";
import DialogActions from "@mui/material/DialogActions";
import IconButton from "@mui/material/IconButton";
import Chip from "@mui/material/Chip";
import { ErrorAlert } from "@/shared/components/ui/feedback";
import CircularProgress from "@mui/material/CircularProgress";
import InputAdornment from "@mui/material/InputAdornment";
import SearchIcon from "@mui/icons-material/Search";
import AddIcon from "@mui/icons-material/Add";
import EditIcon from "@mui/icons-material/Edit";
import DeleteIcon from "@mui/icons-material/Delete";
import RefreshIcon from "@mui/icons-material/Refresh";
import AttachMoneyIcon from "@mui/icons-material/AttachMoney";
import InventoryIcon from "@mui/icons-material/Inventory";
import { useForm, Controller } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { z } from "zod";
import { useTranslation } from '@/shared/i18n';
import { productsTranslations } from './i18n';
import { Button } from "@/shared/components/ui/primitives";
import {
  useGetProductsQuery,
  GetProductsDocument
} from '@/lib/graphql/operations/product/queries.generated';
import {
  useCreateProductMutation,
  useUpdateProductMutation,
  useDeleteProductMutation
} from '@/lib/graphql/operations/product/mutations.generated';
import { ProductInput } from '@/lib/graphql/types/__generated__/graphql';

export default function ProductsPage() {
  const { t } = useTranslation(productsTranslations);
  const [searchTerm, setSearchTerm] = useState("");
  const [stockFilter, setStockFilter] = useState<string>("ALL");
  const [dialogOpen, setDialogOpen] = useState(false);
  const [editingProduct, setEditingProduct] = useState<any>(null);
  const [deleteConfirm, setDeleteConfirm] = useState<any>(null);

  // Apollo Client hooks
  const { data: productsData, loading, error, refetch } = useGetProductsQuery();
  const [createProduct] = useCreateProductMutation();
  const [updateProduct] = useUpdateProductMutation();
  const [deleteProduct] = useDeleteProductMutation();

  // Dynamic schema validation with translations
  const productSchema = z.object({
    name: z.string().min(2, t('nameRequired')),
    sku: z.string().min(3, t('skuRequired')),
    priceCents: z.number().min(0, t('priceRequired')),
    stock: z.number().int().min(0, t('stockRequired')),
  });

  type ProductFormData = z.infer<typeof productSchema>;

  const {
    control,
    handleSubmit,
    reset,
    formState: { errors, isSubmitting },
  } = useForm<ProductFormData>({
    resolver: zodResolver(productSchema),
    defaultValues: {
      name: "",
      sku: "",
      priceCents: 0,
      stock: 0,
    },
  });

  // Create or update product via Apollo Client
  const onSubmit = async (data: ProductFormData) => {
    try {
      const input: ProductInput = {
        name: data.name,
        sku: data.sku,
        priceCents: data.priceCents,
        stock: data.stock,
      };

      if (editingProduct) {
        await updateProduct({
          variables: { id: editingProduct.id, input },
          refetchQueries: [GetProductsDocument],
        });
      } else {
        await createProduct({
          variables: { input },
          refetchQueries: [GetProductsDocument],
        });
      }

      reset();
      setDialogOpen(false);
      setEditingProduct(null);
    } catch (err) {
      // Error handling done via toast in mutation
    }
  };

  // Delete product via Apollo Client
  const handleDeleteProduct = async (product: any) => {
    try {
      await deleteProduct({
        variables: { id: product.id },
        refetchQueries: [GetProductsDocument],
      });
      setDeleteConfirm(null);
    } catch (err) {
      // Error handling done via toast in mutation
    }
  };

  // Open edit dialog
  const openEditDialog = (product: any) => {
    setEditingProduct(product);
    reset({
      name: product.name,
      sku: product.sku,
      priceCents: product.priceCents,
      stock: product.stock,
    });
    setDialogOpen(true);
  };

  // Open create dialog
  const openCreateDialog = () => {
    setEditingProduct(null);
    reset({
      name: "",
      sku: "",
      priceCents: 0,
      stock: 0,
    });
    setDialogOpen(true);
  };

  // Get products from Apollo Client data
  const products = productsData?.products || [];

  // Filter products
  const filteredProducts = products.filter((product: any) => {
    const matchesSearch = product.name.toLowerCase().includes(searchTerm.toLowerCase()) ||
                         product.sku.toLowerCase().includes(searchTerm.toLowerCase());
    
    let matchesStock = true;
    if (stockFilter === "IN_STOCK") {
      matchesStock = product.stock > 0;
    } else if (stockFilter === "OUT_OF_STOCK") {
      matchesStock = product.stock === 0;
    } else if (stockFilter === "LOW_STOCK") {
      matchesStock = product.stock > 0 && product.stock <= 10;
    }
    
    return matchesSearch && matchesStock;
  });

  // Get stock status color
  const getStockStatus = (stock: number) => {
    if (stock === 0) return { label: "Out of Stock", color: "error" as const };
    if (stock <= 10) return { label: "Low Stock", color: "warning" as const };
    return { label: "In Stock", color: "success" as const };
  };

  // Format price
  const formatPrice = (priceCents: number) => {
    return `$${(priceCents / 100).toFixed(2)}`;
  };

  return (
    <Container maxWidth="lg" sx={{ py: 3 }}>
      <Box sx={{ display: "flex", justifyContent: "space-between", alignItems: "center", mb: 3 }}>
        <Typography variant="h4" component="h1">
          Product Catalog
        </Typography>
        <Button
          variant="contained"
          startIcon={<AddIcon />}
          onClick={openCreateDialog}
        >
          Add Product
        </Button>
      </Box>

      <ErrorAlert
        error={error || undefined}
        onRetry={() => refetch()}
        fallbackMessage="Failed to load products"
      />

      {/* Search and Filter Controls */}
      <Paper sx={{ p: 2, mb: 3 }}>
        <Box sx={{ display: "flex", gap: 2, alignItems: "center", flexWrap: "wrap" }}>
          <TextField
            placeholder="Search products..."
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
            InputProps={{
              startAdornment: (
                <InputAdornment position="start">
                  <SearchIcon />
                </InputAdornment>
              ),
            }}
            sx={{ minWidth: 200 }}
          />
          <TextField
            select
            label="Stock Status"
            value={stockFilter}
            onChange={(e) => setStockFilter(e.target.value)}
            SelectProps={{ native: true }}
            sx={{ minWidth: 140 }}
          >
            <option value="ALL">All Products</option>
            <option value="IN_STOCK">In Stock</option>
            <option value="LOW_STOCK">Low Stock (≤10)</option>
            <option value="OUT_OF_STOCK">Out of Stock</option>
          </TextField>
          <Button
            variant="outlined"
            startIcon={<RefreshIcon />}
            onClick={() => refetch()}
            disabled={loading}
          >
            Refresh
          </Button>
        </Box>
      </Paper>

      {/* Products Table */}
      <Paper>
        {loading ? (
          <Box sx={{ display: "flex", justifyContent: "center", p: 4 }}>
            <CircularProgress />
          </Box>
        ) : (
          <Table>
            <TableHead>
              <TableRow>
                <TableCell>ID</TableCell>
                <TableCell>Name</TableCell>
                <TableCell>SKU</TableCell>
                <TableCell align="right">Price</TableCell>
                <TableCell align="right">Stock</TableCell>
                <TableCell>Status</TableCell>
                <TableCell>Created</TableCell>
                <TableCell align="right">Actions</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {filteredProducts.length === 0 ? (
                <TableRow>
                  <TableCell colSpan={8} align="center" sx={{ py: 4 }}>
                    <Typography color="text.secondary">
                      {products.length === 0 ? "No products found" : "No products match your search criteria"}
                    </Typography>
                  </TableCell>
                </TableRow>
              ) : (
                filteredProducts.map((product: any) => {
                  const stockStatus = getStockStatus(product.stock);
                  return (
                    <TableRow key={product.id} hover>
                      <TableCell>{product.id}</TableCell>
                      <TableCell>{product.name}</TableCell>
                      <TableCell>
                        <Box sx={{ display: "flex", alignItems: "center", gap: 1 }}>
                          <InventoryIcon fontSize="small" color="action" />
                          {product.sku}
                        </Box>
                      </TableCell>
                      <TableCell align="right">
                        <Box sx={{ display: "flex", alignItems: "center", justifyContent: "flex-end", gap: 0.5 }}>
                          <AttachMoneyIcon fontSize="small" color="success" />
                          <Typography variant="body2" fontWeight="medium">
                            {formatPrice(product.priceCents)}
                          </Typography>
                        </Box>
                      </TableCell>
                      <TableCell align="right">
                        <Typography variant="body2" fontWeight="medium">
                          {product.stock}
                        </Typography>
                      </TableCell>
                      <TableCell>
                        <Chip
                          label={stockStatus.label}
                          color={stockStatus.color}
                          size="small"
                        />
                      </TableCell>
                      <TableCell>
                        {product.createdAt ? new Date(product.createdAt).toLocaleDateString() : "-"}
                      </TableCell>
                      <TableCell align="right">
                        <IconButton
                          color="primary"
                          onClick={() => openEditDialog(product)}
                          size="small"
                        >
                          <EditIcon />
                        </IconButton>
                        <IconButton
                          color="error"
                          onClick={() => setDeleteConfirm(product)}
                          size="small"
                        >
                          <DeleteIcon />
                        </IconButton>
                      </TableCell>
                    </TableRow>
                  );
                })
              )}
            </TableBody>
          </Table>
        )}
      </Paper>

      {/* Create/Edit Dialog */}
      <Dialog open={dialogOpen} onClose={() => setDialogOpen(false)} maxWidth="sm" fullWidth>
        <DialogTitle>
          {editingProduct ? "Edit Product" : "Create New Product"}
        </DialogTitle>
        <form onSubmit={handleSubmit(onSubmit)}>
          <DialogContent>
            <Box sx={{ display: "flex", flexDirection: "column", gap: 2, pt: 1 }}>
              <Controller
                name="name"
                control={control}
                render={({ field }) => (
                  <TextField
                    {...field}
                    label="Product Name"
                    error={!!errors.name}
                    helperText={errors.name?.message}
                    fullWidth
                  />
                )}
              />
              <Controller
                name="sku"
                control={control}
                render={({ field }) => (
                  <TextField
                    {...field}
                    label="SKU"
                    error={!!errors.sku}
                    helperText={errors.sku?.message}
                    fullWidth
                  />
                )}
              />
              <Controller
                name="priceCents"
                control={control}
                render={({ field }) => (
                  <TextField
                    {...field}
                    label="Price (cents)"
                    type="number"
                    error={!!errors.priceCents}
                    helperText={errors.priceCents?.message || "Enter price in cents (e.g., 1999 for $19.99)"}
                    fullWidth
                    onChange={(e) => field.onChange(parseInt(e.target.value) || 0)}
                  />
                )}
              />
              <Controller
                name="stock"
                control={control}
                render={({ field }) => (
                  <TextField
                    {...field}
                    label="Stock Quantity"
                    type="number"
                    error={!!errors.stock}
                    helperText={errors.stock?.message}
                    fullWidth
                    onChange={(e) => field.onChange(parseInt(e.target.value) || 0)}
                  />
                )}
              />
            </Box>
          </DialogContent>
          <DialogActions>
            <MUIButton onClick={() => setDialogOpen(false)}>Cancel</MUIButton>
            <Button 
              type="submit" 
              variant="contained" 
              loading={isSubmitting}
            >
              {editingProduct ? "Update" : "Create"}
            </Button>
          </DialogActions>
        </form>
      </Dialog>

      {/* Delete Confirmation Dialog */}
      <Dialog open={!!deleteConfirm} onClose={() => setDeleteConfirm(null)}>
        <DialogTitle>Confirm Delete</DialogTitle>
        <DialogContent>
          <Typography>
            Are you sure you want to delete product &quot;{deleteConfirm?.name}&quot;? This action cannot be undone.
          </Typography>
        </DialogContent>
        <DialogActions>
          <MUIButton onClick={() => setDeleteConfirm(null)}>Cancel</MUIButton>
          <Button
            onClick={() => deleteConfirm && handleDeleteProduct(deleteConfirm)}
            color="error"
            variant="contained"
          >
            Delete
          </Button>
        </DialogActions>
      </Dialog>
    </Container>
  );
}
