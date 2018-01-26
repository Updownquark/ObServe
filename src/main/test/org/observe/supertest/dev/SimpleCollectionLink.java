package org.observe.supertest.dev;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.observe.collect.CollectionChangeType;
import org.observe.collect.DefaultObservableCollection;
import org.observe.collect.DefaultObservableSortedSet;
import org.observe.collect.FlowOptions;
import org.observe.collect.ObservableCollection.CollectionDataFlow;
import org.observe.supertest.dev.ObservableChainTester.TestValueType;
import org.qommons.Ternian;
import org.qommons.TestHelper;
import org.qommons.ValueHolder;
import org.qommons.collect.BetterList;
import org.qommons.collect.BetterSortedSet;
import org.qommons.collect.CircularArrayList;
import org.qommons.collect.MutableCollectionElement.StdMsg;
import org.qommons.tree.BetterTreeList;
import org.qommons.tree.BetterTreeSet;

import com.google.common.reflect.TypeToken;

public class SimpleCollectionLink<E> extends AbstractObservableCollectionLink<E, E> {
	public SimpleCollectionLink(ObservableCollectionChainLink<?, E> parent, TestValueType type, CollectionDataFlow<?, ?, E> flow,
		TestHelper helper) {
		super(parent, type, flow, helper, true);
	}

	@Override
	public void initialize(TestHelper helper) {
		// Populate the base collection with initial values.
		int length = (int) helper.getDouble(0, 100, 1000); // Aggressively tend smaller
		List<E> values = new ArrayList<>(length);
		for (int i = 0; i < length; i++)
			values.add(getSupplier().apply(helper));
		// We're not testing add or addAll here, but just initial value handling in the listeners
		// We're also not concerned with whether any of the values are illegal or duplicates.
		// The addAll method should not throw exceptions
		getCollection().addAll(values);

		super.initialize(helper);
		getExpected().addAll(getCollection());
	}

	@Override
	public void checkModifiable(List<CollectionOp<E>> ops, int subListStart, int subListEnd, TestHelper helper) {
		for (CollectionOp<E> op : ops) {
			if (op.type == CollectionChangeType.remove) {
				if (op.index < 0 && !getCollection().contains(op.value))
					op.reject(StdMsg.NOT_FOUND, false);
			}
		}
	}

	@Override
	public void fromBelow(List<CollectionOp<E>> ops, TestHelper helper) {
		modified(ops, helper, true);
	}

	@Override
	public void fromAbove(List<CollectionOp<E>> ops, TestHelper helper, boolean above) {
		modified(ops, helper, !above);
	}

	@Override
	public String toString() {
		if (getParent() != null)
			return "simple(" + getExtras() + ")";
		else
			return "base(" + getTestType() + getExtras() + ")";
	}

	@SuppressWarnings("deprecation")
	public static <E> AbstractObservableCollectionLink<E, E> createInitialLink(ObservableCollectionChainLink<?, E> parent,
		TestValueType type, TestHelper helper, int depth, Ternian withSorted, Comparator<? super E> compare) {
		TestValueType fType = type != null ? type : ObservableChainTester.nextType(helper);

		ValueHolder<AbstractObservableCollectionLink<E, E>> holder = new ValueHolder<>();
		TestHelper.RandomAction action = helper.createAction();
		if (withSorted != Ternian.TRUE) {
			action.or(1, () -> {
				// Simple tree-backed list
				BetterList<E> backing = new BetterTreeList<>(true);
				DefaultObservableCollection<E> base = new DefaultObservableCollection<>((TypeToken<E>) fType.getType(), backing);
				holder.accept(new SimpleCollectionLink<>(parent, fType, base.flow(), helper));
			});
			if (CircularArrayList.class.getAnnotation(Deprecated.class) == null) {
				action.or(1, () -> {
					BetterList<E> backing = new CircularArrayList<>();
					DefaultObservableCollection<E> base = new DefaultObservableCollection<>((TypeToken<E>) fType.getType(), backing);
					holder.accept(new SimpleCollectionLink<>(parent, fType, base.flow(), helper));
				});
			}
		}
		if (withSorted != Ternian.FALSE) {
			action.or(.5, () -> {
				// Tree-backed sorted set
				Comparator<? super E> compare2 = compare != null ? compare : SortedCollectionLink.compare(fType, helper);
				BetterSortedSet<E> backing = new BetterTreeSet<>(true, compare2);
				DefaultObservableSortedSet<E> base = new DefaultObservableSortedSet<>((TypeToken<E>) fType.getType(), backing);
				SimpleCollectionLink<E> simple = new SimpleCollectionLink<>(parent, fType, base.flow(), helper);
				holder.accept(new DistinctCollectionLink<>(simple, fType, base.flow(), base.flow(), helper, true,
					new FlowOptions.SimpleUniqueOptions(true), true));
			});
		}
		if (depth < 3) { // Don't create so many flattened layers
			action.or(1.25, () -> { // TODO Change this probability to .25
				holder.accept(FlattenedValueLink.createFlattenedLink(parent, fType, helper, depth, withSorted, compare));
			});
		}
		// TODO ObservableValue
		// TODO ObservableMultiMap
		// TODO ObservableMap
		// TODO ObservableTree?
		// TODO ObservableGraph?
		action.execute(null);
		return holder.get();
	}
}
