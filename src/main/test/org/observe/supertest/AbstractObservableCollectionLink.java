package org.observe.supertest;

import static org.observe.collect.CollectionChangeType.add;
import static org.observe.collect.CollectionChangeType.remove;
import static org.observe.collect.CollectionChangeType.set;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.junit.Assert;
import org.observe.Observable;
import org.observe.SimpleSettableValue;
import org.observe.collect.CollectionChangeType;
import org.observe.collect.Equivalence;
import org.observe.collect.FlowOptions;
import org.observe.collect.ObservableCollection;
import org.observe.collect.ObservableCollection.CollectionDataFlow;
import org.observe.collect.ObservableCollectionDataFlowImpl;
import org.observe.collect.ObservableCollectionEvent;
import org.observe.collect.ObservableCollectionTester;
import org.observe.collect.ObservableElement;
import org.observe.collect.ObservableElementTester;
import org.observe.collect.ObservableSortedSet;
import org.observe.supertest.MappedCollectionLink.TypeTransformation;
import org.observe.supertest.ObservableChainTester.TestValueType;
import org.qommons.Ternian;
import org.qommons.TestHelper;
import org.qommons.Transaction;
import org.qommons.ValueHolder;
import org.qommons.collect.BetterHashMap;
import org.qommons.collect.BetterList;
import org.qommons.collect.BetterMap;
import org.qommons.collect.BetterSet;
import org.qommons.collect.BetterSortedMap;
import org.qommons.collect.BetterSortedSet.SortedSearchFilter;
import org.qommons.collect.CollectionElement;
import org.qommons.collect.ElementId;
import org.qommons.collect.ElementSpliterator;
import org.qommons.collect.MapEntryHandle;
import org.qommons.collect.MutableCollectionElement;
import org.qommons.tree.BetterTreeList;
import org.qommons.tree.BetterTreeMap;

import com.google.common.reflect.TypeToken;

abstract class AbstractObservableCollectionLink<E, T> implements ObservableCollectionChainLink<E, T> {
	private final ObservableCollectionChainLink<?, E> theParent;
	private final TestValueType theType;
	private final CollectionDataFlow<?, ?, T> theFlow;
	private final ObservableCollection<T> theCollection;
	private final boolean isCheckingRemovedValues;
	private ObservableCollectionTester<T> theTester;
	private ObservableCollectionChainLink<T, ?> theChild;
	private final Function<TestHelper, T> theSupplier;

	private final BetterList<LinkElement> theElements;
	private final BetterSortedMap<ElementId, LinkElement> theLinkElementsById;
	private final Deque<LinkElement> theAddedElements;
	private LinkElement theLastAddedOrModified;

	private final Map<LinkElement, Deque<LinkElement>> theSourceToDest;
	private final BetterMap<LinkElement, LinkElement> theDestToSource;
	private final List<ElementId> theElementsToRemove;
	private LinkElement theLastAddedSource;

	// Extras
	private ObservableElement<T> theMonitoredElement;
	private Supplier<CollectionElement<T>> theCorrectMonitoredElement;
	private ObservableElementTester<T> theMonitoredElementTester;

	private String theExtras;

	AbstractObservableCollectionLink(ObservableCollectionChainLink<?, E> parent, TestValueType type,
		CollectionDataFlow<?, ?, T> flow, TestHelper helper, boolean checkRemovedValues) {
		this(parent, type, flow, helper, checkRemovedValues, false, Ternian.NONE);
	}

	AbstractObservableCollectionLink(ObservableCollectionChainLink<?, E> parent, TestValueType type, CollectionDataFlow<?, ?, T> flow,
		TestHelper helper, boolean checkRemovedValues, boolean rebaseRequired, Ternian allowPassive) {
		theParent = parent;
		theType = type;
		isCheckingRemovedValues = checkRemovedValues;
		theSupplier = (Function<TestHelper, T>) ObservableChainTester.SUPPLIERS.get(type);
		boolean passive;
		if (allowPassive.value != null)
			passive = allowPassive.value;
		else
			passive = flow.supportsPassive() && helper.getBoolean();
		if (passive)
			theCollection = flow.collectPassive();
		else
			theCollection = flow.collectActive(Observable.empty);
		if (!rebaseRequired && helper.getBoolean())
			theFlow = flow;
		else
			theFlow = theCollection.flow();

		theElements = new BetterTreeList<>(false);
		theLinkElementsById = new BetterTreeMap<>(false, ElementId::compareTo);
		theAddedElements = new BetterTreeList<>(false);
		theElementsToRemove = new ArrayList<>();

		theSourceToDest = new HashMap<>();
		theDestToSource = BetterHashMap.build().buildMap();
	}

