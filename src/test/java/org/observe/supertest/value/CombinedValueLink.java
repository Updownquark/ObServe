package org.observe.supertest.value;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

import org.observe.ObservableValue;
import org.observe.SettableValue;
import org.observe.XformOptions;
import org.observe.XformOptions.XformDef;
import org.observe.supertest.BiTypeTransformation;
import org.observe.supertest.ChainLinkGenerator;
import org.observe.supertest.ObservableChainLink;
import org.observe.supertest.ObservableChainTester;
import org.observe.supertest.OperationRejection;
import org.observe.supertest.TestValueType;
import org.observe.supertest.collect.CombinedCollectionLink;
import org.qommons.Transactable;
import org.qommons.TriFunction;
import org.qommons.collect.MutableCollectionElement.StdMsg;
import org.qommons.testing.TestHelper;
import org.qommons.testing.TestHelper.RandomAction;

import com.google.common.reflect.TypeToken;

/**
 * Tests {@link ObservableValue#combine(java.util.function.BiFunction, ObservableValue)} and
 * {@link ObservableValue#combine(TriFunction, ObservableValue, ObservableValue)}
 *
 * @param <S> The source value type
 * @param <V> The combination value type
 * @param <T> The combined value type
 */
public class CombinedValueLink<S, V, T> extends ObservableValueLink<S, T> implements ValueSourcedLink<S, T> {
	/** Generates {@link CombinedValueLink}s */
	public static final ChainLinkGenerator GENERATE = new ChainLinkGenerator() {
		@Override
		public <T> double getAffinity(ObservableChainLink<?, T> sourceLink, TestValueType targetType) {
			if (!(sourceLink instanceof ObservableValueLink))
				return 0;
			ObservableValueLink<?, T> sourceVL = (ObservableValueLink<?, T>) sourceLink;
			if (sourceVL.isTypeCheat() || !CombinedCollectionLink.supportsTransform(sourceVL.getType(), null, targetType, true, false))
				return 0;
			return 1;
		}

		@Override
		public <T, X> ObservableChainLink<T, X> deriveLink(String path, ObservableChainLink<?, T> sourceLink, TestValueType targetType,
			TestHelper helper) {
			ObservableValueLink<?, T> sourceCL = (ObservableValueLink<?, T>) sourceLink;
			BiTypeTransformation<T, ?, X> transform = CombinedCollectionLink.transform(sourceCL.getType(), null, targetType, helper, true,
				false);
			return deriveLink(path, sourceCL, transform, helper);
		}

		private <S, V, T> CombinedValueLink<S, V, T> deriveLink(String path, ObservableValueLink<?, S> sourceVL,
			BiTypeTransformation<S, V, T> transform, TestHelper helper) {
			int valueCount = helper.getInt(1, 2); // Value combination only supports 1 or 2 combined values
			List<SettableValue<V>> values = new ArrayList<>(valueCount);
			for (int i = 0; i < valueCount; i++)
				values.add(SettableValue.build((TypeToken<V>) transform.getValueType().getType()).build());
			Function<TestHelper, V> valueSupplier = (Function<TestHelper, V>) ObservableChainTester.SUPPLIERS.get(transform.getValueType());
			for (int i = 0; i < valueCount; i++)
				values.get(i).set(valueSupplier.apply(helper), null);
			Function<List<V>, V> valueCombination = CombinedCollectionLink.getValueCombination(transform.getValueType());

			boolean needsUpdateReeval = !sourceVL.isCheckingOldValues();
			boolean cache = helper.getBoolean(.75);
			boolean withReverse = transform.supportsReverse() && helper.getBoolean(.95);
			boolean fireIfUnchanged = needsUpdateReeval || helper.getBoolean();
			boolean reEvalOnUpdate = needsUpdateReeval || helper.getBoolean();
			XformOptions.XformDef options = new XformOptions.XformDef(new XformOptions.SimpleXformOptions()//
				.cache(cache).fireIfUnchanged(fireIfUnchanged).reEvalOnUpdate(reEvalOnUpdate));
			return new CombinedValueLink<>(path, sourceVL, values, valueCombination, transform, valueSupplier, withReverse, options);
		}
	};

	private final List<SettableValue<V>> theValues;
	private final Function<List<V>, V> theValueCombination;
	private final BiTypeTransformation<S, V, T> theOperation;
	private final Function<TestHelper, V> theValueSupplier;
	private final boolean isReversible;
	private final XformOptions.XformDef theOptions;

