# re-find.web

HTML interface to re-find hosted at [re-find.it](https://re-find.it).

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

## License

Copyright © 2018 Michiel Borkent

Distributed under the MIT License. See LICENSE in the root of this project.
