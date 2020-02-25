![Logo](https://s3-eu-west-1.amazonaws.com/org.paraio/para.png)

## A general-purpose backend framework for the cloud.

[![Build Status](https://travis-ci.org/Erudika/para.svg?branch=master)](https://travis-ci.org/Erudika/para)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.erudika/para-core/badge.svg)](https://maven-badges.herokuapp.com/maven-central/com.erudika/para-core)
[![Join the chat at https://gitter.im/Erudika/para](https://badges.gitter.im/Erudika/para.svg)](https://gitter.im/Erudika/para?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)


**Para** is a simple and modular backend framework for object persistence and retrieval.
It helps you build and prototype applications faster by taking care of backend operations.
It can be a part of your JVM-based application or it can be deployed as standalone, multitenant API server with
multiple applications and clients connecting to it.

The name "pára" means "steam" in Bulgarian. And just like steam is used to power stuff, you can use
Para to power your mobile or web application backend.

See how **Para** [compares to other open source backend frameworks](https://erudika.com/blog/2015/10/21/backend-frameworks-usergrid-loopback-para-baasbox-deployd-telepat/).

**This project is fully funded and supported by [Erudika](https://erudika.com) - an independent, bootstrapped company.**

### Features

- RESTful JSON API secured with Amazon's Signature V4 algorithm
- Database-agnostic, designed for scalable data stores (DynamoDB, Cassandra, MongoDB, etc.)
- Full-text search (Lucene, Elasticsearch)
- Distributed and local object cache (Hazelcast, Caffeine)
- Multitenancy - each app has its own table, index and cache
- Webhooks with signed payloads
- IoT support and integration with AWS and Azure
- Flexible security based on Spring Security (LDAP, SAML, social login, CSRF protection, etc.)
- Stateless client authentication with JSON Web Tokens (JWT)
- Simple but effective resource permissions for client access control
- Robust constraint validation mechanism based on JSR-303 and Hibernate Validator
- Per-object control of persistence, index and cache operations
- Support for optimistic locking and transactions (implemented by each `DAO` natively)
- Advanced serialization and deserialization capabilities (Jackson)
- Full metrics for monitoring and diagnostics (Dropwizard)
- Modular design powered by Google Guice and support for plugins
- I18n utilities for translating language packs and working with currencies
- Standalone executable JAR with embedded Jetty
- [Para Web Console](https://console.paraio.org) - admin user interface

### Architecture

<pre>
+----------------------------------------------------------+
|                  ____  ___ _ ____ ___ _                  |
|                 / __ \/ __` / ___/ __` /                 |
|                / /_/ / /_/ / /  / /_/ /                  |
|               / .___/\__,_/_/   \__,_/     +-------------+
|              /_/                           | Persistence |
+-------------------+  +-----------------+   +-------------+
|      REST API     |  |     Search      |---|    Cache    |
+---------+---------+--+--------+--------+---+------+------+
          |                     |                   |
+---------+---------+  +--------+--------+   +------+------+
|  Signed Requests  |  |  Search Index   |   |  Data Store |
|  and JWT Tokens   |  |      (Any)      |   |    (Any)    |
+----+---------^----+  +-----------------+   +-------------+
     |         |
+----v---------+-------------------------------------------+
|  Clients : JavaScript, PHP, Java, C#, Android, iOS, etc. |
+----------------------------------------------------------+
</pre>

## Documentation

### [Read the Docs](https://paraio.org/docs)

## Blog

### [Read more about Para on our blog](https://erudika.com/blog/tags/para/)

## Hosting

We offer **hosting and premium support** at [paraio.com](https://paraio.com) where you can try Para online with a
free developer account. Browse and manage your users and objects, do backups and edit permissions with a few clicks in
the web console. By upgrading to a premium account you will be able to scale you projects up and down in seconds and
manage multiple apps.

## Quick Start

Create a configuration file `application.conf` file in the same directory as the Para package.
Here's an example default configuration:
```ini
# the name of the root app
para.app_name = "Para"
# or set it to 'production'
para.env = "embedded"
# if true, users can be created without verifying their emails
para.security.allow_unverified_emails = false
# if hosting multiple apps on Para, set this to false
para.clients_can_access_root_app = true
# if false caching is disabled
para.cache_enabled = true
# root app secret, used for token generation, should be a random string
para.app_secret_key = "b8db69a24a43f2ce134909f164a45263"
# enable API request signature verification
para.security.api_security = true
# the node number from 1 to 1024, used for distributed ID generation
para.worker_id = 1
```

1. [Download the latest executable JAR](https://github.com/Erudika/para/releases)
2. Execute it with `java -jar -Dconfig.file=./application.conf para-*.jar`
3. Call `curl localhost:8080/v1/_setup` to get the access and secret keys for the root app (required)
4. Install `para-cli` tool for easy access `npm install -g para-cli` (optional)
5. Create a new "child" app for regular use (optional):
```
# run setup and set endpoint to either 'http://localhost:8080' or 'https://paraio.com'
$ para-cli setup
$ para-cli new-app "myapp" --name "My App"
```
6. Open [Para Web Console](https://console.paraio.org) or integrate with one of the API clients below.


The quickest way to interact with Para is through the [command-line tool](https://github.com/Erudika/para-cli) (CLI):
```
$ npm install -g para-cli
$ para-cli setup
$ para-cli ping
$ echo "{\"type\":\"todo\", \"name\": \"buy milk\"}" > todo.json
$ para-cli create todo.json --id todo1 --encodeId false
$ para-cli read --id todo1
$ para-cli search "type:todo"
```

## Docker

Tagged Docker images for Para are located at `erudikaltd/para` on Docker Hub.
First, create an `application.conf` file in a directory and run this command:

```
$ docker run -ti -p 8080:8080 --rm -v para-data:/para/data \
  -v $(pwd)/application.conf:/para/application.conf \
  -e JAVA_OPTS="-Dconfig.file=/para/application.conf -Dloader.path=lib" erudikaltd/para
```

**Environment variables**

`JAVA_OPTS` - Java system properties, e.g. `-Dpara.port=8000`
`BOOT_SLEEP` - Startup delay, in seconds

**Plugins**

To use plugins, create a new `Dockerfile-plugins` which does a multi-stage build like so:
```
# change X.Y.Z to the version you want to use
FROM erudikaltd/para:vX.Y.Z-base

FROM erudikaltd/para-dao-mongodb:X.Y.Z
```

Then simply run `$ docker build -f Dockerfile-plugins -t ParaMongo`.

## Building Para

Para can be compiled with JDK 8:

To compile it you'll need Maven. Once you have it, just clone and build:

```sh
$ git clone https://github.com/erudika/para.git && cd para
$ mvn install -DskipTests=true
```
To generate the executable "fat-jar" run `$ mvn package` and it will be in `./para-jar/target/para-x.y.z-SNAPSHOT.jar`.
Two JAR files will be generated in total - the fat one is a bit bigger in size.

To build the base package without plugins (excludes `para-dao-sql` and `para-search-lucene`), run:
```
$ cd para-jar && mvn -Pbase package
```

To run a local instance of Para for development, use:
```sh
$ mvn -Dconfig.file=./application.conf spring-boot:run
```

## Standalone server

You can run Para as a standalone server by downloading the executable JAR and then:

```sh
$ java -jar para-X.Y.Z.jar
```

The you can browse your objects through the **Para Web Console** [console.paraio.org](https://console.paraio.org).
Simply change the API endpoint to be your local server and connect your access keys.
The admin interface is client-side only and your secret key is never sent over the the network. Instead,
a JWT access token is generated locally and sent to the server on each request.

Alternatively, you can build a WAR file and deploy it to your favorite servlet container:
```
$ cd para-war && mvn package
```

## [Download JAR](https://github.com/Erudika/para/releases)

## Maven dependency

You can also integrate Para with your project by adding it as a dependency. Para is hosted on Maven Central.
Here's the Maven snippet to include in your `pom.xml`:

```xml
<dependency>
  <groupId>com.erudika</groupId>
  <artifactId>para-server</artifactId>
  <version>{see_green_version_badge_above}</version>
</dependency>
```

For building lightweight client-only applications connecting to Para, include only the client module:
```xml
<dependency>
  <groupId>com.erudika</groupId>
  <artifactId>para-client</artifactId>
  <version>{see_green_version_badge_above}</version>
</dependency>
```

## Command-line tool

- **Para CLI**: [para-cli](https://github.com/erudika/para-cli)

```sh
$ npm install -g para-cli
```

## API clients

Use these client libraries to quickly integrate Para into your project:

- **Java**: [para-client](https://github.com/erudika/para/tree/master/para-client)
- **JavaScript / Node.js**: [para-client-js](https://github.com/erudika/para-client-js)
- **PHP**: [para-client-php](https://github.com/erudika/para-client-php)
- **Python**: [para-client-python](https://github.com/erudika/para-client-python)
- **C# / .NET**: [para-client-csharp](https://github.com/erudika/para-client-csharp)
- **Android**: [para-client-android](https://github.com/erudika/para-client-android)
- **Swift / iOS**: [para-client-ios](https://github.com/erudika/para-client-ios)

## Database integrations

Use these `DAO` implementations to connect to different databases:

- **DynamoDB**: `AWSDynamoDAO` (included in `para-server`)
- **MongoDB**: [para-dao-mongodb](https://github.com/Erudika/para-dao-mongodb)
- **Cassandra**: [para-dao-cassandra](https://github.com/Erudika/para-dao-cassandra)
- **SQL** (H2/MySQL/SQL Server/PostgreSQL, etc.): [para-dao-sql](https://github.com/Erudika/para-dao-sql)
`H2DAO` is the default `DAO` and it's part of the SQL plugin (packaged with the JAR file)

## Search engine integrations

The `Search` interface is implemented by:

- **Lucene**: [para-search-lucene](https://github.com/erudika/para-search-lucene) **default** (packaged with the JAR file)
- **Elasticsearch**: [para-search-elasticsearch](https://github.com/erudika/para-search-elasticsearch)
- **Elasticsearch v5.x**: [para-search-elasticsearch-v5](https://github.com/erudika/para-search-elasticsearch-v5)
Compatible with ES 5.x only and missing some of the latest features like AWS Elasticsearch support.

## Cache integrations

The `Cache` interface is implemented by:

- **Caffeine**: **default** objects are cached locally (included in `para-server`)
- **Hazelcast**: [para-cache-hazelcast](https://github.com/Erudika/para-cache-hazelcast) (distributed)

## Queue implementations

The `Queue` interface is implemented by:

- **AWS SQS**: in the `AWSQueue` class
- `LocalQueue` for single-host deployments and local development

## Projects using Para

- [Scoold](https://scoold.com) - an open source StackOverflow clone
- [ParaIO.com](https://paraio.com) - managed Para hosting
- [Erudika.com](https://erudika.com/blog) - the search bar on our blog uses Para
- [Angular2 demo app](https://github.com/albogdano/angular2-para) - a sample Angular2 project
- [React demo app](https://github.com/albogdano/react-para) - a sample React project

## Wishlist / Roadmap

- Para `2.0` - migration to Quarkus, Java 13+ only, native image
- GraphQL support

## Getting help

- Have a question? - [ask it on Gitter](https://gitter.im/Erudika/para)
- Found a bug? - submit a [bug report here](https://github.com/Erudika/para/issues)
- Ask a question on Stack Overflow using the [`para`](https://stackoverflow.com/tags/para/info) tag
- For questions related to Scoold, use the [`para`](https://stackoverflow.com/tags/scoold/info) tag on Stack Overflow

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
