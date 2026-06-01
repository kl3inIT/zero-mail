# Zero Mail Observability Stack

This is the production self-hosted stack for a single VPS:

- Metrics: Spring Boot Actuator + Prometheus + node_exporter + cAdvisor + Postgres/Redis exporters.
- Logs: Docker json logs shipped by Grafana Alloy to Loki.
- Traces: Spring Boot OpenTelemetry OTLP HTTP to Alloy, then Alloy to Tempo.
- UI: Grafana with Prometheus, Loki, and Tempo provisioned as datasources.

Prompts, completions, and email bodies must never be logged. Spring AI prompt/completion capture stays disabled in both API and worker configs.

## Retention Defaults

- Prometheus metrics: `14d`, capped at `8GB`.
- Loki logs: `7d` through the Loki compactor.
- Tempo traces: `7d` through Tempo compaction.

On an 8 GB VPS this is usable but tight. On 16 GB it is the expected production shape. If memory or disk pressure shows up, reduce Tempo to `72h` first, then Loki to `72h`; keep Prometheus at 7-14 days because Hikari, JVM, and HTTP latency history are the most useful operational signals.

## Host Prep

Create the shared Docker networks once:

```bash
docker network create zeromail-internal || true
docker network create proxy-network || true
```

Enable a small host swap file even on 16 GB. Keep Java containers from swapping via `memswap_limit`, but let the host survive short observability spikes:

```bash
sudo fallocate -l 4G /swapfile
sudo chmod 600 /swapfile
sudo mkswap /swapfile
sudo swapon /swapfile
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
echo 'vm.swappiness=10' | sudo tee /etc/sysctl.d/99-zeromail-swap.conf
sudo sysctl --system
```

## Environment

Use the normal root `.env` for app secrets. Add the observability variables from:

```bash
deploy/compose/.env.observability.example
```

At minimum, set a real `GRAFANA_ADMIN_PASSWORD`. For production, pin image digests or tested tags before deployment.

When migrating from the old root `docker-compose.yml`, confirm the existing data volume names first:

```bash
docker volume ls | grep zero-mail
```

Then set `POSTGRES_VOLUME_NAME` and `REDIS_VOLUME_NAME` to those exact names before starting `prod.infra.yml`. The defaults assume the old project directory was named `zero-mail`.

## Deploy

Infra, app, and observability are split so Postgres/Redis can be upgraded/backed up independently from app releases:

```bash
docker compose --env-file .env -f deploy/compose/prod.infra.yml up -d
docker compose --env-file .env -f deploy/compose/prod.app.yml up -d
docker compose --env-file .env -f deploy/compose/observability.yml up -d
```

If observability variables live in a second env file, pass both env files:

```bash
docker compose --env-file .env --env-file deploy/compose/.env.observability -f deploy/compose/observability.yml up -d
```

Grafana binds to `127.0.0.1:3001` by default. Expose it through Nginx Proxy Manager only if the route is protected; otherwise use an SSH tunnel:

```bash
ssh -L 3001:127.0.0.1:3001 deploy@your-vps
```

## Verify

```bash
docker compose --env-file .env -f deploy/compose/prod.infra.yml config
docker compose --env-file .env -f deploy/compose/prod.app.yml config
docker compose --env-file .env --env-file deploy/compose/.env.observability -f deploy/compose/observability.yml config

curl -fsS http://127.0.0.1:3001/api/health
curl -fsS http://127.0.0.1:9090/-/ready
```

In Grafana, check:

- Prometheus target health: `Status -> Targets`.
- JVM/Hikari metrics from `zeromail-api` and `zeromail-worker`.
- Loki labels `container`, `service`, and `compose_project`.
- Tempo traces with `service.name=zeromail-api` or `service.name=zeromail-worker`.

## Alerts To Add First

- `hikaricp_connections_pending > 0` for API or worker.
- API/worker process down.
- 5xx rate or p95 HTTP latency spike.
- Disk available below 15%.
- Prometheus target down.
- Loki/Tempo container restart loop.

## Research Sources

Checked on 2026-06-01:

- Spring Boot 4 actuator tracing and Prometheus docs: `spring-boot-starter-opentelemetry`, OTLP properties, and `/actuator/prometheus`.
- Grafana Alloy docs: OTLP receiver/exporter pipeline and Docker log collection through `discovery.docker` + `loki.source.docker`.
- Grafana Loki docs: filesystem TSDB setup and compactor retention.
- Grafana Tempo release/config docs: Tempo 2.10.x local storage + compactor retention. Tempo 3.0 was still release-candidate, so this stack stays on 2.10.5.
- GitHub release pages verified the pinned 2026 tags used here: Grafana 13.0.1, Prometheus 3.12.0, Loki 3.7.2, Tempo 2.10.5, Alloy 1.16.1.
