#!/bin/bash
# -*- mode:shell-script; coding:utf-8; -*-
#
# provisionProxy.sh
#
# A bash script for provisioning a proxy into an organization in the
# Apigee Edge Gateway. This script supports the Hashcash example.
#
# Last saved: <2016-December-22 16:21:32>
#

verbosity=2
orgname=""
envname="test"
nametag="hashcash"
proxyname="hashcash"
requiredcache="hashcash-cache"
defaultmgmtserver="https://api.enterprise.apigee.com"
netrccreds=0
resetAll=0
credentials=""
TAB=$'\t'
scriptdir="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
apiproxydir="$(cd "${scriptdir}/../bundle/apiproxy";pwd)"

usage() {
  local CMD=`basename $0`
  echo "$CMD: "
  echo "  Imports and deploys an API Proxy for the Hashcash example."
  echo "  Uses the curl utility."
  echo "usage: "
  echo "  $CMD [options] "
  echo "options: "
  echo "  -m url    optional. the base url for the mgmt server."
  echo "  -o org    required. the org to use."
  echo "  -e env    optional. the environment to use. default: ${envname}"
  echo "  -u creds  optional. http basic authn credentials for the API calls."
  echo "  -n        optional. tells curl to use .netrc to retrieve credentials"
  echo "  -r        optional. tells the script to reset everything (undeploy and delete the proxy)."
  echo "  -q        quiet; decrease verbosity by 1"
  echo "  -v        verbose; increase verbosity by 1"
  echo
  echo "Current parameter values:"
  echo "  mgmt api url: $defaultmgmtserver"
  echo "     verbosity: $verbosity"
  echo
  exit 1
}

## function MYCURL
## Print the curl command, omitting sensitive parameters, then run it.
## There are side effects:
## 1. puts curl output into file named ${CURL_OUT}. If the CURL_OUT
##    env var is not set prior to calling this function, it is created
##    and the name of a tmp file in /tmp is placed there.
## 2. puts curl http_status into variable CURL_RC
MYCURL() {
  [[ -z "${CURL_OUT}" ]] && CURL_OUT=`mktemp /tmp/apigee-edge-provision-demo.curl.out.XXXXXX`
  [[ -f "${CURL_OUT}" ]] && rm ${CURL_OUT}
  [[ $verbosity -gt 0 ]] && echo "curl $@"

  # run the curl command
  CURL_RC=`curl $credentials -s -w "%{http_code}" -o "${CURL_OUT}" "$@"`
  [[ $verbosity -gt 0 ]] && echo "==> ${CURL_RC}"
}

CleanUp() {
  [[ -f ${CURL_OUT} ]] && rm -rf ${CURL_OUT}
}

echoerror() { echo "$@" 1>&2; }

choose_mgmtserver() {
  local name
  echo
  read -p "  Which mgmt server (${defaultmgmtserver}) :: " name
  name="${name:-$defaultmgmtserver}"
  mgmtserver=$name
  echo "  mgmt server = ${mgmtserver}"
}


choose_credentials() {
  local username password

  read -p "username for Edge org ${orgname} at ${mgmtserver} ? (blank to use .netrc): " username
  echo
  if [[ "$username" = "" ]] ; then  
    credentials="-n"
  else
    echo -n "Org Admin Password: "
    read -s password
    echo
    credentials="-u ${username}:${password}"
  fi
}

maybe_ask_password() {
  local password
  if [[ ${credentials} =~ ":" ]]; then
    credentials="-u ${credentials}"
  else
    echo -n "password for ${credentials}?: "
    read -s password
    echo
    credentials="-u ${credentials}:${password}"
  fi
}



check_org() {
  [[ $verbosity -gt 0 ]] && echo "checking org ${orgname}..."
  MYCURL -X GET  ${mgmtserver}/v1/o/${orgname}
  if [[ ${CURL_RC} -eq 200 ]]; then
    check_org=0
  else
    check_org=1
  fi
}


