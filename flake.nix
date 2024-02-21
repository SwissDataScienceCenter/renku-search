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
        # nix build .#nixosConfigurations.dev-vm.config.system.build.vm
        dev-vm = nixpkgs.lib.nixosSystem {
          system = flake-utils.lib.system.x86_64-linux;
          specialArgs = {inherit inputs;};
          modules = [
            selfOverlay
            ./nix/dev-vm.nix
          ];
        };

        # sudo nixos-container create rsdev --flake .
        # sudo nixos-container start rsdev
        # -> http://rsdev:8983/solr
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
    in {
      formatter = pkgs.alejandra;
      packages = {solr = pkgs.callPackage (import ./nix/solr.nix) {};};
      devShells = rec {
        default = container;
        container = pkgs.mkShell {
          buildInputs = [
            pkgs.redis
            pkgs.jq
            (pkgs.writeShellScriptBin "solr-create-core" ''
              sudo nixos-container run ''${RS_CONTAINER:-rsdev} -- su solr -c "solr create -c $1"
              sudo nixos-container run ''${RS_CONTAINER:-rsdev} -- find /var/solr/data/$1/conf -type f -exec chmod 644 {} \;
            '')
            (pkgs.writeShellScriptBin "solr-delete-core" ''
              sudo nixos-container run ''${RS_CONTAINER:-rsdev} -- su solr -c "solr delete -c $1"
            '')
            (pkgs.writeShellScriptBin "cnt-solr-recreate-core" ''
              cnt-solr-delete-core "$1"
              cnt-solr-create-core "$1"
            '')
            (pkgs.writeShellScriptBin "recreate-container" ''
              cnt=''${RS_CONTAINER:-rsdev}
              if nixos-container list | grep $cnt > /dev/null; then
                echo "Destroying container $cnt"
                sudo nixos-container destroy $cnt
              fi
              echo "Creating and starting container $cnt ..."
              sudo nixos-container create $cnt --flake .
              sudo nixos-container start $cnt
            '')
            (pkgs.writeShellScriptBin "redis-push" ''
              cnt=''${RS_CONTAINER:-rsdev}
              header='{"source":"dev","type":"project.created","dataContentType":"application/avro+json","schemaVersion":"1","time":0,"requestId":"r1"}'
              payload=$(jq --null-input --arg id "$1" --arg name "$2" --arg slug "$1/$2" '{"id":$id,"name":$name,"slug":$slug, "repositories":[],"visibility":"public","description":"my project $id and $name","createdBy":"dev","creationDate":0,"members":[]}')
              redis-cli -h $cnt XADD events '*' header "$header" payload "$payload"
            '')
          ];
        };
      };
    });
}
