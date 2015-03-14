#!/bin/sh
yum install iscsi-initiator-utils
yum install device-mapper-multipath

cat <<EOF >/etc/multipath.conf
defaults {
user_friendly_names no
max_fds max
flush_on_last_del yes
queue_without_daemon no
dev_loss_tmo infinity
fast_io_fail_tmo 5
}
# All data under blacklist must be specific to your system.
blacklist {
wwid "SAdaptec*"
devnode "^hd[a-z]"
devnode "^sd[a-z]"
devnode "^(ram|raw|loop|fd|md|dm-|sr|scd|st)[0-9]*"
devnode "^cciss.*"
}
devices {
device {
vendor "NETAPP"
product "LUN"
path_grouping_policy group_by_prio
features "3 queue_if_no_path pg_init_retries 50"
prio "alua"
path_checker tur
failback immediate
path_selector "round-robin 0"
hardware_handler "1 alua"
rr_weight uniform
rr_min_io 128
getuid_callout "/lib/udev/scsi_id -g -u -d /dev/%n"
}
}
EOF
modprobe dm-multipath
service multipathd start
chkconfig multipathd on

cat <<EOF >/etc/iscsi/initiatorname.iscsi
InitiatorName=$1
EOF

cat <<EOF >> /etc/iscsi/iscsid.conf
node.session.auth.authmethod = CHAP
node.session.auth.username = $2
node.session.auth.password = $3
discovery.sendtargets.auth.authmethod = CHAP
discovery.sendtargets.auth.username = $2
discovery.sendtargets.auth.password = $3
EOF

chkconfig iscsi on
chkconfig iscsid on
service iscsi start
service iscsid start
iscsiadm -m discovery -t sendtargets -p $4 -D
iscsiadm -m node -L automatic