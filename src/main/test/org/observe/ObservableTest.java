package org.observe;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.qommons.TriFunction;

import com.google.common.reflect.TypeToken;

/** Tests observable classes in the org.observe package */
public class ObservableTest {
	/** Tests simple {@link SimpleSettableValue} functionality */
	@Test
	public void settableValue() {
		SimpleSettableValue<Integer> obs = new SimpleSettableValue<>(Integer.TYPE, false);
		obs.set(0, null);
		int [] received = new int[] {0};
		obs.changes().act(value -> received[0] = value.getNewValue());
		for(int i = 1; i < 10; i++) {
			obs.set(i, null);
			assertEquals(i, received[0]);
		}
	}

	/** Tests {@link ObservableValue#map(java.util.function.Function)} */
	@Test
	public void valueMap() {
		SimpleSettableValue<Integer> obs = new SimpleSettableValue<>(Integer.TYPE, false);
		obs.set(0, null);
		int [] received = new int[] {0};
		obs.map(//
			value -> value * 10//
		).changes().act(//
			value -> received[0] = value.getNewValue());

		for(int i = 1; i < 10; i++) {
			obs.set(i, null);
			assertEquals(i * 10, received[0]);
		}
	}

	/** Tests {@link Observable#filter(java.util.function.Function)} */
	@Test
	public void filter() {
		SimpleObservable<Integer> obs = new SimpleObservable<>();
		int [] received = new int[] {0};
		obs.filter(value -> value % 3 == 0).act(value -> received[0] = value);

		for(int i = 1; i < 30; i++) {
			obs.onNext(i);
			assertEquals((i / 3) * 3, received[0]);
		}
	}

	/** Tests {@link Observable#take(int)} */
	@Test
	public void takeNumber() {
		SimpleObservable<Integer> obs = new SimpleObservable<>();
		int [] received = new int[] {0};
		boolean [] complete = new boolean[1];
		Observable<Integer> take = obs.take(10);
		take.act(value -> received[0] = value);
		take.completed().act(value -> complete[0] = true);

		for(int i = 1; i < 30; i++) {
			obs.onNext(i);
			if(i < 10) {
				assertEquals(i, received[0]);
				assertEquals(false, complete[0]);
			} else {
				assertEquals(10, received[0]);
				assertEquals(true, complete[0]);
			}
		}
	}

	/** Tests {@link Observable#takeUntil(Observable)} */
	@Test
	public void takeUntil() {
		SimpleObservable<Integer> obs = new SimpleObservable<>();
		SimpleObservable<Boolean> stop = new SimpleObservable<>();
		int [] received = new int[] {0};
		int [] count = new int[1];
		boolean [] complete = new boolean[1];
		Observable<Integer> take = obs.takeUntil(stop);
		take.act(value -> {
			count[0]++;
			received[0] = value;
		});
		take.completed().act(value -> complete[0] = true);

		for(int i = 1; i <= 30; i++) {
			obs.onNext(i);
			assertEquals(i, received[0]);
			assertEquals(i, count[0]);
		}
		stop.onNext(true);
		assertEquals(true, complete[0]);
		final int finalValue = received[0];
		for(int i = 1; i <= 30; i++) {
			obs.onNext(i);
			assertEquals(finalValue, received[0]);
			assertEquals(30, count[0]);
		}

		complete[0] = false;
		obs.onCompleted(0);
		assertEquals(false, complete[0]);

		obs = new SimpleObservable<>();
		complete[0] = false;
		take = obs.takeUntil(stop);
		take.completed().act(value -> complete[0] = true);

		obs.onCompleted(0);
		assertEquals(true, complete[0]);
	}

	/** Tests {@link ObservableValue#takeUntil(Observable)} */
	@Test
	public void valueTakeUntil() {
		SimpleSettableValue<Integer> obs = new SimpleSettableValue<>(Integer.TYPE, false);
		obs.set(0, null);
		SimpleObservable<Boolean> stop = new SimpleObservable<>();
		int [] received = new int[] {0};
		int [] count = new int[1];
		boolean [] complete = new boolean[1];
		ObservableValue<Integer> take = obs.takeUntil(stop);
		take.changes().act(value -> {
			count[0]++;
			received[0] = value.getNewValue();
		});
		take.changes().completed().act(value -> complete[0] = true);

		for(int i = 1; i <= 30; i++) {
			obs.set(i, null);
			assertEquals(i, received[0]);
			assertEquals(i + 1, count[0]); // Plus 1 because of the initialization
		}
		stop.onNext(true);
		assertEquals(true, complete[0]);
		final int finalValue = received[0];
		for(int i = 1; i <= 30; i++) {
			obs.set(i, null);
			assertEquals(finalValue, received[0]);
			assertEquals(31, count[0]);
		}
	}

	/** Tests {@link Observable#skip(int)} */
	@Test
	public void skip() {
		SimpleObservable<Integer> obs = new SimpleObservable<>();

		int [] received = new int[1];
		obs.skip(5).act(value -> received[0] = value);

		for(int i = 0; i < 10; i++) {
			obs.onNext(i);
			int correct = i < 5 ? 0 : i;
			assertEquals(correct, received[0]);
		}
	}

