#!/bin/bash
# -*- mode:shell-script; coding:utf-8; -*-
#
# Created: <Mon Dec  5 17:51:55 2016>
# Last Updated: <2016-December-22 14:40:08>
#

scriptdir="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
tooldir=${scriptdir}/../project/tool/target
resource=""
bits=""
cash=""

function usage() {
  local CMD=`basename $0`
  echo "$CMD: "
  echo "  Create or verify a Hashcash."
  echo "  Uses the HashcashTool Java program."
  echo "usage to generate a hash: "
  echo "  $CMD -r dchiesa@google.com -b 18"
  echo "usage to parse a hash: "
  echo "  $CMD -c 1:15:161222143021:dchiesa@apigee.com::ac1f7951b1d2fc77:86f910c171fa9d9c"
  echo "options: "
  echo "  -r resource   Optional. resource to include in the hashcash."
  echo "  -b bits       Optional. zero bits for hash collision. Should be 16 < x.  Above 24 may take a long time."
  echo "  -c cash       Optional. cash to parse."
  echo
  exit 1
}


while getopts "hr:b:c:" opt; do
  case $opt in
    h) usage ;;
    r) resource=$OPTARG ;;
    b) bits=$OPTARG ;;
    c) cash=$OPTARG ;;
    *) echo "unknown arg" && usage ;;
  esac
done

####################################################################

if [[ ! -z "${resource}" && ! -z "${bits}" ]]; then
    cash=$(java -classpath "$tooldir/hashcash-tool.jar:$tooldir/lib/*" com.google.apigee.tools.HashcashTool -r "${resource}" -b ${bits})
    echo $cash
    echo 
elif [[ ! -z "${cash}" ]]; then

    java -classpath "$tooldir/hashcash-tool.jar:$tooldir/lib/*" com.google.apigee.tools.HashcashTool -c "${cash}"
else
    usage
fi




