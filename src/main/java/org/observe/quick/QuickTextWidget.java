package org.observe.quick;

import java.text.ParseException;
import java.time.Duration;
import java.time.Instant;

import org.observe.SettableValue;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.InterpretedExpressoEnv;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableModelSet.InterpretedValueSynth;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.qonfig.CompiledExpression;
import org.observe.expresso.qonfig.ElementTypeTraceability;
import org.observe.expresso.qonfig.ElementTypeTraceability.SingleTypeTraceability;
import org.observe.expresso.qonfig.ExElement;
import org.observe.expresso.qonfig.ExpressoQIS;
import org.observe.expresso.qonfig.QonfigAttributeGetter;
import org.observe.util.TypeTokens;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretationException;
import org.qommons.io.Format;
import org.qommons.io.LocatedFilePosition;
import org.qommons.io.SpinnerFormat;

import com.google.common.reflect.TypeToken;

public interface QuickTextWidget<T> extends QuickValueWidget<T> {
	public static final String TEXT_WIDGET = "text-widget";
	public static final SingleTypeTraceability<QuickTextWidget<?>, Interpreted<?, ?>, Def<?>> TEXT_WIDGET_TRACEABILITY = ElementTypeTraceability
		.getElementTraceability(QuickCoreInterpretation.NAME, QuickCoreInterpretation.VERSION, TEXT_WIDGET, Def.class, Interpreted.class,
			QuickTextWidget.class);

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
		@QonfigAttributeGetter("format")
		CompiledExpression getFormat();

		boolean isTypeEditable();

		@QonfigAttributeGetter("editable")
		CompiledExpression isEditable();

		@Override
		Interpreted<?, ? extends W> interpret(ExElement.Interpreted<?> parent);

		public abstract class Abstract<W extends QuickTextWidget<?>> extends QuickValueWidget.Def.Abstract<W>
		implements Def<W> {
			private CompiledExpression theFormat;
			private CompiledExpression isEditable;

			protected Abstract(ExElement.Def<?> parent, QonfigElementOrAddOn type) {
				super(parent, type);
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
			protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
				withTraceability(TEXT_WIDGET_TRACEABILITY.validate(session.getFocusType(), session.reporting()));
				super.doUpdate(session.asElement(session.getFocusType().getSuperElement()));
				theFormat = session.getAttributeExpression("format");
				isEditable = session.getAttributeExpression("editable");
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

			protected Abstract(Def<? super W> definition, ExElement.Interpreted<?> parent) {
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
			protected void doUpdate(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
				super.doUpdate(env);
				TypeToken<T> valueType = getValueType();
				TypeToken<Format<T>> formatType = TypeTokens.get().keyFor(Format.class).<Format<T>> parameterized(valueType);
				if (getDefinition().getFormat() != null)
					theFormat = getDefinition().getFormat().interpret(ModelTypes.Value.forType(formatType), getExpressoEnv());
				isEditable = getDefinition().isEditable() == null ? null
					: getDefinition().isEditable().interpret(ModelTypes.Value.BOOLEAN, getExpressoEnv());
			}

			@Override
			protected void checkValidModel() throws ExpressoInterpretationException {
				super.checkValidModel();
				if (theFormat == null)
					theFormat = getDefaultFormat(getValueType(), getDefinition().isTypeEditable(), reporting().getPosition());
			}
		}
	}

	public static <T> InterpretedValueSynth<SettableValue<?>, SettableValue<Format<T>>> getDefaultFormat(TypeToken valueType,
		boolean editRequired, LocatedFilePosition position) throws ExpressoInterpretationException {
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
		else if (editRequired)
			throw new ExpressoInterpretationException("No format specified and no default available for type " + valueType, position, 0);
		else
			defaultFormat = (Format<T>) TO_STRING_FORMAT;
		TypeToken<Format<T>> formatType = TypeTokens.get().keyFor(Format.class).<Format<T>> parameterized(valueType);
		return InterpretedValueSynth.literal(formatType, defaultFormat, "default-" + raw.getSimpleName() + "-format");
	}

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

		protected Abstract(QuickTextWidget.Interpreted<T, ?> interpreted, ExElement parent) {
			super(interpreted, parent);
			TypeToken<Format<T>> formatType;
			try {
				formatType = TypeTokens.get().keyFor(Format.class).<Format<T>> parameterized(interpreted.getValueType());
			} catch (ExpressoInterpretationException e) {
				throw new IllegalStateException("Value type not evaluated?", e);
			}
			theFormat = SettableValue.build(TypeTokens.get().keyFor(SettableValue.class).<SettableValue<Format<T>>> parameterized(//
				formatType)).build();
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
		protected void updateModel(ExElement.Interpreted<?> interpreted, ModelSetInstance myModels) throws ModelInstantiationException {
			super.updateModel(interpreted, myModels);
			QuickTextWidget.Interpreted<T, ?> myInterpreted = (QuickTextWidget.Interpreted<T, ?>) interpreted;
			theFormat.set(myInterpreted.getFormat() == null ? null : myInterpreted.getFormat().instantiate().get(myModels), null);
			isEditable.set(myInterpreted.isEditable() == null ? null : myInterpreted.isEditable().instantiate().get(myModels), null);
		}
	}
}
