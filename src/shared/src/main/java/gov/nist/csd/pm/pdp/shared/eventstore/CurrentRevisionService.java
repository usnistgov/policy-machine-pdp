package gov.nist.csd.pm.pdp.shared.eventstore;

import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicLong;

@Service
public class CurrentRevisionService {

	private final AtomicLong currentRevision;

	public CurrentRevisionService() {
		currentRevision = new AtomicLong(-1);
	}

	public void set(long revision) {
		this.currentRevision.set(revision);
	}

	public long get() {
		return currentRevision.get();
	}
}
