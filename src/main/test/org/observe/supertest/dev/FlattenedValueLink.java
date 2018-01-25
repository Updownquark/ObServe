package org.observe.supertest.dev;

import java.util.ArrayList;
import java.util.List;

import org.observe.ObservableValue;
import org.observe.SimpleSettableValue;
import org.observe.collect.CollectionChangeType;
import org.observe.collect.ObservableCollection;
import org.observe.collect.ObservableCollection.CollectionDataFlow;
import org.observe.collect.ObservableSet;
import org.observe.collect.ObservableSortedSet;
import org.observe.supertest.dev.ObservableChainTester.TestValueType;
import org.qommons.TestHelper;
import org.qommons.Transaction;
import org.qommons.collect.MutableCollectionElement.StdMsg;

import com.google.common.reflect.TypeToken;

public class FlattenedValueLink<E> extends AbstractObservableCollectionLink<E, E> {
	private final SimpleSettableValue<? extends ObservableCollection<E>> theValue;
	private final List<AbstractObservableCollectionLink<E, E>> theContents;
	private final boolean isDistinct;
	private final boolean isSorted;
	private int theSelected;

	public FlattenedValueLink(ObservableCollectionChainLink<?, E> parent, TestValueType type,
		SimpleSettableValue<? extends ObservableCollection<E>> value, boolean distinct, boolean sorted, CollectionDataFlow<?, ?, E> flow,
			TestHelper helper) {
		super(parent, type, flow, helper, false, true);
		theValue = value;
		theContents = new ArrayList<>();
		isDistinct = distinct;
		isSorted = sorted;
	}

	@Override
	public void initialize(TestHelper helper) {
		int collections = helper.getInt(2, 5);
		for (int i = 0; i < collections; i++) {
			AbstractObservableCollectionLink<E, E> link = SimpleCollectionLink.createInitialLink(null, getTestType(), helper);
			if (isSorted && !(link.getCollection() instanceof ObservableSortedSet))
				link = (AbstractObservableCollectionLink<E, E>) link.deriveDistinctSorted(helper);
			else if (isDistinct && !(link.getCollection() instanceof ObservableSet))
				link = (AbstractObservableCollectionLink<E, E>) link.deriveDistinct(helper);
			link.initialize(helper);
			theContents.add(link);
		}

		theSelected = helper.getInt(0, theContents.size() + 1);
		if (theSelected < theContents.size()) {
			AbstractObservableCollectionLink<E, E> selected = theContents.get(theSelected);
			((SimpleSettableValue<ObservableCollection<E>>) theValue).set(selected.getCollection(), null);
			selected.setChild(this);
			for (E v : selected.getCollection())
				getExpected().add(v);
		}

		super.initialize(helper);
	}

	@Override
	public void tryModify(TestHelper helper) {
		if (helper.getBoolean(.75)) {
			if (theSelected < theContents.size() && helper.getBoolean(.75)) {
				theContents.get(theSelected).tryModify(helper);
			} else {
				int random = helper.getInt(0, theContents.size());
				theContents.get(random).tryModify(helper);
			}
			return;
		}
		int preSelected = theSelected;
		theSelected = helper.getInt(0, theContents.size() + 1);
		if (theSelected < theContents.size())
			((SimpleSettableValue<ObservableCollection<E>>) theValue).set(theContents.get(theSelected).getCollection(), null);
		else
			theValue.set(null, null);
		AbstractObservableCollectionLink<E, E> pre = preSelected < theContents.size() ? theContents.get(preSelected) : null;
		AbstractObservableCollectionLink<E, E> post = theSelected < theContents.size() ? theContents.get(theSelected) : null;
		if (helper.isReproducing())
			System.out.println("Switching from [" + preSelected + "]:" + pre + " to [" + theSelected + "]:" + post);
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
		for (int i = 0; i < theContents.size(); i++)
			theContents.get(i).check(transComplete);
	}

	@Override
	public String toString() {
		StringBuilder str = new StringBuilder("flattenedV(");
		if (theSelected == theContents.size())
			str.append("empty");
		else
			str.append(theContents.get(theSelected));
		str.append(getExtras()).append(')');
		return str.toString();
	}

	public static <E> FlattenedValueLink<E> createFlattenedLink(ObservableCollectionChainLink<?, E> parent, TestValueType type,
		TestHelper helper) {
		boolean distinct = helper.getBoolean(.2);
		boolean sorted = distinct && helper.getBoolean();

		SimpleSettableValue<? extends ObservableCollection<E>> value;
		ObservableCollection<E> collection;
		if (sorted) {
			value = new SimpleSettableValue<>(new TypeToken<ObservableSortedSet<E>>() {}, true);
			collection = ObservableSortedSet.flattenValue((ObservableValue<ObservableSortedSet<E>>) value);
		} else if (distinct) {
			value = new SimpleSettableValue<>(new TypeToken<ObservableSet<E>>() {}, true);
			collection = ObservableSet.flattenValue((ObservableValue<ObservableSet<E>>) value);
		} else {
			value = new SimpleSettableValue<>(new TypeToken<ObservableCollection<E>>() {}, true);
			collection = ObservableCollection.flattenValue(value);
		}
		return new FlattenedValueLink<>(parent, type, value, distinct, sorted, collection.flow(), helper);
	}

	class FlattenedValueOption extends AbstractObservableCollectionLink<E, E> {
		final AbstractObservableCollectionLink<E, E> link;
		final int index;

		FlattenedValueOption(AbstractObservableCollectionLink<E, E> link, int index, TestHelper helper) {
			super(null, link.getTestType(), ObservableCollection.of(link.getType()).flow(), helper, false, false);
			this.link = link;
			this.index = index;
		}

		@Override
		public void initialize(TestHelper helper) {}

		@Override
		public Transaction lock() {
			return link.lock();
		}

		@Override
		public void check(boolean transComplete) {
			link.check(transComplete);
		}

		@Override
		public <X> ObservableChainLink<X> derive(TestHelper helper) {
			return null;
		}

		@Override
		public String printValue() {
			return link.printValue();
		}

		@Override
		public TestValueType getTestType() {
			return link.getTestType();
		}

		@Override
		public ObservableCollectionChainLink<E, ?> getChild() {
			return null;
		}

		@Override
		public ObservableCollection<E> getCollection() {
			return link.getCollection();
		}

		@Override
		public List<E> getExpected() {
			return link.getExpected();
		}

		@Override
		public void checkModifiable(List<CollectionOp<E>> ops, int subListStart, int subListEnd, TestHelper helper) {
			link.checkModifiable(ops, subListStart, subListEnd, helper);
		}

		@Override
		public void fromBelow(List<CollectionOp<E>> ops, TestHelper helper) {}

		@Override
		public void fromAbove(List<CollectionOp<E>> ops, TestHelper helper, boolean above) {
			link.fromAbove(ops, helper, above);
			if (theSelected == index)
				FlattenedValueLink.this.modified(ops, helper, true);
		}
	}
}
