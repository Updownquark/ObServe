package org.observe.expresso.qonfig;

import java.util.Comparator;

import org.observe.Equivalence;
import org.observe.Observable;
import org.observe.ObservableValue;
import org.observe.SettableValue;
import org.observe.Transformation;
import org.observe.Transformation.ReversibleTransformation;
import org.observe.collect.ObservableCollection;
import org.observe.collect.ObservableCollectionImpl;
import org.observe.collect.ObservableSet;
import org.observe.collect.ObservableSetImpl;
import org.observe.collect.ObservableSortedCollection;
import org.observe.collect.ObservableSortedCollectionImpl;
import org.observe.collect.ObservableSortedSet;
import org.observe.collect.ObservableSortedSetImpl;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.InterpretedExpressoEnv;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ModelType;
import org.observe.expresso.ModelType.ModelInstanceType;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableModelSet.InterpretedValueSynth;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.qonfig.ElementTypeTraceability.SingleTypeTraceability;
import org.observe.expresso.qonfig.ExElement.Def;
import org.observe.expresso.qonfig.ExpressoTransformations.Operation;
import org.observe.expresso.qonfig.ExpressoTransformations.TypePreservingTransform;
import org.observe.expresso.qonfig.ExpressoTransformations.ValueTransform;
import org.observe.util.TypeTokens;
import org.qommons.LambdaUtils;
import org.qommons.ThreadConstraint;
import org.qommons.Transaction;
import org.qommons.collect.BetterList;
import org.qommons.collect.MutableCollectionElement.StdMsg;
import org.qommons.config.QonfigElement.QonfigValue;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretationException;
import org.qommons.config.QonfigInterpreterCore;
import org.qommons.io.LocatedPositionedContent;
import org.qommons.io.PositionedContent;

import com.google.common.reflect.TypeToken;

public class ObservableValueTransformations {
	private ObservableValueTransformations() {
	}

	public static void configureTransformation(QonfigInterpreterCore.Builder interpreter) {
		interpreter.createWith("disable", ValueTransform.class, ExElement.creator(DisabledValueTransform::new));
		interpreter.createWith("filter-accept", ValueTransform.class, ExElement.creator(FilterAcceptValueTransform::new));
		interpreter.createWith("map-to", ValueTransform.class, ExElement.creator(MapValueTransform::new));
		interpreter.createWith("refresh", ValueTransform.class, ExElement.creator(RefreshValueTransform::new));
		interpreter.createWith("unmodifiable", ValueTransform.class, ExElement.creator(UnmodifiableValueTransform::new));
		interpreter.createWith("flatten", ValueTransform.class, ExElement.creator(FlattenValueTransform::new));
	}

	static class DisabledValueTransform extends TypePreservingTransform<SettableValue<?>>
	implements ValueTransform<SettableValue<?>, ExElement> {
		private CompiledExpression theDisablement;

		DisabledValueTransform(Def<?> parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType);
		}

		public CompiledExpression getDisablement() {
			return theDisablement;
		}

		@Override
		public void update(ExpressoQIS session, ModelType<SettableValue<?>> sourceModelType) throws QonfigInterpretationException {
			super.update(session, sourceModelType);
			theDisablement = session.getAttributeExpression("with");
		}

		@Override
		protected Interpreted<?> tppInterpret(ExElement.Interpreted<?> parent) {
			return new Interpreted<>(this, parent);
		}

