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

# CA values
CA="testCA"
CA_CN="Test CA"

# file name constants
KEY_STORE_SUFFIX="KeyStore"
TRUST_STORE_SUFFIX="TrustStore"

# file extensions
CONFIG_EXT="cnf"
CSR_EXT="csr"
DER_EXT="der"
JKS_EXT="jks"
P12_EXT="p12"
PEM_EXT="pem"
PRIVATE_KEY_EXT="key"
SIGNED_CERT_EXT="crt"

# passes arguments to 'echo' if $VERBOSE is true.
# Otherwise, is a no-op.
log() {
    if [[ $VERBOSE -eq 1 ]]; then
        echo "$@"
    fi
}

mkdir_check_force() {
  local dir=$1

  if [ -d "$dir" ]; then
    if [ $FORCE -eq 1 ]; then
      rm -rf "$dir"
    else
      echo "Error: directory $dir already exists. Use -f flag to overwrite."
      exit
    fi
  fi

  mkdir "$dir"
}

check_return_value() {
  if [ $? -ne 0 ]; then
      # if in verbose mode, echo newline to ensure
      # that error appears on its own line
      if [[ $VERBOSE -eq 1 ]]; then
        echo -e "\n"
      fi
      echo "Error: $1 failed"
      exit
  fi
}

create_ca() {
  # create directory for CA files
  mkdir_check_force "$CA"
  cd "$CA"

  # create CA private key
  log -n "Creating CA private key..."
  openssl genrsa -out "$CA".key 2048 \
    &>/dev/null
  check_return_value "openssl genrsa"
  log "done"

  # create self-signed certificate for CA
  log -n "Creating self-signed certificate for CA..."
  openssl req -x509 -new -nodes \
    -key "$CA"."$PRIVATE_KEY_EXT" -sha256 -days 1024 \
    -out "$CA"."$SIGNED_CERT_EXT" \
    -subj "/C=US/ST=CA/L=Palo Alto/O=Test Org/OU=Test OU/CN=$CA_CN" \
    &>/dev/null
  check_return_value "openssl req -x509"
  log "done"

  # create a DER version of the CA certificate
  log -n "Creating DER certificate for CA..."
  openssl x509 -outform der \
    -in "$CA"."$SIGNED_CERT_EXT" \
    -out "$CA"."$DER_EXT" \
    &>/dev/null
  check_return_value "openssl req -x509"
  log "done"

  # create PKCS12 trust store that contains CA certificate (no password)
  log -n "Creating PKCS12 trust store that contains CA certificate..."
  openssl pkcs12 -export -nokeys \
    -in "$CA"."$SIGNED_CERT_EXT" \
    -out "$CA""$TRUST_STORE_SUFFIX"."$P12_EXT" -passout pass: \
    &>/dev/null
  check_return_value "openssl pkcs12 -export"
  log "done"

  # create JKS trust store that contains CA certificate. JKS stores require a password
  # (even though the password is not needed to read certificates from a JKS), so a
  # default password of "changeit" is specified.
  log -n "Creating JKS trust store that contains CA certificate..."
  keytool -noprompt -import \
    -file "$CA"."$SIGNED_CERT_EXT" \
    -alias "$CA" \
    -keystore "$CA""$TRUST_STORE_SUFFIX"."$JKS_EXT" -storepass changeit \
    &>/dev/null
  check_return_value "keytool -import"
  log "done"

  # restore original working directory
  cd $OLDPWD
}

