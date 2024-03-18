#!/bin/bash

set -e

OSHOST="http://localhost:9200"
OSCREDENTIALS="" # "-u admin:admin"

echo "Deleting content index template"

curl $OSCREDENTIALS -s -XDELETE "$OSHOST/_index_template/content"

echo
echo
echo "Creating content index template with mapping"

curl $OSCREDENTIALS -s -XPUT $OSHOST/_index_template/content -H 'Content-Type: application/json' --upload-file src/main/resources/content.mapping

echo