{
  lib,
  config,
  pkgs,
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

  services.nginx = {
    enable = true;
    virtualHosts.rsdev.locations."/" = {
      root = "${pkgs.openapi-doc}";
    };
  };

  networking = {
    firewall.allowedTCPPorts = [8983 80];
  };
}
