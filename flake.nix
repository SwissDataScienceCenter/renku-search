{
  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/release-23.11";
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
          ];
        };

        rsdev-cnt = devshell-tools.lib.mkContainer {
          system = flake-utils.lib.system.x86_64-linux;
          modules = [
            ./nix/services.nix
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
        projectCreated = "project.created";
        projectUpdated = "project.updated";
        projectRemoved = "project.removed";
        projectAuthAdded = "projectAuth.added";
        projectAuthUpdated = "projectAuth.updated";
        projectAuthRemoved = "projectAuth.removed";
        userAdded = "user.added";
        userUpdated = "user.updated";
        userRemoved = "user.removed";
      };

      queueNameConfig = with nixpkgs.lib; mapAttrs' (key: qn: nameValuePair "RS_REDIS_QUEUE_${key}" qn) queueNames;
    in {
      formatter = pkgs.alejandra;

      devShells = rec {
        default = container;
        container = pkgs.mkShellNoCC (queueNameConfig
          // {
            RS_SOLR_HOST = "rsdev-cnt";
            RS_SOLR_URL = "http://rsdev-cnt:8983/solr";
            RS_SOLR_CORE = "rsdev-test";
            RS_REDIS_HOST = "rsdev-cnt";
            RS_REDIS_PORT = "6379";
            RS_CONTAINER = "rsdev";
            RS_LOG_LEVEL = "3";
            RS_SEARCH_HTTP_SERVER_PORT = "8080";
            RS_PROVISION_HTTP_SERVER_PORT = "8082";
            RS_METRICS_UPDATE_INTERVAL = "0s";

            #don't start docker container for dbTests
            NO_SOLR = "true";
            NO_REDIS = "true";
            DEV_CONTAINER = "rsdev-cnt";

            buildInputs =
              commonPackages
              ++ (builtins.attrValues devshell-tools.legacyPackages.${system}.cnt-scripts);
          });
        vm = pkgs.mkShellNoCC (queueNameConfig
          // {
            RS_SOLR_HOST = "localhost";
            RS_SOLR_PORT = "18983";
            RS_SOLR_URL = "http://localhost:18983/solr";
            RS_SOLR_CORE = "rsdev-test";
            RS_REDIS_HOST = "localhost";
            RS_REDIS_PORT = "16379";
            VM_SSH_PORT = "10022";
            RS_LOG_LEVEL = "3";
            RS_SEARCH_HTTP_SERVER_PORT = "8080";
            RS_PROVISION_HTTP_SERVER_PORT = "8082";
            RS_METRICS_UPDATE_INTERVAL = "0s";

            #don't start docker container for dbTests
            NO_SOLR = "true";
            NO_REDIS = "true";

            DEV_VM = "rsdev-vm";

            buildInputs =
              commonPackages
              ++ (builtins.attrValues devshell-tools.legacyPackages.${system}.vm-scripts);
          });
      };
    });
}
