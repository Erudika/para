![Logo](https://s3-eu-west-1.amazonaws.com/org.paraio/para.png)
============================

> ### A general-purpose back-end framework for the cloud.

## What is this?

**Para** was designed as a simple and modular back-end framework for object persistence and retrieval.
It enables your application to store objects directly to a data store (NoSQL) or any relational database (RDBMS)
and it also automatically indexes those objects and makes them searchable.

The name "Para" means "steam" in Bulgarian. And just like steam is used to power stuff, you can use
Para to power your mobile or web application back-end.

Para can be used in two ways - as the backbone of your JVM-based application or as a standalone server supporting
multiple applications and clients written in any programming language.

Para is similar to [Dropwizard](https://dropwizard.github.io/dropwizard/) and [Deployd](http://www.deployd.com/).

### Features

- Standalone executable JAR with embedded Jetty (WAR also available).
- RESTful JSON API for your objects secured with Amazon's Signature 4 algorithm.
- Full text search (current implementation is based on ElasticSearch).
- Distributed object cache support (implemented with Hazelcast).
- Flexible security based on Spring Security (OpenID and Facebook login, CSRF protection, etc.).
- Support for scalable data stores (Amazon DynamoDB, Cassandra).
- Modular design based on Google Guice.
- I18n utilities for translating language packs into different languages.

### Architecture

<pre>
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
|        Clients : JavaScript, Ruby, Python etc.           |
+----------------------------------------------------------+
</pre>

## Documentation

###[Read the Docs](http://paraio.org/docs)

## Getting started

To get started quickly just (grab the JAR file)[https://github.com/Erudika/para/releases] and execute it with:

```sh
java -jar para-X.Y.Z.jar
```

### Building Para

Para can be compiled with JDK 1.6 and up.

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
  <version>1.6.0</version>
</dependency>
```

## Wishlist / Future plans

- Add more implementations for popular databases like MongoDB, PostgreSQL, etc.
- Integration with JOOQ
- Integration with Comsat, Quasar
- Add API clients written in popular languages - Ruby, C#, JavaScript and why not... Swift :)
- Separate implementations from core

## Contributing

1. Fork this repository and clone the fork to your machine
2. Create a branch (`git checkout -b my-new-feature`)
3. Implement a new feature or fix a bug and add some tests
4. Commit your changes (`git commit -am 'Added a new feature'`)
5. Push the branch to **your fork** on GitHub (`git push origin my-new-feature`)
6. Create new Pull Request from your fork

Please try to respect the code style of this project. To check your code, run it through the style checker:

```sh
mvn -f para validate
```

For more information see [CONTRIBUTING.md](https://github.com/Erudika/para/blob/master/CONTRIBUTING.md)

## License
[Apache 2.0](LICENSE)