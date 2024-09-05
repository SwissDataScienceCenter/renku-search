{
  system,
  writeShellScriptBin,
  devshell-tools,
}: rec {
  redis-port-forward = devshell-tools.lib.installScript {
    script = ./scripts/redis-port-forward;
    inherit system;
  };

  redis-list-streams = devshell-tools.lib.installScript {
    script = ./scripts/redis-list-streams;
    inherit system;
  };

  redis-stream-info = devshell-tools.lib.installScript {
    script = ./scripts/redis-stream-info;
    inherit system;
  };

  k8s-reprovision = devshell-tools.lib.installScript {
    script = ./scripts/k8s-reprovision;
    inherit system;
  };

  solr-port-forward = devshell-tools.lib.installScript {
    script = ./scripts/solr-port-forward;
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
