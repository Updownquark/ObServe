package org.observe.quick.base;

import org.observe.ObservableValue;
import org.observe.SettableValue;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ObservableModelSet.ModelComponentId;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.qonfig.ElementTypeTraceability;
import org.observe.expresso.qonfig.ElementTypeTraceability.SingleTypeTraceability;
import org.observe.expresso.qonfig.ExElement;
import org.observe.expresso.qonfig.ExFlexibleElementModelAddOn;
import org.observe.expresso.qonfig.ExWithElementModel;
import org.observe.expresso.qonfig.ExpressoQIS;
import org.observe.expresso.qonfig.QonfigAttributeGetter;
import org.observe.quick.QuickTextWidget;
import org.observe.util.TypeTokens;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretationException;

public interface QuickEditableTextWidget<T> extends QuickTextWidget<T> {
	public static final String EDITABLE_TEXT_WIDGET = "editable-text-widget";
	public static final SingleTypeTraceability<QuickEditableTextWidget<?>, Interpreted<?, ?>, Def<?>> EDITABLE_TEXT_WIDGET_TRACEABILITY = ElementTypeTraceability
		.getElementTraceability(QuickBaseInterpretation.NAME, QuickBaseInterpretation.VERSION, EDITABLE_TEXT_WIDGET, Def.class,
			Interpreted.class, QuickEditableTextWidget.class);

	public interface Def<W extends QuickEditableTextWidget<?>> extends QuickTextWidget.Def<W> {
		@QonfigAttributeGetter("commit-on-type")
		boolean isCommitOnType();

		ModelComponentId getErrorVariable();

		ModelComponentId getWarningVariable();

		@Override
		Interpreted<?, ? extends W> interpret(ExElement.Interpreted<?> parent);

		public abstract class Abstract<W extends QuickEditableTextWidget<?>> extends QuickTextWidget.Def.Abstract<W>
		implements Def<W> {
			private boolean isCommitOnType;
			private ModelComponentId theErrorVariable;
			private ModelComponentId theWarningVariable;

			protected Abstract(ExElement.Def<?> parent, QonfigElementOrAddOn type) {
				super(parent, type);
			}

			@Override
			public boolean isCommitOnType() {
				return isCommitOnType;
			}

			@Override
			public ModelComponentId getErrorVariable() {
				return theErrorVariable;
			}

			@Override
			public ModelComponentId getWarningVariable() {
				return theWarningVariable;
			}

			@Override
			protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
				withTraceability(EDITABLE_TEXT_WIDGET_TRACEABILITY.validate(session.getFocusType(), session.reporting()));
				super.doUpdate(session.asElement(session.getFocusType().getSuperElement()));
				isCommitOnType = session.getAttribute("commit-on-type", boolean.class);

				ExWithElementModel.Def elValues = getAddOn(ExWithElementModel.Def.class);
				theErrorVariable = elValues.getElementValueModelId("error");
				theWarningVariable = elValues.getElementValueModelId("warning");
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
		private ModelComponentId theErrorVariable;
		private ModelComponentId theWarningVariable;
		private SettableValue<SettableValue<String>> theErrorStatus;
		private SettableValue<SettableValue<String>> theWarningStatus;

		protected Abstract(Object id) {
			super(id);
			theErrorStatus = SettableValue
				.build(TypeTokens.get().keyFor(SettableValue.class).<SettableValue<String>> parameterized(String.class)).build();
			theWarningStatus = SettableValue.build(theErrorStatus.getType()).build();
		}

		public boolean isCommitOnType() {
			return isCommitOnType;
		}

		@Override
		public QuickEditableTextWidget<T> setContext(EditableTextWidgetContext ctx) throws ModelInstantiationException {
			theErrorStatus.set(ctx.getError(), null);
			theWarningStatus.set(ctx.getWarning(), null);
			return this;
		}

		@Override
		protected void doUpdate(ExElement.Interpreted<?> interpreted) {
			super.doUpdate(interpreted);
			QuickEditableTextWidget.Interpreted<T, ?> myInterpreted = (QuickEditableTextWidget.Interpreted<T, ?>) interpreted;
			theErrorVariable = myInterpreted.getDefinition().getErrorVariable();
			theWarningVariable = myInterpreted.getDefinition().getWarningVariable();
			isCommitOnType = myInterpreted.getDefinition().isCommitOnType();
		}

		@Override
		protected void doInstantiate(ModelSetInstance myModels) throws ModelInstantiationException {
			super.doInstantiate(myModels);
			ExFlexibleElementModelAddOn.satisfyElementValue(theErrorVariable, myModels, SettableValue.flatten(theErrorStatus));
			ExFlexibleElementModelAddOn.satisfyElementValue(theWarningVariable, myModels, SettableValue.flatten(theWarningStatus));
		}

		@Override
		public QuickEditableTextWidget.Abstract<T> copy(ExElement parent) {
			QuickEditableTextWidget.Abstract<T> copy = (QuickEditableTextWidget.Abstract<T>) super.copy(parent);

			copy.theErrorStatus = SettableValue.build(theErrorStatus.getType()).build();
			copy.theWarningStatus = SettableValue.build(theErrorStatus.getType()).build();

			return copy;
		}
	}
}
