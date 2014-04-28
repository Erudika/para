![Logo](https://s3-eu-west-1.amazonaws.com/org.paraio/para.png)
============================

> ### Para - a general-purpose back-end framework for the cloud.

## What is this?

**Para** was designed as a simple and modular back-end framework for object persistence and retrieval.
It enables your application to store its objects to a datastore (SQL or NoSQL based) and then it 
automatically indexes those objects and makes them searchable.

Para can be used in two ways - as part of single application or as a standalone server supporting multiple
applications. 

### Features

- RESTful JSON API secured with Amazon's Signature 4 algorithm.
- Simple annotation-based Object Grid Mapper (OGM) for mapping objects to a datastore.
- Support for scalable data stores (Amazon DynamoDB, Cassandra).
- Full text search (current implementation is based on ElasticSearch).
- Distributed object cache support (implemented with Hazelcast).
- Strong and flexible security based on Spring Security (OpenID and Facebook integration, CSRF protection, etc.).
- Internationalization utilities for translating language packs into different languages.

## Getting started

### Standalone & WAR
Run the following command to install it:

```sh
git clone https://github.com/erudika/para.git para
cd para
mvn jetty:run
```
And in your browser open `http://localhost:8080`.

Alternatively, you can grab the WAR file and deploy it to your your servlet container.

####[Download WAR](https://github.com/Erudika/para/releases/download/v1.1.2/para-web-1.1.2.war)

### Maven

Para is hosted on Maven Central - just add it to your `pom.xml`:

```xml
<dependency>
  <groupId>com.erudika</groupId>
  <artifactId>para</artifactId>
  <version>1.1.2</version>
</dependency>
```

## Usage

**We are still writing the full documentation. Sorry!**

Docs - TODO

## Configuration

See the configuration file `src/main/resources/reference.conf` and modify it if needed.

## Contributing

1. Fork this repository and clone the fork to your machine
2. Create your feature branch (`git checkout -b my-new-feature`)
3. Implement a new feature or fix a bug and add some tests
4. Commit your changes (`git commit -am 'Add some feature'`)
5. Push to the branch (`git push origin my-new-feature`)
6. Create new Pull Request

For more information see [CONTRIBUTING.md](https://github.com/Erudika/para/blob/master/CONTRIBUTING.md)

## License
[Apache 2.0](LICENSE)