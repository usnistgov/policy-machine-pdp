# Policy Machine PDP


## Quickstart
To start the PDP using docker-compose:
```shell
cd docker && docker-compose up
```

This will create the following services: 

- `admin-pdp-epp`, port 50052 
- `resource-pdp`, port 50051
- `eventstore`, port 2113 

## Server Components
### admin-pdp-epp


### resource-pdp

### eventstore

## gRPC Headers
- note authentication is not implemented, but gprc services expect a header with the username