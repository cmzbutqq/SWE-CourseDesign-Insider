#!/usr/bin/env bash
set -euo pipefail

mkdir -p /run/mysqld
chown -R mysql:mysql /run/mysqld /var/lib/mysql

if [ ! -d /var/lib/mysql/mysql ]; then
  mariadb-install-db --user=mysql --datadir=/var/lib/mysql >/tmp/mariadb-init.log 2>&1
fi

exec /usr/bin/supervisord -n -c /etc/supervisor/conf.d/middleware-node.conf
