# Cambada

[![Clojars Project](https://img.shields.io/clojars/v/luchiniatwork/cambada.svg)](https://clojars.org/luchiniatwork/cambada)

Cambada is a packager for Clojure based on `deps.edn` (AKA `tools.deps`). It is
heavily inspired by Leiningen's jar and uberjar tasks and also supports
GraalVM's new native-image making it a one-stop shop for any packaging needed
for your Clojure project.

## Motivation

Leiningen has laid the foundations of what many of us have come to accept as the
standard for Clojure projects. Clojure's `tools.deps` potentially brings new
ideas to the Clojure workflow. Cambada brings some of the great features of
Leiningen to the `tools.deps` workflow.

Cambada's sole focus is packaging. It doesn't have plugins, templates or Clojars
integration. It packages your `deps.edn` progject as one - or all - of:

1. jar
2. uberjar
3. GraalVM native image

On top of Phil Hagelberg's (and so many others') great Leiningen, many thanks to
Dominic Monroe and his work on `pack` as well as Taylor Wood and his
`clj.native-image`. These projects offered a lot of inspiration (and, in some
cases, donor code too).

## Table of Contents

* [Getting Started](#getting-started)
* [Easy Aliases](#easy-aliases)
* [Packaging as a Jar](#packaging-as-a-jar)
* [Packaging as an Uberjar](#packaging-as-an-uberjar)
* [Packaging as a Native Image](#packaging-as-a-native-image)
* [Performance Comparison](#performance-comparison)
* [Bugs](#bugs)
* [Help!](#help)

## Getting Started

Cambada is a simple set of main functions that can be called from a `deps.edn`
alias. The simplest way to have it available in your project is to add an alias
with `extra-deps` to your `deps.edn` file:

``` clojure
{:aliases {:cambada
           {:extra-deps
            {luchiniatwork/cambada
             {:mvn/version "1.0.0"}}}}}
```

Cambada has three main entry points, `cambada.jar`, `cambada.uberjar` and
`cambada.native-image`. Let's say you simply want to create an uberjar:

``` shell
$ clj -R:cambada -m cambada.uberjar
Cleaning target
Creating target/classes
  Compiling ...
Creating target/project-name-1.0.0-SNAPSHOT.jar
Updating pom.xml
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

One of the powers-in-simplicity of `tools.deps` is the ability to define aliases
on `deps.edn`. When we used the alias `cambada` on the section above, we simply
specified it as an dependency to be resolved (therefore the `-R` when calling
`clj`).

You can also be a lot more prescriptive in your aliases by making them do more
work for you. For instance, the alias below will create a versioned uberjar:

``` clojure
{:aliases {:uberjar
           {:extra-deps
            {luchiniatwork/cambada {:mvn/version "1.0.0"}}
            :main-opts ["-m" "cambada.uberjar"
                        "--app-version" "0.5.3"]}}}
```

By having an alias like this `uberjar` one in your `deps.edn` you can simply run
it by using `$ clj -A:uberjar` making it very familiar to those used with `$
lein uberjar`:

``` shell
$ clj -A:uberjar
Cleaning target
Creating target/classes
  Compiling ...
Creating target/project-name-0.5.3.jar
Updating pom.xml
Creating target/project-name-0.5.3-standalone.jar
  Including ...
Done!
```

## Packaging as a Jar

Let's start with an empty project folder:

``` shell
$ mkdir -p myproj/src/myproj/
$ cd myproj
```

Create a `deps.edn` at the root of your project with `cambada.jar` as an alias:

``` clojure
{:aliases {:jar
           {:extra-deps
            {luchiniatwork/cambada {:mvn/version "1.0.0"}}
            :main-opts ["-m" "cambada.jar"
                        "-m" "myproj.core"]}}}
```

Create a simple hello world on a `-main` function at `src/myproj/core.clj`:

``` clojure
(ns myproj.core
  (:gen-class))

(defn -main [& args]
  (println "Hello World!"))
```

Of course, just for safe measure, let's run this hello world via `clj`:

``` shell
$ clj -m myproj.core
Hello World!
```

Then just call the alias from the project's root:

``` shell
$ clj -A:jar
Cleaning target
Creating target/classes
  Compiling myproj.core
Creating target/myproj-1.0.0-SNAPSHOT.jar
Updating pom.xml
Done!
```

Once Cambada is done, you'll have a jar package at `target/`. In order to run
it, you'll need to add Clojure and spec to your class path. The paths will vary
on your system:

``` shell
$ java -cp target/myproj-1.0.0-SNAPSHOT.jar:/Users/<your_user>/.m2/repository/org/clojure/clojure/1.9.0/clojure-1.9.0.jar:/Users/<your_user>/.m2/repository/org/clojure/spec.alpha/0.1.143/spec.alpha-0.1.143.jar myproj.core
Hello World!
```
For a standalone jar file see the uberjar option on the next section.

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

Let's start with an empty project folder:

``` shell
$ mkdir -p myproj/src/myproj/
$ cd myproj
```

Create a `deps.edn` at the root of your project with `cambada.jar` as an alias:

``` clojure
{:aliases {:uberjar
           {:extra-deps
            {luchiniatwork/cambada {:mvn/version "1.0.0"}}
            :main-opts ["-m" "cambada.uberjar"
                        "-m" "myproj.core"]}}}
```

Create a simple hello world on a `-main` function at `src/myproj/core.clj`:

``` clojure
(ns myproj.core
  (:gen-class))

(defn -main [& args]
  (println "Hello World!"))
```

Of course, just for safe measure, let's run this hello world via `clj`:

``` shell
$ clj -m myproj.core
Hello World!
```

Then just call the alias from the project's root:

``` shell
$ clj -A:uberjar
Cleaning target
Creating target/classes
  Compiling myproj.core
Creating target/myproj-1.0.0-SNAPSHOT.jar
Updating pom.xml
Creating target/myproj-1.0.0-SNAPSHOT-standalone.jar
  Including myproj-1.0.0-SNAPSHOT.jar
  Including clojure-1.9.0.jar
  Including spec.alpha-0.1.143.jar
  Including core.specs.alpha-0.1.24.jar
Done!
```

Once Cambada is done, you'll have two jar packages at `target/`. One for a basic
jar and one standalone with all dependencies in it. In order to run it, simply
call it:

``` shell
$ java -jar target/myproj-1.0.0-SNAPSHOT-standalone.jar
Hello World!
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
set (if you don't, use `--graalvm-home`).

Let's start with an empty project folder:

``` shell
$ mkdir -p myproj/src/myproj/
$ cd myproj
```

Create a `deps.edn` at the root of your project with `cambada.jar` as an alias:

``` clojure
{:aliases {:native-image
           {:extra-deps
            {luchiniatwork/cambada {:mvn/version "1.0.0"}}
            :main-opts ["-m" "cambada.native-image"
                        "-m" "myproj.core"]}}}
```

Create a simple hello world on a `-main` function at `src/myproj/core.clj`:

``` clojure
(ns myproj.core
  (:gen-class))

(defn -main [& args]
  (println "Hello World!"))
```

Of course, just for safe measure, let's run this hello world via `clj`:

``` shell
$ clj -m myproj.native-image
Hello World!
```

Then just call the alias from the project's root:

``` shell
$ clj -A:native-image
Cleaning target
Creating target/classes
  Compiling myproj.core
Creating target/myproj
   classlist:   2,810.07 ms
       (cap):   1,469.31 ms
       setup:   2,561.28 ms
  (typeflow):   5,802.45 ms
   (objects):   2,644.17 ms
  (features):      40.54 ms
    analysis:   8,609.18 ms
    universe:     314.28 ms
     (parse):   1,834.84 ms
    (inline):   2,338.45 ms
   (compile):  16,824.24 ms
     compile:  21,435.77 ms
       image:   1,862.44 ms
       write:   1,276.55 ms
     [total]:  38,942.48 ms

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

## Performance Comparison

A quick comparison of the `myproj` hello world as described previously and ran
across different packaging options:

Straight with `clj`:

``` shell
$ time clj -m myproj.core
Hello World!
1.160 secs
```

As a standalone uberjar:

``` shell
$ time java -jar target/myproj-1.0.0-SNAPSHOT-standalone.jar
Hello World!
0.850 secs
```

As a native image:

``` shell
$ time ./target/myproj
Hello World!
0.054 secs
```

Comparing with `clj` as a baseline:

| Method         | Speed in secs | Speed relative to `clj` |
| -------------- | ------------- | ----------------------- |
| `clj`          | `1.160 secs`  | `1x`                    |
| `uberjar`      | `0.850 secs`  | `1.36x`                 |
| `native-image` | `0.054 secs`  | `21.48x`                |

## Bugs

If you find a bug, submit a
[Github issue](https://github.com/luchiniatwork/cambada/issues).

## Help

This project is looking for team members who can help this project succeed!
If you are interested in becoming a team member please open an issue.

## License

Copyright © 2018 Tiago Luchini

Distributed under the MIT License. See LICENSE
