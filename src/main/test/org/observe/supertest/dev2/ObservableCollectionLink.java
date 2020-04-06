package org.observe.supertest.dev2;

import static org.observe.collect.CollectionChangeType.add;
import static org.observe.collect.CollectionChangeType.remove;
import static org.observe.collect.CollectionChangeType.set;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

import org.junit.Assert;
import org.observe.Observable;
import org.observe.collect.CollectionChangeType;
import org.observe.collect.ObservableCollection;
import org.observe.collect.ObservableCollectionEvent;
import org.observe.collect.ObservableCollectionTester;
import org.qommons.TestHelper;
import org.qommons.TestHelper.RandomAction;
import org.qommons.Transaction;
import org.qommons.collect.BetterCollections;
import org.qommons.collect.BetterList;
import org.qommons.collect.BetterSet;
import org.qommons.collect.BetterSortedList.SortedSearchFilter;
import org.qommons.collect.CollectionElement;
import org.qommons.collect.ElementId;
import org.qommons.collect.MutableCollectionElement;
import org.qommons.collect.MutableCollectionElement.StdMsg;
import org.qommons.tree.BetterTreeList;

public abstract class ObservableCollectionLink<S, T> extends AbstractChainLink<S, T> implements CollectionSourcedLink<S, T> {
	public interface OperationRejection {
		boolean isRejected();

		void reject(String message, boolean error);
	}

	private final ObservableCollectionTestDef<T> theDef;
	private final ObservableCollection<T> theOneStepCollection;
	private final ObservableCollection<T> theMultiStepCollection;
	private final ObservableCollectionTester<T> theMultiStepTester;
	private final Function<TestHelper, T> theSupplier;

	private final BetterTreeList<CollectionLinkElement<S, T>> theElements;
	private final BetterTreeList<CollectionLinkElement<S, T>> theElementsForCollection;

	public ObservableCollectionLink(ObservableCollectionLink<?, S> sourceLink, ObservableCollectionTestDef<T> def, TestHelper helper) {
		super(sourceLink);
		theDef = def;
		theSupplier = (Function<TestHelper, T>) ObservableChainTester.SUPPLIERS.get(def.type);
		theElements = new BetterTreeList<>(false);
		theElementsForCollection = new BetterTreeList<>(false);
		boolean passive;
		if (def.allowPassive.value != null)
			passive = def.allowPassive.value;
		else
			passive = def.oneStepFlow.supportsPassive() && helper.getBoolean();
		if (passive)
			theOneStepCollection = def.oneStepFlow.collectPassive();
		else
			theOneStepCollection = def.oneStepFlow.collectActive(Observable.empty);
		if (passive && def.multiStepFlow.supportsPassive())
			theMultiStepCollection = def.multiStepFlow.collectPassive();
		else
			theMultiStepCollection = def.multiStepFlow.collectActive(Observable.empty);
		theMultiStepTester = new ObservableCollectionTester<>("[" + getDepth() + "] Multi-step", theMultiStepCollection);

		if (getSourceLink() == null && theSupplier != null && helper.getBoolean(.25)) {
			int size = helper.getInt(0, 10);
			for (int i = 0; i < size; i++)
				getCollection().add(theSupplier.apply(helper));
		}

		// Listen to the collection to populate and maintain theElements
		getCollection().subscribe(new Consumer<ObservableCollectionEvent<? extends T>>() {
			@Override
			public void accept(ObservableCollectionEvent<? extends T> evt) {
				switch (evt.getType()) {
				case add:
					CollectionElement<CollectionLinkElement<S, T>> added;
					if (evt.getIndex() == theElementsForCollection.size())
						added = theElements.addElement(null, false);
					else
						added = theElements.addElement(null, null, theElementsForCollection.get(evt.getIndex()).getElementAddress(), false);
					CollectionLinkElement<S, T> newLink = new CollectionLinkElement<>(ObservableCollectionLink.this, evt.getElementId(),
						added.getElementId());
					theElements.mutableElement(added.getElementId()).set(newLink);
					theElementsForCollection.add(evt.getIndex(), newLink);
					break;
				case remove:
					CollectionElement<CollectionLinkElement<S, T>> removed = theElementsForCollection.getElement(evt.getIndex());
					if (theDef.checkOldValues && !getCollection().equivalence().elementEquals(removed.get().get(), evt.getOldValue()))
						throw new AssertionError("Old values do not match: Expected " + removed.get() + " but was " + evt.getOldValue());
					removed.get().removed();
					theElementsForCollection.mutableElement(removed.getElementId()).remove();
					break;
				case set:
					CollectionLinkElement<S, T> element = theElementsForCollection.get(evt.getIndex());
					if (theDef.checkOldValues && !getCollection().equivalence().elementEquals(element.get(), evt.getOldValue()))
						throw new AssertionError("Old values do not match: Expected " + element.get() + " but was " + evt.getOldValue());
					element.updated();
					break;
				}
			}
		}, true);
	}

	@Override
	public void initialize(TestHelper helper) {
		super.initialize(helper);
	}

	protected abstract void validate(CollectionLinkElement<S, T> element);

	@Override
	public TestValueType getType() {
		return theDef.type;
	}

	@Override
	public boolean isLockSupported() {
		return getCollection().isLockSupported();
	}

	@Override
	public Transaction lock(boolean write, Object cause) {
		return getCollection().lock(write, cause);
	}

