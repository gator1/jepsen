for i in $(docker images -q)
do
     docker history $i | grep -q 8beb272bf3f7 && echo $i
done | sort -u
