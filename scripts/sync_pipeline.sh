#!/usr/bin/env bash
# ==============================================================================
# Netra Security Hub - Automated Git Synchronization & Verification Pipeline
# ==============================================================================
set -euo pipefail

DEFAULT_BRANCH="main"
REMOTE_NAME="origin"

echo "=== Netra Security Hub: Synchronization Pipeline ==="

# 1. Check if git is initialized
if [ ! -d ".git" ]; then
    echo "[SYNC_STARTED] Initializing Git repository..."
    git init -b "${DEFAULT_BRANCH}"
fi

# 2. Configure safe defaults
git config user.name "Netra Security Agent" || true
git config user.email "agent@netra-security-hub.local" || true

# 3. Verify clean build before sync
echo "[VALIDATION] Running local compilation check..."
gradle :app:testDebugUnitTest --no-daemon

# 4. Stage tracked files
echo "[SYNC] Staging changes..."
git add .

# 5. Commit if changes exist
if git diff-index --quiet HEAD -- 2>/dev/null; then
    echo "[SYNC_STATUS] Working tree clean, no uncommitted changes."
else
    COMMIT_MSG="fix(security-hub): sync authoritative telemetry architecture and continuous delivery pipeline $(date -u +'%Y-%m-%d %H:%M:%S UTC')"
    git commit -m "${COMMIT_MSG}"
    echo "[SYNC_SUCCESS] Local commit created: ${COMMIT_MSG}"
fi

# 6. Check remote configuration
if git remote get-url "${REMOTE_NAME}" >/dev/null 2>&1; then
    REMOTE_URL=$(git remote get-url "${REMOTE_NAME}")
    echo "[REMOTE] Authoritative remote configured: ${REMOTE_URL}"
    echo "[PUSH] Pushing to ${REMOTE_NAME}/${DEFAULT_BRANCH}..."
    git push -u "${REMOTE_NAME}" "${DEFAULT_BRANCH}" || {
        echo "[SYNC_FAILED] Push to remote failed. Check repository permissions and credentials."
        exit 1
    }
    echo "[SYNC_SUCCESS] Successfully pushed to authoritative GitHub branch."
else
    echo "[NOTICE] Remote '${REMOTE_NAME}' not yet set. To link to GitHub:"
    echo "  git remote add origin <AUTHORITATIVE_GITHUB_REPO_URL>"
    echo "  git push -u origin ${DEFAULT_BRANCH}"
fi

echo "=== Pipeline Verification Complete ==="
