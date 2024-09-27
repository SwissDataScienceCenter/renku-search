<!-- -*- fill-column: 80 -*- -->
# Renku Search

mod


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

- `headers`
- `payload`

Where the header denotes properties that control how a payload is processed. The
important properties are `type`, `dataContentType` and `schemaVersion`.

**type** specifies the payload type and how it can be decoded. There are
currently these message types, each denoting a specific payload structure
defined in
[renku-schema](https://github.com/SwissDataScienceCenter/renku-schema):

- `project.created`
- `project.updated`
- `project.removed`
- `projectAuth.added`
- `projectAuth.updated`
- `projectAuth.removed`
- `user.added`
- `user.updated`
- `user.removed`
- `group.added`
- `group.updated`
- `group.removed`
- `memberGroup.added`
- `memberGroup.updated`
- `memberGroup.removed`
- `reprovisioning.started`
- `reprovisioning.finished`

If a header contains a value different from that list, the message cannot be
processed.

**dataContentType** specifies the transport encoding, where avro supports

- `application/avro+binary`
- `application/avro+json`

**schemaVersion** specifies which version of `renku-schema` messages is sent.
Search supports

- `V1`
- `V2`

The `V` can be omitted in the payload.

### Endpoints

There are few endpoints exposed for internal use only.

- `/reindex`
- `/ping`
- `/version`

Doing a re-index works by dropping the SOLR index completely and then re-reading
the Redis stream. The `reindex` endpoint requires POST request with a JSON
payload. It can optionally specify a redis message-id from where to start
reading. If it is omitted, it will start from the last known message that
initiated the index. In this case, an empty JSON object must be sent in the
request.

Example:

Re-Index from last known start:
``` 
POST /reindex
Content-Type: application/json

{
  
}
```

Re-index by speciying a message id to start from:
``` 
POST /reindex
Content-Type: application/json

{
  "messageId": "22154-0"
}
```


### Configuration

The service is configured via environment variables. Each variable is prefixed
with `RS_` (for "renku search").

``` 
RS_CLIENT_ID=search-provisioner
RS_HTTP_SHUTDOWN_TIMEOUT=30s
RS_LOG_LEVEL=2
RS_METRICS_UPDATE_INTERVAL=15 seconds
RS_PROVISION_HTTP_SERVER_BIND_ADDRESS=0.0.0.0
RS_PROVISION_HTTP_SERVER_PORT=8081
RS_REDIS_CONNECTION_REFRESH_INTERVAL=30 minutes
RS_REDIS_DB=
RS_REDIS_HOST=localhost
RS_REDIS_MASTER_SET=
RS_REDIS_PASSWORD=
RS_REDIS_PORT=6379
RS_REDIS_QUEUE_DATASERVICE_ALLEVENTS=
RS_REDIS_QUEUE_GROUPMEMBER_ADDED=
RS_REDIS_QUEUE_GROUPMEMBER_REMOVED=
RS_REDIS_QUEUE_GROUPMEMBER_UPDATED=
RS_REDIS_QUEUE_GROUP_ADDED=
RS_REDIS_QUEUE_GROUP_REMOVED=
RS_REDIS_QUEUE_GROUP_UPDATED=
RS_REDIS_QUEUE_PROJECTAUTH_ADDED=
RS_REDIS_QUEUE_PROJECTAUTH_REMOVED=
RS_REDIS_QUEUE_PROJECTAUTH_UPDATED=
RS_REDIS_QUEUE_PROJECT_CREATED=
RS_REDIS_QUEUE_PROJECT_REMOVED=
RS_REDIS_QUEUE_PROJECT_UPDATED=
RS_REDIS_QUEUE_USER_ADDED=
RS_REDIS_QUEUE_USER_REMOVED=
RS_REDIS_QUEUE_USER_UPDATED=
RS_REDIS_SENTINEL=
RS_RETRY_ON_ERROR_DELAY=10 seconds
RS_SOLR_CORE=search-core-test
RS_SOLR_LOG_MESSAGE_BODIES=false
RS_SOLR_PASS=
RS_SOLR_URL=http://localhost:8983
RS_SOLR_USER=admin
```


## Search Api

Provides http endpoints for searching the index. There is a [query
dsl](/docs/query-manual.md) for more convenient searching for renku entities.
Additionally, there is an openapi documentation generated and a version
endpoint.

``` 
GET /api/search/query?q=<query-string>
GET /api/search/version
GET /api/search/spec.json
```

Here is an example of the result structure. For more details, the openapi doc
should be consulted.

``` json
{
  "items": [
    {
      "type": "Project",
      "id": "01HRA7AZ2Q234CDQWGA052F8MK",
      "name": "renku",
      "slug": "renku",
      "namespace": {
        "type": "Group",
        "id": "2CAF4C73F50D4514A041C9EDDB025A36",
        "name": "SDSC",
        "namespace": "SDSC",
        "description": "SDSC group",
        "score": 1.1
      },
      "repositories": [
        "https: //github.com/renku"
      ],
      "visibility": "public",
      "description": "Renku project",
      "createdBy": {
        "type": "User",
        "id": "1CAF4C73F50D4514A041C9EDDB025A36",
        "namespace": "renku/renku",
        "firstName": "Albert",
        "lastName": "Einstein",
        "score": 2.1
      },
      "creationDate": "2024-09-27T08: 56: 36.895631641Z",
      "keywords": [
        "data",
        "science"
      ],
      "score": 1.0
    },
    {
      "type": "User",
      "id": "1CAF4C73F50D4514A041C9EDDB025A36",
      "namespace": "renku/renku",
      "firstName": "Albert",
      "lastName": "Einstein",
      "score": 2.1
    },
    {
      "type": "Group",
      "id": "2CAF4C73F50D4514A041C9EDDB025A36",
      "name": "SDSC",
      "namespace": "SDSC",
      "description": "SDSC group",
      "score": 1.1
    }
  ],
  "facets": {
    "entityType": {
      "Project": 1,
      "Group": 1,
      "User": 1
    }
  },
  "pagingInfo": {
    "page": {
      "limit": 25,
      "offset": 0
    },
    "totalResult": 3,
    "totalPages": 1
  }
}
```

### Configuration

The service is configured via environment variables. Each variable is prefixed
with `RS_` (for "renku search").

``` 
RS_HTTP_SHUTDOWN_TIMEOUT=30s
RS_JWT_ALLOWED_ISSUER_URL_PATTERNS=
RS_JWT_ENABLE_SIGNATURE_CHECK=true
RS_JWT_KEYCLOAK_REQUEST_DELAY=1 minute
RS_JWT_OPENID_CONFIG_PATH=.well-known/openid-configuration
RS_LOG_LEVEL=2
RS_SEARCH_HTTP_SERVER_BIND_ADDRESS=0.0.0.0
RS_SEARCH_HTTP_SERVER_PORT=8080
RS_SOLR_CORE=search-core-test
RS_SOLR_LOG_MESSAGE_BODIES=false
RS_SOLR_PASS=
RS_SOLR_URL=http://localhost:8983
RS_SOLR_USER=admin
```
