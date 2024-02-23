{
  concatTextFile,
  writeShellScriptBin,
}: rec {
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
    core_name=''${1:-search-core-test}
    sudo nixos-container run ''${RS_CONTAINER:-rsdev} -- su solr -c "solr create -c $core_name"
    sudo nixos-container run ''${RS_CONTAINER:-rsdev} -- find /var/solr/data/$core_name/conf -type f -exec chmod 644 {} \;
  '';

  solr-delete-core = writeShellScriptBin "solr-delete-core" ''
    core_name=''${1:-search-core-test}
    sudo nixos-container run ''${RS_CONTAINER:-rsdev} -- su solr -c "solr delete -c $core_name"
  '';

  solr-recreate-core = writeShellScriptBin "solr-recreate-core" ''
    ${solr-delete-core}/bin/solr-delete-core "$1"
    ${solr-create-core}/bin/solr-create-core "$1"
  '';

  vm-build = writeShellScriptBin "vm-build" ''
    nix build .#nixosConfigurations.dev-vm.config.system.build.vm
  '';

  vm-run = writeShellScriptBin "vm-run" ''
    nix run .#nixosConfigurations.dev-vm.config.system.build.vm
  '';

  vm-ssh = writeShellScriptBin "vm-ssh" ''
    ssh -p $VM_SSH_PORT root@localhost "$@"
  '';

  vm-solr-create-core = writeShellScriptBin "solr-create-core" ''
    core_name=''${1:-search-core-test}
    ssh -p $VM_SSH_PORT root@localhost "su solr -c \"solr create -c $core_name\""
    ssh -p $VM_SSH_PORT root@localhost "find /var/solr/data/$core_name/conf -type f -exec chmod 644 {} \;"
  '';

  vm-solr-delete-core = writeShellScriptBin "solr-delete-core" ''
    core_name=''${1:-search-core-test}
    ssh -p $VM_SSH_PORT root@localhost "su solr -c \"solr delete -c $core_name\""
  '';

  vm-solr-recreate-core = writeShellScriptBin "solr-recreate-core" ''
    ${vm-solr-delete-core}/bin/solr-delete-core "$1"
    ${vm-solr-create-core}/bin/solr-create-core "$1"
  '';
}