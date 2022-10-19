package org.observe.expresso;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

import org.observe.Observable;
import org.observe.ObservableValue;
import org.observe.ObservableValueEvent;
import org.observe.SettableValue;
import org.observe.collect.ObservableCollection;
import org.observe.expresso.Expression.ExpressoParseException;
import org.observe.expresso.ModelType.ModelInstanceType;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.ObservableModelSet.ValueContainer;
import org.observe.util.TypeTokens;
import org.qommons.ClassMap;
import org.qommons.ClassMap.TypeMatch;
import org.qommons.Identifiable;
import org.qommons.Identifiable.AbstractIdentifiable;
import org.qommons.LambdaUtils;
import org.qommons.Transaction;
import org.qommons.collect.MutableCollectionElement.StdMsg;
import org.qommons.config.QonfigInterpretationException;
import org.qommons.config.QonfigInterpreterCore.CoreSession;
import org.qommons.config.SpecialSession;

import com.google.common.reflect.TypeToken;

/** A special session with extra utility for the Expresso toolkits */
public class ExpressoQIS implements SpecialSession<ExpressoQIS> {
	public static final String TOOLKIT_NAME = "Expresso-Core";
	static final String SATISFIERS_KEY = ExpressoQIS.class.getSimpleName() + "$SATISFIERS";
	public static final String MODEL_VALUE_OWNER_PROP = "expresso-interpreter-model-value-owner";
	public static final String LOCAL_MODEL_KEY = "ExpressoLocalModel";

	private final CoreSession theWrapped;

	ExpressoQIS(CoreSession session) {
		theWrapped = session;
	}

	@Override
	public CoreSession getWrapped() {
		return theWrapped;
	}

	/** @return The expresso parser to use to parse expressions under this session */
	public ExpressoParser getExpressoParser() {
		return (ExpressoParser) theWrapped.get("EXPRESSO_PARSER");
	}

	/**
	 * @param parser The expresso parser to use to parse expressions under this session
	 * @return This session
	 */
	public ExpressoQIS setExpressoParser(ExpressoParser parser) {
		theWrapped.put("EXPRESSO_PARSER", parser);
		return this;
	}

	/** @return The expresso environment to use to evaluate expressions under this session */
	public ExpressoEnv getExpressoEnv() {
		return (ExpressoEnv) theWrapped.get("EXPRESSO_ENV");
	}

	/**
	 * @param env The expresso environment to use to evaluate expressions under this session
	 * @return This session
	 */
	public ExpressoQIS setExpressoEnv(ExpressoEnv env) {
		theWrapped.put("EXPRESSO_ENV", env);
		return this;
	}

	/**
	 * @param models The models to use for evaluating expressions under this session (or null to keep this session's)
	 * @param classView The class view to use for evaluating expressions under this session (or null to keep this session's)
	 * @return This session
	 */
	public ExpressoQIS setModels(ObservableModelSet models, ClassView classView) {
		setExpressoEnv(getExpressoEnv().with(models, classView));
		return this;
	}

	/**
	 * All {@link ObservableModelSet.ValueContainer}s for expressions parsed under this session should be
	 * {@link ObservableModelSet.ValueContainer#get(ModelSetInstance) satisfied} with a model set wrapped by this method if this element
	 * extends with-local-models.
	 *
	 * @param models The model instance
	 * @return The wrapped model instance containing data for this element's local models
	 */
	public ModelSetInstance wrapLocal(ModelSetInstance models) {
		ObservableModelSet.Wrapped localModel = (ObservableModelSet.Wrapped) get(LOCAL_MODEL_KEY);
		return localModel == null ? models : localModel.wrap(models).build();
	}

	public ObservableExpression getAttributeExpression(String attrName) throws QonfigInterpretationException {
		QonfigExpression expression = getAttribute(attrName, QonfigExpression.class);
		if (expression == null)
			return null;
		try {
			return getExpressoParser().parse(expression.text);
		} catch (ExpressoParseException e) {
			throw new QonfigInterpretationException(e);
		}
	}

