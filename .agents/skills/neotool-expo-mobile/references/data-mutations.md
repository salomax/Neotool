# Mutations, Feedback & Offline — Mobile

## Rule: every mutation gives feedback

A mutation must always reach the user with a **success or error** signal. On mobile this means
a Snackbar/toast, and — for meaningful actions — a **haptic** cue. Never fail silently.

```typescript
import { useMutation } from '@apollo/client';
import * as Haptics from 'expo-haptics';
import { useSnackbar } from '@/shared/hooks/useSnackbar'; // Paper Snackbar wrapper
import { UpdateTransaction } from '@/generated'; // typed document

const { showSuccess, showError } = useSnackbar();
const [updateTransaction, { loading }] = useMutation(UpdateTransaction);

const onSave = async (input: TransactionInput) => {
  try {
    await updateTransaction({
      variables: { input },
      // optimistic UI — reflect the change instantly, roll back on error
      optimisticResponse: buildOptimistic(input),
      update: (cache, { data }) => writeToCache(cache, data),
    });
    await Haptics.notificationAsync(Haptics.NotificationFeedbackType.Success);
    showSuccess(t('transaction.updated'));
  } catch (err) {
    await Haptics.notificationAsync(Haptics.NotificationFeedbackType.Error);
    showError(extractErrorMessage(err)); // map via neotool-conventions error contract
  }
};
```

## Optimistic UI + rollback

Prefer `optimisticResponse` + a cache `update` for list/detail mutations so the UI feels instant.
Apollo rolls the optimistic layer back automatically on error — surface the error and let the UI revert.

## Error mapping

Use the shared error contract (network / auth / business validation / unknown) from
**neotool-conventions**. Do not invent per-screen error copy; resolve messages through i18n keys.
Business-validation errors map to inline field errors where possible, not just a toast.

## Offline mutations

Reads are offline-first via the persisted cache. Mutations issued offline should:

- Detect connectivity with `useNetworkStatus()` / NetInfo before firing when appropriate.
- Either block with a clear "you're offline" affordance, or queue and replay on reconnect
  (only where the backlog explicitly requires an offline write queue — do not build one implicitly).
- Never lose user input: keep form state until the mutation confirms.

## Forms

React Hook Form + Zod, same schemas as web where feasible (shared validation is a goal).

```typescript
const { control, handleSubmit, formState } = useForm<Schema>({
  resolver: zodResolver(schema),
  mode: 'onBlur',
});
```

- Use `Controller` to bind RN Paper `TextInput`.
- Money/number fields use numeric keyboards and masks; validate inline on blur.
- Disable submit while pending; show progress on the button, not a blocking overlay.
