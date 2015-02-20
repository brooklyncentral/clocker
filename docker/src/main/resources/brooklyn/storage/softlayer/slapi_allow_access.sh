#!/bin/sh
APIUSER='shwetas'
APIKEY='12345'
APIENDPOINT='api.softlayer.com/rest/v3'
STORAGEID=$1

# escape brackets from bash for object masks
ARGS=$(echo "$@" | sed -e "s/\[/\\\[/g" -e "s/\]/\\\]/g")

# When passing values with POST, the json must look like the following:
# '{"parameters": [THINGS]}'
curl -s --max-time 40 -H 'Accept: application/json' https://$APIUSER:$APIKEY@$APIENDPOINT/SoftLayer_Network_Storage/$STORAGEID/allowAccessFromHardware $ARGS | python -m json.tool
