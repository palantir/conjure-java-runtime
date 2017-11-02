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
- [http-remoting-api](https://github.com/palantir/http-remoting-api): API classes for service configuration, tracing, and error propagation

# Usage

Maven artifacts are published to JCenter. Example Gradle dependency configuration:

```groovy
repositories {
  jcenter()
}

dependencies {
  compile "com.palantir.remoting3:jaxrs-clients:$version"
  compile "com.palantir.remoting3:retrofit2-clients:$version"
  compile "com.palantir.remoting3:jersey-servers:$version"
  // optional support for PEM key store type using Bouncy Castle libraries:
  //     compile "com.palantir.remoting3:pkcs1-reader-bouncy-castle:$version"
  // optional support for PEM key store type using Sun libraries:
  //     compile "com.palantir.remoting3:pkcs1-reader-sun:$version"
}
```


## jaxrs-clients
Provides the `JaxRsClient` factory for creating Feign-based clients for JAX-RS APIs. SSL configuration is mandatory for
all clients, plain-text HTTP is not supported. Example:

```java
SslConfiguration sslConfig = SslConfiguration.of(Paths.get("path/to/trustStore""));
ClientConfiguration config = ClientConfigurations.of(
        ImmutableList.copyOf("https://url-to-server:6789"),
        SslSocketFactories.createSslSocketFactory(sslConfig),
        SslSocketFactories.createX509TrustManager(sslConfig));
MyService service = JaxRsClient.create(MyService.class, "my user agent", config);
```

The `JaxRsClient#create` factory comes in two flavours: one for creating immutable clients given a fixed
`ClientConfiguration`, and one for creating mutable clients whose configuration (e.g., server URLs, timeouts, SSL
configuration, etc.) changes when the underlying `ClientConfiguration` changes.

## retrofit2-clients
Similar to `jaxrs-clients`, but generates clients using the Retrofit library. Example:

```java
ClientConfiguration config = ... as above... ;
MyService service = Retrofit2Client.create(MyService.class, "my user agent", config);
```

## jersey-servers
Provides Dropwizard/Jersey exception mappers for translating common JAX-RS exceptions to appropriate HTTP error codes. A
Dropwizard server is configured for http-remoting as follows:

```java
public class MyServer extends Application<Configuration> {
    @Override
    public final void run(Configuration config, final Environment env) throws Exception {
        env.jersey().register(HttpRemotingJerseyFeature.INSTANCE);
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
  compile "com.palantir.remoting3:tracing:$version"
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


## service-config (http-remoting-api)
Provides utilities for setting up service clients from file-based configuration. Example:

```yaml
# config.yml
services:
  security:
    # default truststore for all clients
    trustStorePath: path/to/trustStore.jks
  myService:  # the key used in `factory.get("myService")` below
    uris:
      - https://my-server/
    # optionally set a myService-specific truststore
    # security:
    #   trustStorePath: path/to/trustStore.jks
```

```java
ServiceConfigBlock config = readFromYaml("config.yml");
ServiceConfigurationFactory factory = ServiceConfigurationFactory.of(config);
MyService client = JaxRsClient.create(MyService.class, "my-agent", ClientConfigurations.of(factory.get("myService")));
```


## keystores and ssl-config (http-remoting-api)

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

## errors (http-remoting-api)
Provides utilities for relaying service errors across service boundaries (see below).


# API Contract

http-remoting makes the following opinionated customizations to the standard Dropwizard/Feign/Retrofit behavior.

#### Object serialization/deserialization

All parameters and return values of `application/json` endpoints are serialized/deserialized to/from JSON using a
Jackson `ObjectMapper` with `GuavaModule`, `ShimJdk7Module` (same as Jackson’s `Jdk7Module`, but avoids Jackson 2.6
requirement) and `Jdk8Module`. Servers must not expose parameters or return values that cannot be handled by this object
mapper.


#### Error propagation
Servers should use the `ServiceException` class to propagate application-specific errors to its callers. The
`ServiceException` class exposes standard error codes that clients can handle in a well-defined manner; further,
ServiceException implements [SafeLoggable](https://github.com/palantir/safe-logging) and thus allows logging
infrastructure to handle "unsafe" and "safe" exception parameters appropriately. Typically, services define its error
types as follows:

```
class Errors {
  private static final ErrorType DATASET_NOT_FOUND =
    ErrorType.create(ErrorType.Code.INVALID_ARGUMENT, "MyApplication:DatasetNotFound");

  static ServiceException datasetNotFound(DatasetId datasetId, String userName) {
    // Note that only SafeArg parameters are sent to the caller in the resulting SerializableError.
    return new ServiceException(
            DATASET_NOT_FOUND, SafeArg.of("datasetId", datasetId), UnsafeArg.of("userName", userName));
  }
}

void someMethod(String datasetId, String userName) {
  if (!exists(datasetId)) {
    throw Errors.datasetNotFound(datasetId, userName);
  }
}
```

The `HttpRemotingJerseyFeature` installs an exception mapper for `ServiceException`. The exception mapper sets the
response media type to `application/json` and returns as response body a JSON representation of a `SerializableError`
capturing the error code, error name, and error parameters. The resulting JSON response is:

```json
{
  "errorCode": "INVALID_ARGUMENT",
  "errorName": "MyApplication:DatasetNotFound",
  "errorInstanceId": "xxxxxxxx-xxxx-Mxxx-Nxxx-xxxxxxxxxxxx",
  "parameters": {
    "datasetId": "123abc",
  }
}
```

Both JaxRsClient and Retrofit2Client intercept non-successful HTTP responses and throw a `RemoteException` wrapping the
deserialized server-side `SerializableError`. The error codes and names of the `ServiceException` and
`SerializableError` are defined by the service API, and clients should handle errors based on the error code and name:

```
try {
    service.someMethod();
catch (RemoteException e) {
    if (e.getError().errorName().equals("MyApplication:DatasetNotFound")) {
        handleError(e.getError().parameters().get("datasetId"));
    } else {
        throw new RuntimeException("Failed to call someMethod()", e);
    }
}
```

Frontends receiving such errors should use a combination of error code, error name, and parameters to display localized,
user friendly error information. For example, the above error could be surfaced as *"The requested dataset with id
123abc could not be found"*.

To support **legacy server implementations**, the `HttpRemotingJerseyFeature` also installs exception mappers for
`IllegalArgumentException`, `NoContentException`, `RuntimeException` and `WebApplicationException`. The exceptions
typically yield `SerializableError`s with `exceptionClass=errorCode=<exception classname>` and
`message=errorName=<exception message>`. Clients should refrain from displaying the `message` or `errorName` fields to
user directly. Services should prefer to throw `ServiceException`s instead of the above, since they are easier to
consume for clients and support transmitting exception parameters in a safe way.


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

#### Quality of service: retry, failover, throttling

http-remoting servers can use the `QosException` class to advertise the following conditions:

* `throttle`: Returns a `Throttle` exception indicating that the calling
  client should throttle its requests.  The client may retry against an arbitrary node of this service.
* `retryOther`: Returns a `RetryOther` exception indicating that the calling client should retry against the
  given node of this service.
* `unavailable`: An exception indicating that (this node of) this service is currently unavailable and the client
  may try again at a later time, possibly against a different node of this service.

The `QosExceptions` have a stable mapping to HTTP status codes and response headers:
* `throttle`: 429 Too Many Requests, plus optional `Retry-After` header
* `retryOther`: 308 Permanent Redirect, plus `Location` header indicating the target host
* `unavailable`: 503 Unavailable

http-remoting clients (both Retrofit2 and JaxRs) handle the above error codes and take the appropriate action:
* `throttle`: reschedule the request with a delay: either the indicated `Retry-After` period, or a configured
  exponential backoff
* `retryOther`: retry the request against the indicated service node; all request parameters and headers are maintained
* `unavailable`: retry against a different node after a configurable exponential delay, as described below.

Additionally, connection errors (e.g., `connection refused` or DNS errors) yield a retry against a different node of the
service. Retries pick a target host by cycling through the list of URLs configured for a Service (see
`ClientConfiguration#uris`). Note that the "current" URL is maintained across calls; for example, if a first call yields
a `retryOther`/308 redirect, then any subsequent calls will be made against that URL. Similarly, if the first URL yields
a DNS error and the retried call succeeds against the URL from the list, then subsequent calls are made aginst that URL.

The number of retries for `503` and connection errors can be configured via `ClientConfiguration#maxNumRetries` or
`ServiceConfiguration#maxNumRetries`, defaulting to the number of URIs provided in `#uris`.

# License
This repository is made available under the [Apache 2.0 License](http://www.apache.org/licenses/LICENSE-2.0).
