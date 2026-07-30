#!/usr/bin/env bash
set -euo pipefail

artifact="$1"
project_id="$2"

if [[ -z "${CURSEFORGE_API_TOKEN:-}" ]]; then
    echo "should_upload=false" >> "$GITHUB_OUTPUT"
    echo "reason=CURSEFORGE_API_TOKEN is not configured" >> "$GITHUB_OUTPUT"
    exit 0
fi

sha1="$(sha1sum "$artifact" | awk '{print tolower($1)}')"
index=0
page_size=50

while :; do
    response="$(curl --fail-with-body --silent --show-error --retry 3 --retry-delay 2 \
        -H "x-api-key: $CURSEFORGE_API_TOKEN" \
        "https://api.curseforge.com/v1/mods/$project_id/files?index=$index&pageSize=$page_size")"

    existing_file_id="$(jq -r --arg sha1 "$sha1" '
        .data[]
        | select(any(.hashes[]?; .algo == 1 and (.value | ascii_downcase) == $sha1))
        | .id
    ' <<< "$response" | head -n 1)"

    if [[ -n "$existing_file_id" ]]; then
        echo "should_upload=false" >> "$GITHUB_OUTPUT"
        echo "existing_file_id=$existing_file_id" >> "$GITHUB_OUTPUT"
        echo "reason=an identical SHA-1 is already published" >> "$GITHUB_OUTPUT"
        exit 0
    fi

    count="$(jq '.data | length' <<< "$response")"
    total="$(jq '.pagination.totalCount // 0' <<< "$response")"
    index=$((index + count))
    if (( count == 0 || index >= total )); then
        break
    fi
done

echo "should_upload=true" >> "$GITHUB_OUTPUT"
echo "sha1=$sha1" >> "$GITHUB_OUTPUT"