	@Override
	public Transaction tryLock(boolean write, Object cause) {
		return getCollection().tryLock(write, cause);
	}

	@Override
	public ObservableCollectionLink<?, S> getSourceLink() {
		return (ObservableCollectionLink<?, S>) super.getSourceLink();
	}

	public ObservableCollectionTestDef<T> getDef() {
		return theDef;
	}

	public Function<TestHelper, T> getValueSupplier() {
		return theSupplier;
	}

	@Override
	public List<CollectionSourcedLink<T, ?>> getDerivedLinks() {
		return (List<CollectionSourcedLink<T, ?>>) super.getDerivedLinks();
	}

	public ObservableCollection<T> getCollection() {
		return theOneStepCollection;
	}

	BetterList<CollectionLinkElement<S, T>> getUnprotectedElements() {
		return theElements;
	}

	public BetterList<CollectionLinkElement<S, T>> getElements() {
		return BetterCollections.unmodifiableList(theElements);
	}

	public CollectionLinkElement<S, T> getElement(ElementId collectionEl) {
		return CollectionElement
			.get(theElementsForCollection.search(el -> collectionEl.compareTo(el.getCollectionAddress()), SortedSearchFilter.OnlyMatch));
	}

	public abstract void expect(ExpectedCollectionOperation<?, T> derivedOp, OperationRejection rejection, int derivedIndex);

	public abstract CollectionLinkElement<S, T> expectAdd(T value, CollectionLinkElement<?, T> after, CollectionLinkElement<?, T> before,
		boolean first, OperationRejection rejection, int derivedIndex);

	public abstract boolean isAcceptable(T value);

	public abstract T getUpdateValue(T value);

	@Override
	public double getModificationAffinity() {
		int affinity = 3;
		if (theSupplier != null)
			affinity += 7;
		return affinity;
	}

	@Override
	public void tryModify(RandomAction action, TestHelper helper) {
		int subListStart, subListEnd;
		BetterList<T> modify;
		boolean subList = helper.getBoolean(.05);
		if (subList) {
			subListStart = helper.getInt(0, getCollection().size());
			subListEnd = subListStart + helper.getInt(0, getCollection().size() - subListStart);
			modify = getCollection().subList(subListStart, subListEnd);
			if (helper.isReproducing())
				System.out.print("subList(" + subListStart + ".." + subListEnd + ") ");
		} else {
			subListStart = 0;
			subListEnd = getCollection().size();
			modify = getCollection();
		}
		CollectionOpContext opCtx = new CollectionOpContext(modify, subList, subListStart, subListEnd);
		if (theSupplier != null) {
			action.or(5, () -> { // More position-less adds than other ops
				T value = theSupplier.apply(helper);
				CollectionOp op = new CollectionOp(opCtx, add, -1, -1, value, helper.getBoolean());
				if (helper.isReproducing())
					System.out.println(op);
				helper.placemark();
				addSingle(op, helper);
			}).or(1, () -> { // Add in position range
				int minIndex = (int) helper.getDouble(0, modify.size() / 3, modify.size());
				int maxIndex = helper.getInt(minIndex, modify.size());
				T value = theSupplier.apply(helper);
				CollectionOp op = new CollectionOp(opCtx, add, minIndex, maxIndex, value, helper.getBoolean());
				if (helper.isReproducing())
					System.out.println(op);
				helper.placemark();
				addSingle(op, helper);
			}).or(1, () -> { // addAll
				int length = (int) helper.getDouble(0, 5, 100); // Aggressively tend smaller
				List<T> values = new ArrayList<>(length);
				for (int i = 0; i < length; i++)
					values.add(theSupplier.apply(helper));
				int index = helper.getInt(0, modify.size());
				boolean first = helper.getBoolean();
				CollectionOp op = new CollectionOp(opCtx, add, index, values);
				if (helper.isReproducing())
					System.out.println(op);
				helper.placemark();
				addAll(index, first, op, helper);
			}).or(2, () -> { // Set
				if (modify.isEmpty()) {
					if (helper.isReproducing())
						System.out.println("Set, but empty");
					return;
				}
				int index = helper.getInt(0, modify.size());
				T value;
				CollectionLinkElement<S, T> element = theElements.get(opCtx.subListStart + index);
				if (helper.getBoolean(0.1)) // More updates
					value = element.getValue();
				else
					value = theSupplier.apply(helper);
				CollectionOp op = new CollectionOp(opCtx, set, index, index, value, false);
				op.add(element);
				if (helper.isReproducing())
					System.out.println(op + " from " + element.getValue());
				helper.placemark();
				set(op, helper);
			}).or(1, () -> {// Remove by value
				T value = theSupplier.apply(helper);
				CollectionOp op = new CollectionOp(opCtx, remove, -1, -1, value, true);
				boolean found = false;
				for (int i = 0; i < modify.size(); i++) {
					if (getCollection().equivalence().elementEquals(modify.get(i), value)) {
						op.add(theElements.get(opCtx.subListStart + i));
						found = true;
						break;
					}
				}
				if (!found)
					op.add(null);
				if (helper.isReproducing())
					System.out.println("Remove " + value + ": " + op.elements.get(0));
				helper.placemark();
				removeSingle(op, helper);
			}).or(1, () -> { // removeAll
				int length = (int) helper.getDouble(0, 25, 100); // Tend smaller
				List<T> values = new ArrayList<>(length);
				BetterSet<T> valueSet = getCollection().equivalence().createSet();
				for (int i = 0; i < length; i++) {
					T value = theSupplier.apply(helper);
					values.add(value);
					valueSet.add(value);
				}
				if (helper.isReproducing())
					System.out.println("Remove all " + values.size() + values);

				CollectionOp op = new CollectionOp(opCtx, remove, -1, values);
				for (int i = 0; i < modify.size(); i++) {
					T value = modify.get(i);
					if (valueSet.contains(value))
						op.add(theElements.get(opCtx.subListStart + i));
				}
				if (helper.isReproducing())
					System.out.println("\t\tShould remove " + op.printElements());
				helper.placemark();
				removeAll(op, helper);
			}).or(.1, () -> { // retainAll
				// Allow for larger, because the smaller the generated collection,
				// the more elements will be removed from the collection
				int length = helper.getInt(0, 5000);
				List<T> values = new ArrayList<>(length);
				Set<T> valueSet = getCollection().equivalence().createSet();
				for (int i = 0; i < length; i++) {
					T value = theSupplier.apply(helper);
					values.add(value);
					valueSet.add(value);
				}
				if (helper.isReproducing())
					System.out.println("Retain all " + values.size() + values);
				CollectionOp op = new CollectionOp(opCtx, remove, -1, values);
				for (int i = 0; i < modify.size(); i++) {
					T value = modify.get(i);
					if (!valueSet.contains(value))
						op.add(theElements.get(opCtx.subListStart + i));
				}
				if (helper.isReproducing())
					System.out.println("\t\tShould remove " + op.printElements());
				helper.placemark();
				retainAll(values, op, helper);
			});
		}
		action.or(1, () -> {// Remove by index
			if (modify.isEmpty()) {
				if (helper.isReproducing())
					System.out.println("Remove, but empty");
				return;
			}
			int index = helper.getInt(0, modify.size());
			CollectionOp op = new CollectionOp(opCtx, remove, index, index, null, true);
			op.add(theElements.get(opCtx.subListStart + index));
			if (helper.isReproducing()) {
				System.out.println(op);
				System.out.println("\t\tShould remove " + op.printElements());
			}
			helper.placemark();
			removeSingle(op, helper);
		}).or(.1, () -> { // Remove range
			if (modify.isEmpty()) {
				if (helper.isReproducing())
					System.out.println("Remove range, but empty");
				return;
			}
			int max = modify.size();
			int minIndex = (int) helper.getDouble(0, max / 3.0, max);
			int maxIndex = helper.getInt(minIndex, max);
			CollectionOp op = new CollectionOp(opCtx, remove, minIndex, maxIndex, null, false);
			if (helper.isReproducing())
				System.out.println(op);
			for (int i = op.minIndex; i < op.maxIndex; i++)
				op.add(theElements.get(opCtx.subListStart + i));
			helper.placemark();
			removeRange(op, helper);
		}).or(.1, () -> { // clear
			if (helper.isReproducing())
				System.out.println("clear()");
			CollectionOp op = new CollectionOp(opCtx, remove, 0, modify.size(), null, false);
			for (int i = 0; i < modify.size(); i++)
				op.add(theElements.get(opCtx.subListStart + i));
			helper.placemark();
			clearCollection(op, helper);
		}).or(1, () -> {
			if (helper.isReproducing())
				System.out.println("Check bounds");
			helper.placemark();
			testBounds(helper);
		});
		addExtraActions(action);
	}

