![Logo](https://s3-eu-west-1.amazonaws.com/org.paraio/para.png)
============================

> ### Para - a general-purpose back-end framework for the cloud.

## What is this?

**Para** was designed as a simple and modular back-end framework for object persistence and retrieval.
It enables your application to store objects directly to a data store (NoSQL) or any relational database (RDBMS)
and it also automatically indexes those objects and makes them searchable.

Para can be used in two ways - as the backbone of your JVM-based application or as a standalone server supporting
multiple applications and clients written in any programming language.

### Features

- Standalone executable JAR with embedded Jetty (WAR also available).
- RESTful JSON API secured with Amazon's Signature 4 algorithm.
- Simple annotation-based Object Grid Mapper (OGM) for mapping objects to a datastore.
- Support for scalable data stores (Amazon DynamoDB, Cassandra).
- Full text search (current implementation is based on ElasticSearch).
- Distributed object cache support (implemented with Hazelcast).
- Strong and flexible security based on Spring Security (OpenID and Facebook integration, CSRF protection, etc.).
- Internationalization utilities for translating language packs into different languages.
- Modular design based on Google Guice.

### Architecture

```
+----------------------------------------------------------+
|                                                          |
|               Java Domain Objects (POJOs)                |
|                                                          |
+----------------------------------------------------------+
+----------------------------------------------------------+
|                  ____  ___ _ ____ ___ _                  |
|                 / __ \/ __` / ___/ __` /                 |
|                / /_/ / /_/ / /  / /_/ /                  |
|               / .___/\__,_/_/   \__,_/                   |
|              /_/                           +-------------+
|                                            | Persistence |
+--------+  +-------+  +-----------------+   +-------------+
|REST API|  | Utils |  |     Search      |   |    Cache    |
+----+---+--+-------+--+--------+--------+---+------+------+
     |                          |                   |
     |                          |                   |
+----+--------------+  +--------+--------+   +------+------+
|  Spring Security  |  |                 |   |             |
|                   |  |  ElasticSearch  +---+ Data Store  |
|  Signed Requests  |  |                 |   |             |
+----+---------^----+  +-----------------+   +-------------+
     |         |
+----v---------+-------------------------------------------+
|           Clients : Node.js, Ruby, Python etc.           |
+----------------------------------------------------------+
```

## Getting started

### Building Para

Para uses Maven. Here's how you clone it and build it:

```sh
git clone https://github.com/erudika/para.git para
cd para
mvn install -DskipTests=true
```

You can create a "fat" executable JAR file like this:

```sh
mvn clean package -f para -DskipTests=true -DfatJAR=true
```

### Standalone JAR & WAR

You can run Para as a standalone server by downloading the "fat" JAR and then:

```sh
java -jar para-X.Y.Z.jar
```

Alternatively, you can grab the WAR file and deploy it to your favorite servlet container.

####[Download JAR or WAR](https://github.com/Erudika/para/releases)

### Maven

You can also integrate Para with your project by adding it as a dependency in your build system.
Para is hosted on Maven Central.

Here's the Maven snippet to include in your `pom.xml`:

```xml
<dependency>
  <groupId>com.erudika</groupId>
  <artifactId>para</artifactId>
  <version>1.3.1</version>
</dependency>
```

## Documentation

####[Read the Docs](http://paraio.org/docs)
*The docs will be online soon.*

## Configuration

See the configuration file `src/main/resources/reference.conf` and modify it if needed.
Para uses [Typesafe's Config](https://github.com/typesafehub/config) library so you can easily load your config file from any URL.
See the docs for more info.

## Contributing

1. Fork this repository and clone the fork to your machine
2. Create a branch (`git checkout -b my-new-feature`)
3. Implement a new feature or fix a bug and add some tests
4. Commit your changes (`git commit -am 'Added a new feature'`)
5. Push the branch to **your fork** on GitHub (`git push origin my-new-feature`)
6. Create new Pull Request from your fork

For more information see [CONTRIBUTING.md](https://github.com/Erudika/para/blob/master/CONTRIBUTING.md)

## License
[Apache 2.0](LICENSE)