	public <M, MV extends M> ValueContainer<M, MV> getAttribute(String attrName, ModelInstanceType<M, MV> type,
		Supplier<Function<ModelSetInstance, MV>> defaultValue) throws QonfigInterpretationException {
		QonfigExpression expression = getAttribute(attrName, QonfigExpression.class);
		if (expression == null) {
			if (defaultValue == null)
				return null;
			return new ValueContainer<M, MV>() {
				final Function<ModelSetInstance, MV> def = defaultValue == null ? null : defaultValue.get();

				@Override
				public ModelInstanceType<M, MV> getType() {
					return type;
				}

				@Override
				public MV get(ModelSetInstance models) {
					return def == null ? null : def.apply(models);
				}
			};
		}
		ObservableExpression obEx;
		try {
			obEx = getExpressoParser().parse(expression.text);
		} catch (ExpressoParseException e) {
			throw new QonfigInterpretationException(e);
		}
		return obEx.evaluate(type, getExpressoEnv());
	}

	public ObservableExpression getValueExpression() throws QonfigInterpretationException {
		QonfigExpression expression = getValue(QonfigExpression.class, null);
		if (expression == null)
			return null;
		try {
			return getExpressoParser().parse(expression.text);
		} catch (ExpressoParseException e) {
			throw new QonfigInterpretationException(e);
		}
	}

	public <M, MV extends M> ValueContainer<M, MV> getValue(ModelInstanceType<M, MV> type,
		Supplier<Function<ModelSetInstance, MV>> defaultValue) throws QonfigInterpretationException {
		Object value = getElement().getValue();
		if (value == null) {
			if (defaultValue == null)
				return null;
			return new ValueContainer<M, MV>() {
				final Function<ModelSetInstance, MV> def = defaultValue.get();

				@Override
				public ModelInstanceType<M, MV> getType() {
					return type;
				}

				@Override
				public MV get(ModelSetInstance models) {
					return def.apply(models);
				}
			};
		} else if (!(value instanceof QonfigExpression))
			throw new QonfigInterpretationException(
				"Value of " + getElement() + " is a " + value.getClass().getName() + ", not an expression");

		ObservableExpression obEx;
		try {
			obEx = getExpressoParser().parse(((QonfigExpression) value).text);
		} catch (ExpressoParseException e) {
			throw new QonfigInterpretationException(e);
		}
		return obEx.evaluate(type, getExpressoEnv());
	}

	public <T> ValueContainer<SettableValue<?>, SettableValue<T>> getAttributeAsValue(String attrName, TypeToken<T> type,
		Supplier<Function<ModelSetInstance, SettableValue<T>>> defaultValue) throws QonfigInterpretationException {
		return getAttribute(attrName, ModelTypes.Value.forType(type), defaultValue);
	}

	public <T> ValueContainer<SettableValue<?>, SettableValue<T>> getAttributeAsValue(String attrName, Class<T> type,
		Supplier<Function<ModelSetInstance, SettableValue<T>>> defaultValue) throws QonfigInterpretationException {
		return getAttributeAsValue(attrName, TypeTokens.get().of(type), defaultValue);
	}

	public <T> ValueContainer<SettableValue<?>, SettableValue<T>> getValueAsValue(TypeToken<T> type,
		Supplier<Function<ModelSetInstance, SettableValue<T>>> defaultValue) throws QonfigInterpretationException {
		return getValue(ModelTypes.Value.forType(type), defaultValue);
	}

	public <T> ValueContainer<SettableValue<?>, SettableValue<T>> getValueAsValue(Class<T> type,
		Supplier<Function<ModelSetInstance, SettableValue<T>>> defaultValue) throws QonfigInterpretationException {
		return getValueAsValue(TypeTokens.get().of(type), defaultValue);
	}

	public <T> ValueContainer<ObservableCollection<?>, ObservableCollection<T>> getAttributeAsCollection(String attrName, TypeToken<T> type,
		Supplier<Function<ModelSetInstance, ObservableCollection<T>>> defaultValue) throws QonfigInterpretationException {
		return getAttribute(attrName, ModelTypes.Collection.forType(type), defaultValue);
	}

	public <T> ValueContainer<ObservableCollection<?>, ObservableCollection<T>> getAttributeAsCollection(String attrName, Class<T> type,
		Supplier<Function<ModelSetInstance, ObservableCollection<T>>> defaultValue) throws QonfigInterpretationException {
		return getAttributeAsCollection(attrName, TypeTokens.get().of(type), defaultValue);
	}

	public <T> ValueContainer<ObservableCollection<?>, ObservableCollection<T>> getValueAsCollection(TypeToken<T> type,
		Supplier<Function<ModelSetInstance, ObservableCollection<T>>> defaultValue) throws QonfigInterpretationException {
		return getValue(ModelTypes.Collection.forType(type), defaultValue);
	}