	@Override
	public void initialize(TestHelper helper) {
		if (theTester != null)
			throw new IllegalStateException("Already initialized");
		theTester = new ObservableCollectionTester<>("Link " + getLinkIndex(), theCollection);
		theTester.checkRemovedValues(isCheckingRemovedValues);
		theTester.getExpected().clear();
		getCollection().onChange(this::change);
		getCollection().spliterator().forEachElement(el -> {
			ElementId el2 = theElements.addElement(null, false).getElementId();
			LinkElement linkEl = new LinkElement(theElements, el2, getCollection(), el.getElementId());
			theElements.mutableElement(el2).set(linkEl);
			theLinkElementsById.put(el.getElementId(), linkEl);
		}, true);

		// Extras
		theExtras = "";
		// TODO This might be better as another link in the chain when value testing is implemented
		TestHelper.RandomAction extraAction = helper.doAction(10, () -> {
			// Usually, do nothing
		}).or(1, () -> { // observeElement
			T value = theSupplier.apply(helper);
			boolean first = helper.getBoolean();
			theExtras = ", equivalent(" + value + ", " + (first ? "first" : "last") + ")";
			theMonitoredElement = theCollection.observeElement(value, first);
			theCorrectMonitoredElement = () -> {
				ValueHolder<CollectionElement<T>> el = new ValueHolder<>();
				ElementSpliterator<T> spliter = theCollection.spliterator(first);
				while (el.get() == null && spliter.forElement(e -> {
					if (theCollection.equivalence().elementEquals(e.get(), value))
						el.accept(e);
				}, first)) {}
				return el.get();
			};
		}).or(1, () -> { // min/max
			Comparator<T> compare = SortedCollectionLink.compare(getTestType(), helper);
			boolean min = helper.getBoolean();
			boolean first = helper.getBoolean();
			theExtras = ", " + (min ? "min" : "max") + "(" + (first ? "first" : "last") + ")";
			theMonitoredElement = min ? theCollection.minBy(compare, () -> null, Ternian.of(first))
				: theCollection.maxBy(compare, () -> null, Ternian.of(first));
			theCorrectMonitoredElement = () -> {
				ValueHolder<CollectionElement<T>> el = new ValueHolder<>();
				ElementSpliterator<T> spliter = theCollection.spliterator();
				while (spliter.forElement(e -> {
					boolean better = el.get() == null;
					if (!better) {
						int comp = compare.compare(e.get(), el.get().get());
						if (comp == 0)
							better = !first;
						else if ((comp < 0) == min)
							better = true;
					}
					if (better) {
						el.accept(e);
						return;
					}
				}, true)) {}
				return el.get();
			};
		}).or(1, () -> { // First/last
			boolean first = helper.getBoolean();
			theExtras = ", " + (first ? "first" : "last");
			theMonitoredElement = theCollection.observeFind(v -> true).at(first).find();
			theCorrectMonitoredElement = () -> theCollection.getTerminalElement(first);
		});
		if (theCollection instanceof ObservableSortedSet) {
			extraAction.or(5, () -> { // observeRelative
				T value = theSupplier.apply(helper);
				SortedSearchFilter filter = SortedSearchFilter.values()[helper.getInt(0, SortedSearchFilter.values().length)];
				theExtras = ", " + filter.getSymbol() + value;
				ObservableSortedSet<T> ss = (ObservableSortedSet<T>) theCollection;
				theMonitoredElement = ss.observeRelative(ss.searchFor(value, 0), filter, () -> null);
				theCorrectMonitoredElement = () -> {
					ValueHolder<CollectionElement<T>> preEl = new ValueHolder<>();
					ValueHolder<CollectionElement<T>> exactEl = new ValueHolder<>();
					ValueHolder<CollectionElement<T>> postEl = new ValueHolder<>();
					ElementSpliterator<T> spliter = theCollection.spliterator();
					while (!(exactEl.isPresent() || postEl.isPresent()) && spliter.forElement(e -> {
						int comp = ss.comparator().compare(e.get(), value);
						if (comp == 0)
							exactEl.accept(e);
						else if (comp > 0)
							postEl.accept(e);
						else
							preEl.accept(e);
					}, true)) {}
					if (exactEl.isPresent()) // Exact always satisfies the filter
						return exactEl.get();
					switch (filter) {
					case Less:
						return preEl.get();
					case PreferLess:
						return preEl.isPresent() ? preEl.get() : postEl.get();
					case OnlyMatch:
						return null;
					case PreferGreater:
						return postEl.isPresent() ? postEl.get() : preEl.get();
					case Greater:
						return postEl.get();
					default:
						throw new IllegalStateException();
					}
				};
			});
		}
		extraAction.execute(null);
		if (theMonitoredElement != null)
			theMonitoredElementTester = new ObservableElementTester<>(theMonitoredElement);
	}

	protected void change(ObservableCollectionEvent<? extends T> evt) {
		switch (evt.getType()) {
		case add:
			MapEntryHandle<ElementId, LinkElement> lebiHandle = theLinkElementsById.putEntry(evt.getElementId(), null, false);
			CollectionElement<ElementId> prev = theLinkElementsById.keySet().getAdjacentElement(lebiHandle.getElementId(), false);
			int addIndex;
			if (prev == null)
				addIndex = 0;
			else
				addIndex = theLinkElementsById.getEntryById(prev.getElementId()).getValue().getIndex() + 1;
			ElementId addedId = theElements.addElement(addIndex, null).getElementId();
			LinkElement element = new LinkElement(theElements, addedId, getCollection(), evt.getElementId());
			theLinkElementsById.mutableEntry(lebiHandle.getElementId()).set(element);
			theElements.mutableElement(addedId).set(element);
			theAddedElements.add(element);
			theLastAddedOrModified = element;
			if (getParent() != null) {
				LinkElement srcEl = getParent().getLastAddedOrModifiedElement();
				if (srcEl != theLastAddedSource) {
					theLastAddedSource = srcEl;
					mapSourceElement(srcEl, element);
				}
			}
			break;
		case remove:
			element = theLinkElementsById.remove(evt.getElementId());
			theElementsToRemove.add(theElements.getElement(element.getIndex()).getElementId());
			break;
		case set:
			theLastAddedOrModified = theLinkElementsById.get(evt.getElementId());
			break;
		}
	}

	@Override
	public ObservableCollectionChainLink<?, E> getParent() {
		return theParent;
	}

	@Override
	public ObservableCollectionChainLink<T, ?> getChild() {
		return theChild;
	}

	@Override
	public ObservableCollection<T> getCollection() {
		return theCollection;
	}

	protected CollectionDataFlow<?, ?, T> getFlow() {
		return theFlow;
	}

	protected Function<TestHelper, T> getSupplier() {
		return theSupplier;
	}

	@Override
	public List<T> getExpected() {
		return theTester.getExpected();
	}

	@Override
	public String printValue() {
		return theCollection.size() + theCollection.toString();
	}

	@Override
	public TestValueType getTestType() {
		return theType;
	}

	@Override
	public TypeToken<T> getType() {
		return (TypeToken<T>) theType.getType();
	}

	@Override
	public BetterList<LinkElement> getElements() {
		return theElements;
	}

	protected LinkElement getSourceElement(LinkElement ours) {
		return theDestToSource.get(ours);
	}

	protected Deque<LinkElement> getDestElements(LinkElement src) {
		return theSourceToDest.get(src);
	}

