# kahn-queue — polyglot monorepo
# Expects a POSIX shell (Git Bash, WSL, Linux, macOS) for `cd … &&` and `source .venv/...`.

GRADLE := cd java && ./gradlew
GO := cd go &&
PY := cd python &&
TS := cd typescript &&

.DEFAULT_GOAL := help

.PHONY: help build test clean \
	build-java test-java clean-java \
	build-go test-go clean-go \
	build-python test-python clean-python \
	build-typescript test-typescript clean-typescript \
	patch minor major \
	retry-version retryVersion

help:
	@echo "Per language (build, test, clean):"
	@echo "  make build-java | test-java | clean-java"
	@echo "  make build-go   | test-go   | clean-go"
	@echo "  make build-python | test-python | clean-python"
	@echo "  make build-typescript | test-typescript | clean-typescript"
	@echo ""
	@echo "All languages:"
	@echo "  make build   make test   make clean"
	@echo ""
	@echo "Release helpers (one commit + one tag per language):"
	@echo "  make patch typescript python java    # or: make minor go"
	@echo ""
	@echo "Retry release tag (delete + recreate + push same tag on HEAD; needs origin):"
	@echo "  make retry-version java    # or: make retryVersion java"

# --- Java --------------------------------------------------------------------

build-java:
	$(GRADLE) assemble

test-java:
	$(GRADLE) test

clean-java:
	$(GRADLE) clean

# --- Go ----------------------------------------------------------------------

build-go:
	$(GO) go build ./...

test-go:
	$(GO) go test ./...

clean-go:
	$(GO) go clean -testcache ./...

# --- Python ------------------------------------------------------------------

build-python:
	$(PY) ( test -d .venv || python -m venv .venv ) && . .venv/bin/activate && python -m pip install -q -r requirements-dev.txt && python -m compileall -q src

test-python:
	$(PY) ( test -d .venv || python -m venv .venv ) && . .venv/bin/activate && python -m pip install -q -r requirements-dev.txt && python -m pytest

clean-python:
	$(PY) python -c "import pathlib, shutil; [shutil.rmtree(p) for p in pathlib.Path('.').rglob('__pycache__')]; shutil.rmtree('.pytest_cache', ignore_errors=True)"

# --- TypeScript --------------------------------------------------------------

build-typescript:
	$(TS) npm install && npm run build

test-typescript:
	$(TS) npm install && npm test

clean-typescript:
	$(TS) rm -rf dist

# --- All languages -------------------------------------------------------------

build: build-java build-go build-python build-typescript

test: test-java test-go test-python test-typescript

clean: clean-java clean-go clean-python clean-typescript

# --- Release helpers ----------------------------------------------------------
# Multi-language: `make patch typescript python` (extra words are goals; swallowed by % rule at end).
patch minor major:
	./scripts/bump-version.sh $@ $(filter-out patch minor major,$(MAKECMDGOALS))

retry-version retryVersion:
	./scripts/retry-version.sh $(filter-out retry-version retryVersion,$(MAKECMDGOALS))

# Swallow extra words from `make patch ts py` / `make retry-version java` (must be last).
%:
	@:
