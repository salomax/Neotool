#!/usr/bin/env bash

set -euo pipefail
set -m  # Enable job control for process group management

# Task Runner / Scheduler
#
# Manages a worker pool over a task dependency graph.
# Polls for task completion and respects --fail-fast.

# Global state
RUNNER_LOG_DIR="${1:-}"
RUNNER_JOBS_MAX="${2:-4}"
RUNNER_FAIL_FAST="${3:-false}"

# Process group tracking (for cleanup)
RUNNER_PIDS=()

# Colors and helpers (will be sourced from caller)
# RED, GREEN, YELLOW, CYAN, GRAY, BRIGHT, RESET

# Trap EXIT/INT/TERM to kill all child processes in process group
# Abort handler. ONLY wired to INT/TERM — never to EXIT.
#
# It must not run on EXIT: run_task is forked with `&`, so a task exiting
# non-zero would fire the handler in the child, and `kill -9 -$$` (the shared
# process group) would take down the scheduler and every sibling task. That
# turned any single failing check into a silent total abort with no output.
cleanup_processes() {
    trap - INT TERM
    # The live reporter hides the cursor; make sure Ctrl-C never leaves the
    # user's terminal without one.
    printf '\033[?25h' 2>/dev/null || true
    # Terminate tracked children politely first...
    if [[ ${#RUNNER_PIDS[@]} -gt 0 ]]; then
        kill -TERM ${RUNNER_PIDS[@]+"${RUNNER_PIDS[@]}"} 2>/dev/null || true
    fi
    sleep 0.2
    # ...then take out the whole group, which is what actually reaps
    # gradle daemons and playwright browsers.
    kill -9 -$$ 2>/dev/null || true
}

trap cleanup_processes INT TERM

# Format elapsed time
format_elapsed_time() {
    local seconds="$1"
    seconds="${seconds//,/.}"
    local int_seconds=${seconds%.*}
    int_seconds="${int_seconds//[^0-9]/}"
    int_seconds="${int_seconds:-0}"

    if [[ $int_seconds -lt 1 ]]; then
        printf "0.0s"
    elif [[ $int_seconds -lt 60 ]]; then
        printf "%ds" "$int_seconds"
    else
        local mins=$(( int_seconds / 60 ))
        local secs=$(( int_seconds % 60 ))
        printf "%dm %ds" "$mins" "$secs"
    fi
}

# Write an event to the events file
# Format: event_type|task_id|...extra
emit_event() {
    local event_type="$1"
    local task_id="$2"
    shift 2
    echo "${event_type}|${task_id}|$*" >> "$RUNNER_LOG_DIR/events.log"
}

# Get task attribute from registry (arrays indexed by task_id)
# Expects globals: TASK_IDS, TASK_TARGETS, TASK_STEPS, TASK_WORKDIRS, TASK_NEEDS, TASK_COMMANDS
get_task_attr() {
    local attr_type="$1"
    local task_id="$2"
    local i=0

    case "$attr_type" in
        target)
            for i in "${!TASK_IDS[@]}"; do
                if [[ "${TASK_IDS[$i]}" == "$task_id" ]]; then
                    echo "${TASK_TARGETS[$i]}"
                    return 0
                fi
            done
            ;;
        step)
            for i in "${!TASK_IDS[@]}"; do
                if [[ "${TASK_IDS[$i]}" == "$task_id" ]]; then
                    echo "${TASK_STEPS[$i]}"
                    return 0
                fi
            done
            ;;
        workdir)
            for i in "${!TASK_IDS[@]}"; do
                if [[ "${TASK_IDS[$i]}" == "$task_id" ]]; then
                    echo "${TASK_WORKDIRS[$i]}"
                    return 0
                fi
            done
            ;;
        needs)
            for i in "${!TASK_IDS[@]}"; do
                if [[ "${TASK_IDS[$i]}" == "$task_id" ]]; then
                    echo "${TASK_NEEDS[$i]}"
                    return 0
                fi
            done
            ;;
        command)
            for i in "${!TASK_IDS[@]}"; do
                if [[ "${TASK_IDS[$i]}" == "$task_id" ]]; then
                    echo "${TASK_COMMANDS[$i]}"
                    return 0
                fi
            done
            ;;
    esac
    return 1
}

