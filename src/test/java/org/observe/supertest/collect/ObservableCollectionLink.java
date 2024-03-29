package org.observe.supertest.collect;

import static org.observe.supertest.CollectionOpType.add;
import static org.observe.supertest.CollectionOpType.move;
import static org.observe.supertest.CollectionOpType.remove;
import static org.observe.supertest.CollectionOpType.set;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;

import org.junit.Assert;
import org.observe.collect.ObservableCollection;
import org.observe.collect.ObservableCollectionEvent;
import org.observe.collect.ObservableCollectionTester;
import org.observe.supertest.AbstractChainLink;
import org.observe.supertest.CollectionOpType;
import org.observe.supertest.ObservableChainLink;
import org.observe.supertest.ObservableChainTester;
import org.observe.supertest.OperationRejection;
import org.observe.supertest.TestValueType;
import org.qommons.collect.BetterCollections;
import org.qommons.collect.BetterList;
import org.qommons.collect.BetterSortedList.SortedSearchFilter;
import org.qommons.collect.CollectionElement;
import org.qommons.collect.ElementId;
import org.qommons.collect.MutableCollectionElement;
import org.qommons.collect.MutableCollectionElement.StdMsg;
import org.qommons.testing.QommonsTestUtils;
import org.qommons.testing.TestHelper;
import org.qommons.testing.TestHelper.RandomAction;
import org.qommons.tree.BetterTreeList;

/**
 * Abstract base class for chain links whose structures are {@link ObservableCollection}s
 *
 * @param <S> The type of the source link
 * @param <T> The type of the collection values
 */
public abstract class ObservableCollectionLink<S, T> extends AbstractChainLink<S, T> {
	private final ObservableCollectionTestDef<T> theDef;
	private final ObservableCollection<T> theOneStepCollection;
	private final ObservableCollection<T> theMultiStepCollection;
	private final ObservableCollectionTester<T> theMultiStepTester;
	private final Function<TestHelper, T> theSupplier;

	private final BetterTreeList<CollectionLinkElement<S, T>> theElements;
	private final BetterTreeList<CollectionLinkElement<S, T>> theElementsForCollection;
	private boolean isDebugging;

	/**
	 * @param path The path for this link
	 * @param sourceLink The source for this link
	 * @param def The collection definition for this link
	 * @param helper The randomness to use to initialize this link
	 */
	public ObservableCollectionLink(String path, ObservableChainLink<?, S> sourceLink, ObservableCollectionTestDef<T> def,
		TestHelper helper) {
		super(path, sourceLink);
		theDef = def;
		theSupplier = (Function<TestHelper, T>) ObservableChainTester.SUPPLIERS.get(def.type);
		theElements = BetterTreeList.<CollectionLinkElement<S, T>>build().build();
		theElementsForCollection = BetterTreeList.<CollectionLinkElement<S, T>> build().build();
		boolean passive = def.oneStepFlow.supportsPassive() && (helper == null || helper.getBoolean());
		if (passive)
			theOneStepCollection = def.oneStepFlow.collectPassive();
		else
			theOneStepCollection = def.oneStepFlow.collectActive(getDestruction());
		if (passive && def.multiStepFlow.supportsPassive())
			theMultiStepCollection = def.multiStepFlow.collectPassive();
		else
			theMultiStepCollection = def.multiStepFlow.collectActive(getDestruction());

		theMultiStepTester = new ObservableCollectionTester<>(getPath() + " Multi-step", theMultiStepCollection);
		theMultiStepTester.setOrderImportant(def.orderImportant);
		theMultiStepTester.checkRemovedValues(def.checkOldValues);
		init(helper);
	}

	/**
	 * Constructor that also provides the actual collections
	 *
	 * @param path The path for this link
	 * @param sourceLink The source for this link
	 * @param def The collection definition for this link
	 * @param oneStepCollection The one-step collection descended from the source link's
	 * @param multiStepCollection The multi-step collection descended from the root link's
	 * @param helper The randomness to use to initialize this link
	 */
	public ObservableCollectionLink(String path, ObservableChainLink<?, S> sourceLink, ObservableCollectionTestDef<T> def,
		ObservableCollection<T> oneStepCollection, ObservableCollection<T> multiStepCollection, TestHelper helper) {
		super(path, sourceLink);
		theDef = def;
		theSupplier = (Function<TestHelper, T>) ObservableChainTester.SUPPLIERS.get(def.type);
		theElements = BetterTreeList.<CollectionLinkElement<S, T>> build().build();
		theElementsForCollection = BetterTreeList.<CollectionLinkElement<S, T>> build().build();
		theOneStepCollection = oneStepCollection;
		theMultiStepCollection = multiStepCollection;

		theMultiStepTester = new ObservableCollectionTester<>(getPath() + " Multi-step", theMultiStepCollection);
		theMultiStepTester.setOrderImportant(def.orderImportant);
		theMultiStepTester.checkRemovedValues(def.checkOldValues);
		init(helper);
	}