	/**
	 * Tests {@link Subscription} as an observable to ensure that it is closed (and its observers notified) when
	 * {@link Subscription#unsubscribe()} is called
	 */
	@Test
	public void chaining() {
		SimpleObservable<Integer> obs = new SimpleObservable<>();
		int [] received = new int[] {0};
		ChainingObservable<Integer> sub = obs.chain().act(value -> received[0] = value);
		int [] received2 = new int[] {0};
		ChainingObservable<Integer> sub2 = sub.act(value -> received2[0] = value);
		int [] received3 = new int[] {0};
		sub2.act(value -> received3[0] = value);

		for(int i = 1; i < 30; i++) {
			obs.onNext(i);
			assertEquals(i, received[0]);
			assertEquals(i, received2[0]);
			assertEquals(i, received3[0]);
		}
		final int finalValue = received[0];
		sub2.unsubscribe();
		for(int i = 1; i < 30; i++) {
			obs.onNext(i);
			assertEquals(finalValue, received[0]);
			assertEquals(finalValue, received2[0]);
			assertEquals(finalValue, received3[0]);
		}
	}

	/** Tests {@link Observable#completed()} */
	@Test
	public void completed() {
		SimpleObservable<Integer> obs = new SimpleObservable<>();
		int [] received = new int[] {0};
		obs.completed().act(value -> received[0] = value);

		for(int i = 1; i < 30; i++) {
			obs.onNext(i);
			assertEquals(0, received[0]);
		}
		obs.onCompleted(10);
		assertEquals(10, received[0]);
	}

	/** Tests {@link ObservableValue#combine(TriFunction, ObservableValue, ObservableValue)} */
	@Test
	public void combine() {
		int [] events = new int[1];
		SimpleSettableValue<Integer> obs1 = new SimpleSettableValue<>(Integer.TYPE, false);
		obs1.set(0, null);
		SimpleSettableValue<Integer> obs2 = new SimpleSettableValue<>(Integer.TYPE, false);
		obs2.set(1, null);
		SimpleSettableValue<Integer> obs3 = new SimpleSettableValue<>(Integer.TYPE, false);
		obs3.set(0, null);
		int [] received = new int[] {0};
		obs1.combine((arg1, arg2, arg3) -> arg1 * arg2 + arg3, obs2, obs3).changes().act(event -> {
			events[0]++;
			received[0] = event.getNewValue();
		});
		for(int i = 1; i < 10; i++) {
			obs1.set(i + 3, null);
			obs2.set(i * 10, null);
			obs3.set(i, null);
			assertEquals(i * 3 + 1, events[0]);
			assertEquals((i + 3) * i * 10 + i, received[0]);
		}
	}

	/** Tests {@link ObservableValue#flatten(ObservableValue)} */
	@Test
	public void observableValueFlatten() {
		SimpleSettableValue<ObservableValue<Integer>> outer = new SimpleSettableValue<>(new TypeToken<ObservableValue<Integer>>() {}, false);
		SimpleSettableValue<Integer> inner1 = new SimpleSettableValue<>(Integer.TYPE, false);
		inner1.set(1, null);
		outer.set(inner1, null);
		SimpleSettableValue<Integer> inner2 = new SimpleSettableValue<>(Integer.TYPE, false);
		inner2.set(2, null);
		int [] received = new int[1];
		ObservableValue.flatten(outer).changes().act(value -> received[0] = value.getNewValue());

		assertEquals(1, received[0]);
		inner1.set(3, null);
		assertEquals(3, received[0]);
		inner2.set(4, null);
		assertEquals(3, received[0]);
		outer.set(inner2, null);
		assertEquals(4, received[0]);
		inner1.set(5, null);
		assertEquals(4, received[0]);
		inner2.set(6, null);
		assertEquals(6, received[0]);
	}

	/**
	 * Tests {@link ObservableValue#firstValue(TypeToken, java.util.function.Predicate, java.util.function.Supplier, ObservableValue...)}
	 */
	@Test
	public void observableFirstValue() {
		SimpleSettableValue<Integer> v1 = new SimpleSettableValue<>(TypeToken.of(Integer.class), true);
		SimpleSettableValue<Integer> v2 = new SimpleSettableValue<>(TypeToken.of(Integer.class), true);
		SimpleSettableValue<Integer> v3 = new SimpleSettableValue<>(TypeToken.of(Integer.class), true);
		ObservableValue<Integer> first = ObservableValue.firstValue(TypeToken.of(Integer.class), null, null, v1, v2, v3);

		assertEquals(null, first.get());
		v2.set(2, null);
		v3.set(3, null);
		assertEquals(Integer.valueOf(2), first.get());
		Integer[] reported = new Integer[1];
		int[] events = new int[1];
		Subscription sub = first.changes().act(evt -> {
			reported[0] = evt.getNewValue();
			events[0]++;
		});
		assertEquals(1, events[0]);
		assertEquals(Integer.valueOf(2), reported[0]);
		v3.set(4, null);
		assertEquals(1, events[0]);
		assertEquals(Integer.valueOf(2), reported[0]);
		v1.set(1, null);
		assertEquals(2, events[0]);
		assertEquals(Integer.valueOf(1), reported[0]);
		v2.set(3, null);
		assertEquals(2, events[0]);
		assertEquals(Integer.valueOf(1), reported[0]);
		v1.set(null, null);
		assertEquals(3, events[0]);
		assertEquals(Integer.valueOf(3), reported[0]);
		v2.set(null, null);
		assertEquals(4, events[0]);
		assertEquals(Integer.valueOf(4), reported[0]);
		v3.set(null, null);
		assertEquals(5, events[0]);
		assertEquals(null, reported[0]);
		v2.set(2, null);
		assertEquals(6, events[0]);
		assertEquals(Integer.valueOf(2), reported[0]);
		v3.set(3, null);
		assertEquals(6, events[0]);
		assertEquals(Integer.valueOf(2), reported[0]);
		sub.unsubscribe();
		v1.set(1, null);
		assertEquals(6, events[0]);
		assertEquals(Integer.valueOf(2), reported[0]);
	}
}
