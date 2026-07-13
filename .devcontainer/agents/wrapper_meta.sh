#!/usr/bin/env bash
# Emit machine-readable metadata on stderr for CLIBackend (tokens, exit hint).
# Usage: emit_wrapper_meta <tokens_in> <tokens_out> [exit_code]
emit_wrapper_meta() {
    local in_tok="${1:-0}"
    local out_tok="${2:-0}"
    local exit_code="${3:-0}"
    printf '@@NEOTOOL@@{"tokens_in":%s,"tokens_out":%s,"exit_code":%s}\n' \
        "$in_tok" "$out_tok" "$exit_code" >&2
}
