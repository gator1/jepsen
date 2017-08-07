for nemesis_between_secs in 5 10 15 20
do
  for nemesis in "partition-random-halves" "partition-node"
  do
    lein run --nemesis "$nemesis" --nemesis-between-secs "$nemesis_between_secs" test
  done
done

