#!/bin/sh
# Supply IDP_ISSUER, CLIENT_ID and CLIENT_SECRET in the environment.
set -eu
: "${IDP_ISSUER:?Set IDP_ISSUER}" "${CLIENT_ID:?Set CLIENT_ID}" "${CLIENT_SECRET:?Set CLIENT_SECRET}"
curl --fail-with-body --silent --show-error \
  --user "$CLIENT_ID:$CLIENT_SECRET" \
  --data-urlencode grant_type=client_credentials \
  --data-urlencode scope=roles \
  "$IDP_ISSUER/oauth2/token"
