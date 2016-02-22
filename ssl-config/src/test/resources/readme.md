# Test Keys and Certificates

This directory contains certificates and keys in various formats that are used to test the package.

The script `certSetup.sh` can be run to generate all of the files listed below.

## Test CA

The certificate authority for this set of test certificates.

### Raw files

File              | Type        | Format | Signed By                | Common Name
----              | ----        | ------ | -----------              |
testCA/testCA.key | Private key | PEM    |                          |
testCA/testCA.crt | Certificate | PEM    | rootCA.key (self-signed) | Test CA

### Stores

File                        | Contents   | Alias  | Format | Password
----                        | --------   | -----  | ------ | --------
testCA/testCATrustStore.p12 | testCA.crt | testCA | PEM    |
testCA/testCATrustStore.jks | testCA.crt | testCA | PEM    | changeit

## Test Server

The key and certificate for the test server. The certificate is signed by the Test CA.

### Raw files

File                      | Type        | Format | Password | Signed By  | Common Name
----                      | ----        | ------ | -------- | ---------  | -----------
testServer/testServer.key | Private key | PEM    |          |            |
testServer/testServer.crt | Certificate | PEM    |          | rootCA.key | localhost

### Stores

File                              | Contents                       | Alias      | Format | Password
----                              | --------                       | -----      | ------ | --------
testServer/testServerKeyStore.p12 | testServer.key, testServer.crt | testServer | PKCS12 | testServer
testServer/testServerKeyStore.jks | testServer.key, testServer.crt | testServer | JKS    | serverStore

## Test Client

The key and certificate for the test client. The certificate is signed by the Test CA.

### Raw files

File                      | Type        | Format | Password | Signed By  | Common Name
----                      | ----        | ------ | -------- | ---------  | -----------
testClient/testClient.key | Private key | PEM    |          |            |
testClient/testClient.crt | Certificate | PEM    |          | rootCA.key | Test Client

### Stores

File                              | Contents                       | Alias      | Format | Password
----                              | --------                       | -----      | ------ | --------
testClient/testClientKeyStore.p12 | testClient.key, testClient.crt | testClient | PKCS12 | testClient
testClient/testClientKeyStore.jks | testClient.key, testClient.crt | testClient | JKS    | clientStore

## Multiple

`multiple.jks` is a key store in JKS format that stores multiple key/certificate pairs. The password for the key store (and the keys within it) is `multiple`.

Alias      | Contents
-----      | --------
testClient | testClient.key, testClient.crt
testServer | testServer.key, testServer.crt

## CRL

`crl.pem` is a certificate revocation list (CRL) for the Test CA that is empty.