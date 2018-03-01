package org.observe.supertest.dev;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;

import org.observe.ObservableValue;
import org.observe.SimpleSettableValue;
import org.observe.collect.CollectionChangeType;
import org.observe.collect.ObservableCollection;
import org.observe.collect.ObservableCollection.CollectionDataFlow;
import org.observe.collect.ObservableSet;
import org.observe.collect.ObservableSortedSet;
import org.observe.supertest.dev.ObservableChainTester.TestValueType;
import org.qommons.Ternian;
import org.qommons.TestHelper;
import org.qommons.collect.MutableCollectionElement.StdMsg;

import com.google.common.reflect.TypeToken;

public class FlattenedValueLink<E> extends AbstractObservableCollectionLink<E, E> {
	private final SimpleSettableValue<? extends ObservableCollection<E>> theValue;
	private final List<AbstractObservableCollectionLink<E, E>> theContents;
	private final boolean isDistinct;
	private final boolean isSorted;
	private final Comparator<? super E> theCompare;
	private final int theDepth;
	private int theSelected;

	public FlattenedValueLink(ObservableCollectionChainLink<?, E> parent, TestValueType type,
		SimpleSettableValue<? extends ObservableCollection<E>> value, boolean distinct, boolean sorted, Comparator<? super E> compare,
			CollectionDataFlow<?, ?, E> flow, TestHelper helper, int depth) {
		super(parent, type, flow, helper, true);
		theValue = value;
		theContents = new ArrayList<>();
		isDistinct = distinct;
		isSorted = sorted;
		theCompare = compare;
		theDepth = depth;
	}

	@Override
	public void initialize(TestHelper helper) {
		int collections = helper.getInt(2, 5);
		for (int i = 0; i < collections; i++) {
			AbstractObservableCollectionLink<E, E> link = SimpleCollectionLink.createInitialLink(null, getTestType(), helper, theDepth + 1,
				Ternian.of(isSorted), theCompare);
			if (isDistinct && !(link.getCollection() instanceof ObservableSet))
				link = (AbstractObservableCollectionLink<E, E>) link.deriveDistinct(helper, true);
			link.initialize(helper);
			theContents.add(link);
		}

		theSelected = helper.getInt(0, theContents.size() + 1);
		if (theSelected < theContents.size()) {
			AbstractObservableCollectionLink<E, E> selected = theContents.get(theSelected);
			((SimpleSettableValue<ObservableCollection<E>>) theValue).set(selected.getCollection(), null);
			selected.setChild(this);
		}

		super.initialize(helper);
		if (theSelected < theContents.size())
			getExpected().addAll(theContents.get(theSelected).getExpected());
	}

	@Override
	public void tryModify(TestHelper helper) {
		if (helper.getBoolean(.75)) {
			if (theSelected < theContents.size() && helper.getBoolean(.75)) {
				theContents.get(theSelected).tryModify(helper);
			} else {
				int random = helper.getInt(0, theContents.size());
				if (helper.isReproducing() && random != theSelected)
					System.out.print("In [" + random + "]:" + theContents.get(random) + ": ");
				theContents.get(random).tryModify(helper);
			}
			return;
		}
		helper.placemark("Modification");
		int preSelected = theSelected;
		theSelected = helper.getInt(0, theContents.size() + 1);
		AbstractObservableCollectionLink<E, E> pre = preSelected < theContents.size() ? theContents.get(preSelected) : null;
		AbstractObservableCollectionLink<E, E> post = theSelected < theContents.size() ? theContents.get(theSelected) : null;
		if (helper.isReproducing())
			System.out.println("Switching from [" + preSelected + "]:" + pre + " to [" + theSelected + "]:" + post);
		if (theSelected < theContents.size())
			((SimpleSettableValue<ObservableCollection<E>>) theValue).set(theContents.get(theSelected).getCollection(), null);
		else
			theValue.set(null, null);
		if (preSelected == theSelected)
			return;
		List<CollectionOp<E>> ops = new ArrayList<>();
		if (preSelected < theContents.size()) {
			theContents.get(preSelected).setChild(null);
			ObservableCollection<E> c = theContents.get(preSelected).getCollection();
			int i = c.size() - 1;
			for (E value : c.reverse())
				ops.add(new CollectionOp<>(CollectionChangeType.remove, value, i--));
		}
		if (theSelected < theContents.size()) {
			ObservableCollection<E> c = theContents.get(theSelected).getCollection();
			int i = 0;
			for (E value : c)
				ops.add(new CollectionOp<>(CollectionChangeType.add, value, i++));
			theContents.get(theSelected).setChild(this);
		}
		modified(ops, helper, true);
	}

