{
  lib,
  config,
  pkgs,
  ...
}: {
  services.dev-solr = {
    enable = true;
    cores = ["rsdev-test" "search-core-test"];
    heap = 1024;
  };

  services.dev-redis = {
    enable = true;
    instances = {
      search = { port = 6379; };
    };
  };

  services.dev-spicedb = {
    enable = true;
  };

  services.openapi-docs = {
    enable = true;
    openapi-spec = "http://localhost:8080/api/search/spec.json";
  };
}
