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
		obs.act(value -> received[0] = value.getValue());
		for(int i = 1; i < 10; i++) {
			obs.set(i, null);
			assertEquals(i, received[0]);
		}
	}

	/** Tests {@link ObservableValue#mapV(java.util.function.Function)} */
	@Test
	public void valueMap() {
		SimpleSettableValue<Integer> obs = new SimpleSettableValue<>(Integer.TYPE, false);
		obs.set(0, null);
		int [] received = new int[] {0};
		obs.mapV(value -> value * 10).act(value -> received[0] = value.getValue());

		for(int i = 1; i < 10; i++) {
			obs.set(i, null);
			assertEquals(i * 10, received[0]);
		}
	}

	/** Tests {@link Observable#filter(java.util.function.Function)} */
	@Test
	public void filter() {
		DefaultObservable<Integer> obs = new DefaultObservable<>();
		Observer<Integer> controller = obs.control(null);
		int [] received = new int[] {0};
		obs.filter(value -> value % 3 == 0).act(value -> received[0] = value);

		for(int i = 1; i < 30; i++) {
			controller.onNext(i);
			assertEquals((i / 3) * 3, received[0]);
		}
	}

	/** Tests {@link Observable#take(int)} */
	@Test
	public void takeNumber() {
		DefaultObservable<Integer> obs = new DefaultObservable<>();
		Observer<Integer> controller = obs.control(null);
		int [] received = new int[] {0};
		boolean [] complete = new boolean[1];
		Observable<Integer> take = obs.take(10);
		take.act(value -> received[0] = value);
		take.completed().act(value -> complete[0] = true);

		for(int i = 1; i < 30; i++) {
			controller.onNext(i);
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
		DefaultObservable<Integer> obs = new DefaultObservable<>();
		Observer<Integer> controller = obs.control(null);
		DefaultObservable<Boolean> stop = new DefaultObservable<>();
		Observer<Boolean> stopControl = stop.control(null);
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
			controller.onNext(i);
			assertEquals(i, received[0]);
			assertEquals(i, count[0]);
		}
		stopControl.onNext(true);
		assertEquals(true, complete[0]);
		final int finalValue = received[0];
		for(int i = 1; i <= 30; i++) {
			controller.onNext(i);
			assertEquals(finalValue, received[0]);
			assertEquals(30, count[0]);
		}

		complete[0] = false;
		controller.onCompleted(0);
		assertEquals(false, complete[0]);

		obs = new DefaultObservable<>();
		controller = obs.control(null);
		complete[0] = false;
		take = obs.takeUntil(stop);
		take.completed().act(value -> complete[0] = true);

		controller.onCompleted(0);
		assertEquals(true, complete[0]);
	}

	/** Tests {@link ObservableValue#takeUntil(Observable)} */
	@Test
	public void valueTakeUntil() {
		SimpleSettableValue<Integer> obs = new SimpleSettableValue<>(Integer.TYPE, false);
		obs.set(0, null);
		DefaultObservable<Boolean> stop = new DefaultObservable<>();
		Observer<Boolean> stopControl = stop.control(null);
		int [] received = new int[] {0};
		int [] count = new int[1];
		boolean [] complete = new boolean[1];
		ObservableValue<Integer> take = obs.takeUntil(stop);
		take.act(value -> {
			count[0]++;
			received[0] = value.getValue();
		});
		take.completed().act(value -> complete[0] = true);

		for(int i = 1; i <= 30; i++) {
			obs.set(i, null);
			assertEquals(i, received[0]);
			assertEquals(i + 1, count[0]); // Plus 1 because of the initialization
		}
		stopControl.onNext(true);
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
		DefaultObservable<Integer> obs = new DefaultObservable<>();
		Observer<Integer> controller = obs.control(null);

		int [] received = new int[1];
		obs.skip(5).act(value -> received[0] = value);

		for(int i = 0; i < 10; i++) {
			controller.onNext(i);
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
		DefaultObservable<Integer> obs = new DefaultObservable<>();
		Observer<Integer> controller = obs.control(null);
		int [] received = new int[] {0};
		ChainingObservable<Integer> sub = obs.chain().act(value -> received[0] = value);
		int [] received2 = new int[] {0};
		ChainingObservable<Integer> sub2 = sub.act(value -> received2[0] = value);
		int [] received3 = new int[] {0};
		sub2.act(value -> received3[0] = value);

		for(int i = 1; i < 30; i++) {
			controller.onNext(i);
			assertEquals(i, received[0]);
			assertEquals(i, received2[0]);
			assertEquals(i, received3[0]);
		}
		final int finalValue = received[0];
		sub2.unsubscribe();
		for(int i = 1; i < 30; i++) {
			controller.onNext(i);
			assertEquals(finalValue, received[0]);
			assertEquals(finalValue, received2[0]);
			assertEquals(finalValue, received3[0]);
		}
	}

	/** Tests {@link Observable#completed()} */
	@Test
	public void completed() {
		DefaultObservable<Integer> obs = new DefaultObservable<>();
		Observer<Integer> controller = obs.control(null);
		int [] received = new int[] {0};
		obs.completed().act(value -> received[0] = value);

		for(int i = 1; i < 30; i++) {
			controller.onNext(i);
			assertEquals(0, received[0]);
		}
		controller.onCompleted(10);
		assertEquals(10, received[0]);
	}

	/** Tests {@link ObservableValue#combineV(TriFunction, ObservableValue, ObservableValue)} */
	@Test
	public void combine() {
		int [] events = new int[1];
		SimpleSettableValue<Integer> obs1 = new SimpleSettableValue<>(Integer.TYPE, false);
		obs1.set(0, null);
		SimpleSettableValue<Integer> obs2 = new SimpleSettableValue<>(Integer.TYPE, false);
		obs2.set(10, null);
		SimpleSettableValue<Integer> obs3 = new SimpleSettableValue<>(Integer.TYPE, false);
		obs3.set(0, null);
		int [] received = new int[] {0};
		obs1.combineV((arg1, arg2, arg3) -> arg1 * arg2 + arg3, obs2, obs3).act(event -> {
			events[0]++;
			received[0] = event.getValue();
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
		ObservableValue.flatten(outer).act(value -> received[0] = value.getValue());

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
}
