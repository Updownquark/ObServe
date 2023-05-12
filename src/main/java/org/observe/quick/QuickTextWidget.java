package org.observe.quick;

import java.text.ParseException;
import java.time.Duration;
import java.time.Instant;

import org.observe.SettableValue;
import org.observe.expresso.CompiledExpression;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableModelSet.InterpretedValueSynth;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.ObservableModelSet.ModelValueSynth;
import org.observe.util.TypeTokens;
import org.qommons.config.AbstractQIS;
import org.qommons.config.QonfigElement;
import org.qommons.config.QonfigInterpretationException;
import org.qommons.io.Format;
import org.qommons.io.SpinnerFormat;

import com.google.common.reflect.TypeToken;

public interface QuickTextWidget<T> extends QuickValueWidget<T> {
	public static final Format<Double> DEFAULT_DOUBLE_FORMAT = Format.doubleFormat(5)//
		.printIntFor(5, false)//
		.withExpCondition(5, 2)//
		.build();
	public static final Format<Float> DEFAULT_FLOAT_FORMAT = Format.doubleFormat(5)//
		.printIntFor(5, false)//
		.withExpCondition(5, 2)//
		.buildFloat();
	public static final Format<Instant> DEFAULT_INSTANT_FORMAT = SpinnerFormat.flexDate(Instant::now, "EEE MMM dd, yyyy", null);
	public static final Format<Duration> DEFAULT_DURATION_FORMAT = SpinnerFormat.flexDuration(false);
	public static final Format<Object> TO_STRING_FORMAT = new Format<Object>() {
		@Override
		public void append(StringBuilder text, Object value) {
			if (value != null)
				text.append(value);
		}

		@Override
		public Object parse(CharSequence text) throws ParseException {
			if (text.length() == 0)
				return null;
			return new ParseException("Default format cannot parse anything", 0);
		}
	};

	public interface Def<W extends QuickTextWidget<?>> extends QuickValueWidget.Def<W> {
		CompiledExpression getFormat();

		boolean isTypeEditable();

		CompiledExpression isEditable();

		@Override
		Interpreted<?, ? extends W> interpret(QuickElement.Interpreted<?> parent);

		public abstract class Abstract<T, W extends QuickTextWidget<T>> extends QuickValueWidget.Def.Abstract<T, W> implements Def<W> {
			private CompiledExpression theFormat;
			private CompiledExpression isEditable;

			public Abstract(QuickElement.Def<?> parent, QonfigElement element) {
				super(parent, element);
			}

			@Override
			public CompiledExpression getFormat() {
				return theFormat;
			}

			@Override
			public CompiledExpression isEditable() {
				return isEditable;
			}

			@Override
			public Def.Abstract<T, W> update(AbstractQIS<?> session) throws QonfigInterpretationException {
				super.update(session);
				theFormat = getExpressoSession().getAttributeExpression("format");
				isEditable = getExpressoSession().getAttributeExpression("editable");
				return this;
			}
		}
	}

	public interface Interpreted<T, W extends QuickTextWidget<T>> extends QuickValueWidget.Interpreted<T, W> {
		@Override
		Def<? super W> getDefinition();

		InterpretedValueSynth<SettableValue<?>, SettableValue<Format<T>>> getFormat();

		InterpretedValueSynth<SettableValue<?>, SettableValue<Boolean>> isEditable();

