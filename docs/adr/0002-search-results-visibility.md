# Use Markdown Architectural Decision Records

## Context and Problem Statement

The search index contains currently data from the primary database and
the authentication service, which is [authzed](https://authzed.com/).

The search results presented must conform to the visibility of each
entity. Currently this information is synhcronized into the search
index. This makes it easy to implement a proper search, by appending a
privacy-query to every submitted user query. This way private projects
that are not visible to the executing user can be deselected.

The problem with this approach is, that the information who can see
what might grow and yield a complex structure, that is foreseen to be
very hard to correctly synchronize. This could make it harder to
always have correctly synchronized data and the possibility of
mistakes gets higher. Any mistake here may have severe consequences.

The idea now is instead of synchronizing this data into the search
index, the search service can directly call the authentication service
in order to obtain the data it requires to implement correct search
results.

## Considered Options

### (a) Remove results from a solr response

The first option that comes to mind is to inspect a solr response and
remove entries that are not visible to the current user. The authzed
api allows to bulk-query permission checks (see
[here](https://www.postman.com/authzed/spicedb/folder/jde7cpq/checkbulk)).

This could be used to post-process the results of solr.

The problem with this approach are:

1. Paging needs to be re-done

  Since items may be removed from the result, it might be necessary to
  do another query in order to fill up the first page returned to the
  user. This then requires to manually compute the cursor solr uses in
  order to continue at the correct position with the next request.
  Solr has not opened up how it constructs the cursor - it could be
  reverse engineered, though.
2. Requires to pull more data from SOLR

  Due to the paging problem, there is a high chance we do too much
  querying at SOLR and transport more data than necessary *most of the
  time*.
3. It can quickly get very complex to adjust the results with features
   like facetting.
4. For every page, an additonal request is done to authzed

### (b) Query authzed to fetch all private entity ids

This approach does a request to authzed to lookup all entity ids, the
current user has read access to. In order to keep the volume low, only
the *private* entities are of interest. The solr index still contains
whether an entity is public or private. This list of ids can be used
to amend the query, similar as it is currently done. The user query
would be amended with a `AND (visibility:public OR id in (<list of ids
from authzed))`

The problem with this approach comes when the number of private
entities gets very large for a specific user. In practice this should
not happen, but these things could become a problem for special users,
like admins. Admins are handled specially though, there will be no
restricting query amendment.

The other problem is currently, that authzed doesn't allow to query
this information. It requires to add another relation so it is easy to
query exactly the private entities a user has access to.

Otherwise this approach would address most of the problems of the
above version. Paging can be done by SOLR as it is now, no
post-processing of results is necessary. For quicker results, the list
of private ids could be cached for a while to support quickly browsing
through search results.

## Decision Outcome

Decision is for approach b.
