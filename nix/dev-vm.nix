{ modulesPath, lib, config, ... }:

{
  imports = [
    (modulesPath + "/virtualisation/qemu-vm.nix")
  ];

  services.openssh = {
    enable = true;
    settings.PermitRootLogin = "yes";
  };

  users.users.root = {
    password = "root";
  };
  i18n = { defaultLocale = "de_DE.UTF-8"; };
  console.keyMap = "de";

  networking = {
    hostName = "renku-search-testvm";
    firewall.allowedTCPPorts = [ 8983 6379 ];
  };

  virtualisation.memorySize = 4096;

  virtualisation.forwardPorts = [
    {
      from = "host";
      host.port = 10022;
      guest.port = 22;
    }
    {
      from = "host";
      host.port = 18983;
      guest.port = 8983;
    }
    {
      from = "host";
      host.port = 16379;
      guest.port = 6379;
    }
  ];
  documentation.enable = false;

  services.solr = {
    enable = true;
  };

  services.redis.servers.search = {
    enable = true;
    port = 6379;
  };

  system.stateVersion = "23.11";
}