	protected void addExtraActions(TestHelper.RandomAction action) {}

	@Override
	public void validate(boolean transactionEnd) throws AssertionError {
		StringBuilder error = new StringBuilder();
		for (CollectionLinkElement<S, T> link : theElements) {
			validate(link);
			link.validate(error);
		}

		if (error.length() > 0)
			throw new AssertionError(error.toString());
		theMultiStepTester.checkValue(theOneStepCollection, transactionEnd);
	}

	@Override
	public String printValue() {
		return getCollection().size() + getCollection().toString();
	}

	@Override
	protected <X> CollectionSourcedLink<T, X> deriveOne(TestHelper helper) {
		TestHelper.RandomSupplier<CollectionSourcedLink<T, X>> action = helper.createSupplier();
		// TODO whereContained
		// TODO refreshEach
		// TODO combine
		/*.or(1, () -> { // flattenValues
		theDerivedLink = FlattenedValuesLink.createFlattenedValuesLink(this, theDef.flow, helper);
		derived.accept((ObservableCollectionLink<T, X>) theDerivedLink);
		})//*/
		// TODO flatMap
		/*.or(1, () -> { // sorted
		Comparator<T> compare = SortedCollectionLink.compare(theType, helper);
		CollectionDataFlow<?, ?, T> derivedFlow = theFlow.sorted(compare);
		theDerivedLink = new SortedCollectionLink<>(this, theType, derivedFlow, helper, isCheckingRemovedValues, compare);
		derived.accept((ObservableCollectionLink<T, X>) theDerivedLink);
		})//*/
		/*.or(1, () -> { // distinct
		derived.accept((ObservableCollectionLink<T, X>) deriveDistinct(helper, false));
		})//
		.or(1, () -> { // distinct sorted
		derived.accept((ObservableCollectionLink<T, X>) deriveDistinctSorted(helper, false));
		})*/;//
		/*if(theCollection instanceof ObservableSet){
			// TODO mapEquivalent
		 }
		 */
		/*if (theCollection instanceof ObservableSortedSet) {
			ObservableSortedSet<T> sortedSet = (ObservableSortedSet<T>) theCollection;
			action.or(1, () -> { // subSet
				T min, max;
				boolean includeMin, includeMax;
				ObservableSortedSet<T> subSet;
				if (helper.getBoolean(.33)) {
					min = theSupplier.apply(helper);
					includeMin = helper.getBoolean();
					max = null;
					includeMax = true;
					subSet = sortedSet.tailSet(min, includeMin);
				} else if (helper.getBoolean()) {
					max = theSupplier.apply(helper);
					includeMax = helper.getBoolean();
					min = null;
					includeMin = true;
					subSet = sortedSet.headSet(max, includeMax);
				} else {
					T v1 = theSupplier.apply(helper);
					T v2 = theSupplier.apply(helper);
					if (sortedSet.comparator().compare(v1, v2) <= 0) {
						min = v1;
						max = v2;
					} else {
						min = v2;
						max = v1;
					}
					includeMin = helper.getBoolean();
					includeMax = helper.getBoolean();
					subSet = sortedSet.subSet(min, includeMin, max, includeMax);
				}
				CollectionDataFlow<?, ?, T> derivedFlow = subSet.flow();
				theDerivedLink = new SubSetLink<>(this, theType, (ObservableCollection.DistinctSortedDataFlow<?, ?, T>) derivedFlow, helper,
					true, min, includeMin, max, includeMax);
				derived.accept((ObservableCollectionLink<T, X>) theDerivedLink);
			});
		}*/

		// TODO containsAny
		// TODO containsAll
		// TODO groupBy
		// TODO groupBy(Sorted)
		return action.get(null);
	}