	protected void mapSourceElement(LinkElement srcEl, LinkElement destEl) {
		theSourceToDest.computeIfAbsent(srcEl, __ -> new LinkedList<>()).add(destEl);
		theDestToSource.put(destEl, srcEl);
	}

	@Override
	public LinkElement getLastAddedOrModifiedElement() {
		return theLastAddedOrModified;
	}

	protected String getExtras() {
		return theExtras;
	}

	@Override
	public Transaction lock() {
		return theCollection.lock(true, null);
	}

	@Override
	public void tryModify(TestHelper helper) {
		int subListStart, subListEnd;
		BetterList<T> modify;
		if (helper.getBoolean(.05)) {
			subListStart = helper.getInt(0, theCollection.size());
			subListEnd = subListStart + helper.getInt(0, theCollection.size() - subListStart);
			modify = theCollection.subList(subListStart, subListEnd);
			if (helper.isReproducing())
				System.out.println("subList(" + subListStart + ", " + subListEnd + ")");
		} else {
			subListStart = 0;
			subListEnd = theCollection.size();
			modify = theCollection;
		}
		TestHelper.RandomAction action=helper.doAction(5, () -> { // More position-less adds than other ops
			CollectionOp<T> op = new CollectionOp<>(null, add, -1, theSupplier.apply(helper));
			if (helper.isReproducing())
				System.out.println(op);
			checkModifiable(Arrays.asList(op), subListStart, subListEnd, helper);
			op = addToCollection(op, modify, subListStart, helper);
			if (op != null)
				postModify(Arrays.asList(op), helper);
		}).or(1, () -> { // Add by index
			CollectionOp<T> op = new CollectionOp<>(null, add, helper.getInt(0, modify.size() + 1), theSupplier.apply(helper));
			if (helper.isReproducing())
				System.out.println(op);
			List<CollectionOp<T>> ops = Arrays.asList(op);
			checkModifiable(ops, subListStart, subListEnd, helper);
			op = addToCollection(op, modify, subListStart, helper);
			if (op != null) {
				ops.set(0, op);
				postModify(ops, helper);
			}
		}).or(1, () -> { // addAll
			int length = (int) helper.getDouble(0, 100, 1000); // Aggressively tend smaller
			int index = helper.getBoolean() ? -1 : helper.getInt(0, modify.size() + 1);
			List<CollectionOp<T>> ops = new ArrayList<>(length);
			for (int i = 0; i < length; i++) {
				CollectionOp<T> op = new CollectionOp<>(null, add, index, theSupplier.apply(helper));
				ops.add(op);
			}
			if (helper.isReproducing()) {
				String msg = "Add all ";
				if (index >= 0) {
					msg += "@" + index;
					if (index > 0)
						msg += ", after " + modify.get(index - 1);
					msg += " ";
				}
				System.out.println(msg + ops.size() + ops.stream().map(op -> op.value).collect(Collectors.toList()));
			}
			checkModifiable(ops, subListStart, subListEnd, helper);
			addAllToCollection(index, ops, modify, subListStart, subListEnd, helper);
			if (!ops.isEmpty())
				fromAbove(ops, helper, false); // The addAllToCollection does the filtering and subListStart addition
		}).or(2, () -> { // Set
			if (modify.isEmpty()) {
				if (helper.isReproducing())
					System.out.println("Set, but empty");
				return;
			}
			CollectionOp<T> op = new CollectionOp<>(null, set, helper.getInt(0, modify.size()), theSupplier.apply(helper));
			if (helper.isReproducing())
				System.out.println("Set @" + op.index + " " + modify.get(op.index) + "->" + op.value);
			checkModifiable(Arrays.asList(op), subListStart, subListEnd, helper);
			setInCollection(op, modify, helper);
			if (op.getMessage() == null) {
				LinkElement linkEl = theElements.get(subListStart + op.index);
				op = new CollectionOp<>(op.type, linkEl, subListStart + op.index, op.value);
				postModify(Arrays.asList(op), helper);
			}
		}).or(1, () -> {// Remove by value
			T value = theSupplier.apply(helper);
			if (helper.isReproducing())
				System.out.println("Remove " + value);
			CollectionOp<T> op = null;
			for (int i = 0; i < modify.size(); i++) {
				if (theCollection.equivalence().elementEquals(modify.get(i), value)) {
					if (helper.isReproducing())
						System.out.println("\t\tIndex " + i);
					op = new CollectionOp<>(null, remove, i, value);
					checkModifiable(Arrays.asList(op), subListStart, subListEnd, helper);
					break;
				}
			}
			op = removeFromCollection(value, op, modify, subListStart, helper);
			if (op != null)
				postModify(Arrays.asList(op), helper);
		}).or(1, () -> {// Remove by index
			if (modify.isEmpty()) {
				if (helper.isReproducing())
					System.out.println("Remove, but empty");
				return;
			}
			int index = helper.getInt(0, modify.size());
			CollectionOp<T> op = new CollectionOp<>(null, remove, index, modify.get(index));
			if (helper.isReproducing())
				System.out.println(op);
			checkModifiable(Arrays.asList(op), subListStart, subListEnd, helper);
			op = removeFromCollection(op, modify, subListStart, helper);
			if (op != null)
				postModify(Arrays.asList(op), helper);
		}).or(1, () -> { // removeAll
			int length = (int) helper.getDouble(0, 250, 1000); // Tend smaller
			List<T> values = new ArrayList<>(length);
			BetterSet<T> valueSet = theCollection.equivalence().createSet();
			for (int i = 0; i < length; i++) {
				T value = theSupplier.apply(helper);
				values.add(value);
				valueSet.add(value);
			}
			if (helper.isReproducing())
				System.out.println("Remove all " + values.size() + values);
			List<CollectionOp<T>> ops = new ArrayList<>(length);
			for (int i = 0; i < modify.size(); i++) {
				T value = modify.get(i);
				if (valueSet.contains(value)) {
					CollectionOp<T> op = new CollectionOp<>(null, remove, i, value);
					ops.add(op);
				}
			}
			checkModifiable(ops, subListStart, subListEnd, helper);
			if (helper.isReproducing())
				System.out.println("\tShould remove " + ops.size() + " " + CollectionOp.print(ops));
			removeAllFromCollection(values, ops, modify, subListStart, helper);
			Collections.reverse(ops); // Indices need to be descending
			postModify(ops, helper);
		}).or(1, () -> { // retainAll
			// Allow for larger, because the smaller the generated collection,
			// the more elements will be removed from the collection
			int length = helper.getInt(0, 5000);
			List<T> values = new ArrayList<>(length);
			Set<T> valueSet = theCollection.equivalence().createSet();
			for (int i = 0; i < length; i++) {
				T value = theSupplier.apply(helper);
				values.add(value);
				valueSet.add(value);
			}
			if (helper.isReproducing())
				System.out.println("Retain all " + values.size() + values);
			List<CollectionOp<T>> ops = new ArrayList<>();
			for (int i = 0; i < modify.size(); i++) {
				T value = modify.get(i);
				if (!valueSet.contains(value)) {
					CollectionOp<T> op = new CollectionOp<>(null, remove, i, value);
					ops.add(op);
				}
			}
			checkModifiable(ops, subListStart, subListEnd, helper);
			if (helper.isReproducing())
				System.out.println("\tShould remove " + ops.size() + " " + CollectionOp.print(ops));
			retainAllInCollection(values, ops, modify, subListStart, helper);
			Collections.reverse(ops); // Indices need to be descending
			postModify(ops, helper);
		}).or(.1, () -> { // clear
			if (helper.isReproducing())
				System.out.println("clear()");
			List<CollectionOp<T>> ops = new ArrayList<>();
			for (int i = 0; i < modify.size(); i++) {
				CollectionOp<T> op = new CollectionOp<>(null, remove, i, modify.get(i));
				ops.add(op);
			}
			checkModifiable(ops, subListStart, subListEnd, helper);
			clearCollection(ops, modify, subListStart, helper);
			Collections.reverse(ops); // Indices need to be descending
			postModify(ops, helper);
		}).or(1, () -> {
			if (helper.isReproducing())
				System.out.println("[" + getLinkIndex() + "]: Check bounds");
			testBounds(helper);
		});
		addExtraActions(action);
		action.execute("Modification");
	}

