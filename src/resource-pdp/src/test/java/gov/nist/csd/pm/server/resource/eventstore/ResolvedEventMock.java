package gov.nist.csd.pm.server.resource.eventstore;

import com.eventstore.dbclient.RecordedEvent;
import com.eventstore.dbclient.ResolvedEvent;
import gov.nist.csd.pm.pdp.proto.event.PMEvent;

import java.time.Instant;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ResolvedEventMock {

	public static ResolvedEvent of(long revision, PMEvent event) {
		RecordedEvent recorded = mock(RecordedEvent.class);
		when(recorded.getRevision()).thenReturn(revision);
		when(recorded.getEventType()).thenReturn(event.getDescriptorForType().getName());
		when(recorded.getCreated()).thenReturn(Instant.now());
		when(recorded.getEventData()).thenReturn(event.toByteArray());

		ResolvedEvent resolved = mock(ResolvedEvent.class);
		when(resolved.getEvent()).thenReturn(recorded);
		return resolved;
	}

}
