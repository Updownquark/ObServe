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
import org.observe.expresso.ObservableModelSet.ModelValueInstantiator;
import org.observe.expresso.qonfig.CompiledExpression;
import org.observe.expresso.qonfig.ExElement;
import org.observe.expresso.qonfig.ExElementTraceable;
import org.observe.expresso.qonfig.ExpressoQIS;
import org.observe.expresso.qonfig.QonfigAttributeGetter;
import org.observe.util.TypeTokens;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretationException;
import org.qommons.io.Format;
import org.qommons.io.LocatedFilePosition;
import org.qommons.io.SpinnerFormat;

import com.google.common.reflect.TypeToken;

/**
 * A widget whose primary function is to display (and potentially allow editing of) a value as text
 *
 * @param <T> The type of the value being edited
 */
public interface QuickTextWidget<T> extends QuickValueWidget<T> {
	/** The XML name of this type */
	public static final String TEXT_WIDGET = "text-widget";

	/** Default format for double-typed values */
	public static final Format<Double> DEFAULT_DOUBLE_FORMAT = Format.doubleFormat(5)//
		.printIntFor(5, false)//
		.withExpCondition(5, 2)//
		.build();
	/** Default format for float-typed values */
	public static final Format<Float> DEFAULT_FLOAT_FORMAT = Format.doubleFormat(5)//
		.printIntFor(5, false)//
		.withExpCondition(5, 2)//
		.buildFloat();
	/** Default format for {@link Instant}-typed values */
	public static final Format<Instant> DEFAULT_INSTANT_FORMAT = SpinnerFormat.flexDate(Instant::now, "EEE MMM dd, yyyy", null);
	/** Default format for {@link Duration}-typed values */
	public static final Format<Duration> DEFAULT_DURATION_FORMAT = SpinnerFormat.flexDuration(false);
	/** Default format for {@link String}-typed values */
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

	/**
	 * Definition for a text widget
	 *
	 * @param <W> The sub-type of text widget
	 */
	@ExElementTraceable(toolkit = QuickCoreInterpretation.CORE,
		qonfigType = TEXT_WIDGET,
		interpretation = Interpreted.class,
		instance = QuickTextWidget.class)
	public interface Def<W extends QuickTextWidget<?>> extends QuickValueWidget.Def<W> {
		/** @return The format for rendering the value as text */
		@QonfigAttributeGetter("format")
		CompiledExpression getFormat();

		/** @return Whether widgets built by this type may allow editing of the value by editing of the text representation */
		boolean isTypeEditable();

		/** @return The expression specifying that text can be edited */
		@QonfigAttributeGetter("editable")
		CompiledExpression isEditable();

		@Override
		Interpreted<?, ? extends W> interpret(ExElement.Interpreted<?> parent);

