[![CircleCI Build Status](https://circleci.com/gh/palantir/http-remoting/tree/develop.svg)](https://circleci.com/gh/palantir/http-remoting)
[![Download](https://api.bintray.com/packages/palantir/releases/http-remoting/images/download.svg) ](https://bintray.com/palantir/releases/http-remoting/_latestVersion)

HTTP Remoting Utilities
=======================
This repository holds a small collection of useful utilities for use with HTTP Remoting setups,
in particular those that use Feign as a client and Jersey as a server.

While allowing for further customizations, this library makes the following opinionated choices
regarding the interplay of Java and HTTP interfaces:
- Server-side Java exceptions are serialized as JSON objects. Client-side error deserializers
  rethrow the original exception if possible, or a RuntimeException else.
- `Optional<>` service interfaces give rise to a 204 HTTP response code to encode `Optional#absent`.
  Client-side, 204 response codes are translated back to `Optional#absent`.
- `String` service interfaces are supported for HTTP media type TEXT_PLAIN.

http-clients
------------
Provides Feign decoders for translating HTTP error codes to appropriate JAX-RS Java exceptions,
and utilities for creating Feign clients in commonly used configurations. Additionally,
offers a basic round-robin failover client configuration for basic failover between multiple
equivalent endpoints.

retrofit-clients
----------------
Similar to `http-clients`, but generates proxies using the [Retrofit](http://square.github.io/retrofit/)
library.

ssl-config
----------
Provides utilities for interacting with Java trust stores and key stores and acquiring
`SSLSocketFactory` instances using those stores, as well as a configuration class for
use in server configuration files.

error-handling
--------------
Provides utilities for relaying Java exceptions across JVM boundaries by serializing exceptions
as JSON POJOs.

http-servers
------------
Provides Dropwizard/Jersey exception mappers for translating common JAX-RS exceptions to
appropriate HTTP error codes.

Usage
=====

ssl-config
----------
The `SslConfiguration` class specifies the configuration that should be used for a particular
`SSLContext`. The configuration is required to include information for creating a trust store and
can optionally be provided with information for creating a key store (for client authentication).

The configuration consists of the following properties:

* `trustStorePath`: path to a file that contains the trust store information. The format of the
 file is specified by the `trustStoreType` property.
* `trustStoreType`: the type of the trust store. See section below for details. The default value
 is `JKS`.
* (optional) `keyStorePath`: path to a file that contains the key store information. If unspecified,
 no key store will be associated with this configuration.
* (optional) `keyStorePassword`: password for the key store. Will be used to read the keystore
 provided by `keyStorePath` (if relevant for the format), and will also be used as the password
 for the in-memory key store created by this configuration. Required if `keyStorePath` is specified.
* (optional) `keyStoreType`: the type of the key store. See section below for details. The default
 value is `JKS`.
* (optional) `keyStoreAlias`: specifies the alias of the key that should be read from the key store
 (relevant for file formats that contain multiple keys). If unspecified, the first key returned by
 the store is used.

An `SslConfiguration` object can be constructed using the static `of()` factory methods of the
class, or by using the `SslConfiguration.Builder` builder. `SslConfiguration` objects can be
serialized and deserialized as JSON.

Once an `SslConfiguration` object is obtained, it can be passed as an argument to the
`SslSocketFactories.createSslSocketFactory` method to create an `SSLSocketFactory` object that can
be used to configure Java SSL connections.

### Store Types

The following values are supported as store types:

* `JKS`: a trust store or key store in JKS format. When used as a trust store, the
 `TrustedCertificateEntry` entries are used as certificates. When used as a key store, the
 `PrivateKeyEntry` specified by the `keyStoreAlias` parameter (or the first such entry returned if
 the parameter is not specifeid) is used as the private key.
* `PEM`: for trust stores, an X.509 certificate file in PEM or DER format, or a directory of such
 files. For key stores, a PEM file that contains a PKCS#1 RSA private key followed by the
 certificates that form the trust chain for the key in PEM format, or a directory of such files. In
 either case, if a directory is specified, every non-hidden file in the directory must be a file of
 the specified format (they will all be read).
* `PKCS12`: a trust store or key store in PKCS12 format. Behavior is the same as for the `JKS` type,
 but operates on stores in PKCS12 format.
* `Puppet`: a directory whose content conforms to the [Puppet SSLdir](https://docs.puppet.com/puppet/latest/reference/dirs_ssldir.html)
 format. For trust stores, the certificates in the `certs` directory are added to the trust store.
 For key stores, the PEM files in the `private_keys` directory are added as the private keys and
 the corresponding files in `certs` are used as the trust chain for the key.

#### Note on the PEM Key Store Type

When `PEM` is used as the key store type, the runtime classpath must provide a `Pkcs1Reader`
implementation and it must be defined as a service in `META-INF/services`. This project provides an
implementation that uses BouncyCastle libraries and another implementation that uses Sun libraries.

The `pkcs1-reader-bouncy-castle` library includes the Bouncy Castle
`PKIX/CMS/EAC/DVCS/PKCS/TSP/OPENSSL` library as a dependency.

The `pkcs1-reader-sun` does not include any extra dependencies, but assumes the availability of the
`sun.security.utils` package. Although this is a package in the Sun namespace, it is generally
available as part of most popular JVM implementations, including the Oracle and OpenJDK JVMs for
Java 7 and Java 8.

Add Dependency Using Gradle
---------------------------

    repositories {
      jcenter()
    }
    dependencies {
      compile "com.palantir.remoting:http-clients:$version"
      compile "com.palantir.remoting:retrofit-clients:$version"
      compile "com.palantir.remoting:http-servers:$version"
      compile "com.palantir.remoting:ssl-config:$version"
      compile "com.palantir.remoting:error-handling:$version"
      // support for PEM key store type using Bouncy Castle libraries
      // compile "com.palantir.remoting:pkcs1-reader-bouncy-castle:$version"
      // support for PEM key store type using Sun libraries
      // compile "com.palantir.remoting:pkcs1-reader-sun:$version"
    }


License
-------
This repository is made available under the [Apache 2.0 License](http://www.apache.org/licenses/LICENSE-2.0).
