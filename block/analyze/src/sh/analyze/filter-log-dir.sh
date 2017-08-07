DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
 
LOG_DIR="$1"
for f in $(find $LOG_DIR -name jepsen.log -print)
do
  d=${f:0:${#f}-10}
  if [ ! -e "${d}filtered.log" ]
  then
    echo $d
    CMD="$DIR/filter-log.sh $d/jepsen.log > $d/filtered.log"
    echo "$CMD"
    eval "$CMD"
  fi
done
