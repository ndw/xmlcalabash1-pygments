# XML Calabash Pygments extension step

[![Build Status](https://travis-ci.org/ndw/xmlcalabash1-pygments.svg?branch=master)](https://travis-ci.org/ndw/xmlcalabash1-pygments.svg?branch=master)

This repository contains an
[XML Calabash](http://github.com/ndw/xmlcalabash1) 1.x extension
step that invokes the [Pygments](http://pygments.org/) syntax
highlighter. In order for this to work, the `pygmentize` program must
be on your path.

This can’t quite be implemented with `p:exec` in XProc 1.0 because I only want
to process the text nodes inside elements.

## Installation

For standalone installation, get the most recent release from the project
[releases](http://github.com/ndw/xmlcalabash1-pygments/releases) page.
The release distributed there includes
relevant dependencies. Unpack it somewhere on your system and add the
included, top-level `jar` file to your class path.

If you're using Maven, you can also get it from there. Note, however, that
the Maven distribution includes a POM file that identifies other dependencies
that must also be downloaded. You'll need them too, which happens naturally
if you're including the Maven dependency in some other Maven project.
If you just grab the `jar` from Maven and don't get the other dependencies,
you're likely to find that the step doesn't work because of some missing
dependencies.
