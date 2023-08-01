package org.observe.expresso.qonfig;

import static org.observe.expresso.qonfig.ExpressoBaseV0_1.creator;

import java.util.function.Supplier;

import org.observe.Observable;
import org.observe.Observer;
import org.observe.SettableValue;
import org.observe.Subscription;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.InterpretedExpressoEnv;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ModelType;
import org.observe.expresso.ModelType.ModelInstanceType;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableModelSet.InterpretedValueSynth;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.VariableType;
import org.observe.expresso.qonfig.ExElement.Def;
import org.observe.expresso.qonfig.ExpressoTransformations.ObservableTransform;
import org.observe.expresso.qonfig.ExpressoTransformations.Operation;
import org.observe.expresso.qonfig.ExpressoTransformations.TypePreservingTransform;
import org.observe.util.TypeTokens;
import org.qommons.Causable;
import org.qommons.Identifiable;
import org.qommons.LambdaUtils;
import org.qommons.collect.BetterList;
import org.qommons.config.QonfigElement.QonfigValue;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretationException;
import org.qommons.config.QonfigInterpreterCore;
import org.qommons.io.LocatedFilePosition;
import org.qommons.io.LocatedPositionedContent;

import com.google.common.reflect.TypeToken;

public class ObservableTransformations {
	private ObservableTransformations() {
	}

	public static void configureTransformation(QonfigInterpreterCore.Builder interpreter) {
		interpreter.createWith("no-init", ObservableTransform.class, creator(NoInitObservableTransform::new));
		interpreter.createWith("skip", ObservableTransform.class, creator(SkippingObservableTransform::new));
		interpreter.createWith("take", ObservableTransform.class, creator(TakeObservableTransform::new));
		interpreter.createWith("take-until", ObservableTransform.class, creator(TakeUntilObservableTransform::new));
		interpreter.createWith("map-to", ObservableTransform.class, creator(MappedObservableTransform::new));
		interpreter.createWith("filter", ObservableTransform.class, creator(FilteredObservableTransform::new));
		interpreter.createWith("filter-by-type", ObservableTransform.class, creator(TypeFilteredObservableTransform::new));
	}

	static class NoInitObservableTransform extends TypePreservingTransform<Observable<?>>
	implements ObservableTransform<Observable<?>, ExElement> {
		public NoInitObservableTransform(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType);
		}

		@Override
		protected Interpreted<?> tppInterpret(ExElement.Interpreted<?> parent) {
			return new Interpreted<>(this, parent);
		}

		static class Interpreted<T> extends TypePreservingTransform.Interpreted<Observable<?>, Observable<T>> implements
		ExpressoTransformations.Operation.EfficientCopyingInterpreted<Observable<?>, Observable<T>, Observable<?>, Observable<T>, ExElement> {
			public Interpreted(NoInitObservableTransform definition, ExElement.Interpreted<?> parent) {
				super(definition, parent);
			}

			@Override
			public boolean isEfficientCopy() {
				return true;
			}

			@Override
			public Observable<T> transform(Observable<T> source, ModelSetInstance models) throws ModelInstantiationException {
				return new NoInitObservable<>(source);
			}

			@Override
			public BetterList<InterpretedValueSynth<?, ?>> getComponents() {
				return BetterList.empty();
			}

			@Override
			public boolean isDifferent(ModelSetInstance sourceModels, ModelSetInstance newModels) throws ModelInstantiationException {
				return false;
			}

			@Override
			public Observable<T> getSource(Observable<T> value) {
				return ((NoInitObservable<T>) value).getWrapped();
			}

			@Override
			public Observable<T> forModelCopy(Observable<T> prevValue, Observable<T> newSource, ModelSetInstance sourceModels,
				ModelSetInstance newModels) throws ModelInstantiationException {
				Observable<T> oldSource = ((NoInitObservable<T>) prevValue).getWrapped();
				if (newSource == oldSource)
					return prevValue;
				else
					return new NoInitObservable<>(newSource);
			}

			@Override
			public String toString() {
				return "noInit";
			}
		}

