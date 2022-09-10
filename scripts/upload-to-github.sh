#!/bin/bash

git config --global user.name stephen-cusi
git config --global user.email magiskcurry@qq.com

generate_readme() # maybe later...
{
echo
}

upload()
{
	# Create new repo with new files
	mkdir deploy
	cp $* deploy/
	cd deploy
	git init
	git remote add deploy https://nillerusr:${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}@github.com/stephen-cusi/srceng-deploy.git
	git checkout -b $DEPLOY_BRANCH
	generate_readme
	git add .
	git commit -m "Latest workflow deploy"
	git push -q --force deploy $DEPLOY_BRANCH &> /dev/null
}

upload $*
