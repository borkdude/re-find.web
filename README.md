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

This will write some files to the `dist` directory.

If you haven't, [install the drivers to automate your
browsers](https://github.com/igrishaev/etaoin#installing-drivers) (currently
only Chrome is used in the tests, probably more to come).

Now start the browser tests:

    SERVE=dist PORT=8000 clojure -A:test

## Release

Static files including compiled JS are hosted on Github. This is set up like
described
[here](https://medium.com/linagora-engineering/deploying-your-js-app-to-github-pages-the-easy-way-or-not-1ef8c48424b7):

All the commands below assume that you already have a git project initialized and that you are in its root folder.

```
# Create an orphan branch named gh-pages
git checkout --orphan gh-pages
# Remove all files from staging
git rm -rf .
# Create an empty commit so that you will be able to push on the branch next
git commit --allow-empty -m "Init empty branch"
# Push the branch
git push origin gh-pages
```

Now that the branch is created and pushed to origin, let’s configure the worktree correctly:

```
# Come back to master
git checkout master
# Add dist to .gitignore
echo "dist/" >> .gitignore
git worktree add dist gh-pages
```

That’s it, you can now build your app as usual with npm run build . If you cd to
the dist folder, you will notice that you are now in the gh-pages branch and if
you go back to the root folder, you will go back to master .

To deploy to Github Pages:

```
cd dist
git add .
git commit -m "update build"
git push
```

## License

Copyright © 2018 Michiel Borkent

Distributed under the EPL License, same as Clojure. See LICENSE.
