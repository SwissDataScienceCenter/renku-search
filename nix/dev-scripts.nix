{
  system,
  writeShellScriptBin,
  devshell-tools,
}: rec {
  redis-project-create = devshell-tools.lib.installScript {
    script = ./scripts/redis-project-create;
    inherit system;
  };

  redis-project-update = devshell-tools.lib.installScript {
    script = ./scripts/redis-project-update;
    inherit system;
  };

  redis-project-remove = devshell-tools.lib.installScript {
    script = ./scripts/redis-project-remove;
    inherit system;
  };

  redis-user-add = devshell-tools.lib.installScript {
    script = ./scripts/redis-user-add;
    inherit system;
  };

  redis-user-remove = devshell-tools.lib.installScript {
    script = ./scripts/redis-user-remove;
    inherit system;
  };

  redis-auth-add = devshell-tools.lib.installScript {
    script = ./scripts/redis-auth-add;
    inherit system;
  };

  redis-auth-remove = devshell-tools.lib.installScript {
    script = ./scripts/redis-auth-remove;
    inherit system;
  };

  k8s-reprovision = devshell-tools.lib.installScript {
    script = ./scripts/k8s-reprovision;
    inherit system;
  };

  solr-recreate-cores = writeShellScriptBin "solr-recreate-cores" ''
    set +e
    script=$([ "$(which cnt-solr-recreate-core 2> /dev/null)" != "" ] && echo "cnt-solr-recreate-core" || echo "vm-solr-recreate-core")
    for c in $@; do
        $script $c
    done
  '';

  # core names are defined in project/SolrServer.scala
  solr-recreate-dbtests-cores = writeShellScriptBin "solr-recreate-dbtests-cores" ''
    ${solr-recreate-cores}/bin/solr-recreate-cores core-test1 core-test2 core-test3 search-core-test
  '';

  solr-recreate-search-core = writeShellScriptBin "solr-recreate-search-core" ''
    ${solr-recreate-cores}/bin/solr-recreate-cores $RS_SOLR_CORE
  '';
}