create_keys() {
  local keyName=$1
  local commonName=$2
  local p12Pass=$3
  local jksPass=$4

  # create directory for keys
  mkdir_check_force "$keyName"
  cd "$keyName"

  # create requested private key
  log -n "Creating private key..."
  openssl genrsa -out "$keyName"."$PRIVATE_KEY_EXT" 2048 \
    &>/dev/null
  check_return_value "openssl genrsa"
  log "done"

  # create CSR for private key
  log -n "Creating CSR for private key..."
  openssl req -new -key "$keyName"."$PRIVATE_KEY_EXT" \
    -out "$keyName"."$CSR_EXT" \
    -subj "/C=US/ST=CA/L=Palo Alto/O=Test Org/OU=Test OU/CN=$commonName" \
    &>/dev/null
  check_return_value "openssl req -new -key"
  log "done"

  # create certificate for private key that is signed by the CA
  log -n "Creating certificate for private key that is signed by the CA..."
  openssl x509 -req \
    -in "$keyName"."$CSR_EXT" \
    -CA ../"$CA"/"$CA"."$SIGNED_CERT_EXT" \
    -CAkey ../"$CA"/"$CA"."$PRIVATE_KEY_EXT" -CAcreateserial \
    -out "$keyName"."$SIGNED_CERT_EXT" \
    -days 500 -sha256 \
    &>/dev/null
  check_return_value "openssl x509 -req"
  log "done"

  local keyWithCert="$keyName_keyWithCert"

  # create single PEM file with certificate and private key
  log -n "Creating single PEM file with certificate and private key..."
  cat "$keyName"."$PRIVATE_KEY_EXT" \
    "$keyName"."$SIGNED_CERT_EXT" \
     > "$keyWithCert"."$PEM_EXT"
  log "done"

  # convert PEM to password-protected PKCS12 file. $keyName is used as
  # the alias for the certificate and key entry.
  log -n "Converting PEM to password-protected PKCS12 file..."
  openssl pkcs12 -export \
    -password pass:"$p12Pass" \
    -in "$keyWithCert"."$PEM_EXT" \
    -out "$keyName""$KEY_STORE_SUFFIX"."$P12_EXT" \
    -name "$keyName" \
    -noiter -nomaciter \
    &>/dev/null
  check_return_value "openssl pkcs12 -export"
  log "done"

  # convert PKCS12 to JKS. Specified JKS password is used as the password
  # for both the keystore and the private key
  log -n "Converting PKCS12 to JKS..."
  keytool -noprompt -importkeystore \
    -srckeystore "$keyName""$KEY_STORE_SUFFIX"."$P12_EXT" -srcstoretype PKCS12 \
    -srcstorepass "$p12Pass" \
    -srcalias "$keyName" \
    -destkeystore "$keyName""$KEY_STORE_SUFFIX"."$JKS_EXT" -deststoretype JKS \
    -deststorepass "$jksPass" \
    -destalias "$keyName" \
    -destkeypass "$jksPass" \
    &>/dev/null
  check_return_value "keytool -importkeystore"

  # clean up temporary files
  rm .pem .srl

  log "done"

  # restore original working directory
  cd $OLDPWD
}

create_combined_jks() {
  local first_store_name=$1
  local first_store_pass=$2
  local second_store_name=$3
  local second_store_pass=$4

  log -n "Creating combined JKS..."
  keytool -noprompt -importkeystore \
    -srckeystore "$first_store_name"/"$first_store_name""$KEY_STORE_SUFFIX"."$JKS_EXT" \
    -srcstorepass "$first_store_pass" \
    -srcalias "$first_store_name" \
    -destkeystore multiple."$JKS_EXT" \
    -deststorepass multiple \
    -destkeypass multiple \
    &>/dev/null
  check_return_value "keytool -importkeystore"

  keytool -noprompt -importkeystore \
    -srckeystore "$second_store_name"/"$second_store_name""$KEY_STORE_SUFFIX"."$JKS_EXT" \
    -srcstorepass "$second_store_pass" \
    -srcalias "$second_store_name" \
    -destkeystore multiple."$JKS_EXT" \
    -deststorepass multiple \
    -destkeypass multiple \
    &>/dev/null
  check_return_value "keytool -importkeystore"
  log "done"
}

create_crl() {
  # CRL command requires a configuration file.
  # Provide content for minimal CA config.
  local crlConf='[ ca ]
default_ca        = CA_default
[ CA_default ]
dir               = ./crlCA
database          = $dir/index.txt
crlnumber         = $dir/crlnumber
default_crl_days  = 30
default_md        = sha1'

  log -n "Creating empty CRL..."

  # create temporary directory for CRL operations.
  # Variable is intentionally global so that 'trap'
  # call can read the value on exit.
  crlTempDir=`mktemp -d -t crlTempDir.XXXXXX`
  # clean up temporary directory on script termination
  trap 'rm -rf "$crlTempDir"' EXIT

  # set working directory to temp directory
  cd $crlTempDir

  # write config to file
  echo "$crlConf" > crlConf."$CONFIG_EXT"

  # create expected supporting files
  mkdir crlCA
  touch crlCA/index.txt
  echo 01 > crlCA/crlnumber

  # generate empty CRL
  openssl ca -gencrl \
    -config crlConf."$CONFIG_EXT" \
    -keyfile $OLDPWD/"$CA"/"$CA"."$PRIVATE_KEY_EXT" \
    -cert $OLDPWD/"$CA"/"$CA"."$SIGNED_CERT_EXT" \
    -out $OLDPWD/crl."$PEM_EXT" \
    &>/dev/null
  check_return_value "openssl ca -gencrl"

  # restore original working directory
  cd $OLDPWD

  log "done"
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
create_ca

# create keys for server
create_keys testServer localhost testServer serverStore

# create keys for client
create_keys testClient localhost testClient clientStore

# create combined JKS
create_combined_jks testServer serverStore testClient clientStore

# create empty crl
create_crl
