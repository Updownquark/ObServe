package org.observe.collect;

import java.util.Comparator;
import java.util.function.BiFunction;

import org.observe.Equivalence;
import org.observe.util.TypeTokens;
import org.qommons.Causable;
import org.qommons.Transaction;
import org.qommons.ValueHolder;
import org.qommons.collect.BetterCollection;
import org.qommons.collect.BetterList;
import org.qommons.collect.BetterSortedList;
import org.qommons.collect.BetterSortedSet;
import org.qommons.collect.CollectionElement;
import org.qommons.collect.ElementId;
import org.qommons.collect.ValueStoredCollection;

import com.google.common.reflect.TypeToken;

/**
 * An {@link ObservableSortedSet} implementation based on a backing {@link BetterSortedSet}
 *
 * @param <E> The type of values in the set
 */
public class DefaultObservableSortedSet<E> extends DefaultObservableCollection<E> implements ObservableSortedSet<E> {
	/**
	 * @param type The type of elements in the set
	 * @param sorting The sorting for the set
	 * @return A builder to build a new sorted set
	 */
	public static <E> ObservableCollectionBuilder.DistinctSortedBuilder<E, ?> build(TypeToken<E> type, Comparator<? super E> sorting) {
		return new ObservableCollectionBuilder.DistinctSortedBuilderImpl<>(type, "observable-sorted-set", sorting);
	}

	/**
	 * @param type The type for the sorted set
	 * @param sortedSet The backing sorted set to hold this observable set's values
	 */
	public DefaultObservableSortedSet(TypeToken<E> type, BetterSortedSet<E> sortedSet) {
		this(type, sortedSet, null, null);
	}

	/**
	 * @param type The type for the sorted set
	 * @param sortedSet The backing sorted set to hold this observable set's values
	 * @param elementSource The function to provide element sources for this collection
	 * @param sourceElements The function to provide source elements for elements in this collection
	 * @see #getElementsBySource(ElementId, BetterCollection)
	 * @see #getSourceElements(ElementId, BetterCollection)
	 */
	public DefaultObservableSortedSet(TypeToken<E> type, BetterSortedSet<E> sortedSet,
		BiFunction<ElementId, BetterCollection<?>, ElementId> elementSource,
		BiFunction<ElementId, BetterCollection<?>, BetterList<ElementId>> sourceElements) {
		super(type, sortedSet, elementSource, sourceElements,
			Equivalence.DEFAULT.sorted(TypeTokens.getRawType(type), sortedSet.comparator(), false));
	}

	@Override
	protected BetterSortedSet<E> getValues() {
		return (BetterSortedSet<E>) super.getValues();
	}

	@Override
	public int indexFor(Comparable<? super E> search) {
		return getValues().indexFor(search);
	}

	@Override
	public CollectionElement<E> search(Comparable<? super E> search, BetterSortedList.SortedSearchFilter filter) {
		return getValues().search(search, filter);
	}

	@Override
	public Equivalence.SortedEquivalence<? super E> equivalence() {
		return (Equivalence.SortedEquivalence<? super E>) super.equivalence();
	}

	@Override
	public Comparator<? super E> comparator() {
		return getValues().comparator();
	}

	@Override
	public CollectionElement<E> getOrAdd(E value, ElementId after, ElementId before, boolean first, Runnable added) {
		ValueHolder<Boolean> addedCheck = new ValueHolder<>(false);
		CollectionElement<E> el = getValues().getOrAdd(value, after, before, first, () -> {
			addedCheck.accept(true);
			added.run();
		});
		if (addedCheck.get()) {
			ObservableCollectionEvent<E> event = new ObservableCollectionEvent<>(el.getElementId(), getType(),
				getValues().getElementsBefore(el.getElementId()), CollectionChangeType.add, null, value, getCurrentCause());
			fire(event);
		}
		return el;
	}

	@Override
	public boolean isConsistent(ElementId element) {
		return getValues().isConsistent(element);
	}

	@Override
	public boolean checkConsistency() {
		return getValues().checkConsistency();
	}

	@Override
	public <X> boolean repair(ElementId element, ValueStoredCollection.RepairListener<E, X> listener) {
		RepairOperation op = new RepairOperation(getCurrentCause());
		try (Transaction opT = op.use(); Transaction t = lock(true, op)) {
			return getValues().repair(element, new ObservableRepairListener<>(listener));
		}
	}

	@Override
	public <X> boolean repair(ValueStoredCollection.RepairListener<E, X> listener) {
		RepairOperation op = new RepairOperation(getCurrentCause());
		try (Transaction opT = op.use(); Transaction t = lock(true, op)) {
			return getValues().repair(new ObservableRepairListener<>(listener));
		}
	}

	private class ObservableRepairListener<X> implements ValueStoredCollection.RepairListener<E, RepairEvent<X>> {
		private final ValueStoredCollection.RepairListener<E, X> theWrapped;

		ObservableRepairListener(ValueStoredCollection.RepairListener<E, X> wrapped) {
			theWrapped = wrapped;
		}

		@Override
		public RepairEvent<X> removed(CollectionElement<E> element) {
			RepairEvent<X> repair = new RepairEvent<>(getCurrentCause());
			boolean success = false;
			try {
				ObservableCollectionEvent<E> event = new ObservableCollectionEvent<>(element.getElementId(), getType(),
					getValues().getElementsBefore(element.getElementId()), CollectionChangeType.remove, element.get(), element.get(),
					repair);
				fire(event);
				repair.wrappedData = theWrapped == null ? null : theWrapped.removed(element);
				success = true;
				return repair;
			} finally {
				if (!success)
					repair.finish.close();
			}
		}

		@Override
		public void disposed(E value, RepairEvent<X> data) {
			try {
				if (theWrapped != null)
					theWrapped.disposed(value, data.wrappedData);
			} finally {
				data.finish.close();
			}
		}

		@Override
		public void transferred(CollectionElement<E> element, RepairEvent<X> data) {
			try {
				ObservableCollectionEvent<E> event = new ObservableCollectionEvent<>(element.getElementId(), getType(),
					getValues().getElementsBefore(element.getElementId()), CollectionChangeType.add, null, element.get(), data);
				fire(event);
				if (theWrapped != null) {
					theWrapped.transferred(element, data.wrappedData);
					data.wrappedData = null;
				}
			} finally {
				data.finish.close();
			}
		}
	}

	private static class RepairEvent<X> extends Causable.AbstractCausable {
		final Transaction finish;
		X wrappedData;

		RepairEvent(Object cause) {
			super(cause);
			finish = use();
		}
	}
}
