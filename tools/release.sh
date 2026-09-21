#!/usr/bin/env bash
#
# Releases the plugin: builds, verifies, signs, tags and publishes the signed archive
# to GitHub. Uploading that archive to JetBrains Marketplace stays manual.
#
# Usage: tools/release.sh <major.minor.patch>

set -euo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")/.."

readonly PROJECT_NAME=project-switcher
readonly BRANCH=main
readonly SIGNING_DIR="$HOME/develop/plugin-signing"

step() { printf '\n==> %s\n' "$1"; }

fail() {
    printf 'FAILED: %s\n' "$1" >&2
    exit 1
}

# --- preflight ---------------------------------------------------------------

step "Preflight"

[ $# -eq 1 ] || fail "Usage: tools/release.sh <major.minor.patch>"

readonly VERSION=$1
[[ $VERSION =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]] || fail "Version '$VERSION' is not <major>.<minor>.<patch>."

readonly TAG="v$VERSION"
readonly SIGNED_ZIP="build/distributions/$PROJECT_NAME-$VERSION-signed.zip"

command -v gh >/dev/null || fail "The GitHub CLI (gh) is not installed."
gh auth status >/dev/null 2>&1 || fail "gh is not authenticated. Run: gh auth login"

current_branch=$(git rev-parse --abbrev-ref HEAD)
[ "$current_branch" = "$BRANCH" ] || fail "On branch '$current_branch', expected '$BRANCH'."

[ -z "$(git status --porcelain)" ] || fail "Working tree is dirty. Commit or stash first."

git fetch --quiet origin "$BRANCH"
[ "$(git rev-parse HEAD)" = "$(git rev-parse "origin/$BRANCH")" ] ||
    fail "HEAD and origin/$BRANCH have diverged. Pull or push first."

if git rev-parse -q --verify "refs/tags/$TAG" >/dev/null; then
    fail "Tag $TAG already exists locally."
fi
if git ls-remote --exit-code --tags origin "$TAG" >/dev/null 2>&1; then
    fail "Tag $TAG already exists on origin."
fi

# patchPluginXml publishes the topmost section as the plugin's change notes, so releasing
# a version whose section is not on top would ship somebody else's notes.
top_section=$(grep -m1 '^## ' CHANGELOG.md | sed 's/^## //')
[ "$top_section" = "$VERSION" ] ||
    fail "CHANGELOG.md starts with section '$top_section', not '$VERSION'."

for file in chain.crt private-encrypted.pem; do
    [ -f "$SIGNING_DIR/$file" ] || fail "Missing $SIGNING_DIR/$file. See $SIGNING_DIR/README.md."
done

if [ -z "${PRIVATE_KEY_PASSWORD:-}" ]; then
    read -r -s -p 'Passphrase for private-encrypted.pem: ' PRIVATE_KEY_PASSWORD || true
    printf '\n'
    [ -n "$PRIVATE_KEY_PASSWORD" ] || fail "An empty passphrase cannot unlock the signing key."
fi

current_version=$(sed -n 's/^pluginVersion *= *//p' gradle.properties)
[ -n "$current_version" ] || fail "Could not read pluginVersion from gradle.properties."

printf '  version     %s -> %s\n' "$current_version" "$VERSION"
printf '  commit      %s\n' "$(git log --oneline -1)"
printf '  repository  %s\n' "$(gh repo view --json nameWithOwner -q .nameWithOwner)"

answer=
read -r -p $'\nTag, publish a GitHub release and push both? [y/N] ' answer || true
[ "$answer" = "y" ] || fail "Aborted."

# --- build -------------------------------------------------------------------
# The bump is committed only in the publish section, so a failure here leaves nothing
# but a modified gradle.properties.

if [ "$current_version" != "$VERSION" ]; then
    step "Setting pluginVersion to $VERSION"
    sed -i '' "s/^pluginVersion *=.*/pluginVersion = $VERSION/" gradle.properties
fi

step "Building"
./gradlew clean build buildPlugin verifyPluginStructure --console=plain

step "Running the IntelliJ Plugin Verifier"
./gradlew verifyPlugin --console=plain

step "Signing"
CERTIFICATE_CHAIN="$(cat "$SIGNING_DIR/chain.crt")" \
    PRIVATE_KEY="$(cat "$SIGNING_DIR/private-encrypted.pem")" \
    PRIVATE_KEY_PASSWORD="$PRIVATE_KEY_PASSWORD" \
    ./gradlew signPlugin --console=plain

step "Verifying the signature"
zip_signer=$({ find "$HOME/.gradle/caches/modules-2" -name 'marketplace-zip-signer-*-cli.jar' 2>/dev/null || true; } | sort -V | tail -1)
[ -n "$zip_signer" ] || fail "marketplace-zip-signer CLI did not appear in the Gradle cache after signPlugin."
[ -f "$SIGNED_ZIP" ] || fail "$SIGNED_ZIP was not produced."
java -cp "$zip_signer" org.jetbrains.zip.signer.ZipSigningTool verify \
    -in "$SIGNED_ZIP" -cert "$SIGNING_DIR/chain.crt" ||
    fail "The signature of $SIGNED_ZIP is not valid."
printf '  %s is signed and valid\n' "$SIGNED_ZIP"

# --- publish -----------------------------------------------------------------

if ! git diff --quiet gradle.properties; then
    step "Committing the version bump"
    git commit --quiet -m "Release $VERSION" -- gradle.properties
fi

step "Tagging and pushing $TAG"
git tag -a "$TAG" -m "Project Switcher $VERSION"
git push --quiet origin "$BRANCH" "$TAG"

step "Creating the GitHub release"

notes=$(mktemp)
trap 'rm -f "$notes"' EXIT

awk -v heading="## $VERSION" '
    $0 == heading { collecting = 1; next }
    collecting && /^## / { exit }
    collecting { print }
' CHANGELOG.md >"$notes"

cat >>"$notes" <<EOF

---

\`$(basename "$SIGNED_ZIP")\` is signed with the author certificate
\`$(openssl x509 -in "$SIGNING_DIR/chain.crt" -noout -subject | sed 's/^subject=//')\`
(SHA-256 fingerprint \`$(openssl x509 -in "$SIGNING_DIR/chain.crt" -noout -fingerprint -sha256 | sed 's/.*=//')\`).
Copies installed from JetBrains Marketplace carry an additional JetBrains signature.
EOF

gh release create "$TAG" "$SIGNED_ZIP" --title "$VERSION" --notes-file "$notes"

step "Released $VERSION"
printf '  Still manual: upload %s to https://plugins.jetbrains.com/plugin/add\n' "$SIGNED_ZIP"
