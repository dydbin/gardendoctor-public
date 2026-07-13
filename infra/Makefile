.DEFAULT_GOAL := help

INFRA_DIR := $(abspath $(dir $(lastword $(MAKEFILE_LIST))))
ROOT_DIR := $(abspath $(INFRA_DIR)/..)
ENV_FILE ?= $(INFRA_DIR)/.env
ENV_EXAMPLE := $(INFRA_DIR)/.env.example
COMPOSE_FILE := $(INFRA_DIR)/compose.yaml
FIREBASE_COMPOSE_FILE := $(INFRA_DIR)/compose.firebase.yaml
COMPOSE := docker compose --env-file "$(ENV_FILE)" -f "$(COMPOSE_FILE)"
COMPOSE_EXAMPLE := docker compose --env-file "$(ENV_EXAMPLE)" -f "$(COMPOSE_FILE)"
COMPOSE_FIREBASE := docker compose --env-file "$(ENV_FILE)" -f "$(COMPOSE_FILE)" -f "$(FIREBASE_COMPOSE_FILE)"
COMPOSE_FIREBASE_EXAMPLE := docker compose --env-file "$(ENV_EXAMPLE)" -f "$(COMPOSE_FILE)" -f "$(FIREBASE_COMPOSE_FILE)"

.PHONY: help env-check public-check compose-check firebase-check firebase-secret-check app-config app-run app-get app-generate app-format app-analyze app-test app-build app-build-local app-check backend-test backend-build backend-check backend-run backend-schema-bootstrap backend-up backend-smoke ai-syntax ai-build ai-run ai-up ai-down ai-smoke infra-up infra-down observability-up observability-down loadtest-bootrun loadtest-seed loadtest-run loadtest-compare loadtest-smoke stack-up stack-up-firebase stack-down stack-smoke verify verify-full

help:
	@echo "GardenDoctor infrastructure commands"
	@echo "  make public-check       Check the tree for public-release blockers"
	@echo "  make app-check          Format, analyze, and test the Flutter app"
	@echo "  make app-config         Render mobile-safe config from infra/.env"
	@echo "  make backend-check      Run Backend unit and source diagnostics"
	@echo "  make ai-syntax          Parse all Python sources without importing them"
	@echo "  make stack-up           Build and start MySQL, Redis, Backend, and AI"
	@echo "  make stack-up-firebase  Start the stack with external Firebase credentials"
	@echo "  make firebase-secret-check  Validate the external Firebase JSON path"
	@echo "  make observability-up   Start the stack with Prometheus and Grafana"
	@echo "  make loadtest-smoke     Run the k6 smoke profile"
	@echo "  make stack-smoke        Check Backend and AI health endpoints"

env-check:
	@test -f "$(ENV_FILE)" || (echo "Missing infra env file. Run: cp $(ENV_EXAMPLE) $(INFRA_DIR)/.env" >&2; exit 1)

public-check:
	"$(ROOT_DIR)/scripts/check-public-tree.sh"

compose-check:
	$(COMPOSE_EXAMPLE) config -q
	$(COMPOSE_EXAMPLE) --profile observability config -q
	$(COMPOSE_EXAMPLE) --profile loadtest config -q

firebase-check:
	$(COMPOSE_FIREBASE_EXAMPLE) config -q

firebase-secret-check: env-check
	bash "$(INFRA_DIR)/scripts/firebase/check-secret.sh" "$(ENV_FILE)"

app-config: env-check
	python3 "$(INFRA_DIR)/scripts/render-mobile-config.py" --env-file "$(ENV_FILE)" --output "$(INFRA_DIR)/generated/mobile/app.local.json"

app-run: app-config
	bash "$(INFRA_DIR)/scripts/mobile/flutter.sh" run "$(INFRA_DIR)/generated/mobile/app.local.json"

app-get:
	cd "$(ROOT_DIR)/apps/mobile" && flutter pub get --enforce-lockfile

