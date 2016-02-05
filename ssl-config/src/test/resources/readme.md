# Test Keys and Certificates

This directory contains certificates and keys in various formats that are used to test the package.

## Test CA

The certificate authority for this set of test certificates.

### Raw files

File | Type | Format | Password | Signed By | Common Name
---- | ---- | ------ | -------- | --------- | -----------
testCA/testCA.key | Private key | PEM | testtest | | 
testCA/testCA.pem | Certificate | PEM | | rootCA.key (self-signed) | Test CA

### Stores

File | Contents | Alias | Format | Password
---- | -------- | ----- | ------ | --------
testCA/testCATrustStore.jks | testCA.pem | testCA | PEM | testCA

## Test Server

The key and certificate for the test server. The certificate is signed by the Test CA.

### Raw files

File | Type | Format | Password | Signed By | Common Name
---- | ---- | ------ | -------- | --------- | -----------
testServer/testServer.key | Private key | PEM | | | 
testServer/testServer.crt | Certificate | PEM | | rootCA.key | Test Server

### Stores

File | Contents | Alias | Format | Password
---- | -------- | ----- | ------ | --------
testServer/testServerKeyStore.p12 | testServer.key, testServer.crt | testServer | PKCS12 | testServer
testServer/testServerKeyStore.jks | testServer.key, testServer.crt | testServer | JKS | serverStore

## Test Client

The key and certificate for the test client. The certificate is signed by the Test CA.

### Raw files

File | Type | Format | Password | Signed By | Common Name
---- | ---- | ------ | -------- | --------- | -----------
testClient/testClient.key | Private key | PEM | | | 
testClient/testClient.pem | Certificate | PEM | | rootCA.key | Test Client

### Stores

File | Contents | Alias | Format | Password
---- | -------- | ----- | ------ | --------
testClient/testClientKeyStore.p12 | testClient.key, testClient.crt | testClient | PKCS12 | testClient
testClient/testClientKeyStore.jks | testClient.key, testClient.crt | testClient | JKS | clientStore