	/*protected ObservableCollectionLink<T, T> deriveDistinct(TestHelper helper, boolean asRoot) {
		ValueHolder<FlowOptions.UniqueOptions> options = new ValueHolder<>();
		CollectionDataFlow<?, ?, T> flow = theDef.flow;
		// distinct() is a no-op for a distinct flow, so unless we change the equivalence, this is pointless
		// plus, hash distinct() can affect ordering, so this could cause failures
		if (flow instanceof ObservableCollection.DistinctDataFlow || helper.getBoolean()) {
			Comparator<T> compare = SortedCollectionLink.compare(theDef.type, helper);
			flow = flow.withEquivalence(Equivalence.of((Class<T>) getType().getRawType(), compare, false));
		}
		CollectionDataFlow<?, ?, T> derivedFlow = flow.distinct(opts -> {
			// opts.useFirst(helper.getBoolean()).preserveSourceOrder(opts.canPreserveSourceOrder() && helper.getBoolean()); TODO
			options.accept(opts);
		});
		theDerivedLink = new DistinctCollectionLink<>(this, theType, derivedFlow, theFlow, helper, isCheckingRemovedValues, options.get(),
			asRoot);
		return (ObservableCollectionLink<T, T>) theDerivedLink;
	}

	protected ObservableCollectionLink<T, T> deriveDistinctSorted(TestHelper helper, boolean asRoot) {
		FlowOptions.UniqueOptions options = new FlowOptions.SimpleUniqueOptions(true);
		CollectionDataFlow<?, ?, T> flow = theDef.flow;
		Comparator<T> compare = SortedCollectionLink.compare(theDef.type, helper);
		options.useFirst(//
			//TODO helper.getBoolean()
			false);
		CollectionDataFlow<?, ?, T> derivedFlow = flow.distinctSorted(compare, options.isUseFirst());
		theDerivedLink = new DistinctCollectionLink<>(this, theType, derivedFlow, theFlow, helper, isCheckingRemovedValues, options,
			asRoot);
		return (ObservableCollectionLink<T, T>) theDerivedLink;
	}*/

	private class CollectionOpContext {
		final BetterList<T> modify;
		final boolean subList;
		final int subListStart;
		final int subListEnd;

		CollectionOpContext(BetterList<T> modify, boolean subList, int subListStart, int subListEnd) {
			this.modify = modify;
			this.subList = subList;
			this.subListStart = subListStart;
			this.subListEnd = subListEnd;
		}
	}

	private class CollectionOp {
		final CollectionOpContext context;
		final CollectionChangeType type;
		final T value;
		final Collection<T> values;
		final int minIndex;
		final int maxIndex;
		final boolean towardBeginning;

		CollectionLinkElement<S, T> after;
		CollectionLinkElement<S, T> before;

		final List<CollectionOpElement> elements;

		CollectionOp(CollectionOpContext ctx, CollectionChangeType type, int minIndex, int maxIndex, T value, boolean towardBeginning) {
			context = ctx;
			this.type = type;
			this.minIndex = minIndex;
			this.maxIndex = maxIndex;
			this.value = value;
			this.towardBeginning = towardBeginning;
			values = null;
			elements = new ArrayList<>();
		}

		CollectionOp(CollectionOpContext ctx, CollectionChangeType type, int index, Collection<T> values) {
			context = ctx;
			this.type = type;
			minIndex = maxIndex = index;
			this.values = values;
			value = null;
			towardBeginning = false;
			elements = new ArrayList<>();
		}

		CollectionOpElement add(CollectionLinkElement<S, T> element) {
			CollectionOpElement opEl = new CollectionOpElement(element);
			elements.add(opEl);
			return opEl;
		}