	protected void addExtraActions(TestHelper.RandomAction action) {}

	protected int getLinkIndex() {
		if (theParent == null)
			return 0;
		ObservableChainLink<?> link = getParent();
		int index = 0;
		while (link != null) {
			index++;
			if (link instanceof AbstractObservableCollectionLink)
				return ((AbstractObservableCollectionLink<?, ?>) link).getLinkIndex() + index;
			link = link.getParent();
		}
		return index;
	}

	private CollectionOp<T> addToCollection(CollectionOp<T> add, BetterList<T> modify, int subListStart, TestHelper helper) {
		int preModSize = modify.size();
		int preSize = theCollection.size();
		if (add.index < 0) {
			if (add.getMessage() != null)
				Assert.assertNotNull(modify.canAdd(add.value));
			else
				Assert.assertNull(modify.canAdd(add.value));
			// Test simple add value
			CollectionElement<T> element;
			boolean first = helper.getBoolean();
			if (helper.isReproducing())
				System.out.println("\t\tfirst=" + first);
			try {
				element = modify.addElement(add.value, first);
			} catch (UnsupportedOperationException | IllegalArgumentException e) {
				if (add.getMessage() == null)
					e.printStackTrace();
				element = null;
			}
			if (element != null) {
				Assert.assertTrue(element.getElementId().isPresent());
				Assert.assertNull(add.getMessage());
				Assert.assertTrue(theCollection.equivalence().elementEquals(add.value, element.get()));
				Assert.assertEquals(preModSize + 1, modify.size());
				Assert.assertEquals(preSize + 1, theCollection.size());
				LinkElement linkEl = theAddedElements.getFirst();
				Assert.assertEquals(subListStart + modify.getElementsBefore(element.getElementId()), linkEl.getIndex());
				return new CollectionOp<>(CollectionChangeType.add, linkEl, linkEl.getIndex(), add.value);
			} else {
				Assert.assertNotNull(add.getMessage());
				Assert.assertEquals(preModSize, modify.size());
				Assert.assertEquals(preSize, theCollection.size());
				return null;
			}
		} else {
			if (modify.isEmpty() || helper.getBoolean()) {
				// Test simple add by index
				try {
					CollectionElement<T> element = modify.addElement(add.index, add.value);
					if (element == null) {
						Assert.assertNotNull(add.getMessage());
						Assert.assertEquals(preModSize, modify.size());
						return null;
					} else {
						Assert.assertNull(add.getMessage());
						Assert.assertTrue(theCollection.equivalence().elementEquals(add.value, element.get()));
					}
					Assert.assertEquals(preModSize + 1, modify.size());
					Assert.assertEquals(preSize + 1, theCollection.size());
					int index = modify.getElementsBefore(element.getElementId());
					Assert.assertTrue(index >= 0 && index <= preModSize);
					LinkElement linkEl = theAddedElements.getFirst();
					Assert.assertEquals(subListStart + index, linkEl.getIndex());
					return new CollectionOp<>(CollectionChangeType.add, linkEl, subListStart + index, add.value);
				} catch (UnsupportedOperationException | IllegalArgumentException e) {
					Assert.assertNotNull(add.getMessage());
					return null;
				}
			} else {
				// Test add by element
				boolean addLeft;
				if (add.index == 0)
					addLeft = true;
				else if (add.index == modify.size())
					addLeft = false;
				else
					addLeft = helper.getBoolean();
				MutableCollectionElement<T> element = modify
					.mutableElement(modify.getElement(addLeft ? add.index : add.index - 1).getElementId());
				if (add.getMessage() != null)
					Assert.assertNotNull(element.canAdd(add.value, addLeft));
				else
					Assert.assertNull(element.canAdd(add.value, addLeft));
				if (add.getMessage() != null) {
					try {
						Assert.assertNull(element.add(add.value, addLeft));
						Assert.assertFalse(add.isError());
					} catch (UnsupportedOperationException | IllegalArgumentException e) {
						// Don't test this.
						// As long as the message's presence correctly predicts the exception, it's ok for the messages to be different.
						// Assert.assertEquals(add.message, e.getMessage());
					}
					Assert.assertEquals(preModSize, modify.size());
					Assert.assertEquals(preSize, theCollection.size());
					return null;
				} else {
					ElementId newElement = element.add(add.value, addLeft);
					Assert.assertNotNull(newElement);
					Assert.assertEquals(preModSize + 1, modify.size());
					Assert.assertEquals(preSize + 1, theCollection.size());
					Assert.assertTrue(theCollection.equivalence().elementEquals(modify.getElement(newElement).get(), add.value));
					int index = modify.getElementsBefore(newElement);
					Assert.assertTrue(index >= 0 && index <= preModSize);
					LinkElement linkEl = theAddedElements.getFirst();
					Assert.assertEquals(subListStart + index, linkEl.getIndex());
					return new CollectionOp<>(CollectionChangeType.add, linkEl, subListStart + index, add.value);
				}
			}
		}
	}

