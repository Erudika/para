![Logo](https://s3-eu-west-1.amazonaws.com/org.paraio/para.png)
============================

> ### A general-purpose back-end framework for the cloud.

[![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.erudika/para-core/badge.svg)](https://maven-badges.herokuapp.com/maven-central/com.erudika/para-core)
[![Join the chat at https://gitter.im/Erudika/para](https://badges.gitter.im/Erudika/para.svg)](https://gitter.im/Erudika/para?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)

## What is this?

**Para** was designed as a simple and modular back-end framework for object persistence and retrieval.
It enables your application to store objects directly to a data store (NoSQL) or any relational database (RDBMS)
and it also automatically indexes those objects and makes them searchable.

The name "Para" means "steam" in Bulgarian. And just like steam is used to power stuff, you can use
Para to power your mobile or web application back-end.

Para can be used in two ways - as the backbone of your JVM-based application or as a standalone server supporting
multiple applications and clients written in any programming language.

See how Para [compares to other open source back-end frameworks](http://www.erudika.com/blog/2015/10/21/backend-frameworks-usergrid-loopback-para-baasbox-deployd-telepat/).

### Features

- Standalone executable WAR with embedded Jetty
- RESTful JSON API for your objects secured with Amazon's Signature 4 algorithm
- Full text search (Elasticsearch)
- Distributed object cache (Hazelcast)
- Multi-app support, each with its own table, index and cache
- Flexible security based on Spring Security (Social login, JWT support, CSRF protection, etc.)
- Simple but effective resource permissions for client access control
- Robust constraint validation mechanism based on JSR-303 and Hibernate Validator
- Support for scalable data stores (Amazon DynamoDB, Apache Cassandra, MongoDB)
- Modular design powered by Google Guice
- I18n utilities for translating language packs into different languages

### Architecture

<pre>
+----------------------------------------------------------+
|                  ____  ___ _ ____ ___ _                  |
|                 / __ \/ __` / ___/ __` /                 |
|                / /_/ / /_/ / /  / /_/ /                  |
|               / .___/\__,_/_/   \__,_/                   |
|              /_/                           +-------------+
|                                            | Persistence |
+-------------------+  +-----------------+   +-------------+
|      REST API     |  |     Search      |   |    Cache    |
+---------+---------+--+--------+--------+---+------+------+
          |                     |                   |
          |                     |                   |
+---------+---------+  +--------+--------+   +------+------+
|  Security and     |  |                 |   |             |
|  Validation of    |  |  ElasticSearch  +---+ Data Store  |
|  Signed Requests  |  |                 |   |             |
+----+---------^----+  +-----------------+   +-------------+
     |         |
+----v---------+-------------------------------------------+
|        Clients : JavaScript, PHP, Java, C#, etc.         |
+----------------------------------------------------------+
</pre>

## Documentation

### [Read the Docs](http://paraio.org/docs)

## Getting started

To get started quickly just [grab the WAR file](https://github.com/Erudika/para/releases) and execute it with:

1. [Download the executable WAR](https://github.com/Erudika/para/releases)
2. Execute it with `java -jar para-X.Y.Z.war`
3. Call `curl localhost:8080/v1/_setup` to get the access and secret keys (give it a few seconds to initialize)
4. Start using the API directly or using the provided `ParaClient` class.

### Building Para

Para can be compiled with JDK 1.6 and up, but we recommend running it on Java 1.8+.

Para uses Maven. Here's how you clone it and build it:

```sh
git clone https://github.com/erudika/para.git para
cd para
mvn install -DskipTests=true
```


### Standalone - executable WAR

You can run Para as a standalone server by downloading the executable WAR and then:

```sh
java -jar para-X.Y.Z.war
```

Alternatively, you can grab the WAR file and deploy it to your favorite servlet container.

### [Download WAR](https://github.com/Erudika/para/releases)

### Maven

You can also integrate Para with your project by adding it as a dependency in your build system.
Para is hosted on Maven Central.

Here's the Maven snippet to include in your `pom.xml`:

```xml
<dependency>
  <groupId>com.erudika</groupId>
  <artifactId>para-server</artifactId>
  <version>1.18.0</version>
</dependency>
```

## API clients

API clients help you quickly integrate your project with Para and we're hoping to write more
clients for popular languages soon.

- **PHP** [para-client-php](https://github.com/erudika/para-client-php)
- **JavaScript / Node.js** [para-client-js](https://github.com/erudika/para-client-js)
- **Java** [para-client](https://github.com/erudika/para/tree/master/para-client)
- **.NET** [para-client-csharp](https://github.com/erudika/para-client-csharp)
- **Android** [para-client-android](https://github.com/erudika/para-client-android)

## Wishlist / Roadmap

- `DAO` implementations for popular databases like MongoDB, PostgreSQL, Cassandra, etc.
- API clients in Python, iOS.
- Integrations with Google App Engine, Heroku, Docker, Vagrant
- GraphQL support

## Contributing

1. Fork this repository and clone the fork to your machine
2. Create a branch (`git checkout -b my-new-feature`)
3. Implement a new feature or fix a bug and add some tests
4. Commit your changes (`git commit -am 'Added a new feature'`)
5. Push the branch to **your fork** on GitHub (`git push origin my-new-feature`)
6. Create new Pull Request from your fork

Please try to respect the code style of this project. To check your code, run it through the style checker:

```sh
mvn validate
```

For more information see [CONTRIBUTING.md](https://github.com/Erudika/para/blob/master/CONTRIBUTING.md)

## License
[Apache 2.0](LICENSE)
