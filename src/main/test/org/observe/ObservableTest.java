package org.observe;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.junit.Test;
import org.observe.collect.*;
import org.observe.util.ObservableUtils;
import org.observe.util.Transaction;

import prisms.lang.Type;

/** Tests observable classes in the org.muis.core.rx package */
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
	public void subSubscription() {
		DefaultObservable<Integer> obs = new DefaultObservable<>();
		Observer<Integer> controller = obs.control(null);
		int [] received = new int[] {0};
		Subscription<Integer> sub = obs.act(value -> received[0] = value);
		int [] received2 = new int[] {0};
		Subscription<Integer> sub2 = sub.act(value -> received2[0] = value);
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
			assertEquals(i, received[0]);
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

	/** Tests {@link ObservableValue#flatten(Type, ObservableValue)} */
	@Test
	public void observableValueFlatten() {
		SimpleSettableValue<ObservableValue<Integer>> outer = new SimpleSettableValue<>(new Type(ObservableValue.class, new Type(
			Integer.TYPE)), false);
		SimpleSettableValue<Integer> inner1 = new SimpleSettableValue<>(Integer.TYPE, false);
		inner1.set(1, null);
		outer.set(inner1, null);
		SimpleSettableValue<Integer> inner2 = new SimpleSettableValue<>(Integer.TYPE, false);
		inner2.set(2, null);
		int [] received = new int[1];
		ObservableValue.flatten(new Type(Integer.TYPE), outer).act(value -> received[0] = value.getValue());

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

	/** Tests basic {@link ObservableSet} functionality */
	@Test
	public void observableSet() {
		DefaultObservableSet<Integer> set = new DefaultObservableSet<>(new Type(Integer.TYPE));
		Set<Integer> controller = set.control(null);
		Set<Integer> compare1 = new TreeSet<>();
		Set<Integer> correct = new TreeSet<>();
		Runnable sub = set.onElement(element -> {
			element.observe(new Observer<ObservableValueEvent<Integer>>() {
				@Override
				public <V extends ObservableValueEvent<Integer>> void onNext(V value) {
					compare1.add(value.getValue());
				}

				@Override
				public <V extends ObservableValueEvent<Integer>> void onCompleted(V value) {
					compare1.remove(value.getValue());
				}
			});
		});

		for(int i = 0; i < 30; i++) {
			controller.add(i);
			correct.add(i);
			assertEquals(new TreeSet<>(set), compare1);
			assertEquals(correct, compare1);
		}
		for(int i = 0; i < 30; i++) {
			controller.remove(i);
			correct.remove(i);
			assertEquals(new TreeSet<>(set), compare1);
			assertEquals(correct, compare1);
		}

		for(int i = 0; i < 30; i++) {
			controller.add(i);
			correct.add(i);
			assertEquals(new TreeSet<>(set), compare1);
			assertEquals(correct, compare1);
		}
		sub.run();
		for(int i = 30; i < 50; i++) {
			controller.add(i);
			assertEquals(correct, compare1);
		}
		for(int i = 0; i < 30; i++) {
			controller.remove(i);
			assertEquals(correct, compare1);
		}
	}

	/** Tests basic {@link ObservableList} functionality */
	@Test
	public void observableList() {
		DefaultObservableList<Integer> list = new DefaultObservableList<>(new Type(Integer.TYPE));
		List<Integer> controller = list.control(null);
		List<Integer> compare1 = new ArrayList<>();
		List<Integer> correct = new ArrayList<>();
		Runnable sub = list.onElement(element -> {
			OrderedObservableElement<Integer> listEl = (OrderedObservableElement<Integer>) element;
			element.observe(new Observer<ObservableValueEvent<Integer>>() {
				@Override
				public <V extends ObservableValueEvent<Integer>> void onNext(V value) {
					compare1.add(listEl.getIndex(), value.getValue());
				}

				@Override
				public <V extends ObservableValueEvent<Integer>> void onCompleted(V value) {
					compare1.remove(listEl.getIndex());
				}
			});
		});

		for(int i = 0; i < 30; i++) {
			controller.add(i);
			correct.add(i);
			assertEquals(new ArrayList<>(list), compare1);
			assertEquals(correct, compare1);
		}
		for(int i = 0; i < 30; i++) {
			controller.remove((Integer) i);
			correct.remove((Integer) i);
			assertEquals(new ArrayList<>(list), compare1);
			assertEquals(correct, compare1);
		}

		for(int i = 0; i < 30; i++) {
			controller.add(i);
			correct.add(i);
			assertEquals(new ArrayList<>(list), compare1);
			assertEquals(correct, compare1);
		}
		sub.run();
		for(int i = 30; i < 50; i++) {
			controller.add(i);
			assertEquals(correct, compare1);
		}
		for(int i = 0; i < 30; i++) {
			controller.remove((Integer) i);
			assertEquals(correct, compare1);
		}
	}

	/** Tests {@link ObservableSet#map(java.util.function.Function)} */
	@Test
	public void observableSetMap() {
		DefaultObservableSet<Integer> set = new DefaultObservableSet<>(new Type(Integer.TYPE));
		Set<Integer> controller = set.control(null);
		Set<Integer> compare1 = new TreeSet<>();
		Set<Integer> correct = new TreeSet<>();
		set.map(value -> value * 10).onElement(element -> {
			element.observe(new Observer<ObservableValueEvent<Integer>>() {
				@Override
				public <V extends ObservableValueEvent<Integer>> void onNext(V value) {
					compare1.add(value.getValue());
				}

				@Override
				public <V extends ObservableValueEvent<Integer>> void onCompleted(V value) {
					compare1.remove(value.getValue());
				}
			});
		});

		for(int i = 0; i < 30; i++) {
			controller.add(i);
			correct.add(i * 10);
			assertEquals(correct, compare1);
		}
		for(int i = 0; i < 30; i++) {
			controller.remove(i);
			correct.remove(i * 10);
			assertEquals(correct, compare1);
		}
	}

	/** Tests {@link ObservableSet#filter(java.util.function.Function)} */
	@Test
	public void observableSetFilter() {
		DefaultObservableSet<Integer> set = new DefaultObservableSet<>(new Type(Integer.TYPE));
		Set<Integer> controller = set.control(null);
		Set<Integer> compare1 = new TreeSet<>();
		Set<Integer> correct = new TreeSet<>();
		set.filter(value -> value != null && value % 2 == 0).onElement(element -> {
			element.observe(new Observer<ObservableValueEvent<Integer>>() {
				@Override
				public <V extends ObservableValueEvent<Integer>> void onNext(V value) {
					compare1.add(value.getValue());
				}

				@Override
				public <V extends ObservableValueEvent<Integer>> void onCompleted(V value) {
					compare1.remove(value.getValue());
				}
			});
		});

		for(int i = 0; i < 30; i++) {
			controller.add(i);
			if(i % 2 == 0)
				correct.add(i);
			assertEquals(correct, compare1);
		}
		for(int i = 0; i < 30; i++) {
			controller.remove(i);
			if(i % 2 == 0)
				correct.remove(i);
			assertEquals(correct, compare1);
		}
	}

	/** Tests {@link ObservableSet#filterMap(java.util.function.Function)} */
	@Test
	public void observableSetFilterMap() {
		DefaultObservableSet<Integer> set = new DefaultObservableSet<>(new Type(Integer.TYPE));
		Set<Integer> controller = set.control(null);
		Set<Integer> compare1 = new TreeSet<>();
		Set<Integer> correct = new TreeSet<>();
		set.filterMap(value -> {
			if(value == null || value % 2 != 0)
				return null;
			return value / 2;
		}).onElement(element -> {
			element.observe(new Observer<ObservableValueEvent<Integer>>() {
				@Override
				public <V extends ObservableValueEvent<Integer>> void onNext(V value) {
					compare1.add(value.getValue());
				}

				@Override
				public <V extends ObservableValueEvent<Integer>> void onCompleted(V value) {
					compare1.remove(value.getValue());
				}
			});
		});

		for(int i = 0; i < 30; i++) {
			controller.add(i);
			if(i % 2 == 0)
				correct.add(i / 2);
			assertEquals(correct, compare1);
		}
		for(int i = 0; i < 30; i++) {
			controller.remove(i);
			if(i % 2 == 0)
				correct.remove(i / 2);
			assertEquals(correct, compare1);
		}
	}

	/** Tests {@link ObservableSet#combine(ObservableValue, java.util.function.BiFunction)} */
	@Test
	// TODO This test hangs, not sure why, ever since I refactored the observable collections to not be observables
	public void observableSetCombine() {
		DefaultObservableSet<Integer> set = new DefaultObservableSet<>(new Type(Integer.TYPE));
		SimpleSettableValue<Integer> value1 = new SimpleSettableValue<>(Integer.TYPE, false);
		value1.set(1, null);
		Set<Integer> controller = set.control(null);
		List<Integer> compare1 = new ArrayList<>();
		Set<Integer> correct = new TreeSet<>();
		set.combine(value1, (v1, v2) -> v1 * v2).filter(value -> value != null && value % 3 == 0).onElement(element -> {
			element.observe(new Observer<ObservableValueEvent<Integer>>() {
				@Override
				public <V extends ObservableValueEvent<Integer>> void onNext(V event) {
					if(event.getOldValue() != null)
						compare1.remove(event.getOldValue());
					compare1.add(event.getValue());
				}

				@Override
				public <V extends ObservableValueEvent<Integer>> void onCompleted(V event) {
					compare1.remove(event.getValue());
				}
			});
		});

		for(int i = 0; i < 30; i++) {
			controller.add(i);
			int value = i * value1.get();
			if(value % 3 == 0)
				correct.add(value);
			assertEquals(correct, new TreeSet<>(compare1));
			assertEquals(correct.size(), compare1.size());
		}

		value1.set(3, null);
		correct.clear();
		for(int i = 0; i < 30; i++) {
			int value = i * value1.get();
			if(value % 3 == 0)
				correct.add(value);
		}
		assertEquals(correct, new TreeSet<>(compare1));
		assertEquals(correct.size(), compare1.size());

		value1.set(10, null);
		correct.clear();
		for(int i = 0; i < 30; i++) {
			int value = i * value1.get();
			if(value % 3 == 0)
				correct.add(value);
		}
		assertEquals(correct, new TreeSet<>(compare1));
		assertEquals(correct.size(), compare1.size());

		for(int i = 0; i < 30; i++) {
			controller.remove(i);
			int value = i * value1.get();
			if(value % 3 == 0)
				correct.remove(value);
			assertEquals(correct, new TreeSet<>(compare1));
			assertEquals(correct.size(), compare1.size());
		}
	}

	/** Tests {@link ObservableSet#unique(ObservableCollection)} */
	@Test
	public void observableSetUnique() {
		DefaultObservableList<Integer> list = new DefaultObservableList<>(new Type(Integer.TYPE));
		List<Integer> controller = list.control(null);
		ObservableSet<Integer> unique = ObservableSet.unique(list);
		List<Integer> compare1 = new ArrayList<>();
		Set<Integer> correct = new TreeSet<>();

		unique.onElement(element -> {
			element.observe(new Observer<ObservableValueEvent<Integer>>() {
				@Override
				public <V extends ObservableValueEvent<Integer>> void onNext(V event) {
					if(event.getOldValue() != null)
						compare1.remove(event.getOldValue());
					compare1.add(event.getValue());
				}

				@Override
				public <V extends ObservableValueEvent<Integer>> void onCompleted(V event) {
					compare1.remove(event.getValue());
				}
			});
		});

		for(int i = 0; i < 30; i++) {
			controller.add(i);
			correct.add(i);
		}
		assertEquals(correct, new TreeSet<>(unique));
		assertEquals(correct, new TreeSet<>(compare1));
		assertEquals(correct.size(), compare1.size());

		for(int i = 0; i < 30; i++) {
			controller.add(i);
		}
		assertEquals(correct, new TreeSet<>(unique));
		assertEquals(correct, new TreeSet<>(compare1));
		assertEquals(correct.size(), compare1.size());

		for(int i = 29; i >= 0; i--) {
			controller.remove(30 + i);
		}
		assertEquals(correct, new TreeSet<>(unique));
		assertEquals(correct, new TreeSet<>(compare1));
		assertEquals(correct.size(), compare1.size());

		for(int i = 0; i < 30; i++) {
			controller.add(i);
		}
		assertEquals(correct, new TreeSet<>(unique));
		assertEquals(correct, new TreeSet<>(compare1));
		assertEquals(correct.size(), compare1.size());

		for(int i = 29; i >= 0; i--) {
			controller.remove(i);
		}
		assertEquals(correct, new TreeSet<>(unique));
		assertEquals(correct, new TreeSet<>(compare1));
		assertEquals(correct.size(), compare1.size());

		for(int i = 29; i >= 0; i--) {
			controller.remove(i);
			correct.remove(i);
		}
		assertEquals(correct, new TreeSet<>(unique));
		assertEquals(correct, new TreeSet<>(compare1));
		assertEquals(correct.size(), compare1.size());
	}

	/** Tests {@link ObservableCollection#flatten(ObservableCollection)} */
	@Test
	public void observableCollectionFlatten() {
		DefaultObservableSet<Integer> set1 = new DefaultObservableSet<>(new Type(Integer.TYPE));
		DefaultObservableSet<Integer> set2 = new DefaultObservableSet<>(new Type(Integer.TYPE));
		DefaultObservableSet<Integer> set3 = new DefaultObservableSet<>(new Type(Integer.TYPE));
		DefaultObservableList<ObservableSet<Integer>> outer = new DefaultObservableList<>(new Type(ObservableSet.class,
			new Type(Integer.TYPE)));
		List<ObservableSet<Integer>> outerControl = outer.control(null);
		outerControl.add(set1);
		outerControl.add(set2);
		ObservableCollection<Integer> flat = ObservableCollection.flatten(outer);
		List<Integer> compare1 = new ArrayList<>();
		List<Integer> filtered = new ArrayList<>();
		flat.onElement(element -> {
			element.observe(new Observer<ObservableValueEvent<Integer>>() {
				@Override
				public <V extends ObservableValueEvent<Integer>> void onNext(V event) {
					if(event.getOldValue() != null)
						compare1.remove(event.getOldValue());
					compare1.add(event.getValue());
				}

				@Override
				public <V extends ObservableValueEvent<Integer>> void onCompleted(V event) {
					compare1.remove(event.getValue());
				}
			});
		});
		flat.filter(value -> value != null && value % 3 == 0).onElement(element -> {
			element.observe(new Observer<ObservableValueEvent<Integer>>() {
				@Override
				public <V extends ObservableValueEvent<Integer>> void onNext(V event) {
					if(event.getOldValue() != null)
						filtered.remove(event.getOldValue());
					filtered.add(event.getValue());
				}

				@Override
				public <V extends ObservableValueEvent<Integer>> void onCompleted(V event) {
					filtered.remove(event.getValue());
				}
			});
		});
		Set<Integer> controller1 = set1.control(null);
		Set<Integer> controller2 = set2.control(null);
		Set<Integer> controller3 = set3.control(null);

		List<Integer> correct1 = new ArrayList<>();
		List<Integer> correct2 = new ArrayList<>();
		List<Integer> correct3 = new ArrayList<>();
		List<Integer> correct = new ArrayList<>();
		List<Integer> filteredCorrect = new ArrayList<>();

		for(int i = 0; i < 30; i++) {
			controller1.add(i);
			controller2.add(i * 10);
			controller3.add(i * 100);
			correct1.add(i);
			correct2.add(i * 10);
			correct3.add(i * 100);
			correct.clear();
			correct.addAll(correct1);
			correct.addAll(correct2);
			filteredCorrect.clear();
			for(int j : correct1)
				if(j % 3 == 0)
					filteredCorrect.add(j);
			for(int j : correct2)
				if(j % 3 == 0)
					filteredCorrect.add(j);
			assertEquals(new TreeSet<>(flat), new TreeSet<>(compare1));
			assertEquals(flat.size(), compare1.size());
			assertEquals(new TreeSet<>(correct), new TreeSet<>(compare1));
			assertEquals(correct.size(), compare1.size());
			assertEquals(new TreeSet<>(filteredCorrect), new TreeSet<>(filtered));
			assertEquals(filteredCorrect.size(), filtered.size());
		}

		outerControl.add(set3);
		correct.clear();
		correct.addAll(correct1);
		correct.addAll(correct2);
		correct.addAll(correct3);
		filteredCorrect.clear();
		for(int j : correct1)
			if(j % 3 == 0)
				filteredCorrect.add(j);
		for(int j : correct2)
			if(j % 3 == 0)
				filteredCorrect.add(j);
		for(int j : correct3)
			if(j % 3 == 0)
				filteredCorrect.add(j);

		assertEquals(new TreeSet<>(flat), new TreeSet<>(compare1));
		assertEquals(flat.size(), compare1.size());
		assertEquals(new TreeSet<>(correct), new TreeSet<>(compare1));
		assertEquals(correct.size(), compare1.size());
		assertEquals(new TreeSet<>(filteredCorrect), new TreeSet<>(filtered));
		assertEquals(filteredCorrect.size(), filtered.size());

		outerControl.remove(set2);
		correct.clear();
		correct.addAll(correct1);
		correct.addAll(correct3);
		filteredCorrect.clear();
		for(int j : correct1)
			if(j % 3 == 0)
				filteredCorrect.add(j);
		for(int j : correct3)
			if(j % 3 == 0)
				filteredCorrect.add(j);

		assertEquals(new TreeSet<>(flat), new TreeSet<>(compare1));
		assertEquals(flat.size(), compare1.size());
		assertEquals(new TreeSet<>(correct), new TreeSet<>(compare1));
		assertEquals(correct.size(), compare1.size());
		assertEquals(new TreeSet<>(filteredCorrect), new TreeSet<>(filtered));
		assertEquals(filteredCorrect.size(), filtered.size());

		for(int i = 0; i < 30; i++) {
			controller1.remove(i);
			controller2.remove(i * 10);
			controller3.remove(i * 100);
			correct1.remove((Integer) i);
			correct2.remove((Integer) (i * 10));
			correct3.remove((Integer) (i * 100));
			correct.clear();
			correct.addAll(correct1);
			correct.addAll(correct3);
			filteredCorrect.clear();
			for(int j : correct1)
				if(j % 3 == 0)
					filteredCorrect.add(j);
			for(int j : correct3)
				if(j % 3 == 0)
					filteredCorrect.add(j);

			assertEquals(new TreeSet<>(flat), new TreeSet<>(compare1));
			assertEquals(flat.size(), compare1.size());
			assertEquals(new TreeSet<>(correct), new TreeSet<>(compare1));
			assertEquals(correct.size(), compare1.size());
			assertEquals(new TreeSet<>(filteredCorrect), new TreeSet<>(filtered));
			assertEquals(filteredCorrect.size(), filtered.size());
		}
	}

	/** Tests {@link ObservableCollection#fold(ObservableCollection)} */
	@Test
	public void observableCollectionFold() {
		SimpleSettableValue<Integer> obs1 = new SimpleSettableValue<>(Integer.class, true);
		SimpleSettableValue<Integer> obs2 = new SimpleSettableValue<>(Integer.class, true);
		SimpleSettableValue<Integer> obs3 = new SimpleSettableValue<>(Integer.class, true);
		DefaultObservableSet<ObservableValue<Integer>> set = new DefaultObservableSet<>(new Type(Observable.class, new Type(
			Integer.TYPE)));
		Set<ObservableValue<Integer>> controller = set.control(null);
		controller.add(obs1);
		controller.add(obs2);
		Observable<Integer> folded = ObservableCollection.fold(set.map(value -> value.value()));
		int [] received = new int[1];
		folded.noInit().act(value -> received[0] = value);

		obs1.set(1, null);
		assertEquals(1, received[0]);
		obs2.set(2, null);
		assertEquals(2, received[0]);
		obs3.set(3, null);
		assertEquals(2, received[0]);
		controller.add(obs3);
		assertEquals(3, received[0]); // Initial value fired
		obs3.set(4, null);
		assertEquals(4, received[0]);
		controller.remove(obs2);
		obs2.set(5, null);
		assertEquals(4, received[0]);
	}

	/** Tests {@link ObservableList#map(java.util.function.Function)} */
	@Test
	public void observableListMap() {
		DefaultObservableList<Integer> list = new DefaultObservableList<>(new Type(Integer.TYPE));
		List<Integer> controller = list.control(null);
		List<Integer> compare1 = new ArrayList<>();
		List<Integer> correct = new ArrayList<>();
		list.map(value -> value * 10).onElement(element -> {
			OrderedObservableElement<Integer> listEl = (OrderedObservableElement<Integer>) element;
			element.observe(new Observer<ObservableValueEvent<Integer>>() {
				@Override
				public <V extends ObservableValueEvent<Integer>> void onNext(V value) {
					if(value.getOldValue() != null)
						compare1.set(listEl.getIndex(), value.getValue());
					else
						compare1.add(listEl.getIndex(), value.getValue());
				}

				@Override
				public <V extends ObservableValueEvent<Integer>> void onCompleted(V value) {
					compare1.remove(listEl.getIndex());
				}
			});
		});

		for(int i = 0; i < 30; i++) {
			controller.add(i);
			correct.add(i * 10);
			assertEquals(correct, compare1);
		}
		for(int i = 0; i < 30; i++) {
			controller.add(i);
			correct.add(i * 10);
			assertEquals(correct, compare1);
		}
		for(int i = 0; i < 30; i++) {
			controller.remove(Integer.valueOf(i));
			correct.remove(Integer.valueOf(i * 10));
			assertEquals(correct, compare1);
		}
	}

	/** Tests {@link ObservableList#filter(java.util.function.Function)} */
	@Test
	public void observableListFilter() {
		DefaultObservableList<Integer> list = new DefaultObservableList<>(new Type(Integer.TYPE));
		List<Integer> controller = list.control(null);
		List<Integer> compare1 = new ArrayList<>();
		List<Integer> correct = new ArrayList<>();
		list.filter(value -> value != null && value % 2 == 0).onElement(element -> {
			OrderedObservableElement<Integer> listEl = (OrderedObservableElement<Integer>) element;
			element.observe(new Observer<ObservableValueEvent<Integer>>() {
				@Override
				public <V extends ObservableValueEvent<Integer>> void onNext(V value) {
					if(value.getOldValue() != null)
						compare1.set(listEl.getIndex(), value.getValue());
					else
						compare1.add(listEl.getIndex(), value.getValue());
				}

				@Override
				public <V extends ObservableValueEvent<Integer>> void onCompleted(V value) {
					compare1.remove(listEl.getIndex());
				}
			});
		});

		for(int i = 0; i < 30; i++) {
			controller.add(i);
			if(i % 2 == 0)
				correct.add(i);
			assertEquals(correct, compare1);
		}
		for(int i = 0; i < 30; i++) {
			controller.add(i);
			if(i % 2 == 0)
				correct.add(i);
			assertEquals(correct, compare1);
		}
		for(int i = 0; i < 30; i++) {
			controller.remove(Integer.valueOf(i));
			if(i % 2 == 0)
				correct.remove(Integer.valueOf(i));
			assertEquals(correct, compare1);
		}
	}

	/** Slight variation on {@link #observableListFilter()} */
	@Test
	public void observableListFilter2() {
		DefaultObservableList<Integer> list = new DefaultObservableList<>(new Type(Integer.TYPE));
		List<Integer> controller = list.control(null);
		List<Integer> compare0 = new ArrayList<>();
		List<Integer> correct0 = new ArrayList<>();
		List<Integer> compare1 = new ArrayList<>();
		List<Integer> correct1 = new ArrayList<>();
		List<Integer> compare2 = new ArrayList<>();
		List<Integer> correct2 = new ArrayList<>();

		list.filter(value -> value % 3 == 0).onElement(element -> {
			OrderedObservableElement<Integer> oel = (OrderedObservableElement<Integer>) element;
			element.observe(new Observer<ObservableValueEvent<Integer>>() {
				@Override
				public <V extends ObservableValueEvent<Integer>> void onNext(V value) {
					if(value.getOldValue() != null)
						compare0.set(oel.getIndex(), value.getValue());
					else
						compare0.add(oel.getIndex(), value.getValue());
				}

				@Override
				public <V extends ObservableValueEvent<Integer>> void onCompleted(V value) {
					compare0.remove(oel.getIndex());
				}
			});
		});
		list.filter(value -> value % 3 == 1).onElement(element -> {
			OrderedObservableElement<Integer> oel = (OrderedObservableElement<Integer>) element;
			element.observe(new Observer<ObservableValueEvent<Integer>>() {
				@Override
				public <V extends ObservableValueEvent<Integer>> void onNext(V value) {
					if(value.getOldValue() != null)
						compare1.set(oel.getIndex(), value.getValue());
					else
						compare1.add(oel.getIndex(), value.getValue());
				}

				@Override
				public <V extends ObservableValueEvent<Integer>> void onCompleted(V value) {
					compare1.remove(oel.getIndex());
				}
			});
		});
		list.filter(value -> value % 3 == 2).onElement(element -> {
			OrderedObservableElement<Integer> oel = (OrderedObservableElement<Integer>) element;
			element.observe(new Observer<ObservableValueEvent<Integer>>() {
				@Override
				public <V extends ObservableValueEvent<Integer>> void onNext(V value) {
					if(value.getOldValue() != null)
						compare2.set(oel.getIndex(), value.getValue());
					else
						compare2.add(oel.getIndex(), value.getValue());
				}

				@Override
				public <V extends ObservableValueEvent<Integer>> void onCompleted(V value) {
					compare2.remove(oel.getIndex());
				}
			});
		});

		int count = 30;
		for(int i = 0; i < count; i++) {
			controller.add(i);
			switch (i % 3) {
			case 0:
				correct0.add(i);
				break;
			case 1:
				correct1.add(i);
				break;
			case 2:
				correct2.add(i);
				break;
			}
			assertEquals(correct0, compare0);
			assertEquals(correct1, compare1);
			assertEquals(correct2, compare2);
		}
		for(int i = 0; i < count; i++) {
			int value = i + 1;
			controller.set(i, value);
			switch (i % 3) {
			case 0:
				correct0.remove(Integer.valueOf(i));
				break;
			case 1:
				correct1.remove(Integer.valueOf(i));
				break;
			case 2:
				correct2.remove(Integer.valueOf(i));
				break;
			}
			switch (value % 3) {
			case 0:
				correct0.add(i / 3, value);
				break;
			case 1:
				correct1.add(i / 3, value);
				break;
			case 2:
				correct2.add(i / 3, value);
				break;
			}
			assertEquals(correct0, compare0);
			assertEquals(correct1, compare1);
			assertEquals(correct2, compare2);
		}
		for(int i = count - 1; i >= 0; i--) {
			int value = controller.get(i);
			controller.remove(Integer.valueOf(value));
			switch (value % 3) {
			case 0:
				correct0.remove(Integer.valueOf(value));
				break;
			case 1:
				correct1.remove(Integer.valueOf(value));
				break;
			case 2:
				correct2.remove(Integer.valueOf(value));
				break;
			}
			assertEquals(correct0, compare0);
			assertEquals(correct1, compare1);
			assertEquals(correct2, compare2);
		}
	}

	/** Tests {@link ObservableList#filterMap(java.util.function.Function)} */
	@Test
	public void observableListFilterMap() {
		DefaultObservableList<Integer> list = new DefaultObservableList<>(new Type(Integer.TYPE));
		List<Integer> controller = list.control(null);
		List<Integer> compare1 = new ArrayList<>();
		List<Integer> correct = new ArrayList<>();
		list.filterMap(value -> {
			if(value == null || value % 2 != 0)
				return null;
			return value / 2;
		}).onElement(element -> {
			OrderedObservableElement<Integer> listEl = (OrderedObservableElement<Integer>) element;
			element.observe(new Observer<ObservableValueEvent<Integer>>() {
				@Override
				public <V extends ObservableValueEvent<Integer>> void onNext(V value) {
					if(value.getOldValue() != null)
						compare1.set(listEl.getIndex(), value.getValue());
					else
						compare1.add(listEl.getIndex(), value.getValue());
				}

				@Override
				public <V extends ObservableValueEvent<Integer>> void onCompleted(V value) {
					compare1.remove(listEl.getIndex());
				}
			});
		});

		for(int i = 0; i < 30; i++) {
			controller.add(i);
			if(i % 2 == 0)
				correct.add(i / 2);
			assertEquals(correct, compare1);
		}
		for(int i = 0; i < 30; i++) {
			controller.add(i);
			if(i % 2 == 0)
				correct.add(i / 2);
			assertEquals(correct, compare1);
		}
		for(int i = 0; i < 30; i++) {
			controller.remove(Integer.valueOf(i));
			if(i % 2 == 0)
				correct.remove(Integer.valueOf(i / 2));
			assertEquals(correct, compare1);
		}
	}

	/** Tests {@link ObservableList#combine(ObservableValue, java.util.function.BiFunction)} */
	@Test
	// TODO This test hangs, not sure why, ever since I refactored the observable collections to not be observables
	public void observableListCombine() {
		DefaultObservableList<Integer> set = new DefaultObservableList<>(new Type(Integer.TYPE));
		SimpleSettableValue<Integer> value1 = new SimpleSettableValue<>(Integer.TYPE, false);
		value1.set(1, null);
		List<Integer> controller = set.control(null);
		List<Integer> compare1 = new ArrayList<>();
		List<Integer> correct = new ArrayList<>();
		set.combine(value1, (v1, v2) -> v1 * v2).filter(value -> value != null && value % 3 == 0).onElement(element -> {
			OrderedObservableElement<Integer> listEl = (OrderedObservableElement<Integer>) element;
			element.observe(new Observer<ObservableValueEvent<Integer>>() {
				@Override
				public <V extends ObservableValueEvent<Integer>> void onNext(V event) {
					if(event.getOldValue() != null)
						compare1.set(listEl.getIndex(), event.getValue());
					else
						compare1.add(listEl.getIndex(), event.getValue());
				}

				@Override
				public <V extends ObservableValueEvent<Integer>> void onCompleted(V event) {
					compare1.remove(listEl.getIndex());
				}
			});
		});

		for(int i = 0; i < 30; i++) {
			controller.add(i);
			int value = i * value1.get();
			if(value % 3 == 0)
				correct.add(value);
			assertEquals(correct, compare1);
			assertEquals(correct.size(), compare1.size());
		}

		value1.set(3, null);
		correct.clear();
		for(int i = 0; i < 30; i++) {
			int value = i * value1.get();
			if(value % 3 == 0)
				correct.add(value);
		}
		assertEquals(correct, compare1);
		assertEquals(correct.size(), compare1.size());

		value1.set(10, null);
		correct.clear();
		for(int i = 0; i < 30; i++) {
			int value = i * value1.get();
			if(value % 3 == 0)
				correct.add(value);
		}
		assertEquals(correct, compare1);
		assertEquals(correct.size(), compare1.size());

		for(int i = 0; i < 30; i++) {
			controller.remove(Integer.valueOf(i));
			int value = i * value1.get();
			if(value % 3 == 0)
				correct.remove(Integer.valueOf(value));
			assertEquals(correct, compare1);
			assertEquals(correct.size(), compare1.size());
		}
	}

	/** Tests {@link ObservableList#flatten(ObservableList)} */
	@Test
	public void observableListFlatten() {
		DefaultObservableList<Integer> set1 = new DefaultObservableList<>(new Type(Integer.TYPE));
		DefaultObservableList<Integer> set2 = new DefaultObservableList<>(new Type(Integer.TYPE));
		DefaultObservableList<Integer> set3 = new DefaultObservableList<>(new Type(Integer.TYPE));
		DefaultObservableList<ObservableList<Integer>> outer = new DefaultObservableList<>(new Type(ObservableList.class, new Type(
			Integer.TYPE)));
		List<ObservableList<Integer>> outerControl = outer.control(null);
		outerControl.add(set1);
		outerControl.add(set2);
		ObservableList<Integer> flat = ObservableList.flatten(outer);
		List<Integer> compare1 = new ArrayList<>();
		List<Integer> filtered = new ArrayList<>();
		flat.onElement(element -> {
			OrderedObservableElement<Integer> listEl = (OrderedObservableElement<Integer>) element;
			element.observe(new Observer<ObservableValueEvent<Integer>>() {
				@Override
				public <V extends ObservableValueEvent<Integer>> void onNext(V event) {
					if(event.getOldValue() != null)
						compare1.set(listEl.getIndex(), event.getOldValue());
					else
						compare1.add(listEl.getIndex(), event.getValue());
				}

				@Override
				public <V extends ObservableValueEvent<Integer>> void onCompleted(V event) {
					compare1.remove(listEl.getIndex());
				}
			});
		});
		flat.filter(value -> value != null && value % 3 == 0).onElement(element -> {
			OrderedObservableElement<Integer> listEl = (OrderedObservableElement<Integer>) element;
			element.observe(new Observer<ObservableValueEvent<Integer>>() {
				@Override
				public <V extends ObservableValueEvent<Integer>> void onNext(V event) {
					if(event.getOldValue() != null) {
						filtered.set(listEl.getIndex(), event.getValue());
					} else {
						filtered.add(listEl.getIndex(), event.getValue());
					}
				}

				@Override
				public <V extends ObservableValueEvent<Integer>> void onCompleted(V event) {
					filtered.remove(listEl.getIndex());
				}
			});
		});
		List<Integer> controller1 = set1.control(null);
		List<Integer> controller2 = set2.control(null);
		List<Integer> controller3 = set3.control(null);

		List<Integer> correct1 = new ArrayList<>();
		List<Integer> correct2 = new ArrayList<>();
		List<Integer> correct3 = new ArrayList<>();
		List<Integer> correct = new ArrayList<>();
		List<Integer> filteredCorrect = new ArrayList<>();

		for(int i = 0; i < 30; i++) {
			controller1.add(i);
			controller2.add(i * 10);
			controller3.add(i * 100);
			correct1.add(i);
			correct2.add(i * 10);
			correct3.add(i * 100);
			correct.clear();
			correct.addAll(correct1);
			correct.addAll(correct2);
			filteredCorrect.clear();
			for(int j : correct1)
				if(j % 3 == 0)
					filteredCorrect.add(j);
			for(int j : correct2)
				if(j % 3 == 0)
					filteredCorrect.add(j);
			assertEquals(flat, compare1);
			assertEquals(flat.size(), compare1.size());
			assertEquals(correct, compare1);
			assertEquals(correct.size(), compare1.size());
			assertEquals(filteredCorrect, filtered);
			assertEquals(filteredCorrect.size(), filtered.size());
		}

		outerControl.add(set3);
		correct.clear();
		correct.addAll(correct1);
		correct.addAll(correct2);
		correct.addAll(correct3);
		filteredCorrect.clear();
		for(int j : correct1)
			if(j % 3 == 0)
				filteredCorrect.add(j);
		for(int j : correct2)
			if(j % 3 == 0)
				filteredCorrect.add(j);
		for(int j : correct3)
			if(j % 3 == 0)
				filteredCorrect.add(j);

		assertEquals(flat, compare1);
		assertEquals(flat.size(), compare1.size());
		assertEquals(correct, compare1);
		assertEquals(correct.size(), compare1.size());
		assertEquals(filteredCorrect, filtered);
		assertEquals(filteredCorrect.size(), filtered.size());


		outerControl.remove(set2);
		correct.clear();
		correct.addAll(correct1);
		correct.addAll(correct3);
		filteredCorrect.clear();
		for(int j : correct1)
			if(j % 3 == 0)
				filteredCorrect.add(j);
		for(int j : correct3)
			if(j % 3 == 0)
				filteredCorrect.add(j);

		assertEquals(flat, compare1);
		assertEquals(flat.size(), compare1.size());
		assertEquals(correct, compare1);
		assertEquals(correct.size(), compare1.size());
		assertEquals(filteredCorrect, filtered);
		assertEquals(filteredCorrect.size(), filtered.size());

		for(int i = 0; i < 30; i++) {
			controller1.remove((Integer) i);
			controller2.remove((Integer) (i * 10));
			controller3.remove((Integer) (i * 100));
			correct1.remove((Integer) i);
			correct2.remove((Integer) (i * 10));
			correct3.remove((Integer) (i * 100));
			correct.clear();
			correct.addAll(correct1);
			correct.addAll(correct3);
			filteredCorrect.clear();
			for(int j : correct1)
				if(j % 3 == 0)
					filteredCorrect.add(j);
			for(int j : correct3)
				if(j % 3 == 0)
					filteredCorrect.add(j);
			assertEquals(flat, compare1);
			assertEquals(flat.size(), compare1.size());
			assertEquals(correct, compare1);
			assertEquals(correct.size(), compare1.size());
			assertEquals(filteredCorrect, filtered);
			assertEquals(filteredCorrect.size(), filtered.size());
		}
	}

	/** Tests {@link ObservableList#find(java.util.function.Predicate)} */
	@Test
	public void observableListFind() {
		DefaultObservableList<Integer> list = new DefaultObservableList<>(new Type(Integer.TYPE));
		List<Integer> controller = list.control(null);
		ObservableValue<Integer> found = list.find(value -> value % 3 == 0);
		Integer [] received = new Integer[] {0};
		found.act(value -> received[0] = value.getValue());
		Integer [] correct = new Integer[] {null};

		assertEquals(correct[0], received[0]);
		assertEquals(correct[0], found.get());
		for(int i = 1; i < 30; i++) {
			controller.add(i);
			if(i % 3 == 0 && correct[0] == null)
				correct[0] = i;
			assertEquals(correct[0], received[0]);
			assertEquals(correct[0], found.get());
		}
		for(int i = 1; i < 30; i++) {
			controller.remove(Integer.valueOf(i));
			if(i % 3 == 0) {
				correct[0] += 3;
				if(correct[0] >= 30)
					correct[0] = null;
			}
			assertEquals(correct[0], received[0]);
			assertEquals(correct[0], found.get());
		}
	}

	/** Tests {@link ObservableUtils#flattenListValues(Type, ObservableList)} */
	@Test
	public void flattenListValues() {
		DefaultObservableList<ObservableValue<Integer>> list = new DefaultObservableList<>(new Type(ObservableValue.class, new Type(
			Integer.TYPE)));
		List<ObservableValue<Integer>> listControl = list.control(null);
		SimpleSettableValue<Integer> value1 = new SimpleSettableValue<>(Integer.TYPE, false);
		value1.set(1, null);
		SimpleSettableValue<Integer> value2 = new SimpleSettableValue<>(Integer.TYPE, false);
		value2.set(2, null);
		SimpleSettableValue<Integer> value3 = new SimpleSettableValue<>(Integer.TYPE, false);
		value3.set(3, null);
		SimpleSettableValue<Integer> value4 = new SimpleSettableValue<>(Integer.TYPE, false);
		value4.set(4, null);
		SimpleSettableValue<Integer> value5 = new SimpleSettableValue<>(Integer.TYPE, false);
		value5.set(9, null);
		listControl.addAll(java.util.Arrays.asList(value1, value2, value3, value4));

		Integer [] received = new Integer[1];
		ObservableUtils.flattenListValues(new Type(Integer.TYPE), list).find(value -> value % 3 == 0).value()
		.act(value -> received[0] = value);
		assertEquals(Integer.valueOf(3), received[0]);
		value3.set(4, null);
		assertEquals(null, received[0]);
		value4.set(6, null);
		assertEquals(Integer.valueOf(6), received[0]);
		listControl.remove(value4);
		assertEquals(null, received[0]);
		listControl.add(value5);
		assertEquals(Integer.valueOf(9), received[0]);
	}

	/** Tests {@link ObservableOrderedCollection#sort(ObservableCollection, java.util.Comparator)} */
	@Test
	public void sortedObservableList() {
		DefaultObservableList<Integer> list = new DefaultObservableList<>(new Type(Integer.TYPE));
		List<Integer> controller = list.control(null);

		List<Integer> compare = new ArrayList<>();
		ObservableOrderedCollection.sort(list, null).onElement(element -> {
			OrderedObservableElement<Integer> orderedEl = (OrderedObservableElement<Integer>) element;
			element.observe(new Observer<ObservableValueEvent<Integer>>() {
				@Override
				public <V extends ObservableValueEvent<Integer>> void onNext(V event) {
					if(event.getOldValue() == null)
						compare.add(orderedEl.getIndex(), event.getValue());
					else
						compare.set(orderedEl.getIndex(), event.getValue());
				}

				@Override
				public <V extends ObservableValueEvent<Integer>> void onCompleted(V event) {
					compare.remove(orderedEl.getIndex());
				}
			});
		});

		List<Integer> correct = new ArrayList<>();

		for(int i = 30; i >= 0; i--) {
			controller.add(i);
			correct.add(i);
		}

		java.util.Collections.sort(correct);
		assertEquals(correct, compare);

		for(int i = 30; i >= 0; i--) {
			controller.remove((Integer) i);
			correct.remove((Integer) i);

			assertEquals(correct, compare);
		}
	}

	/** Tests {@link ObservableOrderedCollection#flatten(ObservableOrderedCollection, java.util.Comparator)} */
	@Test
	public void observableOrderedFlatten() {
		DefaultObservableList<ObservableList<Integer>> outer = new DefaultObservableList<>(new Type(ObservableList.class, new Type(
			Integer.TYPE)));
		DefaultObservableList<Integer> list1 = new DefaultObservableList<>(new Type(Integer.TYPE));
		DefaultObservableList<Integer> list2 = new DefaultObservableList<>(new Type(Integer.TYPE));
		DefaultObservableList<Integer> list3 = new DefaultObservableList<>(new Type(Integer.TYPE));

		List<ObservableList<Integer>> outerControl = outer.control(null);
		List<Integer> control1 = list1.control(null);
		List<Integer> control2 = list2.control(null);
		List<Integer> control3 = list3.control(null);

		outerControl.add(list1);
		outerControl.add(list2);

		ArrayList<Integer> compare = new ArrayList<>();

		ArrayList<Integer> correct = new ArrayList<>();

		for(int i = 0; i <= 30; i++) {
			if(i % 3 == 1) {
				control1.add(i);
				correct.add(i);
			} else if(i % 3 == 0) {
				control2.add(i);
				correct.add(i);
			} else {
				control3.add(i);
			}
		}

		ObservableOrderedCollection.flatten(outer, null).onElement(element -> {
			OrderedObservableElement<Integer> orderedEl = (OrderedObservableElement<Integer>) element;
			element.observe(new Observer<ObservableValueEvent<Integer>>() {
				@Override
				public <V extends ObservableValueEvent<Integer>> void onNext(V event) {
					if(event.getOldValue() == null)
						compare.add(orderedEl.getIndex(), event.getValue());
					else
						compare.set(orderedEl.getIndex(), event.getValue());
				}

				@Override
				public <V extends ObservableValueEvent<Integer>> void onCompleted(V event) {
					compare.remove(orderedEl.getIndex());
				}
			});
		});
		assertEquals(correct, compare);

		outerControl.add(list3);
		correct.clear();
		for(int i = 0; i <= 30; i++)
			correct.add(i);
		assertEquals(correct, compare);

		outerControl.remove(list2);
		correct.clear();
		for(int i = 0; i <= 30; i++)
			if(i % 3 != 0)
				correct.add(i);
		assertEquals(correct, compare);

		control1.remove((Integer) 16);
		correct.remove((Integer) 16);
		assertEquals(correct, compare);
		control1.add(control1.indexOf(19), 16);
		correct.add(correct.indexOf(17), 16);
		assertEquals(correct, compare);
	}

	/** Tests basic transaction functionality on observable collections */
	@Test
	public void testTransactionsBasic() {
		// Use find() and changes() to test
		DefaultObservableList<Integer> list = new DefaultObservableList<>(new Type(Integer.TYPE));
		testTransactionsByFind(list, list);
		testTransactionsByChanges(list, list);
	}

	/** Tests transactions in {@link ObservableList#flatten(ObservableList) flattened} collections */
	@Test
	public void testTransactionsFlattened() {
		DefaultObservableList<Integer> list1 = new DefaultObservableList<>(new Type(Integer.TYPE));
		DefaultObservableList<Integer> list2 = new DefaultObservableList<>(new Type(Integer.TYPE));
		ObservableList<Integer> flat = ObservableList.flattenLists(list1, list2);
		list1.add(50);

		testTransactionsByFind(flat, list2);
		testTransactionsByChanges(flat, list2);
	}

	/**
	 * Tests transactions caused by {@link ObservableCollection#combine(ObservableValue, java.util.function.BiFunction) combining} a list
	 * with an observable value
	 */
	@Test
	public void testTransactionsCombined() {
		// TODO
	}

	/** Tests transactions caused by {@link ObservableCollection#refresh(Observable) refreshing} on an observable */
	@Test
	public void testTransactionsRefresh() {
		// TODO
	}

	private void testTransactionsByFind(ObservableList<Integer> observable, TransactableList<Integer> controller) {
		Integer [] found = new Integer[1];
		int [] findCount = new int[1];
		Runnable sub = observable.find(value -> value % 5 == 4).act(event -> {
			findCount[0]++;
			found[0] = event.getValue();
		});

		assertEquals(1, findCount[0]);
		controller.add(0);
		assertEquals(1, findCount[0]);
		controller.add(3);
		assertEquals(1, findCount[0]);
		controller.add(9);
		assertEquals(2, findCount[0]);
		assertEquals(9, (int) found[0]);
		Transaction trans = controller.startTransaction(null);
		assertEquals(2, findCount[0]);
		controller.add(0, 4);
		assertEquals(2, findCount[0]);
		trans.close();
		assertEquals(3, findCount[0]);
		assertEquals(4, (int) found[0]);

		sub.run();
		controller.clear();
	}

	private void testTransactionsByChanges(ObservableList<Integer> observable, TransactableList<Integer> controller) {
		ArrayList<Integer> compare = new ArrayList<>(observable);
		ArrayList<Integer> correct = new ArrayList<>(observable);
		int [] changeCount = new int[1];
		Runnable sub = observable.changes().act(event -> {
			changeCount[0]++;
			for(int i = 0; i < event.indexes.size(); i++) {
				switch (event.type) {
				case add:
					compare.add(event.indexes.get(i), event.values.get(i));
					break;
				case remove:
					compare.remove(event.indexes.get(i));
					break;
				case set:
					compare.set(event.indexes.get(i), event.values.get(i));
					break;
				}
			}
		});
		assertEquals(0, changeCount[0]);

		for(int i = 0; i < 30; i++) {
			assertEquals(i, changeCount[0]);
			int toAdd = (int) (Math.random() * 2000000) - 1000000;
			controller.add(toAdd);
			correct.add(toAdd);
			assertEquals(correct, new ArrayList<>(observable));
			assertEquals(correct, compare);
		}
		assertEquals(30, changeCount[0]);

		Transaction trans = controller.startTransaction(null);
		controller.clear();
		correct.clear();
		correct.addAll(observable);
		for(int i = 0; i < 30; i++) {
			int toAdd = (int) (Math.random() * 2000000) - 1000000;
			controller.add(toAdd);
			correct.add(toAdd);
			assertEquals(correct, new ArrayList<>(observable));
		}
		assertEquals(31, changeCount[0]);
		trans.close();
		assertEquals(32, changeCount[0]);
		assertEquals(correct, compare);

		sub.run();
		controller.clear();
	}
}
