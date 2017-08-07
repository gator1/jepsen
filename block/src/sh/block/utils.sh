RELDIR=$(dirname "${BASH_SOURCE[0]}")
DIR=$( cd -P "$RELDIR" && pwd )

source $DIR/env.sh

trim() {
  TMP="$1"
  echo "$1" | sed -e 's/^[[:space:]]*//' -e 's/[[:space:]]*$//'
}

my_ips() {
  /sbin/ifconfig | grep -Eo 'inet (addr:)?([0-9]*\.){3}[0-9]*' | grep -Eo '([0-9]*\.){3}[0-9]*'
}

in_strs() {
  for item in $2
  do
    TMP_ITEM=$(trim "$item")
    if [ "" = "$TMP_ITEM" ]
    then
      continue
    elif [ "$1" = "$TMP_ITEM" ]
    then
      echo "true"
      return
    fi
  done
  echo "false"
}

do_it() {
  echo "$1"
  DRY="$2"
  if [ "true" != "$DRY" ]
  then
    eval "$1"
  fi
}

# cp_to_ips <from path> <to path> <user> <host ips> <dry run if true>
scp_to_ips() {
  local_ips="$(my_ips)"
  USER="$3"
  DRY="$5"
  for h in $4
  do
    h1=$(trim $h)
    if [ "true" != $(in_strs $h1 "$local_ips") ]
    then
      CMD="scp $1 $USER@$h:$2"
      do_it "$CMD" "$DRY"
    fi
  done
}


# ssh_run <cmd> <user> <host ips> <dry run if true>
ssh_run() {
  local_ips="$(my_ips)"
  REMOTE_CMD="$1"
  USER="$2"
  DRY="$4"
  for h in $3
  do
    h1=$(trim $h)
    if [ "true" != $(in_strs $h1 "$local_ips") ]
    then
      CMD="ssh $USER@$h "
      CMD=${CMD}'"'
      CMD="${CMD}${REMOTE_CMD}"
      CMD=${CMD}'"'
      do_it "$CMD" "$DRY"
    fi
  done
}
