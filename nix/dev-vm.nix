{
  modulesPath,
  lib,
  config,
  ...
}: {
  imports = [
    (modulesPath + "/virtualisation/qemu-vm.nix")
    ./solr-module.nix
    ./services.nix
  ];

  services.openssh = {
    enable = true;
    settings.PermitRootLogin = "yes";
  };

  users.users.root = {
    password = "root";
  };
  i18n = {defaultLocale = "de_DE.UTF-8";};
  console.keyMap = "de";

  networking = {
    hostName = "renku-search-testvm";
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
    {
      from = "host";
      host.port = 8088;
      guest.port = 80;
    }
  ];
  documentation.enable = false;
}
