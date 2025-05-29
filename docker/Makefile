up:
	docker-compose up eventstore -d
down:
	docker-compose down
restart: down up
build:
	cd ../src && \
	./mvnw clean package -DskipTests && \
	cd ../docker && \
	docker-compose build
all: restart