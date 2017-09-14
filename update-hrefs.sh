#!/bin/bash

if [[ $# -lt 2 ]] ; then
    echo 'Updates the git branch name in hrefs in all *.md files.'
    echo "Syntax: $0 <search_branch_name> <replace_with_branch_name>"
    exit 0
fi

re="(/hazelcast-jet[^/]*/)(blob|tree)/$1/"
perl_command="s!$re!/\1\2/$2/!"
find docs -name \*.md | xargs egrep -l $re | xargs perl -pi -e $perl_command
