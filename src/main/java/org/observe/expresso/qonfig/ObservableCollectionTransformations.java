package org.observe.expresso.qonfig;

import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

import org.observe.Equivalence;
import org.observe.Observable;
import org.observe.ObservableValue;
import org.observe.Observer;
import org.observe.SettableValue;
import org.observe.Subscription;
import org.observe.Transformation;
import org.observe.assoc.ObservableMultiMap;
import org.observe.collect.ObservableCollection;
import org.observe.collect.ObservableCollection.CollectionDataFlow;
import org.observe.collect.ObservableCollection.DistinctDataFlow;
import org.observe.collect.ObservableCollection.SortedDataFlow;
import org.observe.collect.ObservableSet;
import org.observe.collect.ObservableSortedCollection;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.InterpretedExpressoEnv;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ModelType;
import org.observe.expresso.ModelType.ModelInstanceType;
import org.observe.expresso.ModelType.ModelInstanceType.SingleTyped;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableModelSet.InterpretedValueSynth;
import org.observe.expresso.ObservableModelSet.ModelComponentId;
import org.observe.expresso.ObservableModelSet.ModelInstantiator;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.ObservableModelSet.ModelValueInstantiator;
import org.observe.expresso.VariableType;
import org.observe.expresso.qonfig.ExElement.Def;
import org.observe.expresso.qonfig.ExpressoTransformations.AbstractCompiledTransformation;
import org.observe.expresso.qonfig.ExpressoTransformations.CollectionTransform;
import org.observe.expresso.qonfig.ExpressoTransformations.FlowTransformInstantiator;
import org.observe.expresso.qonfig.ExpressoTransformations.Operation;
import org.observe.expresso.qonfig.ExpressoTransformations.TypePreservingTransform;
import org.observe.util.TypeTokens;
import org.qommons.Causable;
import org.qommons.Identifiable;
import org.qommons.LambdaUtils;
import org.qommons.QommonsUtils;
import org.qommons.collect.BetterList;
import org.qommons.config.QonfigElement.QonfigValue;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretationException;
import org.qommons.config.QonfigInterpreterCore;
import org.qommons.config.QonfigValueType;
import org.qommons.ex.CheckedExceptionWrapper;
import org.qommons.io.LocatedFilePosition;
import org.qommons.io.LocatedPositionedContent;

import com.google.common.reflect.TypeToken;

public class ObservableCollectionTransformations {
	private ObservableCollectionTransformations() {
	}

	public static void configureTransformation(QonfigInterpreterCore.Builder interpreter) {
		interpreter.createWith("map-to", CollectionTransform.class, ExElement.creator(MapCollectionTransform::new));
		interpreter.createWith("filter", CollectionTransform.class, ExElement.creator(FilterCollectionTransform::new));
		interpreter.createWith("filter-by-type", CollectionTransform.class, ExElement.creator(TypeFilteredCollectionTransform::new));
		interpreter.createWith("reverse", CollectionTransform.class, ExElement.creator(ReverseCollectionTransform::new));
		interpreter.createWith("refresh", CollectionTransform.class, ExElement.creator(RefreshCollectionTransform::new));
		interpreter.createWith("refresh-each", CollectionTransform.class, ExElement.creator(RefreshEachCollectionTransform::new));
		interpreter.createWith("distinct", CollectionTransform.class, ExElement.creator(DistinctCollectionTransform::new));
		interpreter.createWith("sort", CollectionTransform.class, ExElement.creator(SortedCollectionTransform::new));
		interpreter.createWith("unmodifiable", CollectionTransform.class, ExElement.creator(UnmodifiableCollectionTransform::new));
		interpreter.createWith("filter-mod", CollectionTransform.class, ExElement.creator(FilterModCollectionTransform::new));
		interpreter.createWith("map-equivalent", CollectionTransform.class, ExElement.creator(MapEquivalentCollectionTransform::new));
		interpreter.createWith("flatten", CollectionTransform.class, ExElement.creator(FlattenCollectionTransform::new));
		interpreter.createWith("cross", CollectionTransform.class, ExElement.creator(CrossCollectionTransform::new));
		interpreter.createWith("where-contained", CollectionTransform.class, ExElement.creator(WhereContainedCollectionTransform::new));
		interpreter.createWith("group-by", CollectionTransform.class, ExElement.creator(GroupByCollectionTransform::new));
		interpreter.createWith("size", CollectionTransform.class, ExElement.creator(SizeCollectionTransform::new));
		interpreter.createWith("collect", CollectionTransform.class, ExElement.creator(CollectCollectionTransform::new));

		// TODO Probably should support value-set transformations here, just grabbing the values and returning a collection
		// This can always be overridden later
	}

	static class MapCollectionTransform<C1 extends ObservableCollection<?>, C2 extends ObservableCollection<?>> extends
	ExpressoTransformations.AbstractCompiledTransformation<C1, C2, ExElement> implements CollectionTransform<C1, C2, ExElement> {
		private ModelType<C1> theSourceType;
		private ModelType<C2> theTargetType;

		MapCollectionTransform(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType);
		}

		public ModelType<C1> getSourceType() {
			return theSourceType;
		}

		@Override
		public ModelType<? extends C2> getTargetModelType() {
			return theTargetType;
		}

		@Override
		public void update(ExpressoQIS session, ModelType<C1> sourceModelType) throws QonfigInterpretationException {
			theSourceType = sourceModelType;
			super.update(session, sourceModelType);
			if (sourceModelType == ModelTypes.Collection)
				theTargetType = (ModelType<C2>) sourceModelType;
			else if (sourceModelType == ModelTypes.Set || sourceModelType == ModelTypes.SortedCollection
				|| sourceModelType == ModelTypes.SortedSet) {
				if (isReversible())
					theTargetType = (ModelType<C2>) sourceModelType;
				else
					theTargetType = (ModelType<C2>) ModelTypes.Collection;
			} else
				throw new QonfigInterpretationException("Unrecognized source collection type: " + sourceModelType,
					reporting().getFileLocation().getPosition(0), 0);
		}

		@Override
		public ExpressoTransformations.CompiledTransformation.Interpreted<C1, ?, ?, ?, C2, ?, ? extends ExElement> interpret(
			ExElement.Interpreted<?> parent) throws ExpressoInterpretationException {
			return new Interpreted<>(this, parent);
		}

		static class Interpreted<C1 extends ObservableCollection<?>, S, T, CV1 extends C1, C2 extends ObservableCollection<?>, CV2 extends C2>
		extends ExpressoTransformations.AbstractCompiledTransformation.Interpreted<C1, S, T, CV1, C2, CV2, ExElement> {
			private ModelInstanceType<C2, CV2> theTargetModelType;

			Interpreted(MapCollectionTransform<C1, C2> definition, ExElement.Interpreted<?> parent) {
				super(definition, parent);
			}

			@Override
			public MapCollectionTransform<C1, C2> getDefinition() {
				return (MapCollectionTransform<C1, C2>) super.getDefinition();
			}

			@Override
			public void update(ModelInstanceType<C1, CV1> sourceType, InterpretedExpressoEnv env) throws ExpressoInterpretationException {
				super.update(sourceType, env);
				theTargetModelType = (ModelInstanceType<C2, CV2>) getDefinition().getTargetModelType().forTypes(getTargetValueType());
			}

			@Override
			public ModelInstanceType<? extends C2, ? extends CV2> getTargetType() {
				return theTargetModelType;
			}

			@Override
			public ExpressoTransformations.CompiledTransformation.Instantiator<S, T, CV1, CV2> instantiate() {
				return new Instantiator<>(getExpressoEnv().getModels().instantiate(), getMapWith().instantiate(),
					QommonsUtils.map(getCombinedValues(), cv -> cv.instantiate(), true),
					getReverse() == null ? null : getReverse().instantiate(), getDefinition().getSourceName(), getDefinition().isCached(),
						getDefinition().isReEvalOnUpdate(), getDefinition().isFireIfUnchanged(), getDefinition().isNullToNull(),
						getDefinition().isManyToOne(), getDefinition().isOneToMany(),
						getEquivalence() == null ? null : getEquivalence().instantiate(), getTargetValueType());
			}
		}

