#!/usr/bin/env bash

set -euo pipefail

# Reporters / Renderers
#
# Transforms event stream into various output formats.
# live:    TTY-friendly fixed frame repainting
# plain:   one line per event (default non-TTY)
# json:    raw events as JSON objects
# github:  GitHub Actions ::group:: / ::error:: syntax

RENDER_REPORTER="${1:-auto}"
RENDER_LOG_DIR="${2:-}"

# Auto-detect reporter if needed
if [[ "$RENDER_REPORTER" == "auto" ]]; then
    if [[ -t 1 ]]; then
        RENDER_REPORTER="live"
    else
        RENDER_REPORTER="plain"
    fi
fi

# NOTE: RENDER_TASK_IDS is a space-separated STRING (set by validate.sh from
# "${TASK_IDS[*]}"), not an array — render_live word-splits it. It used to be
# pre-declared here with `declare -a`, which made the later scalar assignment
# land in element 0 and only worked by accident.

# Colors (assumed to be sourced from caller)
# RED, GREEN, YELLOW, CYAN, GRAY, BRIGHT, RESET

# Extract tool name from command (for error matching)
extract_tool() {
    local cmd="$1"
    if [[ "$cmd" =~ pnpm ]]; then
        if [[ "$cmd" =~ typecheck ]]; then
            echo "tsc"
        elif [[ "$cmd" =~ test ]]; then
            echo "vitest"
        elif [[ "$cmd" =~ lint ]]; then
            echo "eslint"
        else
            echo "pnpm"
        fi
    elif [[ "$cmd" =~ gradle|gradlew ]]; then
        if [[ "$cmd" =~ classes ]]; then
            echo "gradle"
        elif [[ "$cmd" =~ test ]]; then
            echo "gradle"
        elif [[ "$cmd" =~ ktlint ]]; then
            echo "ktlint"
        elif [[ "$cmd" =~ kover ]]; then
            echo "gradle"
        else
            echo "gradle"
        fi
    elif [[ "$cmd" =~ trivy ]]; then
        echo "trivy"
    elif [[ "$cmd" =~ playwright ]]; then
        echo "playwright"
    else
        echo "unknown"
    fi
}

# Extract relevant error lines from log file
extract_error_excerpt() {
    local log_file="$1"
    local tool="$2"

    if [[ ! -f "$log_file" ]]; then
        return 0
    fi

    local excerpt=""
    case "$tool" in
        tsc)
            excerpt=$(grep "error TS" "$log_file" | head -20 || true)
            ;;
        vitest)
            excerpt=$(grep -E "FAIL|AssertionError|expected .* to be" "$log_file" | head -20 || true)
            ;;
        eslint)
            excerpt=$(grep "error" "$log_file" | head -20 || true)
            ;;
        gradle|ktlint)
            excerpt=$(grep -E "e: |FAILURE:|What went wrong|Task .* FAILED|\.kt:[0-9]" "$log_file" | head -20 || true)
            ;;
        trivy)
            excerpt=$(grep -E "CRITICAL|HIGH" "$log_file" | head -20 || true)
            ;;
        *)
            excerpt=$(tail -30 "$log_file" || true)
            ;;
    esac

    if [[ -n "$excerpt" ]]; then
        echo "$excerpt" | head -20
    else
        # Fallback to last 30 lines
        tail -30 "$log_file" 2>/dev/null || true
    fi
}

