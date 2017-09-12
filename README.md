![Logo](https://s3-eu-west-1.amazonaws.com/org.paraio/para.png)

## A general-purpose backend framework for the cloud.

[![Build Status](https://travis-ci.org/Erudika/para.svg?branch=master)](https://travis-ci.org/Erudika/para)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.erudika/para-core/badge.svg)](https://maven-badges.herokuapp.com/maven-central/com.erudika/para-core)
[![Join the chat at https://gitter.im/Erudika/para](https://badges.gitter.im/Erudika/para.svg)](https://gitter.im/Erudika/para?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)


**Para** was designed as a simple and modular backend framework for object persistence and retrieval.
It helps you build applications faster by taking care of the backend. It works on three levels -
objects are stored in a NoSQL data store or any old relational database, then automatically indexed
by a search engine and finally, cached.

The name "pára" means "steam" in Bulgarian. And just like steam is used to power stuff, you can use
Para to power your mobile or web application backend.

**Para** can be used as a direct dependency to your JVM-based application or as a standalone API server with
multiple applications and clients connecting to it.

See how **Para** [compares to other open source backend frameworks](https://erudika.com/blog/2015/10/21/backend-frameworks-usergrid-loopback-para-baasbox-deployd-telepat/).

### Features

- RESTful JSON API secured with Amazon's Signature 4 algorithm
- Database-agnostic, designed for scalable data stores (DynamoDB, Cassandra, MongoDB, etc.)
- Full-text search (Lucene by default, Elasticsearch plugin)
- Distributed object cache (Hazelcast by default)
- Multi-tenancy support - each app has its own table, index and cache
- IoT support and integration with AWS and Azure
- Flexible security based on Spring Security (LDAP, social login, CSRF protection, etc.)
- Stateless client authentication with JSON Web Tokens (JWT)
- Simple but effective resource permissions for client access control
- Robust constraint validation mechanism based on JSR-303 and Hibernate Validator
- Per-object control of persistence, index and cache operations
- Modular design powered by Google Guice and support for plugins
- I18n utilities for translating language packs and working with currencies
- Standalone executable WAR with embedded Jetty
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
|  and JWT Tokens   |  | (ElasticSearch) |   |    (Any)    |
+----+---------^----+  +-----------------+   +-------------+
     |         |
+----v---------+-------------------------------------------+
|  Clients : JavaScript, PHP, Java, C#, Android, iOS, etc. |
+----------------------------------------------------------+
</pre>

## Documentation

### [Read the Docs](https://paraio.org/docs)

## Quick Start

1. [Download the latest executable WAR](https://github.com/Erudika/para/releases)
2. Execute it with `java -jar -Dconfig.file=./application.conf para-X.Y.Z.war`
3. Call `curl localhost:8080/v1/_setup` to get the access and secret keys
4. Open [Para Web Console](https://console.paraio.org) or integrate with one of the API clients below.

Configuration properties belong in your `application.conf` file.
Here's an example configuration for development purposes:
```ini
# the name of your app
para.app_name = "My App"
# or set it to 'production'
para.env = "embedded"
# if true, users can be created without verifying their emails
para.security.allow_unverified_emails = false
# if hosting multiple apps on Para, set this to false
para.clients_can_access_root_app = true
# no need for caching in dev mode
para.cache_enabled = false
# change this to a random string
para.app_secret_key = "b8db69a24a43f2ce134909f164a45263"
# enable API request signature checking
para.security.api_security = true
```

The quickest way to interact with Para is through the [command-line tool](https://github.com/Erudika/para-cli) (CLI):
```
$ npm install -g para-cli
$ para-cli ping
$ echo "{\"type\":\"todo\", \"name\": \"buy milk\"}" > todo.json
$ para-cli create todo.json --id todo1 --encodeId false
$ para-cli read --id todo1
$ para-cli search "type:todo"
```

## Hosting

We offer **hosting and premium support** at [paraio.com](https://paraio.com) where you can try Para online with a
free developer account. Browse and manage your users and objects, do backups and edit permissions with a few clicks in
the web console. By upgrading to a premium account you will be able to scale you projects up and down in seconds and
manage multiple apps.

### Building Para

Para can be compiled with JDK 8:

To compile it you'll need Maven. Once you have it, just clone and build:

```sh
$ git clone https://github.com/erudika/para.git && cd para
$ mvn install -DskipTests=true
```
To generate the executable "uber-war" run `$ mvn package` and it will be in `./para-war/target/para-x.y.z-SNAPSHOT.war`.
Two WAR files will be generated in total - the fat one is a bit bigger in size.

To run a local instance of Para for development, use:
```sh
$ mvn -Dconfig.file=./application.conf spring-boot:run
```

### Standalone server

You can run Para as a standalone server by downloading the executable WAR and then:

```sh
$ java -jar para-X.Y.Z.war
```

The you can browse your objects through the **Para Web Console** [console.paraio.org](https://console.paraio.org).
Simply change the API endpoint to be your local server and connect your access keys.
The admin interface is client-side only and your secret key is never sent over the the network. Instead,
a JWT access token is generated locally and sent to the server on each request.

Alternatively, you can grab the WAR file and deploy it to your favorite servlet container.

### [Download WAR](https://github.com/Erudika/para/releases)

### Maven dependency

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
- **C# / .NET**: [para-client-csharp](https://github.com/erudika/para-client-csharp)
- **Android**: [para-client-android](https://github.com/erudika/para-client-android)
- **Swift / iOS**: [para-client-ios](https://github.com/erudika/para-client-ios)

## Database integrations

Use these `DAO` implementations to connect to different databases:

- **H2**: `H2DAO` **default** (included in `para-server`)
- **DynamoDB**: `AWSDynamoDAO` (included in `para-server`)
- **MongoDB**: [para-dao-mongodb](https://github.com/Erudika/para-dao-mongodb)
- **Cassandra**: [para-dao-cassandra](https://github.com/Erudika/para-dao-cassandra)

## Search engine integrations

The `Search` interface is implemented by:

- **Lucene**: `LuceneSearch` **default** (included in `para-server`)
- **Elasticsearch**: [para-search-elasticsearch](https://github.com/erudika/para-search-elasticsearch)

## Cache integrations

The `Cache` interface is implemented by:

- **Hazelcast**: `HazelcastCache` **default** (included in `para-server`)
- **In-memory**: objects can be cached on the JVM heap

## Projects using Para

- [Scoold](https://scoold.com) - an open source StackOverflow clone
- [ParaIO.com](https://paraio.com) - managed Para hosting
- [Erudika.com](https://erudika.com/blog) - the search bar on our blog uses Para
- [Angular2 primer](https://github.com/albogdano/angular2-para) - a sample Angular2 project

## Wishlist / Roadmap

- `DAO` implementation for PostgreSQL.
- MongoDB implementation of `Search`
- Make the API server more efficient with fibers (Quasar)
- Server-side JavaScript support for implementing custom API resources
- Integrations with Google App Engine, Heroku, DigitalOcean
- GraphQL or ([JSON API](http://jsonapi.org/)) compatible API at `/v2` (not soon)

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
