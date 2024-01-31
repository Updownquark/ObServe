package org.observe.supertest.value;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

import org.observe.ObservableValue;
import org.observe.SettableValue;
import org.observe.XformOptions;
import org.observe.supertest.ChainLinkGenerator;
import org.observe.supertest.ObservableChainLink;
import org.observe.supertest.OperationRejection;
import org.observe.supertest.TestValueType;
import org.observe.supertest.TypeTransformation;
import org.observe.supertest.collect.MappedCollectionLink;
import org.qommons.Transactable;
import org.qommons.collect.MutableCollectionElement.StdMsg;
import org.qommons.testing.TestHelper;
import org.qommons.testing.TestHelper.RandomAction;

import com.google.common.reflect.TypeToken;

/**
 * Tests {@link ObservableValue#map(Function)}
 *
 * @param <S> The source value type
 * @param <T> The target value type
 */
public class MappedValueLink<S, T> extends ObservableValueLink<S, T> implements ValueSourcedLink<S, T> {
	/** Generates {@link MappedValueLink}s */
	public static final ChainLinkGenerator GENERATE = new ChainLinkGenerator() {
		@Override
		public <T> double getAffinity(ObservableChainLink<?, T> sourceLink, TestValueType targetType) {
			if (!(sourceLink instanceof ObservableValueLink))
				return 0;
			else if (!MappedCollectionLink.supportsTransform(sourceLink.getType(), targetType, true, false))
				return 0;
			ObservableValueLink<?, T> sourceVL = (ObservableValueLink<?, T>) sourceLink;
			if (sourceVL.isTypeCheat())
				return 0;
			return .2;
		}

		@Override
		public <T, X> ObservableChainLink<T, X> deriveLink(String path, ObservableChainLink<?, T> sourceLink, TestValueType targetType,
			TestHelper helper) {
			ObservableValueLink<?, T> sourceVL = (ObservableValueLink<?, T>) sourceLink;
			TypeTransformation<T, X> transform = MappedCollectionLink.transform(sourceVL.getType(), targetType, helper, true, false);
			SettableValue<TypeTransformation<T, X>> txValue = SettableValue
				.build((TypeToken<TypeTransformation<T, X>>) (TypeToken<?>) new TypeToken<Object>() {
				}).build();
			txValue.set(transform, null);
			boolean variableMap = helper.getBoolean();
			boolean needsUpdateReeval = !sourceVL.isCheckingOldValues() || variableMap;
			boolean cache = helper.getBoolean(.75);
			boolean withReverse = transform.supportsReverse() && sourceVL.getValue() instanceof SettableValue && helper.getBoolean(.95);
			boolean fireIfUnchanged = needsUpdateReeval || helper.getBoolean();
			boolean reEvalOnUpdate = needsUpdateReeval || helper.getBoolean();
			XformOptions.XformDef options = new XformOptions.XformDef(new XformOptions.SimpleXformOptions()//
				.cache(cache).reEvalOnUpdate(reEvalOnUpdate).fireIfUnchanged(fireIfUnchanged));

			return new MappedValueLink<>(path, sourceVL, transform.getType(), txValue, variableMap, withReverse, options);
		}
	};

	private final SettableValue<TypeTransformation<S, T>> theMap;
	private final boolean isMapVariable;
	private final boolean isReversible;
	private final XformOptions.XformDef theOptions;

	/**
	 * @param path The path for this link
	 * @param sourceLink The source value for this link
	 * @param type The type of this link
	 * @param map The transform value for this link
	 * @param mapVariable Whether to vary the transform function periodically
	 * @param reversible Whether to test setting the value
	 * @param options The map options
	 */
	public MappedValueLink(String path, ObservableValueLink<?, S> sourceLink, TestValueType type,
		SettableValue<TypeTransformation<S, T>> map, boolean mapVariable, boolean reversible, XformOptions.XformDef options) {
		super(path, sourceLink, type, options.isCached());
		theMap = map;
		isMapVariable = mapVariable;
		isReversible = reversible;
		theOptions = options;
	}

	@Override
	public ObservableValueLink<?, S> getSourceLink() {
		return (ObservableValueLink<?, S>) super.getSourceLink();
	}

	@Override
	protected Transactable getLocking() {
		if (isMapVariable)
			return Transactable.combine(theMap, super.getLocking());
		else
			return super.getLocking();
	}

	@Override
	public boolean isTransactional() {
		return getSourceLink().isTransactional();
	}

	@Override
	protected ObservableValue<T> createValue(TestHelper helper) {
		ObservableValue<S> sourceValue = getSourceLink().getValue();
		if (isMapVariable)
			sourceValue = sourceValue.refresh(theMap.noInitChanges());
		Consumer<XformOptions> options = opts -> {
			opts.cache(theOptions.isCached()).reEvalOnUpdate(theOptions.isReEvalOnUpdate()).fireIfUnchanged(theOptions.isFireIfUnchanged());
		};
		TypeToken<T> type = (TypeToken<T>) getType().getType();
		if (isReversible)
			return ((SettableValue<S>) sourceValue).map(type, src -> src == null ? null : theMap.get().map(src),
				mapped -> theMap.get().reverse(mapped), options);
		else
			return sourceValue.map(type, src -> src == null ? null : theMap.get().map(src), options);
	}

	@Override
	public double getModificationAffinity() {
		double affinity = super.getModificationAffinity();
		// if(getValue() instanceof SettableValue && theValueSupplier!=null)
		if (isMapVariable)
			affinity++;
		return affinity;
	}

	@Override
	public void tryModify(RandomAction action, TestHelper helper) {
		super.tryModify(action, helper);
		if (isMapVariable) {
			action.or(1, () -> {
				TypeTransformation<S, T> oldMap = theMap.get();
				TypeTransformation<S, T> newMap = MappedCollectionLink.transform(getSourceLink().getType(), getType(), helper, true,
					oldMap.supportsReverse());
				if (helper.isReproducing())
					System.out.println("Map " + oldMap + " -> " + newMap);
				theMap.set(newMap, null);
			});
		}
	}

	@Override
	public void expectSet(T value, OperationRejection rejection) {
		if (!isReversible) {
			rejection.reject(StdMsg.UNSUPPORTED_OPERATION);
			return;
		}
		S reversed = theMap.get().reverse(value);
		T remapped = theMap.get().map(reversed);
		if (!Objects.equals(value, remapped)) {
			rejection.reject(StdMsg.ILLEGAL_ELEMENT);
			return;
		}
		getSourceLink().expectSet(reversed, rejection);
	}

	@Override
	public void validate(boolean transactionEnd) throws AssertionError {
		if (!transactionEnd && isTransactional())
			return;
		S sourceValue = getSourceLink().getValue().get();
		T expectedValue = sourceValue == null ? null : theMap.get().map(sourceValue);
		getTester().check(expectedValue);
	}

	@Override
	public String toString() {
		return "map(" + theMap.get() + (isMapVariable ? ", variable" : "") + ")";
	}
}
