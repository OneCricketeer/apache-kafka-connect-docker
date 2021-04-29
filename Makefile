DOCKER_REGISTRY ?= ''
DOCKER_USER ?= cricketeerone
DOCKER_IMAGE ?= apache-kafka-connect
VERSION = 2.8.0

DOCKER_FQN = $(DOCKER_REGISTRY)$(DOCKER_USER)/$(DOCKER_IMAGE)

build-confluent-hub: build
	@docker build -f Dockerfile.confluent-hub -t $(DOCKER_FQN):$(VERSION)-confluent-hub .
	@docker tag $(DOCKER_FQN):$(VERSION)-confluent-hub $(DOCKER_FQN):latest-confluent-hub
build:
	@./mvnw -B --errors clean package --file pom.xml

push: build
	@docker push $(DOCKER_FQN):latest
	@docker push $(DOCKER_FQN):latest-confluent-hub
	@docker push $(DOCKER_FQN):$(VERSION)
	@docker push $(DOCKER_FQN):$(VERSION)-confluent-hub

clean:
	@docker rmi -f $(DOCKER_FQN):latest
	@docker rmi -f $(DOCKER_FQN):latest-confluent-hub
	@docker rmi -f $(DOCKER_FQN):$(VERSION)
	@docker rmi -f $(DOCKER_FQN):$(VERSION)-confluent-hub