		static class NoInitObservable<T> extends Observable.NoInitObservable<T> {
			NoInitObservable(Observable<T> wrap) {
				super(wrap);
			}

			@Override
			public Observable<T> getWrapped() {
				return super.getWrapped();
			}
		}
	}

	static class SkippingObservableTransform extends TypePreservingTransform<Observable<?>>
	implements ObservableTransform<Observable<?>, ExElement> {
		private int theSkipCount;

		public SkippingObservableTransform(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType);
		}

		public int getSkipCount() {
			return theSkipCount;
		}

		@Override
		public void update(ExpressoQIS session, ModelType<Observable<?>> sourceModelType) throws QonfigInterpretationException {
			super.update(session, sourceModelType);
			theSkipCount = Integer.parseInt(session.getAttributeText("times"));
		}

		@Override
		protected Interpreted<?> tppInterpret(ExElement.Interpreted<?> parent) {
			return new Interpreted<>(this, parent);
		}

		static class Interpreted<T> extends TypePreservingTransform.Interpreted<Observable<?>, Observable<T>> implements
		ExpressoTransformations.Operation.EfficientCopyingInterpreted<Observable<?>, Observable<T>, Observable<?>, Observable<T>, ExElement> {
			public Interpreted(SkippingObservableTransform definition, org.observe.expresso.qonfig.ExElement.Interpreted<?> parent) {
				super(definition, parent);
			}

			@Override
			public SkippingObservableTransform getDefinition() {
				return (SkippingObservableTransform) super.getDefinition();
			}

			@Override
			public boolean isEfficientCopy() {
				return true;
			}

			@Override
			public Observable<T> transform(Observable<T> source, ModelSetInstance models) throws ModelInstantiationException {
				int times = getDefinition().getSkipCount();
				return new SkippingObservable<>(source, LambdaUtils.constantSupplier(times, () -> String.valueOf(times), null));
			}

			@Override
			public BetterList<InterpretedValueSynth<?, ?>> getComponents() {
				return BetterList.empty();
			}

			@Override
			public boolean isDifferent(ModelSetInstance sourceModels, ModelSetInstance newModels) throws ModelInstantiationException {
				return false;
			}

			@Override
			public Observable<T> getSource(Observable<T> value) {
				return ((SkippingObservable<T>) value).getWrapped();
			}

			@Override
			public Observable<T> forModelCopy(Observable<T> prevValue, Observable<T> newSource, ModelSetInstance sourceModels,
				ModelSetInstance newModels) throws ModelInstantiationException {
				SkippingObservable<T> skipping = (SkippingObservable<T>) prevValue;
				Observable<T> oldSource = skipping.getWrapped();
				if (newSource == oldSource)
					return prevValue;
				else
					return new SkippingObservable<>(newSource, skipping.getTimes());
			}

			@Override
			public String toString() {
				return "skip(" + getDefinition().getSkipCount() + ")";
			}
		}

		static class SkippingObservable<T> extends Observable.SkippingObservable<T> {
			SkippingObservable(Observable<T> wrap, Supplier<Integer> times) {
				super(wrap, times);
			}

			@Override
			protected Observable<T> getWrapped() {
				return super.getWrapped();
			}

			@Override
			protected Supplier<Integer> getTimes() {
				return super.getTimes();
			}
		}
	}

	static class TakeObservableTransform extends TypePreservingTransform<Observable<?>>
	implements ObservableTransform<Observable<?>, ExElement> {
		private int theTimes;

		public TakeObservableTransform(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType);
		}

		public int getTimes() {
			return theTimes;
		}

		@Override
		protected Interpreted<?> tppInterpret(org.observe.expresso.qonfig.ExElement.Interpreted<?> parent) {
			return new Interpreted<>(this, parent);
		}

		static class Interpreted<T> extends TypePreservingTransform.Interpreted<Observable<?>, Observable<T>> implements
		ExpressoTransformations.Operation.EfficientCopyingInterpreted<Observable<?>, Observable<T>, Observable<?>, Observable<T>, ExElement> {
			Interpreted(TakeObservableTransform definition, ExElement.Interpreted<?> parent) {
				super(definition, parent);
			}

			@Override
			public TakeObservableTransform getDefinition() {
				return (TakeObservableTransform) super.getDefinition();
			}

			@Override
			public boolean isEfficientCopy() {
				return true;
			}

			@Override
			public Observable<T> transform(Observable<T> source, ModelSetInstance models) throws ModelInstantiationException {
				return new TakeObservable<>(source, getDefinition().getTimes());
			}

			@Override
			public BetterList<InterpretedValueSynth<?, ?>> getComponents() {
				return BetterList.empty();
			}

			@Override
			public boolean isDifferent(ModelSetInstance sourceModels, ModelSetInstance newModels) throws ModelInstantiationException {
				return false;
			}

			@Override
			public Observable<T> getSource(Observable<T> value) {
				return ((TakeObservable<T>) value).getWrapped();
			}

			@Override
			public Observable<T> forModelCopy(Observable<T> prevValue, Observable<T> newSource, ModelSetInstance sourceModels,
				ModelSetInstance newModels) throws ModelInstantiationException {
				TakeObservable<T> take = (TakeObservable<T>) prevValue;
				Observable<T> oldSource = take.getWrapped();
				if (newSource == oldSource)
					return prevValue;
				else
					return new TakeObservable<>(newSource, take.getTimes());
			}

			@Override
			public String toString() {
				return "take(" + getDefinition().getTimes() + ")";
			}
		}

		static class TakeObservable<T> extends Observable.ObservableTakenTimes<T> {
			TakeObservable(Observable<T> wrap, int times) {
				super(wrap, times);
			}

			@Override
			protected int getTimes() {
				return super.getTimes();
			}

			@Override
			protected Observable<T> getWrapped() {
				return super.getWrapped();
			}
		}
	}

	static class TakeUntilObservableTransform extends TypePreservingTransform<Observable<?>>
	implements ObservableTransform<Observable<?>, ExElement> {
		private CompiledExpression theUntil;

		public TakeUntilObservableTransform(Def<?> parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType);
		}

		public CompiledExpression getUntil() {
			return theUntil;
		}

		@Override
		public void update(ExpressoQIS session, ModelType<Observable<?>> sourceModelType) throws QonfigInterpretationException {
			super.update(session, sourceModelType);
			theUntil = session.getAttributeExpression("until");
		}

		@Override
		protected Interpreted<?> tppInterpret(ExElement.Interpreted<?> parent) {
			return new Interpreted<>(this, parent);
		}

		static class Interpreted<T> extends TypePreservingTransform.Interpreted<Observable<?>, Observable<T>> implements
		ExpressoTransformations.Operation.EfficientCopyingInterpreted<Observable<?>, Observable<T>, Observable<?>, Observable<T>, ExElement> {
			private InterpretedValueSynth<Observable<?>, Observable<?>> theUntil;

			public Interpreted(TakeUntilObservableTransform definition, ExElement.Interpreted<?> parent) {
				super(definition, parent);
			}

			@Override
			public TakeUntilObservableTransform getDefinition() {
				return (TakeUntilObservableTransform) super.getDefinition();
			}

			@Override
			public boolean isEfficientCopy() {
				return true;
			}

			@Override
			public void update(ModelInstanceType<Observable<?>, Observable<T>> sourceType, InterpretedExpressoEnv env)
				throws ExpressoInterpretationException {
				super.update(sourceType, env);
				theUntil = getDefinition().getUntil().interpret(ModelTypes.Event.any(), getExpressoEnv());
			}

			@Override
			public Observable<T> transform(Observable<T> source, ModelSetInstance models) throws ModelInstantiationException {
				Observable<?> until = theUntil.get(models);
				return new TakeUntilObservable<>(source, until);
			}

			@Override
			public BetterList<InterpretedValueSynth<?, ?>> getComponents() {
				return BetterList.of(theUntil);
			}

			@Override
			public boolean isDifferent(ModelSetInstance sourceModels, ModelSetInstance newModels) throws ModelInstantiationException {
				Observable<?> until = theUntil.get(sourceModels);
				return sourceModels != theUntil.forModelCopy(until, sourceModels, newModels);
			}

			@Override
			public Observable<T> getSource(Observable<T> value) {
				return ((TakeUntilObservable<T>) value).getWrapped();
			}

			@Override
			public Observable<T> forModelCopy(Observable<T> prevValue, Observable<T> newSource, ModelSetInstance sourceModels,
				ModelSetInstance newModels) throws ModelInstantiationException {
				TakeUntilObservable<T> takeUntil = (TakeUntilObservable<T>) prevValue;
				Observable<?> until = theUntil.forModelCopy(takeUntil.getUntil(), sourceModels, newModels);
				if (newSource == takeUntil.getWrapped() && until == takeUntil.getUntil())
					return prevValue;
				else
					return new TakeUntilObservable<>(newSource, until);
			}

			@Override
			public String toString() {
				return "takeUntil(" + theUntil + ")";
			}
		}

		static class TakeUntilObservable<T> extends Observable.ObservableTakenUntil<T> {
			public TakeUntilObservable(Observable<T> wrap, Observable<?> until) {
				super(wrap, until, true);
			}

			@Override
			protected Observable<?> getUntil() {
				return super.getUntil();
			}

			@Override
			protected Observable<T> getWrapped() {
				return super.getWrapped();
			}
		}
	}

	static class MappedObservableTransform extends ExElement.Def.Abstract<ExElement>
	implements ObservableTransform<Observable<?>, ExElement> {
		private String theSourceName;
		private CompiledExpression theMap;

		public MappedObservableTransform(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType);
		}

		@Override
		public ModelType<? extends Observable<?>> getTargetModelType() {
			return ModelTypes.Event;
		}

		public String getSourceName() {
			return theSourceName;
		}

		public CompiledExpression getMap() {
			return theMap;
		}

		@Override
		public void update(ExpressoQIS session, ModelType<Observable<?>> sourceModelType) throws QonfigInterpretationException {
			super.update(session);
			theSourceName = session.getAttributeText("source-as");
			theMap = session.getAttributeExpression("map");
			getAddOn(ExWithElementModel.Def.class).<Interpreted<?, ?>, SettableValue<?>> satisfyElementValueType(theSourceName,
				ModelTypes.Value, (interp, env) -> ModelTypes.Value.forType(interp.getSourceType()));
		}

		@Override
		public Operation.Interpreted<Observable<?>, ?, Observable<?>, ?, ? extends ExElement> interpret(ExElement.Interpreted<?> parent)
			throws ExpressoInterpretationException {
			return new Interpreted<>(this, parent);
		}

		static class Interpreted<S, T> extends ExElement.Interpreted.Abstract<ExElement>
		implements Operation.EfficientCopyingInterpreted<Observable<?>, Observable<S>, Observable<?>, Observable<T>, ExElement> {
			private TypeToken<S> theSourceType;
			private ModelInstanceType<Observable<?>, Observable<T>> theType;
			private InterpretedValueSynth<SettableValue<?>, SettableValue<T>> theMap;

			Interpreted(MappedObservableTransform definition, ExElement.Interpreted<?> parent) {
				super(definition, parent);
			}

			@Override
			public MappedObservableTransform getDefinition() {
				return (MappedObservableTransform) super.getDefinition();
			}

			@Override
			public boolean isEfficientCopy() {
				return true;
			}

			public TypeToken<S> getSourceType() {
				return theSourceType;
			}

			@Override
			public void update(ModelInstanceType<Observable<?>, Observable<S>> sourceType, InterpretedExpressoEnv env)
				throws ExpressoInterpretationException {
				theSourceType = (TypeToken<S>) sourceType.getType(0);
				super.update(env);
				theMap = getDefinition().getMap().interpret(ModelTypes.Value.<SettableValue<T>> anyAs(), getExpressoEnv());
				theType = ModelTypes.Event.forType((TypeToken<T>) theMap.getType().getType(0));
			}

			@Override
			public ModelInstanceType<? extends Observable<?>, ? extends Observable<T>> getTargetType() {
				return theType;
			}

			@Override
			public BetterList<InterpretedValueSynth<?, ?>> getComponents() {
				return BetterList.of(theMap);
			}

			@Override
			public Observable<T> transform(Observable<S> source, ModelSetInstance models) throws ModelInstantiationException {
				SettableValue<S> sourceV = SettableValue.build(theSourceType).build();
				getAddOn(ExWithElementModel.Interpreted.class).satisfyElementValue(getDefinition().getSourceName(), models, sourceV);
				SettableValue<T> targetV = theMap.get(models);
				return new MappedObservable<>(source, sourceV, targetV);
			}

			@Override
			public boolean isDifferent(ModelSetInstance sourceModels, ModelSetInstance newModels) throws ModelInstantiationException {
				SettableValue<T> map = theMap.get(sourceModels);
				return map != theMap.forModelCopy(map, sourceModels, newModels);
			}

			@Override
			public Observable<S> getSource(Observable<T> value) {
				return ((MappedObservable<S, T>) value).getWrapped();
			}

			@Override
			public Observable<T> forModelCopy(Observable<T> prevValue, Observable<S> newSource, ModelSetInstance sourceModels,
				ModelSetInstance newModels) throws ModelInstantiationException {
				MappedObservable<S, T> mapped = (MappedObservable<S, T>) prevValue;
				SettableValue<T> newTarget = theMap.forModelCopy(mapped.getTargetValue(), sourceModels, newModels);
				if (newSource == mapped.getWrapped() && newTarget == mapped.getTargetValue())
					return prevValue;
				else {
					SettableValue<S> newSourceV = SettableValue.build(theSourceType).build();
					getAddOn(ExWithElementModel.Interpreted.class).satisfyElementValue(getDefinition().getSourceName(), newModels,
						newSourceV);
					return new MappedObservable<>(newSource, newSourceV, newTarget);
				}
			}

			@Override
			public String toString() {
				return "map(" + theMap + ")";
			}
		}

		static class MappedObservable<S, T> extends Observable.WrappingObservable<S, T> {
			private final SettableValue<S> theSourceValue;
			private final SettableValue<T> theTargetValue;

			MappedObservable(Observable<S> source, SettableValue<S> sourceValue, SettableValue<T> targetValue) {
				super(source);
				theSourceValue = sourceValue;
				theTargetValue = targetValue;
			}

			@Override
			protected Observable<S> getWrapped() {
				return super.getWrapped();
			}

			SettableValue<T> getTargetValue() {
				return theTargetValue;
			}

			@Override
			protected Object createIdentity() {
				return Identifiable.wrap(getWrapped().getIdentity(), "map", theTargetValue.getIdentity());
			}

			@Override
			public Subscription subscribe(Observer<? super T> observer) {
				return getWrapped().subscribe(new Observer<S>() {
					@Override
					public <V extends S> void onNext(V value) {
						theSourceValue.set(value, null);
						T target = theTargetValue.get();
						observer.onNext(target);
					}

					@Override
					public void onCompleted(Causable cause) {
						observer.onCompleted(cause);
					}
				});
			}
		}
	}

	static class FilteredObservableTransform extends TypePreservingTransform<Observable<?>>
	implements ObservableTransform<Observable<?>, ExElement> {
		private String theSourceName;
		private CompiledExpression theTest;

		FilteredObservableTransform(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType);
		}

		public String getSourceName() {
			return theSourceName;
		}

		public CompiledExpression getTest() {
			return theTest;
		}

		@Override
		public void update(ExpressoQIS session, ModelType<Observable<?>> sourceModelType) throws QonfigInterpretationException {
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

		static class Interpreted<T> extends TypePreservingTransform.Interpreted<Observable<?>, Observable<T>> implements
		ExpressoTransformations.Operation.EfficientCopyingInterpreted<Observable<?>, Observable<T>, Observable<?>, Observable<T>, ExElement> {
			private TypeToken<T> theSourceType;
			private InterpretedValueSynth<SettableValue<?>, SettableValue<String>> theTest;

			Interpreted(FilteredObservableTransform definition, ExElement.Interpreted<?> parent) {
				super(definition, parent);
			}

			@Override
			public FilteredObservableTransform getDefinition() {
				return (FilteredObservableTransform) super.getDefinition();
			}

			@Override
			public boolean isEfficientCopy() {
				return true;
			}

			public TypeToken<T> getSourceType() {
				return theSourceType;
			}

			@Override
			public void update(ModelInstanceType<Observable<?>, Observable<T>> sourceType, InterpretedExpressoEnv env)
				throws ExpressoInterpretationException {
				theSourceType = (TypeToken<T>) sourceType.getType(0);
				super.update(sourceType, env);
				theTest = ExpressoTransformations.parseFilter(getDefinition().getTest(), getExpressoEnv(), false);
			}

			@Override
			public BetterList<InterpretedValueSynth<?, ?>> getComponents() {
				return BetterList.of(theTest);
			}

			@Override
			public Observable<T> transform(Observable<T> source, ModelSetInstance models) throws ModelInstantiationException {
				SettableValue<T> sourceV = SettableValue.build(theSourceType).build();
				getAddOn(ExWithElementModel.Interpreted.class).satisfyElementValue(getDefinition().getSourceName(), models, sourceV);
				SettableValue<String> testV = theTest.get(models);
				return new FilteredObservable<>(source, sourceV, testV);
			}

			@Override
			public boolean isDifferent(ModelSetInstance sourceModels, ModelSetInstance newModels) throws ModelInstantiationException {
				SettableValue<String> test = theTest.get(sourceModels);
				return test != theTest.forModelCopy(test, sourceModels, newModels);
			}

			@Override
			public Observable<T> getSource(Observable<T> value) {
				return ((FilteredObservable<T>) value).getWrapped();
			}

			@Override
			public Observable<T> forModelCopy(Observable<T> prevValue, Observable<T> newSource, ModelSetInstance sourceModels,
				ModelSetInstance newModels) throws ModelInstantiationException {
				FilteredObservable<T> filtered = (FilteredObservable<T>) prevValue;
				SettableValue<String> newTest = theTest.forModelCopy(filtered.getTest(), sourceModels, newModels);
				if (newSource == filtered.getWrapped() && newTest == filtered.getTest())
					return prevValue;
				else {
					SettableValue<T> newSourceV = SettableValue.build(theSourceType).build();
					getAddOn(ExWithElementModel.Interpreted.class).satisfyElementValue(getDefinition().getSourceName(), newModels,
						newSourceV);
					return new FilteredObservable<>(newSource, newSourceV, newTest);
				}
			}

			@Override
			public String toString() {
				return "filter(" + theTest + ")";
			}
		}

		static class FilteredObservable<T> extends Observable.WrappingObservable<T, T> {
			private final SettableValue<T> theSourceValue;
			private final SettableValue<String> theTest;

			FilteredObservable(Observable<T> wrapped, SettableValue<T> sourceValue, SettableValue<String> test) {
				super(wrapped);
				theSourceValue = sourceValue;
				theTest = test;
			}

			@Override
			protected Observable<T> getWrapped() {
				return super.getWrapped();
			}

			SettableValue<String> getTest() {
				return theTest;
			}

			@Override
			protected Object createIdentity() {
				return Identifiable.wrap(getWrapped().getIdentity(), "filter", theTest.getIdentity());
			}

			@Override
			public Subscription subscribe(Observer<? super T> observer) {
				return getWrapped().subscribe(new Observer<T>() {
					@Override
					public <V extends T> void onNext(V value) {
						theSourceValue.set(value, null);
						if (theTest.get() == null)
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

	static class TypeFilteredObservableTransform extends TypePreservingTransform<Observable<?>>
	implements ObservableTransform<Observable<?>, ExElement> {
		private VariableType theType;

		TypeFilteredObservableTransform(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType);
		}

		public VariableType getType() {
			return theType;
		}

		@Override
		public void update(ExpressoQIS session, ModelType<Observable<?>> sourceModelType) throws QonfigInterpretationException {
			super.update(session, sourceModelType);
			QonfigValue typeQV = session.getAttributeQV("type");
			theType = typeQV == null ? null
				: VariableType.parseType(new LocatedPositionedContent.Default(typeQV.fileLocation, typeQV.position));
			if (theType instanceof VariableType.Parameterized)
				throw new QonfigInterpretationException("Parameterized types are not permitted for filter type",
					new LocatedFilePosition(typeQV.fileLocation, typeQV.position.getPosition(0)), typeQV.position.length());
		}

		@Override
		protected Interpreted<?> tppInterpret(ExElement.Interpreted<?> parent) {
			return new Interpreted<>(this, parent);
		}

		static class Interpreted<T> extends TypePreservingTransform.Interpreted<Observable<?>, Observable<T>> implements
		ExpressoTransformations.Operation.EfficientCopyingInterpreted<Observable<?>, Observable<T>, Observable<?>, Observable<T>, ExElement> {
			private Class<?> theType;

			Interpreted(TypeFilteredObservableTransform definition, ExElement.Interpreted<?> parent) {
				super(definition, parent);
			}

			@Override
			public TypeFilteredObservableTransform getDefinition() {
				return (TypeFilteredObservableTransform) super.getDefinition();
			}

			@Override
			public boolean isEfficientCopy() {
				return true;
			}

			public Class<?> getType() {
				return theType;
			}

			@Override
			public void update(ModelInstanceType<Observable<?>, Observable<T>> sourceType, InterpretedExpressoEnv env)
				throws ExpressoInterpretationException {
				super.update(sourceType, env);
				theType = TypeTokens.getRawType(getDefinition().getType().getType(getExpressoEnv()));
			}

			@Override
			public BetterList<InterpretedValueSynth<?, ?>> getComponents() {
				return BetterList.empty();
			}

			@Override
			public Observable<T> transform(Observable<T> source, ModelSetInstance models) throws ModelInstantiationException {
				return new TypeFilteredObservable<>(source, theType);
			}

			@Override
			public boolean isDifferent(ModelSetInstance sourceModels, ModelSetInstance newModels) throws ModelInstantiationException {
				return false;
			}

			@Override
			public Observable<T> getSource(Observable<T> value) {
				return ((TypeFilteredObservable<T>) value).getWrapped();
			}

			@Override
			public Observable<T> forModelCopy(Observable<T> prevValue, Observable<T> newSource, ModelSetInstance sourceModels,
				ModelSetInstance newModels) throws ModelInstantiationException {
				TypeFilteredObservable<T> filtered = (TypeFilteredObservable<T>) prevValue;
				if (newSource == filtered.getWrapped())
					return prevValue;
				else
					return new TypeFilteredObservable<>(newSource, theType);
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
}
