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

# Provides functions for creating certificate material and stores.
# Assumes existence of `openssl` for most operations and `keytool` for JKS operations.
# This script sets "safe" mode (-e, -u, -o pipefail) and assumes that it is on throughout.

set -euo pipefail

# logging
VERBOSE=0
LOG_INDENT_LEVEL=0
LOG_INDENT_INCREMENT=2

# Passes arguments to `echo` after echoing $LOG_INDENT_LEVEL spaces if $VERBOSE is true.
# If $VERBOSE is not true, is a no-op.
log() {
  local counter=0
  if [[ $VERBOSE -eq 1 ]]; then
    # print spacing
    while [ $counter -lt "$LOG_INDENT_LEVEL" ]; do
      counter=$((counter+1))
      echo -n " "
    done

    echo "$@"
  fi
}

# Echoes "done" if $VERBOSE is true. If $VERBOSE is not true, is a no-op.
# Does not print any leading spaces regardless of $LOG_INDENT_LEVEL.
log_done() {
  if [[ $VERBOSE -eq 1 ]]; then
        echo "done"
  fi
}

# Increments $LOG_INDENT_LEVEL
increment_log_indent() {
  LOG_INDENT_LEVEL=$((LOG_INDENT_LEVEL+LOG_INDENT_INCREMENT))
}

# Decrements $LOG_INDENT_LEVEL
decrement_log_indent() {
  LOG_INDENT_LEVEL=$((LOG_INDENT_LEVEL-LOG_INDENT_INCREMENT))
}

# Create temporary directory for certificate operations.
# Variable is intentionally global so that `trap` call
# can read the value on exit.
TEMP_DIR=`mktemp -d -t generate_certs.XXXXXX`
# clean up temporary directory on script termination
trap 'rm -rf "$TEMP_DIR"' EXIT

# constants for extensions for generated files
CER_EXT="cer"
CRL_EXT="crl"
CSR_EXT="csr"
DER_EXT="der"
KEY_EXT="key"
JKS_EXT="jks"
P12_EXT="p12"
PEM_EXT="pem"

# Base OpenSSL configuration for generating CA certificates.
# Defines a [ v3_root_ca ] section with settings typical for a root
# CA and a [ v3_intermediate_ca ] section with settings typical for
# an intermediate CA. The only difference between them is that the
# intermediate CA has a "pathlen:0" constraint which means that it
# will only verify 1 level below itself (it cannot be used to create
# other certificates that can act as CAs).
OPENSSL_CA_CONF='[ req ]
distinguished_name              = req_distinguished_name

[ req_distinguished_name ]
# blank -- set manually using the "-subj" flag

[ v3_root_ca ]
# Extensions for a typical root CA (`man x509v3_config`).
subjectKeyIdentifier            = hash
authorityKeyIdentifier          = keyid:always,issuer
basicConstraints                = critical, CA:true
keyUsage                        = critical, digitalSignature, cRLSign, keyCertSign

[ v3_intermediate_ca ]
# Extensions for a typical intermediate CA (`man x509v3_config`).
subjectKeyIdentifier            = hash
authorityKeyIdentifier          = keyid:always,issuer
basicConstraints                = critical, CA:true, pathlen:0
keyUsage                        = critical, digitalSignature, cRLSign, keyCertSign'

# Base OpenSSL configuration for generating non-CA certificates.
# This configuration has an [ alt_names ] section that starts empty.
# If subject alternative names are desired, they should be appended.
OPENSSL_CERT_CONF='[ req ]
distinguished_name              = req_distinguished_name

[ req_distinguished_name ]
# blank -- set manually using the "-subj" flag

[ v3 ]
# Extensions to add to a certificate request
basicConstraints                = CA:FALSE
subjectKeyIdentifier            = hash
authorityKeyIdentifier          = keyid,issuer
subjectAltName                  = @alt_names

[ alt_names ]'

# Base OpenSSL configuration for generating CRLs.
# The value of the base directory for the CRL CA directory ("dir")
# is as "{{ca_dir}}" -- this value should be replaced with a valid
# path to a directory that can be used to write CA supporting files
# before the configuration is used.
OPENSSL_CRL_CONF='[ ca ]
default_ca        = CA_default