	/**
	 * @param path The path for this link
	 * @param sourceLink The source value for this link
	 * @param values The values to combine
	 * @param valueCombination The value combination function
	 * @param operation The transform operation
	 * @param valueSupplier Supplies new values for the combined values
	 * @param reversible Whether to allow setting the combined value
	 * @param options The combination options
	 */
	public CombinedValueLink(String path, ObservableValueLink<?, S> sourceLink, List<SettableValue<V>> values,
		Function<List<V>, V> valueCombination, BiTypeTransformation<S, V, T> operation, Function<TestHelper, V> valueSupplier,
		boolean reversible, XformDef options) {
		super(path, sourceLink, operation.getTargetType(), options.isCached());
		theValues = values;
		theValueCombination = valueCombination;
		theOperation = operation;
		theValueSupplier = valueSupplier;
		isReversible = reversible;
		theOptions = options;
	}

	@Override
	public ObservableValueLink<?, S> getSourceLink() {
		return (ObservableValueLink<?, S>) super.getSourceLink();
	}

	@Override
	protected Transactable getLocking() {
		ArrayList<Transactable> locking = new ArrayList<>(theValues.size() + 1);
		locking.addAll(theValues);
		locking.add(super.getLocking());
		return Transactable.combine(locking);
	}

	@Override
	public boolean isTransactional() {
		return getSourceLink().isTransactional();
	}

	@Override
	protected ObservableValue<T> createValue(TestHelper helper) {
		ObservableValue<S> sourceValue = getSourceLink().getValue();
		ObservableValue<T> combinedValue;
		TypeToken<T> type = (TypeToken<T>) getType().getType();
		Consumer<XformOptions> options = opts -> {
			opts.cache(theOptions.isCached()).reEvalOnUpdate(theOptions.isReEvalOnUpdate()).fireIfUnchanged(theOptions.isFireIfUnchanged())//
			.manyToOne(theOperation.isManyToOne()).oneToMany(theOperation.isOneToMany());
		};
		switch (theValues.size()) {
		case 1:
			if (isReversible && sourceValue instanceof SettableValue)
				combinedValue = ((SettableValue<S>) sourceValue).combine(type, //
					(s, v) -> s == null ? null : theOperation.map(s, v), theValues.get(0), theOperation::reverse,
						options);
			else
				combinedValue = sourceValue.combine(type, (s, v) -> s == null ? null : theOperation.map(s, v), theValues.get(0), options);
			break;
		case 2:
			TriFunction<S, V, V, T> map = (s, v1, v2) -> {
				if (s == null)
					return null;
				V combinedV = theValueCombination.apply(Arrays.asList(v1, v2));
				return theOperation.map(s, combinedV);
			};
			if (isReversible && sourceValue instanceof SettableValue)
				combinedValue = ((SettableValue<S>) sourceValue).combine(type, map, theValues.get(0), theValues.get(1), (c, v1, v2) -> {
					V combinedV = theValueCombination.apply(Arrays.asList(v1, v2));
					return theOperation.reverse(c, combinedV);
				}, options);
			else
				combinedValue = sourceValue.combine(type, map, theValues.get(0), theValues.get(1), options);
			break;
		default:
			throw new IllegalStateException();
		}
		return combinedValue;
	}

	/** @return The sum of all of this link's combination values */
	protected V getValueSum() {
		return theValueCombination.apply(CombinedCollectionLink.toList(theValues));
	}

	@Override
	public double getModificationAffinity() {
		return super.getModificationAffinity() + 2;
	}

	@Override
	public void tryModify(RandomAction action, TestHelper helper) {
		super.tryModify(action, helper);
		action.or(2, () -> {
			int targetValue = helper.getInt(0, theValues.size());
			V newValue = theValueSupplier.apply(helper);

			V oldValue = theValues.get(targetValue).get();
			V oldCombinedValue = getValueSum();
			theValues.get(targetValue).set(newValue, null);
			V newCombinedValue = getValueSum();
			if (helper.isReproducing())
				System.out.println(
					"Value[" + targetValue + "] " + oldValue + "->" + newValue + "; total " + oldCombinedValue + "->" + newCombinedValue);
		});
	}

	@Override
	public void expectSet(T value, OperationRejection rejection) {
		if (!isReversible) {
			rejection.reject(StdMsg.UNSUPPORTED_OPERATION);
			return;
		}
		V valueSum = getValueSum();
		S reversed = theOperation.reverse(value, valueSum);
		T remapped = theOperation.map(reversed, valueSum);
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
		V combinedValues = getValueSum();
		T combined = sourceValue == null ? null : theOperation.map(sourceValue, combinedValues);
		getTester().check(combined);
	}

	@Override
	public String toString() {
		String str = "combined:" + theOperation + CombinedCollectionLink.toList(theValues);
		if (!isReversible)
			str += ", irreversible";
		return str + ")";
	}
}
