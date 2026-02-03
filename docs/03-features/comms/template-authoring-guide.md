# Template Authoring Guide

This guide explains how to create and manage communication templates in the Neotool Comms module.

## Template Structure

Templates are stored in `service/kotlin/comms/src/main/resources/templates/{channel}/{template-key}/`:

```
templates/
  email/
    user-welcome/
      template.yml
      body.en.html
      body.pt-BR.html
      body.pt.html
    password-reset/
      template.yml
      body.en.html
      body.pt.html
      body.pt-BR.html
    order-confirmation/
      template.yml
      body.en.html
      body.pt-BR.html
  push/
    chat-notification/
      template.yml
      body.en.txt
      body.pt-BR.txt
```

## Template YAML Schema

Each template directory must contain a `template.yml` file with the following structure:

```yaml
key: user.welcome                    # Unique identifier (dot-separated)
channel: EMAIL                       # EMAIL, PUSH, WHATSAPP, IN_APP, CHAT
metadata:
  name: "User Welcome Email"
  description: "Sent when user completes signup"
  owner: "authentication-team"

variables:                           # Required variables with types
  - name: userName
    type: STRING
    required: true
  - name: verificationUrl
    type: URL
    required: true
  - name: expiresInHours
    type: INTEGER
    required: false
    default: 24

locales:                             # Locale-specific content
  en:
    subject: "Welcome to Neotool, {{userName}}!"
    bodyPath: "body.en.html"
  pt-BR:
    subject: "Bem-vindo ao Neotool, {{userName}}!"
    bodyPath: "body.pt-BR.html"

defaultLocale: en

channelConfig:
  email:
    format: HTML                     # HTML, TEXT, MULTIPART
    inlineCss: true
    trackOpens: false
```

## Variable Substitution

Templates use Mustache syntax for variable substitution:

- `{{variableName}}` - Simple variable (HTML escaped by default)
- `{{{rawHtml}}}` - Raw HTML (use with caution, no escaping)
- `{{#if condition}}...{{/if}}` - Conditional sections
- `{{#each items}}...{{/each}}` - Iteration

### Variable Types

- `STRING` - Text content
- `INTEGER` - Numeric values
- `BOOLEAN` - True/false values
- `URL` - URLs (validated)
- `DATE` - Date values
- `JSON` - Complex objects

## Channel-Specific Guidelines

### Email Templates

- Use HTML format with inline CSS (required for email client compatibility)
- Include both HTML and plain text versions for multipart emails
- Keep subject lines under 78 characters
- Use responsive design patterns
- Test in multiple email clients

Example:
```html
<!DOCTYPE html>
<html>
<head>
  <style>
    .button { background: #007bff; color: white; padding: 12px 24px; }
  </style>
</head>
<body>
  <h1>Welcome, {{userName}}!</h1>
  <a href="{{verificationUrl}}" class="button">Verify Account</a>
</body>
</html>
```

### Push Notification Templates

- Separate title and body fields
- Keep title under 50 characters (iOS limit)
- Keep body under 178 characters (iOS limit)
- Support badge count variables
- Format deep links correctly

### WhatsApp Templates

- Plain text format
- Maximum 4096 characters per message
- Support WhatsApp markdown: `*bold*`, `_italic_`, `~strikethrough~`
- Ensure links are clickable

### In-App Notification Templates

- Support markdown formatting
- Up to 3 action buttons
- Icon and image URL support
- XSS prevention enforced

### Chat Templates

- Plain text only
- Maximum 4000 characters
- Emoji support (Unicode preserved)

## Security Best Practices

1. **HTML Escaping**: Variables are HTML-escaped by default. Use `{{{rawHtml}}}` only when necessary and validate input.

2. **Path Traversal Prevention**: Template body files must be within the template directory.

3. **Variable Validation**: Always define required variables and validate input at the API boundary.

4. **XSS Prevention**: Never trust user input. Always escape variables unless explicitly using raw syntax.

## Locale Resolution

The template engine uses fallback resolution:

1. Try exact match (e.g., `pt-BR`)
2. Try language only (e.g., `pt`)
3. Try default locale (e.g., `en`)
4. Error if no match found

## Testing Templates

Use the test utilities to test templates:

```kotlin
val registry = InMemoryTemplateRegistry()
val template = TemplateTestUtils.createTestEmailTemplate()
registry.register(template)
```

## Example Templates

See `service/kotlin/comms/src/main/resources/templates/` for example templates:

- `email/user-welcome/` - Welcome email template
- `email/password-reset/` - Password reset email template
- `email/order-confirmation/` - Order confirmation email

### Password Reset Email

Located in `email/password-reset/`, this template is used for password reset requests.

**Template Key:** `auth.password-reset`

**Variables:**

- `resetUrl` (URL, required) - Password reset link with token
- `expiresInMinutes` (INTEGER, optional, default: 60) - Link expiration time

**Supported Locales:** `en`, `pt`, `pt-BR`

**Usage Example:**

```kotlin
templateService.renderTemplate(
    templateKey = "auth.password-reset",
    locale = Locale("pt", "BR"),
    channel = Channel.EMAIL,
    variables = mapOf(
        "resetUrl" to "https://app.neotool.io/reset?token=abc123",
        "expiresInMinutes" to 60
    )
)
```

## Troubleshooting

### Template Not Found

- Verify template key matches exactly
- Check channel is correct
- Ensure locale is supported or fallback exists

### Variable Substitution Fails

- Check all required variables are provided
- Verify variable names match exactly (case-sensitive)
- Check variable types match definitions

### CSS Not Inlined

- Ensure `inlineCss: true` in channelConfig
- Check CSS is in `<style>` tags (not external files)
- Verify HTML structure is valid