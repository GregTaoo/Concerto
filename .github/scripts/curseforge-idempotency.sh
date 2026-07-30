#!/usr/bin/env bash
set -euo pipefail

artifact="$1"
project_id="$2"

file_name="$(basename "$artifact")"
index=0
page_size=50

while :; do
    response="$(curl --fail-with-body --silent --show-error --retry 3 --retry-delay 2 \
        -H "Accept: application/json" \
        "https://www.curseforge.com/api/v1/mods/$project_id/files?index=$index&pageSize=$page_size")"

    existing_file_id="$(jq -r --arg file_name "$file_name" '
        .data[]
        | select(.fileName == $file_name)
        | .id
    ' <<< "$response" | head -n 1)"

    if [[ -n "$existing_file_id" ]]; then
        echo "should_upload=false" >> "$GITHUB_OUTPUT"
        echo "existing_file_id=$existing_file_id" >> "$GITHUB_OUTPUT"
        echo "reason=an identical filename is already published" >> "$GITHUB_OUTPUT"
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
echo "file_name=$file_name" >> "$GITHUB_OUTPUT"