	private CollectionOp<T> removeFromCollection(CollectionOp<T> op, BetterList<T> modify, int subListStart, TestHelper helper) {
		int preModSize = modify.size();
		int preSize = theCollection.size();
		MutableCollectionElement<T> element = modify.mutableElement(modify.getElement(op.index).getElementId());
		if (op.getMessage() != null)
			Assert.assertNotNull(element.canRemove());
		else
			Assert.assertNull(element.canRemove());
		LinkElement linkEl = theElements.get(subListStart + op.index);
		if (helper.getBoolean()) {
			// Test simple remove by index
			try {
				modify.remove(op.index);
				Assert.assertNull(op.getMessage());
				Assert.assertFalse(element.getElementId().isPresent());
				Assert.assertEquals(preModSize - 1, modify.size());
				Assert.assertEquals(preSize - 1, theCollection.size());
			} catch (UnsupportedOperationException e) {
				Assert.assertNotNull(op.getMessage());
				Assert.assertTrue(op.isError());
				Assert.assertTrue(element.getElementId().isPresent());
				Assert.assertEquals(preModSize, modify.size());
				Assert.assertEquals(preSize, theCollection.size());
				return null;
			}
		} else {
			// Test remove by element
			try {
				element.remove();
				Assert.assertNull(op.getMessage());
				Assert.assertEquals(preModSize - 1, modify.size());
				Assert.assertEquals(preSize - 1, theCollection.size());
			} catch (UnsupportedOperationException e) {
				Assert.assertNotNull(op.getMessage());
				Assert.assertTrue(op.isError());
				Assert.assertTrue(element.getElementId().isPresent());
				Assert.assertEquals(preModSize, modify.size());
				Assert.assertEquals(preSize, theCollection.size());
				return null;
			}
		}
		return new CollectionOp<>(op.type, linkEl, subListStart + op.index, op.value);
	}

	private CollectionOp<T> removeFromCollection(T value, CollectionOp<T> op, BetterList<T> modify, int subListStart, TestHelper helper) {
		int preModSize = modify.size();
		int preSize = theCollection.size();
		CollectionElement<T> element = modify.getElement(value, true);
		if (op == null) {
			Assert.assertNotNull(modify.canRemove(value));
			Assert.assertNull(element);
			Assert.assertFalse(modify.remove(value));
			return null;
		} else {
			LinkElement linkEl = theElements.get(subListStart + op.index);
			Assert.assertNotNull(element);
			Assert.assertEquals(op.index, modify.getElementsBefore(element.getElementId()));
			MutableCollectionElement<T> mutableElement = modify.mutableElement(element.getElementId());
			if (op.getMessage() == null)
				Assert.assertNull(mutableElement.canRemove());
			else
				Assert.assertNotNull(mutableElement.canRemove());
			if (helper.getBoolean()) {
				// Simple remove
				boolean removed;
				try {
					removed = modify.remove(value);
					Assert.assertFalse(op.isError());
				} catch (UnsupportedOperationException e) {
					Assert.assertTrue(op.isError());
					removed = false;
				}
				if (removed) {
					Assert.assertNull(op.getMessage());
					if (element != null)
						Assert.assertFalse(element.getElementId().isPresent());
					Assert.assertEquals(preModSize - 1, modify.size());
					Assert.assertEquals(preSize - 1, theCollection.size());
				} else {
					Assert.assertNotNull(op.getMessage());
					if (element != null)
						Assert.assertTrue(element.getElementId().isPresent());
					Assert.assertEquals(preModSize, modify.size());
					Assert.assertEquals(preSize, theCollection.size());
				}
				if (!removed)
					return null;
			} else {
				// Test remove by element
				try {
					mutableElement.remove();
					Assert.assertNull(op.getMessage());
					Assert.assertFalse(element.getElementId().isPresent());
					Assert.assertFalse(mutableElement.getElementId().isPresent());
					Assert.assertEquals(preModSize - 1, modify.size());
					Assert.assertEquals(preSize - 1, theCollection.size());
				} catch (UnsupportedOperationException e) {
					Assert.assertNotNull(op.getMessage());
					Assert.assertTrue(op.isError());
					Assert.assertTrue(element.getElementId().isPresent());
					Assert.assertTrue(mutableElement.getElementId().isPresent());
					Assert.assertEquals(preModSize, modify.size());
					Assert.assertEquals(preSize, theCollection.size());
					return null;
				}
			}
			return new CollectionOp<>(op.type, linkEl, subListStart + op.index, op.value);
		}
	}

