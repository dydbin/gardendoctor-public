# GardenDoctor Infrastructure

`infra/` is the single operations entrypoint for `apps/**` and `services/**`.
Docker Compose manages the long-running services, while this Makefile also
delegates Mobile configuration, execution, tests, and builds to the host Flutter
toolchain.

Runtime configuration is physically owned here:

- `config/backend/`: Spring Boot base and performance profile properties
- `config/mobile/` and `generated/mobile/`: public and ignored local projections
- `config/observability/`: Prometheus and Grafana provisioning
- `docker/`: AI and Backend Dockerfiles and build-context policies
- `loadtest/`: k6 scenarios, baselines, aggregation, and tests
- `scripts/`: all host-side service orchestration

## Setup

From the repository root:

```bash
cp infra/.env.example infra/.env
make -C infra compose-check
make -C infra stack-up
make -C infra stack-smoke
```

The root `Makefile` is a compatibility facade, so `make stack-up` is equivalent
to `make -C infra stack-up`. Use `ENV_FILE=/absolute/path/to/file` only when an
explicit alternate environment file is required.

## Managed targets

- `make -C infra app-config|app-run|app-build-local`: render the mobile-safe
  allowlist and operate the Flutter app on the host.
- `make -C infra backend-check|backend-up|backend-smoke`: verify and operate the
  Backend service.
- `make -C infra ai-syntax|ai-build|ai-up|ai-smoke`: verify and operate the AI
  service.
- `make -C infra infra-up|stack-up|stack-down`: operate data services or the
  complete application stack.
- `make -C infra observability-up|observability-down|loadtest-smoke`: operate
  Prometheus, Grafana, and k6 profiles.
- `make -C infra firebase-check|firebase-secret-check|stack-up-firebase`: validate
  and use the optional Firebase Compose override with an external read-only
  service-account JSON.

Mobile is not a daemon and is intentionally not modeled as a Compose service.
Only the keys allowlisted by `scripts/render-mobile-config.py` are copied from
`infra/.env` into `generated/mobile/app.local.json`; server secrets stay out of
the app bundle. Service source directories consume environment variables and do
not locate or parse dotenv files themselves.

For FCM delivery, set `FIREBASE_SERVICE_ACCOUNT_HOST_PATH` in `infra/.env` to an
absolute path outside this repository. `compose.firebase.yaml` mounts that file
read-only at `/run/secrets/firebase-admin.json`; the default Compose stack keeps
Firebase and the FCM outbox worker disabled.

## Production boundary

This Compose stack supports local development and a single-host baseline. Before
an Internet-facing deployment, review TLS/reverse proxying, managed secrets,
database migrations, backups and restores, resource limits, and log rotation.
Do not use placeholder credentials or `ddl-auto=update` in production.
