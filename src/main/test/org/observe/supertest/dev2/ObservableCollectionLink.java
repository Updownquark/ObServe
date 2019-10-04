package org.observe.supertest.dev2;

import static org.observe.collect.CollectionChangeType.add;
import static org.observe.collect.CollectionChangeType.remove;
import static org.observe.collect.CollectionChangeType.set;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
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
import org.observe.collect.ObservableSortedSet;
import org.observe.supertest.dev.FlattenedValuesLink;
import org.observe.supertest.dev2.links.MappedCollectionLink;
import org.observe.supertest.dev2.links.ReversedCollectionLink;
import org.qommons.ArrayUtils;
import org.qommons.TestHelper;
import org.qommons.ValueHolder;
import org.qommons.collect.BetterCollections;
import org.qommons.collect.BetterList;
import org.qommons.collect.BetterSet;
import org.qommons.collect.BetterSortedSet.SortedSearchFilter;
import org.qommons.collect.CollectionElement;
import org.qommons.collect.ElementId;
import org.qommons.collect.MutableCollectionElement;
import org.qommons.tree.BetterTreeList;
import org.qommons.tree.BinaryTreeNode;

import com.google.common.reflect.TypeToken;

public abstract class ObservableCollectionLink<S, T> {
	public interface OperationRejection {
		boolean isRejected();

		void reject(String message, boolean error);
	}

	private final ObservableCollectionTestDef<T> theDef;
	private final ObservableCollection<T> theCollection;
	protected final ObservableCollectionLink<?, S> theSourceLink;
	private ObservableCollectionLink<T, ?> theDerivedLink;
	private final Function<TestHelper, T> theSupplier;

	private final BetterTreeList<CollectionLinkElement<S, T>> theElements;
	protected final BetterTreeList<CollectionLinkElement<S, T>> theExpectedElements;

	public ObservableCollectionLink(ObservableCollectionLink<?, S> sourceLink, ObservableCollectionTestDef<T> def, TestHelper helper) {
		theDef = def;
		theSourceLink = sourceLink;
		theSupplier = (Function<TestHelper, T>) ObservableChainTester.SUPPLIERS.get(def.type);
		theElements = new BetterTreeList<>(false);
		theExpectedElements = new BetterTreeList<>(false);
		boolean passive;
		if (def.allowPassive.value != null)
			passive = def.allowPassive.value;
		else
			passive = def.flow.supportsPassive() && helper.getBoolean();
		if (passive)
			theCollection = def.flow.collectPassive();
		else
			theCollection = def.flow.collectActive(Observable.empty);

		// Listen to the collection to populate and maintain theElements
		getCollection().subscribe(new Consumer<ObservableCollectionEvent<? extends T>>() {
			@Override
			public void accept(ObservableCollectionEvent<? extends T> evt) {
				switch (evt.getType()) {
				case add:
					addElement(evt.getElementId(), evt.getNewValue());
					break;
				case remove:
					removeElement(evt.getElementId(), evt.getOldValue());
					break;
				case set:
					changeElement(evt.getElementId(), evt.getOldValue(), evt.getNewValue());
					break;
				}
			}

			private void addElement(ElementId id, T newValue) {
				BinaryTreeNode<CollectionLinkElement<S, T>> adjacent = theElements.search(node -> id.compareTo(node.getCollectionAddress()),
					SortedSearchFilter.PreferLess);
				BinaryTreeNode<CollectionLinkElement<S, T>> newNode;
				if (adjacent == null)
					newNode = theElements.addElement(null, false);
				else {
					int comp = adjacent.get().getCollectionAddress().compareTo(id);
					if (comp == 0)
						throw new IllegalStateException("Accounting error--element " + id + " already exists. Existing value "
							+ adjacent.get().get() + " compared to " + newValue);
					if (comp < 0)
						newNode = theElements.addElement(null, adjacent.getElementId(), null, true);
					else
						newNode = theElements.addElement(null, null, adjacent.getElementId(), false);
				}
				theElements.mutableElement(newNode.getElementId()).set(//
					new CollectionLinkElement<>(ObservableCollectionLink.this, newNode.getElementId(), newValue).setCollectionAddress(id));
			}

			private void removeElement(ElementId id, T oldValue) {
				BinaryTreeNode<CollectionLinkElement<S, T>> found = theElements.search(node -> id.compareTo(node.getCollectionAddress()),
					SortedSearchFilter.OnlyMatch);
				if (found == null)
					throw new IllegalStateException("Accounting error--missing element " + id + " (" + oldValue + ")");
				theElements.mutableElement(found.getElementId()).remove();
			}

			private void changeElement(ElementId id, T oldValue, T newValue) {
				BinaryTreeNode<CollectionLinkElement<S, T>> found = theElements.search(node -> id.compareTo(node.getCollectionAddress()),
					SortedSearchFilter.OnlyMatch);
				if (found == null)
					throw new IllegalStateException(
						"Accounting error--missing element " + id + " (" + oldValue + " for update to " + newValue + ")");
			}
		}, true);
	}