random_string() {
  local rand_string
  rand_string=$(cat /dev/urandom |  LC_CTYPE=C  tr -cd '[:alnum:]' | head -c 10)
  echo ${rand_string}
}



produce_proxy_zip() {
    local curdir=$(pwd) zipout r=$(random_string) destzip
    destzip="/tmp/${nametag}.apiproxy.${r}.zip"
    
    if [[ -f "${destzip}" ]]; then
        [[ $verbosity -gt 0 ]] && echo "removing the existing zip..."
        rm -rf "${destzip}"
    fi
    [[ $verbosity -gt 0 ]] && echo "Creating the zip ${destzip}..."

    cd ${apiproxydir}
    cd ..

    zipout=$(zip -r "$destzip" apiproxy -x "*/*.*~" -x "*/.tern-port"  -x "*/.DS_Store" -x "*/Icon*" -x "*/#*.*#" -x "*/node_modules/*")

    cd ${curdir}
    
    if [[ ! -f ${destzip} ]] || [[ ! -s ${destzip} ]]; then
        echo
        echo "missing or zero length zip file"
        echo
        CleanUp
        exit 1
    fi
    [[ $verbosity -gt 1 ]] && unzip -l "${destzip}"
    
    apiproxyzip="${destzip}"
}


import_proxy_bundle() {
    local bundleZip="${apiproxyzip}"
    [[ $verbosity -gt 0 ]] && echo "Importing the bundle as ${proxyname}..."

    MYCURL -X POST -H "Content-Type: application/octet-stream" \
           "${mgmtserver}/v1/o/${orgname}/apis?action=import&name=${proxyname}" \
           -T ${bundleZip} 

    if [[ ${CURL_RC} -ne 201 ]]; then
        echo
        echoerror "failed importing the proxy ${proxyname}"
        cat ${CURL_OUT}
        echo
        echo
        importedRevision=""
    else
        [[ $verbosity -gt 1 ]] && cat ${CURL_OUT} && echo && echo

        ## what revision did we just import?
        importedRevision=$(cat ${CURL_OUT} | grep \"revision\" | tr '\r\n' ' ' | sed -E 's/"revision"|[:, "]//g')
        [[ $verbosity -gt 0 ]] && echo "This is revision $importedRevision"
    fi
    rm ${bundleZip}
 }


deploy_proxy() {
    local proxy="${proxyname}" rev=$importedRevision
    # org and environment do not vary
    [[ $verbosity -gt 0 ]] && echo "deploying revision ${rev} of proxy ${proxy}..."
    MYCURL -X POST -H "Content-type:application/x-www-form-urlencoded" \
           "${mgmtserver}/v1/o/${orgname}/e/${envname}/apis/${proxy}/revisions/${rev}/deployments" \
           -d 'override=true&delay=60'

    if [[ ${CURL_RC} -ne 200 ]]; then
        echo
        echoerror "failed deploying the revision."
        cat ${CURL_OUT}
        echo
        echo
        CleanUp
        exit 1
    fi  
}






