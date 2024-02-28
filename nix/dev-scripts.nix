{
  concatTextFile,
  writeShellScriptBin,
}: let key = ./dev-vm-key; in rec {
  redis-push = concatTextFile {
    name = "redis-push";
    files = [./scripts/redis-push];
    executable = true;
    destination = "/bin/redis-push";
  };

  recreate-container = concatTextFile {
    name = "recreate-container";
    files = [./scripts/recreate-container];
    executable = true;
    destination = "/bin/recreate-container";
  };

  start-container = writeShellScriptBin "start-container" ''
    cnt=''${RS_CONTAINER:-rsdev}
    sudo nixos-container start $cnt
  '';

  solr-create-core = writeShellScriptBin "solr-create-core" ''
    core_name=''${1:-rsdev-test}
    sudo nixos-container run ''${RS_CONTAINER:-rsdev} -- su solr -c "solr create -c $core_name"
    sudo nixos-container run ''${RS_CONTAINER:-rsdev} -- find /var/solr/data/$core_name/conf -type f -exec chmod 644 {} \;
  '';

  solr-delete-core = writeShellScriptBin "solr-delete-core" ''
    core_name=''${1:-rsdev-test}
    sudo nixos-container run ''${RS_CONTAINER:-rsdev} -- su solr -c "solr delete -c $core_name"
  '';

  solr-recreate-core = writeShellScriptBin "solr-recreate-core" ''
    ${solr-delete-core}/bin/solr-delete-core "$1"
    ${solr-create-core}/bin/solr-create-core "$1"
  '';

  solr-logs = writeShellScriptBin "solr-logs" ''
    sudo nixos-container run ''${RS_CONTAINER:-rsdev} -- journalctl -efu solr.service
  '';

  vm-build = writeShellScriptBin "vm-build" ''
    nix build .#nixosConfigurations.dev-vm.config.system.build.vm
  '';

  vm-run = writeShellScriptBin "vm-run" ''
    nix run .#nixosConfigurations.dev-vm.config.system.build.vm
  '';

  vm-ssh = writeShellScriptBin "vm-ssh" ''
    ssh -i ${key} -p $VM_SSH_PORT root@localhost "$@"
  '';

  vm-solr-logs = writeShellScriptBin "solr-logs" ''
    ${vm-ssh}/bin/vm-ssh journalctl -efu solr.service
  '';

  vm-solr-create-core = writeShellScriptBin "solr-create-core" ''
    core_name=''${1:-rsdev-test}
    ssh -p $VM_SSH_PORT root@localhost "su solr -c \"solr create -c $core_name\""
    ssh -p $VM_SSH_PORT root@localhost "find /var/solr/data/$core_name/conf -type f -exec chmod 644 {} \;"
  '';

  vm-solr-delete-core = writeShellScriptBin "solr-delete-core" ''
    core_name=''${1:-rsdev-test}
    ssh -p $VM_SSH_PORT root@localhost "su solr -c \"solr delete -c $core_name\""
  '';

  vm-solr-recreate-core = writeShellScriptBin "solr-recreate-core" ''
    ${vm-solr-delete-core}/bin/solr-delete-core "$1"
    ${vm-solr-create-core}/bin/solr-create-core "$1"
  '';

  solr-recreate-dbtests-cores = writeShellScriptBin "solr-recreate-dbtests-cores" ''
    solr-delete-core core-test
    solr-delete-core search-core-test
    solr-create-core core-test
    solr-create-core search-core-test
  '';
}