	public <T> ValueContainer<ObservableCollection<?>, ObservableCollection<T>> getValueAsCollection(Class<T> type,
		Supplier<Function<ModelSetInstance, ObservableCollection<T>>> defaultValue) throws QonfigInterpretationException {
		return getValueAsCollection(TypeTokens.get().of(type), defaultValue);
	}

	static class SatisfierHolder<M, MV extends M, MV2 extends MV, V> {
		final SuppliedModelValue<M, MV> modelValue;
		final Class<V> interpretedValueType;
		final Supplier<MV2> satisfier;
		final BiConsumer<MV2, V> interpretedValue;

		SatisfierHolder(SuppliedModelValue<M, MV> modelValue, Class<V> interpretedValueType, Supplier<MV2> satisfier,
			BiConsumer<MV2, V> interpretedValue) {
			this.modelValue = modelValue;
			this.interpretedValueType = interpretedValueType;
			this.satisfier = satisfier;
			this.interpretedValue = interpretedValue;
		}
	}

	public <IV, M, MV extends M, MV2 extends MV> ExpressoQIS satisfy(SuppliedModelValue<M, MV> modelValue, Class<IV> interpretedValueType,
		Supplier<MV2> satisfier, BiConsumer<MV2, IV> interpretedValue) {
		ClassMap<Map<SuppliedModelValue<?, ?>, SatisfierHolder<M, MV, MV2, IV>>> satisfierMap;
		satisfierMap = (ClassMap<Map<SuppliedModelValue<?, ?>, SatisfierHolder<M, MV, MV2, IV>>>) get(SATISFIERS_KEY);
		satisfierMap.computeIfAbsent(interpretedValueType, HashMap::new).put(modelValue,
			new SatisfierHolder<>(modelValue, interpretedValueType, satisfier, interpretedValue));
		return this;
	}

	public boolean isSatisfied(SuppliedModelValue<?, ?> modelValue, Class<?> interpretedValueType) {
		ClassMap<Map<SuppliedModelValue<?, ?>, SatisfierHolder<?, ?, ?, ?>>> satisfierMap;
		satisfierMap = (ClassMap<Map<SuppliedModelValue<?, ?>, SatisfierHolder<?, ?, ?, ?>>>) get(SATISFIERS_KEY);
		Map<SuppliedModelValue<?, ?>, SatisfierHolder<?, ?, ?, ?>> ism = satisfierMap.get(interpretedValueType, TypeMatch.SUPER_TYPE);
		return ism != null && ism.containsKey(modelValue);
	}

	public SuppliedModelOwner getModelValueOwner() {
		return (SuppliedModelOwner) getWrapped().get(MODEL_VALUE_OWNER_PROP);
	}

	public <V> void startInterpretingAs(Class<V> interpretedValueType, ModelSetInstance models) {
		Map<SuppliedModelValue<?, ?>, SatisfierHolder<?, ?, ?, V>> satisfierMap;
		satisfierMap = ((ClassMap<Map<SuppliedModelValue<?, ?>, SatisfierHolder<?, ?, ?, V>>>) get(SATISFIERS_KEY))//
			.get(interpretedValueType, TypeMatch.SUPER_TYPE);
		SettableValue<SuppliedModelValue.Satisfier> satisfierV = models.get(SuppliedModelValue.SATISFIER_PLACEHOLDER_NAME,
			ModelTypes.Value.forType(SuppliedModelValue.Satisfier.class));
		satisfierV.set(new SuppliedModelValue.Satisfier() {
			@Override
			public <M, MV extends M> MV satisfy(SuppliedModelValue<M, MV> value) {
				if (satisfierMap == null)
					return null;
				SatisfierHolder<M, MV, ?, V> satisfier = (SatisfierHolder<M, MV, ?, V>) satisfierMap.get(value);
				if (satisfier == null)
					throw new IllegalStateException(
						"Model value " + value + " has not been supported for interpreted value type " + interpretedValueType.getName());
				return satisfier.satisfier.get();
			}
		}, null);
	}

	public <MV extends Identifiable> MV getIfSupported(MV modelValue, SimpleModelSupport<MV> support) {
		if (modelValue != null && modelValue.getIdentity() instanceof SimpleModelIdentity
			&& ((SimpleModelIdentity<?>) modelValue.getIdentity()).getSupport() == support)
			return support.getSettable(modelValue);
		else
			return null;
	}

