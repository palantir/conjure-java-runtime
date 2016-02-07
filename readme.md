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
--------------
Provides Feign decoders for translating HTTP error codes to appropriate JAX-RS Java exceptions,
and utilities for creating Feign clients in commonly used configurations. Additionally,
offers a basic round-robin failover client configuration for basic failover between multiple
equivalent endpoints.

ssl-config
----------
Provides utilities for interacting with Java trust stores and acquiring `SSLSocketFactory`
instances using those trust stores, as well as a configuration class for use in server
configuration files.

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


Gradle:

    repositories {
      jcenter()
    } 
    dependencies {
      compile "com.palantir.remoting:http-clients:0.1.2"
      compile "com.palantir.remoting:http-servers:0.1.2"
      compile "com.palantir.remoting:ssl-config:0.1.2"
      compile "com.palantir.remoting:error-handling:0.1.2"
    }


License
-------
This repository is made available under the [Apache 2.0 License](http://www.apache.org/licenses/LICENSE-2.0).
