package gov.nist.csd.pm.pdp.admin.pdp;

import com.eventstore.dbclient.WrongExpectedVersionException;
import gov.nist.csd.pm.core.common.exception.PMException;
import gov.nist.csd.pm.core.common.exception.PMRuntimeException;
import gov.nist.csd.pm.core.pap.query.model.context.UserContext;
import gov.nist.csd.pm.core.pdp.PDP;
import gov.nist.csd.pm.core.pdp.PDPTx;
import gov.nist.csd.pm.core.pdp.PDPTxRunner;
import gov.nist.csd.pm.core.pdp.adjudication.AdjudicationResponse;
import gov.nist.csd.pm.pdp.admin.pap.EventTrackingPAP;
import gov.nist.csd.pm.pdp.proto.adjudication.AdminCommand;
import gov.nist.csd.pm.pdp.proto.event.PMEvent;
import gov.nist.csd.pm.pdp.shared.eventstore.CurrentRevisionService;
import gov.nist.csd.pm.pdp.shared.eventstore.EventStoreConnectionManager;
import gov.nist.csd.pm.pdp.shared.eventstore.EventStoreDBConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class AdjudicatorTest {

	@Mock
	CommandHandler commandHandler;
	@Mock
	EventStoreDBConfig eventStoreDBConfig;
	@Mock
	EventStoreConnectionManager eventStoreConnectionManager;
	@Mock
	CurrentRevisionService currentRevision;
	@Mock
	ContextFactory contextFactory;
	@Mock
	NGACContext contextMock;
	@Mock
	PDP pdpMock;
	@Mock
	PDPTx pdpTxMock;
	@Mock
	EventTrackingPAP papMock;
	@Mock
	UserContext userContextMock;

	Adjudicator adjudicator;

	@BeforeEach
	void setUp() throws PMException {
		when(contextFactory.createContext()).thenReturn(contextMock);
		when(contextMock.pdp()).thenReturn(pdpMock);
		when(contextMock.pap()).thenReturn(papMock);
		when(contextMock.userCtx()).thenReturn(userContextMock);
		when(currentRevision.get()).thenReturn(1L);

		adjudicator = new Adjudicator(
				commandHandler,
				eventStoreDBConfig,
				eventStoreConnectionManager,
				currentRevision,
				contextFactory
		);
	}

	@Nested
	class AdjudicateQueryTest {

		@Test
		void whenSuccess_transactionExecuted() throws PMException {
			String expectedResult = "Success";
			when(pdpMock.runTx(any(), any())).thenReturn(expectedResult);

			String result = adjudicator.adjudicateQuery(tx -> expectedResult);

			assertEquals(expectedResult, result);
			verify(contextFactory).createContext();
			verify(pdpMock).runTx(any(), any());
		}

		@Test
		void whenPMException_NoRetiresAttempted() throws PMException {
			PMException pmException = new PMException("Test exception");
			when(pdpMock.runTx(any(), any())).thenThrow(pmException);

			PMRuntimeException exception = assertThrows(
					PMRuntimeException.class,
					() -> adjudicator.adjudicateQuery(tx -> "Result")
			);

			assertEquals(pmException, exception.getCause());
			verify(contextFactory, times(1)).createContext();
			verify(pdpMock, times(1)).runTx(any(), any());
			verify(papMock, never()).publishToEventStore(any(), any(), anyLong());
		}
	}

	@Nested
	class AdjudicateAdminCommandsTest {

		@Test
		void whenSuccess_adminCommandsHandled() throws PMException {
			AdminCommand cmd1 = mock(AdminCommand.class);
			AdminCommand cmd2 = mock(AdminCommand.class);

			when(pdpMock.runTx(any(), any()))
					.thenAnswer(inv -> {
						Object runner = inv.getArgument(1);
						@SuppressWarnings("unchecked")
						PDPTxRunner<Object> r = (PDPTxRunner<Object>) runner;
						return r.run(pdpTxMock);
					});

			when(papMock.publishToEventStore(any(), any(), anyLong()))
					.thenReturn(Collections.emptyList());

			doAnswer(invocation -> {
				@SuppressWarnings("unchecked")
				Map<String, Long> map = invocation.getArgument(2);
				map.put("one", 1L);
				return null;
			}).when(commandHandler)
					.handleCommand(eq(pdpTxMock), eq(cmd1), anyMap());

			doAnswer(invocation -> {
				@SuppressWarnings("unchecked")
				Map<String, Long> map = invocation.getArgument(2);
				map.put("two", 2L);
				return null;
			}).when(commandHandler)
					.handleCommand(eq(pdpTxMock), eq(cmd2), anyMap());

			Map<String, Long> result = adjudicator.adjudicateAdminCommands(List.of(cmd1, cmd2));

			assertEquals(2, result.size());
			assertEquals(1L, result.get("one"));
			assertEquals(2L, result.get("two"));

			verify(contextFactory, times(1)).createContext();
			verify(commandHandler, times(1))
					.handleCommand(eq(pdpTxMock), eq(cmd1), anyMap());
			verify(commandHandler, times(1))
					.handleCommand(eq(pdpTxMock), eq(cmd2), anyMap());
		}

		@Test
		void whenPMException_NoRetiresAttempted() throws PMException {
			AdminCommand cmd = mock(AdminCommand.class);

			when(pdpMock.runTx(any(), any(PDPTxRunner.class)))
					.thenAnswer(inv -> {
						@SuppressWarnings("unchecked")
						PDPTxRunner<?> runner = inv.getArgument(1);
						return runner.run(pdpTxMock);
					});
			when(papMock.publishToEventStore(any(), any(), anyLong()))
					.thenReturn(Collections.emptyList());

			doThrow(new PMException("fail"))
					.when(commandHandler)
					.handleCommand(eq(pdpTxMock), eq(cmd), anyMap());

			RuntimeException ex = assertThrows(
					RuntimeException.class,
					() -> adjudicator.adjudicateAdminCommands(List.of(cmd))
			);
			assertTrue(ex.getCause() instanceof PMException);
			assertEquals("fail", ex.getCause().getMessage());

			verify(contextFactory, times(1)).createContext();
			verify(commandHandler, times(1))
					.handleCommand(eq(pdpTxMock), eq(cmd), anyMap());
		}

		@Test
		void whenWrongExpectedRevisionExceptionAndSuccessOnRetry_transactionExecutedAndEventsReturned() throws PMException {
			AdminCommand cmd = mock(AdminCommand.class);
			AtomicInteger attempts = new AtomicInteger();

			when(pdpMock.runTx(any(), any(PDPTxRunner.class)))
					.thenAnswer(inv -> {
						@SuppressWarnings("unchecked")
						PDPTxRunner<?> runner = inv.getArgument(1);
						return runner.run(pdpTxMock);
					});
			when(papMock.publishToEventStore(any(), any(), anyLong()))
					.thenReturn(Collections.emptyList());

			doAnswer(inv -> {
				@SuppressWarnings("unchecked")
				Map<String, Long> map = inv.getArgument(2);
				if (attempts.getAndIncrement() == 0) {
					throw mock(WrongExpectedVersionException.class);
				}
				map.put("retry", 42L);
				return null;
			}).when(commandHandler)
					.handleCommand(eq(pdpTxMock), eq(cmd), anyMap());

			Map<String, Long> result = adjudicator.adjudicateAdminCommands(List.of(cmd));

			assertEquals(1, result.size());
			assertEquals(42L, result.get("retry"));
			assertEquals(2, attempts.get());
			verify(contextFactory, times(2)).createContext();
			verify(commandHandler, times(2))
					.handleCommand(eq(pdpTxMock), eq(cmd), anyMap());
		}

		@Test
		void whenWrongExpectedRevisionException_AndMaxRetriesExceeded_throwsException() throws PMException {
			AdminCommand cmd = mock(AdminCommand.class);
			AtomicInteger attempts = new AtomicInteger();

			when(pdpMock.runTx(any(), any(PDPTxRunner.class)))
					.thenAnswer(inv -> {
						@SuppressWarnings("unchecked")
						PDPTxRunner<?> runner = inv.getArgument(1);
						return runner.run(pdpTxMock);
					});
			when(papMock.publishToEventStore(any(), any(), anyLong()))
					.thenReturn(Collections.emptyList());

			doAnswer(inv -> {
				attempts.incrementAndGet();
				throw mock(WrongExpectedVersionException.class);
			}).when(commandHandler)
					.handleCommand(eq(pdpTxMock), eq(cmd), anyMap());

			assertThrows(
					WrongExpectedVersionException.class,
					() -> adjudicator.adjudicateAdminCommands(List.of(cmd))
			);
			assertEquals(3, attempts.get());
			verify(contextFactory, times(3)).createContext();
			verify(commandHandler, times(3))
					.handleCommand(eq(pdpTxMock), eq(cmd), anyMap());
		}

	}

	@Nested
	class AdjudicateTransactionTest {

		@Test
		void whenSuccess_transactionExecutedAndEventsReturned() throws PMException {
			PMEvent ev = mock(PMEvent.class);
			when(papMock.publishToEventStore(any(), any(), anyLong()))
					.thenReturn(List.of(ev));

			List<PMEvent> result = adjudicator.adjudicateTransaction(ctx -> {});

			assertEquals(1, result.size());
			assertSame(ev, result.get(0));
			verify(contextFactory, times(1)).createContext();
			verify(papMock, times(1)).publishToEventStore(any(), any(), anyLong());
		}

		@Test
		void whenPMException_NoRetiresAttempted() throws PMException {
			PMConsumer<NGACContext> badConsumer = ctx -> {throw new PMException("test exception");};
			PMRuntimeException ex = assertThrows(
					PMRuntimeException.class,
					() -> adjudicator.adjudicateTransaction(badConsumer)
			);
			assertTrue(ex.getCause() instanceof PMException);
			assertEquals("test exception", ex.getCause().getMessage());

			verify(contextFactory, times(1)).createContext();
			verify(papMock, never()).publishToEventStore(any(), any(), anyLong());
		}

		@Test
		void whenWrongExpectedRevisionExceptionAndSuccessOnRetry_transactionExecutedAndEventsReturned() throws PMException {
			AtomicInteger attempts = new AtomicInteger();
			PMEvent ev = mock(PMEvent.class);

			when(papMock.publishToEventStore(any(), any(), anyLong()))
					.thenAnswer(invocation -> {
						if (attempts.getAndIncrement() == 0) {
							throw mock(WrongExpectedVersionException.class);
						}
						return List.of(ev);
					});

			List<PMEvent> result = adjudicator.adjudicateTransaction(ctx -> {});

			assertEquals(1, result.size());
			assertSame(ev, result.get(0));
			assertEquals(2, attempts.get());
			verify(contextFactory, times(2)).createContext();
			verify(papMock, times(2)).publishToEventStore(any(), any(), anyLong());
		}

		@Test
		void whenWrongExpectedRevisionException_AndMaxRetriesExceeded_throwsException() throws PMException {
			AtomicInteger attempts = new AtomicInteger();
			when(papMock.publishToEventStore(any(), any(), anyLong()))
					.thenAnswer(inv -> {
						attempts.incrementAndGet();
						WrongExpectedVersionException ex = mock(WrongExpectedVersionException.class);
						throw ex;
					});

			assertThrows(
					WrongExpectedVersionException.class,
					() -> adjudicator.adjudicateTransaction(ctx -> {})
			);

			assertEquals(3, attempts.get());
			verify(contextFactory, times(3)).createContext();
			verify(papMock, times(3)).publishToEventStore(any(), any(), anyLong());
		}
	}

	@Nested
	class AdjudicateAdminOperationTest {
		private final String opName = "op";
		private final Map<String,Object> args = Collections.singletonMap("k","v");

		@Test
		void whenSuccess_returnsResponse() throws PMException {
			AdjudicationResponse resp = mock(AdjudicationResponse.class);
			when(contextMock.pdp().adjudicateAdminOperation(any(), eq(opName), eq(args)))
					.thenReturn(resp);
			when(papMock.publishToEventStore(any(), any(), anyLong()))
					.thenReturn(Collections.emptyList());

			AdjudicationResponse result = adjudicator.adjudicateAdminOperation(opName, args);

			assertSame(resp, result);
			verify(contextFactory, times(1)).createContext();
			verify(contextMock.pdp(), times(1))
					.adjudicateAdminOperation(eq(userContextMock), eq(opName), eq(args));
			verify(papMock, times(1))
					.publishToEventStore(any(), any(), anyLong());
		}

		@Test
		void whenPMException_wrappedAndNoRetry() throws PMException {
			PMException failure = new PMException("boom");
			when(contextMock.pdp().adjudicateAdminOperation(any(), eq(opName), eq(args)))
					.thenThrow(failure);

			PMRuntimeException ex = assertThrows(
					PMRuntimeException.class,
					() -> adjudicator.adjudicateAdminOperation(opName, args)
			);
			assertSame(failure, ex.getCause());

			verify(contextFactory, times(1)).createContext();
			verify(papMock, never()).publishToEventStore(any(), any(), anyLong());
		}

		@Test
		void whenWrongExpectedVersionAndSuccessOnRetry_returnsResponse() throws PMException {
			AdjudicationResponse resp = mock(AdjudicationResponse.class);
			AtomicInteger attempts = new AtomicInteger();

			when(contextMock.pdp().adjudicateAdminOperation(any(), eq(opName), eq(args)))
					.thenAnswer(inv -> {
						if (attempts.getAndIncrement() == 0) {
							throw mock(WrongExpectedVersionException.class);
						}
						return resp;
					});
			when(papMock.publishToEventStore(any(), any(), anyLong()))
					.thenReturn(Collections.emptyList());

			AdjudicationResponse result = adjudicator.adjudicateAdminOperation(opName, args);

			assertSame(resp, result);
			assertEquals(2, attempts.get(), "Should have retried once");
			verify(contextFactory, times(2)).createContext();
			verify(contextMock.pdp(), times(2))
					.adjudicateAdminOperation(eq(userContextMock), eq(opName), eq(args));
			// publish only on successful attempt
			verify(papMock, times(1))
					.publishToEventStore(any(), any(), anyLong());
		}

		@Test
		void whenWrongExpectedVersionAndMaxRetriesExceeded_throws() throws PMException {
			AtomicInteger attempts = new AtomicInteger();
			when(contextMock.pdp().adjudicateAdminOperation(any(), eq(opName), eq(args)))
					.thenAnswer(inv -> {
						attempts.incrementAndGet();
						throw mock(WrongExpectedVersionException.class);
					});

			assertThrows(
					WrongExpectedVersionException.class,
					() -> adjudicator.adjudicateAdminOperation(opName, args)
			);

			assertEquals(3, attempts.get());
			verify(contextFactory, times(3)).createContext();
			verify(papMock, never()).publishToEventStore(any(), any(), anyLong());
		}
	}

	@Nested
	class AdjudicateAdminRoutineTest {
		private final String rtName = "routine";
		private final Map<String,Object> args = Collections.singletonMap("x",123);

		@Test
		void whenSuccess_returnsResponse() throws PMException {
			AdjudicationResponse resp = mock(AdjudicationResponse.class);
			when(contextMock.pdp().adjudicateAdminRoutine(any(), eq(rtName), eq(args)))
					.thenReturn(resp);
			when(papMock.publishToEventStore(any(), any(), anyLong()))
					.thenReturn(Collections.emptyList());

			AdjudicationResponse result = adjudicator.adjudicateAdminRoutine(rtName, args);

			assertSame(resp, result);
			verify(contextFactory).createContext();
			verify(contextMock.pdp())
					.adjudicateAdminRoutine(eq(userContextMock), eq(rtName), eq(args));
			verify(papMock).publishToEventStore(any(), any(), anyLong());
		}

		@Test
		void whenPMException_wrappedAndNoRetry() throws PMException {
			PMException failure = new PMException("oops");
			when(contextMock.pdp().adjudicateAdminRoutine(any(), eq(rtName), eq(args)))
					.thenThrow(failure);

			PMRuntimeException ex = assertThrows(
					PMRuntimeException.class,
					() -> adjudicator.adjudicateAdminRoutine(rtName, args)
			);
			assertSame(failure, ex.getCause());

			verify(contextFactory).createContext();
			verify(papMock, never()).publishToEventStore(any(), any(), anyLong());
		}

		@Test
		void whenWrongExpectedVersionAndSuccessOnRetry_returnsResponse() throws PMException {
			AdjudicationResponse resp = mock(AdjudicationResponse.class);
			AtomicInteger attempts = new AtomicInteger();
			when(contextMock.pdp().adjudicateAdminRoutine(any(), eq(rtName), eq(args)))
					.thenAnswer(inv -> {
						if (attempts.getAndIncrement() == 0) {
							throw mock(WrongExpectedVersionException.class);
						}
						return resp;
					});
			when(papMock.publishToEventStore(any(), any(), anyLong()))
					.thenReturn(Collections.emptyList());

			AdjudicationResponse result = adjudicator.adjudicateAdminRoutine(rtName, args);

			assertSame(resp, result);
			assertEquals(2, attempts.get());
			verify(contextFactory, times(2)).createContext();
			verify(papMock, times(1)).publishToEventStore(any(), any(), anyLong());
		}

		@Test
		void whenWrongExpectedVersionAndMaxRetriesExceeded_throws() throws PMException {
			AtomicInteger attempts = new AtomicInteger();
			when(contextMock.pdp().adjudicateAdminRoutine(any(), eq(rtName), eq(args)))
					.thenAnswer(inv -> {
						attempts.incrementAndGet();
						throw mock(WrongExpectedVersionException.class);
					});

			WrongExpectedVersionException ex = assertThrows(
					WrongExpectedVersionException.class,
					() -> adjudicator.adjudicateAdminRoutine(rtName, args)
			);
			assertEquals(3, attempts.get());

			verify(contextFactory, times(3)).createContext();
			verify(papMock, never()).publishToEventStore(any(), any(), anyLong());
		}
	}
}
