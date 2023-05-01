DOCKER_REGISTRY ?= 
DOCKER_USER ?= cricketeerone
DOCKER_IMAGE ?= apache-kafka-connect
DOCKER_FQN = $(DOCKER_REGISTRY)$(DOCKER_USER)/$(DOCKER_IMAGE)
VERSION = $(shell ./mvnw org.apache.maven.plugins:maven-help-plugin:3.4.0:evaluate -Dexpression=project.version -q -DforceStdout)

DOCKER_TAG_CONFLUENT_HUB = confluent-hub
DOCKERFILE_CONFLUENT_HUB = Dockerfile.$(DOCKER_TAG_CONFLUENT_HUB)

# Override with 'linux/amd64,linux/arm64' to do multi-platform builds
# Requires running 'docker buildx create --use' to do multi-platform
# ref. https://www.docker.com/blog/how-to-rapidly-build-multi-architecture-images-with-buildx/
BUILDX_PLATFORMS ?= linux/amd64
BUILDX_DO_PUSH ?= 1

# Defaults to build and push. Requires 'docker login'.
# Other supported option: 'compile jib:dockerBuild'
MVN_BUILD_CMD ?= compile jib:build
MAVEN = ./mvnw -B --errors --file pom.xml clean $(MVN_BUILD_CMD)

# Supports arm64; builds and pushes. Refer blog above for setup
# Requires 'docker login'
ifneq (,$(findstring arm64,$(BUILDX_PLATFORMS)))
ifeq ($(BUILDX_DO_PUSH),1)
BUILDX_PUSH = --push --platform=$(BUILDX_PLATFORMS)
endif
buildx-confluent-hub: build-multi-arch
	@docker buildx build -f $(DOCKERFILE_CONFLUENT_HUB) -t $(DOCKER_FQN):$(VERSION)-$(DOCKER_TAG_CONFLUENT_HUB) $(BUILDX_PUSH) .
	@docker buildx build -f $(DOCKERFILE_CONFLUENT_HUB) -t $(DOCKER_FQN):latest-$(DOCKER_TAG_CONFLUENT_HUB) $(BUILDX_PUSH) .
buildx-confluent-hub-alpine: build-multi-arch-alpine
	@docker buildx build -f $(DOCKERFILE_CONFLUENT_HUB)-alpine -t $(DOCKER_FQN):$(VERSION)-alpine-$(DOCKER_TAG_CONFLUENT_HUB) $(BUILDX_PUSH) .
	@docker buildx build -f $(DOCKERFILE_CONFLUENT_HUB)-alpine -t $(DOCKER_FQN):alpine-$(DOCKER_TAG_CONFLUENT_HUB) $(BUILDX_PUSH) .
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
ifneq (,$(findstring arm64,$(BUILDX_PLATFORMS)))
build-multi-arch:  # refer pom.xml for built platforms
	@$(MAVEN) -Pubuntu-multi-arch
build-multi-arch-alpine:  # refer pom.xml for built platforms
	@$(MAVEN) -Palpine-multi-arch
endif

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