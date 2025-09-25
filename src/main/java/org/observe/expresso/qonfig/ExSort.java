package org.observe.expresso.qonfig;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.observe.SettableValue;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableModelSet.InterpretedValueSynth;
import org.observe.expresso.ObservableModelSet.ModelComponentId;
import org.observe.expresso.ObservableModelSet.ModelInstantiator;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.ObservableModelSet.ModelValueInstantiator;
import org.observe.expresso.qonfig.ExSort.Interpreted.SortInstantiator;
import org.observe.util.TypeTokens;
import org.qommons.LambdaUtils;
import org.qommons.QommonsUtils;
import org.qommons.StringUtils;
import org.qommons.collect.BetterList;
import org.qommons.config.AbstractQIS;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretationException;
import org.qommons.io.LocatedPositionedContent;

import com.google.common.reflect.TypeToken;

/** A &lt;sort> element to sort a collection, transformation results, etc., or a &lt;sort-by> in a &lt;sort> */
@ExElementTraceable(toolkit = ExpressoBaseV0_1.BASE, qonfigType = ExSort.SORT, interpretation = ExSort.Interpreted.class)
public abstract class ExSort extends ExElement.Def.Abstract<ExElement> {
	/** The XML name of the &lt;sort> element */
	public static final String SORT = "sort";
	/** The XML name of the &lt;sort-by> element */
	public static final String SORT_BY = "sort-by";

	private ModelComponentId theSortValue;
	private ModelComponentId theSortCompareValue;
	private CompiledExpression theSortWith;
	private final List<ExSortBy> theSortBy;
	private boolean isAscending;

	/**
	 * @param parent The parent element of this sort
	 * @param qonfigType The Qonfig type of this element
	 */
	protected ExSort(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
		super(parent, qonfigType);
		theSortBy = new ArrayList<>();
	}

	/** @return The ID of the model value that the first value to be sorted will be available to expressions as */
	@QonfigAttributeGetter("sort-value-as")
	public ModelComponentId getSortValue() {
		return theSortValue;
	}

	/** @return The ID of the model value that the second value to be sorted will be available to expressions as */
	@QonfigAttributeGetter("sort-compare-value-as")
	public ModelComponentId getSortCompareValue() {
		return theSortCompareValue;
	}

	/**
	 * @return The expression to return an integer determining how the values available as the {@link #getSortValue() sort-value-as} and
	 *         {@link #getSortCompareValue() sort-compare-value-as} will be ordered
	 */
	@QonfigAttributeGetter("sort-with")
	public CompiledExpression getSortWith() {
		return theSortWith;
	}

	/** @return &lt;sort-by> elements in this sort to sort values by their qualities */
	@QonfigChildGetter("sort-by")
	public List<ExSortBy> getSortBy() {
		return Collections.unmodifiableList(theSortBy);
	}

	/** @return If false, this sort will be reversed */
	@QonfigAttributeGetter("ascending")
	public boolean isAscending() {
		return isAscending;
	}

	@Override
	protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
		super.doUpdate(session);
		ExWithElementModel.Def elModels = getAddOn(ExWithElementModel.Def.class);
		String sortValueName = session.getAttributeText("sort-value-as");
		theSortValue = sortValueName == null ? null : elModels.getElementValueModelId(sortValueName);
		LocatedPositionedContent sortValueNamePosition = session.attributes().get("sort-value-as").getLocatedContent();
		String sortValueCompareName = session.getAttributeText("sort-compare-value-as");
		theSortCompareValue = sortValueCompareName == null ? null : elModels.getElementValueModelId(sortValueCompareName);
		LocatedPositionedContent sortCompareValueNamePosition = session.attributes().get("sort-compare-value-as").getLocatedContent();
		theSortWith = getAttributeExpression("sort-with", session);
		isAscending = session.getAttribute("ascending", boolean.class);
		syncChildren(ExSortBy.class, theSortBy, session.forChildren("sort-by"));