[ CA_default ]
dir               = {{ca_dir}}
database          = $dir/index.txt
crlnumber         = $dir/crlnumber
default_crl_days  = 30
default_md        = sha256
crl_extensions    = crl_ext

[ crl_ext ]
# Extension for CRLs (`man x509v3_config`).
authorityKeyIdentifier=keyid:always'

# Checks the exit code of the last command that was executed.
# If the exit code is non-zero, print the arguments and exit
# the script with the same exit code. Uses `$?` as the exit code
# to check, so must be called immediately after the command to check.
#
# $1: description of error
# $2: output of the command that failed
#
# Result: if the exit code of the previous command was non-zero,
#         prints the arguments and exits the script with the same
#         exit code. Otherwise, is a no-op.
check_exit_code() {
  local exit_code=$?
  if [ $exit_code -ne 0 ]; then
    # if in verbose mode, echo newline to ensure
    # that error appears on its own line
    if [[ $VERBOSE -eq 1 ]]; then
      echo -e "\n"
    fi

    echo "$1"
    echo "$2"
    exit $exit_code
  fi
}

# Creates a root CA that consists of a private key and a self-signed certificate. The generated
# key pair can be used to create both regular certificates and intermediate CA certificates.
#
# $1: the base name for the generated CA files. Also used as the common name for the certificate.
#
# Result: Creates the files "$1.$KEY_EXT" and "$1.$CER_EXT" in the working directory.
create_root_ca() {
  local ca_name=$1
  local conf_file=$(write_conf_file "$OPENSSL_CA_CONF")
  local output

  # create CA private key
  create_key "$ca_name"

  # create self-signed CA
  log -n "Creating self-signed certificate for CA..."
  set +e
  output=$(
    openssl \
      req \
      -x509 \
      -new \
      -sha256 \
      -nodes \
      -batch \
      -days 3650 \
      -set_serial 01 \
      -config "$conf_file" \
      -subj "/CN=$ca_name" \
      -extensions v3_root_ca \
      -key "$ca_name.$KEY_EXT" \
      -out "$ca_name.$CER_EXT" \
      2>&1)
  check_exit_code "failed to create self-signed CA" "$output"
  set -e
  log_done
}

# Creates an intermediate CA that consists of a private key and a certificate that is signed
# by the provided CA. The generated key pair can be used to create certificates, but cannot
# be used to generate CA certificates (pathlen is set to 0).
#
# $1: the base name for the generated CA files. Also used as the common name for the certificate.
# $2: path to the signing CA certificate in PEM format.
# $3: path to the signing CA private key in PEM format.
#
# Result: Creates the files "$1.$KEY_EXT" and "$1.$CER_EXT" in the working directory.
create_intermediate_ca() {
  local ca_name=$1
  local signing_ca_certificate=$2
  local signing_ca_private_key=$3
  local conf_file=$(write_conf_file "$OPENSSL_CA_CONF")
  local output

  # create CA private key
  create_key "$ca_name"

  # create CSR in temp directory
  local csr_path=`mktemp $TEMP_DIR/${ca_name}.${CSR_EXT}.XXXXXX`

  set +e
  log -n "Creating CSR for intermediate CA..."
  output=$(
    openssl \
      req \
      -new \
      -sha256 \
      -batch \
      -config "$conf_file" \
      -subj "/CN=$ca_name" \
      -extensions v3_intermediate_ca \
      -key "$ca_name.$KEY_EXT" \
      -out "$csr_path" \
      2>&1)
  check_exit_code "failed to create CSR for intermediate CA" "$output"
  set -e
  log_done

  set +e
  log -n "Creating signed certificate for intermediate CA..."
  output=$(
    # create signed CA
    openssl \
      x509 \
      -req \
      -days 3650 \
      -in "$csr_path" \
      -CA "$signing_ca_certificate" \
      -CAkey "$signing_ca_private_key" \
      -set_serial 01 \
      -out "$ca_name.$CER_EXT" \
      -extfile "$conf_file" \
      -extensions v3_intermediate_ca \
      2>&1)
  check_exit_code "failed to sign intermediate CA" "$output"
  set -e
  log_done
}

