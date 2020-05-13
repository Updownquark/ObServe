package org.observe.supertest.links;

import java.util.function.Function;

import org.observe.collect.ObservableCollection;
import org.observe.collect.ObservableCollection.CollectionDataFlow;
import org.observe.collect.ObservableCollectionDataFlowImpl;
import org.observe.supertest.ChainLinkGenerator;
import org.observe.supertest.CollectionLinkElement;
import org.observe.supertest.ExpectedCollectionOperation;
import org.observe.supertest.ObservableChainLink;
import org.observe.supertest.ObservableCollectionLink;
import org.observe.supertest.ObservableCollectionTestDef;
import org.observe.supertest.OperationRejection;
import org.observe.supertest.TestValueType;
import org.qommons.TestHelper;
import org.qommons.ValueHolder;

/**
 * Tests {@link org.observe.collect.ObservableCollection.CollectionDataFlow#filterMod(java.util.function.Consumer)}
 *
 * @param <T> The type of values in the collection
 */
public class ModFilteredCollectionLink<T> extends OneToOneCollectionLink<T, T> {
	/** Generates {@link ModFilteredCollectionLink}s */
	public static final ChainLinkGenerator GENERATE = new ChainLinkGenerator.CollectionLinkGenerator() {
		@Override
		public <T> double getAffinity(ObservableChainLink<?, T> link, TestValueType targetType) {
			if (!(link instanceof ObservableCollectionLink))
				return 0;
			else if (targetType != null && targetType != link.getType())
				return 0;
			return 1;
		}

		@Override
		public <T, X> ObservableCollectionLink<T, X> deriveLink(String path, ObservableChainLink<?, T> sourceLink, TestValueType targetType,
			TestHelper helper) {
			ObservableCollectionLink<?, T> sourceCL = (ObservableCollectionLink<?, T>) sourceLink;
			ValueHolder<ObservableCollection.ModFilterBuilder<T>> filter = new ValueHolder<>();
			boolean unmodifiable = helper.getBoolean(.1);
			boolean updatable = !unmodifiable || helper.getBoolean(.75);
			boolean noAdd = unmodifiable || helper.getBoolean(.25);
			boolean noMove = unmodifiable || helper.getBoolean();
			Function<T, String> addFilter = (noAdd || helper.getBoolean(.85)) ? null
				: FilteredCollectionLink.filterFor(sourceCL.getDef().type, helper);
			boolean noRemove = unmodifiable || helper.getBoolean(.25);
			Function<T, String> removeFilter = (noRemove || helper.getBoolean(.85)) ? null
				: FilteredCollectionLink.filterFor(sourceCL.getDef().type, helper);
			CollectionDataFlow<?, ?, T> derivedOneStepFlow = sourceCL.getCollection().flow().filterMod(f -> {
				if (unmodifiable)
					f.unmodifiable("Unmodifiable", updatable);
				else {
					if (noAdd)
						f.noAdd("No adds");
					else if (addFilter != null)
						f.filterAdd(addFilter);
					if (noRemove)
						f.noRemove("No removes");
					else if (removeFilter != null)
						f.filterRemove(removeFilter);
					if (noMove)
						f.noMove("No moves");
				}
				filter.accept(f);
			});
			CollectionDataFlow<?, ?, T> derivedMultiStepFlow = sourceCL.getDef().multiStepFlow.filterMod(f -> {
				if (unmodifiable)
					f.unmodifiable("Unmodifiable", updatable);
				else {
					if (noAdd)
						f.noAdd("No adds");
					else if (addFilter != null)
						f.filterAdd(addFilter);
					if (noRemove)
						f.noRemove("No removes");
					else if (removeFilter != null)
						f.filterRemove(removeFilter);
					if (noMove)
						f.noMove("No moves");
				}
				filter.accept(f);
			});
			return (ObservableCollectionLink<T, X>) new ModFilteredCollectionLink<>(path, sourceCL,
				new ObservableCollectionTestDef<>(sourceCL.getDef().type, derivedOneStepFlow, derivedMultiStepFlow,
					sourceCL.getDef().orderImportant, sourceCL.getDef().checkOldValues),
				helper, new ObservableCollectionDataFlowImpl.ModFilterer<>(filter.get()));
		}
	};

