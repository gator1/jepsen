Dockerized Jepsen
=================

This docker image attempts to simplify the setup required by ceph.
It is intended to be used by a CI tool or anyone with docker who wants to try ceph and jepsen themselves.

It contains a multi-nodes ceph on docker setup amd all the jepsen dependencies and code. It uses [docker-in-docker](https://github.com/gators/dind, cloned from jpetazzo) to all nodes for ceph and a jepsen controller.
containers used by Jepsen.  

To start run (note the required --privileged flag)
Got my own with the jepsen latest from gator1, me!

````
    docker run --privileged -t -i gators/ceph
    or
    docker run -v /home/gary/docker:/docker --privileged -t -i gators/ceph
    the above map local /home/gary/docker to container's /docker
````

Building the docker image
=========================

Alternatively, you can build the image yourself. This is a multi-step process, mainly because [docker doesn't let you build with --privileged operations](https://github.com/docker/docker/issues/1916)

1.  From this directory run 

    ````
	docker build -t ceph .
    ````

2.  Start the container and run build-dockerized-jepsen.sh

    ````
    docker run --privileged -t -i jepsen

    > build-dockerized-jepsen.sh
    ````

3.  From another window commit the updated image

    ````
    docker commit {above container-id} jepsen
    ````
    
4.  With the final image created, you can create a container to run Jepsen from

    ```
    docker run --privileged -t -i jepsen
    ```

U docker in docker (dind) has docker version 1.5 which can no longer pull images from docker hub 
=========================
    docker save tutum/debian:jessie > debian.tar to save the image into a file.
    after the docker build and run, ssh or -v the debain.tar on the running
    docker container and do docker load (less) debian.tar and  load the image.
    retag if necessary

Run tmux in the docker image to get multiple Windows
=========================

    install tmux in the image for multiple terminals (kind of)
    sudo apt-get install tmux

    issue:
    tmux

    then resize your window to be double width.

    Then:

    CTRL+b, %

    Then you can use:
    CTRL+b, <- and -> 

    (Left arrow and right arrow)

    to switch windows. 

Upload image to docker hub so that it can be used as gators/jepsen
=========================

https://docs.docker.com/engine/getstarted/step_six/

    Tag and push the image
    If you don’t already have a terminal open, open one now:

    Go back to your command line terminal.
    At the prompt, type docker images to list the images you currently have:
    $ docker images
        REPOSITORY          TAG                 IMAGE ID            CREATED    SIZE
        jepsen              latest              6dc563b59188        14 minutes ago 1.14 GB
        gators/jepsen       latest              10fc1c76450a        7 days ago 1.14 GB
        gators/jepsen       mingli              10fc1c76450a        7 days ago 1.14 GB
        gators/jepsen       simulation          10fc1c76450a        7 days ago 1.14 GB
        tutum/debian        jessie              7b5da25fa27e        11 months ago 155 MB
        kojiromike/dind     latest              227220c311f8        2 years ago 350 MB

    Find the IMAGE ID for your jepsen image.
      In this example, the id is 6dc563b59188.
      Notice that currently, the REPOSITORY shows the repo name jepsen but
      not the namespace. You need to include the namespace for Docker Hub to
      associate it with your account. The namespace is the same as your Docker
      Hub account name. You need to rename the image to
      gators/jepsen.

    Use IMAGE ID and the docker tag command to tag your jepsen image.
      The command you type looks like this:
      Docker tag command
      Of course, your account name will be your own. So, you type the command
      with your image’s ID and your account name and press RETURN.
       $ docker tag 6dc563b59188 gators/jepsen:latest

    Type the docker images command again to see your newly tagged image.
        $ docker images
        REPOSITORY          TAG                 IMAGE ID            CREATED             SIZE
        gators/jepsen       latest              6dc563b59188        29 minutes ago      1.14 GB
        jepsen              latest              6dc563b59188        29 minutes ago      1.14 GB
        gators/jepsen       mingli              10fc1c76450a        7 days ago          1.14 GB
        gators/jepsen       simulation          10fc1c76450a        7 days ago          1.14 GB
        tutum/debian        jessie              7b5da25fa27e        11 months ago       155 MB
        kojiromike/dind     latest              227220c311f8        2 years ago         350 MB

    Use the docker login command to log into the Docker Hub from the command line.
        The format for the login command is:
        docker login
        When prompted, enter your password and press enter. So, for
           example:
              $ docker login
                 Login with your Docker ID to push and pull images from Docker
                 Hub. If you don't have a Docker ID, head over to
                 https://hub.docker.com to create one.
                  Username:
                   Password:
                    Login Succeeded

    Type the docker push command to push your image to your new repository.
       $ docker push gators/jepsen:tagname (without :tagname default to latest)
            The push refers to a repository
               [gators/jepsen] (len: 1)

