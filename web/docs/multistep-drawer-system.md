# Multistep Drawer System

## Contexto

O projeto possui 5 drawers com múltiplos steps. Todos gerenciam estado de navegação de forma ad-hoc, cada um com sua própria variação de `useState`, lógica de back button, título condicional e footer. Isso gera inconsistência de UX e dificulta adição de novos fluxos.

**Drawers afetados:**

| Drawer | Etapas |
|--------|--------|
| `CreateFinancialAccountDrawer` | tipo → detalhes |
| `CashTransactionsDrawer` | list → record |
| `ImportTransactionsDrawer` | account → upload → processing → result / review / error |
| `NfceImportDrawer` | input → processing → result / review / error |
| `BillImportDrawer` | upload → processing → card-matching → tx-review → result / error |

---

## Escopo desta entrega

Dois artefatos:

1. **`useStepNavigation`** — hook de navegação entre steps (compartilhado)
2. **`WizardDrawer`** — compound component para fluxos wizard (Perfil A)

> `AsyncPipelineDrawer` (Perfil B) está fora do escopo desta entrega. Os drawers de pipeline (`ImportTransactionsDrawer`, `NfceImportDrawer`, `BillImportDrawer`) serão refatorados em etapa futura após validação do padrão.

---

## 1. `useStepNavigation`

### Localização

```
src/shared/hooks/useStepNavigation.ts
```

### Contrato

```ts
type UseStepNavigationOptions<T extends string> = {
  steps: readonly T[];
  initial: T;
  /** Mantém histórico para suportar goBack(). Default: true. */
  history?: boolean;
};

type UseStepNavigationReturn<T extends string> = {
  step: T;
  goTo: (step: T) => void;
  goBack: () => void;
  canGoBack: boolean;
  isFirst: boolean;
  isLast: boolean;
  reset: () => void;
};
```

### Requisitos funcionais

- `goTo(step)` navega para qualquer step (não precisa ser sequencial).
- `goBack()` retorna ao step anterior na pilha de histórico. Se `history: false`, faz noop.
- `canGoBack` é `true` quando há pelo menos um item no histórico.
- `isFirst` / `isLast` são derivados do array `steps` e do step atual.
- `reset()` volta ao `initial` e limpa o histórico.
- O hook **não** gerencia lógica de negócio — apenas navegação.

### Requisitos não-funcionais

- Sem dependências externas além do React.
- Totalmente testável de forma unitária.
- Deve ter testes em `src/shared/hooks/__tests__/useStepNavigation.test.ts`.

### Casos de teste obrigatórios

- Navegar forward e verificar `step` atual.
- `goBack()` retorna ao step anterior.
- `canGoBack` é `false` no step inicial.
- `reset()` volta ao initial e limpa histórico.
- Com `history: false`, `canGoBack` é sempre `false`.
- `isFirst` e `isLast` corretos nos extremos.

---

## 2. `WizardDrawer`

Compound component para fluxos wizard: fluxo bidirecional, back button no header, título por step, footer customizável por step.

### Localização

```
src/shared/components/ui/layout/WizardDrawer.tsx
src/shared/components/ui/layout/__tests__/WizardDrawer.test.tsx
```

Exportar via `src/shared/components/ui/layout/index.ts`.

### API — Compound Components

```tsx
<WizardDrawer
  open={boolean}
  onClose={() => void}
  step={string}                         // step atual (controlado externamente)
  onBack={() => void}                   // chamado pelo back button
  canGoBack={boolean}                   // exibe back button quando true
  size?: DrawerSize                     // herda tamanhos do Drawer base
  data-testid?: string
>
  <WizardDrawer.Step name="step-name">
    <WizardDrawer.Header title="Título do step" />
    <WizardDrawer.Body>
      {/* conteúdo */}
    </WizardDrawer.Body>
    <WizardDrawer.Footer>
      {/* action buttons */}
    </WizardDrawer.Footer>
  </WizardDrawer.Step>
</WizardDrawer>
```

### Requisitos funcionais

- Renderiza apenas o `WizardDrawer.Step` cujo `name` corresponde ao `step` prop.
- `WizardDrawer.Header` injeta automaticamente o back button quando `canGoBack` é `true`, chamando `onBack` ao clicar. O botão fica à esquerda do título, replicando o comportamento atual do `Drawer.Header` com `icon` prop.
- `WizardDrawer.Body` aplica o mesmo scroll/padding do `Drawer.Body` atual.
- `WizardDrawer.Footer` renderiza dentro do `Drawer.ActionButtons` area existente — mas sem opinar sobre quais botões existem; o consumidor passa os botões como children.
- Se nenhum `WizardDrawer.Step` corresponder ao `step` atual, não renderiza nada (sem erro).
- `WizardDrawer.Header` é opcional dentro do step — se omitido, o header fica vazio (sem título, sem back button).

### Requisitos não-funcionais

- Deve usar o `Drawer` base existente (`src/shared/components/ui/layout/Drawer.tsx`) internamente — não duplicar lógica de overlay/animation.
- Sem `any` no TypeScript; tipos completamente derivados das props.
- A prop `step` é controlada externamente — o componente não gerencia estado de step.

### Não deve fazer

- Não gerenciar estado de step internamente.
- Não ter lógica de negócio (validação, submissão, etc.).
- Não ter opinião sobre quais botões existem no footer.
- Não usar render props ou HOCs — apenas compound components.

---

## 3. Migração

A migração dos drawers existentes deve ser feita **um drawer por vez**, validando antes de avançar.

### Ordem sugerida

| Ordem | Drawer | Justificativa |
|-------|--------|---------------|
| 1 | `CreateFinancialAccountDrawer` | Mais simples (2 steps), boa prova de conceito |
| 2 | `CashTransactionsDrawer` | Simples, valida o padrão list → form |
| 3 | `BillImportDrawer` | Mais complexo, valida casos de skip e step condicional |
| 4–5 | `ImportTransactionsDrawer`, `NfceImportDrawer` | Aguardam definição do `AsyncPipelineDrawer` |

### Critério de aceite por migração

- Comportamento visual idêntico ao anterior (nenhuma regressão de UX).
- Código de navegação removido do drawer e delegado ao hook + componente.
- Testes existentes continuam passando.

---

## 4. Fora do escopo

- `AsyncPipelineDrawer` (pipelines assíncronos com transição automática de step).
- Animação de transição entre steps (slide, fade).
- Step indicators / breadcrumb visual de progresso.
- Qualquer mudança nos drawers de Perfil B nesta entrega.

---

## Referências

- Drawer base: [src/shared/components/ui/layout/Drawer.tsx](../src/shared/components/ui/layout/Drawer.tsx)
- Exemplo de compound component existente: `Drawer.Header`, `Drawer.Body`, `Drawer.ActionButtons`
