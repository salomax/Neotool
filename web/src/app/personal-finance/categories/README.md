# Account categories (Minhas categorias)

## User journey

1. **Entry**: User goes to **Finanças pessoais** (personal finance hub) and clicks **Categorias** (or navigates to `/personal-finance/categories`).
2. **List**: Page shows only **account-scoped custom categories** (no system/global categories in this screen). Each row: icon, name, type (Receita/Despesa), actions (Edit, Delete).
3. **Create**: User clicks **Nova categoria** → drawer opens with form (name, type, optional parent category, icon, color) → Save → category is created and list refreshes; it appears in the CategoryPicker on transactions.
4. **Edit**: User clicks Edit on a row → same drawer with form pre-filled (name, icon, color; type and parent are immutable) → Save → list refreshes.
5. **Delete**: User clicks Delete → confirmation dialog explains that existing transactions keep the category → Confirm → category is soft-deleted and list refreshes.

No bulk categorize or global category management in this flow; rules management is a separate feature.
