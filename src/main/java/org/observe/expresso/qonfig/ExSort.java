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
import org.observe.expresso.InterpretedExpressoEnv;
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
import org.qommons.config.QonfigElement;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretationException;
import org.qommons.io.LocatedFilePosition;
import org.qommons.io.LocatedPositionedContent;

import com.google.common.reflect.TypeToken;

@ExElementTraceable(toolkit = ExpressoBaseV0_1.BASE, qonfigType = "sort", interpretation = ExSort.Interpreted.class)
public abstract class ExSort extends ExElement.Def.Abstract<ExElement> {
	/** A structure parsed from a {@link QonfigElement} that is capable of generating a {@link Comparator} for sorting */
	public interface CompiledSorting {
		/**
		 * @param <T> The type to sort
		 * @param type The type to sort
		 * @return A value container capable
		 * @throws ExpressoInterpretationException
		 */
		<T> InterpretedValueSynth<SettableValue<?>, SettableValue<Comparator<T>>> evaluate(TypeToken<T> type)
			throws ExpressoInterpretationException;
	}

	private ModelComponentId theSortValue;
	private ModelComponentId theSortCompareValue;
	private CompiledExpression theSortWith;
	private final List<ExSortBy> theSortBy;
	private boolean isAscending;

	protected ExSort(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
		super(parent, qonfigType);
		theSortBy = new ArrayList<>();
	}

	@QonfigAttributeGetter("sort-value-as")
	public ModelComponentId getSortValue() {
		return theSortValue;
	}

	@QonfigAttributeGetter("sort-compare-value-as")
	public ModelComponentId getSortCompareValue() {
		return theSortCompareValue;
	}

	@QonfigAttributeGetter("sort-with")
	public CompiledExpression getSortWith() {
		return theSortWith;
	}

	@QonfigChildGetter("sort-by")
	public List<ExSortBy> getSortBy() {
		return Collections.unmodifiableList(theSortBy);
	}

	@QonfigAttributeGetter("ascending")
	public boolean isAscending() {
		return isAscending;
	}