		static class Instantiator<S, T, CV1 extends ObservableCollection<?>, CV2 extends ObservableCollection<?>>
		extends ExpressoTransformations.AbstractCompiledTransformation.Instantiator<S, T, CV1, CV2>
		implements FlowTransformInstantiator<CV1, CV2, S, T> {
			private final TypeToken<T> theTargetValueType;

			Instantiator(ModelInstantiator localModel, ExpressoTransformations.MapWith.Instantiator<S, T> mapWith,
				List<ExpressoTransformations.CombineWith.Instantiator<?>> combinedValues,
				ExpressoTransformations.CompiledMapReverse.Instantiator<S, T> reverse, ModelComponentId sourceVariable, boolean cached,
				boolean reEvalOnUpdate, boolean fireIfUnchanged, boolean nullToNull, boolean manyToOne, boolean oneToMany,
				ModelValueInstantiator<SettableValue<Equivalence<? super T>>> equivalence, TypeToken<T> targetValueType) {
				super(localModel, mapWith, combinedValues, reverse, sourceVariable, cached, reEvalOnUpdate, fireIfUnchanged, nullToNull,
					manyToOne, oneToMany, equivalence);
				theTargetValueType = targetValueType;
			}

			@Override
			public CV2 transform(CV1 source, ModelSetInstance models) throws ModelInstantiationException {
				CollectionDataFlow<?, ?, T> flow = transformToFlow(source, models);
				if (flow.prefersPassive())
					return (CV2) flow.collectPassive();
				else
					return (CV2) flow.collectActive(models.getUntil());
			}

			@Override
			public CollectionDataFlow<?, ?, T> transformFlow(CollectionDataFlow<?, ?, S> source, ModelSetInstance models)
				throws ModelInstantiationException {
				try {
					return source.transform(theTargetValueType, tx -> {
						try {
							return transform(tx, models);
						} catch (ModelInstantiationException e) {
							throw new CheckedExceptionWrapper(e);
						}
					});
				} catch (CheckedExceptionWrapper e) {
					throw CheckedExceptionWrapper.getThrowable(e, ModelInstantiationException.class);
				}
			}

			@Override
			public CollectionDataFlow<?, ?, T> transformToFlow(CV1 source, ModelSetInstance models) throws ModelInstantiationException {
				return transformFlow((CollectionDataFlow<?, ?, S>) source.flow(), models);
			}
		}
	}

	static class FilterCollectionTransform<C extends ObservableCollection<?>> extends TypePreservingTransform<C>
	implements CollectionTransform<C, C, ExElement> {
		private ModelComponentId theSourceVariable;
		private CompiledExpression theTest;

		FilterCollectionTransform(Def<?> parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType);
		}

		public ModelComponentId getSourceVariable() {
			return theSourceVariable;
		}

		public CompiledExpression getTest() {
			return theTest;
		}

		@Override
		public void update(ExpressoQIS session, ModelType<C> sourceModelType) throws QonfigInterpretationException {
			super.update(session, sourceModelType);
			String sourceAs = session.getAttributeText("source-as");
			ExWithElementModel.Def elModels = getAddOn(ExWithElementModel.Def.class);
			theSourceVariable = elModels.getElementValueModelId(sourceAs);
			theTest = getAttributeExpression("test", session);
			elModels.<Interpreted<?, ?, ?>, SettableValue<?>> satisfyElementValueType(theSourceVariable, ModelTypes.Value,
				(interp, env) -> ModelTypes.Value.forType(interp.getSourceType()));
		}

		@Override
		protected Interpreted<?, C, ?> tppInterpret(ExElement.Interpreted<?> parent) {
			return new Interpreted<>(this, parent);
		}

		static class Interpreted<T, C extends ObservableCollection<?>, CV extends C> extends TypePreservingTransform.Interpreted<C, CV> {
			private TypeToken<T> theSourceType;
			private InterpretedValueSynth<SettableValue<?>, SettableValue<String>> theTest;

			Interpreted(FilterCollectionTransform<C> definition, ExElement.Interpreted<?> parent) {
				super(definition, parent);
			}

			@Override
			public FilterCollectionTransform<C> getDefinition() {
				return (FilterCollectionTransform<C>) super.getDefinition();
			}

			public TypeToken<T> getSourceType() {
				return theSourceType;
			}

			@Override
			public void update(ModelInstanceType<C, CV> sourceType, InterpretedExpressoEnv env) throws ExpressoInterpretationException {
				theSourceType = (TypeToken<T>) sourceType.getType(0);
				super.update(sourceType, env);
				theTest = ExpressoTransformations.parseFilter(getDefinition().getTest(), this, true);
			}

			@Override
			public BetterList<InterpretedValueSynth<?, ?>> getComponents() {
				return BetterList.of(theTest);
			}

			@Override
			public Operation.Instantiator<CV, CV> instantiate() {
				return new Instantiator<>(theSourceType, getDefinition().getSourceVariable(), theTest.instantiate());
			}
		}

		static class Instantiator<T, CV extends ObservableCollection<?>> implements FlowTransformInstantiator<CV, CV, T, T> {
			private final TypeToken<T> theSourceType;
			private final ModelComponentId theSourceVariable;
			private final ModelValueInstantiator<SettableValue<String>> theTest;

			Instantiator(TypeToken<T> sourceType, ModelComponentId sourceVariable, ModelValueInstantiator<SettableValue<String>> test) {
				theSourceType = sourceType;
				theSourceVariable = sourceVariable;
				theTest = test;
			}

			@Override
			public void instantiate() {
				theTest.instantiate();
			}

			@Override
			public CollectionDataFlow<?, ?, T> transformFlow(CollectionDataFlow<?, ?, T> source, ModelSetInstance models)
				throws ModelInstantiationException {
				SettableValue<T> sourceV = SettableValue.build(theSourceType).build();
				ExFlexibleElementModelAddOn.satisfyElementValue(theSourceVariable, models, sourceV);
				SettableValue<String> testV = theTest.get(models);
				String print = theTest.toString();
				Function<T, String> filter = LambdaUtils.printableFn(v -> {
					sourceV.set(v, null);
					return testV.get();
				}, () -> print);
				return source.filter(filter);
			}

			@Override
			public CollectionDataFlow<?, ?, T> transformToFlow(CV source, ModelSetInstance models) throws ModelInstantiationException {
				return transformFlow((CollectionDataFlow<?, ?, T>) source.flow(), models);
			}

			@Override
			public boolean isDifferent(ModelSetInstance sourceModels, ModelSetInstance newModels) throws ModelInstantiationException {
				SettableValue<String> test = theTest.get(sourceModels);
				return test != theTest.forModelCopy(test, sourceModels, newModels);
			}
		}
	}

	static class TypeFilteredCollectionTransform<C extends ObservableCollection<?>> extends TypePreservingTransform<C>
	implements CollectionTransform<C, C, ExElement> {
		private VariableType theType;

		TypeFilteredCollectionTransform(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType);
		}

		public VariableType getType() {
			return theType;
		}

		@Override
		public void update(ExpressoQIS session, ModelType<C> sourceModelType) throws QonfigInterpretationException {
			super.update(session, sourceModelType);
			QonfigValue typeQV = session.attributes().get("type").get();
			theType = typeQV == null ? null
				: VariableType.parseType(new LocatedPositionedContent.Default(typeQV.fileLocation, typeQV.position));
			if (theType instanceof VariableType.Parameterized)
				throw new QonfigInterpretationException("Parameterized types are not permitted for filter type",
					new LocatedFilePosition(typeQV.fileLocation, typeQV.position.getPosition(0)), typeQV.position.length());
		}

		@Override
		protected Interpreted<?, C, ?> tppInterpret(ExElement.Interpreted<?> parent) {
			return new Interpreted<>(this, parent);
		}

		static class Interpreted<T, C extends ObservableCollection<?>, CV extends C> extends TypePreservingTransform.Interpreted<C, CV> {
			private Class<?> theType;

			Interpreted(TypeFilteredCollectionTransform<C> definition, ExElement.Interpreted<?> parent) {
				super(definition, parent);
			}

			@Override
			public TypeFilteredCollectionTransform<C> getDefinition() {
				return (TypeFilteredCollectionTransform<C>) super.getDefinition();
			}

			public Class<?> getType() {
				return theType;
			}

			@Override
			public void update(ModelInstanceType<C, CV> sourceType, InterpretedExpressoEnv env) throws ExpressoInterpretationException {
				super.update(sourceType, env);
				theType = TypeTokens.getRawType(getDefinition().getType().getType(getExpressoEnv()));
			}

			@Override
			public BetterList<InterpretedValueSynth<?, ?>> getComponents() {
				return BetterList.empty();
			}

			@Override
			public Operation.Instantiator<CV, CV> instantiate() {
				return new Instantiator<>(theType);
			}
		}

		static class Instantiator<T, CV extends ObservableCollection<?>> implements FlowTransformInstantiator<CV, CV, T, T> {
			private final Class<?> theType;

			Instantiator(Class<?> type) {
				theType = type;
			}

			@Override
			public void instantiate() {
			}

			@Override
			public CollectionDataFlow<?, ?, T> transformFlow(CollectionDataFlow<?, ?, T> source, ModelSetInstance models)
				throws ModelInstantiationException {
				Class<?> type = theType;
				String notInstance = "Not an instance of " + type.getName();
				Function<T, String> filter = LambdaUtils.printableFn(v -> {
					if (type.isInstance(v))
						return null;
					return notInstance;
				}, () -> "instanceof " + type.getName());
				return source.filter(filter);
			}

			@Override
			public CollectionDataFlow<?, ?, T> transformToFlow(CV source, ModelSetInstance models) throws ModelInstantiationException {
				return transformFlow((CollectionDataFlow<?, ?, T>) source.flow(), models);
			}

			@Override
			public boolean isDifferent(ModelSetInstance sourceModels, ModelSetInstance newModels) throws ModelInstantiationException {
				return false;
			}
		}

		static class TypeFilteredObservable<T> extends Observable.WrappingObservable<T, T> {
			private final Class<?> theFilterType;

			TypeFilteredObservable(Observable<T> wrapped, Class<?> filterType) {
				super(wrapped);
				theFilterType = filterType;
			}

			@Override
			protected Observable<T> getWrapped() {
				return super.getWrapped();
			}

			Class<?> getFilterType() {
				return theFilterType;
			}

			@Override
			protected Object createIdentity() {
				return Identifiable.wrap(getWrapped().getIdentity(), "filter", theFilterType);
			}

			@Override
			public Subscription subscribe(Observer<? super T> observer) {
				return getWrapped().subscribe(new Observer<T>() {
					@Override
					public <V extends T> void onNext(V value) {
						if (theFilterType.isInstance(value))
							observer.onNext(value);
					}

					@Override
					public void onCompleted(Causable cause) {
						observer.onCompleted(cause);
					}
				});
			}
		}
	}

	static class ReverseCollectionTransform<C extends ObservableCollection<?>> extends TypePreservingTransform<C>
	implements CollectionTransform<C, C, ExElement> {
		ReverseCollectionTransform(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType);
		}

		@Override
		protected Interpreted<?, C, ?> tppInterpret(org.observe.expresso.qonfig.ExElement.Interpreted<?> parent) {
			return new Interpreted<>(this, parent);
		}

		static class Interpreted<T, C extends ObservableCollection<?>, CV extends C> extends TypePreservingTransform.Interpreted<C, CV> {
			Interpreted(ReverseCollectionTransform<C> definition, ExElement.Interpreted<?> parent) {
				super(definition, parent);
			}

			@Override
			public BetterList<InterpretedValueSynth<?, ?>> getComponents() {
				return BetterList.empty();
			}

			@Override
			public Instantiator<CV, CV> instantiate() {
				return new Instantiator<>();
			}

			@Override
			public String toString() {
				return "reverse()";
			}
		}

		static class Instantiator<T, CV extends ObservableCollection<?>> implements FlowTransformInstantiator<CV, CV, T, T> {
			@Override
			public CV transform(CV source, ModelSetInstance models) throws ModelInstantiationException {
				return (CV) source.reverse();
			}

			@Override
			public void instantiate() {
			}

			@Override
			public CollectionDataFlow<?, ?, T> transformFlow(CollectionDataFlow<?, ?, T> source, ModelSetInstance models)
				throws ModelInstantiationException {
				return source.reverse();
			}

			@Override
			public CollectionDataFlow<?, ?, T> transformToFlow(CV source, ModelSetInstance models) throws ModelInstantiationException {
				return ((CollectionDataFlow<?, ?, T>) source.flow()).reverse();
			}

			@Override
			public boolean isDifferent(ModelSetInstance sourceModels, ModelSetInstance newModels) throws ModelInstantiationException {
				return false;
			}
		}
	}

	@ExElementTraceable(toolkit = ExpressoBaseV0_1.BASE,
		qonfigType = "refresh",
		interpretation = RefreshCollectionTransform.Interpreted.class)
	static class RefreshCollectionTransform<C extends ObservableCollection<?>> extends TypePreservingTransform<C>
	implements CollectionTransform<C, C, ExElement> {
		private CompiledExpression theRefresh;

		RefreshCollectionTransform(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType);
		}

		@QonfigAttributeGetter("on")
		public CompiledExpression getRefresh() {
			return theRefresh;
		}

		@Override
		public void update(ExpressoQIS session, ModelType<C> sourceModelType) throws QonfigInterpretationException {
			super.update(session, sourceModelType);
			theRefresh = getAttributeExpression("on", session);
		}

		@Override
		protected Interpreted<?, C, ?> tppInterpret(ExElement.Interpreted<?> parent) {
			return new Interpreted<>(this, parent);
		}

		static class Interpreted<T, C extends ObservableCollection<?>, CV extends C> extends TypePreservingTransform.Interpreted<C, CV> {
			private InterpretedValueSynth<Observable<?>, Observable<?>> theRefresh;

			Interpreted(RefreshCollectionTransform<C> definition, ExElement.Interpreted<?> parent) {
				super(definition, parent);
			}

			@Override
			public RefreshCollectionTransform<C> getDefinition() {
				return (RefreshCollectionTransform<C>) super.getDefinition();
			}

			public InterpretedValueSynth<Observable<?>, Observable<?>> getRefresh() {
				return theRefresh;
			}

			@Override
			public void update(ModelInstanceType<C, CV> sourceType, InterpretedExpressoEnv env) throws ExpressoInterpretationException {
				super.update(sourceType, env);
				theRefresh = interpret(getDefinition().getRefresh(), ModelTypes.Event.any());
			}

			@Override
			public BetterList<InterpretedValueSynth<?, ?>> getComponents() {
				return BetterList.of(theRefresh);
			}

			@Override
			public Operation.Instantiator<CV, CV> instantiate() {
				return new Instantiator<>(theRefresh.instantiate());
			}

			@Override
			public String toString() {
				return "refresh(" + theRefresh + ")";
			}
		}

		static class Instantiator<T, CV extends ObservableCollection<?>> implements FlowTransformInstantiator<CV, CV, T, T> {
			private final ModelValueInstantiator<Observable<?>> theRefresh;

			Instantiator(ModelValueInstantiator<Observable<?>> refresh) {
				theRefresh = refresh;
			}

			@Override
			public void instantiate() {
				theRefresh.instantiate();
			}

			@Override
			public CollectionDataFlow<?, ?, T> transformFlow(CollectionDataFlow<?, ?, T> source, ModelSetInstance models)
				throws ModelInstantiationException {
				Observable<?> refresh = theRefresh.get(models);
				return source.refresh(refresh);
			}

			@Override
			public CollectionDataFlow<?, ?, T> transformToFlow(CV source, ModelSetInstance models) throws ModelInstantiationException {
				return transformFlow((CollectionDataFlow<?, ?, T>) source.flow(), models);
			}

			@Override
			public boolean isDifferent(ModelSetInstance sourceModels, ModelSetInstance newModels) throws ModelInstantiationException {
				Observable<?> refresh = theRefresh.get(sourceModels);
				return refresh != theRefresh.forModelCopy(refresh, sourceModels, newModels);
			}
		}
	}

	@ExMultiElementTraceable({
		@ExElementTraceable(toolkit = ExpressoBaseV0_1.BASE,
			qonfigType = "refresh-each",
			interpretation = RefreshEachCollectionTransform.Interpreted.class),
		@ExElementTraceable(toolkit = ExpressoBaseV0_1.BASE,
		qonfigType = "complex-operation",
		interpretation = RefreshEachCollectionTransform.Interpreted.class) })
	static class RefreshEachCollectionTransform<C extends ObservableCollection<?>> extends TypePreservingTransform<C>
	implements CollectionTransform<C, C, ExElement> {
		private ModelComponentId theSourceName;
		private CompiledExpression theRefresh;

		RefreshEachCollectionTransform(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType);
		}

		@QonfigAttributeGetter(asType = "complex-operation", value = "source-as")
		public ModelComponentId getSourceVariable() {
			return theSourceName;
		}

		@QonfigAttributeGetter(asType = "refresh-each", value = "on")
		public CompiledExpression getRefresh() {
			return theRefresh;
		}

		@Override
		public void update(ExpressoQIS session, ModelType<C> sourceModelType) throws QonfigInterpretationException {
			super.update(session, sourceModelType);
			String sourceAs = session.getAttributeText("source-as");
			ExWithElementModel.Def elModels = getAddOn(ExWithElementModel.Def.class);
			theSourceName = elModels.getElementValueModelId(sourceAs);
			theRefresh = getAttributeExpression("on", session);
			elModels.<Interpreted<?, ?, ?>, SettableValue<?>> satisfyElementValueType(theSourceName, ModelTypes.Value,
				(interp, env) -> ModelTypes.Value.forType(interp.getSourceType()));
		}

		@Override
		protected Interpreted<?, C, ?> tppInterpret(ExElement.Interpreted<?> parent) {
			return new Interpreted<>(this, parent);
		}

		static class Interpreted<T, C extends ObservableCollection<?>, CV extends C> extends TypePreservingTransform.Interpreted<C, CV> {
			private TypeToken<T> theSourceType;
			private InterpretedValueSynth<SettableValue<?>, SettableValue<Observable<?>>> theRefresh;

			Interpreted(RefreshEachCollectionTransform<C> definition, ExElement.Interpreted<?> parent) {
				super(definition, parent);
			}

			@Override
			public RefreshEachCollectionTransform<C> getDefinition() {
				return (RefreshEachCollectionTransform<C>) super.getDefinition();
			}

			public TypeToken<T> getSourceType() {
				return theSourceType;
			}

			public InterpretedValueSynth<SettableValue<?>, SettableValue<Observable<?>>> getRefresh() {
				return theRefresh;
			}

			@Override
			public void update(ModelInstanceType<C, CV> sourceType, InterpretedExpressoEnv env) throws ExpressoInterpretationException {
				theSourceType = (TypeToken<T>) sourceType.getType(0);
				super.update(sourceType, env);
				theRefresh = interpret(getDefinition().getRefresh(),
					ModelTypes.Value.forType(TypeTokens.get().keyFor(Observable.class).wildCard()));
			}

			@Override
			public BetterList<InterpretedValueSynth<?, ?>> getComponents() {
				return BetterList.of(theRefresh);
			}

			@Override
			public Operation.Instantiator<CV, CV> instantiate() {
				return new Instantiator<>(getExpressoEnv().getModels().instantiate(), theSourceType, getDefinition().getSourceVariable(),
					theRefresh.instantiate());
			}

			@Override
			public String toString() {
				return "refreshEach(" + theRefresh + ")";
			}
		}

		static class Instantiator<T, CV extends ObservableCollection<?>> implements FlowTransformInstantiator<CV, CV, T, T> {
			private final ModelInstantiator theLocalModel;
			private final TypeToken<T> theSourceType;
			private final ModelComponentId theSourceVariable;
			private final ModelValueInstantiator<SettableValue<Observable<?>>> theRefresh;

			Instantiator(ModelInstantiator localModel, TypeToken<T> sourceType, ModelComponentId sourceVariable,
				ModelValueInstantiator<SettableValue<Observable<?>>> refresh) {
				theLocalModel = localModel;
				theSourceType = sourceType;
				theSourceVariable = sourceVariable;
				theRefresh = refresh;
			}

			@Override
			public void instantiate() {
				theLocalModel.instantiate();
				theRefresh.instantiate();
			}

			@Override
			public CollectionDataFlow<?, ?, T> transformFlow(CollectionDataFlow<?, ?, T> source, ModelSetInstance models)
				throws ModelInstantiationException {
				models = theLocalModel.wrap(models);
				SettableValue<T> sourceV = SettableValue.build(theSourceType).build();
				ExFlexibleElementModelAddOn.satisfyElementValue(theSourceVariable, models, sourceV);
				SettableValue<Observable<?>> refresh = theRefresh.get(models);
				String print = theRefresh.toString();
				Function<T, Observable<?>> refreshFn = LambdaUtils.printableFn(v -> {
					sourceV.set(v, null);
					return refresh.get();
				}, () -> print);
				return source.refreshEach(refreshFn);
			}

			@Override
			public CollectionDataFlow<?, ?, T> transformToFlow(CV source, ModelSetInstance models) throws ModelInstantiationException {
				return transformFlow((CollectionDataFlow<?, ?, T>) source.flow(), models);
			}

			@Override
			public boolean isDifferent(ModelSetInstance sourceModels, ModelSetInstance newModels) throws ModelInstantiationException {
				SettableValue<Observable<?>> refresh = theRefresh.get(sourceModels);
				return refresh != theRefresh.forModelCopy(refresh, sourceModels, newModels);
			}
		}
	}

	static class DistinctCollectionTransform<C1 extends ObservableCollection<?>, C2 extends ObservableSet<?>>
	extends ExElement.Def.Abstract<ExElement> implements CollectionTransform<C1, C2, ExElement> {
		private ModelType<C1> theSourceType;
		private ModelType<C2> theTargetType;
		private boolean isUseFirst;
		private boolean isPreservingSourceOrder;
		private ExSort.ExRootSort theSort;

		DistinctCollectionTransform(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType);
		}

		public boolean isUseFirst() {
			return isUseFirst;
		}

		public boolean isPreservingSourceOrder() {
			return isPreservingSourceOrder;
		}

		public ExSort.ExRootSort getSort() {
			return theSort;
		}

		@Override
		public ModelType<? extends C2> getTargetModelType() {
			return theTargetType;
		}

		@Override
		public void update(ExpressoQIS session, ModelType<C1> sourceModelType) throws QonfigInterpretationException {
			super.update(session);
			theSourceType = sourceModelType;
			isUseFirst = session.getAttribute("use-first", boolean.class);
			isPreservingSourceOrder = session.getAttribute("preserve-source-order", boolean.class);
			theSort = syncChild(ExSort.ExRootSort.class, theSort, session, "sort");
			if (isPreservingSourceOrder && theSort != null)
				reporting().warn("'preserve-source-order' is not used when sorting is specified");
		}

		@Override
		public Interpreted<?, C1, ?, C2, ?> interpret(ExElement.Interpreted<?> parent) throws ExpressoInterpretationException {
			return new Interpreted<>(this, parent);
		}

		static class Interpreted<T, C1 extends ObservableCollection<?>, CV1 extends C1, C2 extends ObservableSet<?>, CV2 extends C2>
		extends ExElement.Interpreted.Abstract<ExElement> implements Operation.Interpreted<C1, CV1, C2, CV2, ExElement> {
			private TypeToken<T> theValueType;
			private ExSort.ExRootSort.Interpreted<T> theSort;

			Interpreted(DistinctCollectionTransform<C1, C2> definition, ExElement.Interpreted<?> parent) {
				super(definition, parent);
			}

			@Override
			public DistinctCollectionTransform<C1, C2> getDefinition() {
				return (DistinctCollectionTransform<C1, C2>) super.getDefinition();
			}

			public TypeToken<T> getValueType() {
				return theValueType;
			}

			public ExSort.ExRootSort.Interpreted<T> getSort() {
				return theSort;
			}

			@Override
			public void update(ModelInstanceType<C1, CV1> sourceType, InterpretedExpressoEnv env) throws ExpressoInterpretationException {
				theValueType = (TypeToken<T>) sourceType.getType(0);
				update(env);
				if (getDefinition().getSort() == null) {
					if (theSort != null)
						theSort.destroy();
					theSort = null;
				} else {
					if (theSort == null || theSort.getIdentity() != getDefinition().getSort().getIdentity()) {
						if (theSort != null)
							theSort.destroy();
						theSort = (ExSort.ExRootSort.Interpreted<T>) getDefinition().getSort().interpret(this);
					}
					theSort.update(getExpressoEnv());
				}
			}

			@Override
			public ModelInstanceType<? extends C2, ? extends CV2> getTargetType() {
				return (ModelInstanceType<? extends C2, ? extends CV2>) getDefinition().getTargetModelType().forTypes(theValueType);
			}

			@Override
			public BetterList<InterpretedValueSynth<?, ?>> getComponents() {
				return theSort == null ? BetterList.empty() : theSort.getComponents();
			}

			@Override
			public Operation.Instantiator<CV1, CV2> instantiate() {
				return new Instantiator<>(theSort == null ? null : theSort.instantiateSort(), getDefinition().isUseFirst(),
					getDefinition().isPreservingSourceOrder());
			}
		}

		static class Instantiator<T, CV1 extends ObservableCollection<?>, CV2 extends ObservableSet<?>>
		implements FlowTransformInstantiator<CV1, CV2, T, T> {
			private final ModelValueInstantiator<Comparator<? super T>> theSort;
			private final boolean isUseFirst;
			private final boolean isPreservingSourceOrder;

			Instantiator(ModelValueInstantiator<Comparator<? super T>> sort, boolean useFirst, boolean preservingSourceOrder) {
				theSort = sort;
				isUseFirst = useFirst;
				isPreservingSourceOrder = preservingSourceOrder;
			}

			@Override
			public void instantiate() {
				if (theSort != null)
					theSort.instantiate();
			}

			@Override
			public boolean isDifferent(ModelSetInstance sourceModels, ModelSetInstance newModels) throws ModelInstantiationException {
				if (theSort != null && theSort.get(sourceModels) != theSort.get(newModels))
					return true;
				return false;
			}

			@Override
			public CV2 transform(CV1 source, ModelSetInstance models) throws ModelInstantiationException {
				return (CV2) transformToFlow(source, models).collect();
			}

			@Override
			public CollectionDataFlow<?, ?, T> transformFlow(CollectionDataFlow<?, ?, T> source, ModelSetInstance models)
				throws ModelInstantiationException {
				if (theSort != null) {
					Comparator<? super T> sort = theSort.get(models);
					return source.distinctSorted(sort, isUseFirst);
				} else {
					return source.distinct(uo -> uo//
						.preserveSourceOrder(isPreservingSourceOrder)//
						.useFirst(isUseFirst));
				}
			}

			@Override
			public CollectionDataFlow<?, ?, T> transformToFlow(CV1 source, ModelSetInstance models) throws ModelInstantiationException {
				return transformFlow((CollectionDataFlow<?, ?, T>) source.flow(), models);
			}
		}
	}

	public static class SortedCollectionTransform<C1 extends ObservableCollection<?>> extends ExSort.ExRootSort
	implements CollectionTransform<C1, ObservableSortedCollection<?>, ExElement> {
		public SortedCollectionTransform(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType);
		}

		@Override
		public ModelType<? extends ObservableSortedCollection<?>> getTargetModelType() {
			return ModelTypes.SortedCollection;
		}

		@Override
		public void update(ExpressoQIS session, ModelType<C1> sourceModelType) throws QonfigInterpretationException {
			super.update(session);
		}

		@Override
		public Interpreted<?, C1, ?, ?> interpret(ExElement.Interpreted<?> parent) {
			return new Interpreted<>(this, parent);
		}

		static class Interpreted<T, C1 extends ObservableCollection<?>, CV1 extends C1, CV2 extends ObservableSortedCollection<?>> extends
		ExSort.ExRootSort.Interpreted<T> implements Operation.Interpreted<C1, CV1, ObservableSortedCollection<?>, CV2, ExElement> {
			private ModelInstanceType.SingleTyped<ObservableSortedCollection<?>, T, CV2> theTargetType;

			Interpreted(SortedCollectionTransform<C1> definition, ExElement.Interpreted<?> parent) {
				super(definition, parent);
			}

			@Override
			public SortedCollectionTransform<C1> getDefinition() {
				return (SortedCollectionTransform<C1>) super.getDefinition();
			}

			@Override
			public void update(ModelInstanceType<C1, CV1> sourceType, InterpretedExpressoEnv env) throws ExpressoInterpretationException {
				TypeToken<T> valueType = (TypeToken<T>) sourceType.getType(0);
				theTargetType = (SingleTyped<ObservableSortedCollection<?>, T, CV2>) ModelTypes.SortedCollection.forType(valueType);
				super.update(valueType, env);
			}

			@Override
			public ModelInstanceType<? extends ObservableSortedCollection<?>, ? extends CV2> getTargetType() {
				return theTargetType;
			}

			@Override
			public Operation.Instantiator<CV1, CV2> instantiate() {
				return new Instantiator<>(instantiateSort());
			}
		}

		static class Instantiator<T, CV1 extends ObservableCollection<?>, CV2 extends ObservableSortedCollection<?>>
		implements FlowTransformInstantiator<CV1, CV2, T, T> {
			private final ModelValueInstantiator<Comparator<? super T>> theSorting;

			Instantiator(ModelValueInstantiator<Comparator<? super T>> sorting) {
				theSorting = sorting;
			}

			@Override
			public void instantiate() {
				theSorting.instantiate();
			}

			@Override
			public CV2 transform(CV1 source, ModelSetInstance models) throws ModelInstantiationException {
				return (CV2) transformFlow((CollectionDataFlow<?, ?, T>) source.flow(), models).collectActive(models.getUntil());
			}

			@Override
			public CollectionDataFlow<?, ?, T> transformFlow(CollectionDataFlow<?, ?, T> source, ModelSetInstance models)
				throws ModelInstantiationException {
				Comparator<? super T> sorting = theSorting.get(models);
				return source.sorted(sorting);
			}

			@Override
			public CollectionDataFlow<?, ?, T> transformToFlow(CV1 source, ModelSetInstance models) throws ModelInstantiationException {
				return transformFlow((CollectionDataFlow<?, ?, T>) source.flow(), models);
			}

			@Override
			public boolean isDifferent(ModelSetInstance sourceModels, ModelSetInstance newModels) throws ModelInstantiationException {
				if (theSorting.get(sourceModels) != theSorting.get(newModels))
					return true;
				return false;
			}
		}
	}

	@ExElementTraceable(toolkit = ExpressoBaseV0_1.BASE,
		qonfigType = "unmodifiable",
		interpretation = UnmodifiableCollectionTransform.Interpreted.class)
	static class UnmodifiableCollectionTransform<C extends ObservableCollection<?>> extends TypePreservingTransform<C>
	implements CollectionTransform<C, C, ExElement> {
		private boolean isAllowUpdates;

		UnmodifiableCollectionTransform(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType);
		}

		public boolean isAllowUpdates() {
			return isAllowUpdates;
		}

		@Override
		public void update(ExpressoQIS session, ModelType<C> sourceModelType) throws QonfigInterpretationException {
			super.update(session, sourceModelType);
			isAllowUpdates = session.getAttribute("allow-updates", boolean.class);
		}

		@Override
		protected Interpreted<?, C, ?> tppInterpret(ExElement.Interpreted<?> parent) {
			return new Interpreted<>(this, parent);
		}

		static class Interpreted<T, C extends ObservableCollection<?>, CV extends C> extends TypePreservingTransform.Interpreted<C, CV> {
			Interpreted(UnmodifiableCollectionTransform<C> definition, ExElement.Interpreted<?> parent) {
				super(definition, parent);
			}

			@Override
			public UnmodifiableCollectionTransform<C> getDefinition() {
				return (UnmodifiableCollectionTransform<C>) super.getDefinition();
			}

			@Override
			public BetterList<InterpretedValueSynth<?, ?>> getComponents() {
				return BetterList.empty();
			}

			@Override
			public Instantiator<CV, CV> instantiate() {
				return new Instantiator<>(getDefinition().isAllowUpdates());
			}

			@Override
			public String toString() {
				return "unmodifiable()";
			}
		}

		static class Instantiator<T, CV extends ObservableCollection<?>> implements FlowTransformInstantiator<CV, CV, T, T> {
			private final boolean isAllowUpdates;

			Instantiator(boolean allowUpdates) {
				isAllowUpdates = allowUpdates;
			}

			@Override
			public void instantiate() {
			}

			@Override
			public CollectionDataFlow<?, ?, T> transformFlow(CollectionDataFlow<?, ?, T> source, ModelSetInstance models)
				throws ModelInstantiationException {
				return source.unmodifiable(isAllowUpdates);
			}

			@Override
			public CollectionDataFlow<?, ?, T> transformToFlow(CV source, ModelSetInstance models) throws ModelInstantiationException {
				return transformFlow((CollectionDataFlow<?, ?, T>) source.flow(), models);
			}

			@Override
			public boolean isDifferent(ModelSetInstance sourceModels, ModelSetInstance newModels) throws ModelInstantiationException {
				return false;
			}
		}
	}

	@ExElementTraceable(toolkit = ExpressoBaseV0_1.BASE,
		qonfigType = "refresh",
		interpretation = FilterModCollectionTransform.Interpreted.class)
	static class FilterModCollectionTransform<C extends ObservableCollection<?>> extends TypePreservingTransform<C>
	implements CollectionTransform<C, C, ExElement> {
		FilterModCollectionTransform(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType);
		}

		@Override
		public void update(ExpressoQIS session, ModelType<C> sourceModelType) throws QonfigInterpretationException {
			super.update(session, sourceModelType);
		}

		@Override
		protected Interpreted<?, C, ?> tppInterpret(ExElement.Interpreted<?> parent) {
			return new Interpreted<>(this, parent);
		}

		static class Interpreted<T, C extends ObservableCollection<?>, CV extends C> extends TypePreservingTransform.Interpreted<C, CV> {
			Interpreted(FilterModCollectionTransform<C> definition, ExElement.Interpreted<?> parent) {
				super(definition, parent);
			}

			@Override
			public FilterModCollectionTransform<C> getDefinition() {
				return (FilterModCollectionTransform<C>) super.getDefinition();
			}

			@Override
			public void update(ModelInstanceType<C, CV> sourceType, InterpretedExpressoEnv env) throws ExpressoInterpretationException {
				super.update(sourceType, env);
				throw new ExpressoInterpretationException("Not yet implemented", reporting().getFileLocation().getPosition(0), 0);
			}

			@Override
			public BetterList<InterpretedValueSynth<?, ?>> getComponents() {
				throw new UnsupportedOperationException();
			}

			@Override
			public Instantiator<CV, CV> instantiate() {
				throw new UnsupportedOperationException("Not yet implemented");
			}

			@Override
			public String toString() {
				throw new UnsupportedOperationException();
			}
		}
	}

	static class MapEquivalentCollectionTransform<C extends ObservableCollection<?>>
	extends ExpressoTransformations.AbstractCompiledTransformation<C, C, ExElement> implements CollectionTransform<C, C, ExElement> {
		private ModelType<C> theSourceType;
		private ExSort.ExRootSort theSort;

		MapEquivalentCollectionTransform(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType);
		}

		@Override
		public ModelType<? extends C> getTargetModelType() {
			return theSourceType;
		}

		@QonfigChildGetter("sort")
		public ExSort.ExRootSort getSort() {
			return theSort;
		}

		@Override
		public void update(ExpressoQIS session, ModelType<C> sourceModelType) throws QonfigInterpretationException {
			theSourceType = sourceModelType;
			super.update(session, sourceModelType);
			theSort = syncChild(ExSort.ExRootSort.class, theSort, session, "sort");
			if (theSourceType == ModelTypes.Collection)
				throw new QonfigInterpretationException("map-equivalent is not valid for a simple collection",
					session.reporting().getPosition(), 0);
			else if (theSourceType == ModelTypes.Set) {
				if (theSort != null)
					throw new QonfigInterpretationException("Sorting is invalid for map-equivalent on a non-sorted set",
						theSort.reporting().getPosition(), 0);
				else if (getReverse() == null)
					throw new QonfigInterpretationException("Reverse required for map-equivalent on a non-sorted set",
						reporting().getFileLocation().getPosition(0), 0);
			} else if (theSourceType == ModelTypes.SortedCollection || theSourceType == ModelTypes.SortedSet) {
				if (theSort == null && getReverse() == null)
					throw new QonfigInterpretationException(
						"Either reverse or sort required for map-equivalent on a sorted collection or set",
						reporting().getFileLocation().getPosition(0), 0);
			} else
				throw new QonfigInterpretationException("Unrecognized source collection type: " + theSourceType,
					reporting().getFileLocation().getPosition(0), 0);
		}

		@Override
		public ExpressoTransformations.CompiledTransformation.Interpreted<C, ?, ?, ?, C, ?, ? extends ExElement> interpret(
			ExElement.Interpreted<?> parent) throws ExpressoInterpretationException {
			return new Interpreted<>(this, parent);
		}

		static class Interpreted<C extends ObservableCollection<?>, S, T, CV1 extends C, CV2 extends C>
		extends ExpressoTransformations.AbstractCompiledTransformation.Interpreted<C, S, T, CV1, C, CV2, ExElement> {
			private ModelInstanceType<C, CV2> theTargetModelType;
			private ExSort.ExRootSort.Interpreted<T> theSort;

			Interpreted(MapEquivalentCollectionTransform<C> definition, ExElement.Interpreted<?> parent) {
				super(definition, parent);
			}

			@Override
			public MapEquivalentCollectionTransform<C> getDefinition() {
				return (MapEquivalentCollectionTransform<C>) super.getDefinition();
			}

			public ExSort.ExRootSort.Interpreted<? super T> getSort() {
				return theSort;
			}

			@Override
			public void update(ModelInstanceType<C, CV1> sourceType, InterpretedExpressoEnv env) throws ExpressoInterpretationException {
				super.update(sourceType, env);
				theTargetModelType = (ModelInstanceType<C, CV2>) getDefinition().getTargetModelType().forTypes(getTargetValueType());
				if (getDefinition().getSort() == null) {
					if (theSort != null)
						theSort.destroy();
					theSort = null;
				} else if (theSort == null || theSort.getIdentity() != getDefinition().getSort().getIdentity()) {
					if (theSort != null)
						theSort.destroy();
					theSort = (ExSort.ExRootSort.Interpreted<T>) getDefinition().getSort().interpret(this);
				}
				if (theSort != null)
					theSort.update(getExpressoEnv());
			}

			@Override
			public ModelInstanceType<? extends C, ? extends CV2> getTargetType() {
				return theTargetModelType;
			}

			@Override
			public ExpressoTransformations.CompiledTransformation.Instantiator<S, T, CV1, CV2> instantiate() {
				return new Instantiator<>(getExpressoEnv().getModels().instantiate(), getMapWith().instantiate(),
					QommonsUtils.map(getCombinedValues(), cv -> cv.instantiate(), true),
					getReverse() == null ? null : getReverse().instantiate(), getDefinition().getSourceName(), getDefinition().isCached(),
						getDefinition().isReEvalOnUpdate(), getDefinition().isFireIfUnchanged(), getDefinition().isNullToNull(),
						getDefinition().isManyToOne(), getDefinition().isOneToMany(),
						getEquivalence() == null ? null : getEquivalence().instantiate(), getTargetValueType(),
							theSort == null ? null : theSort.instantiateSort(), reporting().getPosition());
			}
		}

		static class Instantiator<S, T, CV1 extends ObservableCollection<?>, CV2 extends ObservableCollection<?>>
		extends AbstractCompiledTransformation.Instantiator<S, T, CV1, CV2> implements FlowTransformInstantiator<CV1, CV2, S, T> {
			private final TypeToken<T> theTargetValueType;
			private final ModelValueInstantiator<Comparator<? super T>> theSort;
			private final LocatedFilePosition theLocation;

			Instantiator(ModelInstantiator localModel, ExpressoTransformations.MapWith.Instantiator<S, T> mapWith,
				List<ExpressoTransformations.CombineWith.Instantiator<?>> combinedValues,
				ExpressoTransformations.CompiledMapReverse.Instantiator<S, T> reverse, ModelComponentId sourceVariable, boolean cached,
				boolean reEvalOnUpdate, boolean fireIfUnchanged, boolean nullToNull, boolean manyToOne, boolean oneToMany,
				ModelValueInstantiator<SettableValue<Equivalence<? super T>>> equivalence, TypeToken<T> targetValueType,
				ModelValueInstantiator<Comparator<? super T>> sort, LocatedFilePosition location) {
				super(localModel, mapWith, combinedValues, reverse, sourceVariable, cached, reEvalOnUpdate, fireIfUnchanged, nullToNull,
					manyToOne, oneToMany, equivalence);
				theTargetValueType = targetValueType;
				theSort = sort;
				theLocation = location;
			}

			@Override
			public void instantiate() {
				super.instantiate();
				if (theSort != null)
					theSort.instantiate();
			}

			@Override
			public CV2 transform(CV1 source, ModelSetInstance models) throws ModelInstantiationException {
				CollectionDataFlow<?, ?, T> flow = transformToFlow(source, models);
				if (flow.prefersPassive())
					return (CV2) flow.collectPassive();
				else
					return (CV2) flow.collectActive(models.getUntil());
			}

			@Override
			public CollectionDataFlow<?, ?, T> transformFlow(CollectionDataFlow<?, ?, S> source, ModelSetInstance models)
				throws ModelInstantiationException {
				try {
					if (source instanceof SortedDataFlow) {
						if (theSort != null) {
							Comparator<? super T> sort = theSort.get(models);
							return ((SortedDataFlow<?, ?, S>) source).transformEquivalent(theTargetValueType, tx -> {
								try {
									return transform(tx, models);
								} catch (ModelInstantiationException e) {
									throw new CheckedExceptionWrapper(e);
								}
							}, sort);
						} else {
							return ((SortedDataFlow<?, ?, S>) source).transformEquivalent(theTargetValueType, tx -> {
								try {
									return (Transformation.ReversibleTransformation<S, T>) transform(tx, models);
								} catch (ModelInstantiationException e) {
									throw new CheckedExceptionWrapper(e);
								}
							});
						}
					} else if (source instanceof DistinctDataFlow) {
						return ((DistinctDataFlow<?, ?, S>) source).transformEquivalent(theTargetValueType, tx -> {
							try {
								return (Transformation.ReversibleTransformation<S, T>) transform(tx, models);
							} catch (ModelInstantiationException e) {
								throw new CheckedExceptionWrapper(e);
							}
						});
					} else
						throw new ModelInstantiationException("Source flow is neither distinct nor sorted: " + source.getClass().getName(),
							theLocation, 0);
				} catch (CheckedExceptionWrapper e) {
					throw CheckedExceptionWrapper.getThrowable(e, ModelInstantiationException.class);
				}
			}

			@Override
			public CollectionDataFlow<?, ?, T> transformToFlow(CV1 source, ModelSetInstance models) throws ModelInstantiationException {
				return transformFlow((CollectionDataFlow<?, ?, S>) source.flow(), models);
			}
		}
	}

	static class FlattenCollectionTransform<C1 extends ObservableCollection<?>, C2 extends ObservableCollection<?>>
	extends ExElement.Def.Abstract<ExElement> implements CollectionTransform<C1, C2, ExElement> {
		private ModelType.SingleTyped<C2> theTargetModelType;
		private ExSort.ExRootSort theSort;
		private boolean isPropagateToParent;

		public FlattenCollectionTransform(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType);
		}

		public ExSort.ExRootSort getSort() {
			return theSort;
		}

		public boolean isPropagateToParent() {
			return isPropagateToParent;
		}

		@QonfigAttributeGetter("to")
		@Override
		public ModelType<? extends C2> getTargetModelType() {
			return theTargetModelType;
		}

		@Override
		public void update(ExpressoQIS session, ModelType<C1> sourceModelType) throws QonfigInterpretationException {
			update(session);
			if (!session.forChildren("reverse").isEmpty())
				throw new QonfigInterpretationException("Reverse is not yet implemented",
					session.attributes().get("reverse").getLocatedContent());
			theSort = syncChild(ExSort.ExRootSort.class, theSort, session, "sort");
			isPropagateToParent = session.attributes().get("propagate-to-parent").getValue(boolean.class, false);

			String targetModelTypeName = session.getAttributeText("to");
			switch (targetModelTypeName.toLowerCase()) {
			case "list":
				if (theSort != null)
					theTargetModelType = (ModelType.SingleTyped<C2>) ModelTypes.SortedCollection;
				else
					theTargetModelType = (ModelType.SingleTyped<C2>) ModelTypes.Collection;
				break;
			case "sorted-list":
				theTargetModelType = (ModelType.SingleTyped<C2>) ModelTypes.SortedCollection;
				break;
			case "set":
				if (theSort != null)
					theTargetModelType = (ModelType.SingleTyped<C2>) ModelTypes.SortedSet;
				else
					theTargetModelType = (ModelType.SingleTyped<C2>) ModelTypes.Set;
				break;
			case "sorted-set":
				theTargetModelType = (ModelType.SingleTyped<C2>) ModelTypes.SortedSet;
				break;
			case "event":
			case "action":
			case "value":
			case "value-set":
			case "map":
			case "sorted-map":
			case "multi-map":
			case "sorted-multi-map":
				throw new QonfigInterpretationException("Unsupported collection flatten target: '" + targetModelTypeName + "'",
					session.attributes().get("to").getLocatedContent());
			default:
				throw new QonfigInterpretationException("Unrecognized model type target: '" + targetModelTypeName + "'",
					session.attributes().get("to").getLocatedContent());
			}
		}

		@Override
		public Interpreted<C1, ?, ?, ?, C2, ?> interpret(ExElement.Interpreted<?> parent) throws ExpressoInterpretationException {
			return new Interpreted<>(this, parent);
		}

		static class Interpreted<C1 extends ObservableCollection<?>, CV1 extends C1, S, T, C2 extends ObservableCollection<?>, CV2 extends C2>
		extends ExElement.Interpreted.Abstract<ExElement> implements Operation.Interpreted<C1, CV1, C2, CV2, ExElement> {
			private TypeToken<T> theResultType;
			private ExSort.ExRootSort.Interpreted<T> theSort;
			private Function<CollectionDataFlow<?, ?, ?>, CollectionDataFlow<?, ?, T>> theFlatten;

			Interpreted(FlattenCollectionTransform<C1, C2> definition, ExElement.Interpreted<?> parent) {
				super(definition, parent);
			}

			@Override
			public FlattenCollectionTransform<C1, C2> getDefinition() {
				return (FlattenCollectionTransform<C1, C2>) super.getDefinition();
			}

			@Override
			public void update(ModelInstanceType<C1, CV1> sourceType, InterpretedExpressoEnv env) throws ExpressoInterpretationException {
				Class<?> raw = TypeTokens.getRawType(sourceType.getType(0));
				TypeToken<T> resultType;
				if (ObservableValue.class.isAssignableFrom(raw)) {
					resultType = (TypeToken<T>) sourceType.getType(0).resolveType(ObservableValue.class.getTypeParameters()[0]);
					theFlatten = flatValues(resultType);
				} else if (ObservableCollection.class.isAssignableFrom(raw)) {
					System.err.println("WARNING: Collection flatten is not fully implemented.  Many options are unsupported.");
					// TODO Use map options, reverse
					resultType = (TypeToken<T>) sourceType.getType(0).resolveType(ObservableCollection.class.getTypeParameters()[0]);
					theFlatten = flatCollections(resultType);
				} else if (CollectionDataFlow.class.isAssignableFrom(raw)) {
					System.err.println("WARNING: Collection flatten is not fully implemented.  Many options are unsupported.");
					// TODO Use map options, reverse
					resultType = (TypeToken<T>) sourceType.getType(0).resolveType(CollectionDataFlow.class.getTypeParameters()[2]);
					theFlatten = flatFlows(resultType);
				} else
					throw new ExpressoInterpretationException("Cannot flatten a collection of type " + sourceType.getType(0),
						reporting().getFileLocation().getPosition(0), 0);
				theResultType = resultType;

				update(env);

				if (getDefinition().getSort() == null) {
					if (theSort != null)
						theSort.destroy();
					theSort = null;
				} else if (theSort == null || theSort.getIdentity() != getDefinition().getSort().getIdentity()) {
					if (theSort != null)
						theSort.destroy();
					theSort = (ExSort.ExRootSort.Interpreted<T>) getDefinition().getSort().interpret(this);
				}
				if (theSort != null)
					theSort.update(getExpressoEnv());
			}

			@Override
			public ModelInstanceType<? extends C2, ? extends CV2> getTargetType() {
				return (ModelInstanceType<? extends C2, ? extends CV2>) getDefinition().getTargetModelType().forTypes(theResultType);
			}

			@Override
			public BetterList<InterpretedValueSynth<?, ?>> getComponents() {
				if (theSort != null)
					return theSort.getComponents();
				else
					return BetterList.empty();
			}

			@Override
			public Operation.Instantiator<CV1, CV2> instantiate() {
				return new Instantiator<S, T, CV1, CV2>((ModelType<CV2>) getDefinition().getTargetModelType(),
					theSort == null ? null : theSort.instantiateSort(), theFlatten);
			}
		}

		static <T> Function<CollectionDataFlow<?, ?, ?>, CollectionDataFlow<?, ?, T>> flatValues(TypeToken<T> resultType) {
			return LambdaUtils.printableFn(flow -> flow.flattenValues(resultType, v -> (ObservableValue<? extends T>) v), "flatValues",
				null);
		}

		static <T> Function<CollectionDataFlow<?, ?, ?>, CollectionDataFlow<?, ?, T>> flatCollections(TypeToken<T> resultType) {
			return LambdaUtils.printableFn(flow -> flow.flatMap(resultType, v -> ((ObservableCollection<? extends T>) v).flow()),
				"FlatCollections", null);
		}

		static <T> Function<CollectionDataFlow<?, ?, ?>, CollectionDataFlow<?, ?, T>> flatFlows(TypeToken<T> resultType) {
			return LambdaUtils.printableFn(flow -> flow.flatMap(resultType, v -> (CollectionDataFlow<?, ?, ? extends T>) v), "flatFlows",
				null);
		}

		static class Instantiator<S, T, CV1 extends ObservableCollection<?>, CV2 extends ObservableCollection<?>>
		implements FlowTransformInstantiator<CV1, CV2, S, T> {
			private final ModelType<CV2> theTargetModelType;
			private final ModelValueInstantiator<Comparator<? super T>> theSort;
			private Function<CollectionDataFlow<?, ?, ?>, CollectionDataFlow<?, ?, T>> theFlatten;

			Instantiator(ModelType<CV2> targetModelType, ModelValueInstantiator<Comparator<? super T>> sort,
				Function<CollectionDataFlow<?, ?, ?>, CollectionDataFlow<?, ?, T>> flatten) {
				theTargetModelType = targetModelType;
				theSort = sort;
				theFlatten = flatten;
			}

			@Override
			public void instantiate() {
				if (theSort != null)
					theSort.instantiate();
			}

			@Override
			public boolean isDifferent(ModelSetInstance sourceModels, ModelSetInstance newModels) throws ModelInstantiationException {
				if (theSort != null && theSort.get(sourceModels) != theSort.get(newModels))
					return true;
				return false;
			}

			@Override
			public CV2 transform(CV1 source, ModelSetInstance models) throws ModelInstantiationException {
				return (CV2) transformToFlow(source, models).collect();
			}

			@Override
			public CollectionDataFlow<?, ?, T> transformFlow(CollectionDataFlow<?, ?, S> source, ModelSetInstance models)
				throws ModelInstantiationException {
				Comparator<? super T> sort = theSort == null ? null : theSort.get(models);
				CollectionDataFlow<?, ?, T> mapped = theFlatten.apply(source);
				boolean distinct = theTargetModelType == ModelTypes.SortedSet || theTargetModelType == ModelTypes.Set;
				if (distinct) {
					if (theSort != null)
						mapped = mapped.distinctSorted(sort, false);
					else
						mapped = mapped.distinct();
				} else if (theSort != null)
					mapped = mapped.sorted(sort);
				return mapped;
			}

			@Override
			public CollectionDataFlow<?, ?, T> transformToFlow(CV1 source, ModelSetInstance models) throws ModelInstantiationException {
				return transformFlow((CollectionDataFlow<?, ?, S>) source.flow(), models);
			}
		}
	}

	static class CrossCollectionTransform<C extends ObservableCollection<?>> extends ExElement.Def.Abstract<ExElement>
	implements CollectionTransform<C, ObservableCollection<?>, ExElement> {
		private String theSourceAs;
		private CompiledExpression theWith;

		public CrossCollectionTransform(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType);
		}

		public String getSourceAs() {
			return theSourceAs;
		}

		@QonfigAttributeGetter("with")
		public CompiledExpression getWith() {
			return theWith;
		}

		@Override
		public ModelType<? extends ObservableCollection<?>> getTargetModelType() {
			return ModelTypes.Collection;
		}

		@Override
		public void update(ExpressoQIS session, ModelType<C> sourceModelType) throws QonfigInterpretationException {
			super.update(session);
			theSourceAs = session.getAttributeText("source-as");
			theWith = getAttributeExpression("with", session);
		}

		@Override
		public ExpressoTransformations.Operation.Interpreted<C, ?, ObservableCollection<?>, ?, ? extends ExElement> interpret(
			ExElement.Interpreted<?> parent) throws ExpressoInterpretationException {
			throw new ExpressoInterpretationException("Not yet implemented", reporting().getFileLocation().getPosition(0), 0);
		}
	}

	@ExElementTraceable(toolkit = ExpressoBaseV0_1.BASE,
		qonfigType = "where-contained",
		interpretation = WhereContainedCollectionTransform.Interpreted.class)
	static class WhereContainedCollectionTransform<C extends ObservableCollection<?>> extends TypePreservingTransform<C>
	implements CollectionTransform<C, C, ExElement> {
		private CompiledExpression theFilter;
		private boolean isInclusive;

		WhereContainedCollectionTransform(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType);
		}

		@QonfigAttributeGetter("flter")
		public CompiledExpression getFilter() {
			return theFilter;
		}

		public boolean isInclusive() {
			return isInclusive;
		}

		@Override
		public void update(ExpressoQIS session, ModelType<C> sourceModelType) throws QonfigInterpretationException {
			super.update(session, sourceModelType);
			theFilter = getAttributeExpression("filter", session);
			isInclusive = session.getAttribute("inclusive", boolean.class);
		}

		@Override
		protected Interpreted<?, C, ?> tppInterpret(ExElement.Interpreted<?> parent) {
			return new Interpreted<>(this, parent);
		}

		static class Interpreted<T, C extends ObservableCollection<?>, CV extends C> extends TypePreservingTransform.Interpreted<C, CV> {
			private InterpretedValueSynth<ObservableCollection<?>, ObservableCollection<?>> theFilter;

			Interpreted(WhereContainedCollectionTransform<C> definition, ExElement.Interpreted<?> parent) {
				super(definition, parent);
			}

			@Override
			public WhereContainedCollectionTransform<C> getDefinition() {
				return (WhereContainedCollectionTransform<C>) super.getDefinition();
			}

			public InterpretedValueSynth<ObservableCollection<?>, ObservableCollection<?>> getRefresh() {
				return theFilter;
			}

			@Override
			public void update(ModelInstanceType<C, CV> sourceType, InterpretedExpressoEnv env) throws ExpressoInterpretationException {
				super.update(sourceType, env);
				theFilter = interpret(getDefinition().getFilter(), ModelTypes.Collection.any());
			}

			@Override
			public BetterList<InterpretedValueSynth<?, ?>> getComponents() {
				return BetterList.of(theFilter);
			}

			@Override
			public Operation.Instantiator<CV, CV> instantiate() {
				return new Instantiator<>(theFilter.instantiate(), getDefinition().isInclusive());
			}

			@Override
			public String toString() {
				return "whereContained(" + theFilter + ", " + getDefinition().isInclusive() + ")";
			}
		}

		static class Instantiator<T, CV extends ObservableCollection<?>> implements FlowTransformInstantiator<CV, CV, T, T> {
			private final ModelValueInstantiator<ObservableCollection<?>> theFilter;
			private final boolean isInclusive;

			Instantiator(ModelValueInstantiator<ObservableCollection<?>> filter, boolean inclusive) {
				theFilter = filter;
				isInclusive = inclusive;
			}

			@Override
			public void instantiate() {
				theFilter.instantiate();
			}

			@Override
			public CollectionDataFlow<?, ?, T> transformFlow(CollectionDataFlow<?, ?, T> source, ModelSetInstance models)
				throws ModelInstantiationException {
				ObservableCollection<?> filter = theFilter.get(models);
				return source.whereContained(filter.flow(), isInclusive);
			}

			@Override
			public CollectionDataFlow<?, ?, T> transformToFlow(CV source, ModelSetInstance models) throws ModelInstantiationException {
				return transformFlow((CollectionDataFlow<?, ?, T>) source.flow(), models);
			}

			@Override
			public boolean isDifferent(ModelSetInstance sourceModels, ModelSetInstance newModels) throws ModelInstantiationException {
				ObservableCollection<?> filter = theFilter.get(sourceModels);
				return theFilter.forModelCopy(filter, sourceModels, newModels) != filter;
			}
		}
	}

	static class GroupByCollectionTransform<C extends ObservableCollection<?>> extends ExElement.Def.Abstract<ExElement>
	implements CollectionTransform<C, ObservableMultiMap<?, ?>, ExElement> {
		private String theSourceAs;
		private CompiledExpression theKey;

		public GroupByCollectionTransform(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType);
		}

		public String getSourceAs() {
			return theSourceAs;
		}

		@QonfigAttributeGetter("key")
		public CompiledExpression getKey() {
			return theKey;
		}

		@Override
		public ModelType<? extends ObservableMultiMap<?, ?>> getTargetModelType() {
			return ModelTypes.MultiMap;
		}

		@Override
		public void update(ExpressoQIS session, ModelType<C> sourceModelType) throws QonfigInterpretationException {
			super.update(session);
			theSourceAs = session.getAttributeText("source-as");
			theKey = getAttributeExpression("key", session);
		}

		@Override
		public ExpressoTransformations.Operation.Interpreted<C, ?, ObservableMultiMap<?, ?>, ?, ? extends ExElement> interpret(
			ExElement.Interpreted<?> parent) throws ExpressoInterpretationException {
			throw new ExpressoInterpretationException("Not yet implemented", reporting().getFileLocation().getPosition(0), 0);
		}
	}

	@ExElementTraceable(toolkit = ExpressoBaseV0_1.BASE, qonfigType = "size", interpretation = SizeCollectionTransform.Interpreted.class)
	static class SizeCollectionTransform<C extends ObservableCollection<?>> extends ExElement.Def.Abstract<ExElement>
	implements CollectionTransform<C, SettableValue<?>, ExElement> {
		private QonfigValueType.Literal theType;

		SizeCollectionTransform(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType);
		}

		@QonfigAttributeGetter("type")
		public QonfigValueType.Literal getType() {
			return theType;
		}

		@Override
		public ModelType<? extends SettableValue<?>> getTargetModelType() {
			return ModelTypes.Value;
		}

		@Override
		public void update(ExpressoQIS session, ModelType<C> sourceModelType) throws QonfigInterpretationException {
			super.update(session);
			theType = session.getAttribute("type", QonfigValueType.Literal.class);
			if (!"value".equals(theType.getValue()))
				throw new QonfigInterpretationException("Only 'value' type may be used for size of collections, not " + theType.getValue(),
					session.attributes().get("type").getLocatedContent());
		}

		@Override
		public Interpreted<C, ?> interpret(ExElement.Interpreted<?> parent) throws ExpressoInterpretationException {
			return new Interpreted<>(this, parent);
		}

		static class Interpreted<C extends ObservableCollection<?>, CV extends C> extends ExElement.Interpreted.Abstract<ExElement>
		implements Operation.Interpreted<C, CV, SettableValue<?>, SettableValue<Integer>, ExElement> {
			Interpreted(SizeCollectionTransform<C> definition, ExElement.Interpreted<?> parent) {
				super(definition, parent);
			}

			@Override
			public ModelInstanceType<? extends SettableValue<?>, ? extends SettableValue<Integer>> getTargetType() {
				return ModelTypes.Value.INT;
			}

			@Override
			public void update(ModelInstanceType<C, CV> sourceType, InterpretedExpressoEnv env) throws ExpressoInterpretationException {
				super.update(env);
			}

			@Override
			public BetterList<InterpretedValueSynth<?, ?>> getComponents() {
				return BetterList.empty();
			}

			@Override
			public Operation.Instantiator<CV, SettableValue<Integer>> instantiate() {
				return new Instantiator<>();
			}
		}

		static class Instantiator<CV extends ObservableCollection<?>>
		implements Operation.EfficientCopyingInstantiator<CV, SettableValue<Integer>> {
			@Override
			public boolean isEfficientCopy() {
				return true;
			}

			@Override
			public void instantiate() {
			}

			@Override
			public SettableValue<Integer> transform(CV source, ModelSetInstance models) throws ModelInstantiationException {
				return new CollectionSizeObservable<>(source);
			}

			@Override
			public boolean isDifferent(ModelSetInstance sourceModels, ModelSetInstance newModels) throws ModelInstantiationException {
				return false;
			}

			@Override
			public CV getSource(SettableValue<Integer> value) {
				return ((CollectionSizeObservable<CV>) value).getSource();
			}

			@Override
			public SettableValue<Integer> forModelCopy(SettableValue<Integer> prevValue, CV newSource, ModelSetInstance sourceModels,
				ModelSetInstance newModels) throws ModelInstantiationException {
				if (newSource == ((CollectionSizeObservable<CV>) prevValue).getSource())
					return prevValue;
				return new CollectionSizeObservable<>(newSource);
			}
		}

		static class CollectionSizeObservable<CV extends ObservableCollection<?>> extends SettableValue.AlwaysDisabledValue<Integer> {
			private final CV theCollection;

			CollectionSizeObservable(CV collection) {
				super(collection.observeSize(), __ -> "Size cannot be assigned directly");
				theCollection = collection;
			}

			protected CV getSource() {
				return theCollection;
			}

			@Override
			public String toString() {
				return theCollection.getIdentity() + ".size()";
			}
		}
	}

	@ExElementTraceable(toolkit = ExpressoBaseV0_1.BASE,
		qonfigType = "collect",
		interpretation = CollectCollectionTransform.Interpreted.class)
	static class CollectCollectionTransform<C extends ObservableCollection<?>> extends TypePreservingTransform<C>
	implements CollectionTransform<C, C, ExElement> {
		Boolean isActive;

		CollectCollectionTransform(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType);
		}

		@QonfigAttributeGetter("active")
		public Boolean isActive() {
			return isActive;
		}

		@Override
		public void update(ExpressoQIS session, ModelType<C> sourceModelType) throws QonfigInterpretationException {
			super.update(session, sourceModelType);
			isActive = session.getAttribute("active", Boolean.class);
		}

		@Override
		protected Interpreted<?, C, ?> tppInterpret(ExElement.Interpreted<?> parent) {
			return new Interpreted<>(this, parent);
		}

		@Override
		public String toString() {
			return "collect(" + (isActive == null ? "" : (isActive ? "active" : "passive")) + ")";
		}

		static class Interpreted<T, C extends ObservableCollection<?>, CV extends C> extends TypePreservingTransform.Interpreted<C, CV> {
			Interpreted(CollectCollectionTransform<C> definition, ExElement.Interpreted<?> parent) {
				super(definition, parent);
			}

			@Override
			public CollectCollectionTransform<C> getDefinition() {
				return (CollectCollectionTransform<C>) super.getDefinition();
			}

			@Override
			public void update(ModelInstanceType<C, CV> sourceType, InterpretedExpressoEnv env) throws ExpressoInterpretationException {
				super.update(sourceType, env);
			}

			@Override
			public BetterList<InterpretedValueSynth<?, ?>> getComponents() {
				return BetterList.empty();
			}

			@Override
			public Operation.Instantiator<CV, CV> instantiate() {
				return new Instantiator<>(getDefinition().isActive());
			}

			@Override
			public String toString() {
				return getDefinition().toString();
			}
		}

		static class Instantiator<T, CV extends ObservableCollection<?>> implements FlowTransformInstantiator<CV, CV, T, T> {
			private final Boolean isActive;

			Instantiator(Boolean active) {
				isActive = active;
			}

			@Override
			public void instantiate() {
			}

			@Override
			public CollectionDataFlow<?, ?, T> transformFlow(CollectionDataFlow<?, ?, T> source, ModelSetInstance models)
				throws ModelInstantiationException {
				boolean reallyActive;
				if (isActive != null)
					reallyActive = isActive.booleanValue();
				else
					reallyActive = !source.prefersPassive();
				if (reallyActive)
					return source.collectActive(models.getUntil()).flow();
				else
					return source.collectPassive().flow();
			}

			@Override
			public CollectionDataFlow<?, ?, T> transformToFlow(CV source, ModelSetInstance models) throws ModelInstantiationException {
				return transformFlow((CollectionDataFlow<?, ?, T>) source.flow(), models);
			}

			@Override
			public boolean isDifferent(ModelSetInstance sourceModels, ModelSetInstance newModels) throws ModelInstantiationException {
				return false;
			}
		}
	}
}