		static class Interpreted<T> extends TypePreservingTransform.Interpreted<SettableValue<?>, SettableValue<T>> implements
		ExpressoTransformations.Operation.EfficientCopyingInterpreted<SettableValue<?>, SettableValue<T>, SettableValue<?>, SettableValue<T>, ExElement> {
			private InterpretedValueSynth<SettableValue<?>, SettableValue<String>> theDisablement;

			Interpreted(DisabledValueTransform definition, ExElement.Interpreted<?> parent) {
				super(definition, parent);
			}

			@Override
			public DisabledValueTransform getDefinition() {
				return (DisabledValueTransform) super.getDefinition();
			}

			@Override
			public boolean isEfficientCopy() {
				return true;
			}

			@Override
			public void update(ModelInstanceType<SettableValue<?>, SettableValue<T>> sourceType, InterpretedExpressoEnv env)
				throws ExpressoInterpretationException {
				super.update(sourceType, env);
				theDisablement = ExpressoTransformations.parseFilter(getDefinition().getDisablement(), getExpressoEnv(), true);
			}

			@Override
			public BetterList<InterpretedValueSynth<?, ?>> getComponents() {
				return BetterList.of(theDisablement);
			}

			@Override
			public SettableValue<T> transform(SettableValue<T> source, ModelSetInstance models) throws ModelInstantiationException {
				SettableValue<String> disabled = theDisablement.get(models);
				return new DisabledValue<>(source, disabled);
			}

			@Override
			public boolean isDifferent(ModelSetInstance sourceModels, ModelSetInstance newModels) throws ModelInstantiationException {
				SettableValue<String> disabled = theDisablement.get(sourceModels);
				return disabled != theDisablement.forModelCopy(disabled, sourceModels, newModels);
			}

			@Override
			public SettableValue<T> getSource(SettableValue<T> value) {
				return ((DisabledValue<T>) value).getWrapped();
			}

			@Override
			public SettableValue<T> forModelCopy(SettableValue<T> prevValue, SettableValue<T> newSource, ModelSetInstance sourceModels,
				ModelSetInstance newModels) throws ModelInstantiationException {
				DisabledValue<T> dv = (DisabledValue<T>) prevValue;
				SettableValue<String> newDisabled = theDisablement.forModelCopy((SettableValue<String>) dv.getEnabled(), sourceModels,
					newModels);
				if (newSource == dv.getWrapped() && newDisabled == dv.getEnabled())
					return prevValue;
				else
					return new DisabledValue<>(newSource, newDisabled);
			}

			@Override
			public String toString() {
				return "disableWith(" + theDisablement + ")";
			}

			static class DisabledValue<T> extends SettableValue.DisabledValue<T> {
				DisabledValue(SettableValue<T> wrapped, ObservableValue<String> enabled) {
					super(wrapped, enabled);
				}

				@Override
				protected ObservableValue<String> getEnabled() {
					return super.getEnabled();
				}

				@Override
				protected SettableValue<T> getWrapped() {
					return super.getWrapped();
				}
			}
		}
	}

	static class FilterAcceptValueTransform extends TypePreservingTransform<SettableValue<?>>
	implements ValueTransform<SettableValue<?>, ExElement> {
		private String theSourceName;
		private CompiledExpression theTest;

		FilterAcceptValueTransform(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType);
		}

		public String getSourceName() {
			return theSourceName;
		}

		public CompiledExpression getTest() {
			return theTest;
		}

		@Override
		public void update(ExpressoQIS session, ModelType<SettableValue<?>> sourceModelType) throws QonfigInterpretationException {
			super.update(session, sourceModelType);
			theSourceName = session.getAttributeText("source-as");
			theTest = session.getAttributeExpression("test");
			getAddOn(ExWithElementModel.Def.class).<Interpreted<?>, SettableValue<?>> satisfyElementValueType(theSourceName,
				ModelTypes.Value, (interp, env) -> ModelTypes.Value.forType(interp.getSourceType()));
		}

		@Override
		protected Interpreted<?> tppInterpret(ExElement.Interpreted<?> parent) {
			return new Interpreted<>(this, parent);
		}

		static class Interpreted<T> extends TypePreservingTransform.Interpreted<SettableValue<?>, SettableValue<T>> implements
		ExpressoTransformations.Operation.EfficientCopyingInterpreted<SettableValue<?>, SettableValue<T>, SettableValue<?>, SettableValue<T>, ExElement> {
			private TypeToken<T> theSourceType;
			private InterpretedValueSynth<SettableValue<?>, SettableValue<String>> theTest;

			Interpreted(FilterAcceptValueTransform definition, ExElement.Interpreted<?> parent) {
				super(definition, parent);
			}

			@Override
			public FilterAcceptValueTransform getDefinition() {
				return (FilterAcceptValueTransform) super.getDefinition();
			}

			@Override
			public boolean isEfficientCopy() {
				return true;
			}

			public TypeToken<T> getSourceType() {
				return theSourceType;
			}

			@Override
			public void update(ModelInstanceType<SettableValue<?>, SettableValue<T>> sourceType, InterpretedExpressoEnv env)
				throws ExpressoInterpretationException {
				theSourceType = (TypeToken<T>) sourceType.getType(0);
				super.update(sourceType, env);
				theTest = ExpressoTransformations.parseFilter(getDefinition().getTest(), getExpressoEnv(), true);
			}

			@Override
			public BetterList<InterpretedValueSynth<?, ?>> getComponents() {
				return BetterList.of(theTest);
			}

			@Override
			public SettableValue<T> transform(SettableValue<T> source, ModelSetInstance models) throws ModelInstantiationException {
				SettableValue<T> sourceV = SettableValue.build(theSourceType).build();
				getAddOn(ExWithElementModel.Interpreted.class).satisfyElementValue(getDefinition().getSourceName(), models, sourceV);
				SettableValue<String> test = theTest.get(models);
				return new FilterEnabledValue<>(source, sourceV, test);
			}

			@Override
			public boolean isDifferent(ModelSetInstance sourceModels, ModelSetInstance newModels) throws ModelInstantiationException {
				SettableValue<String> test = theTest.get(sourceModels);
				return test != theTest.forModelCopy(test, sourceModels, newModels);
			}

			@Override
			public SettableValue<T> getSource(SettableValue<T> value) {
				return ((FilterEnabledValue<T>) value).getWrapped();
			}

			@Override
			public SettableValue<T> forModelCopy(SettableValue<T> prevValue, SettableValue<T> newSource, ModelSetInstance sourceModels,
				ModelSetInstance newModels) throws ModelInstantiationException {
				FilterEnabledValue<T> filtered = (FilterEnabledValue<T>) prevValue;
				SettableValue<String> newTest = theTest.forModelCopy(filtered.getTest(), sourceModels, newModels);
				if (newSource == filtered.getWrapped() && newTest == filtered.getTest())
					return prevValue;
				else {
					SettableValue<T> newSourceV = SettableValue.build(theSourceType).build();
					getAddOn(ExWithElementModel.Interpreted.class).satisfyElementValue(getDefinition().getSourceName(), newModels,
						newSourceV);
					return new FilterEnabledValue<>(newSource, newSourceV, newTest);
				}
			}

			@Override
			public String toString() {
				return "filterAccept(" + theTest + ")";
			}
		}

		static class FilterEnabledValue<T> extends SettableValue.WrappingSettableValue<T> {
			private final SettableValue<T> theSourceValue;
			private final SettableValue<String> theTest;

			FilterEnabledValue(SettableValue<T> wrapped, SettableValue<T> sourceValue, SettableValue<String> test) {
				super(wrapped);
				theSourceValue = sourceValue;
				theTest = test;
			}

			@Override
			protected SettableValue<T> getWrapped() {
				return super.getWrapped();
			}

			SettableValue<String> getTest() {
				return theTest;
			}
		}
	}

	static class MapValueTransform
	extends ExpressoTransformations.AbstractCompiledTransformation<SettableValue<?>, SettableValue<?>, ExElement>
	implements ValueTransform<SettableValue<?>, ExElement> {
		public MapValueTransform(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType);
		}

		@Override
		public ModelType<? extends SettableValue<?>> getTargetModelType() {
			return ModelTypes.Value;
		}

		@Override
		public ExpressoTransformations.CompiledTransformation.Interpreted<SettableValue<?>, ?, ?, ?, SettableValue<?>, ?, ? extends ExElement> interpret(
			ExElement.Interpreted<?> parent) throws ExpressoInterpretationException {
			return new Interpreted<>(this, parent);
		}

		static class Interpreted<S, T> extends
		ExpressoTransformations.AbstractCompiledTransformation.EfficientCopyingInterpreted<SettableValue<?>, S, T, SettableValue<S>, SettableValue<?>, SettableValue<T>, ExElement> {
			public Interpreted(MapValueTransform definition, ExElement.Interpreted<?> parent) {
				super(definition, parent);
			}

			@Override
			public MapValueTransform getDefinition() {
				return (MapValueTransform) super.getDefinition();
			}

			@Override
			public ModelInstanceType<? extends SettableValue<?>, ? extends SettableValue<T>> getTargetType() {
				return ModelTypes.Value.forType(getTargetValueType());
			}

			@Override
			public boolean isEfficientCopy() {
				return true;
			}

			@Override
			public SettableValue<T> transform(SettableValue<S> source, ModelSetInstance models) throws ModelInstantiationException {
				Transformation<S, T> transformation = transform(models);
				if (transformation instanceof Transformation.ReversibleTransformation)
					return new TransformedSettableValue<>(getTargetValueType(), source,
						(Transformation.ReversibleTransformation<S, T>) transformation);
				else
					return new TransformedUnsettableValue<>(getTargetValueType(), source, transformation);
			}

			@Override
			public SettableValue<S> getSource(SettableValue<T> value) {
				if (value instanceof TransformedSettableValue)
					return ((TransformedSettableValue<S, T>) value).getSource();
				else
					return ((TransformedUnsettableValue<S, T>) value).getSource();
			}
		}

		static class TransformedSettableValue<S, T> extends SettableValue.TransformedSettableValue<S, T> {
			TransformedSettableValue(TypeToken<T> type, SettableValue<S> source, ReversibleTransformation<S, T> combination) {
				super(type, source, combination);
			}

			@Override
			protected SettableValue<S> getSource() {
				return super.getSource();
			}

			@Override
			public Transformation<S, T> getTransformation() {
				return super.getTransformation();
			}
		}

		static class TransformedUnsettableValue<S, T> extends ObservableValue.TransformedObservableValue<S, T> implements SettableValue<T> {
			TransformedUnsettableValue(TypeToken<T> type, SettableValue<S> source, Transformation<S, T> transformation) {
				super(type, source, transformation);
			}

			@Override
			protected SettableValue<S> getSource() {
				return (SettableValue<S>) super.getSource();
			}

			@Override
			public Transformation<S, T> getTransformation() {
				return super.getTransformation();
			}

			@Override
			public Transaction lock(boolean write, Object cause) {
				return getSource().lock();
			}

			@Override
			public Transaction tryLock(boolean write, Object cause) {
				return getSource().tryLock();
			}

			@Override
			public ObservableValue<String> isEnabled() {
				return SettableValue.ALWAYS_DISABLED;
			}

			@Override
			public <V extends T> String isAcceptable(V value) {
				return StdMsg.UNSUPPORTED_OPERATION;
			}

			@Override
			public <V extends T> T set(V value, Object cause) throws IllegalArgumentException, UnsupportedOperationException {
				throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
			}
		}
	}

	static class RefreshValueTransform extends TypePreservingTransform<SettableValue<?>>
	implements ValueTransform<SettableValue<?>, ExElement> {
		private static final SingleTypeTraceability<ExElement, Interpreted<?>, RefreshValueTransform> TRACEABILITY = ElementTypeTraceability
			.getElementTraceability(ExpressoBaseV0_1.NAME, ExpressoBaseV0_1.VERSION, "refresh", RefreshValueTransform.class,
				Interpreted.class, null);
		private CompiledExpression theRefresh;

		RefreshValueTransform(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType);
		}

		@QonfigAttributeGetter("on")
		public CompiledExpression getRefresh() {
			return theRefresh;
		}

		@Override
		public void update(ExpressoQIS session, ModelType<SettableValue<?>> sourceModelType) throws QonfigInterpretationException {
			withTraceability(TRACEABILITY.validate(session.getFocusType(), session.reporting()));
			super.update(session, sourceModelType);
			theRefresh = session.getAttributeExpression("on");
		}

		@Override
		protected Interpreted<?> tppInterpret(ExElement.Interpreted<?> parent) {
			return new Interpreted<>(this, parent);
		}

		static class Interpreted<T> extends TypePreservingTransform.Interpreted<SettableValue<?>, SettableValue<T>> implements
		ExpressoTransformations.Operation.EfficientCopyingInterpreted<SettableValue<?>, SettableValue<T>, SettableValue<?>, SettableValue<T>, ExElement> {
			private InterpretedValueSynth<Observable<?>, Observable<?>> theRefresh;

			Interpreted(RefreshValueTransform definition, ExElement.Interpreted<?> parent) {
				super(definition, parent);
			}

			@Override
			public RefreshValueTransform getDefinition() {
				return (RefreshValueTransform) super.getDefinition();
			}

			public InterpretedValueSynth<Observable<?>, Observable<?>> getRefresh() {
				return theRefresh;
			}

			@Override
			public boolean isEfficientCopy() {
				return true;
			}

			@Override
			public boolean isDifferent(ModelSetInstance sourceModels, ModelSetInstance newModels) throws ModelInstantiationException {
				Observable<?> refresh = theRefresh.get(sourceModels);
				return refresh != theRefresh.forModelCopy(refresh, sourceModels, newModels);
			}

			@Override
			public void update(ModelInstanceType<SettableValue<?>, SettableValue<T>> sourceType, InterpretedExpressoEnv env)
				throws ExpressoInterpretationException {
				super.update(sourceType, env);
				theRefresh = getDefinition().getRefresh().interpret(ModelTypes.Event.any(), getExpressoEnv());
			}

			@Override
			public BetterList<InterpretedValueSynth<?, ?>> getComponents() {
				return BetterList.of(theRefresh);
			}

			@Override
			public SettableValue<T> transform(SettableValue<T> source, ModelSetInstance models) throws ModelInstantiationException {
				Observable<?> refresh = theRefresh.get(models);
				return new RefreshingValue<>(source, refresh);
			}

			@Override
			public SettableValue<T> getSource(SettableValue<T> value) {
				return ((RefreshingValue<T>) value).getWrapped();
			}

			@Override
			public SettableValue<T> forModelCopy(SettableValue<T> prevValue, SettableValue<T> newSource, ModelSetInstance sourceModels,
				ModelSetInstance newModels) throws ModelInstantiationException {
				RefreshingValue<T> refreshing = (RefreshingValue<T>) prevValue;
				Observable<?> newRefresh = theRefresh.forModelCopy(refreshing.getRefresh(), sourceModels, newModels);
				if (newSource == refreshing.getWrapped() && newRefresh == refreshing.getRefresh())
					return prevValue;
				else
					return new RefreshingValue<>(newSource, newRefresh);
			}

			@Override
			public String toString() {
				return "refresh(" + theRefresh + ")";
			}
		}

		static class RefreshingValue<T> extends SettableValue.RefreshingSettableValue<T> {
			RefreshingValue(SettableValue<T> wrap, Observable<?> refresh) {
				super(wrap, refresh);
			}

			@Override
			protected SettableValue<T> getWrapped() {
				return super.getWrapped();
			}

			@Override
			protected Observable<?> getRefresh() {
				return super.getRefresh();
			}
		}
	}

	static class UnmodifiableValueTransform extends TypePreservingTransform<SettableValue<?>>
	implements ValueTransform<SettableValue<?>, ExElement> {
		private boolean allowUpdates;

		UnmodifiableValueTransform(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType);
		}

		public boolean isAllowUpdates() {
			return allowUpdates;
		}

		@Override
		public void update(ExpressoQIS session, ModelType<SettableValue<?>> sourceModelType) throws QonfigInterpretationException {
			super.update(session, sourceModelType);
			allowUpdates = session.getAttribute("allow-updates", boolean.class);
		}

		@Override
		protected Interpreted<?> tppInterpret(ExElement.Interpreted<?> parent) {
			return new Interpreted<>(this, parent);
		}

		static class Interpreted<T> extends TypePreservingTransform.Interpreted<SettableValue<?>, SettableValue<T>> implements
		ExpressoTransformations.Operation.EfficientCopyingInterpreted<SettableValue<?>, SettableValue<T>, SettableValue<?>, SettableValue<T>, ExElement> {
			Interpreted(UnmodifiableValueTransform definition, ExElement.Interpreted<?> parent) {
				super(definition, parent);
			}

			@Override
			public UnmodifiableValueTransform getDefinition() {
				return (UnmodifiableValueTransform) super.getDefinition();
			}

			@Override
			public boolean isEfficientCopy() {
				return true;
			}

			@Override
			public BetterList<InterpretedValueSynth<?, ?>> getComponents() {
				return BetterList.empty();
			}

			@Override
			public SettableValue<T> transform(SettableValue<T> source, ModelSetInstance models) throws ModelInstantiationException {
				return new UnmodifiableValue<>(source, getDefinition().isAllowUpdates());
			}

			@Override
			public boolean isDifferent(ModelSetInstance sourceModels, ModelSetInstance newModels) throws ModelInstantiationException {
				return false;
			}

			@Override
			public SettableValue<T> getSource(SettableValue<T> value) {
				return ((UnmodifiableValue<T>) value).getWrapped();
			}

			@Override
			public SettableValue<T> forModelCopy(SettableValue<T> prevValue, SettableValue<T> newSource, ModelSetInstance sourceModels,
				ModelSetInstance newModels) throws ModelInstantiationException {
				UnmodifiableValue<T> unmodifiable = (UnmodifiableValue<T>) prevValue;
				if (newSource == unmodifiable.getWrapped())
					return prevValue;
				else
					return new UnmodifiableValue<>(newSource, unmodifiable.isAllowUpdates());
			}

			@Override
			public String toString() {
				return getDefinition().isAllowUpdates() ? "updateOnly" : "unmodifiable";
			}
		}

		static class UnmodifiableValue<T> extends SettableValue.WrappingSettableValue<T> {
			private final boolean allowUpdates;

			UnmodifiableValue(SettableValue<T> wrapped, boolean allowUpdates) {
				super(wrapped);
				this.allowUpdates = allowUpdates;
			}

			@Override
			protected SettableValue<T> getWrapped() {
				return super.getWrapped();
			}

			boolean isAllowUpdates() {
				return allowUpdates;
			}

			@Override
			public <V extends T> String isAcceptable(V value) {
				if (value == get())
					return null;
				return StdMsg.ILLEGAL_ELEMENT;
			}

			@Override
			public <V extends T> T set(V value, Object cause) throws IllegalArgumentException {
				if (value == get())
					return getWrapped().set(value, cause);
				else
					throw new IllegalArgumentException(StdMsg.ILLEGAL_ELEMENT);
			}

			@Override
			public String toString() {
				return allowUpdates ? "updateOnly" : "unmodifiable";
			}
		}
	}

	static class FlattenValueTransform<M> extends ExElement.Def.Abstract<ExElement> implements ValueTransform<M, ExElement> {
		private ModelType<M> theTargetType;
		private ExSort.ExRootSort theSorting;
		private CompiledExpression theEquivalence;
		private PositionedContent theEquivalencePosition;
		private boolean isCollection;

		FlattenValueTransform(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType);
		}

		@Override
		public ModelType<? extends M> getTargetModelType() {
			return theTargetType;
		}

		public ExSort.ExRootSort getSorting() {
			return theSorting;
		}

		public CompiledExpression getEquivalence() {
			return theEquivalence;
		}

		protected PositionedContent getEquivalencePosition() {
			return theEquivalencePosition;
		}

		public boolean isCollection() {
			return isCollection;
		}

		@Override
		public void update(ExpressoQIS session, ModelType<SettableValue<?>> sourceModelType) throws QonfigInterpretationException {
			super.update(session);

			QonfigValue pTP = session.getAttributeQV("propagate-to-parent"); // Defaulted to true, but warn if they specify it
			if (pTP.position != null) // Not defaulted, but specified
				reporting().at(pTP.position).warn("'propagate-update-to-parent' attribute not usable for value flattening");
			ExpressoQIS reverse = session.forChildren("reverse").peekFirst();
			if (reverse != null)
				reverse.reporting().warn("reverse is not usable for value flattening");

			theSorting = ExElement.useOrReplace(ExSort.ExRootSort.class, theSorting, session, "sort");
			theEquivalence = session.getAttributeExpression("equivalence");
			theEquivalencePosition = theEquivalence == null ? null : session.getAttributeValuePosition("equivalence");
			LocatedPositionedContent targetModelType = session.getAttributeValuePosition("to");
			theTargetType = (ModelType<M>) parseModelType(targetModelType);
			isCollection = ObservableCollection.class.isAssignableFrom(theTargetType.modelType);
		}

		protected ModelType<?> parseModelType(LocatedPositionedContent modelTypeName) throws QonfigInterpretationException {
			switch (modelTypeName.toString().toLowerCase()) {
			case "value":
				if (theSorting != null)
					theSorting.reporting().warn("Sorting specified, but not usable for value->value flattening");
				else if (theEquivalence != null)
					reporting().at(getEquivalencePosition()).warn("Equivalence specified, but not usable for value->value flattening");
				return ModelTypes.Value;
			case "list":
				if (theSorting != null)
					theSorting.reporting().warn("Sorting specified, but not usable for value->list flattening");
				return ModelTypes.SortedCollection;
			case "sorted-list":
				if (theEquivalence != null)
					reporting().at(getEquivalencePosition())
					.warn("Equivalence specified, but not usable for value->sorted-list flattening");
				return ModelTypes.SortedCollection;
			case "set":
				if (theSorting != null)
					theSorting.reporting().warn("Sorting specified, but not usable for value->set flattening");
				return ModelTypes.SortedSet;
			case "sorted-set":
				if (theEquivalence != null)
					reporting().at(getEquivalencePosition()).warn("Equivalence specified, but not usable for value->sorted-set flattening");
				return ModelTypes.SortedSet;
			case "event":
			case "action":
			case "value-set":
			case "map":
			case "sorted-map":
			case "multi-map":
			case "sorted-multi-map":
				throw new QonfigInterpretationException("Unsupported value flatten target: '" + modelTypeName + "'",
					modelTypeName.getPosition(0), modelTypeName.length());
			default:
				throw new QonfigInterpretationException("Unrecognized model type target: '" + modelTypeName + "'",
					modelTypeName.getPosition(0), modelTypeName.length());
			}
		}

		@Override
		public Operation.Interpreted<SettableValue<?>, ?, M, ?, ? extends ExElement> interpret(ExElement.Interpreted<?> parent)
			throws ExpressoInterpretationException {
			return interpret(theTargetType, parent);
		}

		protected Interpreted<M, ?> interpret(ModelType<M> targetType, ExElement.Interpreted<?> parent)
			throws ExpressoInterpretationException {
			if (theTargetType == ModelTypes.Value)
				return (Interpreted<M, ?>) new FlattenedValueInterpretation<>((FlattenValueTransform<SettableValue<?>>) this, parent);
			else if (isCollection())
				return (Interpreted<M, ?>) new FlattenedCollectionValueInterpretation<>(
					(FlattenValueTransform<? extends ObservableSortedCollection<?>>) this, parent);
			else
				throw new ExpressoInterpretationException("Unsupported value flatten target: '" + targetType + "'",
					reporting().getFileLocation().getPosition(0), 0);
		}

		static abstract class Interpreted<M, MV extends M> extends ExElement.Interpreted.Abstract<ExElement>
		implements Operation.EfficientCopyingInterpreted<SettableValue<?>, SettableValue<?>, M, MV, ExElement> {
			Interpreted(FlattenValueTransform<M> definition, ExElement.Interpreted<?> parent) {
				super(definition, parent);
			}

			@Override
			public FlattenValueTransform<M> getDefinition() {
				return (FlattenValueTransform<M>) super.getDefinition();
			}

			@Override
			public boolean isEfficientCopy() {
				return true;
			}

			@Override
			public String toString() {
				return "flat(" + getDefinition().getTargetModelType() + ")";
			}
		}

		protected static class FlattenedValueInterpretation<T> extends Interpreted<SettableValue<?>, SettableValue<T>> {
			private boolean isSettable;
			private TypeToken<T> theValueType;

			protected FlattenedValueInterpretation(FlattenValueTransform<SettableValue<?>> definition, ExElement.Interpreted<?> parent) {
				super(definition, parent);
			}

			public boolean isSettable() {
				return isSettable;
			}

			public TypeToken<T> getValueType() {
				return theValueType;
			}

			@Override
			public ModelInstanceType<? extends SettableValue<?>, ? extends SettableValue<T>> getTargetType() {
				return ModelTypes.Value.forType(theValueType);
			}

			@Override
			public void update(ModelInstanceType<SettableValue<?>, SettableValue<?>> sourceType, InterpretedExpressoEnv env)
				throws ExpressoInterpretationException {
				super.update(env);
				TypeToken<?> valueType = sourceType.getType(0);
				Class<?> rawType = TypeTokens.getRawType(valueType);
				isSettable = SettableValue.class.isAssignableFrom(rawType);
				if (!isSettable && !ObservableValue.class.isAssignableFrom(rawType))
					throw new ExpressoInterpretationException("Cannot flatten type " + valueType + " to a value",
						reporting().getFileLocation().getPosition(0), 0);
				theValueType = (TypeToken<T>) valueType.resolveType(ObservableValue.class.getTypeParameters()[0]);
			}

			@Override
			public BetterList<InterpretedValueSynth<?, ?>> getComponents() {
				return BetterList.empty();
			}

			@Override
			public SettableValue<T> transform(SettableValue<?> source, ModelSetInstance models) throws ModelInstantiationException {
				if (source == null)
					return new WrappedSettableValue<>((SettableValue<? extends ObservableValue<? extends T>>) source,
						SettableValue.of(theValueType, null, StdMsg.UNSUPPORTED_OPERATION));
				else if (source.getThreadConstraint() == ThreadConstraint.NONE) {
					if (source.get() == null)
						return new WrappedSettableValue<>((SettableValue<? extends ObservableValue<? extends T>>) source,
							SettableValue.of(theValueType, null, StdMsg.UNSUPPORTED_OPERATION));
					else if (isSettable)
						return new WrappedSettableValue<>((SettableValue<? extends ObservableValue<? extends T>>) source,
							(SettableValue<T>) source.get());
					else
						return new AsSettableValue<>((SettableValue<? extends ObservableValue<? extends T>>) source,
							(ObservableValue<T>) source.get());
				} else
					return new FlattenedSettableValue<>((SettableValue<? extends SettableValue<? extends T>>) source);
			}

			@Override
			public boolean isDifferent(ModelSetInstance sourceModels, ModelSetInstance newModels) throws ModelInstantiationException {
				return false;
			}

			@Override
			public SettableValue<?> getSource(SettableValue<T> value) {
				if (value instanceof WrappedSettableValue)
					return ((WrappedSettableValue<T>) value).getSourceValue();
				else if (value instanceof AsSettableValue)
					return ((AsSettableValue<T>) value).getSourceValue();
				else if (value instanceof FlattenedSettableValue)
					return ((FlattenedSettableValue<T>) value).getWrapped();
				else
					return null;
			}

			@Override
			public SettableValue<T> forModelCopy(SettableValue<T> prevValue, SettableValue<?> newSource, ModelSetInstance sourceModels,
				ModelSetInstance newModels) throws ModelInstantiationException {
				if (getSource(prevValue) == newSource)
					return prevValue;
				else
					return transform(newSource, newModels);
			}

			static class WrappedSettableValue<T> extends SettableValue.WrappingSettableValue<T> {
				private final SettableValue<? extends ObservableValue<? extends T>> theSourceValue;

				public WrappedSettableValue(SettableValue<? extends ObservableValue<? extends T>> sourceValue, SettableValue<T> wrapped) {
					super(wrapped);
					theSourceValue = sourceValue;
				}

				public SettableValue<? extends ObservableValue<? extends T>> getSourceValue() {
					return theSourceValue;
				}

				@Override
				public String toString() {
					return theSourceValue + ".flat()";
				}
			}

			static class AsSettableValue<T> extends SettableValue.AlwaysDisabledValue<T> implements SettableValue<T> {
				private final SettableValue<? extends ObservableValue<? extends T>> theSourceValue;

				AsSettableValue(SettableValue<? extends ObservableValue<? extends T>> sourceValue, ObservableValue<T> value) {
					super(value, __ -> StdMsg.UNSUPPORTED_OPERATION);
					theSourceValue = sourceValue;
				}

				SettableValue<? extends ObservableValue<? extends T>> getSourceValue() {
					return theSourceValue;
				}

				@Override
				public String toString() {
					return getValue() + ".flat()";
				}
			}

			static class FlattenedSettableValue<T> extends SettableValue.SettableFlattenedObservableValue<T> {
				FlattenedSettableValue(SettableValue<? extends ObservableValue<? extends T>> value) {
					super(value, LambdaUtils.constantSupplier(null, "null", null));
				}

				@Override
				protected SettableValue<? extends ObservableValue<? extends T>> getWrapped() {
					return (SettableValue<? extends ObservableValue<? extends T>>) super.getWrapped();
				}

				@Override
				public String toString() {
					return getWrapped() + ".flat()";
				}
			}
		}

		protected static class FlattenedCollectionValueInterpretation<T, C extends ObservableCollection<?>, CV extends C>
		extends Interpreted<C, CV> {
			private TypeToken<T> theValueType;
			private ModelInstanceType<C, CV> theModelType;
			private ExSort.ExRootSort.Interpreted<T> theSorting;
			private Comparator<? super T> theDefaultSorting;
			private InterpretedValueSynth<SettableValue<?>, SettableValue<Equivalence<? super T>>> theEquivalence;

			protected FlattenedCollectionValueInterpretation(FlattenValueTransform<C> definition, ExElement.Interpreted<?> parent) {
				super(definition, parent);
			}

			@Override
			public void update(ModelInstanceType<SettableValue<?>, SettableValue<?>> sourceType, InterpretedExpressoEnv env)
				throws ExpressoInterpretationException {
				super.update(env);
				ModelType<C> modelType = (ModelType<C>) getDefinition().getTargetModelType();
				TypeToken<?> valueType = sourceType.getType(0);
				Class<?> rawType = TypeTokens.getRawType(valueType);
				if (!modelType.modelType.isAssignableFrom(rawType))
					throw new ExpressoInterpretationException("Cannot flatten type " + valueType + " to a " + modelType,
						reporting().getFileLocation().getPosition(0), 0);
				theValueType = (TypeToken<T>) valueType.resolveType(ObservableCollection.class.getTypeParameters()[0]);
				theModelType = (ModelInstanceType<C, CV>) modelType.forTypes(theValueType);
				if (modelType == ModelTypes.Collection || modelType == ModelTypes.Set) {//
					theEquivalence = getDefinition().getEquivalence() == null ? null : getDefinition().getEquivalence().interpret(//
						ModelTypes.Value.forType(TypeTokens.get().keyFor(Equivalence.class)
							.<Equivalence<? super T>> parameterized(TypeTokens.get().getSuperWildcard(theValueType))),
						getExpressoEnv());
					if (theSorting != null)
						theSorting.destroy();
					theSorting = null;
				} else if (modelType == ModelTypes.SortedCollection || modelType == ModelTypes.SortedSet) {//
					theEquivalence = null;
					theSorting = getDefinition().getSorting() == null ? null
						: (ExSort.ExRootSort.Interpreted<T>) getDefinition().getSorting().interpret(this);
					if (theSorting == null) {
						theDefaultSorting = ExSort.getDefaultSorting(TypeTokens.getRawType(theValueType));
						if (theDefaultSorting == null)
							throw new ExpressoInterpretationException(
								"No sorting specified and no default sorting available for type " + theValueType,
								reporting().getFileLocation().getPosition(0), 0);
					} else
						theDefaultSorting = null;
				} else
					throw new ExpressoInterpretationException("Unrecognized collection type: " + modelType,
						reporting().getFileLocation().getPosition(0), 0);
			}

			@Override
			public ModelInstanceType<? extends C, ? extends CV> getTargetType() {
				return theModelType;
			}

			@Override
			public CV transform(SettableValue<?> source, ModelSetInstance models) throws ModelInstantiationException {
				Equivalence<? super T> equivalence = theEquivalence == null ? null : theEquivalence.get(models).get();
				if (equivalence == null)
					equivalence = Equivalence.DEFAULT;
				Comparator<? super T> sorting = theSorting == null ? theDefaultSorting : theSorting.getSorting(models);

				ModelType<C> modelType = (ModelType<C>) getDefinition().getTargetModelType();
				if (modelType == ModelTypes.Collection)
					return (CV) new FlattenedCollection<>((SettableValue<? extends ObservableCollection<? extends T>>) source, equivalence);
				else if (modelType == ModelTypes.Set)
					return (CV) new FlattenedSet<>((SettableValue<? extends ObservableSet<? extends T>>) source, equivalence);
				else if (modelType == ModelTypes.SortedCollection)
					return (CV) new FlattenedSortedCollection<>((SettableValue<? extends ObservableSortedCollection<? extends T>>) source,
						sorting);
				else if (modelType == ModelTypes.SortedSet)
					return (CV) new FlattenedSortedSet<>((SettableValue<? extends ObservableSortedSet<? extends T>>) source, sorting);
				else
					throw new IllegalStateException("Unrecognized collection type: " + modelType);
			}

			@Override
			public boolean isDifferent(ModelSetInstance sourceModels, ModelSetInstance newModels) throws ModelInstantiationException {
				if (theEquivalence != null && theEquivalence.get(sourceModels) != theEquivalence.get(newModels))
					return true;
				if (theSorting != null && theSorting.getSorting(sourceModels) != theSorting.getSorting(newModels))
					return true;
				return false;
			}

			@Override
			public SettableValue<?> getSource(CV value) {
				return ((FlattenedCollectionValue<?, ?>) value).getWrapped();
			}

			@Override
			public BetterList<InterpretedValueSynth<?, ?>> getComponents() {
				if (theSorting != null)
					return theSorting.getComponents();
				else if (theEquivalence != null)
					return BetterList.of(theEquivalence);
				else
					return BetterList.empty();
			}

			@Override
			public CV forModelCopy(CV prevValue, SettableValue<?> newSource, ModelSetInstance sourceModels, ModelSetInstance newModels)
				throws ModelInstantiationException {
				if (newSource != getSource(prevValue))
					return transform(newSource, newModels);
				if (theEquivalence != null) {
					Equivalence<? super T> equivalence = theEquivalence.get(newModels).get();
					if (equivalence == null)
						equivalence = Equivalence.DEFAULT;
					if (equivalence != prevValue.equivalence())
						return transform(newSource, newModels);
				}
				if (theSorting != null && theSorting.getSorting(newModels) != ((ObservableSortedCollection<?>) prevValue).comparator())
					return transform(newSource, newModels);
				return prevValue;
			}

			interface FlattenedCollectionValue<T, C extends ObservableCollection<? extends T>> extends ObservableCollection<T> {
				SettableValue<? extends C> getWrapped();
			}

			static class FlattenedCollection<T> extends ObservableCollectionImpl.FlattenedValueCollection<T>
			implements FlattenedCollectionValue<T, ObservableCollection<? extends T>> {
				FlattenedCollection(SettableValue<? extends ObservableCollection<? extends T>> collectionObservable,
					Equivalence<? super T> equivalence) {
					super(collectionObservable, equivalence);
				}

				@Override
				public SettableValue<? extends ObservableCollection<? extends T>> getWrapped() {
					return (SettableValue<? extends ObservableCollection<? extends T>>) super.getWrapped();
				}
			}

			static class FlattenedSet<T> extends ObservableSetImpl.FlattenedValueSet<T>
			implements FlattenedCollectionValue<T, ObservableSet<? extends T>> {
				FlattenedSet(SettableValue<? extends ObservableSet<T>> collectionObservable, Equivalence<? super T> equivalence) {
					super(collectionObservable, equivalence);
				}

				@Override
				public SettableValue<? extends ObservableSet<T>> getWrapped() {
					return (SettableValue<? extends ObservableSet<T>>) super.getWrapped();
				}
			}

			static class FlattenedSortedCollection<T> extends ObservableSortedCollectionImpl.FlattenedValueSortedCollection<T>
			implements FlattenedCollectionValue<T, ObservableSortedCollection<? extends T>> {
				FlattenedSortedCollection(SettableValue<? extends ObservableSortedCollection<? extends T>> collectionObservable,
					Comparator<? super T> compare) {
					super(collectionObservable, compare);
				}

				@Override
				public SettableValue<? extends ObservableSortedCollection<T>> getWrapped() {
					return (SettableValue<? extends ObservableSortedCollection<T>>) super.getWrapped();
				}
			}

			static class FlattenedSortedSet<T> extends ObservableSortedSetImpl.FlattenedValueSortedSet<T>
			implements FlattenedCollectionValue<T, ObservableSortedSet<T>> {
				FlattenedSortedSet(SettableValue<? extends ObservableSortedSet<? extends T>> collectionObservable,
					Comparator<? super T> compare) {
					super(collectionObservable, compare);
				}

				@Override
				public SettableValue<? extends ObservableSortedSet<T>> getWrapped() {
					return (SettableValue<? extends ObservableSortedSet<T>>) super.getWrapped();
				}
			}
		}
	}
}
