#!/usr/bin/env bash

GIT_REPO=https://github.com/tbouron/clocker
GIT_BRANCH_TARGET=gh-pages
GIT_CLONE=_clone

if [ -d "$GIT_CLONE" ]; then
    pushd $GIT_CLONE
    git fetch origin
    git checkout origin/$GIT_BRANCH_TARGET
    git checkout -B $GIT_BRANCH_TARGET
    popd
else
    git clone $GIT_REPO $GIT_CLONE
    pushd $GIT_CLONE
    git checkout origin/$GIT_BRANCH_TARGET
    git checkout -B $GIT_BRANCH_TARGET
    popd
fi

bundle exec jekyll build
rsync -av --delete --checksum --exclude=.git ./_site/ ./$GIT_CLONE/
pushd $GIT_CLONE
    git add .
    git commit -m "Release new version of the documentation"
    read push
    if [ "$push" = "y" ]; then
        echo "Pushing ... "
        git push origin $GIT_BRANCH_TARGET
    fi
popd
