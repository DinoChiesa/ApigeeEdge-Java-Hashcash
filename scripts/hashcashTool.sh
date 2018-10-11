#!/bin/bash
# -*- mode:shell-script; coding:utf-8; -*-
#
# Created: <Mon Dec  5 17:51:55 2016>
# Last Updated: <2018-October-11 13:26:53>
#

scriptdir="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
tooldir=${scriptdir}/../project/tool/target
hc_classpath="${tooldir}/hashcash-tool-1.0.2.jar:$tooldir/lib/*"
resource=""
bits=""
cash=""

function usage() {
  local CMD=`basename $0`
  echo "$CMD: "
  echo "  Create or verify a Hashcash."
  echo "  Uses the HashcashTool Java program."
  echo "generate a hash with 18 collision bits: "
  echo "  $CMD -r dchiesa@google.com -b 18"
  echo "generate a hash with SHA-256: "
  echo "  $CMD -r dchiesa@google.com -b 20 -s SHA-256"
  echo "parse a hash: "
  echo "  $CMD -c 1:15:161222143021:dchiesa@apigee.com::ac1f7951b1d2fc77:86f910c171fa9d9c"
  echo "parse and verify a hash for 18 bits: "
  echo "  $CMD -c 1:15:161222143021:dchiesa@apigee.com::ac1f7951b1d2fc77:86f910c171fa9d9c -b 18"
  echo "options: "
  echo "  -r resource   Optional. resource to include in the hashcash."
  echo "  -b bits       Optional. zero bits for hash collision. Should be 16 < x.  Above 24 may take a long time."
  echo "  -f hashFn     Optional. SHA1 or SHA-256, for example. Defaults to SHA1"
  echo "  -c cash       Optional. cash to parse."
  echo
  exit 1
}


while getopts "hr:b:c:a:" opt; do
  case $opt in
    h) usage ;;
    r) resource=$OPTARG ;;
    b) bits=$OPTARG ;;
    c) cash=$OPTARG ;;
    f) hashFn=$OPTARG ;;
    *) echo "unknown arg" && usage ;;
  esac
done

####################################################################

if [[ ! -z "${resource}" && ! -z "${bits}" ]]; then
    echo java -classpath "${hc_classpath}" com.google.apigee.tools.HashcashTool -r "${resource}" -b ${bits} -f ${hashFn}
    cash=$(java -classpath "${hc_classpath}" com.google.apigee.tools.HashcashTool -r "${resource}" -b ${bits} -f ${hashFn})
    echo $cash
    echo 
elif [[ ! -z "${cash}" ]]; then

    java -classpath "${hc_classpath}" com.google.apigee.tools.HashcashTool -c "${cash}" -a ${hashFn}
else
    usage
fi




