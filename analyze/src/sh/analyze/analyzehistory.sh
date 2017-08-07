path=$1
nemesis_params='"'$(grep "init-ops are:" $path/jepsen.log | head -n 1 | sed -e 's/.*are://')'"'
total_ops_count=$(grep invoke $path/history.txt | wc -l)
read_ops_count=$(grep invoke $path/history.txt | grep read | wc -l)
write_ops_count=$(grep invoke $path/history.txt | grep write | wc -l)
success_ops_count=$(grep :ok $path/history.txt | wc -l)
timeout_ops_count=$(grep timeout $path/history.txt | wc -l)
fail_ops_count=$(grep fail $path/history.txt | wc -l)


echo "path, "'"'$path'"'
for x in nemesis_params total_ops_count read_ops_count write_ops_count success_ops_count timeout_ops_count fail_ops_count
do
  echo "$x , ${!x}"
done


