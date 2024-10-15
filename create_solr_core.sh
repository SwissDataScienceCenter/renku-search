#!/usr/bin/env bash

set -e

curl -f -X POST "$RS_SOLR_URL/api/cores" -d "{\"create\": {\"name\": \"$1\", \"configSet\": \"_default\"}}"