		void rejectAll(String message, boolean error) {
			for (CollectionOpElement el : elements)
				el.reject(message, error);
		}

		@Override
		public String toString() {
			StringBuilder str = new StringBuilder();
			str.append(type);
			if (minIndex >= 0) {
				str.append('@').append(minIndex);
				if (minIndex != maxIndex)
					str.append('-').append(maxIndex);
			}
			str.append(' ');
			if (values != null)
				str.append(values.size()).append(values);
			else if (value != null || minIndex < 0)
				str.append(value);
			return str.toString();
		}

		public String printElements() {
			return elements.toString();
		}
	}

	private class CollectionOpElement implements OperationRejection {
		CollectionLinkElement<S, T> element;

		private String theMessage;
		private boolean isError;

		CollectionOpElement(CollectionLinkElement<S, T> element) {
			this.element = element;
		}

		@Override
		public void reject(String message, boolean error) {
			theMessage = message;
			isError = message != null && error;
		}

		@Override
		public boolean isRejected() {
			return theMessage != null;
		}

		public String getMessage() {
			return theMessage;
		}

		public boolean isError() {
			return isError;
		}

		@Override
		public String toString() {
			if (element == null)
				return "none";
			return element.toString();
			// StringBuilder str = new StringBuilder(type.name()).append(' ');
			// if (value != null)
			// str.append(value);
			// if (minIndex >= 0)
			// str.append('@').append(minIndex).append('-').append(maxIndex);
			// return str.toString();
		}

		// public static boolean isSameIndex(List<? extends CollectionOpElement<?>> ops) {
		// int minIdx = ops.get(0).minIndex;
		// int maxIdx = ops.get(0).maxIndex;
		// for (int i = 1; i < ops.size(); i++)
		// if (ops.get(i).minIndex != minIdx && ops.get(i).maxIndex == maxIdx)
		// return false;
		// return true;
		// }
		//
		// public static boolean isSameType(List<? extends CollectionOpElement<?>> ops) {
		// CollectionChangeType type = ops.get(0).type;
		// for (int i = 1; i < ops.size(); i++)
		// if (ops.get(i).type != type)
		// return false;
		// return true;
		// }
		//
		// public static String print(List<? extends CollectionOpElement<?>> ops) {
		// if (ops.isEmpty())
		// return "[]";
		// StringBuilder str = new StringBuilder();
		// boolean separateTypes = !isSameType(ops);
		// if (!separateTypes)
		// str.append(ops.get(0).type);
		// boolean sameIndexes = isSameIndex(ops);
		// if (ops.get(0).minIndex >= 0 && sameIndexes) {
		// str.append('@').append(ops.get(0).minIndex);
		// if (ops.get(0).minIndex != ops.get(0).maxIndex)
		// str.append('-').append(ops.get(0).maxIndex);
		// }
		// str.append('[');
		// boolean first = true;
		// for (CollectionOpElement<?> op : ops) {
		// if (!first)
		// str.append(", ");
		// first = false;
		// if (separateTypes)
		// str.append(op.type);
		// if (op.value != null)
		// str.append(op.value);
		// if (!sameIndexes && op.minIndex >= 0) {
		// str.append('@').append(op.minIndex);
		// if (op.minIndex != op.maxIndex)
		// str.append('-').append(op.maxIndex);
		// }
		// }
		// str.append(']');
		// return str.toString();
		// }
	}

	private void prepareOp(CollectionOp op) {
		if (op.minIndex >= 0) {
			if (op.context.subListStart + op.minIndex == theElements.size()) {
				op.after = theElements.peekLast();
				op.before = null;
			} else if (op.context.subListStart + op.maxIndex == 0) {
				op.after = null;
				op.before = theElements.peekFirst();
			} else {
				op.after = op.context.subListStart + op.minIndex == 0 ? null
					: theElements.get(op.context.subListStart + op.minIndex - 1);
				op.before = op.context.subListStart + op.maxIndex == theElements.size() ? null
					: theElements.get(op.context.subListStart + op.maxIndex);
			}
		}
	}

	private void expectModification(CollectionOp op, TestHelper helper) {
		int sz = Math.min(theElements.size(), op.context.subListEnd) - op.context.subListStart;
		if (op.maxIndex > sz) {
			op.rejectAll(op.maxIndex + " of " + sz, true);
			return;
		}
		switch (op.type) {
		case add:
			int added = 0;
			if (op.values != null) {
				for (T value : op.values) {
					CollectionOpElement opEl = op.add(null);
					CollectionLinkElement<S, T> newElement = expectAdd(value, op.after, op.before, op.towardBeginning, opEl, -1);
					if (!opEl.isRejected()) {
						opEl.element = newElement;
						added++;
					}
				}
			} else {
				CollectionOpElement opEl = op.add(null);
				CollectionLinkElement<S, T> newElement = expectAdd(op.value, op.after, op.before, op.towardBeginning, opEl, -1);
				if (!opEl.isRejected()) {
					opEl.element = newElement;
					added++;
				}
			}
			for (CollectionOpElement el : op.elements) {
				if (!el.isRejected()) {
					int index = theElements.getElementsBefore(el.element.getElementAddress());
					if (op.minIndex >= 0
						&& (index < op.context.subListStart + op.minIndex || index > op.context.subListStart + op.maxIndex + added))
						throw new AssertionError("Added in wrong location");
				}
			}
			break;
		case remove:
			for (int i = op.elements.size() - 1; i >= 0; i--) {
				CollectionOpElement el = op.elements.get(i);
				CollectionLinkElement<S, T> exEl = el.element;
				if (exEl == null)
					continue; // A hack, putting the value to remove in the initial element
				expect(//
					new ExpectedCollectionOperation<>(exEl, op.type, exEl.get(), exEl.get()), el, -1);
			}
			break;
		case set:
			for (CollectionOpElement el : op.elements) {
				CollectionLinkElement<S, T> exEl = el.element;
				T oldValue = exEl.get();
				expect(//
					new ExpectedCollectionOperation<>(exEl, op.type, oldValue, op.value), el, -1);
			}
			break;
		}
	}

