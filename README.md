# Scar

> Long live the King!

A poorly opinionated fork of environ.

## Rationale

I've happily used [environ](http://github.com/weavejester/environ) for years and it provides
a nice API to deal with config variables. It is based on the
[Twelve-Factor App's](https://12factor.net/config) for config variables and it boils down to
"use environment variables". I will distinguish between "configs" (the variables we are using
in the code, regardless of their source) and "envars" (Unix environment variables). You read
configs from envars or from other sources, like a file or a config map in `project.clj`
Lately I've hit some problems that don't fit with environ's decisions and this fork addresses
those. Scar is different from environ in the following ways:

1. Config checked into git
2. Spec checking
3. Fully qualified names for config vars
4. Arbitrary edn values as config (not just strings)
5. Temporary stubbing for testing
6. Jar support

### 1. Config checked into git

There are config variables that should remain secret (AWS API keys) and config variables that
are unimportant (HTTP port). I want to keep as much as possible in git. To add secrets to Scar,
you can do it with env vars (like in Environ) or with [env-secrets].

For development and the repl you can put your envs in `project.clj` or `build.boot` just like
with environ:

```clj
;; project.clj
{:dev {:app.server/port 80
       :app.db/url "postgres://username:password@host/database"}}
```

For jars and multiple deploy environments you can use standalone edn resources and
check them into git (See Jar Support).

### 2. Spec checking

Each namespace defines specs for the config variables it will at runtime with `defenv`.
When the app is initialized, the config values are checked against the defined specs, and an
exception is thrown if any is missing or doesn't conform to the spec:

```clj
;; resources/prod/config.edn
{:app.server/port "not-an-integer"
 :app.server/host "www.app.com"}

;; app.server
(defenv
  ::port integer?
  ::host string?)

(defn -main []
  (env/init!)
  (start-server! (env :app.server/host) (env :app.server/port)))

> (-main)
=> ExceptionInfo The following envs didn't conform to their expected values:

	val: "not-an-integer" fails spec: :app.server/port predicate: integer?
```

### 3. Fully qualified names for config vars

Because there could be many `:port`s or `:db-url`s. (But mostly because `clojure.spec`
requires it). The limitation here is that envars are not namespaced. I chose
the following scheme to get around the problem:

```
APP__SERVER___HTTP_PORT => :app.server/http-port

APP__SERVER_TEST___HANDLER_NAME => :app.server-test/handler-name
```

### 4. Arbitrary edn values

Config vars read from edn files, `project.clj`, or `build.boot` are read as edn.
envars are first read as strings, and only read with `edn/read-string`
if they don't conform to their spec as a string.

Example:

```clj
;; app.server

(defenv
  ::http-port integer?
  ::zip string?)

APP__SERVER___HTTP_PORT=3005    # (env :app.server/http-port) => 3005 as int
APP__SERVER___HTTP_PORT="3005"  # (env :app.server/http-port) => 3005 as int

APP__SERVER___ZIP=94114         # (env :app.server/zip) => "94144" as string
APP__SERVER___ZIP="94114"       # (env :app.server/zip) => "94144" as string
```

This prevents us from having to cast HTTP ports to `Integer.` but allows us to use
strings that look like numbers.

Compound edn example:

```
;; ec2.helper

(defenv ::regions (s/and set? (s/* string?)))

;; resources/prod/config.edn
{:ec2.helper/regions #{"us-west1" "us-west2"}}

;; or with envs
EC2__HELPER___REGIONS="#{\"us-west2\" \"us-west1\"}"
```

### 5. Temporary stubbing for testing

During one test run (i.e. `lein test`) it might be interesting to test several values for some
config variable. For example, if the app when run in certain environments should not send
emails it might be useful to wrap some tests with `::send-email true` and others with
`::send-email false` to test the underlying implementation.

```
(env ::send-email) ;; => true

(defn test-payflow []
  (if (env ::send-email)
    (send-email-to-customer!)
    (log-payment!)))

(with-env [::send-email false]
  (test-payflow))
;; no email was sent
```

### 6. Jar Support

environ loads all the config vars once at startup. This is problematic when using uberjars
since any `aot` namespaces (like `environ.core`) will pick up the envars
present at the moment of compilation, not runtime.

For Jars in production, you can use standalone edn files and load them on startup:

```clj
;; resources/prod/config.edn
{:app.server/port 80
 :app.db/url "postgres://username:password@host/database"}}

;; app.server/core
(defn -main []
  (env/init!)
  (start-server! (env ::port)))
```

and then start your jar with

```sh
ENVIRON__CORE___FILE="prod/config.edn" \
java -jar targe/app-standalone.jar
```

## Behavior

Currently, Scar supports four sources, resolved in the following
order:

1. A `.lein-env` file in the project directory
2. A `.boot-env` file on the classpath
3. A resource file passed with `ENVIRON__CORE__FILE`
4. Environment variables

The first two sources are set by the `lein-environ` and `boot-environ`
plugins respectively, and should not be edited manually.

The `.lein-env` file is populated with the content of the `:env` key
in the Leiningen project map. The `.boot-env` file is populated by the
`environ.boot/environ` Boot task.


## Installation

Include the following dependency in your `project.clj` file:

```clojure
:dependencies [[environ "1.1.0"]]
```

If you want to be able to draw settings from the Leiningen project
map, you'll also need the following plugin:

```clojure
:plugins [[lein-environ "1.1.0"]]
```

If you are using the Boot toolchain, you may want to read and write
settings from build pipelines. In *build.boot*, add the dependency:

```clojure
:dependencies '[[boot-environ "1.1.0"]]
```

Then require the environ boot task.

```clojure
(require '[environ.boot :refer [environ]])
```

## Usage

Let's say you have an application that requires a database connection.
Often you'll need three different databases, one for development, one
for testing, and one for production.

Lets pull the database connection details from the key `:database-url`
on the `environ.core/env` map.

```clojure
(require '[environ.core :refer [env]])

(def database-url
  (env :database-url))
```

The value of this key can be set in several different ways. The most
common way during development is to use a local `profiles.clj` file in
your project directory. This file contained a map that is merged with
the standard `project.clj` file, but can be kept out of version
control and reserved for local development options.

```clojure
{:dev  {:env {:database-url "jdbc:postgres://localhost/dev"}}
 :test {:env {:database-url "jdbc:postgres://localhost/test"}}}
```

In this case we add a database URL for the dev and test environments.
This means that if you run `lein repl`, the dev database will be used,
and if you run `lein test`, the test database will be used.

Keywords with a `project` namespace are looked up in the project
map. For example:

```clojure
{:env {:app-version :project/version}}
```

This looks up the `:version` key in the Leiningen project map. You can
view the full project map by using [lein-pprint][].

In the case of Boot, you have the full flexibility of tasks and build
pipelines, meaning that all the following are valid:

```clojure
$ boot environ -e database-url=jdbc:postgres://localhost/dev repl
```

```clojure
(environ :env {:database-url "jdbc:postgres://localhost/dev"})
```

The latter form can be included in custom pipelines and `task-options!'.

The task also creates or updates a `.boot-env` file in the fileset.
This is useful for tasks that create their own pods like
[boot-test][], which won't see changes in the environ vars.

When you deploy to a production environment, you can make use of
environment variables, like so:

```bash
DATABASE_URL=jdbc:postgres://localhost/prod java -jar standalone.jar
```

Or use Java system properties:

```bash
java -Ddatabase.url=jdbc:postgres://localhost/prod -jar standalone.jar
```

Note that Environ automatically lowercases keys, and replaces the
characters "_" and "." with "-". The environment variable
`DATABASE_URL` and the system property `database.url` are therefore
both converted to the same keyword `:database-url`.

[lein-pprint]: https://github.com/technomancy/leiningen/tree/master/lein-pprint
[boot-test]:   https://github.com/adzerk-oss/boot-test


## License

Copyright © 2016 James Reeves

Distributed under the Eclipse Public License, the same as Clojure.
