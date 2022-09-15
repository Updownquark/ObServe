package org.observe.expresso;

import org.observe.SettableValue;
import org.observe.expresso.ModelType.ModelInstanceType;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.ObservableModelSet.ValueContainer;
import org.qommons.Named;
import org.qommons.config.QonfigElement;

public class SuppliedModelValue<M, MV extends M> implements Named, ValueContainer<M, MV> {
	public interface Satisfier {
		<M, MV extends M> MV satisfy(SuppliedModelValue<M, MV> value);
	}

	public static final String SATISFIER_PLACEHOLDER_NAME = SuppliedModelValue.class.getSimpleName() + "$SATISFIER_PLACEHOLDER";
	public static final ValueContainer<SettableValue<?>, SettableValue<Satisfier>> SATISFIER_PLACEHOLDER = new ValueContainer<SettableValue<?>, SettableValue<Satisfier>>() {
		private final ModelInstanceType<SettableValue<?>, SettableValue<Satisfier>> theType = ModelTypes.Value
			.forType((Class<Satisfier>) (Class<?>) Satisfier.class);

		@Override
		public ModelInstanceType<SettableValue<?>, SettableValue<Satisfier>> getType() {
			return theType;
		}

		@Override
		public SettableValue<Satisfier> get(ModelSetInstance models) {
			throw new IllegalStateException("Model value satisfier not installed");
		}

		@Override
		public String toString() {
			return SATISFIER_PLACEHOLDER_NAME;
		}
	};

	private final SuppliedModelOwner theOwner;
	private final QonfigElement theValueElement;
	private final String theName;
	private final ModelInstanceType<M, MV> theType;

	public SuppliedModelValue(SuppliedModelOwner style, QonfigElement valueElement, String name, ModelInstanceType<M, MV> type) {
		theOwner = style;
		theValueElement = valueElement;
		theName = name;
		theType = type;
	}

	public SuppliedModelOwner getOwner() {
		return theOwner;
	}

	public QonfigElement getValueElement() {
		return theValueElement;
	}

	@Override
	public String getName() {
		return theName;
	}

	@Override
	public ModelInstanceType<M, MV> getType() {
		return theType;
	}

	@Override
	public MV get(ModelSetInstance models) {
		return _get(models);
	}

	private <V> MV _get(ModelSetInstance models) {
		Satisfier satisfier = models.get(SATISFIER_PLACEHOLDER_NAME, ModelTypes.Value.forType(Satisfier.class)).get();
		// It's the job of the implementation to ensure that the types of the supplier and the interpreted value match
		return satisfier.satisfy(this);
	}

	@Override
	public String toString() {
		return theOwner.getElement() + "." + theName + "(" + theType + ")";
	}
}
