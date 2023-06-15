package org.observe.quick.base;

import org.observe.ObservableValue;
import org.observe.SettableValue;
import org.observe.expresso.ExpressoQIS;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.qonfig.ExElement;
import org.observe.quick.QuickTextWidget;
import org.observe.util.TypeTokens;
import org.qommons.config.QonfigElement;
import org.qommons.config.QonfigInterpretationException;

public interface QuickEditableTextWidget<T> extends QuickTextWidget<T> {
	public static final String EDITABLE_TEXT_WIDGET = "editable-text-widget";

	public interface Def<W extends QuickEditableTextWidget<?>> extends QuickTextWidget.Def<W> {
		boolean isCommitOnType();

		@Override
		Interpreted<?, ? extends W> interpret(ExElement.Interpreted<?> parent);

		public abstract class Abstract<T, W extends QuickEditableTextWidget<T>> extends QuickTextWidget.Def.Abstract<T, W> implements Def<W> {
			private boolean isCommitOnType;

			protected Abstract(ExElement.Def<?> parent, QonfigElement element) {
				super(parent, element);
			}

			@Override
			public boolean isCommitOnType() {
				return isCommitOnType;
			}

			@Override
			public void update(ExpressoQIS session) throws QonfigInterpretationException {
				checkElement(session.getFocusType(), QuickBaseInterpretation.NAME, QuickBaseInterpretation.VERSION, EDITABLE_TEXT_WIDGET);
				super.update(session.asElement(session.getFocusType().getSuperElement()));
				isCommitOnType = session.getAttribute("commit-on-type", boolean.class);
			}
		}
	}

	public interface Interpreted<T, W extends QuickEditableTextWidget<T>> extends QuickTextWidget.Interpreted<T, W> {
		@Override
		Def<? super W> getDefinition();

		public abstract class Abstract<T, W extends QuickEditableTextWidget<T>> extends QuickTextWidget.Interpreted.Abstract<T, W>
		implements Interpreted<T, W> {
			protected Abstract(QuickEditableTextWidget.Def<? super W> definition, ExElement.Interpreted<?> parent) {
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
		private boolean isCommitOnType;
		private final SettableValue<SettableValue<String>> theErrorStatus;
		private final SettableValue<SettableValue<String>> theWarningStatus;

		protected Abstract(QuickEditableTextWidget.Interpreted<T, ?> interpreted, ExElement parent) {
			super(interpreted, parent);
			theErrorStatus = SettableValue
				.build(TypeTokens.get().keyFor(SettableValue.class).<SettableValue<String>> parameterized(String.class)).build();
			theWarningStatus = SettableValue.build(theErrorStatus.getType()).build();
		}

		public boolean isCommitOnType() {
			return isCommitOnType;
		}

		@Override
		protected void updateModel(ExElement.Interpreted<?> interpreted, ModelSetInstance myModels) throws ModelInstantiationException {
			super.updateModel(interpreted, myModels);
			QuickEditableTextWidget.Interpreted<T, ?> myInterpreted = (QuickEditableTextWidget.Interpreted<T, ?>) interpreted;
			isCommitOnType = myInterpreted.getDefinition().isCommitOnType();
			ExElement.satisfyContextValue("error", ModelTypes.Value.STRING, SettableValue.flatten(theErrorStatus), myModels, this);
			ExElement.satisfyContextValue("warning", ModelTypes.Value.STRING, SettableValue.flatten(theWarningStatus), myModels, this);
		}

		@Override
		public QuickEditableTextWidget<T> setContext(EditableTextWidgetContext ctx) throws ModelInstantiationException {
			theErrorStatus.set(ctx.getError(), null);
			theWarningStatus.set(ctx.getWarning(), null);
			return this;
		}
	}
}
