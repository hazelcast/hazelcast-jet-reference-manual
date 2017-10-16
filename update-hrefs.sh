#!/bin/bash

if [[ $# -lt 1 ]] ; then
    echo 'Updates the hrefs in all *.md files to point to a release version.'
    echo "Syntax: $0 <jet_verson>"
    exit 0
fi

re="(/hazelcast-jet[^/]*/)(blob|tree)/master/"
perl_command="s!$re!/\1\2/$1-maintenance/!"
find docs -name \*.md | xargs egrep -l $re | xargs perl -pi -e $perl_command

search_for="https://hazelcast-l337.ci.cloudbees.com/view/Jet/job/Jet-javadoc/javadoc/"
replace_with="http://docs.hazelcast.org/docs/jet/$1/javadoc/"
find docs -name \*.md | xargs egrep -l $search_for | xargs perl -pi -e "s!$search_for!$replace_with!"
