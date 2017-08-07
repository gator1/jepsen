for time_limit_secs in 100 200
  do
  for nemesis_between_secs in 5 10 15 20 40 60
  do
    for nemesis_after_secs in 4 9 14 19 39 59
    do
      nemesis="partition-node"
      no_heal=false
      cut_once=false
      lein run --nemesis "$nemesis" --nemesis-between-secs "$nemesis_between_secs" \
        --no-heal "$no_heal" --cut-once "$cut_once" \
        --nemesis-after-secs "$nemesis_after_secs" --time-limit-secs "$time_limit_secs" test
    done
  done
done

