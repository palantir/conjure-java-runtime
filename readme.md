HTTP Remoting Utils
===================
This repository holds a small collection of useful utilties for use with HTTP Remoting setups,
in particular those that use Feign as a client and Jersey as a server.

remote-clients
--------------
Provides utilities for creating Feign clients in commonly used configurations. Additionally,
offers a basic round-robin failover client configuration for basic failover between multiple
endpoints.

trust-stores
------------
Provides utiltiies for interacting with Java trust stores and acquiring `SSLSocketFactory`
instances using those trust stores, as well as a configuration class for use in server
configuration files.

License
-------
This repository is made available under the [Apache 2.0 License](http://www.apache.org/licenses/LICENSE-2.0).
