#!/usr/bin/env bash
set -euo pipefail

artifact="$1"
project_id="$2"
version_number="$3"

if [[ -z "${MODRINTH_TOKEN:-}" ]]; then
    echo "should_upload=false" >> "$GITHUB_OUTPUT"
    echo "reason=MODRINTH_TOKEN is not configured" >> "$GITHUB_OUTPUT"
    exit 0
fi

response="$(curl --fail-with-body --silent --show-error --retry 3 --retry-delay 2 \
    -H "User-Agent: Concerto-GitHub-Actions/1.0 (https://github.com/GregTaoo/Concerto)" \
    "https://api.modrinth.com/v2/project/$project_id/version")"

existing_version_id="$(jq -r --arg version_number "$version_number" '
    .[]
    | select(.version_number == $version_number)
    | .id
' <<< "$response" | head -n 1)"

if [[ -n "$existing_version_id" ]]; then
    echo "should_upload=false" >> "$GITHUB_OUTPUT"
    echo "existing_version_id=$existing_version_id" >> "$GITHUB_OUTPUT"
    echo "reason=the version number is already published" >> "$GITHUB_OUTPUT"
    exit 0
fi

echo "should_upload=true" >> "$GITHUB_OUTPUT"
echo "version_number=$version_number" >> "$GITHUB_OUTPUT"
