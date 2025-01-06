PHONY: build-services
build-services:
	./mvnw clean package

PHONY: build-docker-images
build-docker-images:
	docker buildx create --use && \
	docker buildx build \
      --platform linux/amd64,linux/arm64 \
      -t csd773/pm.server.admin-pdp-epp:latest \
      --push ./admin-pdp-epp && \
	docker buildx build \
      --platform linux/amd64,linux/arm64 \
      -t csd773/pm.server.resource-pdp:latest \
      --push ./resource-pdp

# Variables
KUBECTL = kubectl
K8S_DIR = ./k8s

.PHONY: apply delete
apply:
	$(KUBECTL) apply -f $(K8S_DIR)/pvc.yaml
	$(KUBECTL) apply -f $(K8S_DIR)/eventstore-deployment.yaml
	$(KUBECTL) apply -f $(K8S_DIR)/admin-pdp-epp-deployment.yaml
	$(KUBECTL) apply -f $(K8S_DIR)/resource-pdp-deployment.yaml
	$(KUBECTL) apply -f $(K8S_DIR)/istio-gateway.yaml

delete:
	$(KUBECTL) delete -f $(K8S_DIR)/istio-gateway.yaml --ignore-not-found
	$(KUBECTL) delete -f $(K8S_DIR)/resource-pdp-deployment.yaml --ignore-not-found
	$(KUBECTL) delete -f $(K8S_DIR)/admin-pdp-epp-deployment.yaml --ignore-not-found
	$(KUBECTL) delete -f $(K8S_DIR)/eventstore-deployment.yaml --ignore-not-found
	$(KUBECTL) delete -f $(K8S_DIR)/pvc.yaml --ignore-not-found


.PHONY: install-istio
install-istio:
	istioctl install --set profile=default -y
	$(KUBECTL) label namespace default istio-injection=enabled