	private void addSingle(CollectionOp op, TestHelper helper) {
		prepareOp(op);
		BetterList<T> modify = op.context.modify;
		String msg;
		boolean error;
		CollectionElement<T> element;
		int preSize = getCollection().size();
		int preModSize = modify.size();
		int targetIndex;
		if (op.towardBeginning) {
			if (op.after == null) {
				targetIndex = 0;
			} else
				targetIndex = op.after.getIndex() + 1 - op.context.subListStart;
		} else {
			if (op.before == null) {
				targetIndex = preModSize;
			} else
				targetIndex = op.before.getIndex() - op.context.subListStart;
		}
		if (op.minIndex < 0 || modify.isEmpty()) {
			msg = modify.canAdd(op.value);
			// Test simple add value
			try {
				element = modify.addElement(op.value, op.towardBeginning);
				error = false;
			} catch (UnsupportedOperationException | IllegalArgumentException e) {
				error = true;
				element = null;
			}
		} else if (op.minIndex == op.maxIndex) {
			boolean addLeft;
			if (op.minIndex == 0)
				addLeft = true;
			else if (op.minIndex == modify.size())
				addLeft = false;
			else
				addLeft = helper.getBoolean();
			MutableCollectionElement<T> adjacent = modify
				.mutableElement(modify.getElement(addLeft ? op.minIndex : op.minIndex - 1).getElementId());
			msg = adjacent.canAdd(op.value, addLeft);
			if (helper.getBoolean()) {
				// Test simple add by index
				try {
					element = modify.addElement(op.minIndex, op.value);
					error = false;
				} catch (UnsupportedOperationException | IllegalArgumentException e) {
					error = true;
					element = null;
				}
			} else {
				// Test add by element
				try {
					ElementId elementId = adjacent.add(op.value, addLeft);
					element = modify.getElement(elementId);
					error = false;
				} catch (UnsupportedOperationException | IllegalArgumentException e) {
					error = true;
					element = null;
				}
			}
		} else {
			ElementId after, before;
			if (op.maxIndex == 0) {
				after = null;
				before = CollectionElement.getElementId(modify.getTerminalElement(true));
			} else if (op.minIndex == getCollection().size()) {
				before = null;
				after = modify.getTerminalElement(false).getElementId();
			} else {
				after = op.minIndex == 0 ? null : modify.getElement(op.minIndex - 1).getElementId();
				before = op.maxIndex == modify.size() ? null : modify.getElement(op.maxIndex).getElementId();
			}
			msg = modify.canAdd(op.value, after, before);
			try {
				element = modify.addElement(op.value, after, before, op.towardBeginning);
				error = false;
			} catch (UnsupportedOperationException | IllegalArgumentException e) {
				error = true;
				element = null;
			}
		}

		expectModification(op, helper);
		if (element != null && op.minIndex >= 0) {
			int index = modify.getElementsBefore(element.getElementId());
			if (index < op.minIndex || index > op.maxIndex) {
				StringBuilder str = new StringBuilder("Should have added ");
				if (op.minIndex == op.maxIndex)
					str.append("at ").append(op.minIndex);
				else
					str.append(" between ").append(op.minIndex).append(" and ").append(op.maxIndex);
				str.append(", but was [").append(index).append(']');
				throw new AssertionError(str.toString());
			}
		}
		if (op.minIndex < 0 && !getCollection().isContentControlled() && isAcceptable(op.value)) {
			if (element == null) {
				throw new AssertionError("Uncontrolled list should have added but didn't");
			} else {
				int index = modify.getElementsBefore(element.getElementId());
				if (index != targetIndex)
					throw new AssertionError(new StringBuilder("Uncontrolled list should have added at [").append(targetIndex)
						.append("] but was [").append(index).append(']').toString());
			}
		}

		CollectionOpElement el = op.elements.get(0);
		Assert.assertEquals(el.getMessage() == null, msg == null);
		Assert.assertEquals(el.isError(), error);
		if (error)
			Assert.assertNotNull(msg);
		if (element == null) {
			Assert.assertNotNull(msg);
			Assert.assertEquals(preSize, getCollection().size());
			Assert.assertEquals(preModSize, modify.size());
		} else {
			Assert.assertNull(msg);
			Assert.assertTrue(getCollection().equivalence().elementEquals(op.value, element.get()));
			Assert.assertEquals(preModSize + 1, modify.size());
			Assert.assertEquals(preSize + 1, getCollection().size());
			int index = modify.getElementsBefore(element.getElementId());
			Assert.assertTrue(index >= 0 && index <= preModSize);
			if (op.minIndex >= 0)
				Assert.assertTrue(index >= op.minIndex && index <= op.maxIndex);
		}
	}