		public abstract class Abstract<T, W extends QuickTextWidget<T>> extends QuickValueWidget.Interpreted.Abstract<T, W>
		implements Interpreted<T, W> {
			private InterpretedValueSynth<SettableValue<?>, SettableValue<Format<T>>> theFormat;
			private InterpretedValueSynth<SettableValue<?>, SettableValue<Boolean>> isEditable;

			public Abstract(Def<? super W> definition, QuickElement.Interpreted<?> parent) {
				super(definition, parent);
			}

			@Override
			public Def<? super W> getDefinition() {
				return (Def<? super W>) super.getDefinition();
			}

			@Override
			public InterpretedValueSynth<SettableValue<?>, SettableValue<Format<T>>> getFormat() {
				return theFormat;
			}

			@Override
			public InterpretedValueSynth<SettableValue<?>, SettableValue<Boolean>> isEditable() {
				return isEditable;
			}

			@Override
			public Interpreted.Abstract<T, W> update(QuickStyledElement.QuickInterpretationCache cache) throws ExpressoInterpretationException {
				super.update(cache);
				TypeToken<T> valueType = getValueType();
				TypeToken<Format<T>> formatType = TypeTokens.get().keyFor(Format.class).<Format<T>> parameterized(valueType);
				if (getDefinition().getFormat() != null) {
					theFormat = getDefinition().getFormat().evaluate(ModelTypes.Value.forType(formatType)).interpret();
				} else {
					Class<T> raw = TypeTokens.get().unwrap(TypeTokens.getRawType(valueType));
					Format<T> defaultFormat;
					if (raw == String.class)
						defaultFormat = (Format<T>) Format.TEXT;
					else if (raw == int.class)
						defaultFormat = (Format<T>) SpinnerFormat.INT;
					else if (raw == long.class)
						defaultFormat = (Format<T>) SpinnerFormat.LONG;
					else if (raw == double.class)
						defaultFormat = (Format<T>) DEFAULT_DOUBLE_FORMAT;
					else if (raw == float.class)
						defaultFormat = (Format<T>) DEFAULT_FLOAT_FORMAT;
					else if (raw == Instant.class)
						defaultFormat = (Format<T>) DEFAULT_INSTANT_FORMAT;
					else if (raw == Duration.class)
						defaultFormat = (Format<T>) DEFAULT_DURATION_FORMAT;
					else if (getDefinition().isTypeEditable())
						throw new ExpressoInterpretationException("No format specified and no default available for type " + valueType,
							getDefinition().getExpressoSession().getElement().getPositionInFile(), 0);
					else
						defaultFormat = (Format<T>) TO_STRING_FORMAT;
					theFormat = ModelValueSynth.literal(formatType, defaultFormat, "default-" + raw.getSimpleName() + "-format");
				}
				isEditable = getDefinition().isEditable() == null ? null
					: getDefinition().isEditable().evaluate(ModelTypes.Value.BOOLEAN).interpret();
				return this;
			}
		}
	}

	@Override
	Interpreted<T, ?> getInterpreted();

	SettableValue<Format<T>> getFormat();

	SettableValue<Boolean> isEditable();

	default String getCurrentText() {
		T value = getValue().get();
		Format<T> format = getFormat().get();
		if (format != null)
			return format.format(value);
		else if (value == null)
			return "";
		else
			return value.toString();
	}

	public abstract class Abstract<T> extends QuickValueWidget.Abstract<T> implements QuickTextWidget<T> {
		private final SettableValue<SettableValue<Format<T>>> theFormat;
		private final SettableValue<SettableValue<Boolean>> isEditable;

		public Abstract(QuickTextWidget.Interpreted<T, ?> interpreted, QuickElement parent) {
			super(interpreted, parent);
			theFormat = SettableValue.build(TypeTokens.get().keyFor(SettableValue.class).<SettableValue<Format<T>>> parameterized(//
				interpreted.getFormat().getType().getType(0))).build();
			isEditable = SettableValue
				.build(TypeTokens.get().keyFor(SettableValue.class).<SettableValue<Boolean>> parameterized(boolean.class)).build();
		}

		@Override
		public SettableValue<Format<T>> getFormat() {
			return SettableValue.flatten(theFormat);
		}

		@Override
		public SettableValue<Boolean> isEditable() {
			return SettableValue.flatten(isEditable);
		}

		@Override
		public QuickTextWidget.Interpreted<T, ?> getInterpreted() {
			return (QuickTextWidget.Interpreted<T, ?>) super.getInterpreted();
		}

		@Override
		public QuickTextWidget.Abstract<T> update(ModelSetInstance models) throws ModelInstantiationException {
			super.update(models);
			theFormat.set(getInterpreted().getFormat().get(getModels()), null);
			isEditable.set(getInterpreted().isEditable() == null ? null : getInterpreted().isEditable().get(getModels()), null);
			return this;
		}
	}
}
