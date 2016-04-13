#!/bin/bash
#
# Copyright 2016 Palantir Technologies
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
# <http://www.apache.org/licenses/LICENSE-2.0>
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

set -euo pipefail

# import certificate functions
source "cert_functions.sh"

# Creates the directory with the provided name. If the directory already
# exists, an error will be echoed and the script will exit with exit code 1
# unless $FORCE is true, in which case the existing directory will be removed
# using `rm -rf` and the specified directory will be created.
#
# $1: name of the directory that should be created.
#
# Result: directory with name "$1" will be created in working directory if
#         it did not already exist or if $FORCE is 1, otherwise, will echo
#         error and exit with exit code 1.
mkdir_check_force() {
  local dir=$1

  if [ -d "$dir" ]; then
    if [ $FORCE -eq 1 ]; then
      rm -rf "$dir"
    else
      echo "Error: directory $dir already exists. Use -f flag to overwrite."
      exit 1
    fi
  fi

  mkdir "$dir"
}

create_ca_dir() {
  local ca_name=$1
  local jks_pass=$2

  # create directory for CA files
  mkdir_check_force "$ca_name"
  cd "$ca_name"
  local old_pwd=$OLDPWD

  # create root CA
  create_root_ca "$ca_name"

  # create a DER version of the CA certificate
  create_der_from_pem_cert "$ca_name" "$ca_name.$CER_EXT"

  # create PKCS12 trust store that contains CA certificate
  create_p12_trust_store_from_pem "$ca_name" "$ca_name.$CER_EXT"

  # create JKS trust store that contains CA certificate
  create_jks_trust_store_from_pem "$ca_name" "$jks_pass" "$ca_name.$CER_EXT"

  # restore original working directory
  cd $old_pwd
}

create_intermediate_ca_dir() {
  local intermediate_ca_name=$1
  local signing_ca_name=$2

  # create directory for CA files
  mkdir_check_force "$intermediate_ca_name"
  cd "$intermediate_ca_name"
  local old_pwd=$OLDPWD

  create_intermediate_ca "$intermediate_ca_name" "../$signing_ca_name/$signing_ca_name.$CER_EXT" "../$signing_ca_name/$signing_ca_name.$KEY_EXT"

  # restore original working directory
  cd $old_pwd
}

create_key_pair_dir() {
  local key_name=$1
  local common_name=$2
  local p12_pass=$3
  local jks_pass=$4
  local ca_name=$5

  # create directory for keys
  mkdir_check_force "$key_name"
  cd "$key_name"
  local old_pwd=$OLDPWD

  # create key pair
  create_key_pair "$key_name" "$common_name" "../$ca_name/$ca_name.$CER_EXT" "../$ca_name/$ca_name.$KEY_EXT" "$common_name"

  # create combined file (key + certificate as single PEM)
  cat "$key_name.$KEY_EXT" "$key_name.$CER_EXT" > "$key_name.$PEM_EXT"

  # create PKCS12 store
  create_p12_key_store_from_pem "$key_name" "$p12_pass" "$key_name.$CER_EXT" "$key_name.$KEY_EXT"

  # create JKS
  create_jks_key_store_from_p12 "$key_name" "$jks_pass" "$key_name.$P12_EXT" "$key_name" "$p12_pass"

  # restore original working directory
  cd $old_pwd
}

create_child_keys() {
  local key_name=$1
  local common_name=$2
  local ca_name=$3

  # create directory for keys
  mkdir_check_force "$key_name"
  cd "$key_name"
  local old_pwd=$OLDPWD

  create_key_pair "$key_name" "$common_name" "../$ca_name/$ca_name.$CER_EXT" "../$ca_name/$ca_name.$KEY_EXT" "$common_name"

  cat "$key_name.$KEY_EXT" \
      "$key_name.$CER_EXT" \
      "../$ca_name/$ca_name.$CER_EXT" \
      > "${key_name}_key_cert_chain.$PEM_EXT"

  # restore original working directory
  cd $old_pwd
}

create_crl_dir() {
  local crl_name="crl"

  # create directory for CRL files
  mkdir_check_force "$crl_name"
  cd "$crl_name"
  local old_pwd=$OLDPWD

  # create empty crl for testCA
  create_crl "testCA" "../testCA/testCA.$CER_EXT" "../testCA/testCA.$KEY_EXT"

  # create empty crl for intermediateCA
  create_crl "intermediateCA" "../intermediateCA/intermediateCA.$CER_EXT" "../intermediateCA/intermediateCA.$KEY_EXT"

  # create combined CRL
  cat testCA.crl intermediateCA.crl > combined.crl

  # restore original working directory
  cd $old_pwd
}

# parse command-line options
VERBOSE=0
FORCE=0
while getopts ":vf" opt; do
  case $opt in
    f)
      FORCE=1
      ;;
    v)
      VERBOSE=1
      ;;
    \?)
      echo "Invalid option: -$OPTARG" >&2
      exit
      ;;
  esac
done

# create CA
create_ca_dir "testCA" "changeit"

# create keys for server
create_key_pair_dir "testServer" "localhost" "testServer" "serverStore" "testCA"

# create keys for client
create_key_pair_dir "testClient" "client" "testClient" "clientStore" "testCA"

# create combined JKS
combine_jks_key_stores "multiple" "multiple" \
  "testServer/testServer.$JKS_EXT" "testServer" "serverStore" \
  "testClient/testClient.$JKS_EXT" "testClient" "clientStore" \

# create intermediate CA
create_intermediate_ca_dir "intermediateCA" "testCA"

# create child key
create_child_keys "testChild" "child" "intermediateCA"

# create CRL
create_crl_dir
