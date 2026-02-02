---
title: Cloudflare CDN Runbook
id: cloudflare-cdn-runbook
type: infrastructure
category: networking
status: current
version: 1.0.0
tags: [cloudflare, dns, cdn, https, k3s, traefik, cert-manager]
ai_optimized: true
search_keywords: [cloudflare, dns, cdn, https, ssl, tls, hostinger, k3s, traefik]
related:
  - 11-infrastructure/hostinger-runbook.md
  - 11-infrastructure/k8s-runbook.md
  - 11-infrastructure/web-deployment-runbook.md
  - 02-architecture/infrastructure-architecture.md
last_updated: 2026-01-22
---

# Cloudflare CDN Runbook

> **Goal**: Create a Cloudflare user, onboard a domain, enable Cloudflare CDN, and route HTTPS traffic to the Hostinger K3S web app.

---

## Table of Contents

1. [Overview](#overview)
2. [Prerequisites](#prerequisites)
3. [Current Infra Assumptions](#current-infra-assumptions)
4. [Step 1: Collect Required Values](#step-1-collect-required-values)
5. [Step 2: Create a Cloudflare User](#step-2-create-a-cloudflare-user)
6. [Step 3: Add the Domain to Cloudflare](#step-3-add-the-domain-to-cloudflare)
7. [Step 4: Configure DNS Records (CDN)](#step-4-configure-dns-records-cdn)
8. [Step 5: Point the Ingress to Your Domain](#step-5-point-the-ingress-to-your-domain)
9. [Step 6: Ensure HTTPS in Kubernetes](#step-6-ensure-https-in-kubernetes)
10. [Step 7: Configure Cloudflare SSL/TLS](#step-7-configure-cloudflare-ssltls)
11. [Validation Checklist](#validation-checklist)
12. [Troubleshooting](#troubleshooting)
13. [Rollback](#rollback)

---

## Overview

This runbook connects **Cloudflare DNS/CDN** to the **Hostinger K3S** cluster so that:

```
Internet → Cloudflare (CDN + TLS) → Hostinger VPS (Traefik) → Next.js web app
```

Cloudflare handles DNS and edge caching. TLS is end-to-end using **cert-manager + Let’s Encrypt** in the cluster.

---

## Prerequisites

- Cloudflare account with access to the target zone
- Access to the domain registrar to change nameservers
- Access to the GitOps repo (`invistus-flux`) to update ingress host
- K8S access to production cluster (`kubectl get nodes` works)

---

## Current Infra Assumptions

Based on this repo:

- Traefik is the ingress controller (K3S default)
- cert-manager is used for TLS
- The web ingress is in:
  - `invistus-flux/infra/kubernetes/flux/apps/web/nextjs/ingress.yaml`
- TLS secret name: `neotool-web-tls`
- ClusterIssuer expected: `letsencrypt-prod`

If any of these differ, adjust the steps accordingly.

---

## Step 1: Collect Required Values

Gather the following before proceeding:

- **Domain name** (e.g., `example.com`)
- **VPS public IP** (Hostinger): from `invistus/infra/terraform/hostinger/terraform.tfvars` → `vps_ip`
- **Web app ingress host** (will replace `yourdomain.com`)

---

## Step 2: Create a Cloudflare User

1. Log in to Cloudflare.
2. Go to **Account** → **Members**.
3. Click **Invite Members**.
4. Enter the user email.
5. Assign a role:
   - **Admin** (full control) or
   - **DNS + Zone** (least-privilege, recommended)
6. Send the invite and confirm the user accepts it.

---

## Step 3: Add the Domain to Cloudflare

1. In Cloudflare, select **Add a Site**.
2. Enter your domain (e.g., `example.com`).
3. Choose the plan (Free is fine for CDN + TLS).
4. Cloudflare will provide **nameservers**.
5. Update nameservers at your registrar to the Cloudflare values.
6. Wait for Cloudflare to activate the zone (can take minutes to hours).

---

## Step 4: Configure DNS Records (CDN)

Create DNS records that point to the Hostinger VPS:

| Type | Name | Value | Proxy | Notes |
|------|------|-------|-------|-------|
| A | `@` | `VPS_IP` | **Proxied** | Apex domain → Hostinger VPS |
| CNAME | `www` | `@` | **Proxied** | www → apex |

**Notes**:
- If cert-manager is issuing HTTP-01 certificates, you may need to set records to **DNS only** temporarily during first issuance. Switch back to **Proxied** after certificates are ready.
- Keep TTL as **Auto** unless you need a faster rollback.

---

## Step 5: Point the Ingress to Your Domain

Update the ingress host in the GitOps repo so Traefik routes traffic correctly.

**File**: `invistus-flux/infra/kubernetes/flux/apps/web/nextjs/ingress.yaml`

Replace `yourdomain.com` in both `spec.rules.host` and `spec.tls.hosts`:

```yaml
rules:
  - host: example.com
    http:
      paths:
        - path: /
          pathType: Prefix
          backend:
            service:
              name: neotool-web
              port:
                number: 3000

...

tls:
  - hosts:
      - example.com
    secretName: neotool-web-tls
```

Commit and push to the `invistus-flux` repo so Flux applies the change.

---

## Step 6: Ensure HTTPS in Kubernetes

Confirm cert-manager and the ClusterIssuer are present:

```bash
kubectl get pods -n cert-manager
kubectl get clusterissuer letsencrypt-prod
```

Then check that the certificate for the domain is issued:

```bash
kubectl get certificate -n production
kubectl describe certificate neotool-web-tls -n production
```

Expected:
- `Ready=True` for the certificate
- Secret `neotool-web-tls` exists in `production`

---

## Step 7: Configure Cloudflare SSL/TLS

1. Go to **SSL/TLS** → **Overview**.
2. Set SSL mode to **Full (strict)**.
3. Go to edge-certificates and enable:
   - **Always Use HTTPS**
   - **Automatic HTTPS Rewrites**
4. Optional hardening:
   - **HSTS** (after validating HTTPS)
   - **Minimum TLS Version** = 1.2+

**Why Full (strict)?**
The origin (K3S) serves a valid Let’s Encrypt certificate via cert-manager, so strict mode ensures end-to-end TLS.

---

## Validation Checklist

- DNS resolves to Cloudflare (should show Cloudflare IPs):
  ```bash
  dig +short example.com
  ```
- HTTPS returns 200 with correct cert:
  ```bash
  curl -I https://example.com
  ```
- Ingress shows correct host:
  ```bash
  kubectl get ingress -n production neotool-web
  ```
- Certificate is ready:
  ```bash
  kubectl get certificate -n production
  ```

---

## Troubleshooting

**Issue: Cloudflare shows 525/526 (SSL handshake / invalid cert)**
- Cause: Cloudflare expects valid origin cert.
- Fix: Ensure cert-manager issued `neotool-web-tls` and set SSL mode to **Full (strict)** only after cert is ready.

**Issue: Let’s Encrypt HTTP-01 challenge fails**
- Cause: Cloudflare proxy can interfere with HTTP-01.
- Fix: Temporarily set DNS record to **DNS only**, re-issue certificate, then re-enable proxy.

**Issue: 404 or wrong backend**
- Cause: Ingress host mismatch.
- Fix: Ensure `spec.rules.host` matches the DNS host exactly.

**Issue: Cloudflare cached old content**
- Fix: Purge cache in Cloudflare or set a development cache rule.

---

## Rollback

- Switch Cloudflare DNS records to **DNS only** to bypass CDN.
- Revert the ingress host change in `invistus-flux/infra/kubernetes/flux/apps/web/nextjs/ingress.yaml`.
- If needed, point DNS back to the previous origin IP.

