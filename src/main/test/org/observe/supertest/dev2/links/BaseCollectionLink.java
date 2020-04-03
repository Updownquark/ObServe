package org.observe.supertest.dev2.links;

import java.util.Comparator;

import org.observe.collect.CollectionChangeType;
import org.observe.collect.DefaultObservableCollection;
import org.observe.supertest.dev2.CollectionLinkElement;
import org.observe.supertest.dev2.CollectionSourcedLink;
import org.observe.supertest.dev2.ExpectedCollectionOperation;
import org.observe.supertest.dev2.ObservableCollectionLink;
import org.observe.supertest.dev2.ObservableCollectionTestDef;
import org.observe.supertest.dev2.TestValueType;
import org.qommons.Ternian;
import org.qommons.TestHelper;
import org.qommons.ValueHolder;
import org.qommons.collect.BetterList;
import org.qommons.collect.CollectionElement;
import org.qommons.tree.BetterTreeList;

import com.google.common.reflect.TypeToken;

public class BaseCollectionLink<T> extends ObservableCollectionLink<T, T> {
	public BaseCollectionLink(ObservableCollectionTestDef<T> def, TestHelper helper) {
		super(null, def, helper);
	}

	@Override
	public void initialize(TestHelper helper) {
	}

	@Override
	public boolean isAcceptable(T value) {
		return true;
	}

	@Override
	public T getUpdateValue(T value) {
		return value;
	}

	@Override
	public void expectFromSource(ExpectedCollectionOperation<?, T> sourceOp) {
		throw new IllegalStateException("Unexpected source");
	}

	@Override
	public void expect(ExpectedCollectionOperation<?, T> derivedOp, OperationRejection rejection, int derivedIndex) {
		switch (derivedOp.getType()) {
		case add:
			throw new IllegalStateException("Should be using expectAdd");
		case remove:
			derivedOp.getElement().expectRemoval();
			int d = 0;
			for (CollectionSourcedLink<T, ?> derivedLink : getDerivedLinks()) {
				if (d != derivedIndex)
					derivedLink.expectFromSource(//
						new ExpectedCollectionOperation<>(derivedOp.getElement(), CollectionChangeType.remove,
							derivedOp.getElement().getValue(), derivedOp.getElement().getValue()));
				d++;
			}
			break;
		case set:
			T oldValue = derivedOp.getElement().get();
			derivedOp.getElement().setValue(derivedOp.getValue());
			d = 0;
			for (CollectionSourcedLink<T, ?> derivedLink : getDerivedLinks()) {
				if (d != derivedIndex)
					derivedLink.expectFromSource(//
						new ExpectedCollectionOperation<>(derivedOp.getElement(), CollectionChangeType.set, oldValue,
							derivedOp.getValue()));
				d++;
			}
			break;
		}
	}

	@Override
	public CollectionLinkElement<T, T> expectAdd(T value, CollectionLinkElement<?, T> after, CollectionLinkElement<?, T> before,
		boolean first, OperationRejection rejection, int derivedIndex) {
		for (CollectionElement<CollectionLinkElement<T, T>> el : getElements().elementsBetween(
			after == null ? null : after.getElementAddress(), false, //
				before == null ? null : before.getElementAddress(), false)) {
			if (el.get().wasAdded() && getCollection().equivalence().elementEquals(el.get().getCollectionValue(), value)) {
				el.get().expectAdded(value);
				int d = 0;
				for (CollectionSourcedLink<T, ?> derivedLink : getDerivedLinks()) {
					if (d != derivedIndex)
						derivedLink.expectFromSource(//
							new ExpectedCollectionOperation<>(el.get(), CollectionChangeType.add, null, el.get().getCollectionValue()));
					d++;
				}
				return el.get();
			}
		}
		throw new AssertionError("No new elements found to expect between " + after + " and " + before);
	}

	@Override
	protected void validate(CollectionLinkElement<T, T> element) {}


	static TestValueType nextType(TestHelper helper) {
		// The DOUBLE type is much less performant. There may be some value, but we'll use it less often.
		ValueHolder<TestValueType> result = new ValueHolder<>();
		TestHelper.RandomAction action = helper.createAction();
		action.or(10, () -> result.accept(TestValueType.INT));
		action.or(5, () -> result.accept(TestValueType.STRING));
		action.or(2, () -> result.accept(TestValueType.DOUBLE));
		action.execute(null);
		return result.get();
		// return TestValueType.values()[helper.getInt(0, TestValueType.values().length)];
	}

	@SuppressWarnings("deprecation")
	public static <E> BaseCollectionLink<E> createInitialLink(TestValueType type, TestHelper helper, int depth, Ternian withSorted,
		Comparator<? super E> compare) {
		TestValueType fType = type != null ? type : nextType(helper);

		ValueHolder<BaseCollectionLink<E>> holder = new ValueHolder<>();
		TestHelper.RandomAction action = helper.createAction();
		if (withSorted != Ternian.TRUE) {
			action.or(1, () -> {
				// Simple tree-backed list
				BetterList<E> backing = new BetterTreeList<>(true);
				DefaultObservableCollection<E> base = new DefaultObservableCollection<>((TypeToken<E>) fType.getType(), backing);
				holder.accept(
					new BaseCollectionLink<>(new ObservableCollectionTestDef<>(fType, base.flow(), base.flow(), true, true), helper));
			});
		}
		/*TODO
		 if (withSorted != Ternian.FALSE) {
			action.or(.5, () -> {
				// Tree-backed sorted set
				Comparator<? super E> compare2 = compare != null ? compare : SortedCollectionLink.compare(fType, helper);
				BetterSortedSet<E> backing = new BetterTreeSet<>(true, compare2);
				DefaultObservableSortedSet<E> base = new DefaultObservableSortedSet<>((TypeToken<E>) fType.getType(), backing);
				BaseCollectionLink<E> simple = new BaseCollectionLink<>(new ObservableCollectionTestDef<>(fType, base.flow(), true, true),
					helper);
				holder.accept(new DistinctCollectionLink<>(simple, fType, base.flow(), base.flow(), helper, true,
					new FlowOptions.SimpleUniqueOptions(true), true));
			});
		}
		if (depth < 3) { // Don't create so many flattened layers
			action.or(1.25, () -> { // TODO Change this probability to .25
				holder.accept(FlattenedValueLink.createFlattenedLink(fType, helper, depth, withSorted, compare));
			});
		}*/
		// TODO ObservableValue
		// TODO ObservableMultiMap
		// TODO ObservableMap
		// TODO ObservableTree?
		// TODO ObservableGraph?
		action.execute(null);
		return holder.get();
	}

	@Override
	public String toString() {
		return "base(" + getType() + ")";
	}
}