	private final ObservableCollectionDataFlowImpl.ModFilterer<T> theFilter;

	/**
	 * @param path The path for this link
	 * @param sourceLink The source for this link
	 * @param def The collection definition for this link
	 * @param helper The randomness to use to initialize this link
	 * @param filter The modification filter to apply to expected modifications here or downstream
	 */
	public ModFilteredCollectionLink(String path, ObservableCollectionLink<?, T> sourceLink, ObservableCollectionTestDef<T> def,
		TestHelper helper, ObservableCollectionDataFlowImpl.ModFilterer<T> filter) {
		super(path, sourceLink, def, helper);
		theFilter = filter;
	}

	@Override
	protected T map(T sourceValue) {
		return sourceValue;
	}

	@Override
	protected T reverse(T value) {
		return value;
	}

	@Override
	protected boolean isReversible() {
		return true;
	}

	@Override
	public boolean isAcceptable(T value) {
		return getSourceLink().isAcceptable(value);
	}

	@Override
	public T getUpdateValue(CollectionLinkElement<T, T> element, T value) {
		return ((ObservableCollectionLink<Object, T>) getSourceLink())
			.getUpdateValue((CollectionLinkElement<Object, T>) element.getFirstSource(), value);
	}

	@Override
	public void expect(ExpectedCollectionOperation<?, T> derivedOp, OperationRejection rejection, boolean execute) {
		String msg = null;
		switch (derivedOp.getType()) {
		case add:
		case move:
			throw new IllegalStateException();
		case remove:
			msg = theFilter.canRemove(//
				() -> derivedOp.getElement().getValue());
			break;
		case set:
			T updateValue = getUpdateValue((CollectionLinkElement<T, T>) derivedOp.getElement(), derivedOp.getElement().getValue());
			if (theFilter.areUpdatesAllowed() && getCollection().equivalence().elementEquals(updateValue, derivedOp.getValue())) {
				// Things get complicated when the elements are equivalent.
				// Lower level caching and operations can affect whether the operation is actually detected as an update
				msg = rejection.getActualRejection();
			} else if (rejection.isRejectable()) {
				msg = theFilter.canRemove(//
					() -> derivedOp.getElement().getValue());
				if (msg == null)
					msg = theFilter.canAdd(derivedOp.getValue());
			} else
				msg = null;
			break;
		}
		if (msg != null)
			rejection.reject(msg);
		else
			super.expect(derivedOp, rejection, execute);
	}

	@Override
	public CollectionLinkElement<T, T> expectAdd(T value, CollectionLinkElement<?, T> after, CollectionLinkElement<?, T> before,
		boolean first, OperationRejection rejection, boolean execute) {
		String msg = theFilter.canAdd(value);
		if (msg != null) {
			rejection.reject(msg);
			return null;
		}
		return super.expectAdd(value, after, before, first, rejection, execute);
	}

	@Override
	public CollectionLinkElement<T, T> expectMove(CollectionLinkElement<?, T> source, CollectionLinkElement<?, T> after,
		CollectionLinkElement<?, T> before, boolean first, OperationRejection rejection, boolean execute) {
		if ((after != null && source.getElementAddress().compareTo(after.getElementAddress()) < 0)//
			|| (before != null && source.getElementAddress().compareTo(before.getElementAddress()) > 0)) {
			String msg = theFilter.canMove();
			if (msg != null) {
				rejection.reject(msg);
				return null;
			}
		}
		return super.expectMove(source, after, before, first, rejection, execute);
	}

	@Override
	public String toString() {
		return "modFilter(" + theFilter + ")";
	}
}
