#!/usr/bin/env bash
#
# Travollio release & publish pipeline.
#   Builds a versioned, signed APK, tags it, and publishes a GitHub Release whose
#   asset the in-app updater (Settings tab) reads via the Releases API.
#
# The release tag convention MUST stay `v{versionCode}-{versionName}`
# (e.g. v4-0.3.2) — Updater.kt parses it to compute the update.
#
# Config (all optional, each has a sensible default):
#   OWNER (env) / REPO (env)      GitHub repo, e.g. OWNER=odago REPO=travollio
#                                 defaults to the values baked in build.gradle.kts
#   GITHUB_TOKEN (env or file)    Personal Access Token with `repo` scope.
#                                 Also read from ~/.config/travollio/token
#   GRADLE (env)                  gradle binary. Default: /home/odago/gradle-8.10.2/bin/gradle
#   DIR (env)                     repo root. Default: ~/travollio
#   NOTE (env)                    release notes text for the changelog
#   MANDATORY=1                   mark the release [mandatory] (see Updater.kt)
#
# Usage:
#   scripts/release.sh                  # bump patch version, release
#   scripts/release.sh 5 0.4.0          # explicit versionCode + versionName
#   scripts/release.sh 5 0.4.0 "Release notes here"
set -euo pipefail

DIR="${DIR:-$HOME/travollio}"
GRADLE="${GRADLE:-/home/odago/gradle-8.10.2/bin/gradle}"
BG="$DIR/app/build.gradle.kts"

# Gradle 8.10.2 can't run on the box's default JDK 25 — use a self-contained JDK 21
# (see Temurin in ~/.jdks). Override with JAVA_HOME if you have a preferred JDK.
if [ -z "${JAVA_HOME:-}" ]; then
  JAVA_HOME="$(ls -d "$HOME"/.jdks/jdk-21* 2>/dev/null | sort -V | tail -1)"
fi
if [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/java" ]; then
  export JAVA_HOME
fi

TOKEN="${GITHUB_TOKEN:-}"
[ -z "$TOKEN" ] && TOKEN="$(cat "${TOKEN_FILE:-$HOME/.config/travollio/token}" 2>/dev/null || true)"
[ -z "$TOKEN" ] && die "No GITHUB_TOKEN set. Export it or put it in ~/.config/travollio/token"

# Resolve repo identity from env or build.gradle.kts BuildConfig defaults.
default_owner() { grep -oE 'GH_OWNER", "([^"]+)"' "$BG" | head -1 | sed -E 's/.*"([^"]+)".*/\1/' ; }
default_repo()  { grep -oE 'GH_REPO", "([^"]+)"'  "$BG" | head -1 | sed -E 's/.*"([^"]+)".*/\1/' ; }
OWNER="${OWNER:-$(default_owner || true)}"
REPO="${REPO:-$(default_repo || true)}"

die() { echo "ERROR: $*" >&2; exit 1; }

[ -f "$BG" ] || die "build file not found: $BG"
[ -n "$OWNER" ] && [ -n "$REPO" ] || die "Set OWNER and REPO (env) or github.local.properties"

# ── Version ────────────────────────────────────────────────────────────────
CURR_CODE="$(grep -oE 'versionCode = [0-9]+' "$BG" | head -1 | grep -oE '[0-9]+')"
CURR_NAME="$(grep -oE 'versionName = "[^"]+"' "$BG" | head -1 | sed -E 's/.*"([^"]+)".*/\1/')"
NOTES=""

if [ $# -ge 2 ]; then
  CODE="$1"; NAME="$2"; NOTES="${3:-}"
else
  CODE=$((CURR_CODE + 1))
  # simple patch bump: X.Y.Z -> X.Y.(Z+1)
  IFS='.' read -r MA MI PA <<< "$CURR_NAME"
  NAME="${MA}.${MI}.$(( ${PA:-0} + 1 ))"
fi
[ -z "$NOTES" ] && NOTES="${NOTE:-}"
if [ "${MANDATORY:-0}" = "1" ]; then NOTES="$NOTES
[mandatory]"; fi

TAG="v${CODE}-${NAME}"
APK_NAME="Travollio-v${NAME}.apk"

echo "== Travollio release =="
echo "  repo: ${OWNER}/${REPO}"
echo "  local : v${CURR_CODE}-${CURR_NAME}"
echo "  new   : v${CODE}-${NAME} ($TAG)"
echo
echo "writing versions to $BG ..."
sed -i -E "s/versionCode = [0-9]+/versionCode = $CODE/" "$BG"
sed -i -E "s/versionName = \"[^\"]+\"/versionName = \"$NAME\"/" "$BG"

# ── Build ──────────────────────────────────────────────────────────────────
echo "building release ..."
( cd "$DIR" && "$GRADLE" :app:assembleRelease --console=plain )
APK="$DIR/app/build/outputs/apk/release/app-release.apk"
[ -f "$APK" ] || die "APK not found at $APK"
cp "$APK" "$DIR/$APK_NAME"
echo "  -> $DIR/$APK_NAME ($(du -h "$DIR/$APK_NAME" | cut -f1))"

# ── Tag + push ─────────────────────────────────────────────────────────────
if [ -d "$DIR/.git" ]; then
  git -C "$DIR" add -A
  git -C "$DIR" commit -m "release v${CODE}-${NAME}" 2>&1 | sed 's/^/  /' || true
  if git -C "$DIR" rev-parse "$TAG" >/dev/null 2>&1; then
    git -C "$DIR" tag -d "$TAG" >/dev/null 2>&1 || true
  fi
  git -C "$DIR" tag "$TAG"
  git -C "$DIR" push --tags 2>&1 | sed 's/^/  /' || echo "  (tag push skipped/failed)"
else
  echo "  (no .git -- skipping tag/push; use setup.sh to init + create the repo)"
fi

# ── GitHub release ─────────────────────────────────────────────────────────
echo "publishing release ..."
BODY_FILE="$(mktemp)"
printf '%s' "$NOTES" > "$BODY_FILE"

RELEASE_JSON="$(
  curl -fsS -X POST "https://api.github.com/repos/${OWNER}/${REPO}/releases" \
    -H "Authorization: bearer $TOKEN" \
    -H "Accept: application/vnd.github+json" \
    -d "$(python3 - "$TAG" "$BODY_FILE" <<'PY'
import json,sys
tag,bodyfile=sys.argv[1],sys.argv[2]
print(json.dumps({"tag_name":tag,"name":tag,"body":open(bodyfile).read(),"prerelease":False}))
PY
)"
RELEASE_ID="$(python3 -c 'import json,sys;print(json.load(sys.stdin)["id"])' <<< "$RELEASE_JSON")"
rm -f "$BODY_FILE"

echo "  uploaded ${APK_NAME} ..."
curl -fsS -X POST \
  -H "Authorization: bearer $TOKEN" \
  -H "Content-Type: application/octet-stream" \
  --data-binary "@$DIR/$APK_NAME" \
  "https://uploads.github.com/repos/${OWNER}/${REPO}/releases/${RELEASE_ID}/assets?name=$(python3 -c 'import urllib.parse,sys;print(urllib.parse.quote(sys.argv[1]))' "$APK_NAME")" \
  >/dev/null && echo "  done"

echo
echo "Published https://github.com/${OWNER}/${REPO}/releases/tag/${TAG}"
echo "Open the app → Settings → Scan for updates on your phone."
