<!-- -*- fill-column: 80 -*- -->
# Development Docs

This is a [Scala 3](https://scala-lang.org) project and it uses a pure
functional style building on top of the [typelevel](https://typelevel.org)
ecosystem.


## Dev Environment

A dev environment contains build tools and additional utilities convenient for
developing. These environments can be easily created using
[nix](https://nixos.org/download). Please install nix first as a prerequisite.

There can be many development environments, each is defined in the `devShells`
property in `flake.nix`. There is one called `ci` which is used to run the `sbt
ci` task as a github action. It defines everything (and nothing else) that is
required for the ci task.

You can enter such an environment with `nix develop .#<dev-shell-name>`. To drop
into the environment used by the CI, run:

```
$ nix develop .#ci
```

Once that returns, sbt is available and you can run `sbt` to compile the
project.

The nix setup uses [devshell-tools](https://github.com/eikek/devshell-tools)
providing the vm and container definitions. Services are defined in
`./nix/services.nix`. The `nix/` folder also contains scripts that are made
available in the dev shells.

### Multiple dev shells

The `flake.nix` file defines other dev shells that target specific setups for
running the services locally on your machine.

To run locally, search-api requires a SOLR to connect to and search-provision
additonally needs Redis. These two external dependencies are provided either in
a VM or a container. The container here is based on `systemd-nspawn` and meant
to be used when developing on NixOS (but might work on other systemd linuxes).
The VM can be created on other systems. As an example, here the vm version is
shown.

Drop into the corresponding dev shell:

```
$ nix develop .#vm
```

Now sbt and other utilities are available, as well as scripts to create and
start a VM with the required services. Then it also defines environment
variables to configure the search services to connect to this vm. To create and
start the VM:

```
$ vm-run
```

Do `vm-<TAB>` to find other scripts related to managing the VM. The `vm-run`
command starts the VM in headless mode in your terminal. So to continue, open
another terminal for running the application.

If you have other needs, you can always create another dev shell in `flake.nix`
and tweak it to your likings. It won't affect other developers and ci, since
they use separate definitions.

### Most convenience using dirnev

The `nix develop` command drops you in a bash shell, which might not be your
most favorite shell. It is recommended to use [direnv](https://direnv.net/) for
a much easier and convenient way to get into a development shell.

The project contains a `.envrc-template` file. Copy this to `.envrc` and change
its contents to name a dev environment you would like to use. Now run `direnv
allow` in the source root to allow direnv to load this shell from the
`flake.nix` file whenever you enter this directory.

With this setup, when you `cd` into the project directory, `direnv` will load
the development shell. Direnv is available for many shells and there are also
many integrations with editors.


## Build

For building from source, [sbt](https://scala-sbt.org) > 1.0 and JVM >=17 is
required. When sbt is started it looks into `project/build.properties` to
download the correct version of itself. Obviously, when using a dev shell as
described above, sbt is already there.

Then, from within the sbt shell, run

- `compile` to compile all main sources
- `Test/compile` to compile main and test sources

For creating a package, there are these commands:

- zip files:
  - `search-provision/Universal/packageBin` to create a zip containing the
    search-provision service
  - `search-api/Universal/packageBin` to create a zip containing the search-api
    service
- Docker
  - `search-provision/Docker/publish` to create a docker image for the
    search-provision service
  - `search-api/Docker/publish` to create a docker image for the search-api
    service


## Run from Sources

To run the two services directly from the source tree:

```
sbt:renku-search> reStart
```

This will start the two services, it may fail if the
[environment](#dev-environment) is not setup correctly. The search services
require SOLR and Redis (search-provision only) as an external dependency to
connect to.

The easiest way to get there, is using the `vm` dev shell.

1. Open two terminal sessions, enter the dev shell either via direnv or `nix
   develop .#vm`
2. In one terminal, start the vm with `vm-run`
   - this creates and then runs the VM in headless mode in your terminal
   - you can login with `root:root` or use `vm-ssh` from the other terminal
     session
3. In the other terminal, run `sbt` and inside the sbt shell `reStart`

Now the search services are running since the dev dev shell also defines the
correct environment variables to have the services connect to SOLR and Redis
running inside the VM.

As a quick test, go to <http://localhost:8081/> which should show the openapi
docs for the search service. This is served from inside the VM while the openapi
specification is taken from the locally running search service
<http://localhost:8080/api/search/spec.json>.

SOLR can be reached at <http://localhost:18983> and Redis is port-forwarded from
`localhost:16973`. Look into the `flake.nix` file for more details.

## Use the CLI for testing

The module `search-cli` implements a simple cli tool that can be used to try
things out. For example, it can send redis messages into the stream that will
then be read by the provisioning service and finally populate the SOLR index.

Inside the sbt shell, you can run the cli for example to create a new user:

```
sbt:renku-search> search-cli/run user add --id user1 --namespace user1 --first-name John --last-name Doe
```

## Avro Schema Models

The messages in the Redis stream must conform to the specification in the
[renku-schema](https://github.com/SwissDataScienceCenter/renku-schema)
repository. The module `events` defines the events data types and uses generated
code from the `renku-schema` files. The code is generated as part of the
`compile` build step.

The relevant parts for it are in the `AvroCodeGen` and `AvroSchemaDownload`
build plugins (inside `./project`). The `AvroSchemaDownload` simply clones the
`renku-schema` repository while `AvroCodeGen` wraps around the
[sbt-avrohugger](https://github.com/julianpeeters/sbt-avrohugger) plugin to
generate Scala files.

## Tests

Some tests require solr and redis to be available. The test will by default
start a docker container for each external service, _unless_ environment
variables `NO_SOLR` and `NO_REDIS`, respectively, are present.

If you use a dev shell, these variables are defined so that the tests use either
the container defined in the dev shell or the VM.

In order to reduce run time of the tests, the external services are started
before any test is run and stopped after all tests are finished. The tests must
take care to operate on an isolated part, like creating random solr cores.

If you want to run the tests with the solr and redis setup:

```
sbt:renku-search> dbTests
```

You can run tests without this setup:
```
sbt:renku-search> test
```


## CI

GitHub actions are used to check pull requests. The ci is setup to delegate
everything to sbt. This way you can always run the exact same checks on your
local machine.

The ci actions use the dev shell `ci` and then run `sbt ci` command. The sbt
`ci` command is an alias that simply defines a list of other sbt tasks to run.

This runs the ci action locally:

```
$ nix develop .#ci --command sbt ci
```

If you run the above, make sure *not* to be in a development shell.


## Commits and PRs

Commits should ideally address one semantically unit of work. They should have a
meaningful title and ideally provide a good description about the intention and
motives for the change as well as perhaps certain special mentions. A good,
short and simple guide is
[here](https://tbaggery.com/2008/04/19/a-note-about-git-commit-messages.html).

Multiple nicely crafted commits can go into one pull request, in which case it
is merged with a merge commit. This would be the preferred way, if the PR is not
trivial as it plays better with `git bisect` and reviewing.

It is also fine to create many "fixup" commits in a pull request and
squash-merge it.

For organizing changes and creating the release notes, GitHub pull requests are
used. So the PR title should be written in a nice way, preferably without
spelling errors :-). Each pull request should be labelled with exactly one of
these labels:

- `feature` or `enhancement`
- `fix` or `bug`
- `chore`
- `documentation`
- `dependencies`

More other labels are fine, obviously. For the exact labels affecting the
release notes, look into `.github/release-drafter.yml`.

These labels are then used to draft release notes on GitHub using the
[release-drafter](https://github.com/release-drafter/release-drafter) GitHub
action. If a PR should not show up there, label it with `skip-changelog`. This
might be desired if a feature PR gets fixes *after* it has been merged but
*before* it has been released. It then doesn't make sense to mention everything
found during the initial build of that feature.


## Making a Release

A release is created on GitHub:
https://github.com/SwissDataScienceCenter/renku-search/releases

Click on `Edit` of the latest draft release that has been prepared by the
release-drafter github action. The release notes should already look pretty and
in almost all cases don't need to be tweaked.

Choose a new tag **and prefix it with a `v`**, like in `v0.6.0`. Then hit
*Publish release* and wait…


## Documentation

For changing the `README.md` file, do any modification to `docs/readme.md` and
then run the sbt task `readme/readmeUpdate` (this is also part of the ci chain).
This task compiles and runs the scala code snippets in that file.

The manual about the query dsl is in a separate module to be easier consumed by
the search api module. Changes here must go to
`module/search-query-docs/docs/manual.md`.

Markdown files should also be nicely readable in plain text (the original
intention of the markdown format). Please use a proper setup in your editor to
wrap lines at 70-90 characters. Inidcate the desired line length in the file
header.


### ADRs

The ADR section is for logging decisions from the team that affect this project
in some way and the consequences to design decisions here.