# Get task status from marker files
# Returns: pending, running, done, failed, or skipped
get_task_status() {
    local task_id="$1"
    local task_id_safe="${task_id//:/-}"
    local rc_file="$RUNNER_LOG_DIR/${task_id_safe}.rc"
    local log_file="$RUNNER_LOG_DIR/${task_id_safe}.log"
    local skipped_file="$RUNNER_LOG_DIR/${task_id_safe}.skipped"

    # Check if skipped
    if [[ -f "$skipped_file" ]]; then
        echo "skipped"
        return 0
    fi

    # Check if completed (rc file exists)
    if [[ -f "$rc_file" ]]; then
        local rc
        rc=$(cat "$rc_file" 2>/dev/null || echo "unknown")
        if [[ "$rc" == "0" ]]; then
            echo "done"
        else
            echo "failed"
        fi
        return 0
    fi

    # Check if running (log file exists but no rc)
    if [[ -f "$log_file" ]]; then
        echo "running"
        return 0
    fi

    # Otherwise pending
    echo "pending"
    return 0
}

# Check if all dependencies of a task are done (and record if any failed)
all_deps_done() {
    local task_id="$1"
    local needs
    needs=$(get_task_attr "needs" "$task_id")

    if [[ "$needs" == "-" ]] || [[ -z "$needs" ]]; then
        return 0
    fi

    # Split space-separated needs
    local dep
    for dep in $needs; do
        local dep_status
        dep_status=$(get_task_status "$dep")
        if [[ "$dep_status" != "done" && "$dep_status" != "failed" && "$dep_status" != "skipped" ]]; then
            return 1
        fi
    done
    return 0
}

# Check if any dependency failed
any_dep_failed() {
    local task_id="$1"
    local needs
    needs=$(get_task_attr "needs" "$task_id")

    if [[ "$needs" == "-" ]] || [[ -z "$needs" ]]; then
        return 1
    fi

    local dep
    for dep in $needs; do
        local dep_status
        dep_status=$(get_task_status "$dep")
        if [[ "$dep_status" == "failed" ]]; then
            return 0
        fi
    done
    return 1
}

