#!/bin/sh
APIUSER='shwetas'
APIKEY='1234'
APIENDPOINT='api.softlayer.com/rest/v3'
STORAGEID=$1

# escape brackets from bash for object masks
ARGS=$(echo "$@" | sed -e "s/\[/\\\[/g" -e "s/\]/\\\]/g")

# When passing values with POST, the json must look like the following:
# '{"parameters": [THINGS]}'

curl -g "https://$APIUSER:$APIKEY@$APIENDPOINT/SoftLayer_Network_Storage_Iscsi/$STORAGEID/getObject.xml?objectMask=mask[storageGroups[allowedHosts[credential]]]"
curl -g "https://$APIUSER:$APIKEY@$APIENDPOINT/SoftLayer_Network_Storage/$STORAGEID/getNetworkConnectionDetails"
