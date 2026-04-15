# kahn-queue — polyglot monorepo
# Expects a POSIX shell (Git Bash, WSL, Linux, macOS) for `cd … &&` and `source .venv/...`.

GRADLE := cd java && ./gradlew
GO := cd go &&
PY := cd python &&
TS := cd typescript &&

.DEFAULT_GOAL := help

.PHONY: help build test clean deploy \
	build-java test-java clean-java deploy-java \
	build-go test-go clean-go deploy-go \
	build-python test-python clean-python deploy-python \
	build-typescript test-typescript clean-typescript deploy-typescript \
	patch-java minor-java major-java \
	patch-python minor-python major-python \
	patch-typescript minor-typescript major-typescript \
	patch-go minor-go major-go

help:
	@echo "Per language (build, test, clean, deploy):"
	@echo "  make build-java | test-java | clean-java | deploy-java"
	@echo "  make build-go   | test-go   | clean-go   | deploy-go"
	@echo "  make build-python | test-python | clean-python | deploy-python"
	@echo "  make build-typescript | test-typescript | clean-typescript | deploy-typescript"
	@echo ""
	@echo "All languages:"
	@echo "  make build   make test   make clean   make deploy"
	@echo ""
	@echo "Release helpers (bumps metadata + commits + tags language/vX.Y.Z):"
	@echo "  make patch-java | minor-java | major-java"
	@echo "  make patch-python | minor-python | major-python"
	@echo "  make patch-typescript | minor-typescript | major-typescript"
	@echo "  make patch-go | minor-go | major-go"

# --- Java --------------------------------------------------------------------

build-java:
	$(GRADLE) assemble

test-java:
	$(GRADLE) test

clean-java:
	$(GRADLE) clean

deploy-java:
	$(GRADLE) publish

# --- Go ----------------------------------------------------------------------

build-go:
	$(GO) go build ./...

test-go:
	$(GO) go test ./...

clean-go:
	$(GO) go clean -testcache ./...

deploy-go:
	@echo "go: publish by pushing a semver tag for the module (e.g. go/v1.0.0); see https://go.dev/ref/modules#vcs"
	cd go && go list -m

# --- Python ------------------------------------------------------------------

build-python:
	$(PY) ( test -d .venv || python -m venv .venv ) && . .venv/bin/activate && python -m pip install -q -r requirements-dev.txt && python -m compileall -q src

test-python:
	$(PY) ( test -d .venv || python -m venv .venv ) && . .venv/bin/activate && python -m pip install -q -r requirements-dev.txt && python -m pytest

clean-python:
	$(PY) python -c "import pathlib, shutil; [shutil.rmtree(p) for p in pathlib.Path('.').rglob('__pycache__')]; shutil.rmtree('.pytest_cache', ignore_errors=True)"

deploy-python:
	@echo "python: add packaging (pyproject.toml), then: python -m build && twine upload dist/*"

# --- TypeScript --------------------------------------------------------------

build-typescript:
	$(TS) npm install && npm run build

test-typescript:
	$(TS) npm install && npm test

clean-typescript:
	$(TS) rm -rf dist

deploy-typescript:
	$(TS) npm install && npm run build && npm publish --access public

# --- Release helpers ----------------------------------------------------------

patch-java:
	./scripts/bump-version.sh patch java
minor-java:
	./scripts/bump-version.sh minor java
major-java:
	./scripts/bump-version.sh major java

patch-python:
	./scripts/bump-version.sh patch python
minor-python:
	./scripts/bump-version.sh minor python
major-python:
	./scripts/bump-version.sh major python

patch-typescript:
	./scripts/bump-version.sh patch typescript
minor-typescript:
	./scripts/bump-version.sh minor typescript
major-typescript:
	./scripts/bump-version.sh major typescript

patch-go:
	./scripts/bump-version.sh patch go
minor-go:
	./scripts/bump-version.sh minor go
major-go:
	./scripts/bump-version.sh major go

# --- All languages -------------------------------------------------------------

build: build-java build-go build-python build-typescript

test: test-java test-go test-python test-typescript

clean: clean-java clean-go clean-python clean-typescript

deploy: deploy-java deploy-go deploy-python deploy-typescript