# Run a single task in the background
# Sets RUNNER_PIDS array and records status
run_task() {
    # This body runs in a forked child (callers use `run_task &`). Drop the
    # inherited signal handlers so a child can never kill the process group.
    trap - INT TERM

    local task_id="$1"

    # Get task info
    local workdir command start_time end_time elapsed
    workdir=$(get_task_attr "workdir" "$task_id")
    command=$(get_task_attr "command" "$task_id")

    # Record start time
    if command -v gdate >/dev/null 2>&1; then
        start_time=$(gdate +%s)
    else
        start_time=$(date +%s)
    fi

    # Write start marker file
    local task_id_safe="${task_id//:/-}"
    echo "$start_time" > "$RUNNER_LOG_DIR/${task_id_safe}.start"

    emit_event "START" "$task_id"

    # Run in background with output to log
    local log_file="$RUNNER_LOG_DIR/${task_id_safe}.log"
    (
        cd "$workdir" && eval "$command"
    ) > "$log_file" 2>&1 &

    local pid=$!
    RUNNER_PIDS+=("$pid")

    # Record the PID so a supervisor can reap exactly THIS run's processes.
    #
    # Job control (`set -m`, top of this file) makes each backgrounded job a
    # process-group leader with PGID == PID, so `kill -- -$pid` takes down the
    # task's whole tree (pnpm -> tsc, gradlew -> its workers) without touching
    # unrelated processes. Matching by name (pgrep -f gradle) would hit daemons
    # belonging to other sessions.
    local pid_file="$RUNNER_LOG_DIR/${task_id_safe}.pid"
    echo "$pid" > "$pid_file"

    # Wait for this single task and capture exit code
    wait "$pid" 2>/dev/null || {
        local rc=$?
        # Drop the PID record first: once the process is gone the number can be
        # recycled by an unrelated process, and a stale file would make cleanup
        # kill an innocent bystander.
        rm -f "$pid_file"
        # Task failed
        if command -v gdate >/dev/null 2>&1; then
            end_time=$(gdate +%s)
        else
            end_time=$(date +%s)
        fi

        # Write end and rc marker files (rc LAST — it's the completion flag)
        echo "$end_time" > "$RUNNER_LOG_DIR/${task_id_safe}.end"
        echo "$rc" > "$RUNNER_LOG_DIR/${task_id_safe}.rc"

        elapsed=$(format_elapsed_time "$(( end_time - start_time ))")
        emit_event "DONE" "$task_id" "$rc" "$elapsed"
        return "$rc"
    }

    # Task succeeded
    rm -f "$pid_file"
    if command -v gdate >/dev/null 2>&1; then
        end_time=$(gdate +%s)
    else
        end_time=$(date +%s)
    fi

    # Write end and rc marker files (rc LAST — it's the completion flag)
    echo "$end_time" > "$RUNNER_LOG_DIR/${task_id_safe}.end"
    echo "0" > "$RUNNER_LOG_DIR/${task_id_safe}.rc"

    elapsed=$(format_elapsed_time "$(( end_time - start_time ))")
    emit_event "DONE" "$task_id" "0" "$elapsed"
    return 0
}

# Main scheduler loop
# Returns overall exit code (0 = all passed, 1 = at least one failed)
# Exclusive resource a task needs, or "" if it can run alongside anything.
#
# A Gradle daemon serves ONE build at a time, so concurrent `./gradlew`
# invocations cannot share one — each forks another daemon ("N busy Daemons
# could not be reused"), and they accumulate. Daemons reuse fine *sequentially*,
# so gradle tasks take a mutex and everything else stays parallel.
#
# This is why `--no-daemon` is the wrong answer: it would make every invocation
# pay JVM startup plus configuration instead of reusing one warm daemon.
task_lock() {
    local cmd
    cmd=$(get_task_attr "command" "$1")
    case "$cmd" in
        *gradlew*|*gradle\ *) echo "gradle" ;;
        *) echo "" ;;
    esac
}

