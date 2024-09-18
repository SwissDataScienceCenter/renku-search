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
    instance = "search";
  };

  services.openapi-docs = {
    enable = true;
    openapi-spec = "http://localhost:8080/api/search/spec.json";
  };
}
