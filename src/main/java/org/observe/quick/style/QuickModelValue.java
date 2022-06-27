package org.observe.quick.style;

import org.observe.ObservableValue;
import org.observe.SettableValue;
import org.observe.expresso.ModelType.ModelInstanceType;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.ObservableModelSet.ValueContainer;
import org.qommons.LambdaUtils;
import org.qommons.Named;

import com.google.common.reflect.TypeToken;

public class QuickModelValue<T> implements Named, ValueContainer<SettableValue<?>, SettableValue<T>> {
	public interface Satisfier {
		<T> ObservableValue<T> satisfy(QuickModelValue<T> value);
	}

	public static final String SATISFIER_PLACEHOLDER_NAME = QuickModelValue.class.getSimpleName() + "$SATISFIER_PLACEHOLDER";
	public static final ValueContainer<SettableValue<?>, SettableValue<Satisfier>> SATISFIER_PLACEHOLDER = new ValueContainer<SettableValue<?>, SettableValue<Satisfier>>() {
		private final ModelInstanceType<SettableValue<?>, SettableValue<Satisfier>> theType = ModelTypes.Value.forType(Satisfier.class);

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

	private final QuickStyleType theStyle;
	private final String theName;
	private final TypeToken<T> theValueType;
	private final ModelInstanceType<SettableValue<?>, SettableValue<T>> theModelType;
	private final int thePriority;

	public QuickModelValue(QuickStyleType style, String name, TypeToken<T> type, int priority) {
		theStyle = style;
		theName = name;
		theValueType = type;
		theModelType = ModelTypes.Value.forType(type);
		thePriority = priority;
	}

	public QuickStyleType getStyle() {
		return theStyle;
	}

	@Override
	public String getName() {
		return theName;
	}

	public TypeToken<T> getValueType() {
		return theValueType;
	}

	@Override
	public ModelInstanceType<SettableValue<?>, SettableValue<T>> getType() {
		return theModelType;
	}

	@Override
	public SettableValue<T> get(ModelSetInstance models) {
		Satisfier satisfier = models.get(SATISFIER_PLACEHOLDER_NAME, ModelTypes.Value.forType(Satisfier.class)).get();
		ObservableValue<T> value = satisfier.satisfy(this);
		if (value instanceof SettableValue)
			return (SettableValue<T>) value;
		else
			return SettableValue.asSettable(value, LambdaUtils.constantFn("Not reversible", "Not reversible", "Not reversible"));
	}

	public int getPriority() {
		return thePriority;
	}

	@Override
	public String toString() {
		return theStyle.getElement() + "." + theName + "(" + theValueType + ")";
	}
}
