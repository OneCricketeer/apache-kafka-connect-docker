DOCKER_REGISTRY ?= 
DOCKER_USER ?= cricketeerone
DOCKER_IMAGE ?= apache-kafka-connect
DOCKER_FQN = $(DOCKER_REGISTRY)$(DOCKER_USER)/$(DOCKER_IMAGE)
VERSION = 3.4.0

DOCKER_TAG_CONFLUENT_HUB = confluent-hub
DOCKERFILE_CONFLUENT_HUB = Dockerfile.$(DOCKER_TAG_CONFLUENT_HUB)

# Override with 'linux/amd64,linux/arm64' to do multi-platform builds
# Requires running 'docker buildx create --use' to do multi-platform
# ref. https://www.docker.com/blog/how-to-rapidly-build-multi-architecture-images-with-buildx/
BUILDX_PLATFORMS ?= linux/amd64

# Defaults to build and push. Requires 'docker login'.
# Other supported option: 'compile jib:dockerBuild'
MVN_BUILD_CMD ?= compile jib:build
MAVEN = ./mvnw -B --errors --file pom.xml clean $(MVN_BUILD_CMD)

# Supports arm64; builds and pushes. Refer blog above for setup
# Requires 'docker login'
ifneq (,$(findstring arm64,$(BUILDX_PLATFORMS)))
buildx-confluent-hub: build-multi-arch
	@docker buildx build -f $(DOCKERFILE_CONFLUENT_HUB) -t $(DOCKER_FQN):$(VERSION)-$(DOCKER_TAG_CONFLUENT_HUB) --push --platform=$(BUILDX_PLATFORMS) .
	@docker buildx build -f $(DOCKERFILE_CONFLUENT_HUB) -t $(DOCKER_FQN):latest-$(DOCKER_TAG_CONFLUENT_HUB) --push --platform=$(BUILDX_PLATFORMS) .
#buildx-confluent-hub-alpine: build-multi-arch-alpine # TODO: wait for jre-alpine images to support arm64
#	@docker buildx build -f $(DOCKERFILE_CONFLUENT_HUB)-alpine -t $(DOCKER_FQN):$(VERSION)-alpine-$(DOCKER_TAG_CONFLUENT_HUB) --push --platform=linux/amd64 .
#	@docker buildx build -f $(DOCKERFILE_CONFLUENT_HUB)-alpine -t $(DOCKER_FQN):alpine-$(DOCKER_TAG_CONFLUENT_HUB) --push --platform=linux/amd64 .
else
build-confluent-hub: build
	@docker build -f $(DOCKERFILE_CONFLUENT_HUB) -t $(DOCKER_FQN):$(VERSION)-$(DOCKER_TAG_CONFLUENT_HUB) .
	@docker tag $(DOCKER_FQN):$(VERSION)-$(DOCKER_TAG_CONFLUENT_HUB) $(DOCKER_FQN):latest-$(DOCKER_TAG_CONFLUENT_HUB)
build-confluent-hub-alpine: build-alpine
	@docker build -f $(DOCKERFILE_CONFLUENT_HUB)-alpine -t $(DOCKER_FQN):$(VERSION)-alpine-$(DOCKER_TAG_CONFLUENT_HUB) .
	@docker tag $(DOCKER_FQN):$(VERSION)-alpine-$(DOCKER_TAG_CONFLUENT_HUB) $(DOCKER_FQN):alpine-$(DOCKER_TAG_CONFLUENT_HUB)
endif

build:  # default machine architecture build
	@$(MAVEN)
build-alpine:
	@$(MAVEN) -Palpine
build-multi-arch:  # refer pom.xml for built platforms
	@$(MAVEN) -Pubuntu,ubuntu-multi-arch
# build-multi-arch-alpine:  # refer pom.xml for built platforms; TODO: wait for jre-alpine images to support arm64
#	@$(MAVEN) -Palpine

# required targets if using `mvn jib:dockerBuild`
push: build-confluent-hub
ifneq (jib:build,$(findstring jib:build,$(MVN_BUILD_CMD)))
	@docker push $(DOCKER_FQN):latest
	@docker push $(DOCKER_FQN):$(VERSION)
endif
	@docker push $(DOCKER_FQN):latest-$(DOCKER_TAG_CONFLUENT_HUB)
	@docker push $(DOCKER_FQN):$(VERSION)-$(DOCKER_TAG_CONFLUENT_HUB)
push-alpine: build-confluent-hub-alpine # separated command as jib is overriding 'latest' tag
ifneq (jib:build,$(findstring jib:build,$(MVN_BUILD_CMD)))
	@docker push $(DOCKER_FQN):alpine
	@docker push $(DOCKER_FQN):$(VERSION)-alpine
endif
	@docker push $(DOCKER_FQN):alpine-$(DOCKER_TAG_CONFLUENT_HUB)
	@docker push $(DOCKER_FQN):$(VERSION)-alpine-$(DOCKER_TAG_CONFLUENT_HUB)

clean:
	@docker rmi -f $(DOCKER_FQN):latest
	@docker rmi -f $(DOCKER_FQN):latest-$(DOCKER_TAG_CONFLUENT_HUB)
	@docker rmi -f $(DOCKER_FQN):$(VERSION)
	@docker rmi -f $(DOCKER_FQN):$(VERSION)-$(DOCKER_TAG_CONFLUENT_HUB)
	@docker rmi -f $(DOCKER_FQN):alpine
	@docker rmi -f $(DOCKER_FQN):alpine-$(DOCKER_TAG_CONFLUENT_HUB)
	@docker rmi -f $(DOCKER_FQN):$(VERSION)-alpine
	@docker rmi -f $(DOCKER_FQN):$(VERSION)-alpine-$(DOCKER_TAG_CONFLUENT_HUB)