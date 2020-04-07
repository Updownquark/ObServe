package org.observe.supertest.dev2.links;

import java.util.function.Function;

import org.observe.collect.ObservableCollection;
import org.observe.collect.ObservableCollection.CollectionDataFlow;
import org.observe.collect.ObservableCollectionDataFlowImpl;
import org.observe.supertest.dev2.ChainLinkGenerator;
import org.observe.supertest.dev2.CollectionLinkElement;
import org.observe.supertest.dev2.ExpectedCollectionOperation;
import org.observe.supertest.dev2.ObservableChainLink;
import org.observe.supertest.dev2.ObservableCollectionLink;
import org.observe.supertest.dev2.ObservableCollectionTestDef;
import org.observe.supertest.dev2.OneToOneCollectionLink;
import org.qommons.TestHelper;
import org.qommons.ValueHolder;

public class ModFilteredCollectionLink<T> extends OneToOneCollectionLink<T, T> {
	public static final ChainLinkGenerator GENERATE = new ChainLinkGenerator() {
		@Override
		public <T> double getAffinity(ObservableChainLink<?, T> link) {
			if (!(link instanceof ObservableCollectionLink))
				return 0;
			return 1;
		}

		@Override
		public <T, X> ObservableChainLink<T, X> deriveLink(String path, ObservableChainLink<?, T> sourceLink, TestHelper helper) {
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
				new ObservableCollectionTestDef<>(sourceCL.getDef().type, derivedOneStepFlow, derivedMultiStepFlow, true,
					sourceCL.getDef().checkOldValues),
				helper, new ObservableCollectionDataFlowImpl.ModFilterer<>(filter.get()));
		}
	};

	private final ObservableCollectionDataFlowImpl.ModFilterer<T> theFilter;

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
	public T getUpdateValue(T value) {
		return getSourceLink().getUpdateValue(value);
	}

	@Override
	public void expect(ExpectedCollectionOperation<?, T> derivedOp, OperationRejection rejection) {
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
			T updateValue = getUpdateValue(derivedOp.getElement().getValue());
			if (theFilter.areUpdatesAllowed() && getCollection().equivalence().elementEquals(updateValue, derivedOp.getValue())) {
				// Things get complicated when the elements are equivalent.
				// Lower level caching and operations can affect whether the operation is actually detected as an update
				msg = rejection.getActualRejection();
			} else {
				msg = theFilter.canRemove(//
					() -> derivedOp.getElement().getValue());
				if (msg == null)
					msg = theFilter.canAdd(derivedOp.getValue());
			}
			break;
		}
		if (msg != null)
			rejection.reject(msg, true);
		else
			super.expect(derivedOp, rejection);
	}

	@Override
	public CollectionLinkElement<T, T> expectAdd(T value, CollectionLinkElement<?, T> after, CollectionLinkElement<?, T> before,
		boolean first, OperationRejection rejection) {
		String msg = theFilter.canAdd(value);
		if (msg != null) {
			rejection.reject(msg, true);
			return null;
		}
		return super.expectAdd(value, after, before, first, rejection);
	}

	@Override
	public CollectionLinkElement<T, T> expectMove(CollectionLinkElement<?, T> source, CollectionLinkElement<?, T> after,
		CollectionLinkElement<?, T> before, boolean first, OperationRejection rejection) {
		if ((after != null && source.getElementAddress().compareTo(after.getElementAddress()) < 0)//
			|| (before != null && source.getElementAddress().compareTo(before.getElementAddress()) > 0)) {
			String msg = theFilter.canMove();
			if (msg != null) {
				rejection.reject(msg, true);
				return null;
			}
		}
		return super.expectMove(source, after, before, first, rejection);
	}

	@Override
	public String toString() {
		return "modFilter(" + theFilter + ")";
	}
}