		/**
		 * Abstract {@link QuickValueWidget} definition implementation
		 *
		 * @param <W> The sub-type of text widget
		 */
		public abstract class Abstract<W extends QuickTextWidget<?>> extends QuickValueWidget.Def.Abstract<W>
		implements Def<W> {
			private CompiledExpression theFormat;
			private CompiledExpression isEditable;

			/**
			 * @param parent The parent element of this widget
			 * @param type The Qonfig type of this element
			 */
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
				super.doUpdate(session.asElement(session.getFocusType().getSuperElement()));
				theFormat = getAttributeExpression("format", session);
				isEditable = getAttributeExpression("editable", session);
			}
		}
	}

	/**
	 * Abstract {@link QuickValueWidget} interpretation implementation
	 *
	 * @param <T> The type of value to edit
	 * @param <W> The sub-type of text widget
	 */
	public interface Interpreted<T, W extends QuickTextWidget<T>> extends QuickValueWidget.Interpreted<T, W> {
		@Override
		Def<? super W> getDefinition();

		/** @return The format for rendering the value as text */
		InterpretedValueSynth<SettableValue<?>, SettableValue<Format<T>>> getFormat();

		/** @return The expression specifying that text can be edited */
		InterpretedValueSynth<SettableValue<?>, SettableValue<Boolean>> isEditable();

		/**
		 * Abstract {@link QuickValueWidget} interpretation implementation
		 *
		 * @param <T> The type of value to edit
		 * @param <W> The sub-type of text widget
		 */
		public abstract class Abstract<T, W extends QuickTextWidget<T>> extends QuickValueWidget.Interpreted.Abstract<T, W>
		implements Interpreted<T, W> {
			private InterpretedValueSynth<SettableValue<?>, SettableValue<Format<T>>> theFormat;
			private InterpretedValueSynth<SettableValue<?>, SettableValue<Boolean>> isEditable;

			/**
			 * @param definition The definition to interpret
			 * @param parent The parent element of this widget
			 */
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
				theFormat = interpret(getDefinition().getFormat(), ModelTypes.Value.forType(formatType));
				isEditable = interpret(getDefinition().isEditable(), ModelTypes.Value.BOOLEAN);
			}

			@Override
			protected void checkValidModel() throws ExpressoInterpretationException {
				super.checkValidModel();
				if (theFormat == null)
					theFormat = getDefaultFormat(getValueType(), getDefinition().isTypeEditable(), reporting().getPosition());
			}
		}
	}

	/**
	 * @param <T> The type of the value
	 * @param valueType The type of the value
	 * @param editRequired Whether the format must support editing
	 * @param position The position to report errors for
	 * @return An interpreted expression for a format to render and possibly edit values of the given type
	 * @throws ExpressoInterpretationException If no such default format is available
	 */
	public static <T> InterpretedValueSynth<SettableValue<?>, SettableValue<Format<T>>> getDefaultFormat(TypeToken<T> valueType,
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
		return InterpretedValueSynth.literalValue(formatType, defaultFormat, "default-" + raw.getSimpleName() + "-format");
	}

	/** @return The format for rendering the value as text */
	SettableValue<Format<T>> getFormat();

	/** @return Whether the text can be edited */
	SettableValue<Boolean> isEditable();

	/** @return The current value rendered as text */
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

	/**
	 * Abstract {@link QuickTextWidget} implementation
	 *
	 * @param <T> The type of the value to edit
	 */
	public abstract class Abstract<T> extends QuickValueWidget.Abstract<T> implements QuickTextWidget<T> {
		private ModelValueInstantiator<SettableValue<Format<T>>> theFormatInstantiator;
		private ModelValueInstantiator<SettableValue<Boolean>> theEditableInstantiator;
		private SettableValue<SettableValue<Format<T>>> theFormat;
		private SettableValue<SettableValue<Boolean>> isEditable;

		/** @param id The element identifier for this text widget */
		protected Abstract(Object id) {
			super(id);
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
		protected void doUpdate(ExElement.Interpreted<?> interpreted) {
			super.doUpdate(interpreted);
			QuickTextWidget.Interpreted<T, ?> myInterpreted = (QuickTextWidget.Interpreted<T, ?>) interpreted;
			if (theFormat == null) {
				TypeToken<Format<T>> formatType;
				try {
					formatType = TypeTokens.get().keyFor(Format.class).<Format<T>> parameterized(myInterpreted.getValueType());
				} catch (ExpressoInterpretationException e) {
					throw new IllegalStateException("Value type not evaluated?", e);
				}
				theFormat = SettableValue.build(TypeTokens.get().keyFor(SettableValue.class).<SettableValue<Format<T>>> parameterized(//
					formatType)).build();
			}

			theFormatInstantiator = myInterpreted.getFormat() == null ? null : myInterpreted.getFormat().instantiate();
			theEditableInstantiator = myInterpreted.isEditable() == null ? null : myInterpreted.isEditable().instantiate();
		}

		@Override
		public void instantiated() {
			super.instantiated();
			if (theFormatInstantiator != null)
				theFormatInstantiator.instantiate();
			if (theEditableInstantiator != null)
				theEditableInstantiator.instantiate();
		}

		@Override
		protected void doInstantiate(ModelSetInstance myModels) throws ModelInstantiationException {
			super.doInstantiate(myModels);

			theFormat.set(theFormatInstantiator == null ? null : theFormatInstantiator.get(myModels), null);
			isEditable.set(theEditableInstantiator == null ? null : theEditableInstantiator.get(myModels), null);
		}

		@Override
		public QuickTextWidget.Abstract<T> copy(ExElement parent) {
			QuickTextWidget.Abstract<T> copy = (QuickTextWidget.Abstract<T>) super.copy(parent);

			copy.theFormat = SettableValue.build(theFormat.getType()).build();
			copy.isEditable = SettableValue.build(isEditable.getType()).build();

			return copy;
		}
	}
}