	private void setInCollection(CollectionOp<T> op, BetterList<T> modify, TestHelper helper) {
		int preModSize = modify.size();
		int preSize = theCollection.size();
		MutableCollectionElement<T> element = modify.mutableElement(modify.getElement(op.index).getElementId());
		if (element.isEnabled() != null)
			Assert.assertNotNull(op.getMessage());
		else if (element.isAcceptable(op.value) != null)
			Assert.assertNotNull(op.getMessage());
		else
			Assert.assertNull(op.getMessage());
		if (helper.getBoolean()) {
			// Test simple set by index
			try {
				modify.set(op.index, op.value);
				Assert.assertNull(op.getMessage());
			} catch (UnsupportedOperationException | IllegalArgumentException e) {
				Assert.assertNotNull(op.getMessage());
			}
		} else {
			// Test set by element
			try {
				element.set(op.value);
				Assert.assertNull(op.getMessage());
			} catch (UnsupportedOperationException | IllegalArgumentException e) {
				if (op.getMessage() == null) {
					e.printStackTrace();
					Assert.assertTrue("Should not have thrown exception", false);
				}
			}
		}
		Assert.assertTrue(element.getElementId().isPresent());
		Assert.assertEquals(preModSize, modify.size());
		Assert.assertEquals(preSize, theCollection.size());
	}

	private void addAllToCollection(int index, List<CollectionOp<T>> ops, BetterList<T> modify, int subListStart, int subListEnd,
		TestHelper helper) {
		int preModSize = modify.size();
		int preSize = theCollection.size();
		List<T> values = ops.stream().map(op -> op.value).collect(Collectors.toList());
		boolean modified;
		try {
			if (index >= 0)
				modified = modify.addAll(index, values);
			else
				modified = modify.addAll(values);
		} catch (UnsupportedOperationException | IllegalArgumentException e) {
			e.printStackTrace();
			Assert.assertFalse("Should not throw exceptions", true);
			return;
		}
		int added = 0;
		for (int i = 0; i < ops.size(); i++) {
			CollectionOp<T> op = ops.get(i);
			if (op.getMessage() != null) {
				ops.remove(i);
				i--;
				continue;
			}
			if (modify.getElement(op.value, true) == null)
				Assert.assertTrue(i + ": " + op, false);
			if (theCollection.getElement(op.value, true) == null)
				Assert.assertTrue(i + ": " + op, false);
			added++;
		}
		Assert.assertEquals(modified, added > 0);
		Assert.assertEquals(preModSize + added, modify.size());
		Assert.assertEquals(preSize + added, theCollection.size());
		if (!ops.isEmpty()) {
			// Need to replace the indexes in the operations with the index at which the values were added in the collection (or sub-list)
			//Assuming here that the addition order is the same as the value order in the collection
			Iterator<LinkElement> addedElements=theAddedElements.iterator();
			for (int i = 0; i < ops.size(); i++) {
				LinkElement element=addedElements.next();
				int elIndex = element.getIndex();
				Assert.assertEquals(theCollection.get(elIndex), ops.get(i).value);
				ops.set(i, new CollectionOp<>(CollectionChangeType.add, element, elIndex, ops.get(i).value));
			}
			Collections.sort(ops, (o1, o2) -> o1.index - o2.index);
		}
	}

	private void removeAllFromCollection(List<T> values, List<CollectionOp<T>> ops, BetterList<T> modify, int subListStart,
		TestHelper helper) {
		int preModSize = modify.size();
		int preSize = theCollection.size();
		List<LinkElement> linkEls = ops.stream().map(op -> theElements.get(subListStart + op.index)).collect(Collectors.toList());
		boolean modified = modify.removeAll(values);
		int removed = 0;
		for (int i = 0; i < ops.size(); i++) {
			CollectionOp<T> op = ops.get(i);
			if (op.getMessage() != null) {
				ops.remove(i);
				linkEls.remove(i);
				i--;
			} else {
				ops.set(i, new CollectionOp<>(CollectionChangeType.remove, linkEls.get(i), subListStart + op.index, op.value));
				removed++;
				if (theCollection instanceof Set)
					Assert.assertNull(modify.getElement(op.value, true));
			}
		}
		Assert.assertEquals(removed > 0, modified);
		Assert.assertEquals(preModSize - removed, modify.size());
		Assert.assertEquals(preSize - removed, theCollection.size());
	}

	private void retainAllInCollection(Collection<T> values, List<CollectionOp<T>> ops, BetterList<T> modify, int subListStart,
		TestHelper helper) {
		int preModSize = modify.size();
		int preSize = theCollection.size();
		List<LinkElement> linkEls = ops.stream().map(op -> theElements.get(subListStart + op.index)).collect(Collectors.toList());
		boolean modified = modify.retainAll(values);
		int removed = 0;
		for (int i = 0; i < ops.size(); i++) {
			CollectionOp<T> op = ops.get(i);
			if (op.getMessage() != null) {
				ops.remove(i);
				linkEls.remove(i);
				i--;
			} else {
				ops.set(i, new CollectionOp<>(CollectionChangeType.remove, linkEls.get(i), subListStart + op.index, op.value));
				removed++;
				if (theCollection instanceof Set)
					Assert.assertNull(modify.getElement(op.value, true));
			}
		}
		Assert.assertEquals(removed > 0, modified);
		Assert.assertEquals(preModSize - removed, modify.size());
		Assert.assertEquals(preSize - removed, theCollection.size());
	}

	private void clearCollection(List<CollectionOp<T>> ops, BetterList<T> modify, int subListStart, TestHelper helper) {
		int preModSize = modify.size();
		int preSize = theCollection.size();
		List<LinkElement> linkEls = ops.stream().map(op -> theElements.get(subListStart + op.index)).collect(Collectors.toList());
		modify.clear();
		int removed = 0;
		for (int i = 0; i < ops.size(); i++) {
			CollectionOp<T> op = ops.get(i);
			if (op.getMessage() != null) {
				ops.remove(i);
				linkEls.remove(i);
				i--;
			} else {
				ops.set(i, new CollectionOp<>(CollectionChangeType.remove, linkEls.get(i), subListStart + op.index, op.value));
				removed++;
			}
		}
		Assert.assertEquals(preModSize - removed, modify.size());
		Assert.assertEquals(preSize - removed, theCollection.size());
	}