# Print the post-run failure report: one block per failed task, with a
# tool-aware excerpt, a copy-pasteable reproduce line, and the full log path.
# Expects the caller to have the task registry arrays in scope.
render_failure_report() {
    local log_dir="$1"
    local project_root="$2"
    local i task_id safe status tool workdir command rel

    for i in "${!TASK_IDS[@]}"; do
        task_id="${TASK_IDS[$i]}"
        safe="${task_id//:/-}"
        status=$(get_task_status "$task_id")
        [[ "$status" == "failed" ]] || continue

        workdir="${TASK_WORKDIRS[$i]}"
        command="${TASK_COMMANDS[$i]}"
        rel="${workdir#"$project_root"/}"
        [[ "$rel" == "$workdir" ]] && rel="."

        echo ""
        log_error "  ✖ ${task_id} failed (rc $(cat "$log_dir/${safe}.rc" 2>/dev/null || echo '?'))"
        echo ""

        tool=$(extract_tool "$command")
        extract_error_excerpt "$log_dir/${safe}.log" "$tool" | sed 's/^/    /'

        echo ""
        log "    reproduce:  cd ${rel} && ${command}" "$CYAN"
        log "    full log:   ${log_dir}/${safe}.log" "$GRAY"
    done
    echo ""
}

# Are all tasks terminal (have .rc or .skipped)?
_render_all_complete() {
    local task_id safe
    for task_id in ${RENDER_TASK_IDS:-}; do
        safe="${task_id//:/-}"
        if [[ ! -f "$RENDER_LOG_DIR/${safe}.rc" && ! -f "$RENDER_LOG_DIR/${safe}.skipped" ]]; then
            return 1
        fi
    done
    return 0
}

# Follow events.log until the run finishes, passing each NEW line to $1.
#
# The streaming reporters previously read events.log exactly once and returned
# immediately — since the scheduler runs in the background the file was still
# empty, so non-TTY runs printed no progress whatsoever.
_render_follow() {
    local emit_fn="$1"
    local events_file="$RENDER_LOG_DIR/events.log"
    local seen=0 total line

    while true; do
        if [[ -f "$events_file" ]]; then
            total=$(wc -l < "$events_file" 2>/dev/null || echo 0)
            if [[ "$total" -gt "$seen" ]]; then
                while IFS= read -r line; do
                    [[ -z "$line" ]] && continue
                    IFS='|' read -r _t _i _r <<< "$line"
                    "$emit_fn" "$_t" "$_i" "$_r"
                done < <(tail -n +$(( seen + 1 )) "$events_file")
                seen="$total"
            fi
        fi
        if _render_all_complete && [[ -f "$events_file" ]]; then
            # One final drain, then stop.
            total=$(wc -l < "$events_file" 2>/dev/null || echo 0)
            [[ "$total" -le "$seen" ]] && break
        fi
        sleep 0.2
    done
}

_emit_plain() {
    local event_type="$1" task_id="$2" rest="$3"
    case "$event_type" in
        START)    echo "[${task_id}] Starting..." ;;
        PROGRESS) echo "[${task_id}] $rest" ;;
        DONE)
            local rc elapsed
            rc=$(echo "$rest" | awk '{print $1}')
            elapsed=$(echo "$rest" | awk '{print $2}')
            if [[ "$rc" == "0" ]]; then
                echo "[${task_id}] Done ($elapsed)"
            else
                echo "[${task_id}] Failed ($elapsed)"
            fi
            ;;
    esac
}

# Plain reporter: one line per event
render_plain() {
    if [[ -n "${RENDER_TASK_IDS:-}" ]]; then
        _render_follow _emit_plain
        return 0
    fi

    # No task list (direct/legacy call): fall back to a single pass.
    local events_file="$RENDER_LOG_DIR/events.log"
    if [[ ! -f "$events_file" ]]; then
        return 0
    fi

    while IFS='|' read -r event_type task_id rest; do
        case "$event_type" in
            START)
                echo "[${task_id}] Starting..."
                ;;
            PROGRESS)
                echo "[${task_id}] $rest"
                ;;
            DONE)
                local rc elapsed
                rc=$(echo "$rest" | awk '{print $1}')
                elapsed=$(echo "$rest" | awk '{print $2}')
                if [[ "$rc" == "0" ]]; then
                    echo "[${task_id}] Done ($elapsed)"
                else
                    echo "[${task_id}] Failed ($elapsed)"
                fi
                ;;
        esac
    done < "$events_file"
}

