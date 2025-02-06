PHONY: build-services
build-services:
	cd src && ./mvnw clean package

PHONY: build-docker-images
build-docker-images:
	docker buildx create --use && \
	docker buildx build \
      --platform linux/amd64,linux/arm64 \
      -t csd773/pm.server.admin-pdp-epp:latest \
      --push ./src/admin-pdp-epp && \
	docker buildx build \
      --platform linux/amd64,linux/arm64 \
      -t csd773/pm.server.resource-pdp:latest \
      --push ./src/resource-pdp

PHONY: build
build: build-services build-docker-images

# Variables
KUBECTL = kubectl
K8S_DIR = ./k8s

.PHONY: apply delete
apply:
	$(KUBECTL) apply -f $(K8S_DIR)/

delete:
	$(KUBECTL) delete -f $(K8S_DIR)/


.PHONY: install-istio
install-istio:
	istioctl install --set profile=default -y
	$(KUBECTL) label namespace default istio-injection=enabled
#	$(KUBECTL) get crd gateways.gateway.networking.k8s.io &> /dev/null || \
#		{ $(KUBECTL) kustomize "github.com/kubernetes-sigs/gateway-api/config/crd?ref=v1.2.0" | $(KUBECTL) apply -f -; }
	$(KUBECTL) get pods -n istio-system
