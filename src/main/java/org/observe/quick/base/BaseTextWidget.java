package org.observe.quick.base;

import org.observe.SettableValue;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.InterpretedExpressoEnv;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ObservableExpression;
import org.observe.expresso.ObservableModelSet.InterpretedValueSynth;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.qonfig.ExElement;
import org.observe.expresso.qonfig.ExpressoQIS;
import org.observe.quick.QuickTextWidget;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretationException;

import com.google.common.reflect.TypeToken;

public interface BaseTextWidget<T> extends QuickTextWidget<T> {
	public interface Def<W extends BaseTextWidget<?>> extends QuickTextWidget.Def<W> {
		StyledDocument.Def<?> getDocument();

		@Override
		abstract Interpreted<?, ? extends W> interpret(ExElement.Interpreted<?> parent);

		public abstract class Abstract<W extends BaseTextWidget<?>> extends QuickTextWidget.Def.Abstract<W> implements Def<W> {
			private StyledDocument.Def<?> theDocument;

			protected Abstract(ExElement.Def<?> parent, QonfigElementOrAddOn type) {
				super(parent, type);
			}

			@Override
			public StyledDocument.Def<?> getDocument() {
				return theDocument;
			}

			@Override
			protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
				super.doUpdate(session.asElement(session.getFocusType().getSuperElement()));

				theDocument = ExElement.useOrReplace(StyledDocument.Def.class, theDocument, session, "document");
			}
		}
	}

	public interface Interpreted<T, W extends BaseTextWidget<T>> extends QuickTextWidget.Interpreted<T, W> {
		StyledDocument.Interpreted<T, ?> getDocument();

		public abstract class Abstract<T, W extends BaseTextWidget<T>> extends QuickTextWidget.Interpreted.Abstract<T, W>
		implements Interpreted<T, W> {
			private StyledDocument.Interpreted<T, ?> theDocument;
			private boolean isDocumentStale;

			protected Abstract(Def<? super W> definition, ExElement.Interpreted<?> parent) {
				super(definition, parent);
			}

			@Override
			public Def<? super W> getDefinition() {
				return (Def<? super W>) super.getDefinition();
			}

			@Override
			public TypeToken<? extends W> getWidgetType() throws ExpressoInterpretationException {
				return null;
			}

			@Override
			public TypeToken<T> getValueType() throws ExpressoInterpretationException {
				getOrInitValue();
				if (theDocument != null)
					return theDocument.getValueType();
				else
					return super.getValueType();
			}

			@Override
			public InterpretedValueSynth<SettableValue<?>, SettableValue<T>> getOrInitValue() throws ExpressoInterpretationException {
				super.getOrInitValue();
				if (isDocumentStale) {
					isDocumentStale = false;
					if (getDefinition().getDocument() == null) {
						if (theDocument != null)
							theDocument.destroy();
						theDocument = null;
					} else if (theDocument == null || theDocument.getIdentity() != getDefinition().getDocument().getIdentity()) {
						if (theDocument != null)
							theDocument.destroy();
						theDocument = (StyledDocument.Interpreted<T, ?>) getDefinition().getDocument().interpret(this);
					}
					if (theDocument != null)
						theDocument.updateDocument(getExpressoEnv());
				}
				return getValue();
			}

			@Override
			public StyledDocument.Interpreted<T, ?> getDocument() {
				return theDocument;
			}

			@Override
			protected void doUpdate(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
				isDocumentStale = true;
				super.doUpdate(env);
			}

			@Override
			protected void checkValidModel() throws ExpressoInterpretationException {
				if (theDocument != null) {
					if (getValue() != null && getDefinition().getValue().getExpression() != ObservableExpression.EMPTY)
						throw new ExpressoInterpretationException("Both document and value are specified, but only one is allowed",
							getDefinition().getValue().getFilePosition(0),
							getDefinition().getValue().getExpression().getExpressionLength());
					if (getFormat() != null)
						throw new ExpressoInterpretationException("Format is not needed when document is specified",
							getDefinition().getFormat().getFilePosition(0),
							getDefinition().getFormat().getExpression().getExpressionLength());
				} else
					super.checkValidModel();
			}
		}
	}

	StyledDocument<T> getDocument();

	public abstract class Abstract<T> extends QuickTextWidget.Abstract<T> implements BaseTextWidget<T> {
		private StyledDocument<T> theDocument;

		protected Abstract(BaseTextWidget.Interpreted<T, ?> interpreted, ExElement parent) {
			super(interpreted, parent);
		}

		@Override
		public StyledDocument<T> getDocument() {
			return theDocument;
		}

		@Override
		protected void updateModel(ExElement.Interpreted<?> interpreted, ModelSetInstance myModels) throws ModelInstantiationException {
			super.updateModel(interpreted, myModels);
			BaseTextWidget.Interpreted<T, ?> myInterpreted = (BaseTextWidget.Interpreted<T, ?>) interpreted;
			theDocument = myInterpreted.getDocument() == null ? null : myInterpreted.getDocument().create(this);
			if (theDocument != null)
				theDocument.update(myInterpreted.getDocument(), myModels);
		}
	}
}
