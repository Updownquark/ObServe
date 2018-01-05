package org.observe.supertest.dev;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.observe.collect.FlowOptions;
import org.observe.collect.ObservableCollection.CollectionDataFlow;
import org.observe.supertest.dev.ObservableChainTester.TestValueType;
import org.observe.supertest.dev.ObservableChainTester.TypeTransformation;
import org.qommons.TestHelper;
import org.qommons.collect.MutableCollectionElement.StdMsg;

public class MappedCollectionLink<E, T> extends AbstractObservableCollectionLink<E, T> {
	private final TypeTransformation<E, T> theMap;
	private final FlowOptions.MapDef<E, T> theOptions;

	public MappedCollectionLink(ObservableCollectionChainLink<?, E> parent, TestValueType type, CollectionDataFlow<?, ?, T> flow,
		TestHelper helper, TypeTransformation<E, T> map, FlowOptions.MapDef<E, T> options) {
		super(parent, type, flow, helper, false);
		theMap = map;
		theOptions = options;

		for (E src : getParent().getCollection())
			getExpected().add(theMap.map(src));
	}

	@Override
	public void checkAddable(List<CollectionOp<T>> adds, int subListStart, int subListEnd, TestHelper helper) {
		List<CollectionOp<E>> parentAdds = new ArrayList<>(adds.size());
		for (CollectionOp<T> add : adds) {
			if (theOptions.getReverse() == null) {
				add.reject(StdMsg.UNSUPPORTED_OPERATION, true);
				continue;
			}
			E reversed = theOptions.getReverse().apply(add.source);
			if (!getCollection().equivalence().elementEquals(theMap.map(reversed), add.source)) {
				add.reject(StdMsg.ILLEGAL_ELEMENT, true);
				continue;
			}
			CollectionOp<E> parentAdd = new CollectionOp<>(add, reversed, add.index);
			parentAdds.add(parentAdd);
		}
		getParent().checkAddable(parentAdds, subListStart, subListEnd, helper);
	}

	@Override
	public void checkRemovable(List<CollectionOp<T>> removes, int subListStart, int subListEnd,
		TestHelper helper) {
		List<CollectionOp<E>> parentRemoves = new ArrayList<>(removes.size());
		for (CollectionOp<T> remove : removes) {
			if (remove.index < 0) {
				if (!getCollection().contains(remove.source)) {
					remove.reject(StdMsg.NOT_FOUND, false);
					continue;
				} else if (theOptions.getReverse() == null) {
					remove.reject(StdMsg.UNSUPPORTED_OPERATION, true);
					continue;
				}
				parentRemoves.add(new CollectionOp<>(remove, theOptions.getReverse().apply(remove.source), remove.index));
			} else
				parentRemoves.add(new CollectionOp<>(remove, getParent().getCollection().get(remove.index), remove.index));
		}
		getParent().checkRemovable(parentRemoves, subListStart, subListEnd, helper);
	}

	@Override
	public void checkSettable(List<CollectionOp<T>> sets, int subListStart, TestHelper helper) {
		List<CollectionOp<E>> parentSets = new ArrayList<>(sets.size());
		for (CollectionOp<T> set : sets) {
			if (theOptions.getElementReverse() != null) {
				String message = theOptions.getElementReverse().setElement(getParent().getCollection().get(set.index), set.source, false);
				if (message == null)
					continue; // Don't even need to consult the parent for this
				if (theOptions.getReverse() == null) {
					set.reject(message, true);
					continue;
				}
			}
			if (theOptions.getReverse() == null) {
				set.reject(StdMsg.UNSUPPORTED_OPERATION, true);
				continue;
			}
			E reversed = theOptions.getReverse().apply(set.source);
			if (!getCollection().equivalence().elementEquals(theMap.map(reversed), set.source)) {
				set.reject(StdMsg.ILLEGAL_ELEMENT, true);
				return;
			}
			parentSets.add(new CollectionOp<>(set, reversed, set.index));
		}
		getParent().checkSettable(parentSets, subListStart, helper);
	}

	@Override
	public void addedFromBelow(List<CollectionOp<E>> adds, TestHelper helper) {
		added(adds.stream().map(add -> new CollectionOp<>(add, theMap.map(add.source), add.index)).collect(Collectors.toList()), helper,
			true);
	}

	@Override
	public void removedFromBelow(int index, TestHelper helper) {
		removed(index, helper, true);
	}

	@Override
	public void setFromBelow(int index, E value, TestHelper helper) {
		// TODO Need to cache (if options allow) to detect whether the change is an update, which may not result in an event for some
		// options
		set(index, theMap.map(value), helper, true);
	}

	@Override
	public void addedFromAbove(List<CollectionOp<T>> adds, TestHelper helper, boolean above) {
		getParent().addedFromAbove(//
			adds.stream().<CollectionOp<E>> map(add -> new CollectionOp<>(add, theOptions.getReverse().apply(add.source), add.index))
			.collect(Collectors.toList()),
			helper, true);
		added(adds, helper, !above);
	}

	@Override
	public void removedFromAbove(int index, T value, TestHelper helper, boolean above) {
		getParent().removedFromAbove(index, getParent().getExpected().get(index), helper, true);
		removed(index, helper, !above);
	}

	@Override
	public void setFromAbove(int index, T value, TestHelper helper, boolean above) {
		if (theOptions.getElementReverse() != null) {
			if (theOptions.getElementReverse().setElement(getParent().getCollection().get(index), value, true) == null)
				return;
		}
		set(index, value, helper, !above);
		getParent().setFromAbove(index, theOptions.getReverse().apply(value), helper, true);
	}

	@Override
	public String toString() {
		return "mapped(" + theMap + ")";
	}
}