parse_deployments_output() {
    local output_parsed
    ## extract the environment names and revision numbers in the list of deployments.
    output_parsed=$(cat ${CURL_OUT} | grep -A 6 -B 2 "revision")

    if [ $? -eq 0 ]; then

        deployed_envs=`echo "${output_parsed}" | grep -B 2 revision | grep name | sed -E 's/[\",]//g'| sed -E 's/name ://g'`

        deployed_revs=`echo "${output_parsed}" | grep -A 5 revision | grep name | sed -E 's/[\",]//g'| sed -E 's/name ://g'`

        IFS=' '; declare -a rev_array=(${deployed_revs})
        IFS=' '; declare -a env_array=(${deployed_envs})

        m=${#rev_array[@]}
        [[ $verbosity -gt 0 ]] && echo "found ${m} deployed revisions"

        deployments=()
        let m-=1
        while [ $m -ge 0 ]; do
            rev=${rev_array[m]}
            env=${env_array[m]}
            # trim spaces
            rev="$(echo "${rev}" | tr -d '[[:space:]]')"
            env="$(echo "${env}" | tr -d '[[:space:]]')"
            echo "${env}=${rev}"
            deployments+=("${env}=${rev}")
            let m-=1
        done
        have_deployments=1
    fi
}

clear_env_state() {
    local apparray revisionarray prod env rev deployment dev app i j

    echo "check for the ${proxyname} apiproxy..."
    MYCURL -X GET "${mgmtserver}/v1/o/${orgname}/apis/${proxyname}"
    if [[ ${CURL_RC} -eq 200 ]]; then
        
        echo "checking deployments"
        MYCURL -X GET "${mgmtserver}/v1/o/${orgname}/apis/${proxyname}/deployments"
        if [[ ${CURL_RC} -eq 200 ]]; then
            echo "found, querying it..."
            parse_deployments_output

            # undeploy from any environments in which the proxy is deployed
            for deployment in ${deployments[@]}; do
                env=`expr "${deployment}" : '\([^=]*\)'`
                # trim spaces
                env="$(echo "${env}" | tr -d '[[:space:]]')"
                rev=`expr "$deployment" : '[^=]*=\([^=]*\)'`
                MYCURL -X POST "${mgmtserver}/v1/o/${orgname}/apis/${proxyname}/revisions/${rev}/deployments?action=undeploy&env=${env}"
                ## ignore errors
            done
        fi
        
        # delete all revisions
        MYCURL -X GET ${mgmtserver}/v1/o/${orgname}/apis/${proxyname}/revisions
        revisionarray=(`cat ${CURL_OUT} | grep "\[" | sed -E 's/[]",[]//g'`)
        for i in "${!revisionarray[@]}"; do
            rev=${revisionarray[i]}
            echo "delete revision $rev"
            MYCURL -X DELETE "${mgmtserver}/v1/o/${orgname}/apis/${proxyname}/revisions/${rev}"
            if [[ $CURL_RC -ne 200 ]]; then 
                echo
                echo CURL_RC = ${CURL_RC}
                echo
                cat ${CURL_OUT}
                echo
            fi
        done

        [[ $verbosity -gt 0 ]] && echo "delete the api"
        MYCURL -X DELETE ${mgmtserver}/v1/o/${orgname}/apis/${proxyname}
        if [[ ${CURL_RC} -ne 200 ]]; then
            echo "failed to delete that API"
            echo
            echo CURL_RC = ${CURL_RC}
            echo
            cat ${CURL_OUT}
            echo
        fi 
    fi
}


## =======================================================

while getopts "hm:o:e:u:nd:p:qvr" opt; do
  case $opt in
    h) usage ;;
    m) mgmtserver=$OPTARG ;;
    o) orgname=$OPTARG ;;
    e) envname=$OPTARG ;;
    u) credentials="-u $OPTARG" ;;
    n) netrccreds=1 ;;
    r) resetAll=1 ;;
    q) verbosity=$(($verbosity-1)) ;;
    v) verbosity=$(($verbosity+1)) ;;
    *) echo "unknown arg" && usage ;;
  esac
done

echo
if [[ "X$mgmtserver" = "X" ]]; then
  mgmtserver="$defaultmgmtserver"
fi 

if [[ "X$orgname" = "X" ]]; then
    echo "You must specify an org name (-o)."
    echo
    usage
    exit 1
fi

if [[ "X$credentials" = "X" ]]; then
  if [[ ${netrccreds} -eq 1 ]]; then
    credentials='-n'
  else
    choose_credentials
  fi 
else
  maybe_ask_password
fi 

check_org 
if [[ ${check_org} -ne 0 ]]; then
  echo "that org cannot be validated"
  CleanUp
  exit 1
fi

if [[ $resetAll -eq 1 ]]; then 
    clear_env_state
else 
    produce_proxy_zip

    [[ ! -f "${apiproxyzip}" ]] && echo "no API proxy zip" && exit 1

    import_proxy_bundle

    [[ ! -n "$importedRevision" ]] && echo "the import failed" && exit 1

    deploy_proxy

fi

CleanUp
exit 0

