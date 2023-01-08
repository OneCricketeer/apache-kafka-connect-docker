DOCKER_REGISTRY ?= ''
DOCKER_USER ?= cricketeerone
DOCKER_IMAGE ?= apache-kafka-connect
VERSION = 3.2.3

DOCKER_TAG_CONFLUENT_HUB = confluent-hub
DOCKERFILE_CONFLUENT_HUB = Dockerfile.$(DOCKER_TAG_CONFLUENT_HUB)

BUILDX_PLATFORMS ?= linux/arm64

MVN_BUILD_CMD ?= compile jib:build

DOCKER_FQN = $(DOCKER_REGISTRY)$(DOCKER_USER)/$(DOCKER_IMAGE)

build-confluent-hub: build
	@docker build -f $(DOCKERFILE_CONFLUENT_HUB) -t $(DOCKER_FQN):$(VERSION)-$(DOCKER_TAG_CONFLUENT_HUB) .
	@docker tag $(DOCKER_FQN):$(VERSION)-$(DOCKER_TAG_CONFLUENT_HUB) $(DOCKER_FQN):latest-$(DOCKER_TAG_CONFLUENT_HUB)
build:
	@./mvnw -B --errors clean $(MVN_BUILD_CMD) --file pom.xml

# Targets to support arm64
jib-build:  # requires docker login
	@./mvnw -B --errors clean compile jib:build --file pom.xml
buildx-confluent-hub-arm64:
	@docker buildx build -f $(DOCKERFILE_CONFLUENT_HUB) -t $(DOCKER_FQN):$(VERSION)-$(DOCKER_TAG_CONFLUENT_HUB) --push --platform=$(BUILDX_PLATFORMS) .
	@docker buildx build -f $(DOCKERFILE_CONFLUENT_HUB) -t $(DOCKER_FQN):latest-$(DOCKER_TAG_CONFLUENT_HUB) --push --platform=$(BUILDX_PLATFORMS) .

push: build-confluent-hub
	@docker push $(DOCKER_FQN):latest
	@docker push $(DOCKER_FQN):latest-$(DOCKER_TAG_CONFLUENT_HUB)
	@docker push $(DOCKER_FQN):$(VERSION)
	@docker push $(DOCKER_FQN):$(VERSION)-$(DOCKER_TAG_CONFLUENT_HUB)

clean:
	@docker rmi -f $(DOCKER_FQN):latest
	@docker rmi -f $(DOCKER_FQN):latest-$(DOCKER_TAG_CONFLUENT_HUB)
	@docker rmi -f $(DOCKER_FQN):$(VERSION)
	@docker rmi -f $(DOCKER_FQN):$(VERSION)-$(DOCKER_TAG_CONFLUENT_HUB)
