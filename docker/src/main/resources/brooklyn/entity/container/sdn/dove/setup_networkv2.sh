#!/bin/bash

# UUID of the Container instance
UUID=$1
# Network ID
NETWORK_ID=$2
# Container Port ID
PORT_ID=$3
# Container mac address
MAC=$4
# Container ethernet interface name, i.e. eth0
INTF_NAME=eth0
# CIDR IP address assigned to the above interface
CIDR_IP=$5
# Default gateway assigned to the Container
GATEWAY=$6
#VNID to be used
VNID=$7

DACTL=/usr/bin/dactl

if [ "$UUID" == "" ] || [ "$INTF_NAME" == "" ] || [ "$CIDR_IP" == "" ] || [ "$GATEWAY" == "" ] || [ "$NETWORK_ID" == "" ] || [ "$PORT_ID" == "" ] || [ "$MAC" == "" ] || [ "$VNID" == "" ]; then
    echo 'Usage: setup_networkv2.sh UUID NETWORK_ID PORT_ID MAC CIDR_ID GATEWAY VNID'
    exit -1
fi

# Get the Container PID
PID=`docker inspect -f '{{.State.Pid}}' $UUID`
if [ "$PID" == "" ]; then
    echo 'Docker container has not been found.'
    exit -1
fi

# Prepare working directories
mkdir -p /var/run/netns
ln -s /proc/$PID/ns/net /var/run/netns/$PID

# Create virtual ethernet interfaces
IF_SUFFIX=`echo $PORT_ID | cut -b 1-8`
TAPIF=dkb$IF_SUFFIX
NSIF=dkr$IF_SUFFIX
ip link add name $TAPIF type veth peer name $NSIF
ip link set $TAPIF up
ip link set $NSIF netns $PID

# Config networking within the container
ip netns exec $PID ip link set $NSIF name $INTF_NAME
ip netns exec $PID ip link set $INTF_NAME address $MAC
ip netns exec $PID ip addr add $CIDR_IP dev $INTF_NAME
ip netns exec $PID ip link set $INTF_NAME up

NSIP=$(echo $CIDR_IP | cut -d/ -f 1)
echo "NSIP is $NSIP"
BRIDGE_ID=`echo $NETWORK_ID | cut -b 1-11`
BRIDGE_NAME=dovebr_$VNID
echo "bridge name is: $BRIDGE_NAME"

# Register to DOVE agent
$DACTL register dockerport \
	$BRIDGE_NAME \
	$TAPIF \
        $NSIF \
	$MAC \
        $NSIP \
        $VNID


ip netns exec $PID ip route add default via $GATEWAY
#ip netns exec $PID ping -c 3 $GATEWAY

echo 'DONE'