# Creates an empty CRL for the specified CA.
#
# $1: the base name for the generated CRL file.
# $2: path to the certificate for the CRL CA in PEM format.
# $3: path to the private key for the CRL CA in PEM format.
#
# Result: Creates the file "$1.$CRL_EXT" in the working directory.
create_crl() {
  local base_name=$1
  local ca_cert=$2
  local ca_key=$3
  local output

  local crl_temp_dir=`mktemp -d $TEMP_DIR/crlTempDir.XXXXXX`
  local crl_conf="${OPENSSL_CRL_CONF/\{\{ca_dir\}\}/$crl_temp_dir}"

  # write config to file
  echo "$crl_conf" > "$crl_temp_dir/crl_conf.cnf"

  # create expected supporting files
  touch "$crl_temp_dir/index.txt"
  echo 01 > "$crl_temp_dir/crlnumber"

  # generate empty CRL
  log -n "Creating CRL..."
  set +e
  output=$(
    openssl \
      ca \
      -gencrl \
      -config "$crl_temp_dir/crl_conf.cnf" \
      -cert "$ca_cert" \
      -keyfile "$ca_key" \
      -out "${base_name}.$CRL_EXT" \
      2>&1)
  check_exit_code "Failed to create CRL" "$output"
  set -e
  log_done
}

# Creates a private key with the provided name. The private key
# will be a 2048 bit RSA key.
#
# $1: the base name for the generated key.
#
# Result: Creates the file "$1.$KEY_EXT" in the working directory.
create_key() {
  local key_name=$1
  local output

  log -n "Creating private key..."
  set +e
  output=$(
    openssl \
      genrsa \
      -out "$key_name.$KEY_EXT" \
      2048 \
      2>&1)
  check_exit_code "failed to create private key" "$output"
  set -e
  log_done
}

# Creates a private key and an associated certificate that is signed by a CA.
#
# $1: the base name for the generated key pair files.
# $2: the common name (CN) that should be used.
# $3: path to the CA certificate in PEM format.
# $4: path to the CA private key in PEM format.
# $5+: the subject alternate names for the certificate.
#
# Result: creates the files "$1.$KEY_EXT" and "$1.$CER_EXT" in the working directory.
create_key_pair() {
  local key_name=$1
  local common_name=$2
  local ca_certificate=$3
  local ca_private_key=$4
  local output

  local all_args=("$@")
  local conf_content=$(get_openssl_conf_with_subject_alternative_names "${all_args[@]:4}")
  local conf_file=$(write_conf_file "$conf_content")

  # create private key
  create_key $key_name

  # create CSR in temp directory
  log -n "Creating CSR for private key..."
  local csr_path=`mktemp $TEMP_DIR/${key_name}.${CSR_EXT}.XXXXXX`
  set +e
  output=$(
    openssl \
      req \
      -new \
      -batch \
      -out "$csr_path" \
      -key "$key_name.$KEY_EXT" \
      -config "$conf_file" \
      -subj "/CN=$common_name" \
      2>&1)
  check_exit_code "failed to create CSR for private key" "$output"
  set -e
  log_done

  # create signed certificate
  log -n "Creating signed certificate for private key..."
  set +e
  output=$(
    openssl \
      x509 \
      -req \
      -days 3650 \
      -in "$csr_path" \
      -CA "$ca_certificate" \
      -CAkey "$ca_private_key" \
      -set_serial 01 \
      -out "$key_name.$CER_EXT" \
      -extensions v3 \
      -extfile "$conf_file" \
      2>&1)
  check_exit_code "failed to create certificate for private key" "$output"
  set -e
  log_done
}

# Creates a certificate in DER format by converting the provided certificate
# in PEM format.
#
# $1: the base name for the generated DER certificate.
# $2: the path to the certificate in PEM format that should be converted to DER.
#
# Result: creates the file `$1.$DER_EXT` in the working directory.
create_der_from_pem_cert() {
  local cert_name=$1
  local pem_cert_path=$2

  # create a DER version of PEM certificate
  log -n "Creating DER certificate from PEM..."
  set +e
  output=$(
    openssl \
      x509 \
      -outform der \
      -in "$pem_cert_path" \
      -out "$cert_name.$DER_EXT" \
      2>&1)
  check_exit_code "failed to create certificate for private key" "$output"
  set -e
  log_done
}

