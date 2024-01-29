# search-provision

This is the provisioning microservice module.

The service is responsible for:
* Preparing the Search specific Solr DB by running all necessary migrations (creation of the relevant core on Solr is not part of the process).
* Starting a process that:
  * Takes all the messages from a relevant stream (historical ones as well as all new that come after the service startup).
  * Decode them using relevant AVRO definitions.
  * Convert them into specific Search Solr documents.
  * Push them to the relevant Solr DB (core).
