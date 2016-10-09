# scar

> Long live the King!

A poorly opinionated fork of environ.

## Rationale

[environ](http://github.com/weavejester/environ) provides a nice API to deal with config
variables. It is based on the [Twelve-Factor App's](https://12factor.net/config) which
recommends using environment variables to configure your app in different deployment
environments. I will distinguish between "configs" (the variables we are using
in the code, regardless of their source) and "envars" (Unix environment variables). Your app
reads configs from envars or from other sources, like a file or a config map in `project.clj`
Lately I've hit some problems that don't fit with environ's decisions and this fork addresses
those. scar is different from environ in the following ways:

1. Config checked into git
2. Spec checking
3. Fully qualified names for config vars
4. Arbitrary edn values as config (not just strings)
5. Temporary stubbing for testing
6. Jar support

### 1. Config checked into git

There are config variables that you want to keep secret (AWS API keys) and config variables
that can be public (HTTP port). I want to keep as much as possible in git. To add secrets to
scar, you can do it with envars (like in Environ) or with [env-secrets].

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

Each namespace defines specs for the config variables it will use at runtime with `defenv`.
When the app is initialized, the config values are checked against the defined specs, and an
exception is thrown if any config is missing or doesn't conform to the spec:

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

### 3. Fully qualified names for configs

Because there could be many `:port`s or `:db-url`s. (But mostly because `clojure.spec`
requires it). Requiring configs to be namespaced makes it harder to deal with envars which
are not naturally namespaced. I chose to encode namespaces with the following scheme:

```
APP__SERVER___HTTP_PORT => :app.server/http-port

APP__SERVER_TEST___HANDLER_NAME => :app.server-test/handler-name
```

### 4. Arbitrary edn values

Configs read from edn files, `project.clj`, or `build.boot` are read as edn and can be anything
clojure can read, including custom reader tags. envars are first read as strings, and only
read as edn if they don't conform to their spec as a string.

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

This prevents us from having to cast HTTP ports with `Integer.` and also allows us to use
strings that look like numbers or other edn.

Compound edn example:

```
;; ec2.helper

(defenv ::regions (s/and set? (s/* string?)))

;; resources/prod/config.edn
{:ec2.helper/regions #{"us-west1" "us-west2"}}

;; or with envars
EC2__HELPER___REGIONS="#{\"us-west2\" \"us-west1\"}"
```

### 5. Temporary stubbing for testing

Some functions might need to be tested with different values for a certain config. For example,
if the app should not send emails when run in certain environments it might be useful to wrap
some tests with `::send-email true` and others with `::send-email false` to test the underlying
implementation.

```
(defenv ::send-email boolean?)

(defn test-payflow []
  (if (env ::send-email)
    (send-email-to-customer!)
    (log-payment!)))

;; an email will be sent
(with-env [::send-email true]
  (test-payflow))

;; no email will be sent
(with-env [::send-email false]
  (test-payflow))

```

### 6. Jar Support

environ loads all the config vars once at startup. This is problematic when using uberjars
since any `aot` namespaces will pick up the envars present at the moment of compilation, not
runtime.

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

scar supports four config sources, resolved in the following order:

1. A `.lein-env` file in the project directory
2. A `.boot-env` file on the classpath
3. A resource file passed with `ENVIRON__CORE__FILE`
4. envars

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

Lets pull the database connection details from the key `:app.db/url`
on the `environ.core/env` map.

```clojure
;; app.db
(require '[scar.core :as scar :refer [env defenv]])

(defenv ::url string?)

(defn get-connection []
  (connect! (env ::url)))

(defn -main []
  (scar/init!)
  (start! (get-connection)))
```

Do not use `(env ::url)` in the top level since that won't allow you to use jars.

The value of this key can be set in several different ways. The most
common way during development is to use a local `profiles.clj` file in
your project directory. This file contained a map that is merged with
the standard `project.clj` file, but can be kept out of version
control and reserved for local development options.

```clojure
;; project.clj

:profile {:dev  {:env {:app.db/url "jdbc:postgres://localhost/dev"}}
          :test {:env {:app.db/url "jdbc:postgres://localhost/test"}}}
```

In this case we add a database URL for the dev and test environments.
This means that if you run `lein repl`, the dev database will be used,
and if you run `lein test`, the test database will be used.

For each production deployment, define an edn file under `resources/` with all the configs
for that environment:

```clj
;; resources/prod/configs.edn
{:app.db/url  "jdbc:postgres://localhost/prod"}
```

and then run your Jar with:

```
SCAR__CORE___FILE=prod/configs.edn java -jar standalone.jar
```

This has the advantage of using only *one* envar per deployment environment.

Alternatively, to make quick tests or changes you could override that file with envars:

```bash
SCAR__CORE___FILE=prod/configs.edn \
APP__DB___URL=jdbc:postgres://localhost/prod \
java -jar standalone.jar
```

**Note**: Remember to load the configs by running `(scar.core/init!)` before calling `env`.

## License

Most of the credit goes to [James Reeves](https://github.com/weavejester) who wrote environ
and an incredible number of other libraries. I just bolted new requirements on top of what helpewrote.

Copyright © 2016 James Reeves & Sebastian Bensusan

Distributed under the Eclipse Public License, the same as Clojure.