run_scheduler() {
    local running_pids=()
    local running_ids=()
    local task_count=${#TASK_IDS[@]}
    local failed_any=false

    # Initialize events log
    echo "" > "$RUNNER_LOG_DIR/events.log"

    while true; do
        # Reap any finished processes.
        # NOTE: every array expansion below uses the ${arr[@]+"${arr[@]}"}
        # guard. On bash 3.2 (macOS default) `set -u` treats expansion of an
        # empty array as an unbound variable and aborts.
        # Keep running_pids and running_ids index-aligned so we know which
        # locks are currently held.
        local new_running=() new_running_ids=() held_locks=" " _k _lk
        for _k in "${!running_pids[@]}"; do
            if kill -0 "${running_pids[$_k]}" 2>/dev/null; then
                new_running+=("${running_pids[$_k]}")
                new_running_ids+=("${running_ids[$_k]}")
                _lk=$(task_lock "${running_ids[$_k]}")
                [[ -n "$_lk" ]] && held_locks="${held_locks}${_lk} "
            fi
        done
        running_pids=(${new_running[@]+"${new_running[@]}"})
        running_ids=(${new_running_ids[@]+"${new_running_ids[@]}"})

        # Check for --fail-fast condition
        if [[ "$RUNNER_FAIL_FAST" == "true" ]]; then
            # Scan for any failed task
            for i in "${!TASK_IDS[@]}"; do
                local status
                status=$(get_task_status "${TASK_IDS[$i]}")
                if [[ "$status" == "failed" ]]; then
                    # Kill remaining running tasks.
                    for pid in ${running_pids[@]+"${running_pids[@]}"}; do
                        kill -9 "$pid" 2>/dev/null || true
                    done
                    # Suppress the shell's asynchronous "Killed" job notices.
                    wait 2>/dev/null || true
                    running_pids=()

                    # Every task that never completed needs a terminal marker.
                    # Without one the live renderer polls forever waiting for a
                    # .rc/.skipped that is never coming.
                    local j other_id other_status
                    for j in "${!TASK_IDS[@]}"; do
                        other_id="${TASK_IDS[$j]}"
                        other_status=$(get_task_status "$other_id")
                        if [[ "$other_status" == "running" || "$other_status" == "pending" ]]; then
                            touch "$RUNNER_LOG_DIR/${other_id//:/-}.skipped"
                            emit_event "DONE" "$other_id" "0" "cancelled"
                        fi
                    done

                    failed_any=true
                    break 2  # Break both loops
                fi
            done
        fi

        # Find all tasks ready to run (pending + all deps done)
        local ready_tasks=()
        for i in "${!TASK_IDS[@]}"; do
            local task_id="${TASK_IDS[$i]}"
            local status
            status=$(get_task_status "$task_id")

            if [[ "$status" == "pending" ]]; then
                if all_deps_done "$task_id"; then
                    # Check if any dep failed
                    if any_dep_failed "$task_id"; then
                        # Mark as skipped by touching .skipped file
                        touch "$RUNNER_LOG_DIR/${task_id//:/-}.skipped"
                        emit_event "DONE" "$task_id" "0" "skipped"
                    else
                        ready_tasks+=("$task_id")
                    fi
                fi
            fi
        done

        # Launch up to (jobs - running) of the ready tasks
        local can_launch=$(( RUNNER_JOBS_MAX - ${#running_pids[@]} ))
        if [[ $can_launch -lt 0 ]]; then
            can_launch=0
        fi

        local launched=0 want_lock
        for task_id in ${ready_tasks[@]+"${ready_tasks[@]}"}; do
            if [[ $launched -ge $can_launch ]]; then
                break
            fi
            # Skip (don't break) if this task's exclusive resource is busy —
            # a later ready task without that lock can still start now.
            want_lock=$(task_lock "$task_id")
            if [[ -n "$want_lock" && "$held_locks" == *" ${want_lock} "* ]]; then
                continue
            fi
            run_task "$task_id" &
            local bg_pid=$!
            running_pids+=("$bg_pid")
            running_ids+=("$task_id")
            [[ -n "$want_lock" ]] && held_locks="${held_locks}${want_lock} "
            launched=$((launched + 1))
        done

        # Check if we're done
        local pending_count=0
        local failed_count=0
        for i in "${!TASK_IDS[@]}"; do
            local status
            status=$(get_task_status "${TASK_IDS[$i]}")
            if [[ "$status" == "pending" ]]; then
                pending_count=$((pending_count + 1))
            elif [[ "$status" == "failed" ]]; then
                failed_count=$((failed_count + 1))
                failed_any=true
            fi
        done

        if [[ $pending_count -eq 0 && ${#running_pids[@]} -eq 0 ]]; then
            break  # All done
        fi

        # Small sleep to avoid busy-waiting
        sleep 0.1
    done

    if [[ "$failed_any" == "true" ]]; then
        return 1
    fi
    return 0
}

# Export functions if sourced
export -f format_elapsed_time
export -f emit_event
export -f get_task_attr
export -f get_task_status
export -f all_deps_done
export -f any_dep_failed
export -f run_task
export -f run_scheduler