app-generate:
	cd "$(ROOT_DIR)/apps/mobile" && dart run build_runner build --delete-conflicting-outputs

app-format:
	cd "$(ROOT_DIR)/apps/mobile" && dart format --output=none --set-exit-if-changed lib test

app-analyze:
	cd "$(ROOT_DIR)/apps/mobile" && flutter analyze --no-fatal-infos --no-fatal-warnings

app-test:
	cd "$(ROOT_DIR)/apps/mobile" && flutter test

app-build:
	cd "$(ROOT_DIR)/apps/mobile" && flutter build apk --debug --dart-define-from-file="$(INFRA_DIR)/config/mobile/public.json"

app-build-local: app-config
	bash "$(INFRA_DIR)/scripts/mobile/flutter.sh" build-local "$(INFRA_DIR)/generated/mobile/app.local.json"

app-check: app-format app-analyze app-test

backend-test:
	cd "$(ROOT_DIR)/services/backend" && GRADLE_USER_HOME=$${GRADLE_USER_HOME:-/tmp/gd-gradle} ./gradlew test portfolioDiagnostics --no-daemon

backend-build:
	cd "$(ROOT_DIR)/services/backend" && GRADLE_USER_HOME=$${GRADLE_USER_HOME:-/tmp/gd-gradle} ./gradlew bootJar --no-daemon

backend-check: backend-test

backend-run: env-check
	bash "$(INFRA_DIR)/scripts/backend/bootrun.sh" "$(ENV_FILE)"

backend-schema-bootstrap: env-check
	bash "$(INFRA_DIR)/scripts/backend/schema-bootstrap.sh" "$(ENV_FILE)"

backend-up: env-check
	$(COMPOSE) up -d --build mysql redis ai backend

backend-smoke:
	"$(INFRA_DIR)/scripts/smoke-backend.sh"

ai-syntax:
	"$(ROOT_DIR)/scripts/check-python-syntax.sh"

ai-build:
	$(COMPOSE_EXAMPLE) build ai

ai-run: env-check
	bash "$(INFRA_DIR)/scripts/ai/run.sh" "$(ENV_FILE)"

ai-up: env-check
	$(COMPOSE) up -d --build ai

ai-down: env-check
	$(COMPOSE) stop ai
	$(COMPOSE) rm -f ai

ai-smoke:
	"$(INFRA_DIR)/scripts/smoke-ai.sh"

infra-up: env-check
	$(COMPOSE) up -d mysql redis

infra-down: env-check
	$(COMPOSE) stop mysql redis
	$(COMPOSE) rm -f mysql redis

observability-up: env-check
	bash "$(INFRA_DIR)/scripts/observability/up.sh" "$(ENV_FILE)"

observability-down: env-check
	bash "$(INFRA_DIR)/scripts/observability/down.sh" "$(ENV_FILE)"

loadtest-bootrun: env-check
	bash "$(INFRA_DIR)/scripts/loadtest/bootrun.sh" "$(ENV_FILE)"

loadtest-seed: env-check
	bash "$(INFRA_DIR)/scripts/loadtest/seed.sh" "$(ENV_FILE)"

loadtest-run: env-check
	bash "$(INFRA_DIR)/scripts/loadtest/run.sh" diary-cursor "$(ENV_FILE)"

loadtest-compare: env-check
	bash "$(INFRA_DIR)/scripts/loadtest/compare.sh" "$(ENV_FILE)"

loadtest-smoke: observability-up
	bash "$(INFRA_DIR)/scripts/loadtest/run.sh" smoke "$(ENV_FILE)"

stack-up: env-check
	$(COMPOSE) up -d --build

stack-up-firebase: firebase-secret-check
	$(COMPOSE_FIREBASE) up -d --build

stack-down: env-check
	$(COMPOSE) down

stack-smoke: backend-smoke ai-smoke

verify: public-check compose-check firebase-check ai-syntax backend-check app-get app-generate app-check

verify-full:
	bash "$(INFRA_DIR)/scripts/verify-full.sh"
