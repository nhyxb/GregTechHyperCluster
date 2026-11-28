#!/usr/bin/env bash
set -euo pipefail

cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.."
evidence_dir=${1:-ci-logs/client-smoke}
mkdir -p "$evidence_dir" run

game_dir=$(mktemp -d "$PWD/run/client-smoke.XXXXXX")
client_log="$evidence_dir/client.log"
game_log="$evidence_dir/latest.log"
screenshot="$evidence_dir/main-menu.png"
marker='Client smoke: title screen rendered'
xvfb_pid=''
gradle_pid=''

client_pids() {
    local pid
    # Gradle can launch the game JVM in a separate process group and via an argument file.
    for pid in $(pgrep -x java); do
        if [[ "$(readlink "/proc/$pid/cwd" 2>/dev/null)" == "$game_dir" ]]; then
            printf '%s\n' "$pid"
        fi
    done
}

signal_clients() {
    local signal=$1
    local pid
    kill "-$signal" -- "-$gradle_pid" 2>/dev/null || true
    for pid in $(client_pids); do
        kill "-$signal" "$pid" 2>/dev/null || true
    done
}

cleanup() {
    local result=$?
    if [[ -n "$gradle_pid" ]]; then
        signal_clients TERM
        for ((attempt = 0; attempt < 30; attempt++)); do
            if ! kill -0 "$gradle_pid" 2>/dev/null && [[ -z "$(client_pids)" ]]; then
                break
            fi
            sleep 0.1
        done
        signal_clients KILL
        wait "$gradle_pid" 2>/dev/null || true
    fi
    if [[ -n "$xvfb_pid" ]]; then
        kill -TERM "$xvfb_pid" 2>/dev/null || true
        wait "$xvfb_pid" 2>/dev/null || true
    fi
    if [[ -n "$(client_pids)" ]]; then
        echo '::error::The smoke-test client is still running.'
        result=1
    else
        echo 'Client and Xvfb stopped.'
    fi
    exit "$result"
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

# Fresh clients would otherwise stop on Minecraft's first-run accessibility screen.
printf '%s\n' 'onboardAccessibility:false' 'narrator:0' 'narratorOnboarded:true' > "$game_dir/options.txt"

display_file="$game_dir/display-number"
Xvfb -displayfd 3 -screen 0 1280x720x24 -nolisten tcp 3>"$display_file" > "$evidence_dir/xvfb.log" 2>&1 &
xvfb_pid=$!
for ((attempt = 0; attempt < 100; attempt++)); do
    [[ ! -s "$display_file" ]] || break
    kill -0 "$xvfb_pid" 2>/dev/null || break
    sleep 0.1
done
if [[ ! -s "$display_file" ]]; then
    echo '::error::Xvfb did not open a display.'
    tail -30 "$evidence_dir/xvfb.log"
    exit 1
fi
display=:$(<"$display_file")

: > "$client_log"
setsid env -u WAYLAND_DISPLAY DISPLAY="$display" LIBGL_ALWAYS_SOFTWARE=1 ALSOFT_DRIVERS=null \
    ./gradlew --no-daemon --console=plain "-PhyperclusterClientSmokeDir=$game_dir" runClient \
    > "$client_log" 2>&1 &
gradle_pid=$!

deadline=$((SECONDS + ${CLIENT_SMOKE_TIMEOUT_SECONDS:-720}))
reached=no
while ((SECONDS < deadline)); do
    if grep -Fq "$marker" "$client_log"; then
        reached=yes
        break
    fi
    kill -0 "$gradle_pid" 2>/dev/null || break
    sleep 2
done
echo "MAIN_MENU_REACHED=$reached"
if [[ "$reached" == yes ]]; then
    # Let the first rendered frame reach the X server before capturing it.
    sleep 2
fi
captured=no
if timeout 15s import -display "$display" -window root "$screenshot" 2>>"$evidence_dir/xvfb.log"; then
    captured=yes
else
    echo '::warning::The Xvfb screenshot could not be captured.'
fi
if [[ -s "$game_dir/logs/latest.log" ]]; then
    cp -- "$game_dir/logs/latest.log" "$game_log"
fi

if [[ "$reached" != yes ]]; then
    echo '::error::The client did not render the title screen.'
    tail -50 "$client_log"
    exit 1
fi
if [[ "$captured" != yes || ! -s "$screenshot" || ! -s "$game_dir/logs/latest.log" ]] ||
        ! kill -0 "$gradle_pid" 2>/dev/null; then
    echo '::error::The rendered title screen could not be captured with a live client and game log.'
    tail -50 "$client_log"
    exit 1
fi

# Filter only the known native narrator notice. Any other ERROR fails the smoke run.
awk '/\/ERROR\]/ && !/ \[com\.mojang\.text2speech\.Narrator\/\]: Error while loading the narrator$/' \
    "$game_log" > "$evidence_dir/errors.log"
if [[ -s "$evidence_dir/errors.log" ]]; then
    echo '::error::Unexpected errors in the client log.'
    head -30 "$evidence_dir/errors.log"
    exit 1
fi

identify -format 'Title screen captured at %wx%h.\n' "$screenshot"