	private void postModify(List<CollectionOp<T>> ops, TestHelper helper) {
		fromAbove(ops, helper, false);
	}

	private void testBounds(TestHelper helper) {
		try {
			theCollection.get(-1);
			Assert.assertFalse("Should have errored", true);
		} catch (IndexOutOfBoundsException e) {}
		try {
			theCollection.get(theCollection.size());
			Assert.assertFalse("Should have errored", true);
		} catch (IndexOutOfBoundsException e) {}
		try {
			theCollection.remove(-1);
			Assert.assertFalse("Should have errored", true);
		} catch (IndexOutOfBoundsException e) {}
		try {
			theCollection.remove(theCollection.size());
			Assert.assertFalse("Should have errored", true);
		} catch (IndexOutOfBoundsException e) {}
		try {
			theCollection.add(-1, theSupplier.apply(helper));
			Assert.assertFalse("Should have errored", true);
		} catch (IndexOutOfBoundsException | IllegalArgumentException e) { // We'll allow either exception
		}
		try {
			theCollection.add(theCollection.size() + 1, theSupplier.apply(helper));
			Assert.assertFalse("Should have errored", true);
		} catch (IndexOutOfBoundsException | IllegalArgumentException e) { // We'll allow either exception
		}
		try {
			theCollection.set(-1, theSupplier.apply(helper));
			Assert.assertFalse("Should have errored", true);
		} catch (IndexOutOfBoundsException | IllegalArgumentException e) { // We'll allow either exception
		}
		try {
			theCollection.set(theCollection.size() + 1, theSupplier.apply(helper));
			Assert.assertFalse("Should have errored", true);
		} catch (IndexOutOfBoundsException | IllegalArgumentException e) { // We'll allow either exception
		}
	}

	protected void modified(List<CollectionOp<T>> ops, TestHelper helper, boolean propagateUp) {
		for (int i = 0; i < ops.size(); i++) {
			CollectionOp<T> op = ops.get(i);
			switch (op.type) {
			case add:
				theTester.add(op.index, op.value);
				break;
			case remove:
				theTester.getExpected().remove(op.index);
				break;
			case set:
				theTester.getExpected().set(op.index, op.value);
				break;
			}
		}
		if (propagateUp && theChild != null)
			theChild.fromBelow(ops, helper);
	}

	@Override
	public void check(boolean transComplete) {
		// Clean up synthetic element mappings
		theAddedElements.clear();

		for (ElementId el : theElementsToRemove) {
			LinkElement linkEl = theElements.getElement(el).get();
			theElements.mutableElement(el).remove();
			LinkElement srcEl = theDestToSource.remove(linkEl);
			if (srcEl != null) {
				Deque<LinkElement> std = theSourceToDest.get(srcEl);
				std.remove(el);
				if (std.isEmpty())
					theSourceToDest.remove(srcEl);
			}
		}
		// Remove mappings for source elements that are no longer present
		Iterator<Map.Entry<LinkElement, Deque<LinkElement>>> srcMaps = theSourceToDest.entrySet().iterator();
		while (srcMaps.hasNext()) {
			Map.Entry<LinkElement, Deque<LinkElement>> entry = srcMaps.next();
			if (entry.getKey().isPresent())
				continue;
			for (LinkElement destEl : entry.getValue()) {
				MapEntryHandle<LinkElement, LinkElement> destMap = theDestToSource.getEntry(destEl);
				if (destMap != null && destMap.getValue().equals(entry.getKey()))
					theDestToSource.mutableEntry(destMap.getElementId()).remove();
			}
			srcMaps.remove();
		}
		theElementsToRemove.clear();
		// if (getLinkIndex() == 7)
		// BreakpointHere.breakpoint();

		if (transComplete)
			theTester.check();
		else
			theTester.checkNonBatchSynced();

		if (transComplete && theMonitoredElement != null) {
			CollectionElement<T> correct = theCorrectMonitoredElement.get();
			theMonitoredElementTester.check(correct == null ? null : correct.getElementId(), correct == null ? null : correct.get());
		}
	}

