#!/bin/bash

temp_file=$(mktemp)

# If $1 is not provided, use 'warc.paths' as the default value
file=${1:-warc.paths}

while IFS= read -r line
do
  echo "https://data.commoncrawl.org/$line" >> "$temp_file"
done < "$file"

mv "$temp_file" "$file"