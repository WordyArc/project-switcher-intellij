#!/usr/bin/env bash

set -euo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")/.."

readonly BRANCH=main

step() { printf '\n==> %s\n' "$1"; }

fail() {
    printf 'FAILED: %s\n' "$1" >&2
    exit 1
}

rewrite() {
    local file=$1 rewritten
    shift
    rewritten=$(mktemp)
    if awk "$@" "$file" >"$rewritten"; then
        cat "$rewritten" >"$file"
        rm -f "$rewritten"
    else
        rm -f "$rewritten"
        fail "Could not rewrite $file."
    fi
}

step "Preflight"

[ $# -eq 1 ] || fail "Usage: tools/tag-release.sh <major.minor.patch>"

readonly VERSION=$1
[[ $VERSION =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]] || fail "Version '$VERSION' is not <major>.<minor>.<patch>."

readonly TAG="v$VERSION"
readonly NEXT_SECTION="${VERSION%%.*}.*.*"

current_branch=$(git rev-parse --abbrev-ref HEAD)
[ "$current_branch" = "$BRANCH" ] || fail "On branch '$current_branch', expected '$BRANCH'."

[ -z "$(git status --porcelain)" ] || fail "Working tree is dirty. Commit or stash first."

git fetch --quiet origin "$BRANCH"
[ "$(git rev-parse HEAD)" = "$(git rev-parse "origin/$BRANCH")" ] ||
    fail "HEAD and origin/$BRANCH differ. Push or pull first, so that only the release goes out."

if git rev-parse -q --verify "refs/tags/$TAG" >/dev/null; then
    fail "Tag $TAG already exists locally."
fi
if git ls-remote --exit-code origin "refs/tags/$TAG" >/dev/null 2>&1; then
    fail "Tag $TAG already exists on origin."
fi
if git ls-remote --exit-code origin "refs/heads/release/$VERSION" >/dev/null 2>&1; then
    fail "Branch release/$VERSION already exists on origin."
fi

current_version=$(sed -n 's/^pluginVersion *= *//p' gradle.properties)
[ -n "$current_version" ] || fail "Could not read pluginVersion from gradle.properties."
[ "$current_version" != "$VERSION" ] || fail "pluginVersion is already $VERSION."
[ "$(printf '%s\n%s\n' "$current_version" "$VERSION" | sort -V | tail -1)" = "$VERSION" ] ||
    fail "$VERSION is lower than the current pluginVersion $current_version."

top_section=$(grep -m1 '^## ' CHANGELOG.md | sed 's/^## //') || fail "CHANGELOG.md has no sections."
if [ "$top_section" != "$VERSION" ] && [[ $top_section =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
    fail "The topmost CHANGELOG.md section is the released $top_section. Add a section for $VERSION above it."
fi
if [ "$top_section" != "$VERSION" ] && grep -qxF "## $VERSION" CHANGELOG.md; then
    fail "CHANGELOG.md already has a $VERSION section below '$top_section'."
fi
awk '/^## / { if (seen++) exit; next } seen && /^[[:space:]]*- / { found = 1 } END { exit !found }' CHANGELOG.md ||
    fail "The topmost section of CHANGELOG.md has no entries, so the release would have no change notes."

step "Setting the version to $VERSION"

trap 'git checkout --quiet -- gradle.properties CHANGELOG.md' EXIT

rewrite gradle.properties -v version="$VERSION" '
    /^pluginVersion *=/ { print "pluginVersion = " version; next }
    { print }
'
rewrite CHANGELOG.md -v version="$VERSION" '
    !done && /^## / { print "## " version; done = 1; next }
    { print }
'

git --no-pager diff

printf '\n  version     %s -> %s\n' "$current_version" "$VERSION"
printf '  changelog   %s -> %s\n' "$top_section" "$VERSION"
printf '  next        an empty "## %s" section is committed on top after the release\n' "$NEXT_SECTION"

answer=
read -r -p $'\nCommit, tag, open the next section and push? [y/N] ' answer || true
[ "$answer" = "y" ] || fail "Aborted. gradle.properties and CHANGELOG.md are restored."

step "Committing and tagging $TAG"
git commit --quiet -m "Release $VERSION" -- gradle.properties CHANGELOG.md
trap 'printf "\nFAILED: stopped before %s went out, nothing was pushed.\n  Undo:  git tag -d %s; git reset --hard origin/%s\n" "$TAG" "$TAG" "$BRANCH" >&2' EXIT
git tag -a "$TAG" -m "Project Switcher $VERSION"

step "Opening the $NEXT_SECTION section of CHANGELOG.md"
rewrite CHANGELOG.md -v section="$NEXT_SECTION" '
    !done && /^## / { print "## " section; print ""; done = 1 }
    { print }
'
git commit --quiet -m "Open the changelog for the release after $VERSION" -- CHANGELOG.md

step "Pushing $BRANCH and $TAG"
git push --atomic --quiet origin "$BRANCH" "$TAG" ||
    fail "Nothing was pushed. Retry with: git push --atomic origin $BRANCH $TAG, or undo with: git tag -d $TAG && git reset --keep origin/$BRANCH"

step "Pushed $TAG"
printf '  The release workflow is running: gh run list --workflow release.yml\n'