_emit_json() {
    local event_type="$1" task_id="$2" rest="$3"
    case "$event_type" in
        START)    echo "{\"type\":\"start\",\"task\":\"$task_id\"}" ;;
        PROGRESS) echo "{\"type\":\"progress\",\"task\":\"$task_id\",\"line\":\"$rest\"}" ;;
        DONE)
            local rc elapsed
            rc=$(echo "$rest" | awk '{print $1}')
            elapsed=$(echo "$rest" | awk '{print $2}')
            echo "{\"type\":\"done\",\"task\":\"$task_id\",\"rc\":${rc:-0},\"elapsed\":\"$elapsed\"}"
            ;;
    esac
}

# JSON reporter: raw events as JSON objects
render_json() {
    if [[ -n "${RENDER_TASK_IDS:-}" ]]; then
        _render_follow _emit_json
        return 0
    fi

    local events_file="$RENDER_LOG_DIR/events.log"

    if [[ ! -f "$events_file" ]]; then
        return 0
    fi

    while IFS='|' read -r event_type task_id rest; do
        case "$event_type" in
            START)
                echo "{\"type\":\"start\",\"task\":\"$task_id\"}"
                ;;
            PROGRESS)
                echo "{\"type\":\"progress\",\"task\":\"$task_id\",\"line\":\"$rest\"}"
                ;;
            DONE)
                local rc elapsed
                rc=$(echo "$rest" | awk '{print $1}')
                elapsed=$(echo "$rest" | awk '{print $2}')
                echo "{\"type\":\"done\",\"task\":\"$task_id\",\"rc\":$rc,\"elapsed\":\"$elapsed\"}"
                ;;
        esac
    done < "$events_file"
}

# GitHub reporter: ::group:: / ::error:: syntax
render_github() {
    local events_file="$RENDER_LOG_DIR/events.log"

    if [[ ! -f "$events_file" ]]; then
        return 0
    fi

    # Track which tasks have been grouped.
    # A space-delimited string, not an associative array: `declare -A` is
    # bash 4+ and this must run on macOS's bash 3.2.
    local grouped_tasks=" "

    while IFS='|' read -r event_type task_id rest; do
        if [[ "$grouped_tasks" != *" ${task_id} "* ]]; then
            echo "::group::Task: $task_id"
            grouped_tasks="${grouped_tasks}${task_id} "
        fi

        case "$event_type" in
            START)
                echo "Starting..."
                ;;
            PROGRESS)
                echo "$rest"
                ;;
            DONE)
                local rc elapsed
                rc=$(echo "$rest" | awk '{print $1}')
                elapsed=$(echo "$rest" | awk '{print $2}')

                if [[ "$rc" != "0" ]]; then
                    echo "::error::Task $task_id failed ($elapsed)"
                fi
                echo "::endgroup::"
                ;;
        esac
    done < "$events_file"
}