	public <V> void installInterpretedValue(V interpretedValue, ModelSetInstance models) {
		Map<SuppliedModelValue<?, ?>, SatisfierHolder<Object, Object, Object, V>> satisfierMap;
		satisfierMap = ((ClassMap<Map<SuppliedModelValue<?, ?>, SatisfierHolder<Object, Object, Object, V>>>) get(SATISFIERS_KEY))//
			.get(interpretedValue.getClass(), TypeMatch.SUPER_TYPE);
		if (satisfierMap == null)
			return;
		for (SatisfierHolder<Object, Object, Object, V> satisfier : satisfierMap.values()) {
			if (satisfier == null)
				continue; // Satisfier doesn't care
			Object satisfied = models.get(satisfier.modelValue.getName(), null);
			if (satisfied != null)
				satisfier.interpretedValue.accept(satisfied, interpretedValue);
		}
	}

	@Override
	public String toString() {
		return getWrapped().toString();
	}

	public static class OneTimeSettableValue<T> implements SettableValue<T> {
		private final TypeToken<T> theType;
		private T theValue;

		public OneTimeSettableValue(TypeToken<T> type) {
			theType = type;
		}

		@Override
		public TypeToken<T> getType() {
			return theType;
		}

		@Override
		public Object getIdentity() {
			return this;
		}

		@Override
		public long getStamp() {
			return 0;
		}

		@Override
		public T get() {
			return theValue;
		}

		void set(T value) {
		}

		@Override
		public Transaction lock(boolean write, Object cause) {
			return Transaction.NONE;
		}

		@Override
		public Transaction tryLock(boolean write, Object cause) {
			return Transaction.NONE;
		}

		@Override
		public boolean isLockSupported() {
			return true;
		}

		@Override
		public <V extends T> T set(V value, Object cause) throws IllegalArgumentException, UnsupportedOperationException {
			if (value == null)
				throw new NullPointerException();
			else if (theValue != null)
				throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
			theValue = value;
			return null;
		}

		@Override
		public <V extends T> String isAcceptable(V value) {
			return StdMsg.UNSUPPORTED_OPERATION;
		}

		@Override
		public ObservableValue<String> isEnabled() {
			return SettableValue.ALWAYS_DISABLED;
		}

		@Override
		public Observable<ObservableValueEvent<T>> noInitChanges() {
			return Observable.empty();
		}
	}

	public static class ProducerModelValueSupport<T> extends ObservableValue.FlattenedObservableValue<T> implements SettableValue<T> {
		public ProducerModelValueSupport(TypeToken<T> type, T defaultValue) {
			super(SettableValue.build(TypeTokens.get().keyFor(ObservableValue.class).<ObservableValue<T>> parameterized(type)).build(),
				LambdaUtils.constantSupplier(defaultValue, () -> String.valueOf(defaultValue), defaultValue));
		}

		@Override
		protected SettableValue<ObservableValue<T>> getWrapped() {
			return (SettableValue<ObservableValue<T>>) super.getWrapped();
		}

		public void install(ObservableValue<T> value) {
			getWrapped().set(value, null);
		}

		public ObservableValue<T> getValue() {
			return getWrapped().get();
		}

		@Override
		public boolean isLockSupported() {
			if (!getWrapped().isLockSupported())
				return false;
			ObservableValue<T> value = getValue();
			return value == null || value.isLockSupported();
		}

		@Override
		public Transaction lock(boolean write, Object cause) {
			Transaction wrapperLock = getWrapped().lock();
			ObservableValue<T> value = getWrapped().get();
			if (value == null)
				return wrapperLock;
			else if (value instanceof SettableValue)
				return Transaction.and(wrapperLock, ((SettableValue<T>) value).lock(write, cause));
			else
				return Transaction.and(wrapperLock, value.lock());
		}

		@Override
		public Transaction tryLock(boolean write, Object cause) {
			Transaction wrapperLock = getWrapped().tryLock();
			if (wrapperLock == null)
				return null;
			ObservableValue<T> value = getWrapped().get();
			if (value == null)
				return wrapperLock;
			else if (value instanceof SettableValue) {
				Transaction valueLock = ((SettableValue<T>) value).tryLock(write, cause);
				if (valueLock == null) {
					wrapperLock.close();
					return null;
				}
				return Transaction.and(wrapperLock, valueLock);
			} else {
				Transaction valueLock = value.lock();
				if (valueLock == null) {
					wrapperLock.close();
					return null;
				}
				return Transaction.and(wrapperLock, valueLock);
			}
		}

