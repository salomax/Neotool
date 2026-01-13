# K3S Quick Start Guide

Your K3S cluster is ready! Follow these steps to get your foundation up and running.

## ✅ Prerequisites Check

```bash
# Set your kubeconfig
export KUBECONFIG=~/.kube/config-hostinger

# Verify cluster access
kubectl get nodes
```

Expected output: `neotool-hostinger-node-0   Ready   control-plane,etcd`

## 🚀 Quick Setup (5 minutes)

### Step 1: Run Foundation Script

```bash
cd infra/kubernetes/scripts
./setup-foundations.sh
```

This will:
- ✓ Verify cluster connection
- ✓ Create all namespaces
- ✓ Check storage and ingress

### Step 2: Verify Foundation

```bash
# Check namespaces
kubectl get namespaces | grep neotool

# Check storage class
kubectl get storageclass

# Check Traefik ingress
kubectl get pods -n kube-system -l app.kubernetes.io/name=traefik
```

## 📋 What's Next?

You have **3 deployment options**:

### Option A: Start with Observability (Recommended)
Good for understanding what's happening in your cluster first.

```bash
kubectl apply -f base/observability/prometheus/deployment.yaml
kubectl apply -f base/observability/loki/loki-statefulset.yaml
kubectl apply -f base/observability/promtail/promtail-daemonset.yaml
kubectl apply -f base/observability/grafana/deployment.yaml
```

### Option B: Start with Security (Vault)
If you need secrets management right away.

```bash
kubectl apply -f base/vault/
```

### Option C: Deploy Everything
Use Kustomize to deploy the full stack.

```bash
kubectl apply -k overlays/prod/
```

⚠️ **Warning**: Deploying everything at once on a small VPS may cause resource issues. Start with Option A or B.

## 📊 Monitor Your Cluster

```bash
# Watch all pods
kubectl get pods -A -w

# Check resource usage (requires metrics-server)
kubectl top nodes
kubectl top pods -A

# Check what's consuming resources
kubectl describe node neotool-hostinger-node-0
```

## 🔧 Common Commands

```bash
# Check pod logs
kubectl logs -f <pod-name> -n <namespace>

# Execute into a pod
kubectl exec -it <pod-name> -n <namespace> -- /bin/sh

# Port forward a service
kubectl port-forward -n <namespace> svc/<service-name> 8080:80

# Delete a pod (will be recreated by deployment)
kubectl delete pod <pod-name> -n <namespace>

# Restart a deployment
kubectl rollout restart deployment/<name> -n <namespace>
```

## 🆘 Troubleshooting

### Pods Stuck in Pending

```bash
# Check why
kubectl describe pod <pod-name> -n <namespace>

# Common causes:
# - Insufficient resources
# - Image pull errors
# - Volume mount issues
```

### Out of Memory

```bash
# Check memory usage
kubectl top nodes
kubectl top pods -A --sort-by=memory

# Scale down or delete unnecessary pods
kubectl scale deployment <name> -n <namespace> --replicas=0
```

### Service Not Accessible

```bash
# Check service and endpoints
kubectl get svc -n <namespace>
kubectl get endpoints <service-name> -n <namespace>

# Test connectivity
kubectl run -it --rm debug --image=curlimages/curl --restart=Never -- sh
# Then: curl http://<service-name>.<namespace>.svc.cluster.local
```

## 📚 Full Documentation

- **Detailed deployment guide**: [DEPLOYMENT.md](./DEPLOYMENT.md)
- **Terraform setup**: [../terraform/hostinger/README.md](../terraform/hostinger/README.md)

## 🎯 Recommended First Steps

1. **Deploy observability stack** (15 min)
   - Prometheus, Loki, Grafana
   - Gives you visibility into cluster health

2. **Deploy Vault** (10 min)
   - Set up secrets management
   - Initialize and unseal Vault

3. **Deploy PostgreSQL** (10 min)
   - Your primary database
   - Consider PgBouncer for connection pooling

4. **Deploy MinIO** (5 min)
   - Object storage for files

5. **Deploy your applications** (varies)
   - Start with stateless services first
   - Add stateful services gradually

## 💡 Tips

- **Start small**: Don't deploy everything at once
- **Monitor resources**: Keep an eye on memory/CPU usage
- **Set resource limits**: All deployments should have requests/limits
- **Use labels**: Makes filtering and querying easier
- **Test locally first**: Use `kubectl apply --dry-run=client` to validate
- **Back up secrets**: Especially Vault keys and database credentials

## 🎉 Success Indicators

Your foundation is properly set up when:
- ✓ All namespaces exist
- ✓ Storage class is available
- ✓ Traefik ingress is running
- ✓ You can deploy and access a test pod
- ✓ Resource usage is stable

Happy deploying! 🚀
