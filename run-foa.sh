#!/bin/sh
# script to invoke this in production!
nohup ./bin/foa-challenge \
  -Dhttp.port=9004 \
  -Dplay.mailer.mock=no \
  -Dplay.http.host="https://ncats.io" \
  -Dplay.http.context="/challenge" \
  -Dplay.http.secret.key=`head -c2096 /dev/urandom | sha256sum |cut -d' ' -f1` &
