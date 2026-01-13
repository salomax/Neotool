# Network Policies

Este diretório contém as Network Policies que controlam o tráfego de rede entre pods no namespace `production`.

## Políticas Implementadas

### 1. PostgreSQL - `postgres-allow-pgbouncer.yaml`
**Objetivo**: Apenas PgBouncer pode conectar diretamente no PostgreSQL.

**Permite**:
- PgBouncer → PostgreSQL (porta 5432)
- Health checks do kubelet

**Bloqueia**:
- Aplicações conectando diretamente no PostgreSQL (devem usar PgBouncer)

### 2. PgBouncer - `pgbouncer-allow-apps.yaml`
**Objetivo**: Aplicações podem conectar no PgBouncer.

**Permite**:
- Services (component: service) → PgBouncer (porta 6432)
- Web (component: web) → PgBouncer (porta 6432)
- PgBouncer → PgBouncer (health checks)
- Health checks do kubelet

**Nota**: Aplicações precisam ter o label `component: service` ou `component: web` para funcionar.

### 3. Vault - `vault-restrict-access.yaml`
**Objetivo**: Apenas External Secrets Operator pode acessar Vault.

**Permite**:
- External Secrets Operator → Vault (porta 8200)
- Vault → Vault (para HA/clustering)
- Health checks do kubelet

**Bloqueia**:
- Aplicações acessando Vault diretamente (devem usar Kubernetes Secrets)

### 4. Kafka - `kafka-allow-producers-consumers.yaml`
**Objetivo**: Apenas aplicações autorizadas podem produzir/consumir mensagens.

**Permite**:
- Services (component: service) → Kafka (porta 9092)
- Web (component: web) → Kafka (porta 9092)
- Kafka → Kafka (portas 9092, 9093 para clustering)
- Health checks do kubelet

**Nota**: Aplicações precisam ter o label `component: service` ou `component: web` para funcionar.

## Labels Necessários

Para que as Network Policies funcionem corretamente, seus deployments precisam ter os seguintes labels:

### Para conectar no PgBouncer e Kafka:
```yaml
metadata:
  labels:
    component: service  # Para serviços Kotlin
    # ou
    component: web      # Para Next.js, Apollo Router
```

### Exemplo de Deployment:
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: meu-servico
  namespace: production
spec:
  template:
    metadata:
      labels:
        app: meu-servico
        component: service  # ← Necessário para Network Policies
    spec:
      containers:
      - name: app
        # ...
```

## Adicionando Novas Políticas

1. Crie o arquivo YAML da política neste diretório
2. Adicione o arquivo em `kustomization.yaml`
3. Commit e push - Flux aplicará automaticamente

## Troubleshooting

### Pod não consegue conectar
1. Verifique se o pod tem os labels corretos (`component: service` ou `component: web`)
2. Verifique se a Network Policy está aplicada:
   ```bash
   kubectl get networkpolicy -n production
   ```
3. Verifique os logs do pod:
   ```bash
   kubectl logs <pod-name> -n production
   ```

### Testando uma conexão
```bash
# Testar conexão do pod A para o pod B
kubectl exec -n production <pod-a> -- nc -zv <service-b>.<namespace>.svc.cluster.local <port>
```

## Próximos Passos

- [ ] Adicionar políticas para observability (Prometheus, Grafana, Loki, Promtail)
- [ ] Adicionar política padrão "deny all" (mais restritiva)
- [ ] Revisar e ajustar políticas conforme necessário