	private void init(TestHelper helper) {
		if (getSourceLink() == null && helper != null && theSupplier != null && helper.getBoolean(.25)) {
			int size = helper.getInt(0, 10);
			try {
				for (int i = 0; i < size; i++)
					getCollection().add(theSupplier.apply(helper));
			} catch (UnsupportedOperationException e) {
				// Allow it--for example, the flat base collection can't add elements if the value is null
			}
		}

		// Listen to the collection to populate and maintain theElements
		getCollection().subscribe(new Consumer<ObservableCollectionEvent<? extends T>>() {
			@Override
			public void accept(ObservableCollectionEvent<? extends T> evt) {
				if (isDebugging)
					System.out.println(getPath() + ": " + evt);
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
					removed.get().removed(evt.getOldValue());
					theElementsForCollection.mutableElement(removed.getElementId()).remove();
					break;
				case set:
					CollectionLinkElement<S, T> element = theElementsForCollection.get(evt.getIndex());
					element.updated(evt.getOldValue(), evt.getNewValue());
					break;
				}
			}
		}, true);
	}

	@Override
	public void initialize(TestHelper helper) {
		super.initialize(helper);
	}

	/**
	 * Validates aspects of a single element
	 *
	 * @param element The element to validate
	 * @param transactionEnd Whether any transaction begun before the modification has been closed
	 * @throws AssertionError If this link's data is not valid
	 */
	protected abstract void validate(CollectionLinkElement<S, T> element, boolean transactionEnd) throws AssertionError;

	@Override
	public TestValueType getType() {
		return theDef.type;
	}

	/** @return This link's collection definition */
	public ObservableCollectionTestDef<T> getDef() {
		return theDef;
	}

	/** @return The source of new values for this link */
	public Function<TestHelper, T> getValueSupplier() {
		return theSupplier;
	}

	@Override
	public List<CollectionSourcedLink<T, ?>> getDerivedLinks() {
		return (List<CollectionSourcedLink<T, ?>>) super.getDerivedLinks();
	}

	/** @return This link's main collection */
	public ObservableCollection<T> getCollection() {
		return theOneStepCollection;
	}

	/**
	 * @return Another collection managed by this link using the same underlying mechanisms, but without intermediate
	 *         {@link org.observe.collect.ObservableCollection.CollectionDataFlow#collect() collecting}
	 */
	public ObservableCollection<T> getMultiStepCollection() {
		return theMultiStepCollection;
	}

	BetterList<CollectionLinkElement<S, T>> getUnprotectedElements() {
		return theElements;
	}

	/** @return This link's elements, each a representation of an element in the collection */
	public BetterList<CollectionLinkElement<S, T>> getElements() {
		return BetterCollections.unmodifiableList(theElements);
	}

	/**
	 * @param collectionEl The collection element ID to get the test element for
	 * @return The test element associated with the given collection element
	 */
	public CollectionLinkElement<S, T> getElement(ElementId collectionEl) {
		return CollectionElement
			.get(theElementsForCollection.search(el -> collectionEl.compareTo(el.getCollectionAddress()), SortedSearchFilter.OnlyMatch));
	}

	/**
	 *
	 * @param value The value to attempt to add
	 * @param after The element to add the value after (if any)
	 * @param before The element to add the value before (if any)
	 * @param first Whether to attempt to add the value near the beginning of the specified range
	 * @param rejection The rejection capability for the operation
	 * @param execute Whether to actually execute the operation if it is not rejected
	 * @return The new element
	 */
	public abstract CollectionLinkElement<S, T> expectAdd(T value, CollectionLinkElement<?, T> after, CollectionLinkElement<?, T> before,
		boolean first, OperationRejection rejection, boolean execute);

	/**
	 * @param source The element to attempt to move
	 * @param after The element to move the element after (if any)
	 * @param before The element to move the element before (if any)
	 * @param first Whether to attempt to move the element near the beginning of the specified range
	 * @param rejection The rejection capability for the operation
	 * @param execute Whether to actually execute the operation if it is not rejected
	 * @return The new element
	 */
	public abstract CollectionLinkElement<S, T> expectMove(CollectionLinkElement<?, T> source, CollectionLinkElement<?, T> after,
		CollectionLinkElement<?, T> before, boolean first, OperationRejection rejection, boolean execute);

	/**
	 * @param derivedOp The non-add, non-move operation to attempt
	 * @param rejection The rejection capability for the operation
	 * @param execute Whether to actually execute the operation if it is not rejected
	 */
	public abstract void expect(ExpectedCollectionOperation<?, T> derivedOp, OperationRejection rejection, boolean execute);

	/**
	 * @param value The value to test
	 * @return Whether the given value may be acceptable as a value in this link's collection
	 */
	public abstract boolean isAcceptable(T value);

	/**
	 * @param element The element to update the value for
	 * @param value The input value
	 * @return The value, reverse-mapped to the root, then re-mapped back to this collection
	 */
	public abstract T getUpdateValue(CollectionLinkElement<S, T> element, T value);

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
		// Composite collections and sub lists don't go together--unexpected adds or removes before the initial sub list index
		// make the sub-list inconsistent. There's not an efficient workaround for this.
		// Composite collections aren't really an excellent use-case anyway--it's just convenient for testing
		boolean subList = !isComposite() && helper.getBoolean(.05);
		if (subList) {
			subListStart = helper.getInt(0, getCollection().size());
			subListEnd = subListStart + helper.getInt(0, getCollection().size() - subListStart);
			modify = getCollection().subList(subListStart, subListEnd);
			if (helper.isReproducing())
				System.out.print("subList(" + subListStart + ".." + subListEnd + ") ");
			Assert.assertEquals(subListEnd - subListStart, modify.size());
			if (subListEnd > subListStart) {
				Assert.assertEquals(getCollection().getElement(subListStart), modify.getTerminalElement(true));
				if (subListEnd > subListStart + 1)
					Assert.assertEquals(getCollection().getElement(subListEnd - 1), modify.getTerminalElement(false));
			} else
				Assert.assertTrue(modify.isEmpty());
		} else {
			subListStart = 0;
			subListEnd = getCollection().size();
			modify = getCollection();
		}
		CollectionOpContext opCtx = new CollectionOpContext(modify, subList, subListStart, subListEnd);
		if (theSupplier != null) {
			action.or(10, () -> { // More position-less adds than other ops
				T value = theSupplier.apply(helper);
				tryAdd(opCtx, value, helper, null);
			}).or(3, () -> { // Add in position range
				int minIndex = (int) helper.getDouble(0, modify.size() / 3, modify.size());
				int maxIndex = helper.getInt(minIndex, modify.size());
				T value = theSupplier.apply(helper);
				CollectionOp op = new CollectionOp(opCtx, add, minIndex, maxIndex, value, helper.getBoolean());
				if (helper.isReproducing())
					System.out.println(op);
				helper.placemark();
				addSingle(op, helper, null);
			}).or(.1, () -> { // addAll
				int length = (int) helper.getDouble(0, 2, 15); // Aggressively tend smaller
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
			});
			if (!modify.isEmpty()) {
				action.or(2, () -> { // Set
					if (modify.isEmpty()) {
						if (helper.isReproducing())
							System.out.println("Set, but empty");
						return;
					}
					int index = helper.getInt(0, modify.size());
					T value;
					CollectionLinkElement<S, T> element = theElements.get(opCtx.subListStart + index);
					boolean update = helper.getBoolean(0.1);
					if (update) // More updates
						value = element.getValue();
					else
						value = theSupplier.apply(helper);
					CollectionOp op = new CollectionOp(opCtx, set, index, index, value, false);
					op.add(element);
					if (helper.isReproducing())
						System.out.println(op + (update ? "(update)" : "") + " from " + element.getValue());
					helper.placemark();
					set(op, helper);
				});
			}
			action.or(1, () -> {// Remove by value
				T value = theSupplier.apply(helper);
				tryRemove(opCtx, value, helper, null);
			}).or(.2, () -> { // removeAll
				int length = (int) helper.getDouble(0, 25, 100); // Tend smaller
				List<T> values = new ArrayList<>(length);
				// The removeAll method uses the equivalence of the given collection. See BetterCollection#removeAll(Collection).
				Set<T> valueSet = new HashSet<>();
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
				// The retainAll method uses the equivalence of the given collection. See BetterCollection#retainAll(Collection).
				Set<T> valueSet = new HashSet<>();
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
		if (!modify.isEmpty()) {
			action.or(2, () -> {// Remove by index
				int index = helper.getInt(0, modify.size());
				CollectionOp op = new CollectionOp(opCtx, remove, index, index, null, true);
				op.add(theElements.get(opCtx.subListStart + index));
				if (helper.isReproducing()) {
					System.out.println(op);
					System.out.println("\t\tShould remove " + op.printElements());
				}
				helper.placemark();
				removeSingle(op, helper, null);
			}).or(.1, () -> { // Remove range
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
			}).or(.5, () -> { // Move element to index
				int source = helper.getInt(0, modify.size());
				int target = helper.getInt(0, modify.size());
				CollectionOp op = new CollectionOp(opCtx, move, target, target, null, false);
				op.add(theElements.get(opCtx.subListStart + source));
				if (helper.isReproducing())
					System.out.println(op);
				helper.placemark();
				move(op, helper);
			}).or(.5, () -> { // Move element to index range
				int source = helper.getInt(0, modify.size());
				int max = modify.size();
				int minIndex = (int) helper.getDouble(0, max / 3.0, max);
				int maxIndex = helper.getInt(minIndex, max);
				CollectionOp op = new CollectionOp(opCtx, move, minIndex, maxIndex, null, false);
				op.add(theElements.get(opCtx.subListStart + source));
				if (helper.isReproducing())
					System.out.println(op);
				helper.placemark();
				move(op, helper);
			});
		}
		action.or(.1, () -> { // clear
			tryClear(opCtx, helper);
		}).or(1, () -> {
			if (helper.isReproducing())
				System.out.println("Check bounds");
			helper.placemark();
			testNoModOps(helper);
		});
	}

	@Override
	public void validate(boolean transactionEnd) throws AssertionError {
		StringBuilder error = new StringBuilder();
		for (CollectionLinkElement<S, T> link : theElements) {
			validate(link, transactionEnd);
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

	/* These are used by the ObservableMap supertester classes which are not currently in this branch
	private CollectionOpContext getStandardContext() {
		return new CollectionOpContext(getCollection(), false, 0, getCollection().size());
	}

	public void tryAdd(T value, TestHelper helper, Function<Boolean, CollectionElement<T>> subExecute) {
		tryAdd(getStandardContext(), value, helper, subExecute);
	}

	public void tryRemove(T value, TestHelper helper, BooleanSupplier subExecute) {
		tryRemove(getStandardContext(), value, helper, subExecute);
	}

	public void tryClear(TestHelper helper) {
		tryClear(getStandardContext(), helper);
	}*/

	void tryAdd(CollectionOpContext opCtx, T value, TestHelper helper, Function<Boolean, CollectionElement<T>> subExecute) {
		CollectionOp op = new CollectionOp(opCtx, add, -1, -1, value, helper.getBoolean());
		if (helper.isReproducing())
			System.out.println(op);
		helper.placemark();
		addSingle(op, helper, subExecute);
	}

	void tryRemove(CollectionOpContext opCtx, T value, TestHelper helper, BooleanSupplier subExecute) {
		CollectionOp op = new CollectionOp(opCtx, remove, -1, -1, value, true);
		boolean found = false;
		for (int i = 0; i < opCtx.modify.size(); i++) {
			if (getCollection().equivalence().elementEquals(opCtx.modify.get(i), value)) {
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
		removeSingle(op, helper, subExecute);
	}

	void tryClear(CollectionOpContext opCtx, TestHelper helper) {
		if (helper.isReproducing())
			System.out.println("clear()");
		CollectionOp op = new CollectionOp(opCtx, remove, 0, opCtx.modify.size(), null, false);
		for (int i = 0; i < opCtx.modify.size(); i++)
			op.add(theElements.get(opCtx.subListStart + i));
		helper.placemark();
		clearCollection(op, helper);
	}

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
		final CollectionOpType type;
		final T value;
		final List<T> values;
		final int minIndex;
		final int maxIndex;
		final boolean towardBeginning;

		CollectionLinkElement<S, T> after;
		CollectionLinkElement<S, T> before;

		final List<CollectionOpElement> elements;

		CollectionOp(CollectionOpContext ctx, CollectionOpType type, int minIndex, int maxIndex, T value, boolean towardBeginning) {
			context = ctx;
			this.type = type;
			this.minIndex = minIndex;
			this.maxIndex = maxIndex;
			this.value = value;
			this.towardBeginning = towardBeginning;
			values = null;
			elements = new ArrayList<>();
		}

		CollectionOp(CollectionOpContext ctx, CollectionOpType type, int index, List<T> values) {
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

		void rejectAll(String message) {
			for (CollectionOpElement el : elements)
				el.reject(message);
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
			if (type == CollectionOpType.move) {
				str.append('[').append(elements.get(0).element.getIndex()).append(']').append(elements.get(0).element.getValue());
			} else if (values != null)
				str.append(values.size()).append(values);
			else if (value != null || minIndex < 0)
				str.append(value);
			return str.toString();
		}

		public String printElements() {
			return elements.size() + elements.toString();
		}
	}

	private class CollectionOpElement extends OperationRejection.Simple {
		CollectionLinkElement<S, T> element;

		CollectionOpElement(CollectionLinkElement<S, T> element) {
			this.element = element;
		}

		@Override
		public String toString() {
			if (element == null)
				return "none";
			return element.toString();
		}
	}

	private void prepareOp(CollectionOp op) {
		if (!op.context.subList && op.minIndex < 0)
			return;
		int minIndex = op.context.subListStart;
		int maxIndex = op.context.subListEnd;
		if (op.minIndex >= 0) {
			minIndex += op.minIndex;
			maxIndex = op.context.subListStart + op.maxIndex;
		}
		if (minIndex == theElements.size()) {
			op.after = theElements.peekLast();
			op.before = null;
		} else if (maxIndex == 0) {
			op.after = null;
			op.before = theElements.peekFirst();
		} else if (op.type == CollectionOpType.move) {
			op.after = theElements.isEmpty() ? null : theElements.get(minIndex);
			op.before = maxIndex >= theElements.size() - 1 ? null : theElements.get(maxIndex + 1);
		} else {
			op.after = minIndex == 0 ? null : theElements.get(minIndex - 1);
			op.before = maxIndex == theElements.size() ? null : theElements.get(maxIndex);
		}
	}

	private void expectModification(CollectionOp op, int elements, TestHelper helper) {
		int sz = Math.min(theElements.size(), op.context.subListEnd) - op.context.subListStart;
		if (op.maxIndex > sz) {
			op.rejectAll(op.maxIndex + " of " + sz);
			return;
		}
		switch (op.type) {
		case add:
			int expectAdded = 0;
			if (op.values != null) {
				for (int i = 0; i < op.values.size(); i++) {
					T value = op.values.get(i);
					CollectionOpElement opEl = op.elements.get(i);
					CollectionLinkElement<S, T> newElement = expectAdd(value, op.after, op.before, op.towardBeginning, opEl, true);
					if (!opEl.isRejected()) {
						opEl.element = newElement;
						expectAdded++;
					}
				}
			} else {
				CollectionOpElement opEl = op.elements.get(0);
				CollectionLinkElement<S, T> newElement = expectAdd(op.value, op.after, op.before, op.towardBeginning, opEl, true);
				if (!opEl.isRejected()) {
					opEl.element = newElement;
					expectAdded++;
				}
			}
			if(!isComposite())
				Assert.assertEquals(expectAdded, elements);
			int tolerance = elements - expectAdded;
			for (CollectionOpElement el : op.elements) {
				if (!el.isRejected()) {
					int index = theElements.getElementsBefore(el.element.getElementAddress());
					if (op.minIndex >= 0
						&& (index < op.context.subListStart + op.minIndex - tolerance
							|| index > op.context.subListStart + op.maxIndex + expectAdded + tolerance))
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
					new ExpectedCollectionOperation<>(exEl, op.type, exEl.getValue(), exEl.getValue()), el, true);

			}
			break;
		case set:
			for (CollectionOpElement el : op.elements) {
				CollectionLinkElement<S, T> exEl = el.element;
				T oldValue = exEl.getValue();
				expect(//
					new ExpectedCollectionOperation<>(exEl, op.type, oldValue, op.value), el, true);
			}
			break;
		case move:
			CollectionOpElement opEl = op.elements.get(0);
			opEl.element = expectMove(opEl.element, op.after, op.before, op.towardBeginning, opEl, true);
		}
	}

	private void addSingle(CollectionOp op, TestHelper helper, Function<Boolean, CollectionElement<T>> subExecute) {
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
				if (subExecute != null)
					element = subExecute.apply(op.towardBeginning);
				else
					element = modify.addElement(op.value, op.towardBeginning);
				error = false;
			} catch (UnsupportedOperationException | IllegalArgumentException e) {
				if (msg == null)
					throw new AssertionError("Unexpected operation exception", e);
				error = true;
				element = null;
			}
		} else if (op.minIndex == op.maxIndex) {
			ElementId after, before;
			if (op.minIndex == 0) {
				after = null;
				before = CollectionElement.getElementId(modify.getTerminalElement(true));
			} else if (op.minIndex == modify.size()) {
				after = modify.getTerminalElement(false).getElementId();
				before = null;
			} else {
				before = modify.getElement(op.minIndex).getElementId();
				after = modify.getAdjacentElement(before, false).getElementId();
			}
			msg = modify.canAdd(op.value, after, before);
			if (op.towardBeginning) {
				// Test simple add by index
				// Adding by this method uses an implicit towards-beginning boolean of true
				try {
					if (subExecute != null)
						element = subExecute.apply(op.towardBeginning);
					else
						element = modify.addElement(op.minIndex, op.value);
					error = false;
				} catch (UnsupportedOperationException | IllegalArgumentException e) {
					if (msg == null)
						throw new AssertionError("Unexpected operation exception", e);
					error = true;
					element = null;
				}
			} else {
				try {
					if (subExecute != null)
						element = subExecute.apply(op.towardBeginning);
					else
						element = modify.addElement(op.value, //
							op.after == null ? null : op.after.getCollectionAddress(), //
								op.before == null ? null : op.before.getCollectionAddress(), op.towardBeginning);
					error = false;
				} catch (UnsupportedOperationException | IllegalArgumentException e) {
					if (msg == null)
						throw new AssertionError("Unexpected operation exception", e);
					error = true;
					element = null;
				}
			}
			if (element != null && getCollection().size() == preSize + 1)
				Assert.assertEquals(op.minIndex, modify.getElementsBefore(element.getElementId()));
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
				if (msg == null)
					throw new AssertionError("Unexpected operation exception", e);
				error = true;
				element = null;
			}
			if (element != null) {
				if (after != null && element.getElementId().compareTo(after) < 0)//
					throw new AssertionError("Expected add after [" + modify.getElementsBefore(after) + "], but was ["
						+ modify.getElementsBefore(element.getElementId()) + "]");
				if (before != null && element.getElementId().compareTo(before) > 0)//
					throw new AssertionError("Expected add before [" + modify.getElementsBefore(before) + "], but was ["
						+ modify.getElementsBefore(element.getElementId()) + "]");
			}
		}

		op.add(null).withActualRejection(msg);
		int added = getCollection().size() - preSize;
		expectModification(op, added, helper);
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
		if (msg == null && el.getRejection() != null)
			throw new AssertionError("Expected rejection with " + el.getRejection());
		else if (msg != null && el.getRejection() == null)
			throw new AssertionError("Unexpected rejection with " + msg);
		Assert.assertEquals(el.getRejection() == null, msg == null);
		if (msg != null && !error) {
			Assert.assertEquals(StdMsg.ELEMENT_EXISTS, msg);
			// If inexact reverse is allowed, the exact value may not be present even though it cannot be added
			if (!is(ChainLinkFlag.INEXACT_REVERSIBLE) && !modify.contains(op.value))
				throw new AssertionError("Rejection with " + msg + " was expected to generate an error");
		}
		if (error)
			Assert.assertNotNull(msg);
		if (element == null) {
			Assert.assertNotNull(msg);
			Assert.assertEquals(preSize, getCollection().size());
			Assert.assertEquals(preModSize, modify.size());
		} else {
			Assert.assertNull(msg);
			if (!is(ObservableChainLink.ChainLinkFlag.INEXACT_REVERSIBLE))
				Assert.assertTrue(getCollection().equivalence().elementEquals(op.value, element.get()));
			if(!isComposite())
				Assert.assertEquals(1, added);
			if(added>1){
				Assert.assertTrue(modify.size() > preModSize);
				Assert.assertTrue(getCollection().size() > preSize);
			} else{
				Assert.assertEquals(preModSize+1, modify.size());
				Assert.assertEquals(preSize+1, getCollection().size());
				int index = modify.getElementsBefore(element.getElementId());
				Assert.assertTrue(index >= 0 && index < preModSize + added);
				if (op.minIndex >= 0)
					Assert.assertTrue(index >= op.minIndex && index <= op.maxIndex);
			}
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
			before = modify.getElement(index).getElementId();
			after = CollectionElement.getElementId(modify.getAdjacentElement(before, false));
		}
		String[] msgs = new String[op.values.size()];
		int i = 0;
		for (T value : op.values) {
			msgs[i] = modify.canAdd(value, after, before);
			op.add(null).withActualRejection(msgs[i]);
			i++;
		}
		boolean modified;
		if (index >= 0)
			modified = modify.addAll(index, op.values);
		else
			modified = modify.addAll(op.values);

		int added = getCollection().size() - preSize;
		expectModification(op, added, helper);

		int addable = 0;
		for (i = 0; i < op.elements.size(); i++) {
			String expectMsg = op.elements.get(i).getRejection();
			String msg = msgs[i];
			if (msg != null && expectMsg == null)
				throw new AssertionError("Unexpected rejection of add[" + i + "]=" + op.values.get(i) + " with " + msg);
			// If the collection is distinct and there are duplicates in the values to add,
			// duplicates won't be added, but this can't be detected with the canAdd() call above
			if (msg == null && expectMsg != null && !StdMsg.ELEMENT_EXISTS.equals(expectMsg))
				throw new AssertionError("Expected rejection of add[" + i + "]=" + op.values.get(i) + " with " + expectMsg);
			if (expectMsg == null)
				addable++;
		}
		if (!getCollection().isContentControlled() && addable < op.elements.size())
			throw new AssertionError("Uncontrolled collection failed to add some values");
		Assert.assertEquals(modified, addable > 0);
		if(isComposite()){
			Assert.assertTrue(modify.size() >= preModSize + addable);
			Assert.assertTrue(getCollection().size() >= preSize + addable);
		} else{
			Assert.assertEquals(addable, added);
			Assert.assertEquals(preModSize + addable, modify.size());
		}
	}

	private void removeSingle(CollectionOp op, TestHelper helper, BooleanSupplier subExecute) {
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
				op.rejectAll(msg);
			} else
				msg = modify.mutableElement(element.getElementId()).canRemove();
			try {
				boolean modified;
				if (subExecute != null)
					modified = subExecute.getAsBoolean();
				else
					modified = modify.remove(op.value);
				if (modified != (element != null))
					throw new AssertionError("remove(Object) may not return false if the value exists");
				error = false;
			} catch (UnsupportedOperationException e) {
				if (msg == null)
					throw new AssertionError("Unexpected operation exception", e);
				error = true;
			}
		} else if (subExecute != null) {
			throw new IllegalStateException("Substitute execution not implemented for remove by index");
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
				if (msg == null)
					throw new AssertionError("Unexpected operation exception", e);
				error = true;
			}
		}

		op.elements.get(0).withActualRejection(msg);
		int removed = preSize - getCollection().size();
		expectModification(op, removed, helper);

		CollectionOpElement el = op.elements.get(0);
		if (!getCollection().isContentControlled() && el.element != null && msg != null)
			throw new AssertionError("Uncontrolled collection failed to remove element");
		if (msg == null && el.getRejection() != null)
			throw new AssertionError("Expected rejection with " + el.getRejection());
		else if (msg != null && el.getRejection() == null)
			throw new AssertionError("Unexpected rejection with " + msg);
		if (error)
			Assert.assertNotNull(msg);
		if (msg == null) {
			if(isComposite()){
				Assert.assertTrue(getCollection().size() < preSize);
				Assert.assertTrue(modify.size() < preModSize);
			} else{
				Assert.assertEquals(1, removed);
				Assert.assertEquals(preModSize - 1, modify.size());
			}
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
		String[] msgs = new String[op.maxIndex - op.minIndex];
		for (int i = 0; i < msgs.length; i++) {
			msgs[i] = modify.mutableElement(element.getElementId()).canRemove();
			op.elements.get(i).withActualRejection(msgs[i]);
			if (msgs[i] == null)
				removable++;

			element = modify.getAdjacentElement(element.getElementId(), true);
		}
		boolean error;
		try {
			modify.removeRange(op.minIndex, op.maxIndex);
			error = false;
		} catch (UnsupportedOperationException e) {
			error = false;
		}

		int removed = preSize - getCollection().size();
		expectModification(op, removed, helper);
		for (int i = 0; i < msgs.length; i++) {
			String msg = op.elements.get(i).getRejection();
			Assert.assertEquals(msg != null, msgs[i] != null);
		}

		if (!getCollection().isContentControlled() && removable < op.elements.size())
			throw new AssertionError("Uncontrolled collection failed to remove element(s)");
		if (error) {
			Assert.assertTrue(modify.size() > preModSize - (op.maxIndex - op.minIndex));
			Assert.assertTrue(getCollection().size() > preSize - (op.maxIndex - op.minIndex));
		} else if(isComposite()){
			Assert.assertTrue(modify.size() <= preModSize - removable);
			Assert.assertTrue(getCollection().size() <= preSize - removable);
		} else{
			Assert.assertEquals(removable, removed);
			Assert.assertEquals(preModSize - removable, modify.size());
		}
	}

	private void removeAll(CollectionOp op, TestHelper helper) {
		prepareOp(op);
		BetterList<T> modify = op.context.modify;
		int preModSize = modify.size();
		int preSize = getCollection().size();
		for (CollectionOpElement el : op.elements)
			el.withActualRejection(modify.mutableElement(el.element.getCollectionAddress()).canRemove());
		boolean modified = modify.removeAll(op.values);

		int removed = preSize - getCollection().size();
		expectModification(op, removed, helper);

		int removable = 0;
		for (CollectionOpElement el : op.elements) {
			if (el.getRejection() != null)
				continue;
			removable++;
			if (getCollection() instanceof Set)
				Assert.assertNull(getCollection().getElement(el.element.getValue(), true));
		}
		if (!getCollection().isContentControlled() && removable < op.elements.size())
			throw new AssertionError("Uncontrolled collection failed to remove element(s)");
		Assert.assertEquals(removable > 0, modified);
		if(isComposite()){
			Assert.assertTrue(modify.size() <= preModSize - removable);
			Assert.assertTrue(getCollection().size() <= preSize - removable);
		} else{
			Assert.assertEquals(removable, removed);
			Assert.assertEquals(preModSize - removable, modify.size());
		}
	}

	private void retainAll(Collection<T> values, CollectionOp op, TestHelper helper) {
		prepareOp(op);
		BetterList<T> modify = op.context.modify;
		int preModSize = modify.size();
		int preSize = getCollection().size();
		for (CollectionOpElement el : op.elements)
			el.withActualRejection(modify.mutableElement(el.element.getCollectionAddress()).canRemove());
		boolean modified = modify.retainAll(values);

		int removed = preSize - getCollection().size();
		expectModification(op, removed, helper);

		int removable = 0;
		for (CollectionOpElement el : op.elements) {
			if (el.getRejection() != null)
				continue;
			removable++;
			if (getCollection() instanceof Set)
				Assert.assertNull(getCollection().getElement(el.element.getValue(), true));
		}
		if (!getCollection().isContentControlled() && removable < op.elements.size())
			throw new AssertionError("Uncontrolled collection failed to remove element(s)");
		Assert.assertEquals(removable > 0, modified);
		if(isComposite()){
			Assert.assertTrue(modify.size() <= preModSize - removable);
			Assert.assertTrue(getCollection().size() <= preSize - removable);
		} else{
			Assert.assertEquals(removable, removed);
			Assert.assertEquals(preModSize - removable, modify.size());
		}
	}

	private void move(CollectionOp op, TestHelper helper) {
		prepareOp(op);
		int preSize = getCollection().size();
		int preModSize = op.context.modify.size();
		// if (op.after != null && op.after.getElementAddress().equals(op.elements.get(0).element.getElementAddress()))
		// op.after = CollectionElement.get(theElements.getAdjacentElement(op.after.getElementAddress(), false));
		// if (op.before != null && op.before.getElementAddress().equals(op.elements.get(0).element.getElementAddress()))
		// op.before = CollectionElement.get(theElements.getAdjacentElement(op.before.getElementAddress(), true));
		ElementId after = op.after == null ? null : op.after.getCollectionAddress();
		ElementId before = op.before == null ? null : op.before.getCollectionAddress();
		String msg = op.context.modify.canMove(//
			op.elements.get(0).element.getCollectionAddress(), after, before);

		CollectionElement<T> moved = null;
		boolean error;
		try {
			moved = op.context.modify.move(//
				op.elements.get(0).element.getCollectionAddress(), //
				after, before, op.towardBeginning, null);
			error = false;
		} catch (UnsupportedOperationException | IllegalArgumentException e) {
			if (msg == null)
				throw new AssertionError("Unexpected operation exception", e);
			if ((after == null || op.elements.get(0).element.getCollectionAddress().compareTo(after) >= 0)//
				&& (before == null || op.elements.get(0).element.getCollectionAddress().compareTo(before) <= 0))
				throw new AssertionError("Trivial movement operations should never be rejected");
			error = true;
		}

		if (moved != null) {
			if (after != null && after.isPresent() && moved.getElementId().compareTo(after) < 0)//
				throw new AssertionError("Expected move after [" + op.context.modify.getElementsBefore(after) + "], but was ["
					+ op.context.modify.getElementsBefore(moved.getElementId()) + "]");
			if (before != null && before.isPresent() && moved.getElementId().compareTo(before) > 0)//
				throw new AssertionError("Expected move before [" + op.context.modify.getElementsBefore(before) + "], but was ["
					+ op.context.modify.getElementsBefore(moved.getElementId()) + "]");
			Assert.assertTrue(getCollection().equivalence().elementEquals(op.elements.get(0).element.getValue(), //
				op.context.modify.getElement(moved.getElementId()).get())); // Just verify all the links are still working
		}

		op.elements.get(0).withActualRejection(msg);
		expectModification(op, 0, helper);

		CollectionOpElement el = op.elements.get(0);
		if (!getCollection().isContentControlled() && msg != null)
			throw new AssertionError("Uncontrolled collection failed to move element: " + msg);
		if (el.getRejection() != null && msg == null)
			throw new AssertionError("Expected rejection with " + el.getRejection());
		else if (el.getRejection() == null && msg != null)
			throw new AssertionError("Unexpected rejection with " + msg);
		if (error)
			Assert.assertNotNull(msg);
		if (!isComposite()) {
			Assert.assertEquals(preSize, getCollection().size());
			Assert.assertEquals(preModSize, op.context.modify.size());
		}
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
			if (msg != null)
				throw new AssertionError("Expected failure with " + msg);
		} catch (UnsupportedOperationException | IllegalArgumentException e) {
			if (msg == null)
				throw new AssertionError("Unexpected operation exception", e);
			error = true;
		}
		modify.getElement(element.getElementId()).get(); // Just verify all the links are still working

		el.withActualRejection(msg);
		expectModification(op, 0, helper);

		if (el.getRejection() == null && msg != null)
			throw new AssertionError("Unexpected rejection with " + msg);
		else if (el.getRejection() != null && msg == null)
			throw new AssertionError("Expected rejection with " + el.getRejection());
		boolean wasSettable = msg == null;
		if (!getCollection().isContentControlled() && !wasSettable)
			throw new AssertionError("Uncontrolled collection failed to set element");
		if (error)
			Assert.assertNotNull(msg);
		Assert.assertTrue(element.getElementId().isPresent());
		Assert.assertEquals(preSize, getCollection().size());
		Assert.assertEquals(preModSize, modify.size());
		if (msg != null)
			Assert.assertEquals(oldValue, element.get());
		else if (!is(ObservableChainLink.ChainLinkFlag.INEXACT_REVERSIBLE))
			Assert.assertEquals(op.value, element.get());
	}

	private void clearCollection(CollectionOp op, TestHelper helper) {
		prepareOp(op);
		int preModSize = op.context.modify.size();
		int preSize = getCollection().size();
		for (CollectionOpElement el : op.elements)
			el.withActualRejection(op.context.modify.mutableElement(el.element.getCollectionAddress()).canRemove());
		op.context.modify.clear();

		int removed = preSize - getCollection().size();
		expectModification(op, removed, helper);

		int expectRemoved = 0;
		for (CollectionOpElement el : op.elements) {
			if (el.getRejection() != null)
				continue;
			expectRemoved++;
			if (getCollection() instanceof Set)
				Assert.assertNull(getCollection().getElement(el.element.getValue(), true));
		}
		if (!getCollection().isContentControlled() && expectRemoved < op.elements.size())
			throw new AssertionError("Uncontrolled collection failed to remove element(s)");
		if(isComposite()){
			Assert.assertTrue(op.context.modify.size() <= preModSize - expectRemoved);
			Assert.assertTrue(getCollection().size() <= preSize - expectRemoved);
		} else{
			Assert.assertEquals(expectRemoved, removed);
			Assert.assertEquals(preModSize - removed, op.context.modify.size());
			Assert.assertEquals(preSize - removed, getCollection().size());
		}
	}

	private void testNoModOps(TestHelper helper) {
		// Test index bounds
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

		// Test toArray()
		Object[] referenceArray = new Object[getCollection().size()];
		int i = 0;
		for (T value : getCollection())
			referenceArray[i++] = value;
		Assert.assertEquals(i, getCollection().size());
		QommonsTestUtils.assertThat(Arrays.asList(getCollection().toArray()), //
			QommonsTestUtils.collectionsEqual(Arrays.asList(referenceArray), true));
		QommonsTestUtils.assertThat(Arrays.asList(getMultiStepCollection().toArray()), //
			QommonsTestUtils.collectionsEqual(Arrays.asList(referenceArray), theDef.orderImportant));

		// Test equals(Object)
		List<T> refList = new ArrayList<>(referenceArray.length);
		refList.addAll((List<T>) Arrays.asList(referenceArray));
		Assert.assertEquals(getCollection(), refList);
		if (theDef.orderImportant)
			Assert.assertEquals(getMultiStepCollection(), refList);
		if (!refList.isEmpty() && helper.getBoolean())
			refList.remove(helper.getInt(0, refList.size()));
		else if (theSupplier != null)
			refList.add(theSupplier.apply(helper));
		else
			return;
		Assert.assertNotEquals(getCollection(), refList);
		Assert.assertNotEquals(getMultiStepCollection(), refList);

		// Test toString()
		String expectString = new ArrayList<>(getCollection()).toString();
		String oneStepString = getCollection().toString();
		expectString = expectString.substring(1, expectString.length() - 1); // Trim off the brackets
		oneStepString = oneStepString.substring(1, oneStepString.length() - 1); // Trim off the brackets
		Assert.assertEquals(expectString, oneStepString);
		if (theDef.orderImportant) {
			String multiStepString = getMultiStepCollection().toString();
			multiStepString = multiStepString.substring(1, multiStepString.length() - 1); // Trim off the brackets
			Assert.assertEquals(expectString, multiStepString);
		}

		if (theSupplier != null) {
			// Test contains, containsAll, containsAny
			T testValue = theSupplier.apply(helper);
			int testValsLen = helper.getInt(5, 20); // Change min to 0
			List<T> testValues = new ArrayList<>(testValsLen);
			for (i = 0; i < testValsLen; i++)
				testValues.add(theSupplier.apply(helper));
			boolean expectContains = false;
			for (T v : getCollection()) {
				if (getCollection().equivalence().elementEquals(v, testValue)) {
					expectContains = true;
					break;
				}
			}
			boolean expectContainsAny = false, expectContainsAll = true;
			for (T testV : testValues) {
				boolean found = false;
				for (T v : getCollection()) {
					if (getCollection().equivalence().elementEquals(v, testV)) {
						found = true;
						break;
					}
				}
				if (found)
					expectContainsAny = true;
				else
					expectContainsAll = false;
			}
			Assert.assertEquals(expectContains, getCollection().contains(testValue));
			Assert.assertEquals(expectContains, getMultiStepCollection().contains(testValue));
			Assert.assertEquals(expectContainsAny, getCollection().containsAny(testValues));
			Assert.assertEquals(expectContainsAny, getMultiStepCollection().containsAny(testValues));
			Assert.assertEquals(expectContainsAll, getCollection().containsAll(testValues));
			Assert.assertEquals(expectContainsAll, getMultiStepCollection().containsAll(testValues));
		}
	}
}
