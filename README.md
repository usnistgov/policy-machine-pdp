# Policy Machine PDP
The Policy Machine PDP is a general purpose NGAC Policy Decision Point (PDP) and Event Processing Point (EPP). The purpose of this project is to provide a standalone NGAC PDP and EPP that can be plugged into any use case. The server is built on the [policy-machine-core library](https://github.com/usnistgov/policy-machine-core) and exposes a set of gRPC services that domain specific Policy Enforcement Points (PEP) and Resource Access Points (RAP) can call for decision making, event processing, and policy query/review. Custom NGAC operations and routines can be loaded as [plugins](#operation-and-routine-plugins) to facilitate enhanced policy adminsitration. This project is designed as an event-driven gRPC microservice implementation of the NGAC standard architecture. The gRPC 
service and message definitions can be found in the [policy-machine-protos repo](https://github.com/usnistgov/policy-machine-protos).

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
      bootstrap-file-path: "./src/admin-pdp-epp/src/main/resources/bootstrap.pml"
      # Path to store Neo4j policy locally.
      neo4j-db-path: "neo4j/data"
      # Name of the EventStoreDB consumer group.
      esdb-consumer-group: admin-pdp-epp-cg
      # Interval for writing snapshots to the event store.
      snapshot-interval: 1000
      # Shutdown the server once the bootstrap process is complete.
      shutdown-after-bootstrap: false
      # (optional) directory to Operation and Routine plugins
      plugins-dir: "/plugins"
      # Time in milliseconds to wait to ensure revision consistency with event store. Default is 1000.
      revision-consistency-timeout: 1000
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

#### Operation Plugins
Custom policy-machine-core Operations can be packaged into jar files and provided to the admin-pdp-epp. This 
service uses [PF4J](https://pf4j.org/doc/getting-started.html) to load plugins from JAR files in a directory defined in 
the configurations option above.

1. Plugin pom.xml
Here is an example of what a pom.xml looks like for a plugin. The policy-machine-core and PF4J dependencies are required for
the plugin to work.

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>gov.nist.csd.pm.pdp</groupId>
    <artifactId>TestPlugin</artifactId>
    <version>1.0-SNAPSHOT</version>

    <properties>
        <maven.compiler.source>21</maven.compiler.source>
        <maven.compiler.target>21</maven.compiler.target>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>

    <repositories>
        <repository>
            <id>jitpack.io</id>
            <url>https://jitpack.io</url>
        </repository>
    </repositories>

    <dependencies>
        <dependency>
            <groupId>com.github.usnistgov</groupId>
            <artifactId>policy-machine-core</artifactId>
            <version>707e66d</version>
            <scope>provided</scope>
        </dependency>
        <dependency>
            <groupId>com.mysql</groupId>
            <artifactId>mysql-connector-j</artifactId>
            <version>9.1.0</version>
        </dependency>
        <dependency>
            <groupId>org.pf4j</groupId>
            <artifactId>pf4j</artifactId>
            <version>3.9.0</version>
            <scope>provided</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-shade-plugin</artifactId>
                <version>3.5.3</version>
                <executions>
                    <execution>
                        <phase>package</phase>
                        <goals><goal>shade</goal></goals>
                        <configuration>
                            <createDependencyReducedPom>false</createDependencyReducedPom>
                            <filters>
                                <filter>
                                    <artifact>*:*</artifact>
                                    <excludes>
                                        <exclude>META-INF/*.SF</exclude>
                                        <exclude>META-INF/*.DSA</exclude>
                                        <exclude>META-INF/*.RSA</exclude>
                                    </excludes>
                                </filter>
                            </filters>
                            <transformers>
                                <transformer implementation="org.apache.maven.plugins.shade.resource.ServicesResourceTransformer"/>
                                <transformer implementation="org.apache.maven.plugins.shade.resource.ManifestResourceTransformer">
                                    <manifestEntries>
                                        <Plugin-Id>${project.artifactId}</Plugin-Id>
                                        <Plugin-Class>org.pf4j.Plugin</Plugin-Class>
                                        <Plugin-Version>${project.version}</Plugin-Version>
                                        <Plugin-Description>${project.name}</Plugin-Description>
                                    </manifestEntries>
                                </transformer>
                            </transformers>
                        </configuration>
                    </execution>
                </executions>
            </plugin>
        </plugins>
    </build>

</project>
```


2. Plugin classes

To create a class that will be loaded as a plugin, annotate the class with `@org.pf4j.Extension` and implement
`org.pf4j.ExtensionPoint`. The plugin must extend one of the following operation types from the `gov.nist.csd.pm.core.pap.operation` package:

| Type | Description | Authorization | Execution Context |
|------|-------------|---------------|-------------------|
| `AdminOperation<T>` | Modifies policy state | `canExecute()` required | `PAP` (read/write) |
| `ResourceOperation<T>` | Resource access operations | `canExecute()` required | `PolicyQuery` (read-only) |
| `QueryOperation<T>` | Policy query operations | `canExecute()` required | `PolicyQuery` (read-only) |
| `Routine<T>` | Multi-step policy modifications | None | `PAP` (read/write) |
| `Function<T>` | Pure functions (no policy access) | None | `Args` only |

#### AdminOperation Example
Use for operations that modify policy and require authorization.

```java
import gov.nist.csd.pm.core.pap.PAP;
import gov.nist.csd.pm.core.pap.operation.AdminOperation;
import gov.nist.csd.pm.core.pap.operation.arg.Args;
import gov.nist.csd.pm.core.pap.operation.arg.type.ListType;
import gov.nist.csd.pm.core.pap.operation.param.FormalParameter;
import gov.nist.csd.pm.core.pap.query.model.context.UserContext;
import gov.nist.csd.pm.core.common.exception.PMException;
import org.pf4j.Extension;
import org.pf4j.ExtensionPoint;

import java.util.List;

import static gov.nist.csd.pm.core.pap.operation.arg.type.BasicTypes.*;

@Extension
public class CustomAdminOperation extends AdminOperation<Void> implements ExtensionPoint {

    // Define custom formal parameters
    private static final FormalParameter<String> TARGET_PARAM =
        new FormalParameter<>("target", STRING_TYPE);

    public CustomAdminOperation() {
        super(
            "customAdminOp",
            VOID_TYPE,
            List.of(TARGET_PARAM)
        );
    }

    @Override
    public void canExecute(PAP pap, UserContext userCtx, Args args) throws PMException {
        // Check if user is authorized to execute this operation
    }

    @Override
    public Void execute(PAP pap, Args args) throws PMException {
        String target = args.get(TARGET_PARAM);
        // Modify policy using PAP
        return null;
    }
}
```

3. JAR and set plugin path
To make the plugins available to the admin-pdp-epp, package the plugin project into a JAR. *Note:* This has only been tested
with JARs packaged using the maven-shade-plugin configuration in step 2. Then, define the pm.pdp.admin.plugins-dir config
option and ensure the packaged JAR is located in that directory.

To verify the server loaded the plugins successfully look in the application startup logs for a line similar to:
`g.n.c.p.p.admin.plugin.PluginLoader - Loaded 4 Operation plugins via PF4J extensions`

### resource-pdp
The `resource-pdp` is a read only API with a method to adjudicate resource operations. Since resource operations
can be subject to obligations, the `resource-pdp` sends event contexts to the `admin-pdp-epp` for
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
      # Time in milliseconds to wait to ensure revision consistency with event store. Default is 1000.
      revision-consistency-timeout: 1000
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

