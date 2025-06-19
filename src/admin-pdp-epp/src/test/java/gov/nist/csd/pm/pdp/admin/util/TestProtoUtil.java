package gov.nist.csd.pm.pdp.admin.util;

import gov.nist.csd.pm.core.common.graph.node.Node;
import gov.nist.csd.pm.core.common.graph.node.NodeType;
import gov.nist.csd.pm.pdp.shared.protobuf.ProtoUtil;

public class TestProtoUtil {

	public static gov.nist.csd.pm.proto.v1.model.Node testNode(long id) {
		return ProtoUtil.toNodeProto(new Node(id, "testNode" + id, NodeType.OA));
	}


}
