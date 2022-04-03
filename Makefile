all: css

resources/public/css/root.css: src/css/main.scss
	mkdir -p $(dir $@)
	sassc $< $@

css: resources/public/css/root.css

aot:
	mkdir -p classes
	clojure -M:prod -e "(compile 'smyrna.server)"

clean:
	rm -rf classes target resources/public/js resources/public/css

js-dist:
	shadow-cljs release app

uberjar: css js-dist aot
	clojure -M:uberdeps

.PHONY: aot css js-dist uberjar