	private void addAll(int index, boolean first, CollectionOp op, TestHelper helper) {
		prepareOp(op);
		BetterList<T> modify = op.context.modify;
		int preModSize = modify.size();
		int preSize = getCollection().size();
		ElementId after, before;
		if (op.minIndex < 0) {
			before = after = null;
		} else if (index == 0) {
			before = CollectionElement.getElementId(modify.getTerminalElement(true));
			after = null;
		} else if (index == modify.size()) {
			before = null;
			after = CollectionElement.getElementId(modify.getTerminalElement(false));
		} else {
			after = modify.getElement(index).getElementId();
			before = CollectionElement.getElementId(modify.getAdjacentElement(after, true));
		}
		int addable = 0;
		String[] msgs = new String[op.values.size()];
		int i = 0;
		for (T value : op.values) {
			msgs[i] = modify.canAdd(value, before, after);
			if (msgs[i] == null)
				addable++;
			i++;
		}
		boolean modified;
		if (index >= 0)
			modified = modify.addAll(index, op.values);
		else
			modified = modify.addAll(op.values);

		expectModification(op, helper);

		for (i = 0; i < op.elements.size(); i++)
			Assert.assertEquals(op.elements.get(i).getMessage() != null, msgs[i] != null);
		if (!getCollection().isContentControlled() && addable < op.elements.size())
			throw new AssertionError("Uncontrolled collection failed to add some values");
		Assert.assertEquals(modified, addable > 0);
		Assert.assertEquals(preModSize + addable, modify.size());
		Assert.assertEquals(preSize + addable, getCollection().size());
	}

	private void removeSingle(CollectionOp op, TestHelper helper) {
		prepareOp(op);
		BetterList<T> modify = op.context.modify;
		int preModSize = modify.size();
		int preSize = getCollection().size();
		String msg;
		boolean error;
		CollectionElement<T> element;
		if (op.minIndex < 0) {
			// Remove by value
			element = modify.getElement(op.value, true);

			if (element == null) {
				msg = StdMsg.NOT_FOUND;
				op.rejectAll(msg, false);
			} else
				msg = modify.mutableElement(element.getElementId()).canRemove();
			element = null;
			try {
				modify.remove(op.value);
				error = false;
			} catch (UnsupportedOperationException e) {
				error = true;
			}
		} else {
			// Remove by index
			element = modify.getElement(op.minIndex);
			msg = modify.mutableElement(element.getElementId()).canRemove();
			try {
				if (helper.getBoolean()) // Remove by index
					modify.remove(op.minIndex);
				else
					modify.mutableElement(element.getElementId()).remove();
				error = false;
			} catch (UnsupportedOperationException e) {
				error = true;
			}
		}

		expectModification(op, helper);

		CollectionOpElement el = op.elements.get(0);
		if (!getCollection().isContentControlled() && el.element != null && msg != null)
			throw new AssertionError("Uncontrolled collection failed to remove element");
		Assert.assertEquals(el.getMessage() != null, msg != null);
		Assert.assertEquals(el.isError(), error);
		if (error)
			Assert.assertNotNull(msg);
		if (msg == null) {
			Assert.assertEquals(preSize - 1, getCollection().size());
			Assert.assertEquals(preModSize - 1, modify.size());
		} else {
			Assert.assertEquals(preSize, getCollection().size());
			Assert.assertEquals(preModSize, modify.size());
		}
	}

	private void removeRange(CollectionOp op, TestHelper helper) {
		prepareOp(op);
		BetterList<T> modify = op.context.modify;
		int preModSize = modify.size();
		int preSize = getCollection().size();
		CollectionElement<T> element = op.minIndex == op.maxIndex ? null : modify.getElement(op.minIndex);
		int removable = 0;
		boolean expectError = false;
		String[] msgs = new String[op.maxIndex - op.minIndex];
		for (int i = 0; i < msgs.length; i++) {
			msgs[i] = modify.mutableElement(element.getElementId()).canRemove();
			if (msgs[i] == null)
				removable++;
			else if (op.elements.get(i).isError())
				expectError = true;

			element = modify.getAdjacentElement(element.getElementId(), true);
		}
		boolean error;
		try {
			modify.removeRange(op.minIndex, op.maxIndex);
			error = false;
		} catch (UnsupportedOperationException e) {
			error = false;
		}

		expectModification(op, helper);
		for (int i = 0; i < msgs.length; i++)
			Assert.assertEquals(op.elements.get(i).getMessage() != null, msgs[i] != null);

		if (!getCollection().isContentControlled() && removable < op.elements.size())
			throw new AssertionError("Uncontrolled collection failed to remove element(s)");
		Assert.assertEquals(expectError, error);
		if (error) {
			Assert.assertTrue(modify.size() > preModSize - (op.maxIndex - op.minIndex));
			Assert.assertTrue(getCollection().size() > preSize - (op.maxIndex - op.minIndex));
		} else {
			Assert.assertEquals(preModSize - removable, modify.size());
			Assert.assertEquals(preSize - removable, getCollection().size());
		}
	}

