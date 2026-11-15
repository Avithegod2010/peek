#!/usr/bin/env bash
# Generate a readable changelog from commits since the previous tag.
# Usage: scripts/generate-release-notes.sh v1.6.0
set -euo pipefail

TAG="${1:?usage: generate-release-notes.sh <tag>}"
VERSION="${TAG#v}"

if git rev-parse -q --verify "refs/tags/$TAG" >/dev/null; then
    PREVIOUS="$(git describe --tags --abbrev=0 "$TAG^" 2>/dev/null || true)"
    RANGE="${PREVIOUS:+$PREVIOUS..}$TAG"
else
    RANGE="HEAD"
fi

features=()
fixes=()
security=()
docs=()
other=()

while IFS= read -r subject; do
    [ -n "$subject" ] || continue
    case "$subject" in
        Merge\ pull\ request*|Merge\ branch*) continue ;;
    esac
    lower="$(printf '%s' "$subject" | tr '[:upper:]' '[:lower:]')"
    if [[ "$lower" =~ (security|harden|encrypt|privacy|vault|sensitive|allowlist|biometric) ]]; then
        security+=("$subject")
    elif [[ "$lower" =~ (fix|correct|repair|bug|regression) ]]; then
        fixes+=("$subject")
    elif [[ "$lower" =~ ^(add|implement|introduce|support|new|create|allow|enable) ]]; then
        features+=("$subject")
    elif [[ "$lower" =~ (document|docs|readme|changelog) ]]; then
        docs+=("$subject")
    else
        other+=("$subject")
    fi
done < <(git log --no-merges --pretty=format:'%s' "$RANGE")

cat <<EOF
# Peek ${VERSION}

Generated from \`${RANGE}\`. Review this draft before publishing.

EOF

section() {
    local title="$1"
    shift
    local -a items=("$@")
    if [ ${#items[@]} -eq 0 ]; then return; fi
    printf '## %s\n\n' "$title"
    for item in "${items[@]}"; do printf -- '- %s\n' "$item"; done
    printf '\n'
}

section "Features" "${features[@]}"
section "Fixes" "${fixes[@]}"
section "Security" "${security[@]}"
section "Documentation" "${docs[@]}"
section "Other" "${other[@]}"

cat <<'EOF'
## Verify the download

```powershell
Get-FileHash .\peek-<version>-release.apk -Algorithm SHA256
Get-Content .\SHA256SUMS.txt
```

The two hashes must match.

## Notes

- Signed, non-debuggable APK with published SHA-256 checksums.
- No debug APKs are attached to releases.
- Release builds from v1.5.0 onward update each other in place.
EOF
