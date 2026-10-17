.DEFAULT_GOAL := build
EXTENSION ?= electricity-prices

.PHONY: build test check list

build:
	python3 tools/package.py $(EXTENSION)

test:
	cargo test --workspace --locked
	python3 tools/check.py

check:
	cargo fmt --all -- --check
	cargo clippy --workspace --all-targets --locked -- -D warnings
	$(MAKE) test

list:
	python3 tools/package.py --list