	private void removeAll(CollectionOp op, TestHelper helper) {
		prepareOp(op);
		BetterList<T> modify = op.context.modify;
		int preModSize = modify.size();
		int preSize = getCollection().size();
		boolean modified = modify.removeAll(op.values);

		expectModification(op, helper);

		int removable = 0;
		for (CollectionOpElement el : op.elements) {
			if (el.getMessage() != null)
				continue;
			removable++;
			if (getCollection() instanceof Set)
				Assert.assertNull(getCollection().getElement(el.element.getValue(), true));
		}
		if (!getCollection().isContentControlled() && removable < op.elements.size())
			throw new AssertionError("Uncontrolled collection failed to remove element(s)");
		Assert.assertEquals(removable > 0, modified);
		Assert.assertEquals(preModSize - removable, modify.size());
		Assert.assertEquals(preSize - removable, getCollection().size());
	}

	private void retainAll(Collection<T> values, CollectionOp op, TestHelper helper) {
		prepareOp(op);
		BetterList<T> modify = op.context.modify;
		int preModSize = modify.size();
		int preSize = getCollection().size();
		boolean modified = modify.retainAll(values);

		expectModification(op, helper);

		int removable = 0;
		for (CollectionOpElement el : op.elements) {
			if (el.getMessage() != null)
				continue;
			removable++;
			if (getCollection() instanceof Set)
				Assert.assertNull(getCollection().getElement(el.element.getValue(), true));
		}
		if (!getCollection().isContentControlled() && removable < op.elements.size())
			throw new AssertionError("Uncontrolled collection failed to remove element(s)");
		Assert.assertEquals(removable > 0, modified);
		Assert.assertEquals(preModSize - removable, modify.size());
		Assert.assertEquals(preSize - removable, getCollection().size());
	}

	private void set(CollectionOp op, TestHelper helper) {
		prepareOp(op);
		BetterList<T> modify = op.context.modify;
		CollectionOpElement el = op.elements.get(0);
		int preModSize = modify.size();
		int preSize = getCollection().size();
		boolean setByIndex = helper.getBoolean();
		MutableCollectionElement<T> element = modify.mutableElement(modify.getElement(op.minIndex).getElementId());
		T oldValue = element.get();
		String msg = element.isAcceptable(op.value);
		boolean error;
		if (element.isEnabled() != null)
			Assert.assertNotNull(msg);
		try {
			if (setByIndex)
				modify.set(op.minIndex, op.value);
			else
				element.set(op.value);
			error = false;
		} catch (UnsupportedOperationException | IllegalArgumentException e) {
			error = true;
		}

		expectModification(op, helper);

		boolean shouldBeSettable = el.getMessage() == null;
		boolean wasSettable = msg == null;
		if (!getCollection().isContentControlled() && !wasSettable)
			throw new AssertionError("Uncontrolled collection failed to set element");
		Assert.assertEquals(shouldBeSettable, wasSettable);
		Assert.assertEquals(el.isError(), error);
		if (error)
			Assert.assertNotNull(msg);
		Assert.assertTrue(element.getElementId().isPresent());
		Assert.assertEquals(preSize, getCollection().size());
		Assert.assertEquals(preModSize, modify.size());
		if (msg != null)
			Assert.assertEquals(oldValue, element.get());
		else
			Assert.assertEquals(op.value, element.get());
	}

	private void clearCollection(CollectionOp op, TestHelper helper) {
		prepareOp(op);
		int preModSize = op.context.modify.size();
		int preSize = getCollection().size();
		op.context.modify.clear();

		expectModification(op, helper);

		int removed = 0;
		for (CollectionOpElement el : op.elements) {
			if (el.getMessage() != null)
				continue;
			removed++;
			if (getCollection() instanceof Set)
				Assert.assertNull(getCollection().getElement(el.element.getValue(), true));
		}
		if (!getCollection().isContentControlled() && removed < op.elements.size())
			throw new AssertionError("Uncontrolled collection failed to remove element(s)");
		Assert.assertEquals(preModSize - removed, op.context.modify.size());
		Assert.assertEquals(preSize - removed, getCollection().size());
	}

	private void testBounds(TestHelper helper) {
		try {
			getCollection().get(-1);
			Assert.assertFalse("Should have errored", true);
		} catch (IndexOutOfBoundsException e) {}
		try {
			getCollection().get(getCollection().size());
			Assert.assertFalse("Should have errored", true);
		} catch (IndexOutOfBoundsException e) {}
		try {
			getCollection().remove(-1);
			Assert.assertFalse("Should have errored", true);
		} catch (IndexOutOfBoundsException e) {}
		try {
			getCollection().remove(getCollection().size());
			Assert.assertFalse("Should have errored", true);
		} catch (IndexOutOfBoundsException e) {}
		if (theSupplier != null) {
			try {
				getCollection().add(-1, theSupplier.apply(helper));
				Assert.assertFalse("Should have errored", true);
			} catch (IndexOutOfBoundsException | IllegalArgumentException e) { // We'll allow either exception
			}
			try {
				getCollection().add(getCollection().size() + 1, theSupplier.apply(helper));
				Assert.assertFalse("Should have errored", true);
			} catch (IndexOutOfBoundsException | IllegalArgumentException e) { // We'll allow either exception
			}
			try {
				getCollection().set(-1, theSupplier.apply(helper));
				Assert.assertFalse("Should have errored", true);
			} catch (IndexOutOfBoundsException | IllegalArgumentException e) { // We'll allow either exception
			}
			try {
				getCollection().set(getCollection().size(), theSupplier.apply(helper));
				Assert.assertFalse("Should have errored", true);
			} catch (IndexOutOfBoundsException | IllegalArgumentException e) { // We'll allow either exception
			}
		}
	}
}
