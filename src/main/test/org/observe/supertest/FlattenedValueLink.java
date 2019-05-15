package org.observe.supertest;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

import org.observe.ObservableValue;
import org.observe.SimpleSettableValue;
import org.observe.collect.CollectionChangeType;
import org.observe.collect.ObservableCollection;
import org.observe.collect.ObservableCollection.CollectionDataFlow;
import org.observe.collect.ObservableCollectionEvent;
import org.observe.collect.ObservableSet;
import org.observe.collect.ObservableSortedSet;
import org.observe.supertest.ObservableChainTester.TestValueType;
import org.qommons.Lockable;
import org.qommons.Ternian;
import org.qommons.TestHelper;
import org.qommons.Transaction;
import org.qommons.collect.MutableCollectionElement.StdMsg;

import com.google.common.reflect.TypeToken;

public class FlattenedValueLink<E> extends AbstractObservableCollectionLink<E, E> {
	private final SimpleSettableValue<? extends ObservableCollection<E>> theValue;
	private ReentrantReadWriteLock theValueLock;
	private final List<AbstractObservableCollectionLink<E, E>> theContents;
	private final boolean isDistinct;
	private final boolean isSorted;
	private final Comparator<? super E> theCompare;
	private final int theDepth;
	private int theSelected;

	private LinkElement theLastAddedSource;

	public FlattenedValueLink(ObservableCollectionChainLink<?, E> parent, TestValueType type,
		SimpleSettableValue<? extends ObservableCollection<E>> value, ReentrantReadWriteLock lock, boolean distinct, boolean sorted,
			Comparator<? super E> compare,
			CollectionDataFlow<?, ?, E> flow, TestHelper helper, int depth) {
		super(parent, type, flow, helper, true);
		theValue = value;
		theValueLock = lock;
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
		if (theSelected < theContents.size()) {
			AbstractObservableCollectionLink<?, E> selected = theContents.get(theSelected);
			getExpected().addAll(selected.getExpected());
			for (int i = 0; i < getElements().size(); i++)
				mapSourceElement(selected.getElements().get(i), getElements().get(i));
		}
	}

	@Override
	public Transaction lock() {
		// If we just let the superclass do this, the value would be locked read-only
		// (which is correct, since modifying a flattened collection cannot change the value)
		// Plus, since the contents can change, we need to lock all possible contents of the value
		// So we need to lock the value and all contents both for write
		Transaction valueT = Lockable.lockable(theValueLock, true).lock();
		Transaction[] collT = new Transaction[theContents.size()];
		for (int i = 0; i < collT.length; i++)
			collT[i] = theContents.get(i).lock();
		return Transaction.and(valueT, Transaction.and(collT));
	}

	@Override
	protected void change(ObservableCollectionEvent<? extends E> evt) {
		super.change(evt);
		if (evt.getType() == CollectionChangeType.add) {
			if (theSelected < theContents.size()) {
				AbstractObservableCollectionLink<?, E> selected = theContents.get(theSelected);
				LinkElement srcEl = selected.getLastAddedOrModifiedElement();
				if (srcEl != theLastAddedSource) {
					theLastAddedSource = srcEl;
					mapSourceElement(srcEl, getLastAddedOrModifiedElement());
				}
			}
		}
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
		List<LinkElement> linkElCopy = new ArrayList<>(getElements());
		((SimpleSettableValue<ObservableCollection<E>>) theValue).set(post == null ? null : post.getCollection(), null);
		if (preSelected == theSelected)
			return;
		List<CollectionOp<E>> ops = new ArrayList<>();
		if (preSelected < theContents.size()) {
			theContents.get(preSelected).setChild(null);
			ObservableCollection<E> c = theContents.get(preSelected).getCollection();
			int i = c.size() - 1;
			for (E value : c.reverse()) {
				ops.add(new CollectionOp<>(CollectionChangeType.remove, linkElCopy.get(i), i, value));
				i--;
			}
		}
		if (theSelected < theContents.size()) {
			ObservableCollection<E> c = theContents.get(theSelected).getCollection();
			int i = 0;
			for (E value : c) {
				ops.add(new CollectionOp<>(CollectionChangeType.add, getElements().get(i), i, value));
				i++;
			}
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
		modified(//
			ops.stream().map(op -> new CollectionOp<>(op.type, getDestElements(op.elementId).getLast(), op.index, op.value))
			.collect(Collectors.toList()), //
			helper, true);
	}

	@Override
	public void fromAbove(List<CollectionOp<E>> ops, TestHelper helper, boolean above) {
		if (ops.isEmpty())
			return;
		modified(//
			ops.stream().map(op -> new CollectionOp<>(op.type, getSourceElement(op.elementId), op.index, op.value))
			.collect(Collectors.toList()), //
			helper, false); // Since we don't call the super.tryModify, above will never be false
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
		ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
		ObservableCollection<E> collection;
		if (sorted) {
			value = new SimpleSettableValue<>(new TypeToken<ObservableSortedSet<E>>() {}, true, lock, null);
			collection = ObservableSortedSet.flattenValue((ObservableValue<ObservableSortedSet<E>>) value, compare);
		} else if (distinct) {
			value = new SimpleSettableValue<>(new TypeToken<ObservableSet<E>>() {}, true, lock, null);
			collection = ObservableSet.flattenValue((ObservableValue<ObservableSet<E>>) value);
		} else {
			value = new SimpleSettableValue<>(new TypeToken<ObservableCollection<E>>() {}, true, lock, null);
			collection = ObservableCollection.flattenValue(value);
		}
		return new FlattenedValueLink<>(parent, type, value, lock, distinct, sorted, compare, collection.flow(), helper, depth);
	}
}