	@Override
	public void checkModifiable(List<CollectionOp<E>> ops, int subListStart, int subListEnd, TestHelper helper) {
		if (theSelected == theContents.size()) {
			for (CollectionOp<E> op : ops)
				op.reject(StdMsg.UNSUPPORTED_OPERATION, true);
		} else
			theContents.get(theSelected).checkModifiable(ops, subListStart, subListEnd, helper);
	}

	@Override
	public void fromBelow(List<CollectionOp<E>> ops, TestHelper helper) {
		modified(ops, helper, true);
	}

	@Override
	public void fromAbove(List<CollectionOp<E>> ops, TestHelper helper, boolean above) {
		if (ops.isEmpty())
			return;
		modified(ops, helper, false); // Since we don't call the super.tryModify, above will never be false
		theContents.get(theSelected).fromAbove(ops, helper, true);
	}

	@Override
	public void check(boolean transComplete) {
		super.check(transComplete);
		LinkedList<ObservableChainLink<?>> chain = new LinkedList<>();
		for (int i = 0; i < theContents.size(); i++) {
			ObservableChainLink<?> link = theContents.get(i);
			while (link != null) {
				chain.addFirst(link);
				link = link.getParent();
			}
			for (ObservableChainLink<?> lnk : chain)
				lnk.check(transComplete);
			chain.clear();
		}
	}

	@Override
	public String toString() {
		StringBuilder str = new StringBuilder("flattenedV(");
		if (theSelected == theContents.size())
			str.append("empty");
		else
			str.append('[').append(theSelected).append("]:").append(theContents.get(theSelected));
		str.append(getExtras()).append(')');
		return str.toString();
	}

	public static <E> FlattenedValueLink<E> createFlattenedLink(ObservableCollectionChainLink<?, E> parent, TestValueType type,
		TestHelper helper, int depth, Ternian withSorted, Comparator<? super E> compare) {
		boolean distinct, sorted;
		switch (withSorted) {
		case FALSE:
			distinct = helper.getBoolean(.2);
			sorted = false;
			break;
		case TRUE:
			distinct = sorted = true;
			break;
		default:
			distinct = helper.getBoolean(.2);
			sorted = distinct && helper.getBoolean();
			break;
		}
		if (sorted && compare == null)
			compare = SortedCollectionLink.compare(type, helper);

		SimpleSettableValue<? extends ObservableCollection<E>> value;
		ObservableCollection<E> collection;
		if (sorted) {
			value = new SimpleSettableValue<>(new TypeToken<ObservableSortedSet<E>>() {}, true);
			collection = ObservableSortedSet.flattenValue((ObservableValue<ObservableSortedSet<E>>) value, compare);
		} else if (distinct) {
			value = new SimpleSettableValue<>(new TypeToken<ObservableSet<E>>() {}, true);
			collection = ObservableSet.flattenValue((ObservableValue<ObservableSet<E>>) value);
		} else {
			value = new SimpleSettableValue<>(new TypeToken<ObservableCollection<E>>() {}, true);
			collection = ObservableCollection.flattenValue(value);
		}
		return new FlattenedValueLink<>(parent, type, value, distinct, sorted, compare, collection.flow(), helper, depth);
	}
}
