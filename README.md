![Logo](https://s3-eu-west-1.amazonaws.com/org.paraio/para.png)

## A scalable, multitenant backend for the cloud.

[![Docker pulls](https://img.shields.io/docker/pulls/erudikaltd/para)](https://hub.docker.com/r/erudikaltd/para)
![Maven Central Version](https://img.shields.io/maven-central/v/com.erudika/para-parent)
[![Join the chat at https://gitter.im/Erudika/para](https://badges.gitter.im/Erudika/para.svg)](https://gitter.im/Erudika/para?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)


**Para** is a scalable, multitenant backend server/framework for object persistence and retrieval.
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
| Clients: JavaScript, PHP, Java, C#, Android, iOS, et al. |
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

1. [Download the latest executable JAR](https://github.com/Erudika/para/releases)
2. Create a configuration file `application.conf` file in the same directory as the JAR package.
3. Start Para with `java -jar -Dconfig.file=./application.conf para-*.jar`
4. Install [Para CLI](https://github.com/Erudika/para-cli) with `npm install -g para-cli`
5. Create a new dedicated app for your project and save the access keys:
```
# run setup and set endpoint to either 'http://localhost:8080' or 'https://paraio.com'
# the keys for the root app are inside application.conf
$ para-cli setup
$ para-cli new-app "myapp" --name "My App"
```
Alternatively, you can use the [Para Web Console](https://console.paraio.org) to manage data,
or integrate Para directly into your project with one of the API clients below.

## Docker

Tagged Docker images for Para are located at `erudikaltd/para` on Docker Hub.
**It's highly recommended that you pull only release images like `:1.51.0` or `:latest_stable`
because the `:latest` tag can be broken or unstable.**
First, create an `application.conf` file and a `data` folder and start the Para container:

```
$ touch application.conf && mkdir data
$ docker run -ti -p 8080:8080 --rm -v $(pwd)/data:/para/data \
  -v $(pwd)/application.conf:/para/application.conf \
  -e JAVA_OPTS="-Dconfig.file=/para/application.conf" erudikaltd/para:latest_stable
```

**Environment variables**

`JAVA_OPTS` - Java system properties, defaults to `-Dloader.path=lib`

**Plugins**

You can create a custom Para container with all plugins and JDBC drivers you need by using `docker compose`.
Below is an example build of Para, using `para-dao-sql`, `para-search-lucene` and PosgreSQL as a database.

1. First, create a new `Dockerfile-plugins` which does a multi-stage build like so:

<ul><li>
<details>
  <summary>View contents of <code>Dockerfile-plugins</code></summary>

```docker
ARG PARA_VERSION="0.0.0"
ARG SQL_DAO_VERSION="0.0.0"
FROM erudikaltd/para:v${PARA_VERSION} AS base
FROM erudikaltd/para-dao-sql:${SQL_DAO_VERSION} AS dao
FROM erudikaltd/para-search-lucene:${SEARCH_VERSION} AS search
FROM base AS final
COPY --from=dao /para/lib/*.jar /para/lib
COPY --from=search /para/lib/*.jar /para/lib

# EXAMPLE: Add a PostgreSQL JDBC Driver
ARG PG_JDBC_VERSION="0.0.0"
ADD https://jdbc.postgresql.org/download/postgresql-${PG_JDBC_VERSION}.jar /para/lib/
```

</details>
</li></ul>

2. Then, create a `docker-compose.yml` file:

<ul><li>
<details>
  <summary>View contents of <code>docker-compose.yml</code></summary>

```yml
services:
   para:
     depends_on:
       - db
     build:
       context: .
       dockerfile: Dockerfile
       args:
         PARA_VERSION: "1.51.0"
         SQL_DAO_VERSION: "1.49.1"
         PG_JDBC_VERSION: "42.7.7"
     image: para-with-plugins
     pull_policy: never
     ports:
       - "8080:8080"
     volumes:
       - type: volume
         source: paraData
         target: /para/data
       - type: volume
         source: paraLib
         target: /para/lib
       - type: bind
         source: ./para-application.conf
         target: /para/application.conf
     restart: always
     environment:
       - JAVA_OPTS=-Dconfig.file=/para/application.conf -Dloader.path=/para/lib

   db:
     image: postgres:latest
     ports:
       - "5432:5432"
     volumes:
       - type: volume
         source: postgresData
         target: /var/lib/postgresql/data
     restart: always
     environment:
       - POSTGRES_PASSWORD=mysecretpassword
       - PGDATA=/var/lib/postgresql/data
volumes:
  paraData:
  paraLib:
  postgresData:
```
</details>
</li></ul>

3. Also reate the Para configuration file `para-application.conf`:

<ul><li>
<details>
  <summary>View contents of <code>para-application.conf</code></summary>

```ini
para.env = "production"
para.dao = "SqlDAO"
para.sql.driver = "org.postgresql.Driver"
para.sql.url = "postgresql://db:5432/para"
para.sql.user = "postgres"
para.sql.password = "mysecretpassword"
```
</details>
</li></ul>

4. Finally, run `docker compose build para && docker compose up`

## Kubernetes

There's a Helm chart inside the `helm/` folder. First edit `helm/para/values.yaml` and then you can deploy Para to
Kubernetes with a single command:

```
cd helm; helm install para ./para
```

For more info, check the quick start guide at [`helm/README.md`](helm/README.md).

## Building Para

Para can be compiled with JDK 8+:

To compile it you'll need Maven. Once you have it, just clone and build:

```sh
$ git clone https://github.com/erudika/para.git && cd para
$ mvn install -DskipTests=true
```
To generate the executable "fat-jar" run `$ mvn package` and it will be in `./para-server/target/para-x.y.z-SNAPSHOT.jar`.
Two JAR files will be generated in total - the fat one is a bit bigger in size.

To build the native image of Para, run:

```
$ cd para-server && mvn -Pnative package
```

Note, that native images are platform-dependent, so an image built on x86-64 will not run on aarch64, for example.

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

- **DynamoDB**: [para-dao-dynamodb](https://github.com/Erudika/para-dao-dynamodb)
- **MongoDB**: [para-dao-mongodb](https://github.com/Erudika/para-dao-mongodb)
- **Cassandra**: [para-dao-cassandra](https://github.com/Erudika/para-dao-cassandra)
- **SQL** (H2/MySQL/SQL Server/PostgreSQL, etc.): [para-dao-sql](https://github.com/Erudika/para-dao-sql)
`H2DAO` is the default `DAO` and it's part of the SQL plugin (packaged with the JAR file)

## Search engine integrations

The `Search` interface is implemented by:

- **Lucene**: [para-search-lucene](https://github.com/erudika/para-search-lucene)
- **Elasticsearch / OpenSearch**: [para-search-elasticsearch](https://github.com/erudika/para-search-elasticsearch)
- **Elasticsearch v5.x**: [para-search-elasticsearch-v5](https://github.com/erudika/para-search-elasticsearch-v5)
Compatible with ES 5.x only and missing some of the latest features like AWS Elasticsearch support.

## Cache integrations

The `Cache` interface is implemented by:

- **Caffeine**: objects are cached locally (included in `para-server`)
- **Hazelcast**: [para-cache-hazelcast](https://github.com/Erudika/para-cache-hazelcast) (distributed)

## Queue implementations

The `Queue` interface is implemented by:

- **Amazon SQS**: [para-queue-sqs](https://github.com/erudika/para-queue-sqs) plugin for Amazon SQS
- `LocalQueue` for single-host deployments and local development

## File storage implementations

The `FileStore` interface is implemented by:

- **Amazon S3**: [para-storage-s3](https://github.com/erudika/para-storage-s3) plugin for Amazon S3
- `LocalFileStore`: for storing files to the local file system.

## Emailer implementations

The `Emailer` interface is implemented by:

- **AWSEmailer**: [para-email-ses](https://github.com/erudika/para-email-ses) plugin for Amazon SES
- `JavaMailEmailer` - the default JavaMail implementation for handling transaction emails

## Projects using Para

- [Scoold](https://scoold.com) - an open source StackOverflow clone
- [ParaIO.com](https://paraio.com) - managed Para hosting
- [Erudika.com](https://erudika.com/blog) - the search bar on our blog uses Para
- [Angular demo app](https://github.com/albogdano/angular2-para) - a sample Angular project
- [React demo app](https://github.com/albogdano/react-para) - a sample React project

## Wishlist / Roadmap

- Para `2.0` - Java 25+ only, native image
- Better admin UI

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
