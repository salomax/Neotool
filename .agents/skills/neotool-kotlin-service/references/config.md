# Config — Micronaut @ConfigurationProperties and env vars

## Env vars are auto-mapped, no placeholder needed

Any property inside a `@ConfigurationProperties` class is already overridable by an env var
derived from the property path (uppercased, `.`/`-` become `_`) — **no `${VAR:default}` placeholder
required in YAML**. Real example: `oauth.authorization-server.metadata.supported-resources` was
already overridden in production by `OAUTH_AUTHORIZATION_SERVER_METADATA_SUPPORTED_RESOURCES`, even
though the YAML only had the fixed value `supported-resources: []` — no placeholder at all.

Before adding a placeholder `${MY_VAR:default}` to "allow an env override," first check (via `grep`
on the infra deployment manifest, e.g. `invistus-flux`) whether the override already works through
the implicit convention. If it already works, don't add the placeholder — it's redundant, and for
`List<String>` properties it can be actively dangerous (see below).

## Placeholders with a scalar default break List<String> properties

A placeholder like `${VAR:}` or `${VAR:[]}` resolves to a **literal string** before binding.
Converting that string to `List<String>` always produces a **one-element list** containing the
literal string — never an empty list:

- `${VAR:}` with `VAR` unset → resolves to `""` → becomes `[""]` (a one-element list with a blank
  entry), not `[]`.
- `${VAR:[]}` with `VAR` unset → resolves to `"[]"` → becomes `["[]"]` (a one-element list
  containing the literal text `"[]"`), not `[]`.

If the config has eager validation (e.g. a `@Context` `BeanCreatedEventListener` calling
`require(list.all { it.isNotBlank() })` at boot), the `[""]` case crashes the app on startup in any
environment where the env var isn't set — including local dev, if `.env.local` doesn't exist (it's
normally `.gitignore`d).

**Before trusting a placeholder like this, verify the actual binding** with a disposable test:

```kotlin
val result = ConversionService.SHARED.convert("", Argument.listOf(String::class.java))
println(result.orElse(null))       // → [""], size 1 — not an empty []
```

Don't assume behavior from the YAML syntax — confirm it with `ConversionService` before declaring
the config "optional" or "safely defaulted."

## Practical rule

For an optional `List<String>` property in `@ConfigurationProperties`:
1. Prefer a real Kotlin list as the YAML default (`supported-resources: []` or a populated list),
   **with no placeholder**.
2. Let the auto-derived env var handle the per-environment override.
3. Only use an explicit `${VAR:default}` placeholder for scalar types (`String`, `Long`, etc.) —
   there, the literal default behaves as expected.
