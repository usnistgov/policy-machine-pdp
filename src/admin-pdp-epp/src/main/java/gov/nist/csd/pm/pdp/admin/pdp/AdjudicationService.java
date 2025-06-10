package gov.nist.csd.pm.pdp.admin.pdp;

import gov.nist.csd.pm.core.impl.neo4j.embedded.pap.Neo4jEmbeddedPAP;
import gov.nist.csd.pm.core.pdp.UnauthorizedException;
import gov.nist.csd.pm.pdp.proto.adjudication.*;
import gov.nist.csd.pm.pdp.shared.protobuf.ObjectToStruct;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@GrpcService
public class AdjudicationService extends AdjudicationServiceGrpc.AdjudicationServiceImplBase {

	private final Adjudicator adjudicator;
	private final Neo4jEmbeddedPAP pap;

	public AdjudicationService(Adjudicator adjudicator, Neo4jEmbeddedPAP pap) {
		this.adjudicator = adjudicator;
		this.pap = pap;
	}

	@Override
	public void adjudicateAdminCmd(AdjudicateAdminCmdRequest request,
	                               StreamObserver<CreatedNodeIdsResponse> responseObserver) {
		try {
			Map<String, Long> createdNodeIds = adjudicator.adjudicateAdminCommands(request.getCommandsList());
			responseObserver.onNext(
					CreatedNodeIdsResponse.newBuilder()
							.putAllNodeIds(createdNodeIds)
							.build()
			);
			responseObserver.onCompleted();
		} catch (UnauthorizedException e) {
			responseObserver.onError(Status.PERMISSION_DENIED
					                         .withDescription(e.getMessage())
					                         .withCause(e)
					                         .asRuntimeException());
		} catch (Exception e) {
			responseObserver.onError(Status.INTERNAL
					                         .withDescription(e.getMessage())
					                         .withCause(e)
					                         .asRuntimeException());
		}
	}

	@Override
	public void adjudicateGenericOperation(GenericAdminCmd request,
	                                       StreamObserver<AdjudicateGenericResponse> responseObserver) {
		try {
			Map<String, Object> args = fromProtoArgs(request.getArgsMap());
			Object response = adjudicator.adjudicateAdminOperation(request.getOpName(), args);
			responseObserver.onNext(AdjudicateGenericResponse.newBuilder()
					                        .setValue(ObjectToStruct.convert(response))
					                        .build());
			responseObserver.onCompleted();
		} catch (UnauthorizedException e) {
			responseObserver.onError(Status.PERMISSION_DENIED
					                         .withDescription(e.getMessage())
					                         .withCause(e)
					                         .asRuntimeException());
		} catch (Exception e) {
			responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
		}
	}

	@Override
	public void adjudicateGenericRoutine(GenericAdminCmd request,
	                                     StreamObserver<AdjudicateGenericResponse> responseObserver) {
		try {
			Map<String, Object> args = fromProtoArgs(request.getArgsMap());
			Object response = adjudicator.adjudicateAdminRoutine(request.getOpName(), args);
			responseObserver.onNext(AdjudicateGenericResponse.newBuilder()
					                        .setValue(ObjectToStruct.convert(response))
					                        .build());
			responseObserver.onCompleted();
		} catch (UnauthorizedException e) {
			responseObserver.onError(Status.PERMISSION_DENIED
					                         .withDescription(e.getMessage())
					                         .withCause(e)
					                         .asRuntimeException());
		} catch (Exception e) {
			responseObserver.onError(Status.INTERNAL.withDescription(e.getMessage()).asRuntimeException());
		}
	}

	private Map<String, Object> fromProtoArgs(Map<String, Arg> args) {
		Map<String, Object> argsMap = new HashMap<>();
		for (var arg : args.entrySet()) {
			String key = arg.getKey();
			Object value = argToObject(arg.getValue());
			argsMap.put(key, value);
		}
		return argsMap;
	}

	private Object argToObject(Arg arg) {
		return switch (arg.getValueCase()) {
			case INT64VALUE -> arg.getInt64Value();
			case STRINGVALUE -> arg.getStringValue();
			case BOOLVALUE -> arg.getBoolValue();
			case LISTVALUE -> {
				List<Object> objectList = new ArrayList<>();
				for (Arg a : arg.getListValue().getArgsList()) {
					objectList.add(argToObject(a));
				}

				yield objectList;
			}
			case ARGMAP -> {
				Map<Object, Object> objectMap = new HashMap<>();
				for (var entry : arg.getArgMap().getArgsMap().entrySet()) {
					String key = entry.getKey();
					Arg value = entry.getValue();

					objectMap.put(key, argToObject(value));
				}

				yield objectMap;
			}
			case VALUE_NOT_SET -> throw new IllegalArgumentException("No value set for arg " + arg);
		};
	}
}
