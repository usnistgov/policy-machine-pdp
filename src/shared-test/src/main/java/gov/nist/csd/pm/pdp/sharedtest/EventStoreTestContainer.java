/*
 * This Software (Policy Machine) is being made available as a public service by the
 * National Institute of Standards and Technology (NIST), an Agency of the United
 * States Department of Commerce. This software was developed in part by employees of
 * NIST and in part by NIST contractors. Copyright in portions of this software that
 * were developed by NIST contractors has been licensed or assigned to NIST. Pursuant
 * to Title 17 United States Code Section 105, works of NIST employees are not
 * subject to copyright protection in the United States. However, NIST may hold
 * international copyright in software created by its employees and domestic
 * copyright (or licensing rights) in portions of software that were assigned or
 * licensed to NIST. To the extent that NIST holds copyright in this software, it is
 * being made available under the Creative Commons Attribution 4.0 International
 * license (CC BY 4.0). The disclaimers of the CC BY 4.0 license apply to all parts
 * of the software developed or licensed by NIST.
 *
 * ACCESS THE FULL CC BY 4.0 LICENSE HERE:
 * https://creativecommons.org/licenses/by/4.0/legalcode
 */

package gov.nist.csd.pm.pdp.sharedtest;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

public class EventStoreTestContainer extends GenericContainer<EventStoreTestContainer> {

	public EventStoreTestContainer() {
		super("eventstore/eventstore:24.10");

		withEnv("EVENTSTORE_HTTP_PORT", "2113")
				.withEnv("EVENTSTORE_INT_TCP_PORT", "1113")
				.withEnv("EVENTSTORE_CLUSTER_SIZE", "1")
				.withEnv("EVENTSTORE_RUN_PROJECTIONS", "All")
				.withEnv("EVENTSTORE_START_STANDARD_PROJECTIONS", "true")
				.withEnv("EVENTSTORE_INSECURE", "true")
				.withEnv("EVENTSTORE_ENABLE_ATOM_PUB_OVER_HTTP", "true")
				.withExposedPorts(2113, 1113)
				.waitingFor(Wait.forHttp("/stats").forPort(2113).forStatusCode(200));
	}

	public int getPort() {
		return getMappedPort(2113);
	}
}
