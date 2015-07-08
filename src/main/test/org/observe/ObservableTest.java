package org.observe;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.junit.Test;
import org.observe.collect.ObservableCollection;
import org.observe.collect.ObservableList;
import org.observe.collect.ObservableOrderedCollection;
import org.observe.collect.ObservableSet;
import org.observe.collect.OrderedObservableElement;
import org.observe.collect.TransactableList;
import org.observe.collect.impl.ObservableArrayList;
import org.observe.collect.impl.ObservableHashSet;
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
		ObservableHashSet<Integer> set = new ObservableHashSet<>(new Type(Integer.TYPE));
		Set<Integer> compare1 = new TreeSet<>();
		Set<Integer> correct = new TreeSet<>();
		Subscription sub = set.onElement(element -> {
			element.subscribe(new Observer<ObservableValueEvent<Integer>>() {
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
			set.add(i);
			correct.add(i);
			assertEquals(new TreeSet<>(set), compare1);
			assertEquals(correct, compare1);
		}
		for(int i = 0; i < 30; i++) {
			set.remove(i);
			correct.remove(i);
			assertEquals(new TreeSet<>(set), compare1);
			assertEquals(correct, compare1);
		}

		for(int i = 0; i < 30; i++) {
			set.add(i);
			correct.add(i);
			assertEquals(new TreeSet<>(set), compare1);
			assertEquals(correct, compare1);
		}
		sub.unsubscribe();
		for(int i = 30; i < 50; i++) {
			set.add(i);
			assertEquals(correct, compare1);
		}
		for(int i = 0; i < 30; i++) {
			set.remove(i);
			assertEquals(correct, compare1);
		}
	}

	/** Tests basic {@link ObservableList} functionality */
	@Test
	public void observableList() {
		ObservableArrayList<Integer> list = new ObservableArrayList<>(new Type(Integer.TYPE));
		List<Integer> compare1 = new ArrayList<>();
		List<Integer> correct = new ArrayList<>();
		Subscription sub = list.onElement(element -> {
			OrderedObservableElement<Integer> listEl = (OrderedObservableElement<Integer>) element;
			element.subscribe(new Observer<ObservableValueEvent<Integer>>() {
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
			list.add(i);
			correct.add(i);
			assertEquals(new ArrayList<>(list), compare1);
			assertEquals(correct, compare1);
		}
		for(int i = 0; i < 30; i++) {
			list.remove((Integer) i);
			correct.remove((Integer) i);
			assertEquals(new ArrayList<>(list), compare1);
			assertEquals(correct, compare1);
		}

		for(int i = 0; i < 30; i++) {
			list.add(i);
			correct.add(i);
			assertEquals(new ArrayList<>(list), compare1);
			assertEquals(correct, compare1);
		}
		sub.unsubscribe();
		for(int i = 30; i < 50; i++) {
			list.add(i);
			assertEquals(correct, compare1);
		}
		for(int i = 0; i < 30; i++) {
			list.remove((Integer) i);
			assertEquals(correct, compare1);
		}
	}

	/** Tests {@link ObservableSet#map(java.util.function.Function)} */
	@Test
	public void observableSetMap() {
		ObservableHashSet<Integer> set = new ObservableHashSet<>(new Type(Integer.TYPE));
		Set<Integer> compare1 = new TreeSet<>();
		Set<Integer> correct = new TreeSet<>();
		set.map(value -> value * 10).onElement(element -> {
			element.subscribe(new Observer<ObservableValueEvent<Integer>>() {
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
			set.add(i);
			correct.add(i * 10);
			assertEquals(correct, compare1);
		}
		for(int i = 0; i < 30; i++) {
			set.remove(i);
			correct.remove(i * 10);
			assertEquals(correct, compare1);
		}
	}

	/** Tests {@link ObservableSet#filter(java.util.function.Predicate)} */
	@Test
	public void observableSetFilter() {
		ObservableHashSet<Integer> set = new ObservableHashSet<>(new Type(Integer.TYPE));
		Set<Integer> compare1 = new TreeSet<>();
		Set<Integer> correct = new TreeSet<>();
		set.filter(value -> (value != null && value % 2 == 0)).onElement(element -> {
			element.subscribe(new Observer<ObservableValueEvent<Integer>>() {
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
			set.add(i);
			if(i % 2 == 0)
				correct.add(i);
			assertEquals(correct, compare1);
		}
		for(int i = 0; i < 30; i++) {
			set.remove(i);
			if(i % 2 == 0)
				correct.remove(i);
			assertEquals(correct, compare1);
		}
	}

	/** Tests {@link ObservableSet#filterMap(java.util.function.Function)} */
	@Test
	public void observableSetFilterMap() {
		ObservableHashSet<Integer> set = new ObservableHashSet<>(new Type(Integer.TYPE));
		Set<Integer> compare1 = new TreeSet<>();
		Set<Integer> correct = new TreeSet<>();
		set.filterMap(value -> {
			if(value == null || value % 2 != 0)
				return null;
			return value / 2;
		}).onElement(element -> {
			element.subscribe(new Observer<ObservableValueEvent<Integer>>() {
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
			set.add(i);
			if(i % 2 == 0)
				correct.add(i / 2);
			assertEquals(correct, compare1);
		}
		for(int i = 0; i < 30; i++) {
			set.remove(i);
			if(i % 2 == 0)
				correct.remove(i / 2);
			assertEquals(correct, compare1);
		}
	}

	/** Tests {@link ObservableSet#combine(ObservableValue, java.util.function.BiFunction)} */
	@Test
	public void observableSetCombine() {
		ObservableHashSet<Integer> set = new ObservableHashSet<>(new Type(Integer.TYPE));
		SimpleSettableValue<Integer> value1 = new SimpleSettableValue<>(Integer.TYPE, false);
		value1.set(1, null);
		List<Integer> compare1 = new ArrayList<>();
		Set<Integer> correct = new TreeSet<>();
		set.combine(value1, (v1, v2) -> v1 * v2).filter(value -> value != null && value % 3 == 0).onElement(element -> {
			element.subscribe(new Observer<ObservableValueEvent<Integer>>() {
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
			set.add(i);
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
			set.remove(i);
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
		ObservableArrayList<Integer> list = new ObservableArrayList<>(new Type(Integer.TYPE));
		ObservableSet<Integer> unique = ObservableSet.unique(list);
		List<Integer> compare1 = new ArrayList<>();
		Set<Integer> correct = new TreeSet<>();

		unique.onElement(element -> {
			element.subscribe(new Observer<ObservableValueEvent<Integer>>() {
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
			list.add(i);
			correct.add(i);
		}
		assertEquals(correct, new TreeSet<>(unique));
		assertEquals(correct, new TreeSet<>(compare1));
		assertEquals(correct.size(), compare1.size());

		for(int i = 0; i < 30; i++) {
			list.add(i);
		}
		assertEquals(correct, new TreeSet<>(unique));
		assertEquals(correct, new TreeSet<>(compare1));
		assertEquals(correct.size(), compare1.size());

		for(int i = 29; i >= 0; i--) {
			list.remove(30 + i);
		}
		assertEquals(correct, new TreeSet<>(unique));
		assertEquals(correct, new TreeSet<>(compare1));
		assertEquals(correct.size(), compare1.size());

		for(int i = 0; i < 30; i++) {
			list.add(i);
		}
		assertEquals(correct, new TreeSet<>(unique));
		assertEquals(correct, new TreeSet<>(compare1));
		assertEquals(correct.size(), compare1.size());

		for(int i = 29; i >= 0; i--) {
			list.remove(i);
		}
		assertEquals(correct, new TreeSet<>(unique));
		assertEquals(correct, new TreeSet<>(compare1));
		assertEquals(correct.size(), compare1.size());

		for(int i = 29; i >= 0; i--) {
			list.remove(i);
			correct.remove(i);
		}
		assertEquals(correct, new TreeSet<>(unique));
		assertEquals(correct, new TreeSet<>(compare1));
		assertEquals(correct.size(), compare1.size());
	}

	/** Tests {@link ObservableCollection#flatten(ObservableCollection)} */
	@Test
	public void observableCollectionFlatten() {
		ObservableHashSet<Integer> set1 = new ObservableHashSet<>(new Type(Integer.TYPE));
		ObservableHashSet<Integer> set2 = new ObservableHashSet<>(new Type(Integer.TYPE));
		ObservableHashSet<Integer> set3 = new ObservableHashSet<>(new Type(Integer.TYPE));
		ObservableArrayList<ObservableSet<Integer>> outer = new ObservableArrayList<>(new Type(ObservableSet.class,
			new Type(Integer.TYPE)));
		outer.add(set1);
		outer.add(set2);
		ObservableCollection<Integer> flat = ObservableCollection.flatten(outer);
		List<Integer> compare1 = new ArrayList<>();
		List<Integer> filtered = new ArrayList<>();
		flat.onElement(element -> {
			element.subscribe(new Observer<ObservableValueEvent<Integer>>() {
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
			element.subscribe(new Observer<ObservableValueEvent<Integer>>() {
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

		List<Integer> correct1 = new ArrayList<>();
		List<Integer> correct2 = new ArrayList<>();
		List<Integer> correct3 = new ArrayList<>();
		List<Integer> correct = new ArrayList<>();
		List<Integer> filteredCorrect = new ArrayList<>();

		for(int i = 0; i < 30; i++) {
			set1.add(i);
			set2.add(i * 10);
			set3.add(i * 100);
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

		outer.add(set3);
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

		outer.remove(set2);
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
			set1.remove(i);
			set2.remove(i * 10);
			set3.remove(i * 100);
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
		ObservableHashSet<ObservableValue<Integer>> set = new ObservableHashSet<>(new Type(Observable.class, new Type(
			Integer.TYPE)));
		set.add(obs1);
		set.add(obs2);
		Observable<Integer> folded = ObservableCollection.fold(set.map(value -> value.value()));
		int [] received = new int[1];
		folded.noInit().act(value -> received[0] = value);

		obs1.set(1, null);
		assertEquals(1, received[0]);
		obs2.set(2, null);
		assertEquals(2, received[0]);
		obs3.set(3, null);
		assertEquals(2, received[0]);
		set.add(obs3);
		assertEquals(3, received[0]); // Initial value fired
		obs3.set(4, null);
		assertEquals(4, received[0]);
		set.remove(obs2);
		obs2.set(5, null);
		assertEquals(4, received[0]);
	}

	/** Tests {@link ObservableList#map(java.util.function.Function)} */
	@Test
	public void observableListMap() {
		ObservableArrayList<Integer> list = new ObservableArrayList<>(new Type(Integer.TYPE));
		List<Integer> compare1 = new ArrayList<>();
		List<Integer> correct = new ArrayList<>();
		list.map(value -> value * 10).onElement(element -> {
			OrderedObservableElement<Integer> listEl = (OrderedObservableElement<Integer>) element;
			element.subscribe(new Observer<ObservableValueEvent<Integer>>() {
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
			list.add(i);
			correct.add(i * 10);
			assertEquals(correct, compare1);
		}
		for(int i = 0; i < 30; i++) {
			list.add(i);
			correct.add(i * 10);
			assertEquals(correct, compare1);
		}
		for(int i = 0; i < 30; i++) {
			list.remove(Integer.valueOf(i));
			correct.remove(Integer.valueOf(i * 10));
			assertEquals(correct, compare1);
		}
	}

	/** Tests {@link ObservableList#filter(java.util.function.Predicate)} */
	@Test
	public void observableListFilter() {
		ObservableArrayList<Integer> list = new ObservableArrayList<>(new Type(Integer.TYPE));
		List<Integer> compare1 = new ArrayList<>();
		List<Integer> correct = new ArrayList<>();
		list.filter(value -> value != null && value % 2 == 0).onElement(element -> {
			OrderedObservableElement<Integer> listEl = (OrderedObservableElement<Integer>) element;
			element.subscribe(new Observer<ObservableValueEvent<Integer>>() {
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
			list.add(i);
			if(i % 2 == 0)
				correct.add(i);
			assertEquals(correct, compare1);
		}
		for(int i = 0; i < 30; i++) {
			list.add(i);
			if(i % 2 == 0)
				correct.add(i);
			assertEquals(correct, compare1);
		}
		for(int i = 0; i < 30; i++) {
			list.remove(Integer.valueOf(i));
			if(i % 2 == 0)
				correct.remove(Integer.valueOf(i));
			assertEquals(correct, compare1);
		}
	}

	/** Slight variation on {@link #observableListFilter()} */
	@Test
	public void observableListFilter2() {
		ObservableArrayList<Integer> list = new ObservableArrayList<>(new Type(Integer.TYPE));
		List<Integer> compare0 = new ArrayList<>();
		List<Integer> correct0 = new ArrayList<>();
		List<Integer> compare1 = new ArrayList<>();
		List<Integer> correct1 = new ArrayList<>();
		List<Integer> compare2 = new ArrayList<>();
		List<Integer> correct2 = new ArrayList<>();

		list.filter(value -> value % 3 == 0).onElement(element -> {
			OrderedObservableElement<Integer> oel = (OrderedObservableElement<Integer>) element;
			element.subscribe(new Observer<ObservableValueEvent<Integer>>() {
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
			element.subscribe(new Observer<ObservableValueEvent<Integer>>() {
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
			element.subscribe(new Observer<ObservableValueEvent<Integer>>() {
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
			list.add(i);
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
			list.set(i, value);
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
			int value = list.get(i);
			list.remove(Integer.valueOf(value));
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
		ObservableArrayList<Integer> list = new ObservableArrayList<>(new Type(Integer.TYPE));
		List<Integer> compare1 = new ArrayList<>();
		List<Integer> correct = new ArrayList<>();
		list.filterMap(value -> {
			if(value == null || value % 2 != 0)
				return null;
			return value / 2;
		}).onElement(element -> {
			OrderedObservableElement<Integer> listEl = (OrderedObservableElement<Integer>) element;
			element.subscribe(new Observer<ObservableValueEvent<Integer>>() {
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
			list.add(i);
			if(i % 2 == 0)
				correct.add(i / 2);
			assertEquals(correct, compare1);
		}
		for(int i = 0; i < 30; i++) {
			list.add(i);
			if(i % 2 == 0)
				correct.add(i / 2);
			assertEquals(correct, compare1);
		}
		for(int i = 0; i < 30; i++) {
			list.remove(Integer.valueOf(i));
			if(i % 2 == 0)
				correct.remove(Integer.valueOf(i / 2));
			assertEquals(correct, compare1);
		}
	}

	/** Tests {@link ObservableList#combine(ObservableValue, java.util.function.BiFunction)} */
	@Test
	public void observableListCombine() {
		ObservableArrayList<Integer> list = new ObservableArrayList<>(new Type(Integer.TYPE));
		SimpleSettableValue<Integer> value1 = new SimpleSettableValue<>(Integer.TYPE, false);
		value1.set(1, null);
		List<Integer> compare1 = new ArrayList<>();
		List<Integer> correct = new ArrayList<>();
		list.combine(value1, (v1, v2) -> v1 * v2).filter(value -> value != null && value % 3 == 0).onElement(element -> {
			OrderedObservableElement<Integer> listEl = (OrderedObservableElement<Integer>) element;
			element.subscribe(new Observer<ObservableValueEvent<Integer>>() {
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
			list.add(i);
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
			list.remove(Integer.valueOf(i));
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
		ObservableArrayList<Integer> set1 = new ObservableArrayList<>(new Type(Integer.TYPE));
		ObservableArrayList<Integer> set2 = new ObservableArrayList<>(new Type(Integer.TYPE));
		ObservableArrayList<Integer> set3 = new ObservableArrayList<>(new Type(Integer.TYPE));
		ObservableArrayList<ObservableList<Integer>> outer = new ObservableArrayList<>(new Type(ObservableList.class, new Type(
			Integer.TYPE)));
		outer.add(set1);
		outer.add(set2);
		ObservableList<Integer> flat = ObservableList.flatten(outer);
		List<Integer> compare1 = new ArrayList<>();
		List<Integer> filtered = new ArrayList<>();
		flat.onElement(element -> {
			OrderedObservableElement<Integer> listEl = (OrderedObservableElement<Integer>) element;
			element.subscribe(new Observer<ObservableValueEvent<Integer>>() {
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
			element.subscribe(new Observer<ObservableValueEvent<Integer>>() {
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

		List<Integer> correct1 = new ArrayList<>();
		List<Integer> correct2 = new ArrayList<>();
		List<Integer> correct3 = new ArrayList<>();
		List<Integer> correct = new ArrayList<>();
		List<Integer> filteredCorrect = new ArrayList<>();

		for(int i = 0; i < 30; i++) {
			set1.add(i);
			set2.add(i * 10);
			set3.add(i * 100);
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
			assertEquals(new ArrayList<>(flat), compare1);
			assertEquals(flat.size(), compare1.size());
			assertEquals(correct, compare1);
			assertEquals(correct.size(), compare1.size());
			assertEquals(filteredCorrect, filtered);
			assertEquals(filteredCorrect.size(), filtered.size());
		}

		outer.add(set3);
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

		assertEquals(new ArrayList<>(flat), compare1);
		assertEquals(flat.size(), compare1.size());
		assertEquals(correct, compare1);
		assertEquals(correct.size(), compare1.size());
		assertEquals(filteredCorrect, filtered);
		assertEquals(filteredCorrect.size(), filtered.size());


		outer.remove(set2);
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

		assertEquals(new ArrayList<>(flat), compare1);
		assertEquals(flat.size(), compare1.size());
		assertEquals(correct, compare1);
		assertEquals(correct.size(), compare1.size());
		assertEquals(filteredCorrect, filtered);
		assertEquals(filteredCorrect.size(), filtered.size());

		for(int i = 0; i < 30; i++) {
			set1.remove((Integer) i);
			set2.remove((Integer) (i * 10));
			set3.remove((Integer) (i * 100));
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

			assertEquals(new ArrayList<>(flat), compare1);
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
		ObservableArrayList<Integer> list = new ObservableArrayList<>(new Type(Integer.TYPE));
		ObservableValue<Integer> found = list.find(value -> value % 3 == 0);
		Integer [] received = new Integer[] {0};
		found.act(value -> received[0] = value.getValue());
		Integer [] correct = new Integer[] {null};

		assertEquals(correct[0], received[0]);
		assertEquals(correct[0], found.get());
		for(int i = 1; i < 30; i++) {
			list.add(i);
			if(i % 3 == 0 && correct[0] == null)
				correct[0] = i;
			assertEquals(correct[0], received[0]);
			assertEquals(correct[0], found.get());
		}
		for(int i = 1; i < 30; i++) {
			list.remove(Integer.valueOf(i));
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
		ObservableArrayList<ObservableValue<Integer>> list = new ObservableArrayList<>(new Type(ObservableValue.class, new Type(
			Integer.TYPE)));
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
		list.addAll(java.util.Arrays.asList(value1, value2, value3, value4));

		Integer [] received = new Integer[1];
		ObservableUtils.flattenListValues(new Type(Integer.TYPE), list).find(value -> value % 3 == 0).value()
		.act(value -> received[0] = value);
		assertEquals(Integer.valueOf(3), received[0]);
		value3.set(4, null);
		assertEquals(null, received[0]);
		value4.set(6, null);
		assertEquals(Integer.valueOf(6), received[0]);
		list.remove(value4);
		assertEquals(null, received[0]);
		list.add(value5);
		assertEquals(Integer.valueOf(9), received[0]);
	}

	/** Tests {@link ObservableOrderedCollection#sort(ObservableCollection, java.util.Comparator)} */
	@Test
	public void sortedObservableList() {
		ObservableArrayList<Integer> list = new ObservableArrayList<>(new Type(Integer.TYPE));

		List<Integer> compare = new ArrayList<>();
		ObservableOrderedCollection.sort(list, null).onElement(element -> {
			OrderedObservableElement<Integer> orderedEl = (OrderedObservableElement<Integer>) element;
			element.subscribe(new Observer<ObservableValueEvent<Integer>>() {
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
			list.add(i);
			correct.add(i);
		}

		java.util.Collections.sort(correct);
		assertEquals(correct, compare);

		for(int i = 30; i >= 0; i--) {
			list.remove((Integer) i);
			correct.remove((Integer) i);

			assertEquals(correct, compare);
		}
	}

	/** Tests {@link ObservableOrderedCollection#flatten(ObservableOrderedCollection, java.util.Comparator)} */
	@Test
	public void observableOrderedFlatten() {
		observableOrderedFlatten(Comparable::compareTo);
		observableOrderedFlatten(null);
	}

	private void observableOrderedFlatten(java.util.Comparator<Integer> comparator) {
		ObservableArrayList<ObservableList<Integer>> outer = new ObservableArrayList<>(new Type(ObservableList.class, new Type(
			Integer.TYPE)));
		ObservableArrayList<Integer> list1 = new ObservableArrayList<>(new Type(Integer.TYPE));
		ObservableArrayList<Integer> list2 = new ObservableArrayList<>(new Type(Integer.TYPE));
		ObservableArrayList<Integer> list3 = new ObservableArrayList<>(new Type(Integer.TYPE));

		outer.add(list1);
		outer.add(list2);

		ArrayList<Integer> compare = new ArrayList<>();
		ArrayList<Integer> correct1 = new ArrayList<>();
		ArrayList<Integer> correct2 = new ArrayList<>();
		ArrayList<Integer> correct3 = new ArrayList<>();

		// Add data before the subscription because subscribing to non-empty, indexed, flattened collections is complicated
		for(int i = 0; i <= 30; i++) {
			if(i % 3 == 1) {
				list1.add(i);
				correct1.add(i);
			} else if(i % 3 == 0) {
				list2.add(i);
				correct2.add(i);
			} else {
				list3.add(i);
				correct3.add(i);
			}
		}

		Subscription sub = ObservableOrderedCollection.flatten(outer, comparator).onElement(element -> {
			OrderedObservableElement<Integer> orderedEl = (OrderedObservableElement<Integer>) element;
			element.subscribe(new Observer<ObservableValueEvent<Integer>>() {
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
		assertEquals(join(comparator, correct1, correct2), compare);

		outer.add(list3);
		assertEquals(join(comparator, correct1, correct2, correct3), compare);

		outer.remove(list2);
		assertEquals(join(comparator, correct1, correct3), compare);

		list1.remove((Integer) 16);
		correct1.remove((Integer) 16);
		assertEquals(join(comparator, correct1, correct3), compare);
		list1.add(list1.indexOf(19), 16);
		correct1.add(correct1.indexOf(19), 16);
		assertEquals(join(comparator, correct1, correct3), compare);

		sub.unsubscribe();
	}

	private static <T> List<T> join(Comparator<? super T> comparator, List<T>... correct) {
		ArrayList<T> ret = new ArrayList<>();
		for(List<T> c : correct)
			ret.addAll(c);
		if(comparator != null)
			java.util.Collections.sort(ret, comparator);
		return ret;
	}

	/** Tests {@link ObservableList#asList(ObservableCollection)} */
	@Test
	public void obervableListFromCollection() {
		ObservableHashSet<Integer> set = new ObservableHashSet<>(new Type(Integer.TYPE));
		ObservableList<Integer> list = ObservableList.asList(set);
		ArrayList<Integer> compare = new ArrayList<>();
		ArrayList<Integer> correct = new ArrayList<>();
		list.onElement(element -> {
			OrderedObservableElement<Integer> orderedEl = (OrderedObservableElement<Integer>) element;
			element.subscribe(new Observer<ObservableValueEvent<Integer>>() {
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

		int count = 30;
		for(int i = 0; i < count; i++) {
			set.add(i);
			correct.add(i);

			assertEquals(correct, compare);
		}
		for(int i = count - 1; i >= 0; i--) {
			if(i % 2 == 0) {
				set.remove(i);
				correct.remove(i); // By index
			}

			assertEquals(correct, compare);
		}
	}

	/** Tests basic transaction functionality on observable collections */
	@Test
	public void testTransactionsBasic() {
		// Use find() and changes() to test
		ObservableArrayList<Integer> list = new ObservableArrayList<>(new Type(Integer.TYPE));
		testTransactionsByFind(list, list);
		testTransactionsByChanges(list, list);
	}

	/** Tests transactions in {@link ObservableList#flatten(ObservableList) flattened} collections */
	@Test
	public void testTransactionsFlattened() {
		ObservableArrayList<Integer> list1 = new ObservableArrayList<>(new Type(Integer.TYPE));
		ObservableArrayList<Integer> list2 = new ObservableArrayList<>(new Type(Integer.TYPE));
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
		Subscription sub = observable.find(value -> value % 5 == 4).act(event -> {
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
		Transaction trans = controller.lock(true, null);
		assertEquals(2, findCount[0]);
		controller.add(0, 4);
		assertEquals(2, findCount[0]);
		trans.close();
		assertEquals(3, findCount[0]);
		assertEquals(4, (int) found[0]);

		sub.unsubscribe();
		controller.clear();
	}

	private void testTransactionsByChanges(ObservableList<Integer> observable, TransactableList<Integer> controller) {
		ArrayList<Integer> compare = new ArrayList<>(observable);
		ArrayList<Integer> correct = new ArrayList<>(observable);
		int [] changeCount = new int[1];
		Subscription sub = observable.changes().act(event -> {
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

		Transaction trans = controller.lock(true, null);
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

		sub.unsubscribe();
		controller.clear();
	}
}
