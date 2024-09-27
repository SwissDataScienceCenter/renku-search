<!-- -*- fill-column: 80 -*- -->
# Renku Search

```scala mdoc:invisible
import io.renku.search.provision.Routes
import io.renku.search.http.routes.OperationRoutes
import io.renku.redis.client.MessageBodyKeys
import io.renku.search.events.*
import io.renku.search.config.ConfigValues
import io.renku.search.provision.SearchProvisionConfig
import io.renku.search.model.EntityType
import io.renku.search.api.{data, tapir, SearchApiConfig, Microservice as SearchApiMS}
import io.renku.search.readme.*
import cats.effect.*
import cats.effect.unsafe.implicits.*
```

This provides the renku search services for efficiently searching across
entities in the Renku platform.

The engine backing the search functionality is [SOLR](https://solr.apache.org)
(Lucene). The index is created from data pulled from a Redis stream.

There are two services provided: [search-api](#search-api) and
[search-provision](#search-provision).

There is a detailed [development documentation](development.md) for getting
started.

## Search Provision

Responsible for maintaining the index. This service is reading elements off the
Redis stream, transforming it into documents and then updates the index. It also
creates the SOLR schema and provides endpoints to trigger re-indexing.

This service is internal only and can be used by other services in the Renku
platform to publish data that should be search- and discoverable.

### Messages

Messages in the stream must conform to the definitions in
[renku-schema](https://github.com/SwissDataScienceCenter/renku-schema) and are
sent as binary [Avro](https://avro.apache.org/) messages.

A redis message is expected to contain two keys:

```scala mdoc:passthrough
BulletPoints(MessageBodyKeys.values.toSeq.map(_.name.backticks))
```

Where the header denotes properties that control how a payload is processed. The
important properties are `type`, `dataContentType` and `schemaVersion`.

**type** specifies the payload type and how it can be decoded. There are
currently these message types, each denoting a specific payload structure
defined in
[renku-schema](https://github.com/SwissDataScienceCenter/renku-schema):

```scala mdoc:passthrough
BulletPoints(MsgType.values.toSeq.map(_.name.backticks))
```

If a header contains a value different from that list, the message cannot be
processed.

**dataContentType** specifies the transport encoding, where avro supports

```scala mdoc:passthrough
BulletPoints(DataContentType.values.toSeq.map(_.mimeType.backticks))
```

**schemaVersion** specifies which version of `renku-schema` messages is sent.
Search supports

```scala mdoc:passthrough
BulletPoints(SchemaVersion.values.toSeq.map(_.name.backticks))
```

The `V` can be omitted in the payload.

### Endpoints

There are few endpoints exposed for internal use only.

```scala mdoc:passthrough
BulletPoints(Routes.Paths.values.toSeq.map(e => s"/${e.name}".backticks))
BulletPoints(OperationRoutes.Paths.values.toSeq.map(e => s"/${e.name}".backticks))
```

Doing a re-index works by dropping the SOLR index completely and then re-reading
the Redis stream. The `reindex` endpoint requires POST request with a JSON
payload. It can optionally specify a redis message-id from where to start
reading. If it is omitted, it will start from the last known message that
initiated the index. In this case, an empty JSON object must be sent in the
request.

Example:

Re-Index from last known start:
```scala mdoc:passthrough
CodeBlock.plainLines(
 s"POST /${Routes.Paths.Reindex.name}",
 "Content-Type: application/json",
 "",
 JsonPrinter.str(Routes.ReIndexMessage(None))
)
```

Re-index by speciying a message id to start from:
```scala mdoc:passthrough
CodeBlock.plainLines(
  s"POST /${Routes.Paths.Reindex.name}",
  "Content-Type: application/json",
  "",
  JsonPrinter.str(Routes.ReIndexMessage(Some(MessageId("22154-0"))))
)
```


### Configuration

The service is configured via environment variables. Each variable is prefixed
with `RS_` (for "renku search").

```scala mdoc:passthrough
CodeBlock.plain {
  SearchProvisionConfig.configValues
    .getAll.toList.sortBy(_._1).map { case (k, v) =>
      val value = v.getOrElse("")
      s"${k}=$value"
    }
    .mkString("\n")
}
```


## Search Api

Provides http endpoints for searching the index. There is a [query
dsl](/docs/query-manual.md) for more convenient searching for renku entities.
Additionally, there is an openapi documentation generated and a version
endpoint.

```scala mdoc:passthrough
CodeBlock.plainLines(
  s"GET /${SearchApiMS.pathPrefix.mkString("/")}/query?q=<query-string>",
  s"GET /${SearchApiMS.pathPrefix.mkString("/")}/version",
  s"GET /${SearchApiMS.pathPrefix.mkString("/")}/spec.json"
)
```

Here is an example of the result structure. For more details, the openapi doc
should be consulted.

```scala mdoc:passthrough
JsonPrinter.block {
  data.SearchResult(
    items = List(tapir.ApiSchema.exampleProject, tapir.ApiSchema.exampleUser, tapir.ApiSchema.exampleGroup),
    facets = data.FacetData(Map(
      EntityType.Project -> 1,
      EntityType.Group -> 1,
      EntityType.User -> 1
    )),
    pagingInfo = data.PageWithTotals(data.PageDef.default, 3L, false)
  )
}
```

### Configuration

The service is configured via environment variables. Each variable is prefixed
with `RS_` (for "renku search").

```scala mdoc:passthrough
CodeBlock.plain {
  SearchApiConfig.configValues
    .getAll.toList.sortBy(_._1).map { case (k, v) =>
      val value = v.getOrElse("")
      s"${k}=$value"
    }
    .mkString("\n")
}
```
