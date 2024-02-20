{

  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/release-23.11";
    flake-utils.url = "github:numtide/flake-utils";
  };

  outputs = inputs@{ self, nixpkgs, flake-utils }:
    {
      overlays.default = final: prev: {
        solr = self.packages.${prev.system}.solr;
      };
      nixosConfiguration.dev-vm = nixpkgs.lib.nixosSystem {
        system = flake-utils.lib.system.x86_64-linux;
        specialArgs = { inherit inputs; };
        modules = [
          ({ lib, config, ... }: {
            nixpkgs.overlays = [
              self.overlays.default
            ];
          })
          (import ./nix/solr-module.nix)
          (import ./nix/dev-vm.nix)
        ];
      };

    } // flake-utils.lib.eachDefaultSystem (system:
      let pkgs = nixpkgs.legacyPackages.${system};
      in {
        formatter = pkgs.nixfmt;
        packages = { solr = pkgs.callPackage (import ./nix/solr.nix) { }; };

      });
}
