#!/bin/sh

num=$1
flag=$2

if [ "$flag" = "-baseline" ]; then
    while [ ! $num = 0 ]; do
        echo $num
        num=$((num-1))
        ncs_cli -u admin <<EOF
    configure
    run show status devices device commit-queue
    run show status devices device commit-queue
    exit
    #show status devices device commit-queue
    exit
EOF
    done
    exit 0
fi


ncs_cli -u admin <<EOF2
    request devices sync-from
    configure
    set devices global-settings trace pretty
    commit
    exit
    exit
EOF2

while [ ! "$num" = 0 ]; do
    echo $num
    num=$((num-1))
    ncs_cli -u admin <<EOF
    configure
    set devices device ex0..2 config r:sys dns server 4.5.6.1
    commit $flag
    set devices device ex0..2 config r:sys dns server 4.5.6.2
    commit $flag
    delete devices device ex0..2 config r:sys dns server
    commit $flag
    exit
    exit
EOF
done
