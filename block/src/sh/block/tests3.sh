for time_limit_secs in 500 600 900
  do
  for nemesis_between_secs in 100 200 300 400
  do
    for nemesis_after_secs in 99 199 299
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

