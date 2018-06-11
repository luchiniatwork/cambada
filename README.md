# Cambada

[![Clojars Project](https://img.shields.io/clojars/v/cambada.svg)](https://clojars.org/cambada)

Cambada is a packager for Clojure based on `deps.edn` (AKA `tools.deps`). It is
heavily inspired by Leiningen's jar and uberjar tasks and also supports
GraalVM's new native-image making it a one-stop shop for any packaging needed
for your Clojure project.

## Motivation

Leiningen has laid the foundations of what many of us have come to accept as the
standards for Clojure projects. Clojure's `tools.deps` potentially brings new
ideas to the Clojure workflow. Cambada brings at least a few of the great
features of Leiningen to the `tools.deps` workflow.

Cambada's sole focus is packaging. It doesn't have plugins, templates or Clojars
integration. It packages your `deps.edn` progject as a:

1. jar,
2. uberjar or
3. GraalVM native image

On top of Phil Hagelberg's (and so many others) great Leiningen, many thanks to
Dominic Monroe and his great `pack` as well as Taylor Wood and his
`clj.native-image`. These projects offered a lot of inspiration (and, in some
places, donor code too).

## Table of Contents

* [Getting Started](#getting-started)
* [Easy Aliases](#easy-aliases)
* [Packaging as a Jar](#packaging-as-a-jar)
* [Packaging as an Uberjar](#packaging-as-an-uberjar)
* [Packaging as a Native Image](#packaging-as-a-native-image)
* [Bugs](#bugs)
* [Help!](#help)

## Getting Started

Cambada is a simple set of main functions that can be called from a `deps.edn`
alias. The simplest way to have it available in your project is to add an alias
with `extra-deps` to your `deps.edn` file:

``` clojure
{:aliases {:cambada
           {:extra-deps
            {cambada
             {:mvn/version "0.1.0"}}}}}
```

Cambada has three main entry points, `cambada.jar`, `cambada.uberjar` and
`cambada.native-image`. Let's say you simply want to create an uberjar:

``` shell
$ clj -R:cambada -m cambada.uberjar
Cleaning target
Creating target/classes
  Compiling ...
Creating target/project-name-1.0.0-SNAPSHOT.jar
Creating target/project-name-1.0.0-SNAPSHOT-standalone.jar
  Including ...
Done!
```

Your files will be located at `target/` by default.

All entry points have a few extra configuration options you might be interested
in. For instance:

``` shell
$ clj -R:cambada -m cambada.uberjar --help
Package up the project files and all dependencies into a jar file.

Usage: clj -m cambada.uberjar [options]

Options:
  -m, --main NS_NAME                            The namespace with the -main function
      --app-group-id STRING     project-name    Application Maven group ID
      --app-artifact-id STRING  project-name    Application Maven artifact ID
      --app-version STRING      1.0.0-SNAPSHOT  Application version
      --[no-]copy-source                        Copy source files by default
  -a, --aot NS_NAMES            all             Namespaces to be AOT-compiled or `all` (default)
  -d, --deps FILE_PATH          deps.edn        Location of deps.edn file
  -o, --out PATH                target          Output directory
  -h, --help                                    Shows this help
```

Do try `--help` for `cambada.jar` and `cambada.native-image` if you are
interested or refer to the sections below.

## Easy Aliases

One of the simple powers of tools.deps is the ability to define aliases on
`deps.edn`. When we used the alias `cambada` above, we simply specified it as an
extra dependency to be resolved (therefore the `-R` when calling `clj`).

You can also be a lot more perscritive in your aliases making them do more work
for you. For instance, the alias below will create a versioned uberjar:

``` clojure
{:aliases {:uberjar
           {:extra-deps
            {cambada {:local/root "../cambada"}}
            :main-opts ["-m" "cambada.uberjar"
                        "--app-version" "0.5.3"]}}}
```

By having an alias like this in your `deps.edn` you can simply run it by using
`clj -A:uberjar` making it familiar to those used with `lein uberjar`:

``` shell
$ clj -A:uberjar
Cleaning target
Creating target/classes
  Compiling ...
Creating target/project-name-0.5.3.jar
Creating target/project-name-0.5.3-standalone.jar
  Including ...
Done!
```

## Packaging as a Jar

The entry point `cambada.jar` is your starting place for creating a simple jar:

``` shell
$ clj -R:cambada -m cambada.jar
Cleaning target
Creating target/classes
  Compiling ...
Creating target/project-name-1.0.0-SNAPSHOT.jar
Done!
```

You can specify the following options for `cambada.jar`:


``` text
  -m, --main NS_NAME                            The namespace with the -main function
      --app-group-id STRING     project-name    Application Maven group ID
      --app-artifact-id STRING  project-name    Application Maven artifact ID
      --app-version STRING      1.0.0-SNAPSHOT  Application version
      --[no-]copy-source                        Copy source files by default
  -a, --aot NS_NAMES            all             Namespaces to be AOT-compiled or `all` (default)
  -d, --deps FILE_PATH          deps.edn        Location of deps.edn file
  -o, --out PATH                target          Output directory
  -h, --help                                    Shows this help
```

These options should be quite self-explanatory and the defaults are
hopefully sensible enough for most of the basic cases. By default
everything gets AOT-compiled and sources are copied to the resulting jar.

For those used to Leiningen, the application's group ID, artifact ID
and version are not extracted from `project.clj` (since it's assumed
you don't have a `project.clj` in a `deps.edn` workflow). Therefore,
you must specify these expressively as options.

## Packaging as an Uberjar

The entry point `cambada.uberjar` is your starting place for creating
a standalone jar (one with all of your porject's dependencies in it).

``` shell
$ clj -R:cambada -m cambada.uberjar
Cleaning target
Creating target/classes
  Compiling ...
Creating target/project-name-1.0.0-SNAPSHOT.jar
Creating target/project-name-1.0.0-SNAPSHOT-standalone.jar
  Including ...
Done!
```

`cambada.uberjar` has exactly the same options and defaults as
`cambada.jar` (see above for more details).

## Packaging as a Native Image

By using GraalVM we now have the option of packaging everything AOT
compiled as a native image.

If you want to use this feature, make sure to [download and install
GraalVM](https://www.graalvm.org/).

You will need to set your `GRAALVM_HOME` environment variable to point
to where GraalVM is installed. Alternatevely you can call
`cambada.native-image` with the argument `--graalvm-home` pointing to it.

The entry point for native image packaging is
`cambada.native-image`. Let's assume your `GRAALVM_HOME` variable is
set and you have a simple `-main` function in your
`src/myproj/core.clj`:

``` clojure
(ns myproj.core
  (:gen-class))

(defn -main [& args]
  (println "Hello World!"))
```

Let's tell Cambada that your main function is at `myproj.core`:

``` shell
$ clj -R:cambada -m cambada.native-image -m myproj.core
Cleaning target
Creating target/classes
  Compiling ...
Creating target/myproj
  ...
Done!
```

Once Cambada is done, you'll have an executable package at `target/`:

``` shell
$ ./target/myproj
Hello World!
```

Internally Cambada prefers not to spawn native-image servers
(`--no-server` option) and also pushes unsupported elements exceptions
to the runtime (`-H:+ReportUnsupportedElementsAtRuntime`
option). These have been decisions at the time of this writing in
order to compile the most amount of Clojure code possible considering
the current status of GraalVM.

Extra options can be sent to GraalVM's packager by using Cambada's
`--graalvm-opt` option.

## Bugs

If you find a bug, submit a
[Github issue](https://github.com/luchiniatwork/cambada/issues).

## Help

This project is looking for team members who can help this project succeed!
If you are interested in becoming a team member please open an issue.

## License

Copyright © 2017 Tiago Luchini

Distributed under the MIT License. See LICENSE
