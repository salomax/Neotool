# GitHub Token Setup for Flux

## Create Personal Access Token

1. **Go to GitHub Settings**:
   - Visit: https://github.com/settings/tokens
   - Click "Generate new token" → "Generate new token (classic)"

2. **Configure Token**:
   - **Note**: `Flux GitOps - Neotool`
   - **Expiration**: 90 days (or No expiration for production)
   - **Scopes** - Select these:
     - ✅ `repo` (Full control of private repositories)
       - This includes: repo:status, repo_deployment, public_repo, repo:invite, security_events

3. **Generate and Copy Token**:
   - Click "Generate token"
   - **COPY THE TOKEN NOW** - you won't see it again!
   - Example format: `ghp_xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx`

## Using the Token with Bootstrap

### Option 1: Interactive (Recommended)
```bash
cd /Users/salomax/src/Neotool/infra/kubernetes/flux
./bootstrap.sh
```

When prompted, paste the token. The script will:
- Not store it permanently
- Use it only for bootstrap
- Create a deploy key in GitHub (more secure for ongoing operations)

### Option 2: Environment Variable
```bash
export GITHUB_TOKEN=ghp_xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
cd /Users/salomax/src/Neotool/infra/kubernetes/flux
./bootstrap.sh
```

### Option 3: Pass as Parameter
```bash
cd /Users/salomax/src/Neotool/infra/kubernetes/flux
GITHUB_TOKEN=ghp_xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx ./bootstrap.sh
```

## Security Notes

- ✅ Token is **only needed once** during bootstrap
- ✅ After bootstrap, Flux uses a **deploy key** (more secure)
- ✅ The token is **never stored** in the repository
- ✅ You can **revoke the token** after successful bootstrap
- ⚠️  Never commit the token to Git
- ⚠️  Store it securely (password manager)

## What Flux Does with the Token

During bootstrap, Flux uses the token to:
1. Create a deploy key in your GitHub repository
2. Commit Flux manifests to `infra/kubernetes/flux/clusters/production/`
3. Set up webhook for automatic sync (optional)

**After bootstrap completes**, Flux switches to using the deploy key instead of the token.

## Troubleshooting

### "Bad credentials" error
- Token expired or revoked
- Token doesn't have `repo` scope
- Wrong token copied (check for spaces)

### "Resource not accessible by personal access token"
- Need `repo` scope (not just `public_repo`)
- For organization repos, may need admin approval

### "Repository not found"
- Check repository name spelling
- Ensure token has access to the repository
- For private repos, ensure `repo` scope is selected

## Next Steps After Bootstrap

Once bootstrapped, you don't need the token anymore:

```bash
# Verify Flux installation
flux check

# Watch Flux sync
flux get kustomizations --watch

# All future changes via Git
git commit -m "deploy app"
git push  # Flux auto-deploys!
```
