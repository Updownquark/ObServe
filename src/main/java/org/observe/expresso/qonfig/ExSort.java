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
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.qonfig.ElementTypeTraceability.SingleTypeTraceability;
import org.observe.util.TypeTokens;
import org.qommons.LambdaUtils;
import org.qommons.StringUtils;
import org.qommons.collect.BetterList;
import org.qommons.collect.CollectionUtils;
import org.qommons.config.QonfigElement;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretationException;
import org.qommons.io.LocatedFilePosition;
import org.qommons.io.LocatedPositionedContent;

import com.google.common.reflect.TypeToken;

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

	private static final SingleTypeTraceability<ExElement, Interpreted<?, ?>, ExSort> TRACEABILITY = ElementTypeTraceability
		.getElementTraceability(ExpressoBaseV0_1.NAME, ExpressoBaseV0_1.VERSION, "sort", ExSort.class, Interpreted.class, null);

	private String theSortValueName;
	private String theSortCompareValueName;
	private CompiledExpression theSortWith;
	private final List<ExSortBy> theSortBy;
	private boolean isAscending;

	protected ExSort(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
		super(parent, qonfigType);
		theSortBy = new ArrayList<>();
	}

	@QonfigAttributeGetter("sort-value-as")
	public String getSortValueName() {
		return theSortValueName;
	}

	@QonfigAttributeGetter("sort-compare-value-as")
	public String getSortCompareValueName() {
		return theSortCompareValueName;
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
		withTraceability(TRACEABILITY.validate(session.getFocusType(), session.reporting()));
		super.doUpdate(session.asElement(session.getFocusType().getSuperElement()));
		theSortValueName = session.getAttributeText("sort-value-as");
		LocatedPositionedContent sortValueNamePosition = session.getAttributeValuePosition("sort-value-as");
		theSortCompareValueName = session.getAttributeText("sort-compare-value-as");
		LocatedPositionedContent sortCompareValueNamePosition = session.getAttributeValuePosition("sort-compare-value-as");
		theSortWith = session.getAttributeExpression("sort-with");
		isAscending = session.getAttribute("ascending", boolean.class);
		ExElement.syncDefs(ExSortBy.class, theSortBy, session.forChildren("sort-by"));

		if (theSortWith != null) {
			if (!theSortBy.isEmpty())
				reporting().error("sort-with or sort-by may be used, but not both");
			if (theSortValueName == null)
				throw new QonfigInterpretationException("sort-with must be used with sort-value-as",
					theSortWith.getElement().getPositionInFile(), 0);
			else if (theSortCompareValueName == null)
				throw new QonfigInterpretationException("sort-with must be used with sort-compare-value-as",
					theSortWith.getElement().getPositionInFile(), 0);
		} else if (!theSortBy.isEmpty()) {
			if (theSortValueName == null)
				throw new QonfigInterpretationException("sort-by must be used with sort-value-as", getElement().getPositionInFile(), 0);
			if (theSortCompareValueName != null)
				reporting().at(sortCompareValueNamePosition).warn("sort-compare-value-as is not used with sort-by");
		} else {
			if (theSortValueName != null)
				reporting().at(sortValueNamePosition).warn("sort-value-as is not used with default sorting");
			if (theSortCompareValueName != null)
				reporting().at(sortCompareValueNamePosition).warn("sort-compare-value-as is not used with default sorting");
		}

		if (theSortValueName != null || theSortCompareValueName != null) {
			ExWithElementModel.Def withElModel = getAddOn(ExWithElementModel.Def.class);
			if (theSortValueName != null)
				withElModel.<Interpreted<?, ?>, SettableValue<?>> satisfyElementValueType(theSortValueName, ModelTypes.Value,
					(interp, env) -> ModelTypes.Value.forType(interp.getSortType()));
			if (theSortCompareValueName != null)
				withElModel.<Interpreted<?, ?>, SettableValue<?>> satisfyElementValueType(theSortCompareValueName, ModelTypes.Value,
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
			theSortWith = getDefinition().getSortWith() == null ? null
				: getDefinition().getSortWith().interpret(ModelTypes.Value.INT, getExpressoEnv());
			CollectionUtils.synchronize(theSortBy, getDefinition().getSortBy(), (i, d) -> i.getDefinition() == d)//
			.<ExpressoInterpretationException> simpleE(d -> (ExSortBy.Interpreted<IT, ?>) d.interpret(this))//
			.onLeftX(el -> el.getLeftValue().destroy())//
			.onRightX(el -> el.getLeftValue().update(internalType, getExpressoEnv()))//
			.onCommonX(el -> el.getLeftValue().update(internalType, getExpressoEnv()))//
			.adjust();
			if (theSortWith == null && theSortBy.isEmpty()) {
				Class<IT> raw = TypeTokens.getRawType(internalType);
				theDefaultSorting = ExSort.getDefaultSorting(raw);
				if (theDefaultSorting == null)
					throw new ExpressoInterpretationException(internalType + " is not Comparable, use either sort-with or sort-by",
						reporting().getFileLocation().getPosition(0), 0);
			} else
				theDefaultSorting = null;
		}

		public Comparator<? super OT> getSorting(ModelSetInstance models) throws ModelInstantiationException {
			SettableValue<OT> left = SettableValue.build(getSortType()).withDescription(getDefinition().getSortValueName())
				.withValue(TypeTokens.get().getDefaultValue(getSortType())).build();
			SettableValue<OT> right = SettableValue.build(getSortType()).withDescription(getDefinition().getSortValueName())
				.withValue(TypeTokens.get().getDefaultValue(getSortType())).build();
			Supplier<Integer> sorting = getExternalSorting(models, left, right);
			return new SortWithComparator<>(left, right, sorting, getDefinition().isAscending());
		}

		public BetterList<InterpretedValueSynth<?, ?>> getComponents() {
			if (theSortWith != null)
				return BetterList.of(theSortWith);
			else
				return BetterList.of(theSortBy.stream().flatMap(sb -> sb.getComponents().stream()));
		}

		protected abstract Supplier<Integer> getExternalSorting(ModelSetInstance parentModels, SettableValue<OT> left,
			SettableValue<OT> right) throws ModelInstantiationException;

		protected Supplier<Integer> getInternalSorting(ModelSetInstance models, SettableValue<IT> left, SettableValue<IT> right)
			throws ModelInstantiationException {
			if (theSortWith != null) {
				ExWithElementModel.Interpreted withElModel = getAddOn(ExWithElementModel.Interpreted.class);
				withElModel.satisfyElementValue(getDefinition().getSortValueName(), models, left);
				withElModel.satisfyElementValue(getDefinition().getSortCompareValueName(), models, right);
				return theSortWith.get(models);
			} else if (!theSortBy.isEmpty()) {
				Supplier<Integer>[] sortBy = new Supplier[theSortBy.size()];
				int i = 0;
				for (ExSortBy.Interpreted<IT, ?> sb : theSortBy)
					sortBy[i++] = sb.getExternalSorting(models, left, right);
				return new CompositeIntSupplier(sortBy);
			} else
				return new DefaultSortSupplier<>(left, right, theDefaultSorting);
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
			public Comparator<? super T> getSorting(ModelSetInstance models) throws ModelInstantiationException {
				if (getDefaultSorting() != null)
					return getDefaultSorting();
				return super.getSorting(models);
			}

			@Override
			protected Supplier<Integer> getExternalSorting(ModelSetInstance parentModels, SettableValue<T> left, SettableValue<T> right)
				throws ModelInstantiationException {
				ModelSetInstance models = getExpressoEnv().wrapLocal(parentModels);
				return getInternalSorting(models, left, right);
			}
		}
	}

	public static class ExSortBy extends ExSort {
		private static final SingleTypeTraceability<ExElement, Interpreted<?, ?>, ExSortBy> TRACEABILITY = ElementTypeTraceability
			.getElementTraceability(ExpressoBaseV0_1.NAME, ExpressoBaseV0_1.VERSION, "sort-by", ExSort.class, Interpreted.class, null);
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
			withTraceability(TRACEABILITY.validate(session.getFocusType(), session.reporting()));
			super.doUpdate(session.asElement(session.getFocusType().getSuperElement()));
			theAttribute = session.getValueExpression();
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
				theAttribute = getDefinition().getAttribute().interpret(ModelTypes.Value.<SettableValue<IT>> anyAs(), env);
				updateInternal((TypeToken<IT>) theAttribute.getType().getType(0), env);
			}

			@Override
			protected Supplier<Integer> getExternalSorting(ModelSetInstance parentModels, SettableValue<OT> left, SettableValue<OT> right)
				throws ModelInstantiationException {
				ModelSetInstance models = getExpressoEnv().wrapLocal(parentModels);
				ModelSetInstance leftCopy = models.copy().build();
				ExWithElementModel.Interpreted withElModel = getParentElement().getAddOn(ExWithElementModel.Interpreted.class);
				withElModel.satisfyElementValue(getParentElement().getDefinition().getSortValueName(), leftCopy, left,
					ExWithElementModel.ActionIfSatisfied.Replace);
				ModelSetInstance rightCopy = models.copy().build();
				withElModel.satisfyElementValue(getParentElement().getDefinition().getSortValueName(), rightCopy, right,
					ExWithElementModel.ActionIfSatisfied.Replace);
				SettableValue<IT> internalLeft = theAttribute.get(leftCopy);
				SettableValue<IT> internalRight = theAttribute.get(rightCopy);
				return getInternalSorting(models, internalLeft, internalRight);
			}

			@Override
			public BetterList<InterpretedValueSynth<?, ?>> getComponents() {
				return BetterList.of(Stream.concat(Stream.of(theAttribute), super.getComponents().stream()));
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
					return InterpretedValueSynth.literal(TypeTokens.get().keyFor(Comparator.class).parameterized(type), compare,
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