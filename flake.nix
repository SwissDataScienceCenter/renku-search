{
  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/release-23.11";
    flake-utils.url = "github:numtide/flake-utils";
  };

  outputs = inputs @ {
    self,
    nixpkgs,
    flake-utils,
  }:
    {
      overlays.default = final: prev: {
        solr = self.packages.${prev.system}.solr;
        openapi-doc = self.packages.${prev.system}.openapi-doc;
      };
      nixosConfigurations = let
        selfOverlay = {
          lib,
          config,
          ...
        }: {
          nixpkgs.overlays = [
            self.overlays.default
          ];
          system.stateVersion = "23.11";
        };
      in {
        dev-vm = nixpkgs.lib.nixosSystem {
          system = flake-utils.lib.system.x86_64-linux;
          specialArgs = {inherit inputs;};
          modules = [
            selfOverlay
            ./nix/dev-vm.nix
          ];
        };

        container = nixpkgs.lib.nixosSystem {
          system = flake-utils.lib.system.x86_64-linux;
          modules = [
            ({pkgs, ...}: {
              boot.isContainer = true;
              networking.useDHCP = false;
            })
            selfOverlay
            ./nix/solr-module.nix
            ./nix/services.nix
          ];
        };
      };
    }
    // flake-utils.lib.eachDefaultSystem (system: let
      pkgs = nixpkgs.legacyPackages.${system};
      selfPkgs = self.packages.${system};
    in {
      formatter = pkgs.alejandra;
      packages =
        ((import ./nix/dev-scripts.nix) {inherit (pkgs) concatTextFile writeShellScriptBin;})
        // rec {
          solr = pkgs.callPackage (import ./nix/solr.nix) {};
          swagger-ui = pkgs.callPackage (import ./nix/swagger-ui.nix) {};
          openapi-doc = pkgs.callPackage (import ./nix/openapi-doc.nix) {inherit swagger-ui;};
        };

      devShells = rec {
        default = container;
        container = pkgs.mkShell {
          RS_SOLR_HOST = "rsdev";
          RS_SOLR_URL = "http://rsdev:8983/solr";
          RS_SOLR_CORE = "rsdev-test";
          RS_REDIS_HOST = "rsdev";
          RS_REDIS_PORT = "6379";
          RS_CONTAINER = "rsdev";
          RS_LOG_LEVEL = "3";

          #don't start docker container for dbTests
          NO_SOLR = "true";
          NO_REDIS = "true";

          buildInputs = with pkgs;
          with selfPkgs; [
            redis
            jq

            redis-push
            recreate-container
            start-container
            solr-create-core
            solr-delete-core
            solr-recreate-core
            solr-recreate-dbtests-cores
          ];
        };
        vm = pkgs.mkShell {
          RS_SOLR_URL = "http://localhost:18983/solr";
          RS_SOLR_CORE = "rsdev-test";
          RS_REDIS_HOST = "localhost";
          RS_REDIS_PORT = "16379";
          VM_SSH_PORT = "10022";
          RS_LOG_LEVEL = "3";

          #don't start docker container for dbTests
          NO_SOLR = "true";
          NO_REDIS = "true";

          buildInputs = with pkgs;
          with selfPkgs; [
            redis
            jq

            redis-push
            vm-build
            vm-run
            vm-ssh
            vm-solr-create-core
            vm-solr-delete-core
            vm-solr-recreate-core
            solr-recreate-dbtests-cores
          ];
        };
      };
    });
}
