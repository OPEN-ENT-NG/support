#!/bin/bash

MVN_OPTS="-Duser.home=/var/maven -T 4"

# If DEBUG env var is set to "true" then set -x to enable debug mode
if [ "$DEBUG" == "true" ]; then
	set -x
	EDIFICE_CLI_DEBUG_OPTION="--debug"
else
	EDIFICE_CLI_DEBUG_OPTION=""
fi

if [ ! -e node_modules ]
then
  mkdir node_modules
fi

case `uname -s` in
  MINGW*)
    USER_UID=1000
    GROUP_UID=1000
    ;;
  *)
  if [ -z ${USER_UID:+x} ]
    then
      USER_UID=`id -u`
      GROUP_GID=`id -g`
  fi
esac

init() {
  me=`id -u`:`id -g`
  echo "DEFAULT_DOCKER_USER=$me" > .env

  # If CLI_VERSION is empty set to latest
  if [ -z "$CLI_VERSION" ]; then
    CLI_VERSION="latest"
  fi
  # Create a build.compose.yaml file from following template
  cat <<EOF > build.compose.yaml
services:
  edifice-cli:
    image: opendigitaleducation/edifice-cli:$CLI_VERSION
    user: "$DEFAULT_DOCKER_USER"
EOF
  # Copy /root/edifice from edifice-cli container to host machine
  docker compose -f build.compose.yaml create edifice-cli
  docker compose -f build.compose.yaml cp edifice-cli:/root/edifice ./edifice
  docker compose -f build.compose.yaml rm -fsv edifice-cli
  rm -f build.compose.yaml
  chmod +x edifice
  ./edifice version $EDIFICE_CLI_DEBUG_OPTION
}

if [ ! -e .env ]; then
  init
fi

clean () {
  echo "Cleaning..."
  if [ "$NO_DOCKER" = "true" ] ; then
    mvn clean
  else
    docker compose run --rm maven mvn $MVN_OPTS clean
  fi
  echo "Clean done!"
}

build () {
  echo "Building..."
  if [ "$NO_DOCKER" = "true" ] ; then
    mvn install -DskipTests  -Dmaven.test.skip=true
  else
    docker compose run --rm maven mvn $MVN_OPTS install -DskipTests  -Dmaven.test.skip=true
  fi
  echo "Build done!"
}

test () {
  docker compose run --rm maven mvn $MVN_OPTS test
}


publish() {
  echo "Publishing..."
  if [ "$NO_DOCKER" = "true" ] ; then
    version=`mvn help:evaluate -Dexpression=project.version -q -DforceStdout`
    level=`echo $version | cut -d'-' -f3`
    case "$level" in
      *SNAPSHOT) export nexusRepository='snapshots' ;;
      *)         export nexusRepository='releases' ;;
    esac

    mvn -DrepositoryId=ode-$nexusRepository -DskipTests deploy
  else
    version=`docker compose run --rm maven mvn $MVN_OPTS help:evaluate -Dexpression=project.version -q -DforceStdout`
    level=`echo $version | cut -d'-' -f3`
    case "$level" in
      *SNAPSHOT) export nexusRepository='snapshots' ;;
      *)         export nexusRepository='releases' ;;
    esac

    docker compose run --rm  maven mvn $MVN_OPTS -DrepositoryId=ode-$nexusRepository -DskipTests --settings /var/maven/.m2/settings.xml deploy
  fi
  echo "Publish done!"

}

for param in "$@"
do
  case $param in
    init)
      init
      ;;
    clean)
      clean
      ;;
    build)
      build
      ;;
    test)
      test
      ;;
    watch)
      watch
      ;;
    publish)
      publish
      ;;
    *)
      echo "Invalid argument : $param"
  esac
  if [ ! $? -eq 0 ]; then
    exit 1
  fi
done
