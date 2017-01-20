[![CircleCI Build Status](https://circleci.com/gh/palantir/http-remoting/tree/develop.svg?style=shield)](https://circleci.com/gh/palantir/http-remoting)
[![Download](https://api.bintray.com/packages/palantir/releases/http-remoting/images/download.svg) ](https://bintray.com/palantir/releases/http-remoting/_latestVersion)

# HTTP Remoting Utilities
This repository provides an opinionated set of libraries for defining and creating RESTish/RPC servers and clients based
on [Feign](https://github.com/OpenFeign/feign) or [Retrofit](http://square.github.io/retrofit/) as a client and
[Dropwizard](http://www.dropwizard.io/)/[Jersey](https://jersey.java.net/) with [JAX-RS](https://jax-rs-spec.java.net/)
service definitions as a server. Refer to the [API Contract](#api-contract) section for details on the contract between
clients and servers. This library requires Java 8.


Core libraries:
- jaxrs-clients: Clients for JAX-RS-defined service interfaces
- retrofit2-clients: Clients for Retrofit-defined service interfaces
- jersey-servers: Configuration library for Dropwizard/Jersey servers

# Usage

Maven artifacts are published to JCenter. Example Gradle dependency configuration:

```groovy
repositories {
  jcenter()
}

dependencies {
  compile "com.palantir.remoting1:jaxrs-clients:$version"
  compile "com.palantir.remoting1:retrofit2-clients:$version"
  compile "com.palantir.remoting1:jersey-servers:$version"
  // optional support for PEM key store type using Bouncy Castle libraries:
  //     compile "com.palantir.remoting1:pkcs1-reader-bouncy-castle:$version"
  // optional support for PEM key store type using Sun libraries:
  //     compile "com.palantir.remoting1:pkcs1-reader-sun:$version"
}
```


## jaxrs-clients
Provides the `JaxRsClient` factory for creating clients for JAX-RS services. Example:
```java
MyService service = JaxRsClient.builder()
    .build(MyService.class, "my user agent", "https://my-server/");
```
The client is implemented using Feign; however, the Feign dependency is hidden away from both the Java API and the
classpath (via shadowing).

## retrofit2-clients
Similar to `jaxrs-clients`, but generates clients using the Retrofit library. Example:

```java
MyService service = Retrofit2Client.builder()
    .build(MyService.class, "my user agent", "https://my-server/");
```

## jersey-servers
Provides Dropwizard/Jersey exception mappers for translating common JAX-RS exceptions to appropriate HTTP error codes. A
Dropwizard server is configured for http-remoting as follows:

```java
public class MyServer extends Application<Configuration> {
    @Override
    public final void run(Configuration config, final Environment env) throws Exception {
        env.jersey().register(HttpRemotingJerseyFeature.DEFAULT);
        env.jersey().register(new MyResource());
    }
}
```

## tracing
Provides [Zipkin](https://github.com/openzipkin/zipkin)-style call tracing libraries. All `JaxRsClient` and
`Retrofit2Client` instances are instrumented by default. Jersey server instrumentation is enabled via the
`HttpRemotingJerseyFeature` (see above).

By default, the instrumentation forwards trace and span information through HTTP headers, but does not emit completed
spans to a log file or to Zipkin.  Span observers are static (similar to SLF4J appenders) and can be configured as
follows:

```java
// Emit all completed spans to a default SLF4J logger:
Tracer.subscribe("SLF4J" /* user-defined name */, AsyncSlf4jSpanObserver.of(executor));

// No longer emit span events to SLF4J:
Tracer.unsubscribe("SLF4J");
```
Note that span observers are static; a server typically subscribes span observers in its initialization phase.
Libraries should never register span observers (since they can trample observers registered by consumers of the library
whose themselves register observers).

In addition to cross-service call tracing, the `Tracer` library supports intra-thread tracing, for example:
```java
// Record tracing information for expensive doSomeComputation() call:
try {
    Tracer.startSpan("doSomeComputation");
    doSomeComputation();  // may itself invoke cross-service or local traced calls
} finally {
    Tracer.completeSpan(); // triggers all span observers
}
```

The `tracing` library can be used independently of `jaxrs-clients` or `retrofit2-clients`:

```groovy
// build.gradle
dependencies {
  compile "com.palantir.remoting1:tracing:$version"
}
```
```java
Tracer.subscribe("SLF4J", AsyncSlf4jSpanObserver.of(executor));
try {
    Tracer.startSpan("doSomeComputation");
    doSomeComputation();
} finally {
    Tracer.completeSpan();
}

```


## service-config
Provides utilities for setting up service clients from file-based configuration. Example:

```yaml
# config.yml
services:
  myService:  # the key used in `config.getServices().get("myService")` below
    uris:
      - https://my-server/
    security:
      trustStorePath: path/to/trustStore.jks
```

```java
ServiceDiscoveryConfiguration config = readFromYaml("path/to/config.yml");
MyService service = JaxRsClient.create(
    MyService.class, "my user agent", config.getServices().get("myService"));
```


## ssl-config

Provides utilities for interacting with Java trust stores and key stores and acquiring `SSLSocketFactory` instances
using those stores, as well as a configuration class for use in server configuration files.

The `SslConfiguration` class specifies the configuration that should be used for a particular `SSLContext`. The
configuration is required to include information for creating a trust store and can optionally be provided with
information for creating a key store (for client authentication).

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

#### Store Types

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
* `Puppet`: a directory whose content conforms to the
  [Puppet SSLdir](https://docs.puppet.com/puppet/latest/reference/dirs_ssldir.html) format. For trust stores, the
  certificates in the `certs` directory are added to the trust store.  For key stores, the PEM files in the
  `private_keys` directory are added as the private keys and the corresponding files in `certs` are used as the trust
  chain for the key.

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

## error-handling
Provides utilities for relaying Java exceptions across JVM boundaries by serializing exceptions as JSON POJOs.


# API Contract

http-remoting makes the following opinionated customizations to the standard Dropwizard/Feign/Retrofit behavior.

#### Object serialization/deserialization

All parameters and return values of `application/json` endpoints are serialized/deserialized to/from JSON using a
Jackson `ObjectMapper` with `GuavaModule` and `Jdk7Module`. Servers must not expose parameters or return values that
cannot be handled by this object mapper.

#### Error propagation
The `HttpRemotingJerseyFeature` routine installs exception mappers for `IllegalArgumentException`,
`NoContentException`, `RuntimeException` and `WebApplicationException`. The exception mapper sets the response media
type to `application/json` and returns as response body a JSON representation of a `SerializableError` capturing the
message, exception name, and optionally stacktrace of the exception. Both JaxRsClient and Retrofit2Client intercept
non-successful HTTP responses and throw a `RemoteException` wrapping the deserialized server-side `SerializableError`.
The error name of the `RemoteException` is defined by the service API and clients should switch&dispatch based on the
error name. The `SerializableError` format is:

```json
{
  "message": "A string explaning the error",
  "exceptionClass": "applicationSpecificErrorName",
  "stackTrace": [ {"methodName":"...","fileName":"...","lineNumber":...,"className":"...","nativeMethod":false, {...} ]
}
```

Note that the JSON field `exceptionClass` carries this name for historic and back-compatibility reasons and will be
changed to `errorName` in a future version of this library. The optional `stackTrace` field contains a list of
serialized Java `StackTraceElement` objects indicating the server-side stack trace at the time of the exception. A
future version of http-remoting may replace the stack trace mechanism with a more OS-independent API for relaying stack
traces.

#### Serialization of Optional and Nullable objects
`@Nullable` or `Optional<?>` fields in complex types are serialized using the standard Jackson mechanism:
- a present value is serialized as itself (in particular, without being wrapped in a JSON object representing the `Optional` object)
- an absent value is serialized as a JSON `null`.
For example, assume a Java type of the form
```java
public final class ComplexType {
    private final Optional<ComplexType> nested;
    private final Optional<String> string;
}
```
, and an instance
```java
ComplexType value = new ComplexType(
        Optional.of(
                new ComplexType(
                        Optional.<ComplexType>absent(),
                        Optional.<String>absent(),
        Optional.of("baz"));
```
The JSON-serialized representation of this object is:
```json
{"nested":{"nested":null,"string":null},"string":"baz"}
```

#### Optional return values
When a call to a service interface declaring an `Optional<T>` return value with media type `application/json` yields:
- a `Optional#empty` return value, then the HTTP response has error code 204 and an empty response body.
- a non-empty return value, then the HTTP response has error code 200 and the body carries the deserialized `T` object
  directly, rather than a deserialized `Optional<T>` object.

JaxRsClients intercept such responses, deserialize the `T`-typed return value and return it to the caller wrapped as an
`Optional<T>`. No is no equivalent concept for Retrofit2Clients.

#### Call tracing

Clients and servers propagate call trace ids across JVM boundaries according to the
[Zipkin](https://github.com/openzipkin/zipkin) specification. In particular, clients insert `X-B3-TraceId: <Trace ID>`
HTTP headers into all requests which get propagated by Jetty servers into subsequent client invocations.

#### Endpoints returning plain strings

Endpoints returning plain strings should produce media type `text/plain`. Return type `Optional<String>` is only
supported for media type `application/json`.

#### Failover
JaxRsClients and Retrofit2Clients can be configured to *retry* requests in case of failure. Note that JaxRsClients only
retry connection-level errors; HTTP responses carrying a `RemoteException` do typically indicate a permanent error and
do not trigger a retry.


# License
This repository is made available under the [Apache 2.0 License](http://www.apache.org/licenses/LICENSE-2.0).
