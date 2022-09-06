#!/usr/bin/env bash

## Bundle.sh
##  Makes a bundle with a library in it so you don't have to do it by hand.

libraryname="EngageCal.Resources"
pathtothisscript="$(cd "$(dirname "$0")";pwd -P)"

zip -jD "$pathtothisscript/library.zip" "$pathtothisscript/$libraryname/"*