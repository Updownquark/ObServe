package org.observe.supertest.dev2.links;

import java.util.Comparator;
import java.util.List;

import org.observe.collect.DefaultObservableCollection;
import org.observe.collect.DefaultObservableSortedSet;
import org.observe.collect.FlowOptions;
import org.observe.supertest.dev2.CollectionLinkElement;
import org.observe.supertest.dev2.ExpectedCollectionOperation;
import org.observe.supertest.dev2.ObservableCollectionLink;
import org.observe.supertest.dev2.ObservableCollectionTestDef;
import org.observe.supertest.dev2.TestValueType;
import org.qommons.Ternian;
import org.qommons.TestHelper;
import org.qommons.ValueHolder;
import org.qommons.collect.BetterList;
import org.qommons.collect.BetterSortedSet;
import org.qommons.collect.CircularArrayList;
import org.qommons.tree.BetterTreeList;
import org.qommons.tree.BetterTreeSet;
import org.qommons.tree.BinaryTreeNode;

import com.google.common.reflect.TypeToken;

public class BaseCollectionLink<T> extends ObservableCollectionLink<T, T> {
	public BaseCollectionLink(ObservableCollectionTestDef<T> def, TestHelper helper) {
		super(null, def, helper);
	}

	@Override
	protected void initialize(TestHelper helper) {
		theExpectedElements.addAll(getElements());
	}

	@Override
	public List<ExpectedCollectionOperation<T, T>> expectFromSource(ExpectedCollectionOperation<?, T> sourceOp) {
		throw new IllegalStateException("Unexpected source");
	}

	@Override
	public void expect(ExpectedCollectionOperation<?, T> derivedOp, OperationRejection rejection) {
		switch (derivedOp.getType()) {
		case add:
			throw new IllegalStateException("Should be using expectAdd");
		case remove:
			theExpectedElements.mutableElement(derivedOp.getElement().getExpectedAddress()).remove();
			break;
		case set:
			derivedOp.getElement().setValue(derivedOp.getValue());
			break;
		}
	}

	@Override
	public CollectionLinkElement<?, T> expectAdd(T value, CollectionLinkElement<?, T> after, CollectionLinkElement<?, T> before,
		boolean first, OperationRejection rejection) {
		BinaryTreeNode<CollectionLinkElement<T, T>> newNode = theExpectedElements.addElement(null, //
			after == null ? null : after.getExpectedAddress(), //
				before == null ? null : before.getExpectedAddress(), //
					first);
		CollectionLinkElement<T, T> newElement = new CollectionLinkElement<>(this, newNode.getElementId(), value);
		theExpectedElements.mutableElement(newNode.getElementId()).set(newElement);
		if (after != null && newElement.getExpectedAddress().compareTo(after.getExpectedAddress()) < 0)
			throw new IllegalStateException("Added in wrong order");
		if (before != null && newElement.getExpectedAddress().compareTo(before.getExpectedAddress()) > 0)
			throw new IllegalStateException("Added in wrong order");
		return newElement;
	}

	@SuppressWarnings("deprecation")
	public static <E> BaseCollectionLink<E> createInitialLink(TestValueType type, TestHelper helper, int depth, Ternian withSorted,
		Comparator<? super E> compare) {
		TestValueType fType = type != null ? type : TestValueType.nextType(helper);

		ValueHolder<BaseCollectionLink<E>> holder = new ValueHolder<>();
		TestHelper.RandomAction action = helper.createAction();
		if (withSorted != Ternian.TRUE) {
			action.or(1, () -> {
				// Simple tree-backed list
				BetterList<E> backing = new BetterTreeList<>(true);
				DefaultObservableCollection<E> base = new DefaultObservableCollection<>((TypeToken<E>) fType.getType(), backing);
				holder.accept(new BaseCollectionLink<>(new ObservableCollectionTestDef<>(fType, base.flow(), true, true), helper));
			});
			if (CircularArrayList.class.getAnnotation(Deprecated.class) == null) {
				action.or(1, () -> {
					BetterList<E> backing = new CircularArrayList<>();
					DefaultObservableCollection<E> base = new DefaultObservableCollection<>((TypeToken<E>) fType.getType(), backing);
					holder.accept(new BaseCollectionLink<>(new ObservableCollectionTestDef<>(fType, base.flow(), true, true), helper));
				});
			}
		}
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
