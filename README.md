# Policy Machine PDP
The Policy Machine PDP is an event-driven gRPC microservice implementation of the NGAC standard architecture. The gRPC 
service and message definitions can be found in the [policy-machine-protos](https://github.com/usnistgov/policy-machine-protos) repo.

## Quickstart
To start the PDP services using docker-compose:
```shell
cd docker && docker-compose up -d
```

This will create the following services:
- `admin-pdp-epp`, port 50052 
- `resource-pdp`, port 50051
- `eventstore`, port 2113 

See client examples for the `resource-pdp` and `admin-pdp-epp` in the [client examples project](./examples/client)

## gRPC Headers

There is no authentication mechanism implemented in this server. The gRPC services accept the following headers:

- `x-pm-user`: The username as it is in the NGAC graph.
- `x-pm-user-attrs` A json array of attribute names that the user is assigned to. The user itself does not need to exist in the graph.
- `x-pm-process` An ID representing the process a user is operating.

One of `x-pm-user` and `x-pm-user-attrs` is required. `x-pm-process` is optional.

To set the headers in the client side gRPC:
```Java
String[] attributes = new String[]{"ua1", "ua2"};
Metadata metadata = new io.grpc.Metadata();
Metadata.Key<String> userKey = Metadata.Key.of("x-pm-user-attrs", Metadata.ASCII_STRING_MARSHALLER);
metadata.put(userKey, new ObjectMapper().writeValueAsString(attributes));

ResourceAdjudicationServiceGrpc.ResourceAdjudicationServiceBlockingStub blockingStub = ResourceAdjudicationServiceGrpc.newBlockingStub(channel)
		.withInterceptors(MetadataUtils.newAttachHeadersInterceptor(metadata));
```

## Services
### admin-pdp-epp
The `admin-pdp-epp` service provides an API to update policy data. The API implements a command pattern where each known
administrative command is defined as a message. There is also a generic command with a name and args to execute custom
operations and routines. There are 2 EPP services. One is intended to be used by the `resource-pdp` and respond specifically
to resource operation events. The other is intended to be used by the PEP and PDP to handle both policy update and general
events. **The policy is persisted in an embedded Neo4j instance.**

#### Spring Boot Configuration Options

```yaml
pm:
  pdp:
    admin:
      # The file path to the policy file used to bootstrap the PDP.
      bootstrap-file-path: "./src/admin-pdp-epp/src/main/resources/policy.json"
      # The user bootstrapping the policy.
      bootstrap-user: "u1"
      # Path to store Neo4j policy locally.
      neo4j-db-path: "neo4j/data"
      # Name of the EventStoreDB consumer group.
      esdb-consumer-group: admin-pdp-epp-cg
      # Interval for writing snapshots to the event store.
      snapshot-interval: 1000
      # Shutdown the server once the bootstrap process is complete.
      shutdown-after-bootstrap: false
    esdb:
      # Event store hostname.
      hostname: localhost
      # Event store port.
      port: 2113
      # Name of the event store stream.
      event-stream: pm-events-v1
      # Name of the event store stream for snapshots.
      snapshot-stream: pm-snapshot-v1
```

### resource-pdp
The `resource-pdp` is a read only API with a method to adjudicate resource operations. Since resource operations
can be subject to obligations, the `ersource-pdp` can be configured to send event contexts to the `admin-pdp-epp` for
processing in the `epp` service.

#### Spring Boot Configuration Options
```yaml
pm:
  pdp:
    resource:
      # Admin PDP hostname.
      admin-hostname: localhost
      # Admin PDP port.
      admin-port: 50052
      # The mode of the EPP client: ASYNC, SYNC, or DISABLED. Default is ASYNC.
      epp-mode: sync
      # The timeout that the EPPClient will use when waiting for the current revision to catch up
      # to the side effect revision returned by the EPP. This value will be ignored if epp-mode is ASYNC.
      epp-side-effect-timeout: 10
    esdb:
      # Event store hostname.
      hostname: localhost
      # Event store port.
      port: 2113
      # Name of the event store stream.
      event-stream: pm-events-v1
      # Name of the event store stream for snapshots.
      snapshot-stream: pm-snapshot-v1
```

### eventstore
[EventStoreDB](https://github.com/kurrent-io/KurrentDB) serves as the policy data store. Policy updates are stored in 
an immutable log of events. Services can subscribe to the events to stay up to date with policy modifications.

## Data Flow Diagrams
Below are diagrams illustrating the different ways data can flow through the distributed Policy Machine PDP components.

### Overall Diagram
![policy-machine-server 1](https://github.com/user-attachments/assets/56c47730-554e-4a96-9e97-cd36e7f49058)

### Admin Operation Flow
![policy-machine-server-ao 2](https://github.com/user-attachments/assets/b00e088b-41cd-4b94-8925-24c98b349f1c)

### Resource Operation Flow
![policy-machine-server-ro 2](https://github.com/user-attachments/assets/80295f14-74f6-4564-a8a8-bc110c7b7bd5)

### Event Context Flow
![policy-machine-server-ec 1](https://github.com/user-attachments/assets/af7cc871-7c51-4719-9a2d-947fd59bb961)

