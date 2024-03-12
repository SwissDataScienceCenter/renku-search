{
  system,
  writeShellScriptBin,
  devshell-tools,
}: rec {
  redis-push = devshell-tools.lib.installScript {
    script = ./scripts/redis-push;
    inherit system;
  };

  k8s-reprovision = devshell-tools.lib.installScript {
    script = ./scripts/k8s-reprovision;
    inherit system;
  };

  solr-recreate-cores = writeShellScriptBin "solr-recreate-cores" ''
    script=$([ "$(which cnt-solr-recreate-core)" != "" ] && echo "cnt-solr-recreate-core" || echo "vm-solr-recreate-core")
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