	@Override
	public <X> ObservableChainLink<X> derive(TestHelper helper) {
		ValueHolder<ObservableChainLink<X>> derived = new ValueHolder<>();
		TestHelper.RandomAction action=helper//
			.doAction(1, () -> { // map
				ObservableChainTester.TestValueType nextType = ObservableChainTester.nextType(helper);
				SimpleSettableValue<TypeTransformation<T, X>> txValue = new SimpleSettableValue<>(
					(TypeToken<TypeTransformation<T, X>>) (TypeToken<?>) new TypeToken<Object>() {}, false);
				txValue.set(MappedCollectionLink.transform(theType, nextType, helper), null);
				boolean variableMap = helper.getBoolean();
				CollectionDataFlow<?, ?, T> flow = theFlow;
				if (variableMap)
					flow = flow.refresh(txValue.changes().noInit()); // The refresh has to be UNDER the map
				boolean needsUpdateReeval = !isCheckingRemovedValues || variableMap;
				ValueHolder<FlowOptions.MapOptions<T, X>> options = new ValueHolder<>();
				CollectionDataFlow<?, ?, X> derivedFlow = flow.map((TypeToken<X>) nextType.getType(), src -> txValue.get().map(src), o -> {
					o.manyToOne(txValue.get().isManyToOne());
					if (helper.getBoolean(.95))
						o.withReverse(x -> txValue.get().reverse(x));
					options.accept(o.cache(helper.getBoolean()).fireIfUnchanged(needsUpdateReeval || helper.getBoolean())
						.reEvalOnUpdate(needsUpdateReeval || helper.getBoolean()));
				});
				setChild(new MappedCollectionLink<>(this, nextType, derivedFlow, helper, !needsUpdateReeval, txValue, variableMap,
					new FlowOptions.MapDef<>(options.get())));
				derived.accept((ObservableChainLink<X>) theChild);
				// TODO mapEquivalent
			})//
			.or(1, () -> { // reverse
				CollectionDataFlow<?, ?, T> derivedFlow;
				if (helper.getBoolean())
					derivedFlow = theFlow.reverse();
				else
					derivedFlow = theCollection.reverse().flow();
				setChild(new ReversedCollectionLink<>(this, theType, derivedFlow, helper, isCheckingRemovedValues));
				derived.accept((ObservableChainLink<X>) theChild);
			})//
			// TODO size
			// TODO contains
			// TODO containsAny
			// TODO containsAll
			// TODO only
			// TODO reduce
			// TODO flow reverse
			.or(1, () -> { // filter/refresh
				// Getting a java.lang.InternalError: Enclosing method not found when I try to do the TypeToken right.
				// It doesn't matter here anyway
				SimpleSettableValue<Function<T, String>> filterValue = new SimpleSettableValue<>(
					(TypeToken<Function<T, String>>) (TypeToken<?>) new TypeToken<Object>() {}, false);
				filterValue.set(FilteredCollectionLink.filterFor(theType, helper), null);
				boolean variableFilter = helper.getBoolean();
				CollectionDataFlow<?, ?, T> flow = theFlow;
				if (variableFilter)
					flow = flow.refresh(filterValue.changes().noInit()); // The refresh has to be UNDER the filter
				CollectionDataFlow<?, ?, T> derivedFlow = flow.filter(v -> filterValue.get().apply(v));
				setChild(new FilteredCollectionLink<>(this, theType, derivedFlow, helper, isCheckingRemovedValues, filterValue,
					variableFilter));
				derived.accept((ObservableChainLink<X>) theChild);
			})//
			// TODO whereContained
			// TODO refreshEach
			// TODO combine
			// TODO flattenValues
			// TODO flatMap
			.or(1, () -> { // sorted
				Comparator<T> compare = SortedCollectionLink.compare(theType, helper);
				CollectionDataFlow<?, ?, T> derivedFlow = theFlow.sorted(compare);
				setChild(new SortedCollectionLink<>(this, theType, derivedFlow, helper, isCheckingRemovedValues, compare));
				derived.accept((ObservableChainLink<X>) theChild);
			})//
			.or(1, () -> { // distinct
				derived.accept((ObservableChainLink<X>) deriveDistinct(helper, false));
			})//
			.or(1, () -> { // distinct sorted
				derived.accept((ObservableChainLink<X>) deriveDistinctSorted(helper, false));
			});//
		if(theCollection instanceof ObservableSortedSet){
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
				setChild(new SubSetLink<>(this, theType, (ObservableCollection.DistinctSortedDataFlow<?, ?, T>) derivedFlow, helper, true,
					min, includeMin, max, includeMax));
				derived.accept((ObservableChainLink<X>) theChild);
			});
		}
		action.or(1, () -> {// filterMod
			ValueHolder<ObservableCollection.ModFilterBuilder<T>> filter = new ValueHolder<>();
			CollectionDataFlow<?, ?, T> derivedFlow = theFlow.filterMod(f -> {
				if (helper.getBoolean(.1))
					f.unmodifiable("Unmodifiable", helper.getBoolean(.75));
				else {
					if (helper.getBoolean(.25))
						f.noAdd("No adds");
					else if (helper.getBoolean(.15))
						f.filterAdd(FilteredCollectionLink.filterFor(theType, helper));
					if (helper.getBoolean(.25))
						f.noRemove("No removes");
					else if (helper.getBoolean(.15))
						f.filterRemove(FilteredCollectionLink.filterFor(theType, helper));
				}
				filter.accept(f);
			});
			setChild(new ModFilteredCollectionLink<>(this, theType, derivedFlow, helper,
				new ObservableCollectionDataFlowImpl.ModFilterer<>(filter.get()), isCheckingRemovedValues));
			derived.accept((ObservableChainLink<X>) theChild);
		})//
		// TODO groupBy
		// TODO groupBy(Sorted)
		;
		action.execute(null);
		return derived.get();
	}

	protected ObservableCollectionChainLink<T, T> deriveDistinct(TestHelper helper, boolean asRoot) {
		ValueHolder<FlowOptions.UniqueOptions> options = new ValueHolder<>();
		CollectionDataFlow<?, ?, T> flow = theFlow;
		// distinct() is a no-op for a distinct flow, so unless we change the equivalence, this is pointless
		// plus, hash distinct() can affect ordering, so this could cause failures
		if (flow instanceof ObservableCollection.DistinctDataFlow || helper.getBoolean()) {
			Comparator<T> compare = SortedCollectionLink.compare(theType, helper);
			flow = flow.withEquivalence(Equivalence.of((Class<T>) getType().getRawType(), compare, false));
		}
		CollectionDataFlow<?, ?, T> derivedFlow = flow.distinct(opts -> {
			// opts.useFirst(helper.getBoolean()).preserveSourceOrder(opts.canPreserveSourceOrder() && helper.getBoolean()); TODO
			options.accept(opts);
		});
		setChild(new DistinctCollectionLink<>(this, theType, derivedFlow, helper, isCheckingRemovedValues, options.get(), asRoot));
		return (ObservableCollectionChainLink<T, T>) theChild;
	}

	protected ObservableCollectionChainLink<T, T> deriveDistinctSorted(TestHelper helper, boolean asRoot) {
		FlowOptions.UniqueOptions options = new FlowOptions.SimpleUniqueOptions(true);
		CollectionDataFlow<?, ?, T> flow = theFlow;
		Comparator<T> compare = SortedCollectionLink.compare(theType, helper);
		options.useFirst(/*TODO helper.getBoolean()*/ false);
		CollectionDataFlow<?, ?, T> derivedFlow = flow.distinctSorted(compare, options.isUseFirst());
		setChild(new DistinctCollectionLink<>(this, theType, derivedFlow, helper, isCheckingRemovedValues, options, asRoot));
		return (ObservableCollectionChainLink<T, T>) theChild;
	}

	protected void setChild(ObservableCollectionChainLink<T, ?> child) {
		theChild = child;
	}
}