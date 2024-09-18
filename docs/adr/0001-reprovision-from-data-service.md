# Reprovision From Data-Service

## Context and Problem Statement

The SOLR index can get out of sync due to many possible problems:
wrong events get sent, redis streams corrupted, bugs in search, etc.
Also for some SOLR schema changes, a re-index of every document is
necessary. This results in two things that we need:

1. Run a re-index from search service directly that would ingest every
   document again. This can be run on demand or as a result of
   specific migrations that require re-indexing.
2. In case the redis stream is corrupted, all data must be sent again
   from data services. This indicates a new "start" in the stream and
   consumers should discard all previous events.

## Considered Options

### switching multiple streams

In this scenario, data services would create a new stream, recreate
all events from the database and push them into the stream. This
happens concurrently with any "live" events that are happening at the
time. Once done, a different "control message" is sent to tell all
consumers to switch to a new stream.

Pros:
- it makes it very easy to delete the entire old stream and cleanup
  space on redis
- if something fails when re-creating the events, data services could
  just start over as no one has consumed anything yet
- a re-index from the search side would be simple as it only requires
  to re-read the stream from its beginning

Cons:
- it is more complex protocol to tell consumers to switch, they could
  miss the message (due to bugs) etc
- it requires to change configuration of services going from
  statically known stream names to dynamic ones
- only when the "control" message is sent, consumers know about the
  new stream and can start only then with consuming, which would lead
  to a longer time of degraded search

### using the existing stream

Since all events are pushed to a single stream, send a
`ReprovisionStarted` message to indicate that data service is going to
re-send data. The search service can process this message by clearing
the index and keeping the message id.

Pros:
- Doesn't require consumers to change their configuration to redis.
- Simple protocol by introducing another message
- Consumers can start immediately with processing new messages

Cons:
- Deleting obsolete messages from the redis stream is possible, but
  the data structure is not optimized for that
- A re-index from the search side requires to store the redis message
  id of the `ReprovisionStarted` message and thus more book keeping is
  required
- Consumers could still read "bad data" if they go before the
  `ReprovisionStarted` message, as these messages are still visible
  (until finally deleted)

### Additional

We also discussed marking the events coming from reprovisioning so
they are discoverable by consumers. One idea would be to use entirely
different event types, or to add some other property to the events.

## Decision Outcome

For now, we opted into the _using the existing stream_ option. It is
the easiest and quickest to implement and most of the code would be
needed also for the other option. The other option is still possible
to re-visit in the future.

The idea of marking reprovisioning events has been dropped, as it was
deemed to be not necessary or useful.