	protected abstract void initialize(TestHelper helper);

	public abstract List<ExpectedCollectionOperation<S, T>> expectFromSource(ExpectedCollectionOperation<?, S> sourceOp);

	public abstract void expect(ExpectedCollectionOperation<?, T> derivedOp, OperationRejection rejection);

	public abstract CollectionLinkElement<S, T> expectAdd(T value, CollectionLinkElement<?, T> after, CollectionLinkElement<?, T> before,
		boolean first, OperationRejection rejection);

	public TestValueType getType() {
		return theDef.type;
	}

	public ObservableCollectionLink<T, ?> getDerivedLink() {
		return theDerivedLink;
	}

	public ObservableCollection<T> getCollection() {
		return theCollection;
	}

	public BetterList<CollectionLinkElement<S, T>> getElements() {
		return BetterCollections.unmodifiableList(theElements);
	}

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
		TestHelper.RandomAction action = helper.doAction(5, () -> { // More position-less adds than other ops
			CollectionOp<T> op = new CollectionOp<>(add, theSupplier.apply(helper), -1, -1, helper.getBoolean());
			if (helper.isReproducing())
				System.out.println(op);
			expectModification(Arrays.asList(op), subListStart, subListEnd, helper);
			addToCollection(op, modify, helper);
		}).or(1, () -> { // Add in position range
			int minIndex = (int) helper.getDouble(0, modify.size(), modify.size() / 3);
			int maxIndex = helper.getInt(minIndex, modify.size());
			CollectionOp<T> op = new CollectionOp<>(add, theSupplier.apply(helper), minIndex, maxIndex, helper.getBoolean());
			if (helper.isReproducing())
				System.out.println(op);
			List<CollectionOp<T>> ops = Arrays.asList(op);
			expectModification(ops, subListStart, subListEnd, helper);
			addToCollection(op, modify, helper);
		}).or(1, () -> { // addAll
			int length = (int) helper.getDouble(0, 100, 1000); // Aggressively tend smaller
			int minIndex = (int) helper.getDouble(0, modify.size(), modify.size() / 3);
			int maxIndex = helper.getInt(minIndex, modify.size());
			boolean first = helper.getBoolean();
			List<CollectionOp<T>> ops = new ArrayList<>(length);
			for (int i = 0; i < length; i++) {
				CollectionOp<T> op = new CollectionOp<>(add, theSupplier.apply(helper), minIndex, maxIndex, first);
				ops.add(op);
			}
			if (helper.isReproducing()) {
				String msg = "Add all ";
				if (minIndex >= 0) {
					msg += "@" + minIndex + "-" + maxIndex;
					if (minIndex > 0) {
						if (maxIndex < modify.size()) {
							msg += ", between " + modify.get(minIndex - 1) + " and " + modify.get(maxIndex);
						} else
							msg += ", after " + modify.get(minIndex - 1);
					} else if (maxIndex < modify.size())
						msg += ", before " + modify.get(maxIndex);
					msg += " ";
				}
				System.out.println(msg + ops.size() + ops.stream().map(op -> op.value).collect(Collectors.toList()));
			}
			expectModification(ops, subListStart, subListEnd, helper);
			addAllToCollection(minIndex, maxIndex, first, ops, modify, subListStart, subListEnd, helper);
		}).or(2, () -> { // Set
			if (modify.isEmpty()) {
				if (helper.isReproducing())
					System.out.println("Set, but empty");
				return;
			}
			CollectionOp<T> op = new CollectionOp<>(set, theSupplier.apply(helper), helper.getInt(0, modify.size()));
			if (helper.isReproducing())
				System.out.println("Set @" + op.ndex + " " + modify.get(op.ndex) + "->" + op.value);
			expectModification(Arrays.asList(op), subListStart, subListEnd, helper);
			setInCollection(op, modify, helper);
			if (op.getMessage() == null)
				postModify(Arrays.asList(op), subListStart, helper);
		}).or(1, () -> {// Remove by value
			T value = theSupplier.apply(helper);
			if (helper.isReproducing())
				System.out.println("Remove " + value);
			CollectionOp<T> op = null;
			for (int i = 0; i < modify.size(); i++) {
				if (theCollection.equivalence().elementEquals(modify.get(i), value)) {
					if (helper.isReproducing())
						System.out.println("\t\tIndex " + i);
					op = new CollectionOp<>(remove, value, i);
					expectModification(Arrays.asList(op), subListStart, subListEnd, helper);
					break;
				}
			}
			removeFromCollection(value, op, modify, helper);
			if (op != null && op.getMessage() == null)
				postModify(Arrays.asList(op), subListStart, helper);
		}).or(1, () -> {// Remove by index
			if (modify.isEmpty()) {
				if (helper.isReproducing())
					System.out.println("Remove, but empty");
				return;
			}
			int index = helper.getInt(0, modify.size());
			CollectionOp<T> op = new CollectionOp<>(remove, modify.get(index), index);
			if (helper.isReproducing())
				System.out.println(op);
			expectModification(Arrays.asList(op), subListStart, subListEnd, helper);
			removeFromCollection(op, modify, helper);
			if (op.getMessage() == null)
				postModify(Arrays.asList(op), subListStart, helper);
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
					CollectionOp<T> op = new CollectionOp<>(remove, value, i);
					ops.add(op);
				}
			}
			expectModification(ops, subListStart, subListEnd, helper);
			if (helper.isReproducing())
				System.out.println("\tShould remove " + ops.size() + " " + CollectionOp.print(ops));
			removeAllFromCollection(values, ops, modify, helper);
			Collections.reverse(ops); // Indices need to be descending
			postModify(ops, subListStart, helper);
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
					CollectionOp<T> op = new CollectionOp<>(remove, value, i);
					ops.add(op);
				}
			}
			expectModification(ops, subListStart, subListEnd, helper);
			if (helper.isReproducing())
				System.out.println("\tShould remove " + ops.size() + " " + CollectionOp.print(ops));
			retainAllInCollection(values, ops, modify, helper);
			Collections.reverse(ops); // Indices need to be descending
			postModify(ops, subListStart, helper);
		}).or(.1, () -> { // clear
			if (helper.isReproducing())
				System.out.println("clear()");
			List<CollectionOp<T>> ops = new ArrayList<>();
			for (int i = 0; i < modify.size(); i++) {
				CollectionOp<T> op = new CollectionOp<>(remove, modify.get(i), i);
				ops.add(op);
			}
			expectModification(ops, subListStart, subListEnd, helper);
			clearCollection(ops, modify, helper);
			Collections.reverse(ops); // Indices need to be descending
			postModify(ops, subListStart, helper);
		}).or(1, () -> {
			if (helper.isReproducing())
				System.out.println("[" + getLinkIndex() + "]: Check bounds");
			testBounds(helper);
		});
		addExtraActions(action);
		action.execute("Modification");
	}

	public void validate(boolean transactionEnd) throws AssertionError {
		StringBuilder error = new StringBuilder();

		ArrayUtils.adjust(theElements, theExpectedElements,
			new ArrayUtils.DifferenceListener<CollectionLinkElement<S, T>, CollectionLinkElement<S, T>>() {
			@Override
			public boolean identity(CollectionLinkElement<S, T> o1, CollectionLinkElement<S, T> o2) {
				if (o1 == o2)
					return true;
				else if (o1.getExpectedAddress() != null && o1.getExpectedAddress().isPresent())
					return false;
				else if (o2.getElementAddress() != null && o2.getElementAddress().isPresent())
					return false;
				else
					return getCollection().equivalence().elementEquals(o1.get(), o2.get());
			}

			@Override
			public CollectionLinkElement<S, T> added(CollectionLinkElement<S, T> o, int mIdx, int retIdx) {
				if (o.getElementAddress() != null)
					error.append("Unexpected removal of ").append(o).append('\n');
				else
					error.append("Expected addition of ").append(o).append('\n');
				return null;// No change
			}

			@Override
			public CollectionLinkElement<S, T> removed(CollectionLinkElement<S, T> o, int oIdx, int incMod, int retIdx) {
				if (o.getElementAddress() != null)
					error.append("Expected removal of ").append(o).append('\n');
				else
					error.append("Unexpected addition of ").append(o).append('\n');
				return o; // No change
			}

			@Override
			public CollectionLinkElement<S, T> set(CollectionLinkElement<S, T> o1, int idx1, int incMod, CollectionLinkElement<S, T> o2,
				int idx2, int retIdx) {
				if (theDef.orderImportant && incMod != retIdx)
					error.append(o2.get()).append("Expected at index ").append(idx2).append(", but found at ").append(idx1)
					.append('\n');
				o2.validateAgainst(o1, error);
				return o2; // Replace with the expected element
			}
		});

		if (error.length() > 0)
			throw new AssertionError(error.toString());

		for (CollectionLinkElement<S, T> el : theElements) {
			el.fix(//
				theSourceLink != null && !theSourceLink.theDef.orderImportant, //
				theDerivedLink != null && !theDerivedLink.theDef.orderImportant);
		}
		// TODO Validate source and derived elements
	}

	public String printValue() {
		return getCollection().size() + getCollection().toString();
	}

	public <X> ObservableCollectionLink<T, X> derive(TestHelper helper) {
		ValueHolder<ObservableCollectionLink<T, X>> derived = new ValueHolder<>();
		TestHelper.RandomAction action = helper//
			.doAction(1, () -> { // map
				TestValueType nextType = TestValueType.nextType(helper);
				SimpleSettableValue<TypeTransformation<T, X>> txValue = new SimpleSettableValue<>(
					(TypeToken<TypeTransformation<T, X>>) (TypeToken<?>) new TypeToken<Object>() {}, false);
				txValue.set(MappedCollectionLink.transform(theDef.type, nextType, helper), null);
				boolean variableMap = helper.getBoolean();
				CollectionDataFlow<?, ?, T> flow = theDef.flow;
				if (variableMap)
					flow = flow.refresh(txValue.changes().noInit()); // The refresh has to be UNDER the map
				boolean needsUpdateReeval = !theDef.checkOldValues || variableMap;
				ValueHolder<FlowOptions.MapOptions<T, X>> options = new ValueHolder<>();
				CollectionDataFlow<?, ?, X> derivedFlow = flow.map((TypeToken<X>) nextType.getType(), src -> txValue.get().map(src), o -> {
					o.manyToOne(txValue.get().isManyToOne());
					if (helper.getBoolean(.95))
						o.withReverse(x -> txValue.get().reverse(x));
					options.accept(o.cache(helper.getBoolean()).fireIfUnchanged(needsUpdateReeval || helper.getBoolean())
						.reEvalOnUpdate(needsUpdateReeval || helper.getBoolean()));
				});
				theDerivedLink = new MappedCollectionLink<>(this,
					new ObservableCollectionTestDef<>(nextType, derivedFlow, variableMap, !needsUpdateReeval), helper, txValue, variableMap,
					new FlowOptions.MapDef<>(options.get()));
				derived.accept((ObservableCollectionLink<T, X>) theDerivedLink);
				// TODO mapEquivalent
			})//
			.or(1, () -> { // reverse
				CollectionDataFlow<?, ?, T> derivedFlow;
				if (helper.getBoolean())
					derivedFlow = theDef.flow.reverse();
				else
					derivedFlow = theCollection.reverse().flow();
				theDerivedLink = new ReversedCollectionLink<>(this,
					new ObservableCollectionTestDef<>(theDef.type, derivedFlow, true, theDef.checkOldValues), helper);
				derived.accept((ObservableCollectionLink<T, X>) theDerivedLink);
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
				filterValue.set(FilteredCollectionLink.filterFor(theDef.type, helper), null);
				boolean variableFilter = helper.getBoolean();
				CollectionDataFlow<?, ?, T> flow = theDef.flow;
				if (variableFilter)
					flow = flow.refresh(filterValue.changes().noInit()); // The refresh has to be UNDER the filter
				CollectionDataFlow<?, ?, T> derivedFlow = flow.filter(v -> filterValue.get().apply(v));
				theDerivedLink = new FilteredCollectionLink<>(this, theType, derivedFlow, helper, isCheckingRemovedValues, filterValue,
					variableFilter);
				derived.accept((ObservableCollectionLink<T, X>) theDerivedLink);
			})//
			// TODO whereContained
			// TODO refreshEach
			// TODO combine
			.or(1, () -> { // flattenValues
				theDerivedLink = FlattenedValuesLink.createFlattenedValuesLink(this, theDef.flow, helper);
				derived.accept((ObservableCollectionLink<T, X>) theDerivedLink);
			})//
			// TODO flatMap
			.or(1, () -> { // sorted
				Comparator<T> compare = SortedCollectionLink.compare(theType, helper);
				CollectionDataFlow<?, ?, T> derivedFlow = theFlow.sorted(compare);
				theDerivedLink = new SortedCollectionLink<>(this, theType, derivedFlow, helper, isCheckingRemovedValues, compare);
				derived.accept((ObservableCollectionLink<T, X>) theDerivedLink);
			})//
			.or(1, () -> { // distinct
				derived.accept((ObservableCollectionLink<T, X>) deriveDistinct(helper, false));
			})//
			.or(1, () -> { // distinct sorted
				derived.accept((ObservableCollectionLink<T, X>) deriveDistinctSorted(helper, false));
			});//
		if (theCollection instanceof ObservableSortedSet) {
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
		}
		action.or(1, () -> {// filterMod
			ValueHolder<ObservableCollection.ModFilterBuilder<T>> filter = new ValueHolder<>();
			CollectionDataFlow<?, ?, T> derivedFlow = theDef.flow.filterMod(f -> {
				if (helper.getBoolean(.1))
					f.unmodifiable("Unmodifiable", helper.getBoolean(.75));
				else {
					if (helper.getBoolean(.25))
						f.noAdd("No adds");
					else if (helper.getBoolean(.15))
						f.filterAdd(FilteredCollectionLink.filterFor(theDef.type, helper));
					if (helper.getBoolean(.25))
						f.noRemove("No removes");
					else if (helper.getBoolean(.15))
						f.filterRemove(FilteredCollectionLink.filterFor(theDef.type, helper));
				}
				filter.accept(f);
			});
			theDerivedLink = new ModFilteredCollectionLink<>(this, theDef.type, derivedFlow, helper,
				new ObservableCollectionDataFlowImpl.ModFilterer<>(filter.get()), theDef.checkOldValues);
			derived.accept((ObservableCollectionLink<T, X>) theDerivedLink);
		})//
		// TODO groupBy
		// TODO groupBy(Sorted)
		;
		action.execute(null);
		return derived.get();
	}

	protected ObservableCollectionLink<T, T> deriveDistinct(TestHelper helper, boolean asRoot) {
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
		options.useFirst(/*TODO helper.getBoolean()*/ false);
		CollectionDataFlow<?, ?, T> derivedFlow = flow.distinctSorted(compare, options.isUseFirst());
		theDerivedLink = new DistinctCollectionLink<>(this, theType, derivedFlow, theFlow, helper, isCheckingRemovedValues, options,
			asRoot);
		return (ObservableCollectionLink<T, T>) theDerivedLink;
	}

	private static class CollectionOp<E> implements OperationRejection {
		final CollectionChangeType type;
		final E value;
		final int minIndex;
		final int maxIndex;
		final boolean towardBeginning;

		private String theMessage;
		private boolean isError;

		CollectionOp(CollectionChangeType type, E source, int minIndex, int maxIndex, boolean towardBegin) {
			this.type = type;
			this.value = source;
			this.minIndex = minIndex;
			this.maxIndex = maxIndex;
			this.towardBeginning = towardBegin;
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
			StringBuilder str = new StringBuilder(type.name()).append(' ');
			if (value != null)
				str.append(value);
			if (minIndex >= 0)
				str.append('@').append(minIndex).append('-').append(maxIndex);
			return str.toString();
		}

		public static boolean isSameIndex(List<? extends CollectionOp<?>> ops) {
			int minIdx = ops.get(0).minIndex;
			int maxIdx = ops.get(0).maxIndex;
			for (int i = 1; i < ops.size(); i++)
				if (ops.get(i).minIndex != minIdx)
					return false;
			return true;
		}

		public static boolean isSameType(List<? extends CollectionOp<?>> ops) {
			CollectionChangeType type = ops.get(0).type;
			for (int i = 1; i < ops.size(); i++)
				if (ops.get(i).type != type)
					return false;
			return true;
		}

		public static String print(List<? extends CollectionOp<?>> ops) {
			if (ops.isEmpty())
				return "[]";
			StringBuilder str = new StringBuilder();
			boolean separateTypes = !isSameType(ops);
			if (!separateTypes)
				str.append(ops.get(0).type);
			boolean sameIndexes = isSameIndex(ops);
			if (ops.get(0).minIndex >= 0 && sameIndexes) {
				str.append('@').append(ops.get(0).minIndex);
				if (ops.get(0).minIndex != ops.get(0).maxIndex)
					str.append('-').append(ops.get(0).maxIndex);
			}
			str.append('[');
			boolean first = true;
			for (CollectionOp<?> op : ops) {
				if (!first)
					str.append(", ");
				first = false;
				if (separateTypes)
					str.append(op.type);
				if (op.value != null)
					str.append(op.value);
				if (!sameIndexes && op.minIndex >= 0) {
					str.append('@').append(op.minIndex);
					if (op.minIndex != op.maxIndex)
						str.append('-').append(op.maxIndex);
				}
			}
			str.append(']');
			return str.toString();
		}

		public static boolean isAddAllIndex(List<? extends CollectionOp<?>> ops) {
			return !ops.isEmpty()//
				&& ops.get(0).type == CollectionChangeType.add && ops.get(0).minIndex >= 0//
				&& CollectionOp.isSameType(ops) && CollectionOp.isSameIndex(ops);
		}
	}

	private void expectModification(List<CollectionOp<T>> ops, int subListStart, int subListEnd, TestHelper helper) {
		if (ops.isEmpty())
			return;
		CollectionOp<T> rep = ops.get(0);
		switch (rep.type) {
		case add:
			CollectionLinkElement<S, T> after, before;
			if (rep.minIndex >= 0) {
				int sz = Math.min(theExpectedElements.size(), subListEnd - subListStart);
				if (rep.minIndex < 0) {
					rep.reject(rep.minIndex + " of " + sz, true);
					continue;
				} else if (rep.maxIndex > sz) {
					rep.reject(rep.maxIndex + " of " + sz, true);
					continue;
				}
				if (rep.minIndex == theExpectedElements.size()) {
					after = theExpectedElements.peekLast();
					before = null;
				} else if (rep.maxIndex == 0) {
					after = null;
					before = theExpectedElements.peekFirst();
				} else {
					after = rep.minIndex == 0 ? null : theExpectedElements.get(rep.minIndex);
					before = rep.maxIndex == theExpectedElements.size() ? null : theExpectedElements.get(rep.maxIndex);
				}
			} else
				after = before = null;
			for (CollectionOp<T> op : ops) {
				CollectionLinkElement<S, T> newElement = expectAdd(op.value, after, before, op.towardBeginning, op);
				if (!op.isRejected()) {
					int index = theExpectedElements.getElementsBefore(newElement.getExpectedAddress());
					if (index < op.minIndex || index > op.maxIndex)
						throw new IllegalStateException("Added in wrong location");
					if (theDerivedLink != null)
						theDerivedLink.expectFromSource(new ExpectedCollectionOperation<>(newElement, op.type, op.value, op.value));
				}
			}
			break;
		case remove:
			if (rep.minIndex >= 0) {
				CollectionElement<CollectionLinkElement<S, T>> el = theExpectedElements.getElement(rep.minIndex);
				for (int i = rep.minIndex; i < rep.maxIndex; i++) {
					expect(new ExpectedCollectionOperation<>(el.get(), rep.type, el.get().get(), el.get().get()), rep);
					// TODO
				}
			} else {}
			break;
		}
	}

	private int addToCollection(CollectionOp<T> add, BetterList<T> modify, TestHelper helper) {
		int preModSize = modify.size();
		int preSize = theCollection.size();
		if (add.ndex < 0) {
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
				element = null;
			}
			if (element != null) {
				Assert.assertTrue(element.getElementId().isPresent());
				Assert.assertNull(add.getMessage());
				Assert.assertTrue(theCollection.equivalence().elementEquals(add.value, element.get()));
				Assert.assertEquals(preModSize + 1, modify.size());
				Assert.assertEquals(preSize + 1, theCollection.size());
				return modify.getElementsBefore(element.getElementId());
			} else {
				Assert.assertNotNull(add.getMessage());
				Assert.assertEquals(preModSize, modify.size());
				Assert.assertEquals(preSize, theCollection.size());
				return -1;
			}
		} else {
			if (modify.isEmpty() || helper.getBoolean()) {
				// Test simple add by index
				try {
					CollectionElement<T> element = modify.addElement(add.ndex, add.value);
					if (element == null) {
						Assert.assertNotNull(add.getMessage());
						Assert.assertEquals(preModSize, modify.size());
						return -1;
					} else {
						Assert.assertNull(add.getMessage());
						Assert.assertTrue(theCollection.equivalence().elementEquals(add.value, element.get()));
					}
					Assert.assertEquals(preModSize + 1, modify.size());
					Assert.assertEquals(preSize + 1, theCollection.size());
					int index = modify.getElementsBefore(element.getElementId());
					Assert.assertTrue(index >= 0 && index <= preModSize);
					return index;
				} catch (UnsupportedOperationException | IllegalArgumentException e) {
					Assert.assertNotNull(add.getMessage());
					return -1;
				}
			} else {
				// Test add by element
				boolean addLeft;
				if (add.ndex == 0)
					addLeft = true;
				else if (add.ndex == modify.size())
					addLeft = false;
				else
					addLeft = helper.getBoolean();
				MutableCollectionElement<T> element = modify
					.mutableElement(modify.getElement(addLeft ? add.ndex : add.ndex - 1).getElementId());
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
					return -1;
				} else {
					ElementId newElement = element.add(add.value, addLeft);
					Assert.assertNotNull(newElement);
					Assert.assertEquals(preModSize + 1, modify.size());
					Assert.assertEquals(preSize + 1, theCollection.size());
					Assert.assertTrue(theCollection.equivalence().elementEquals(modify.getElement(newElement).get(), add.value));
					int index = modify.getElementsBefore(newElement);
					Assert.assertTrue(index >= 0 && index <= preModSize);
					return index;
				}
			}
		}
	}

	private void removeFromCollection(CollectionOp<T> op, BetterList<T> modify, TestHelper helper) {
		int preModSize = modify.size();
		int preSize = theCollection.size();
		MutableCollectionElement<T> element = modify.mutableElement(modify.getElement(op.ndex).getElementId());
		if (op.getMessage() != null)
			Assert.assertNotNull(element.canRemove());
		else
			Assert.assertNull(element.canRemove());
		if (helper.getBoolean()) {
			// Test simple remove by index
			try {
				modify.remove(op.ndex);
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
			}
		}
	}

	private void removeFromCollection(T value, CollectionOp<T> op, BetterList<T> modify, TestHelper helper) {
		int preModSize = modify.size();
		int preSize = theCollection.size();
		CollectionElement<T> element = modify.getElement(value, true);
		if (op == null) {
			Assert.assertNotNull(modify.canRemove(value));
			if (element != null) {
				MutableCollectionElement<T> mutableElement = modify.mutableElement(element.getElementId());
				Assert.assertNotNull(mutableElement.canRemove());
				if (helper.getBoolean()) {
					// Remove by element
					try {
						mutableElement.remove();
						Assert.assertTrue("Should have thrown exception", false);
					} catch (UnsupportedOperationException e) {}
					if (element != null)
						Assert.assertTrue(element.getElementId().isPresent());
					Assert.assertEquals(preModSize, modify.size());
					Assert.assertEquals(preSize, theCollection.size());
				}
			}
			Assert.assertFalse(modify.remove(value));
		} else {
			Assert.assertNotNull(element);
			Assert.assertEquals(op.ndex, modify.getElementsBefore(element.getElementId()));
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
				}
			}
		}
	}

	private void setInCollection(CollectionOp<T> op, BetterList<T> modify, TestHelper helper) {
		int preModSize = modify.size();
		int preSize = theCollection.size();
		MutableCollectionElement<T> element = modify.mutableElement(modify.getElement(op.ndex).getElementId());
		if (element.isEnabled() != null)
			Assert.assertNotNull(op.getMessage());
		else if (element.isAcceptable(op.value) != null)
			Assert.assertNotNull(op.getMessage());
		else
			Assert.assertNull(op.getMessage());
		if (helper.getBoolean()) {
			// Test simple set by index
			try {
				modify.set(op.ndex, op.value);
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

	private void addAllToCollection(int minIndex, int maxIndex, boolean first, List<CollectionOp<T>> ops, BetterList<T> modify,
		int subListStart, int subListEnd, TestHelper helper) {
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
		/* TODO Do I need this?
		 if (!ops.isEmpty()) {
			// Need to replace the indexes in the operations with the index at which the values were added in the collection (or sub-list)
			for (int i = 0; i < ops.size(); i++) {
				BiTuple<Integer, T> addition = theNewValues.pollFirst();
				boolean found = false;
				for (int j = i; !found && j < ops.size(); j++) {
					if (theCollection.equivalence().elementEquals(addition.getValue2(), ops.get(j).value)) {
						CollectionOp<T> op_i = ops.get(i);
						ops.set(i, new CollectionOp<>(CollectionChangeType.add, ops.get(j).value, addition.getValue1()));
						if (j != i)
							ops.set(j, op_i);
						found = true;
					}
				}
				Assert.assertTrue(found);
			}
		}*/
	}

	private void removeAllFromCollection(List<T> values, List<CollectionOp<T>> ops, BetterList<T> modify, TestHelper helper) {
		int preModSize = modify.size();
		int preSize = theCollection.size();
		boolean modified = modify.removeAll(values);
		int removed = 0;
		for (CollectionOp<T> op : ops) {
			if (op.getMessage() != null)
				continue;
			removed++;
			if (theCollection instanceof Set)
				Assert.assertNull(modify.getElement(op.value, true));
		}
		Assert.assertEquals(removed > 0, modified);
		Assert.assertEquals(preModSize - removed, modify.size());
		Assert.assertEquals(preSize - removed, theCollection.size());
	}

	private void retainAllInCollection(Collection<T> values, List<CollectionOp<T>> ops, BetterList<T> modify, TestHelper helper) {
		int preModSize = modify.size();
		int preSize = theCollection.size();
		boolean modified = modify.retainAll(values);
		int removed = 0;
		for (CollectionOp<T> op : ops) {
			if (op.getMessage() != null)
				continue;
			removed++;
			if (theCollection instanceof Set)
				Assert.assertNull(modify.getElement(op.value, true));
		}
		Assert.assertEquals(removed > 0, modified);
		Assert.assertEquals(preModSize - removed, modify.size());
		Assert.assertEquals(preSize - removed, theCollection.size());
	}

	private void clearCollection(List<CollectionOp<T>> ops, BetterList<T> modify, TestHelper helper) {
		int preModSize = modify.size();
		int preSize = theCollection.size();
		modify.clear();
		int removed = 0;
		for (CollectionOp<T> op : ops) {
			if (op.getMessage() != null)
				continue;
			removed++;
			if (theCollection instanceof Set)
				Assert.assertNull(modify.getElement(op.value, true));
		}
		Assert.assertEquals(preModSize - removed, modify.size());
		Assert.assertEquals(preSize - removed, theCollection.size());
	}
}
