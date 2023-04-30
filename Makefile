DOCKER_REGISTRY ?= ''
DOCKER_USER ?= cricketeerone
DOCKER_IMAGE ?= apache-kafka-connect
DOCKER_FQN = $(DOCKER_REGISTRY)$(DOCKER_USER)/$(DOCKER_IMAGE)
VERSION = 3.3.2

DOCKER_TAG_CONFLUENT_HUB = confluent-hub
DOCKERFILE_CONFLUENT_HUB = Dockerfile.$(DOCKER_TAG_CONFLUENT_HUB)

# Override with 'linux/amd64,linux/arm64' to do multi-platform builds
# Requires running 'docker buildx create --use' to do multi-platform
# ref. https://www.docker.com/blog/how-to-rapidly-build-multi-architecture-images-with-buildx/
BUILDX_PLATFORMS ?= linux/amd64

# TODO: fix 'package' phase
# Defaults to build and push. Requires 'docker login'.
# Other supported option: 'compile jib:dockerBuild'
MVN_BUILD_CMD ?= compile jib:build

# Supports arm64; builds and pushes. Refer blog above for setup
# Requires 'docker login'
ifneq (,$(findstring arm64,$(BUILDX_PLATFORMS)))
buildx-confluent-hub: build-multi-arch
	@docker buildx build -f $(DOCKERFILE_CONFLUENT_HUB) -t $(DOCKER_FQN):$(VERSION)-$(DOCKER_TAG_CONFLUENT_HUB) --push --platform=$(BUILDX_PLATFORMS) .
	@docker buildx build -f $(DOCKERFILE_CONFLUENT_HUB) -t $(DOCKER_FQN):latest-$(DOCKER_TAG_CONFLUENT_HUB) --push --platform=$(BUILDX_PLATFORMS) .
else
build-confluent-hub: build
	@docker build -f $(DOCKERFILE_CONFLUENT_HUB) -t $(DOCKER_FQN):$(VERSION)-$(DOCKER_TAG_CONFLUENT_HUB) .
	@docker tag $(DOCKER_FQN):$(VERSION)-$(DOCKER_TAG_CONFLUENT_HUB) $(DOCKER_FQN):latest-$(DOCKER_TAG_CONFLUENT_HUB)
endif

build:  # default machine architecture build
	@./mvnw -B --errors clean $(MVN_BUILD_CMD) --file pom.xml
build-multi-arch:  # refer pom.xml for built platforms
	@./mvnw -B --errors -Pmulti-arch clean $(MVN_BUILD_CMD) --file pom.xml

# required target if using `mvn jib:dockerBuild`
push: build-confluent-hub
	@docker push $(DOCKER_FQN):latest
	@docker push $(DOCKER_FQN):$(VERSION)
	@docker push $(DOCKER_FQN):latest-$(DOCKER_TAG_CONFLUENT_HUB)
	@docker push $(DOCKER_FQN):$(VERSION)-$(DOCKER_TAG_CONFLUENT_HUB)

clean:
	@docker rmi -f $(DOCKER_FQN):latest
	@docker rmi -f $(DOCKER_FQN):latest-$(DOCKER_TAG_CONFLUENT_HUB)
	@docker rmi -f $(DOCKER_FQN):$(VERSION)
	@docker rmi -f $(DOCKER_FQN):$(VERSION)-$(DOCKER_TAG_CONFLUENT_HUB)