		@Override
		public <V extends T> T set(V value, Object cause) throws IllegalArgumentException, UnsupportedOperationException {
			try (Transaction wrapperLock = getWrapped().lock()) {
				ObservableValue<T> oValue = getWrapped().get();
				if (oValue instanceof SettableValue)
					return ((SettableValue<T>) oValue).set(value, cause);
				else
					throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
			}
		}

		@Override
		public <V extends T> String isAcceptable(V value) {
			ObservableValue<T> oValue = getWrapped().get();
			if (oValue instanceof SettableValue)
				return ((SettableValue<T>) oValue).isAcceptable(value);
			else
				return StdMsg.UNSUPPORTED_OPERATION;
		}

		@Override
		public ObservableValue<String> isEnabled() {
			return ObservableValue.flatten(getWrapped().map(v -> v instanceof SettableValue ? ((SettableValue<T>) v).isEnabled() : null));
		}
	}

	public static abstract class SimpleModelSupport<MV extends Identifiable> implements Supplier<MV> {
		protected abstract MV getSettable(MV modelValue);
	}

	public static class SimpleModelValueSupport<T> extends SimpleModelSupport<SettableValue<T>> {
		private final TypeToken<T> theType;
		private final T theInitialValue;

		public SimpleModelValueSupport(Class<T> type, T initialValue) {
			this(TypeTokens.get().of(type), initialValue);
		}

		public SimpleModelValueSupport(TypeToken<T> type, T initialValue) {
			theType = type;
			theInitialValue = initialValue;
		}

		public TypeToken<T> getType() {
			return theType;
		}

		public T getInitialValue() {
			return theInitialValue;
		}

		@Override
		public SettableValue<T> get() {
			return new SimpleModelValue<>(this);
		}

		@Override
		protected SettableValue<T> getSettable(SettableValue<T> modelValue) {
			return ((SimpleModelValue<T>) modelValue).getSettable();
		}
	}

	static class SimpleModelIdentity<T> {
		final SimpleModelValue<T> theValue;

		SimpleModelIdentity(SimpleModelValue<T> value) {
			theValue = value;
		}

		public SimpleModelValueSupport<T> getSupport() {
			return theValue.getSupport();
		}
	}

	public static class SimpleModelValue<T> extends AbstractIdentifiable implements SettableValue<T> {
		private final SimpleModelValueSupport<T> theSupport;
		private SettableValue<T> theValue;

		public SimpleModelValue(SimpleModelValueSupport<T> support) {
			theSupport = support;
		}

		public SimpleModelValueSupport<T> getSupport() {
			return theSupport;
		}

		SettableValue<T> getSettable() {
			return theValue;
		}

		private SettableValue<T> init() {
			if (theValue == null)
				theValue = SettableValue.build(theSupport.getType()).withValue(theSupport.getInitialValue()).build();
			return theValue;
		}

		@Override
		protected Object createIdentity() {
			return new SimpleModelIdentity<>(this);
		}

		@Override
		public T get() {
			return init().get();
		}

		@Override
		public Observable<ObservableValueEvent<T>> noInitChanges() {
			return init().noInitChanges();
		}

		@Override
		public TypeToken<T> getType() {
			return theSupport.getType();
		}

		@Override
		public long getStamp() {
			return init().getStamp();
		}

		@Override
		public Transaction lock(boolean write, Object cause) {
			return init().lock(write, cause);
		}

		@Override
		public Transaction tryLock(boolean write, Object cause) {
			return init().tryLock(write, cause);
		}

		@Override
		public boolean isLockSupported() {
			return init().isLockSupported();
		}

		@Override
		public <V extends T> T set(V value, Object cause) throws IllegalArgumentException, UnsupportedOperationException {
			throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
		}

		@Override
		public <V extends T> String isAcceptable(V value) {
			return StdMsg.UNSUPPORTED_OPERATION;
		}

		@Override
		public ObservableValue<String> isEnabled() {
			return SettableValue.ALWAYS_DISABLED;
		}
	}
}
