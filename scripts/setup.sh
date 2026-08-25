#!/usr/bin/env bash
#
# One-time setup to publish Travollio updates via GitHub Releases.
#   1. Init a git repo in the project (if not already).
#   2. Create the GitHub repo via the API (if it doesn't exist).
#   3. Write github.local.properties with the owner/repo the app will read.
#   4. Commit + push.
#
# Needs: GITHUB_TOKEN (or ~/.config/travollio/token) with `repo` scope,
#        OWNER + REPO env vars.
#
#   OWNER=odago REPO=travollio GITHUB_TOKEN=ghp_... scripts/setup.sh
set -euo pipefail

DIR="${DIR:-$HOME/travollio}"
TOKEN="${GITHUB_TOKEN:-}"
[ -z "$TOKEN" ] && TOKEN="$(cat "${TOKEN_FILE:-$HOME/.config/travollio/token}" 2>/dev/null || true)"
[ -z "$TOKEN" ] && { echo "ERROR: no GITHUB_TOKEN" >&2; exit 1; }
OWNER="${OWNER:?Need OWNER (your GitHub username)}"
REPO="${REPO:?Need REPO (target repo name)}"
VIS="${VISIBILITY:-public}"

cd "$DIR"

# 1) init + first commit
if [ ! -d .git ]; then
  git init -q
  git add -A
  git commit -q -m "Travollio: initial (auto-setup)"
  echo "git initialized"
fi

# 2) create remote repo if missing
if ! curl -fsS -o /dev/null "https://api.github.com/repos/${OWNER}/${REPO}" >/dev/null 2>&1; then
  echo "creating ${VIS} repo ${OWNER}/${REPO} ..."
  curl -fsS -X POST "https://api.github.com/user/repos" \
    -H "Authorization: bearer $TOKEN" \
    -H "Accept: application/vnd.github+json" \
    -d "$(python3 -c 'import json,sys;print(json.dumps({"name":sys.argv[1],"private":sys.argv[2]=="private","auto_init":False}))' "$REPO" "$VIS")" >/dev/null
  echo "  created"
else
  echo "repo ${OWNER}/${REPO} already exists"
fi

git remote remove origin 2>/dev/null || true
git remote add origin "https://${OWNER}:${TOKEN}@github.com/${OWNER}/${REPO}.git"

# 3) write the feed identity the app's Settings tab will use
cat > github.local.properties <<EOF
github.owner=$OWNER
github.repo=$REPO
github.apiBase=https://api.github.com
EOF

git add -A
git commit -qm "configure update feed (${OWNER}/${REPO})" || true
git branch -M main
git push -u origin main 2>&1 | sed 's/^/  /'

echo
echo "Done. Repo: https://github.com/${OWNER}/${REPO}"
echo "Now build + publish a release with:  scripts/release.sh"