# Live reporter: fixed-frame TTY display
# Uses filesystem markers as source of truth (not event stream)
render_live() {
    local log_dir="$RENDER_LOG_DIR"
    local task_ids="${RENDER_TASK_IDS:-}"
    local run_start="${RUN_START:-}"

    if [[ -z "$task_ids" ]] || [[ -z "$run_start" ]]; then
        # Fallback if task list or start time not provided
        render_plain
        return 0
    fi

    # utils.sh stores colours as literal backslash sequences ('\033[0;32m')
    # which only `echo -e` expands. The frame is emitted with printf '%s', so
    # resolve them to real ESC bytes once here. Using printf '%b' on the whole
    # frame instead would also expand backslashes appearing in task output.
    local C_GREEN C_RED C_GRAY C_RESET
    C_GREEN=$(printf '%b' "$GREEN")
    C_RED=$(printf '%b' "$RED")
    C_GRAY=$(printf '%b' "$GRAY")
    C_RESET=$(printf '%b' "$RESET")

    # Spinner frames as an array. A substring slice of a packed UTF-8 string
    # ('${s:off:3}') is wrong: bash slices by character, not byte, so it
    # yielded three glyphs at once and then nothing once the offset ran past
    # the end. Array indexing is locale-independent.
    local SPIN_FRAMES=(⠹ ⠸ ⠼ ⠴ ⠦ ⠧)
    local RENDER_FRAME_LINES=0
    local RENDER_FIRST_PAINT=1
    local RENDER_TICK=0

    # Truncate a string to terminal width, ANSI-unaware
    _render_fit() {
        local s="$1"
        local w="${COLUMNS:-100}"
        (( w < 40 )) && w=40
        if (( ${#s} > w )); then
            printf '%s' "${s:0:$((w-1))}…"
        else
            printf '%s' "$s"
        fi
    }

    # Main paint function
    _render_paint() {
        local now done_n=0 running_n=0 failed_n=0 total_n=0
        local out="" line spin

        # Get current epoch time
        if command -v gdate >/dev/null 2>&1; then
            now=$(gdate +%s)
        else
            now=$(date +%s)
        fi

        # Count task statuses
        local task_id
        for task_id in $task_ids; do
            local task_id_safe="${task_id//:/-}"
            local rc_file="$log_dir/${task_id_safe}.rc"
            local log_file="$log_dir/${task_id_safe}.log"
            local skipped_file="$log_dir/${task_id_safe}.skipped"

            total_n=$((total_n + 1))

            if [[ -f "$skipped_file" ]]; then
                # Skipped
                :
            elif [[ -f "$rc_file" ]]; then
                # Done or failed
                local rc
                rc=$(cat "$rc_file" 2>/dev/null || echo "unknown")
                if [[ "$rc" == "0" ]]; then
                    done_n=$((done_n + 1))
                else
                    failed_n=$((failed_n + 1))
                fi
            elif [[ -f "$log_file" ]]; then
                # Running
                running_n=$((running_n + 1))
            fi
        done

        # Get spinner frame
        spin="${SPIN_FRAMES[$(( RENDER_TICK % 6 ))]}"

        # Build header line
        local elapsed_secs=$(( now - run_start ))
        local elapsed_fmt
        if [[ $elapsed_secs -lt 60 ]]; then
            elapsed_fmt="${elapsed_secs}s"
        else
            local mins=$(( elapsed_secs / 60 ))
            local secs=$(( elapsed_secs % 60 ))
            elapsed_fmt="${mins}m ${secs}s"
        fi

        # Count frame lines as we build them. Deriving this with `wc -l` broke
        # on macOS: BSD wc pads its output ("      15"), and the padded value
        # then made `printf '\033[%dA'` fail with "invalid number", which under
        # `set -e` killed the whole run right after the first paint.
        local frame_lines=0

        out+="$(_render_fit "neotool validate · ${done_n}/${total_n} done · ${running_n} running · ${failed_n} failed · ${elapsed_fmt}")"$'\n'
        frame_lines=$(( frame_lines + 1 ))
        out+=$'\n'
        frame_lines=$(( frame_lines + 1 ))

        # Build per-task lines
        for task_id in $task_ids; do
            local task_id_safe="${task_id//:/-}"
            local rc_file="$log_dir/${task_id_safe}.rc"
            local log_file="$log_dir/${task_id_safe}.log"
            local start_file="$log_dir/${task_id_safe}.start"
            local end_file="$log_dir/${task_id_safe}.end"
            local skipped_file="$log_dir/${task_id_safe}.skipped"

            # glyph stays a bare character; colour is applied only after the
            # line has been width-fitted. Escape codes inside the string would
            # be counted by ${#s} and could be sliced mid-sequence.
            local glyph glyph_color="" elapsed_str progress_str=""

            if [[ -f "$skipped_file" ]]; then
                glyph="⠿"
                glyph_color="$C_GRAY"
                elapsed_str="skipped"
            elif [[ -f "$rc_file" ]]; then
                local rc
                rc=$(cat "$rc_file" 2>/dev/null || echo "unknown")
                if [[ "$rc" == "0" ]]; then
                    glyph="✔"
                    glyph_color="$C_GREEN"
                else
                    glyph="✖"
                    glyph_color="$C_RED"
                fi
                local start_time end_time
                start_time=$(cat "$start_file" 2>/dev/null || echo "0")
                end_time=$(cat "$end_file" 2>/dev/null || echo "0")
                local secs=$(( end_time - start_time ))
                if [[ $secs -lt 60 ]]; then
                    elapsed_str="${secs}s"
                else
                    local mins=$(( secs / 60 ))
                    local sec=$(( secs % 60 ))
                    elapsed_str="${mins}m ${sec}s"
                fi
            elif [[ -f "$log_file" ]]; then
                glyph="$spin"
                local start_time
                start_time=$(cat "$start_file" 2>/dev/null || echo "0")
                local secs=$(( now - start_time ))
                if [[ $secs -lt 60 ]]; then
                    elapsed_str="${secs}s"
                else
                    local mins=$(( secs / 60 ))
                    local sec=$(( secs % 60 ))
                    elapsed_str="${mins}m ${sec}s"
                fi
                # Get progress line from log
                local last_line
                # The `|| true` is load-bearing. A task's .log exists from the
                # moment it launches but is empty for the first fraction of a
                # second; `grep -v` matching nothing exits 1, `pipefail`
                # propagates that, and `set -e` then killed the renderer on its
                # second repaint — one frame, no error, exit 1.
                last_line=$(tail -c 4096 "$log_file" 2>/dev/null | tr -d '\r' | grep -v '^[[:space:]]*$' | tail -1 || true)
                if [[ -n "$last_line" ]]; then
                    # Strip ANSI escapes
                    last_line=$(printf '%s' "$last_line" | sed 's/\x1b\[[0-9;]*[a-zA-Z]//g')
                    progress_str="▸ $last_line"
                fi
            else
                glyph="⠿"
                glyph_color="$C_GRAY"
                elapsed_str="queued"
            fi

            # Format task line (plain), fit to width, THEN colour the glyph.
            line="  $glyph $(printf '%-15s' "$task_id") $(printf '%-10s' "$elapsed_str")"
            if [[ -n "$progress_str" ]]; then
                line="$line  $progress_str"
            fi
            line="$(_render_fit "$line")"
            if [[ -n "$glyph_color" ]]; then
                # Re-emit the leading "  <glyph>" wrapped in colour.
                line="  ${glyph_color}${glyph}${C_RESET}${line#"  ${glyph}"}"
            fi
            out+="$line"$'\n'
            frame_lines=$(( frame_lines + 1 ))
        done

        # First paint: set up frame
        if [[ $RENDER_FIRST_PAINT -eq 1 ]]; then
            RENDER_FRAME_LINES=$frame_lines
            RENDER_FIRST_PAINT=0
            printf '\033[?25l'  # hide cursor
        else
            # Move cursor up and repaint
            printf '\033[%dA' "$RENDER_FRAME_LINES"
        fi

        # Print frame (each line with clear-to-EOL)
        printf '%s' "$out" | while IFS= read -r l; do
            printf '%s\033[K\n' "$l"
        done

        RENDER_TICK=$(( RENDER_TICK + 1 ))
    }

    # Main loop
    while true; do
        _render_paint

        # Check if all tasks are done
        local all_done=true
        for task_id in $task_ids; do
            local task_id_safe="${task_id//:/-}"
            local rc_file="$log_dir/${task_id_safe}.rc"
            local skipped_file="$log_dir/${task_id_safe}.skipped"

            if [[ ! -f "$rc_file" ]] && [[ ! -f "$skipped_file" ]]; then
                all_done=false
                break
            fi
        done

        if [[ "$all_done" == "true" ]]; then
            break
        fi

        sleep 0.1
    done

    # One final paint after completion
    _render_paint

    # Restore cursor
    printf '\033[?25h'
}