	@Override
	protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
		super.doUpdate(session.asElement(session.getFocusType().getSuperElement()));
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
				withElModel.<Interpreted<?, ?>, SettableValue<?>> satisfyElementValueType(theSortValue, ModelTypes.Value,
					(interp, env) -> ModelTypes.Value.forType(interp.getSortType()));
			if (theSortCompareValue != null)
				withElModel.<Interpreted<?, ?>, SettableValue<?>> satisfyElementValueType(theSortCompareValue, ModelTypes.Value,
					(interp, env) -> ModelTypes.Value.forType(interp.getSortType()));
		}
	}

	public abstract Interpreted<?, ?> interpret(ExElement.Interpreted<?> parent);

	public static abstract class Interpreted<OT, IT> extends ExElement.Interpreted.Abstract<ExElement> {
		private InterpretedValueSynth<SettableValue<?>, SettableValue<Integer>> theSortWith;
		private final List<ExSortBy.Interpreted<IT, ?>> theSortBy;
		private TypeToken<IT> theSortType;
		private Comparator<? super IT> theDefaultSorting;

		protected Interpreted(ExSort definition, ExElement.Interpreted<?> parent) {
			super(definition, parent);
			theSortBy = new ArrayList<>();
		}

		@Override
		public ExSort getDefinition() {
			return (ExSort) super.getDefinition();
		}

		public InterpretedValueSynth<SettableValue<?>, SettableValue<Integer>> getSortWith() {
			return theSortWith;
		}

		public List<ExSortBy.Interpreted<IT, ?>> getSortBy() {
			return Collections.unmodifiableList(theSortBy);
		}

		public Comparator<? super IT> getDefaultSorting() {
			return theDefaultSorting;
		}

		public abstract TypeToken<OT> getSortType();

		protected TypeToken<IT> getInternalSortType() {
			return theSortType;
		}

		public abstract void update(TypeToken<OT> type, InterpretedExpressoEnv env) throws ExpressoInterpretationException;

		protected void updateInternal(TypeToken<IT> internalType, InterpretedExpressoEnv env) throws ExpressoInterpretationException {
			theSortType = internalType;
			super.update(env);
			theSortWith = interpret(getDefinition().getSortWith(), ModelTypes.Value.INT);
			syncChildren(getDefinition().getSortBy(), theSortBy, def -> (ExSortBy.Interpreted<IT, ?>) def.interpret(this),
				(i, sEnv) -> i.update(internalType, sEnv));
			if (theSortWith == null && theSortBy.isEmpty()) {
				Class<IT> raw = TypeTokens.getRawType(internalType);
				theDefaultSorting = ExSort.getDefaultSorting(raw);
				if (theDefaultSorting == null)
					throw new ExpressoInterpretationException(internalType + " is not Comparable, use either sort-with or sort-by",
						reporting().getFileLocation().getPosition(0), 0);
			} else
				theDefaultSorting = null;
		}

		protected List<SortInstantiator<IT, ?>> instantiateSortBy() throws ModelInstantiationException {
			return QommonsUtils.filterMapE(theSortBy, null, sb -> sb.doInstantiateSort());
		}

		public ModelValueInstantiator<Comparator<? super OT>> instantiateSort() throws ModelInstantiationException {
			return doInstantiateSort();
		}

		protected abstract SortInstantiator<OT, IT> doInstantiateSort() throws ModelInstantiationException;

		public BetterList<InterpretedValueSynth<?, ?>> getComponents() {
			if (theSortWith != null)
				return BetterList.of(theSortWith);
			else
				return BetterList.of(theSortBy.stream().flatMap(sb -> sb.getComponents().stream()));
		}

		static abstract class SortInstantiator<OT, IT> implements ModelValueInstantiator<Comparator<? super OT>> {
			protected final TypeToken<OT> theSortType;
			protected final ModelComponentId theSortValue;
			protected final ModelComponentId theSortCompareValue;
			protected final ModelValueInstantiator<SettableValue<Integer>> theSortWith;
			protected final List<SortInstantiator<IT, ?>> theSortBy;
			protected final Comparator<? super IT> theDefaultSorting;
			protected final boolean isAscending;

			protected SortInstantiator(TypeToken<OT> sortType, ModelComponentId sortValue, ModelComponentId sortCompareValue,
				ModelValueInstantiator<SettableValue<Integer>> sortWith, List<SortInstantiator<IT, ?>> sortBy,
				Comparator<? super IT> defaultSorting, boolean ascending) {
				theSortType = sortType;
				theSortValue = sortValue;
				theSortCompareValue = sortCompareValue;
				theSortWith = sortWith;
				theSortBy = sortBy;
				theDefaultSorting = defaultSorting;
				isAscending = ascending;
			}

			@Override
			public Comparator<? super OT> get(ModelSetInstance models) throws ModelInstantiationException, IllegalStateException {
				SettableValue<OT> left = SettableValue.build(theSortType).withDescription(theSortValue.getName())
					.withValue(TypeTokens.get().getDefaultValue(theSortType)).build();
				SettableValue<OT> right = SettableValue.build(theSortType).withDescription(theSortValue.getName())
					.withValue(TypeTokens.get().getDefaultValue(theSortType)).build();
				Supplier<Integer> sorting = getExternalSorting(models, left, right);
				return new SortWithComparator<>(left, right, sorting, isAscending);
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
					return new CompositeIntSupplier(sortBy);
				} else
					return new DefaultSortSupplier<>(left, right, theDefaultSorting);
			}
		}

		public static class SortWithComparator<T> implements Comparator<T> {
			private final SettableValue<T> theLeftValue;
			private final SettableValue<T> theRightValue;
			private final Supplier<Integer> theCompareResult;
			private boolean isAscending;

			public SortWithComparator(SettableValue<T> leftValue, SettableValue<T> rightValue, Supplier<Integer> compareResult,
				boolean ascending) {
				theLeftValue = leftValue;
				theRightValue = rightValue;
				theCompareResult = compareResult;
				isAscending = ascending;
			}

			@Override
			public int compare(T o1, T o2) {
				theLeftValue.set(o1, null);
				theRightValue.set(o2, null);
				Integer result = theCompareResult.get();
				if (result == null)
					return 0;
				else if (isAscending)
					return result.intValue();
				else
					return -result.intValue();
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
				return (isAscending ? "" : "-") + theCompareResult.toString();
			}
		}

		public static class CompositeIntSupplier implements Supplier<Integer> {
			private final Supplier<Integer>[] theComponents;

			public CompositeIntSupplier(Supplier<Integer>[] components) {
				theComponents = components;
			}

			@Override
			public Integer get() {
				Integer result = null;
				for (Supplier<Integer> component : theComponents) {
					result = component.get();
					if (result != null && result.intValue() != 0)
						return result;
				}
				return null;
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

		public static class DefaultSortSupplier<T> implements Supplier<Integer> {
			private final Supplier<T> theLeft;
			private final Supplier<T> theRight;
			private final Comparator<? super T> theSorting;

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

	public static class ExRootSort extends ExSort {
		public ExRootSort(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType);
		}

		@Override
		public Interpreted<?> interpret(ExElement.Interpreted<?> parent) {
			return new Interpreted<>(this, parent);
		}

		public static class Interpreted<T> extends ExSort.Interpreted<T, T> {
			public Interpreted(ExRootSort definition, ExElement.Interpreted<?> parent) {
				super(definition, parent);
			}

			@Override
			public ExRootSort getDefinition() {
				return (ExRootSort) super.getDefinition();
			}

			@Override
			public void update(TypeToken<T> type, InterpretedExpressoEnv env) throws ExpressoInterpretationException {
				updateInternal(type, env);
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
				return new RootSortInstantiator<>(getSortType(), getDefinition().getSortValue(), getDefinition().getSortCompareValue(), //
					getSortWith() == null ? null : getSortWith().instantiate(), //
						instantiateSortBy(), getDefaultSorting(), getDefinition().isAscending(), getExpressoEnv().getModels().instantiate());
			}
		}

		static class RootSortInstantiator<T> extends SortInstantiator<T, T> {
			private final ModelInstantiator theLocalModel;

			RootSortInstantiator(TypeToken<T> sortType, ModelComponentId sortValue, ModelComponentId sortCompareValue,
				ModelValueInstantiator<SettableValue<Integer>> sortWith, List<SortInstantiator<T, ?>> sortBy,
				Comparator<? super T> defaultSorting, boolean ascending, ModelInstantiator localModel) {
				super(sortType, sortValue, sortCompareValue, sortWith, sortBy, defaultSorting, ascending);
				theLocalModel = localModel;
			}

			@Override
			public void instantiate() throws ModelInstantiationException {
				theLocalModel.instantiate();
			}

			@Override
			protected Supplier<Integer> getExternalSorting(ModelSetInstance parentModels, SettableValue<T> left, SettableValue<T> right)
				throws ModelInstantiationException {
				parentModels = theLocalModel.wrap(parentModels);
				return getInternalSorting(parentModels, left, right);
			}
		}
	}

	@ExElementTraceable(toolkit = ExpressoBaseV0_1.BASE, qonfigType = "sort-by", interpretation = ExSortBy.Interpreted.class)
	public static class ExSortBy extends ExSort {
		private CompiledExpression theAttribute;

		public ExSortBy(ExSort parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType);
		}

		@Override
		public ExSort getParentElement() {
			return (ExSort) super.getParentElement();
		}

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
		public Interpreted<?, ?> interpret(ExElement.Interpreted<?> parent) {
			return new Interpreted<>(this, (ExSort.Interpreted<?, ?>) parent);
		}

		public static class Interpreted<OT, IT> extends ExSort.Interpreted<OT, IT> {
			private InterpretedValueSynth<SettableValue<?>, SettableValue<IT>> theAttribute;

			public Interpreted(ExSortBy definition, ExSort.Interpreted<?, OT> parent) {
				super(definition, parent);
			}

			@Override
			public ExSortBy getDefinition() {
				return (ExSortBy) super.getDefinition();
			}

			@Override
			public ExSort.Interpreted<?, OT> getParentElement() {
				return (ExSort.Interpreted<?, OT>) super.getParentElement();
			}

			@Override
			public TypeToken<OT> getSortType() {
				return getParentElement().getInternalSortType();
			}

			public InterpretedValueSynth<SettableValue<?>, SettableValue<IT>> getAttribute() {
				return theAttribute;
			}

			@Override
			public void update(TypeToken<OT> type, InterpretedExpressoEnv env) throws ExpressoInterpretationException {
				env = env.with(getParentElement().getModels());
				if (getExpressoEnv() != null)
					theAttribute = interpret(getDefinition().getAttribute(), ModelTypes.Value.anyAsV());
				else
					theAttribute = getDefinition().getAttribute().interpret(ModelTypes.Value.anyAsV(), env);
				updateInternal((TypeToken<IT>) theAttribute.getType().getType(0), env);
			}

			@Override
			public BetterList<InterpretedValueSynth<?, ?>> getComponents() {
				return BetterList.of(Stream.concat(Stream.of(theAttribute), super.getComponents().stream()));
			}

			@Override
			protected SortInstantiator<OT, IT> doInstantiateSort() throws ModelInstantiationException {
				return new SortByInstantiator<>(getSortType(), getDefinition().getSortValue(), getDefinition().getSortCompareValue(),
					getSortWith() == null ? null : getSortWith().instantiate(), instantiateSortBy(), getDefaultSorting(),
						getDefinition().isAscending(), getExpressoEnv().getModels().instantiate(),
						getParentElement().getDefinition().getSortValue(), theAttribute.instantiate());
			}
		}

		static class SortByInstantiator<OT, IT> extends SortInstantiator<OT, IT> {
			private final ModelInstantiator theLocalModel;
			private final ModelComponentId theParentSortValue;
			private final ModelValueInstantiator<SettableValue<IT>> theAttribute;

			SortByInstantiator(TypeToken<OT> sortType, ModelComponentId sortValue, ModelComponentId sortCompareValue,
				ModelValueInstantiator<SettableValue<Integer>> sortWith, List<SortInstantiator<IT, ?>> sortBy,
				Comparator<? super IT> defaultSorting, boolean ascending, ModelInstantiator localModel, ModelComponentId parentSortValue,
				ModelValueInstantiator<SettableValue<IT>> attribute) {
				super(sortType, sortValue, sortCompareValue, sortWith, sortBy, defaultSorting, ascending);
				theLocalModel = localModel;
				theParentSortValue = parentSortValue;
				theAttribute = attribute;
			}

			@Override
			public void instantiate() throws ModelInstantiationException {
				theLocalModel.instantiate();
				theAttribute.instantiate();
			}

			@Override
			protected Supplier<Integer> getExternalSorting(ModelSetInstance parentModels, SettableValue<OT> left, SettableValue<OT> right)
				throws ModelInstantiationException {
				ModelSetInstance models = theLocalModel.wrap(parentModels);
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

	public static CompiledSorting getDefaultSorting(LocatedFilePosition position) {
		return new CompiledSorting() {
			@Override
			public <T> InterpretedValueSynth<SettableValue<?>, SettableValue<Comparator<T>>> evaluate(TypeToken<T> type)
				throws ExpressoInterpretationException {
				Comparator<T> compare = getDefaultSorting(TypeTokens.getRawType(TypeTokens.get().wrap(type)));
				if (compare != null)
					return InterpretedValueSynth.literalValue(TypeTokens.get().keyFor(Comparator.class).parameterized(type), compare,
						compare.toString());
				else
					throw new ExpressoInterpretationException(type + " is not Comparable, use either sort-with or sort-by", position, 0);
			}

		};
	}

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
