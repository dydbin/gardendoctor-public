.DEFAULT_GOAL := help

INFRA_TARGETS := \
	help env-check public-check compose-check firebase-check firebase-secret-check \
	app-config app-run app-get app-generate app-format app-analyze app-test \
	app-build app-build-local app-check \
	backend-test backend-build backend-check backend-run backend-schema-bootstrap backend-up backend-smoke \
	ai-syntax ai-build ai-run ai-up ai-down ai-smoke \
	infra-up infra-down observability-up observability-down \
	loadtest-bootrun loadtest-seed loadtest-run loadtest-compare loadtest-smoke \
	stack-up stack-up-firebase stack-down stack-smoke verify verify-full

.PHONY: $(INFRA_TARGETS)

$(INFRA_TARGETS):
	+@$(MAKE) --no-print-directory -C infra $@
