#!/bin/sh
# script to invoke this in production; make sure you update
# conf/application.conf directly instead of using -D to override
nohup ./bin/foa-challenge \
  -Dplay.mailer.mock=no \
  -Dplay.evolutions.enabled=false \
  -Dplay.http.secret.key=`head -c2096 /dev/urandom | sha256sum |cut -d' ' -f1` &
