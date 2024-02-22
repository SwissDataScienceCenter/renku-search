{ buildNpmPackage, nodejs, cypress, fetchFromGitHub }:

buildNpmPackage {
  name = "swagger-ui";

  buildInputs = [ nodejs cypress ];

  src = fetchFromGitHub {
    owner = "swagger-api";
    repo = "swagger-ui";
    rev = "v5.11.7";
    sha256 = "sha256-p/KCG+jf0J+KbZpBHwKm4ypt+APpq9L8jzseG0YssSg=";
  };

  npmDepsHash = "sha256-I6InBOAil6F4zFz7YsysDsrqTmhUzkSuEOy9fyBmisA=";

  #https://gist.github.com/r-k-b/2485f977b476aa3f76a47329ce7f9ad4
  CYPRESS_INSTALL_BINARY = "0";
  CYPRESS_RUN_BINARY = "${cypress}/bin/Cypress";

  npmBuildScript = "build:standalone";
}