# Creates a PKCS12 trust store with the provided PEM certificate.
# The created store will not have any password.
#
# $1: the base name for the generated store. Also used as the alias for the entry in the store.
# $2: path to the certificate file in PEM format.
#
# Result: creates the file `$1.$P12_EXT` in the working directory.
create_p12_trust_store_from_pem() {
  local store_name=$1
  local cert_path=$2
  local output

  # convert PEM to PKCS12 trust store
  log -n "Creating PKCS12 trust store from PEM certificate..."
  set +e
  output=$(
    openssl \
      pkcs12 \
      -export \
      -nokeys \
      -in "$cert_path" \
      -name "$store_name" \
      -out "$store_name.$P12_EXT" \
      -passout pass: \
      2>&1)
  check_exit_code "failed to create PKCS12 trust store from PEM certificate" "$output"
  set -e
  log_done
}

# Creates a PKCS12 key store with the provided PEM key and certificate.
#
# $1: the base name for the generated store. Also used as the alias for the entry in the store.
# $2: password that is used for the generated store.
# $3: path to the certificate file in PEM format.
# $4: path to the private key in PEM format.
#
# Result: creates the file `$1.$P12_EXT` in the working directory.
create_p12_key_store_from_pem() {
  local store_name=$1
  local store_pass=$2
  local cert_path=$3
  local key_path=$4
  local output

  # convert PEM to password-protected PKCS12 file. $store_name is used as
  # the alias for the certificate and key entry.
  log -n "Creating PKCS12 key store from PEM files..."
  set +e
  output=$(
    openssl \
      pkcs12 \
      -export \
      -in "$cert_path" \
      -inkey "$key_path" \
      -name "$store_name" \
      -password pass:"$store_pass" \
      -out "$store_name.$P12_EXT" \
      2>&1)
  check_exit_code "failed to create PKCS12 key store from PEM files" "$output"
  set -e
  log_done
}

# Creates a JKS key store using the specified entry in the provided PKCS12 store.
# The entry in the JKS will be a PrivateKeyEntry (as opposed to a TrustedCertEntry).
#
# $1: base name for the generated store. Also used as the alias for the entry in the new store.
# $2: password for the generated store. Also used as the password for the entry in the new store.
# $3: path to the PKCS12 store that should be converted into the JKS store.
# $4: name of the entry in the PKCS12 store to import.
# $5: password for the PKCS12 store.
#
# Result: creates the file `$1.$JKS_EXT` in the working directory.
create_jks_key_store_from_p12() {
  local output_store_name=$1
  local output_store_pass=$2
  local input_store_path=$3
  local input_entry_name=$4
  local input_store_pass=$5
  local output

  # convert PKCS12 to JKS. Provided password is used as the password
  # for both the keystore and the private key.
  log -n "Creating JKS key store from PKCS12 store..."
  set +e
  output=$(
    keytool \
      -importkeystore \
      -noprompt \
      -srckeystore "$input_store_path" \
      -srcstoretype PKCS12 \
      -srcstorepass "$input_store_pass" \
      -srcalias "$input_entry_name" \
      -destkeystore "$output_store_name.$JKS_EXT" \
      -deststoretype JKS \
      -deststorepass "$output_store_pass" \
      -destalias "$output_store_name" \
      -destkeypass "$output_store_pass" \
      2>&1)
  check_exit_code "Failed to create JKS key store from PKCS12 store" "$output"
  set -e
  log_done
}

# Creates a JKS key store using the specified entry in the provided PKCS12 store.
# The entry in the JKS will be a PrivateKeyEntry (as opposed to a TrustedCertEntry).
#
# $1: base name for the generated store. Also used as the alias for the entry in the new store.
# $2: password for the generated store. Even though the password will not be required to read the
#       certificate entry added to the store, it is required by the JKS format.
# $3: path to the certificate to add to the trust store in PEM format.
#
# Result: creates the file `$1.$JKS_EXT` in the working directory.
create_jks_trust_store_from_pem() {
  local output_store_name=$1
  local output_store_pass=$2
  local cert_path=$3
  local output

  log -n "Creating JKS trust store from PEM certificate..."
  set +e
  output=$(
    keytool \
      -import \
      -noprompt \
      -file "$cert_path" \
      -alias "$cert_path" \
      -keystore "$output_store_name.$JKS_EXT" \
      -storepass "$output_store_pass" \
      2>&1)
  check_exit_code "Failed to create JKS trust store from PEM certificate" "$output"
  set -e
  log_done
}

