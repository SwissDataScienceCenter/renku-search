{
  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/release-24.05";
    flake-utils.url = "github:numtide/flake-utils";
    devshell-tools.url = "github:eikek/devshell-tools";
  };

  outputs = inputs @ {
    self,
    nixpkgs,
    flake-utils,
    devshell-tools,
  }:
    {
      nixosConfigurations = {
        rsdev-vm = devshell-tools.lib.mkVm {
          system = flake-utils.lib.system.x86_64-linux;
          modules = [
            ./nix/services.nix
            {
              virtualisation.memorySize = 2048;
              networking.hostName = "rsdev-vm";
            }
          ];
        };

        rsdev-cnt = devshell-tools.lib.mkContainer {
          system = flake-utils.lib.system.x86_64-linux;
          modules = [
            ./nix/services.nix
            {
              networking.hostName = "rsdev-cnt";
            }
          ];
        };
      };
    }
    // flake-utils.lib.eachDefaultSystem (system: let
      pkgs = nixpkgs.legacyPackages.${system};
      selfPkgs = import ./nix/dev-scripts.nix {
        inherit system devshell-tools;
        inherit (pkgs) writeShellScriptBin;
      };
      devshellToolsPkgs = devshell-tools.packages.${system};
      commonPackages = with pkgs;
        [
          redis
          jq
          coreutils
          scala-cli
          kubectl
          devshellToolsPkgs.sbt17
          devshellToolsPkgs.openapi-docs
        ]
        ++ (builtins.attrValues selfPkgs);

      queueNames = {
        PROJECT_CREATED = "project.created";
        PROJECT_UPDATED = "project.updated";
        PROJECT_REMOVED = "project.removed";
        PROJECTAUTH_ADDED = "projectAuth.added";
        PROJECTAUTH_UPDATED = "projectAuth.updated";
        PROJECTAUTH_REMOVED = "projectAuth.removed";
        USER_ADDED = "user.added";
        USER_UPDATED = "user.updated";
        USER_REMOVED = "user.removed";
        GROUP_ADDED = "group.added";
        GROUP_UPDATED = "group.updated";
        GROUP_REMOVED = "groupRemoved";
        GROUPMEMBER_ADDED = "groupMemberAdded";
        GROUPMEMBER_UPDATED = "groupMemberUpdated";
        GROUPMEMBER_REMOVED = "groupMemberRemoved";
        DATASERVICE_ALLEVENTS = "data_service.all_events";
      };

      queueNameConfig = with nixpkgs.lib; mapAttrs' (key: qn: nameValuePair "RS_REDIS_QUEUE_${key}" qn) queueNames;
    in {
      formatter = pkgs.alejandra;

      devShells = {
        container = pkgs.mkShellNoCC (queueNameConfig
          // {
            RS_SOLR_HOST = "rsdev-cnt";
            RS_SOLR_URL = "http://rsdev-cnt:8983";
            RS_SOLR_CORE = "rsdev-test";
            RS_REDIS_HOST = "rsdev-cnt";
            RS_REDIS_PORT = "6379";
            RS_CONTAINER = "rsdev";
            RS_LOG_LEVEL = "3";
            RS_SEARCH_HTTP_SERVER_PORT = "8080";
            RS_SEARCH_HTTP_SHUTDOWN_TIMEOUT = "0ms";
            RS_PROVISION_HTTP_SERVER_PORT = "8082";
            RS_PROVISION_HTTP_SHUTDOWN_TIMEOUT = "0ms";
            RS_METRICS_UPDATE_INTERVAL = "0s";
            RS_SOLR_CREATE_CORE_CMD = "cnt-solr-create-core %s";
            RS_SOLR_DELETE_CORE_CMD = "cnt-solr-delete-core %s";
            RS_JWT_ALLOWED_ISSUER_URL_PATTERNS = "renku.ch,*.renku.ch,*.*.renku.ch";

            #don't start docker container for dbTests
            NO_SOLR = "true";
            NO_REDIS = "true";
            DEV_CONTAINER = "rsdev-cnt";
            SBT_OPTS = "-Xmx2G";

            buildInputs =
              commonPackages
              ++ (builtins.attrValues devshell-tools.legacyPackages.${system}.cnt-scripts);
          });
        vm = pkgs.mkShellNoCC (queueNameConfig
          // {
            RS_SOLR_HOST = "localhost";
            RS_SOLR_PORT = "18983";
            RS_SOLR_URL = "http://localhost:18983";
            RS_SOLR_CORE = "rsdev-test";
            RS_REDIS_HOST = "localhost";
            RS_REDIS_PORT = "16379";
            VM_SSH_PORT = "10022";
            RS_LOG_LEVEL = "3";
            RS_SEARCH_HTTP_SERVER_PORT = "8080";
            RS_SEARCH_HTTP_SHUTDOWN_TIMEOUT = "0ms";
            RS_PROVISION_HTTP_SERVER_PORT = "8082";
            RS_PROVISION_HTTP_SHUTDOWN_TIMEOUT = "0ms";
            RS_METRICS_UPDATE_INTERVAL = "0s";
            RS_SOLR_CREATE_CORE_CMD = "vm-solr-create-core %s";
            RS_SOLR_DELETE_CORE_CMD = "vm-solr-delete-core %s";
            RS_JWT_ALLOWED_ISSUER_URL_PATTERNS = "renku.ch,*.renku.ch,*.*.renku.ch";

            #don't start docker container for dbTests
            NO_SOLR = "true";
            NO_REDIS = "true";
            DEV_VM = "rsdev-vm";
            SBT_OPTS = "-Xmx2G";

            buildInputs =
              commonPackages
              ++ (builtins.attrValues devshell-tools.legacyPackages.${system}.vm-scripts);
          });
      };
    });
}
