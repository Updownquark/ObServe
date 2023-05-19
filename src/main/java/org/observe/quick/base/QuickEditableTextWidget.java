package org.observe.quick.base;

import org.observe.ObservableValue;
import org.observe.SettableValue;
import org.observe.expresso.DynamicModelValue;
import org.observe.expresso.ExpressoQIS;
import org.observe.expresso.ModelException;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.TypeConversionException;
import org.observe.quick.QuickElement;
import org.observe.quick.QuickTextWidget;
import org.qommons.config.QonfigElement;
import org.qommons.config.QonfigInterpretationException;

public interface QuickEditableTextWidget<T> extends QuickTextWidget<T> {
	public interface Def<W extends QuickEditableTextWidget<?>> extends QuickTextWidget.Def<W> {
		boolean isCommitOnType();

		@Override
		Interpreted<?, ? extends W> interpret(QuickElement.Interpreted<?> parent);

		public abstract class Abstract<T, W extends QuickEditableTextWidget<T>> extends QuickTextWidget.Def.Abstract<T, W> implements Def<W> {
			private boolean isCommitOnType;

			protected Abstract(QuickElement.Def<?> parent, QonfigElement element) {
				super(parent, element);
			}

			@Override
			public boolean isCommitOnType() {
				return isCommitOnType;
			}

			@Override
			public void update(ExpressoQIS session) throws QonfigInterpretationException {
				super.update(session);
				isCommitOnType = session.getAttribute("commit-on-type", boolean.class);
			}
		}
	}

	public interface Interpreted<T, W extends QuickEditableTextWidget<T>> extends QuickTextWidget.Interpreted<T, W> {
		@Override
		Def<? super W> getDefinition();

		public abstract class Abstract<T, W extends QuickEditableTextWidget<T>> extends QuickTextWidget.Interpreted.Abstract<T, W>
		implements Interpreted<T, W> {
			protected Abstract(QuickEditableTextWidget.Def<? super W> definition, QuickElement.Interpreted<?> parent) {
				super(definition, parent);
			}

			@Override
			public Def<? super W> getDefinition() {
				return (Def<? super W>) super.getDefinition();
			}
		}
	}

	public interface EditableTextWidgetContext {
		SettableValue<String> getError();

		SettableValue<String> getWarning();

		public class Default implements EditableTextWidgetContext {
			private final SettableValue<String> theError;
			private final SettableValue<String> theWarning;

			public Default(SettableValue<String> error, SettableValue<String> warning) {
				theError = error;
				theWarning = warning;
			}

			public Default(ObservableValue<String> error, ObservableValue<String> warning) {
				theError = SettableValue.asSettable(error, __ -> "Not settable");
				theWarning = SettableValue.asSettable(warning, __ -> "Not settable");
			}

			@Override
			public SettableValue<String> getError() {
				return theError;
			}

			@Override
			public SettableValue<String> getWarning() {
				return theWarning;
			}
		}
	}

	QuickEditableTextWidget<T> setContext(EditableTextWidgetContext ctx) throws ModelInstantiationException;

	public static abstract class Abstract<T> extends QuickTextWidget.Abstract<T> implements QuickEditableTextWidget<T> {
		protected Abstract(QuickEditableTextWidget.Interpreted<T, ?> interpreted, QuickElement parent) {
			super(interpreted, parent);
		}

		@Override
		public QuickEditableTextWidget<T> setContext(EditableTextWidgetContext ctx) throws ModelInstantiationException {
			SettableValue<String> error = ctx.getError();
			if (error != null) {
				try {
					DynamicModelValue.satisfyDynamicValue("error", ModelTypes.Value.STRING, getModels(), error);
				} catch (ModelException e) {
					throw new ModelInstantiationException("No error value?", reporting().getFileLocation().getPosition(0), 0, e);
				} catch (TypeConversionException e) {
					throw new IllegalStateException("error is not a string?", e);
				}
			}
			SettableValue<String> warn = ctx.getWarning();
			if (warn != null) {
				try {
					DynamicModelValue.satisfyDynamicValue("warning", ModelTypes.Value.STRING, getModels(), warn);
				} catch (ModelException e) {
					throw new ModelInstantiationException("No warning value?", reporting().getFileLocation().getPosition(0), 0, e);
				} catch (TypeConversionException e) {
					throw new IllegalStateException("warning is not a string?", e);
				}
			}
			return this;
		}
	}
}