# Creates a JKS key store that is a combination of the specified JKS key stores.
# The entries in the JKS will be PrivateKeyEntry objects (as opposed to TrustedCertEntry objects).
#
# $1: the base name for the generated store.
# $2: password that is used for the generated store. Also used as the password for all entries.
# $3: path to the JKS file to be imported.
# $4: alias of the entry in the JKS file to import.
# $5: password for the JKS file to import. Must also be the key for the alias to import.
# $6+: additional JKS files can be imported by providing $3-$5 for the additional stores.
#      Each additional JKS file to import must provide all 3 arguments.
combine_jks_key_stores() {
  local combined_name=$1
  local combined_pass=$2

  local all_args=("$@")
  local input_args=("${all_args[@]:2}")

  local output

  if [[ ${#input_args[@]}%3 == 0 || $(( ${#input_args[@]}%3 )) != 0 ]]; then
    echo "Incorrect number of arguments for combining JKS key stores."
    echo "JKS arguments must be multiple of 3, but was ${#input_args[@]}."
    echo "Arguments: ${input_args[@]}"
    exit 1
  fi

  local num_input_stores=$(( ${#input_args[@]}/3 ))
  local curr_input_store=1

  log "Creating JKS key store by combining $num_input_stores other JKS key stores..."
  increment_log_indent

  local counter=0
  while [ $counter -lt "${#input_args[@]}" ]; do
    log -n "Importing JKS key store $curr_input_store/$num_input_stores..."
    set +e
    output=$(
      keytool \
        -noprompt \
        -importkeystore \
        -srckeystore "${input_args[$counter]}" \
        -srcalias "${input_args[$(( counter+1 ))]}" \
        -srcstorepass "${input_args[$(( counter+2 ))]}" \
        -destkeystore "$combined_name.$JKS_EXT" \
        -deststorepass "$combined_pass" \
        -destkeypass "$combined_pass" \
        2>&1)
    check_exit_code "Failed to import JKS key store $curr_input_store/$num_input_stores" "$output"
    set -e
    log_done

    curr_input_store=$(( curr_input_store+1 ))
    counter=$(( counter+3 ))
  done

  decrement_log_indent
  log_done
}

# Echoes the OpenSSL configuration for non-CA certificates with the argument values used
# as the subject alternate names for the V3 certificate extension.
#
# $@: the subject alternate names that should be used. If empty, the '[ alt_names ]'
#       section will be empty. If specified, each entry will be added under '[ alt_names ]'
#       as the value of 'DNS.# = $#'. For example, if called with two arguments that are
#       'localhost' and 'root:root', the section will contain the following entries separated
#       by newlines: 'DNS.1 = localhost', 'DNS.2 = root:root'.
#
# Result: Echoes the full SSL configuration for non-CA certificates with the provided subject alternate names.
get_openssl_conf_with_subject_alternative_names() {
  local alt_names=
  if [ "$#" -ne 0 ]; then
    alt_names=("$@")
  fi
  local curr_conf="$OPENSSL_CERT_CONF"

  local curr_name_index=1
  for curr_name in "${alt_names[@]}"; do
      curr_conf="$curr_conf"$'\n'$"DNS.$curr_name_index = $curr_name"
      curr_name_index=$(($curr_name_index+1))
  done

  echo "$curr_conf"
}

# Writes the provided OpenSSL configuration to a temporary file and echoes
# the location of the file.
#
# $1: the content of the configuration file to write.
#
# Result: Writes the provided SSL configuration to a file in the cert_functions temporary directory
#         and echoes the path to the written file.
write_conf_file() {
  local conf_content=$1

  # write configuration to temp location

  local openssl_conf_file=`mktemp $TEMP_DIR/openssl.conf.XXXXXX`
  echo "$conf_content" > "$openssl_conf_file"

  echo "$openssl_conf_file"
}
