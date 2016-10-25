# Smyrna

Informacje w języku polskim znajdują się w pliku [CZYTAJ.md].

## What is it?

Smyrna is a concordancer and statistical analyzer for metadata-rich corpora in Polish.

## Hacking

You’ll need a snapshot jar of the not-yet-released [Polelum]. Clone the Polelum repo and do a `lein install` there.

To build the Smyrna uberjar, do:

    lein less4j once
    lein uberjar

To start a development environment, first run the ClojureScript and LESS watchdogs:

    lein less4j auto
    lein cljsbuild auto

Now launch a REPL (`lein repl`, `M-x cider-jack-in` from within Emacs or equivalent) and evaluate `(start-server)`.

 [CZYTAJ.md]: ./CZYTAJ.md
 [Polelum]: https://github.com/nathell/polelum

## License

Copyright (C) 2010, 2011, 2016 Daniel Janus, http://danieljanus.pl

Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