		if (theSortWith != null) {
			if (!theSortBy.isEmpty())
				reporting().error("sort-with or sort-by may be used, but not both");
			if (theSortValue == null)
				throw new QonfigInterpretationException("sort-with must be used with sort-value-as",
					theSortWith.getElement().getPositionInFile(), 0);
			else if (theSortCompareValue == null)
				throw new QonfigInterpretationException("sort-with must be used with sort-compare-value-as",
					theSortWith.getElement().getPositionInFile(), 0);
		} else if (!theSortBy.isEmpty()) {
			if (theSortValue == null)
				throw new QonfigInterpretationException("sort-by must be used with sort-value-as", getElement().getPositionInFile(), 0);
			if (theSortCompareValue != null)
				reporting().at(sortCompareValueNamePosition).warn("sort-compare-value-as is not used with sort-by");
		} else {
			if (theSortValue != null)
				reporting().at(sortValueNamePosition).warn("sort-value-as is not used with default sorting");
			if (theSortCompareValue != null)
				reporting().at(sortCompareValueNamePosition).warn("sort-compare-value-as is not used with default sorting");
		}

		if (theSortValue != null || theSortCompareValue != null) {
			ExWithElementModel.Def withElModel = getAddOn(ExWithElementModel.Def.class);
			if (theSortValue != null)
				withElModel.<Interpreted<?, ?>, SettableValue<?>> satisfyElementSingleValueType(theSortValue, ModelTypes.Value,
					Interpreted::getSortType);
			if (theSortCompareValue != null)
				withElModel.<Interpreted<?, ?>, SettableValue<?>> satisfyElementSingleValueType(theSortCompareValue, ModelTypes.Value,
					Interpreted::getSortType);
		}
	}

	/**
	 * @param parent The parent for the interpreted sort
	 * @return The interpreted sort
	 * @throws ExpressoInterpretationException If this sort element's placement is illegal
	 */
	public abstract Interpreted<?, ?> interpret(ExElement.Interpreted<?> parent) throws ExpressoInterpretationException;

	/**
	 * Interpreted &lt;sort> element
	 *
	 * @param <OT> The outer type, the type of the value being sorted
	 * @param <IT> The inner type, the type of the value that this sort compares, which may be that of a field or other characteristic of
	 *        the outer type
	 */
	public static abstract class Interpreted<OT, IT> extends ExElement.Interpreted.Abstract<ExElement> {
		private InterpretedValueSynth<SettableValue<?>, SettableValue<Integer>> theSortWith;
		private final List<ExSortBy.Interpreted<IT, ?>> theSortBy;
		private TypeToken<IT> theSortType;
		private Comparator<? super IT> theDefaultSorting;

		/**
		 * @param definition The definition to interpret
		 * @param parent The parent of this sort
		 */
		protected Interpreted(ExSort definition, ExElement.Interpreted<?> parent) {
			super(definition, parent);
			theSortBy = new ArrayList<>();
		}

		@Override
		public ExSort getDefinition() {
			return (ExSort) super.getDefinition();
		}

		/**
		 * @return The expression to return an integer determining how the values available as the {@link ExSort#getSortValue()
		 *         sort-value-as} and {@link ExSort#getSortCompareValue() sort-compare-value-as} will be ordered
		 */
		public InterpretedValueSynth<SettableValue<?>, SettableValue<Integer>> getSortWith() {
			return theSortWith;
		}

		/** @return &lt;sort-by> elements in this sort to sort values by their qualities */
		public List<ExSortBy.Interpreted<IT, ?>> getSortBy() {
			return Collections.unmodifiableList(theSortBy);
		}

		/** @return The sorting to use if neither {@link #getSortWith() sort-with} nor {@link #getSortBy() sort-by} are specified */
		public Comparator<? super IT> getDefaultSorting() {
			return theDefaultSorting;
		}

		/** @return The type of the value being sorted */
		public abstract TypeToken<OT> getSortType();

		/** @return The type of value thiat expressions in this sort handle */
		protected TypeToken<IT> getInternalSortType() {
			return theSortType;
		}

		/**
		 * Instantiates or updates this sorting
		 *
		 * @param type The type to sort
		 * @throws ExpressoInterpretationException If anything in this sort could not be interpreted
		 */
		public abstract void update(TypeToken<OT> type) throws ExpressoInterpretationException;

		/**
		 * @param internalType The internal type, the type of values that this sort's expressions use
		 * @throws ExpressoInterpretationException If anything in this sort could not be interpreted
		 */
		protected void updateInternal(TypeToken<IT> internalType) throws ExpressoInterpretationException {
			theSortType = internalType;
			super.update();
			theSortWith = interpret(getDefinition().getSortWith(), ModelTypes.Value.INT);
			syncChildren(getDefinition().getSortBy(), theSortBy, def -> (ExSortBy.Interpreted<IT, ?>) def.interpret(this),
				i -> i.update(internalType));
			if (theSortWith == null && theSortBy.isEmpty()) {
				Class<IT> raw = TypeTokens.getRawType(internalType);
				theDefaultSorting = ExSort.getDefaultSorting(raw);
				if (theDefaultSorting == null)
					throw new ExpressoInterpretationException(internalType + " is not Comparable, use either sort-with or sort-by",
						reporting().getFileLocation().getPosition(0), 0);
			} else
				theDefaultSorting = null;
		}

		/**
		 * @return The instantiations of all this sort's &lt;sort-by> children
		 * @throws ModelInstantiationException If anything in the &lt;sort-by> children could not be instantiated
		 */
		protected List<SortInstantiator<IT, ?>> instantiateSortBy() throws ModelInstantiationException {
			return QommonsUtils.filterMapE(theSortBy, null, sb -> sb.doInstantiateSort());
		}

		/**
		 * @return The instantiated sorting
		 * @throws ModelInstantiationException If anything in this sorting could not be instantiated
		 */
		public ModelValueInstantiator<Comparator<? super OT>> instantiateSort() throws ModelInstantiationException {
			return doInstantiateSort();
		}

		/**
		 * @return The instantiated sorting
		 * @throws ModelInstantiationException If anything in this sorting could not be instantiated
		 */
		protected abstract SortInstantiator<OT, IT> doInstantiateSort() throws ModelInstantiationException;

		/** @return All expression components of this sort element */
		public BetterList<InterpretedValueSynth<?, ?>> getComponents() {
			if (theSortWith != null)
				return BetterList.of(theSortWith);
			else
				return BetterList.of(theSortBy.stream().flatMap(sb -> sb.getComponents().stream()));
		}

		static abstract class SortInstantiator<OT, IT> implements ModelValueInstantiator<Comparator<? super OT>> {
			protected final ModelComponentId theSortValue;
			protected final ModelComponentId theSortCompareValue;
			protected final ModelValueInstantiator<SettableValue<Integer>> theSortWith;
			protected final List<SortInstantiator<IT, ?>> theSortBy;
			protected final Comparator<? super IT> theDefaultSorting;
			protected final boolean isAscending;

			protected SortInstantiator(ModelComponentId sortValue, ModelComponentId sortCompareValue,
				ModelValueInstantiator<SettableValue<Integer>> sortWith, List<SortInstantiator<IT, ?>> sortBy,
				Comparator<? super IT> defaultSorting, boolean ascending) {
				theSortValue = sortValue;
				theSortCompareValue = sortCompareValue;
				theSortWith = sortWith;
				theSortBy = sortBy;
				theDefaultSorting = defaultSorting;
				isAscending = ascending;
			}

			@Override
			public Comparator<? super OT> get(ModelSetInstance models) throws ModelInstantiationException, IllegalStateException {
				SettableValue<OT> left = SettableValue.<OT> build().withDescription(theSortValue.getName()).build();
				SettableValue<OT> right = SettableValue.<OT> build().withDescription(theSortValue.getName()).build();
				Supplier<Integer> sorting = getExternalSorting(models, left, right);
				return new SortWithComparator<>(left, right, sorting);
			}

			@Override
			public Comparator<? super OT> forModelCopy(Comparator<? super OT> value, ModelSetInstance sourceModels,
				ModelSetInstance newModels) throws ModelInstantiationException {
				return get(newModels);
			}

			protected abstract Supplier<Integer> getExternalSorting(ModelSetInstance parentModels, SettableValue<OT> left,
				SettableValue<OT> right) throws ModelInstantiationException;

			protected Supplier<Integer> getInternalSorting(ModelSetInstance models, SettableValue<IT> left, SettableValue<IT> right)
				throws ModelInstantiationException {
				if (theSortWith != null) {
					ExFlexibleElementModelAddOn.satisfyElementValue(theSortValue, models, left);
					ExFlexibleElementModelAddOn.satisfyElementValue(theSortCompareValue, models, right);
					return theSortWith.get(models);
				} else if (!theSortBy.isEmpty()) {
					Supplier<Integer>[] sortBy = new Supplier[theSortBy.size()];
					int i = 0;
					for (SortInstantiator<IT, ?> sb : theSortBy)
						sortBy[i++] = sb.getExternalSorting(models, left, right);
					return new CompositeIntSupplier(sortBy, isAscending);
				} else
					return new DefaultSortSupplier<>(left, right, theDefaultSorting);
			}
		}

		/**
		 * A comparator representing a &lt;sort-with> element
		 *
		 * @param <T> The type to sort
		 */
		public static class SortWithComparator<T> implements Comparator<T> {
			private final SettableValue<T> theLeftValue;
			private final SettableValue<T> theRightValue;
			private final Supplier<Integer> theCompareResult;

			/**
			 * @param left The container for the left value to sort
			 * @param right The container for the right value to sort
			 * @param compareResult Provides the sorting result once the values are populated
			 */
			public SortWithComparator(SettableValue<T> left, SettableValue<T> right, Supplier<Integer> compareResult) {
				theLeftValue = left;
				theRightValue = right;
				theCompareResult = compareResult;
			}

			@Override
			public int compare(T o1, T o2) {
				theLeftValue.set(o1, null);
				theRightValue.set(o2, null);
				Integer result = theCompareResult.get();
				if (result == null)
					return 0;
				return result.intValue();
			}

			@Override
			public int hashCode() {
				return theCompareResult.hashCode();
			}

			@Override
			public boolean equals(Object obj) {
				if (this == obj)
					return true;
				else
					return obj instanceof SortWithComparator && theCompareResult.equals(((SortWithComparator<?>) obj).theCompareResult);
			}

			@Override
			public String toString() {
				return theCompareResult.toString();
			}
		}

		/** An integer supplier composed of one or more others. Returns the value of the first one that is non-zero, or null. */
		public static class CompositeIntSupplier implements Supplier<Integer> {
			private final Supplier<Integer>[] theComponents;
			private final boolean isAscending;

			/**
			 * @param components The components for this composite
			 * @param ascending Whether the sort is ascending
			 */
			public CompositeIntSupplier(Supplier<Integer>[] components, boolean ascending) {
				theComponents = components;
				isAscending = ascending;
			}

			@Override
			public Integer get() {
				Integer result = null;
				for (Supplier<Integer> component : theComponents) {
					result = component.get();
					if (result != null && result.intValue() != 0)
						break;
				}
				if (result == null)
					return null;
				else if (isAscending)
					return result;
				else
					return -result;
			}

			@Override
			public int hashCode() {
				return Objects.hash((Object[]) theComponents);
			}

			@Override
			public boolean equals(Object obj) {
				if (this == obj)
					return true;
				else
					return obj instanceof CompositeIntSupplier && Arrays.equals(theComponents, ((CompositeIntSupplier) obj).theComponents);
			}

			@Override
			public String toString() {
				return Arrays.toString(theComponents);
			}
		}

		/**
		 * A comparator representing default sorting (when sorting is specified, but no mechanism is given)
		 *
		 * @param <T> The type to sort
		 */
		public static class DefaultSortSupplier<T> implements Supplier<Integer> {
			private final Supplier<T> theLeft;
			private final Supplier<T> theRight;
			private final Comparator<? super T> theSorting;

			/**
			 * @param left The container for the left value to sort
			 * @param right The container for the right value to sort
			 * @param sorting The comparator for the values
			 */
			public DefaultSortSupplier(Supplier<T> left, Supplier<T> right, Comparator<? super T> sorting) {
				theLeft = left;
				theRight = right;
				theSorting = sorting;
			}

			@Override
			public Integer get() {
				return theSorting.compare(theLeft.get(), theRight.get());
			}

			@Override
			public int hashCode() {
				return theSorting.hashCode();
			}

			@Override
			public boolean equals(Object obj) {
				return obj instanceof DefaultSortSupplier && theSorting.equals(((DefaultSortSupplier<?>) obj).theSorting);
			}

			@Override
			public String toString() {
				return theSorting.toString();
			}
		}
	}

	/** A &lt;sort> element to sort a collection, transformation results, etc. */
	public static class ExRootSort extends ExSort {
		/**
		 * @param parent The parent element for this sort
		 * @param qonfigType The Qonfig type of this element
		 */
		public ExRootSort(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType);
		}

		@Override
		public Interpreted<?> interpret(ExElement.Interpreted<?> parent) {
			return new Interpreted<>(this, parent);
		}

		/**
		 * Interpretation for {@link ExRootSort}
		 *
		 * @param <T> The type of value being sorted
		 */
		public static class Interpreted<T> extends ExSort.Interpreted<T, T> {
			Interpreted(ExRootSort definition, ExElement.Interpreted<?> parent) {
				super(definition, parent);
			}

			@Override
			public ExRootSort getDefinition() {
				return (ExRootSort) super.getDefinition();
			}

			@Override
			public void update(TypeToken<T> type) throws ExpressoInterpretationException {
				updateInternal(type);
			}

			@Override
			public TypeToken<T> getSortType() {
				return getInternalSortType();
			}

			@Override
			public ModelValueInstantiator<Comparator<? super T>> instantiateSort() throws ModelInstantiationException {
				if (getDefaultSorting() != null)
					return ModelValueInstantiator.literal(getDefaultSorting(), "default");
				return super.instantiateSort();
			}

			@Override
			protected SortInstantiator<T, T> doInstantiateSort() throws ModelInstantiationException {
				return new RootSortInstantiator<>(getDefinition().getSortValue(), getDefinition().getSortCompareValue(), //
					getSortWith() == null ? null : getSortWith().instantiate(), //
						instantiateSortBy(), getDefaultSorting(), getDefinition().isAscending(), instantiateLocalModels());
			}
		}

		static class RootSortInstantiator<T> extends SortInstantiator<T, T> {
			private final DocumentMap<ModelInstantiator> theLocalModel;

			RootSortInstantiator(ModelComponentId sortValue, ModelComponentId sortCompareValue,
				ModelValueInstantiator<SettableValue<Integer>> sortWith, List<SortInstantiator<T, ?>> sortBy,
				Comparator<? super T> defaultSorting, boolean ascending, DocumentMap<ModelInstantiator> localModel) {
				super(sortValue, sortCompareValue, sortWith, sortBy, defaultSorting, ascending);
				theLocalModel = localModel;
			}

			@Override
			public void instantiate() throws ModelInstantiationException {
				theLocalModel.forEach(ModelInstantiator::instantiate);
			}

			@Override
			protected Supplier<Integer> getExternalSorting(ModelSetInstance parentModels, SettableValue<T> left, SettableValue<T> right)
				throws ModelInstantiationException {
				parentModels = theLocalModel.operate(parentModels, (m, mi) -> mi.wrap(m));
				return getInternalSorting(parentModels, left, right);
			}
		}
	}

	/** A &lt;sort-by> element in a &lt;sort> */
	@ExElementTraceable(toolkit = ExpressoBaseV0_1.BASE, qonfigType = SORT_BY, interpretation = ExSortBy.Interpreted.class)
	public static class ExSortBy extends ExSort {
		private final ExSort theSort;
		private CompiledExpression theAttribute;

		/**
		 * @param parent The parent element for this sort
		 * @param qonfigType The Qonfig type of this element
		 * @param session The session parsing this sorting
		 * @throws QonfigInterpretationException If this sort-by element's parent is not an instance of {@link ExSort}
		 */
		public ExSortBy(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType, AbstractQIS<?> session)
			throws QonfigInterpretationException {
			super(parent, qonfigType);
			theSort = parent.as(ExSort.class, session.reporting().getPosition());
		}

		/** @return This sort-by element's {@link ExSort} parent */
		public ExSort getSort() {
			return theSort;
		}

		/** @return The expression returning the attribute of sorted values to sort by */
		@QonfigAttributeGetter
		public CompiledExpression getAttribute() {
			return theAttribute;
		}

		@Override
		protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
			super.doUpdate(session.asElement(session.getFocusType().getSuperElement()));
			theAttribute = getValueExpression(session);
		}

		@Override
		public Interpreted<?, ?> interpret(ExElement.Interpreted<?> parent) throws ExpressoInterpretationException {
			return new Interpreted<>(this, parent);
		}

		/**
		 * Interpretation of {@link ExSortBy}
		 *
		 * @param <OT> The outer type, the type of the value being sorted
		 * @param <IT> The inner type, the type of the value that this sort compares--that of the field or other characteristic of the outer
		 *        type
		 */
		public static class Interpreted<OT, IT> extends ExSort.Interpreted<OT, IT> {
			private InterpretedValueSynth<SettableValue<?>, SettableValue<IT>> theAttribute;
			private final ExSort.Interpreted<?, OT> theSort;

			Interpreted(ExSortBy definition, ExElement.Interpreted<?> parent) throws ExpressoInterpretationException {
				super(definition, parent);
				theSort = parent.as(ExSort.Interpreted.class, definition.reporting().getPosition());
			}

			@Override
			public ExSortBy getDefinition() {
				return (ExSortBy) super.getDefinition();
			}

			/** @return This sort-by element's {@link ExSort.Interpreted} parent */
			public ExSort.Interpreted<?, OT> getSort() {
				return theSort;
			}

			@Override
			public TypeToken<OT> getSortType() {
				return theSort.getInternalSortType();
			}

			/** @return The expression returning the attribute of sorted values to sort by */
			public InterpretedValueSynth<SettableValue<?>, SettableValue<IT>> getAttribute() {
				return theAttribute;
			}

			@Override
			public void update(TypeToken<OT> type) throws ExpressoInterpretationException {
				theAttribute = getParentElement().interpret(getDefinition().getAttribute(), ModelTypes.Value.anyAsV());
				updateInternal((TypeToken<IT>) theAttribute.getType().getType(0));
			}

			@Override
			public BetterList<InterpretedValueSynth<?, ?>> getComponents() {
				return BetterList.of(Stream.concat(Stream.of(theAttribute), super.getComponents().stream()));
			}

			@Override
			protected SortInstantiator<OT, IT> doInstantiateSort() throws ModelInstantiationException {
				return new SortByInstantiator<>(getDefinition().getSortValue(), getDefinition().getSortCompareValue(),
					getSortWith() == null ? null : getSortWith().instantiate(), instantiateSortBy(), getDefaultSorting(),
						getDefinition().isAscending(), instantiateLocalModels(),
						theSort.getDefinition().getSortValue(), theAttribute.instantiate());
			}
		}

		static class SortByInstantiator<OT, IT> extends SortInstantiator<OT, IT> {
			private final DocumentMap<ModelInstantiator> theLocalModel;
			private final ModelComponentId theParentSortValue;
			private final ModelValueInstantiator<SettableValue<IT>> theAttribute;

			SortByInstantiator(ModelComponentId sortValue, ModelComponentId sortCompareValue,
				ModelValueInstantiator<SettableValue<Integer>> sortWith, List<SortInstantiator<IT, ?>> sortBy,
				Comparator<? super IT> defaultSorting, boolean ascending, DocumentMap<ModelInstantiator> localModel,
				ModelComponentId parentSortValue,
				ModelValueInstantiator<SettableValue<IT>> attribute) {
				super(sortValue, sortCompareValue, sortWith, sortBy, defaultSorting, ascending);
				theLocalModel = localModel;
				theParentSortValue = parentSortValue;
				theAttribute = attribute;
			}

			@Override
			public void instantiate() throws ModelInstantiationException {
				theLocalModel.forEach(ModelInstantiator::instantiate);
				theAttribute.instantiate();
			}

			@Override
			protected Supplier<Integer> getExternalSorting(ModelSetInstance parentModels, SettableValue<OT> left, SettableValue<OT> right)
				throws ModelInstantiationException {
				ModelSetInstance models = theLocalModel.operate(parentModels, (m, mi) -> mi.wrap(m));
				ModelSetInstance leftCopy = models.copy().build();
				ModelSetInstance rightCopy = models.copy().build();
				ExFlexibleElementModelAddOn.satisfyElementValue(theParentSortValue, leftCopy, left);
				ExFlexibleElementModelAddOn.satisfyElementValue(theParentSortValue, rightCopy, right);
				SettableValue<IT> internalLeft = theAttribute.get(leftCopy);
				SettableValue<IT> internalRight = theAttribute.get(rightCopy);
				return getInternalSorting(models, internalLeft, internalRight);
			}
		}
	}

	/**
	 * @param <T> The type to sort
	 * @param type The type to sort
	 * @return A comparator that may be used to sort the type if sorting is specified but no mechanism is given, or null if default sorting
	 *         is not available for the given type
	 */
	public static <T> Comparator<T> getDefaultSorting(Class<T> type) {
		if (CharSequence.class.isAssignableFrom(type))
			return (Comparator<T>) StringUtils.DISTINCT_NUMBER_TOLERANT;
		else if (Comparable.class.isAssignableFrom(TypeTokens.get().wrap(type))) {
			return LambdaUtils.printableComparator((v1, v2) -> {
				if (v1 == null) {
					if (v2 == null)
						return 0;
					else
						return 1;
				} else if (v2 == null)
					return -1;
				else
					return ((Comparable<T>) v1).compareTo(v2);
			}, () -> "comparable", "comparableComparator");
		} else
			return null;
	}
}
