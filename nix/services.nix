{
  lib,
  config,
  ...
}: {
  services.solr = {
    enable = true;
  };

  services.redis.servers.search = {
    enable = true;
    port = 6379;
    bind = "0.0.0.0";
    openFirewall = true;
    settings = {
      "protected-mode" = "no";
    };
  };

  networking = {
    firewall.allowedTCPPorts = [8983];
  };
}
