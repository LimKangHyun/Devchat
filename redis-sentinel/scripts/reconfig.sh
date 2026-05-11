#!/bin/bash

ROLE=$2
NEW_MASTER_IP=$6
NEW_MASTER_PORT=$7
OLD_MASTER_IP=$4
OLD_MASTER_PORT=$5

if [ "$ROLE" == "leader" ]; then
    redis-cli -h $NEW_MASTER_IP -p $NEW_MASTER_PORT CONFIG SET appendonly no
    redis-cli -h $NEW_MASTER_IP -p $NEW_MASTER_PORT CONFIG REWRITE

    redis-cli -h $OLD_MASTER_IP -p $OLD_MASTER_PORT CONFIG SET appendonly yes
    redis-cli -h $OLD_MASTER_IP -p $OLD_MASTER_PORT CONFIG REWRITE
fi
