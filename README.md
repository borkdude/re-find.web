# re-find.web

[![CircleCI](https://circleci.com/gh/borkdude/re-find.web/tree/master.svg?style=svg)](https://circleci.com/gh/borkdude/re-find.web/tree/master)

Web interface to [re-find](https://github.com/borkdude/re-find) hosted
[here](https://borkdude.github.io/re-find.web).

## Development

To get an interactive development environment run:

    clojure -A:fig:dev

or with local checkouts of `re-find` and `speculative`:

    clojure -A:fig:local-deps:dev

This will auto compile and send all changes to the browser without the
need to reload. After the compilation process is complete, you will
get a Browser Connected REPL. An easy way to try it is:

    (js/alert "Am I connected?")

and you should see an alert in the browser window.

To clean all compiled files:

    rm -rf target/public

To co-develop integration tests while developing:

    PORT=9500 clojure -A:test:cider-nrepl

The `cider-nrepl` alias is coming from my `~/.clojure/deps.edn`:

```
:cider-nrepl {:extra-deps {nrepl/nrepl {:mvn/version "0.4.5"}
                           refactor-nrepl {:mvn/version "2.4.0"}
                           cider/cider-nrepl {:mvn/version "0.18.0"}}
              :main-opts ["-m" "nrepl.cmdline" "--middleware"
                          "[cider.nrepl/cider-middleware,refactor-nrepl.middleware/wrap-refactor]"]}
```

## Tests

Build a production version of re-find.web:

    script/build

This will write some files to the `/tmp/re-find.web` directory.

If you haven't, [install the drivers to automate your
browsers](https://github.com/igrishaev/etaoin#installing-drivers) (currently
only Chrome is used in the tests, probably more to come).

Now start the browser tests:

    SERVE=/tmp/re-find.web PORT=8000 clojure -A:test

## License

Copyright © 2018 Michiel Borkent

Distributed under the EPL License, same as Clojure. See LICENSE.
