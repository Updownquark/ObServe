package org.observe.quick.base;

import org.observe.ObservableValue;
import org.observe.SettableValue;
import org.observe.expresso.ExpressoQIS;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.quick.QuickElement;
import org.observe.quick.QuickTextWidget;
import org.observe.util.TypeTokens;
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
		private final SettableValue<SettableValue<String>> theErrorStatus;
		private final SettableValue<SettableValue<String>> theWarningStatus;

		protected Abstract(QuickEditableTextWidget.Interpreted<T, ?> interpreted, QuickElement parent) {
			super(interpreted, parent);
			theErrorStatus = SettableValue
				.build(TypeTokens.get().keyFor(SettableValue.class).<SettableValue<String>> parameterized(String.class)).build();
			theWarningStatus = SettableValue.build(theErrorStatus.getType()).build();
		}

		@Override
		public ModelSetInstance update(QuickElement.Interpreted<?> interpreted, ModelSetInstance models)
			throws ModelInstantiationException {
			ModelSetInstance myModels = super.update(interpreted, models);
			QuickElement.satisfyContextValue("error", ModelTypes.Value.STRING, SettableValue.flatten(theErrorStatus), myModels, this);
			QuickElement.satisfyContextValue("warning", ModelTypes.Value.STRING, SettableValue.flatten(theWarningStatus), myModels, this);
			return myModels;
		}

		@Override
		public QuickEditableTextWidget<T> setContext(EditableTextWidgetContext ctx) throws ModelInstantiationException {
			theErrorStatus.set(ctx.getError(), null);
			theWarningStatus.set(ctx.getWarning(), null);
			return this;
		}
	}
}
