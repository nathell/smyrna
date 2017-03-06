all: index.html

index.html: manual-pl.md
	pandoc -s -f markdown_github+implicit_figures manual-pl.md --toc -o index.html
