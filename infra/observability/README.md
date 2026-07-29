# Observability Configuration

This directory contains all observability-related configurations for the NeoTool project.

## 📁 Structure

```
observability/
├── prometheus/
│   └── prometheus.yml          # Prometheus configuration
├── grafana/
│   ├── dashboards/
│   │   └── neotool-overview.json  # Main system dashboard
│   └── provisioning/
│       ├── datasources/
│       │   └── datasource.yml     # Grafana data sources
│       └── dashboards/
│           └── dashboard.yml      # Dashboard provisioning
```

## 🎯 Purpose

- **Prometheus**: Metrics collection and storage
- **Grafana**: Visualization and dashboards
- **Dashboards**: Pre-configured monitoring views
- **Provisioning**: Automatic configuration loading

## 🚀 Usage

The observability stack is automatically started with:

```bash
cd infra/docker
docker-compose -f docker-compose.local.yml up -d
```

## 📊 Access Points

- **Grafana Dashboard**: http://localhost:3001 (admin/admin)
- **Prometheus Metrics**: http://localhost:9090
- **System Dashboard**: NeoTool - System Overview

## 🔧 Configuration

### Adding New Metrics
1. Update `prometheus/prometheus.yml` to add new scrape targets
2. Restart Prometheus: `docker-compose restart prometheus`

### Adding New Dashboards
1. Create dashboard JSON in `grafana/dashboards/`
2. Restart Grafana: `docker-compose restart grafana`

### Alert Notification Gap
- The local and production Prometheus setups currently evaluate alert rules without any Alertmanager or other notification sink.
- A firing rule is visible in the Prometheus/Grafana UI only; it does not page, email, or message anyone yet.

### Monitoring Services
- **API**: Micrometer metrics (when actuator endpoints are fixed)
- **PostgreSQL**: postgres-exporter
- **Redis**: redis-exporter  
- **Kafka**: kafka-exporter
- **Router**: Built-in metrics

## 📈 Benefits

- **Centralized**: All observability configs in one place
- **Version Controlled**: Easy to track changes
- **Reusable**: Can be used with different deployment methods
- **Maintainable**: Clear separation from Docker-specific configs
