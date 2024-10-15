#!/usr/bin/env bash

set -e

curl -f "$RS_SOLR_URL/solr/admin/cores?action=UNLOAD&deleteInstanceDir=true&core=$1"
