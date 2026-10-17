.DEFAULT_GOAL := build
EXTENSION ?= electricity-prices

.PHONY: build release test check list

build:
	python3 tools/package.py $(EXTENSION)

release: build
	python3 tools/release.py $(EXTENSION)

test:
	cargo test --workspace --locked
	python3 tools/check.py

check:
	cargo fmt --all -- --check
	cargo clippy --workspace --all-targets --locked -- -D warnings
	$(MAKE) test

list:
	python3 tools/package.py --list
