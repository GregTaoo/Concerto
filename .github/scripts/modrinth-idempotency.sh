#!/usr/bin/env bash
set -euo pipefail

artifact="$1"
project_id="$2"

if [[ -z "${MODRINTH_TOKEN:-}" ]]; then
    echo "should_upload=false" >> "$GITHUB_OUTPUT"
    echo "reason=MODRINTH_TOKEN is not configured" >> "$GITHUB_OUTPUT"
    exit 0
fi

sha1="$(sha1sum "$artifact" | awk '{print tolower($1)}')"
response="$(curl --fail-with-body --silent --show-error --retry 3 --retry-delay 2 \
    -H "User-Agent: Concerto-GitHub-Actions/1.0 (https://github.com/GregTaoo/Concerto)" \
    "https://api.modrinth.com/v2/project/$project_id/version")"

existing_version_id="$(jq -r --arg sha1 "$sha1" '
    .[]
    | select(any(.files[]?; (.hashes.sha1? | ascii_downcase) == $sha1))
    | .id
' <<< "$response" | head -n 1)"

if [[ -n "$existing_version_id" ]]; then
    echo "should_upload=false" >> "$GITHUB_OUTPUT"
    echo "existing_version_id=$existing_version_id" >> "$GITHUB_OUTPUT"
    echo "reason=an identical SHA-1 is already published" >> "$GITHUB_OUTPUT"
    exit 0
fi

echo "should_upload=true" >> "$GITHUB_OUTPUT"
echo "sha1=$sha1" >> "$GITHUB_OUTPUT"
