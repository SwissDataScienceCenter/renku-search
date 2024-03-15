{
  lib,
  config,
  pkgs,
  ...
}: {
  services.dev-solr = {
    enable = true;
    cores = ["rsdev-test" "core-test1" "core-test2" "core-test3" "search-core-test"];
    heap = 512;
  };

  networking.hostName = "rsdev";

  services.dev-redis = {
    enable = true;
    instance = "search";
  };

  services.openapi-docs = {
    enable = true;
    openapi-spec = "http://localhost:8080/search/spec.json";
  };
}
