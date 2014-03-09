![Logo](https://s3-eu-west-1.amazonaws.com/org.paraio/para.png)
============================

**Para** - general purpose back-end framework for the cloud.

## What is this?

Para was designed as a simple and modular back-end framework for object persistence and retrieval.
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

### Standalone
Run the following command to install it:

```console
git clone https://github.com/erudika/para.git para
cd para
mvn jetty:run
```

In your browser open:
```console
open http://localhost:8080
```
### Maven

Para will be uploaded to a Maven repo very soon. - TODO

## Usage

**We are still writing the full documentation. Sorry!**

Docs - TODO

## Configuration

See the configuration file `src/main/resources/reference.conf` and modify it if needed.

## Contributing

1. Fork it and clone that to your machine
2. Create your feature branch (`git checkout -b my-new-feature`)
3. Commit your changes (`git commit -am 'Add some feature'`)
4. Push to the branch (`git push origin my-new-feature`)
5. Create new Pull Request

For more information see [CONTRIBUTING.md](http://github.com/erudika/para/CONTRIBUTING.md)

## License
<pre>
Copyright 2013-2014 Erudika. http://erudika.com

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
</pre>