for i in $(docker images -q)
do
     docker history $i | grep -q c4675fe8d6f5 && echo $i
done | sort -u
