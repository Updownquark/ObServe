package org.observe.quick.base;

import java.io.File;

import org.observe.SettableValue;
import org.observe.expresso.ModelType.ModelInstanceType;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.qonfig.ExElement;
import org.observe.expresso.qonfig.ExElementTraceable;
import org.observe.expresso.qonfig.ExpressoQIS;
import org.observe.expresso.qonfig.QonfigAttributeGetter;
import org.observe.quick.QuickValueWidget;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretationException;

public class QuickFileButton extends QuickValueWidget.Abstract<File> {
	public static final String FILE_BUTTON = "file-button";

	@ExElementTraceable(toolkit = QuickBaseInterpretation.BASE,
		qonfigType = FILE_BUTTON,
		interpretation = Interpreted.class,
		instance = QuickFileButton.class)
	public static class Def extends QuickValueWidget.Def.Abstract<QuickFileButton> {
		private boolean isOpen;

		public Def(ExElement.Def<?> parent, QonfigElementOrAddOn type) {
			super(parent, type);
		}

		@QonfigAttributeGetter("open")
		public boolean isOpen() {
			return isOpen;
		}

		@Override
		protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
			super.doUpdate(session);
			isOpen = session.getAttribute("open", boolean.class);
		}

		@Override
		public Interpreted interpret(ExElement.Interpreted<?> parent) {
			return new Interpreted(this, parent);
		}
	}

	public static class Interpreted extends QuickValueWidget.Interpreted.Abstract<File, QuickFileButton> {
		public Interpreted(Def definition, ExElement.Interpreted<?> parent) {
			super(definition, parent);
		}

		@Override
		public Def getDefinition() {
			return (Def) super.getDefinition();
		}

		@Override
		protected ModelInstanceType<SettableValue<?>, SettableValue<File>> getTargetType() {
			return ModelTypes.Value.forType(File.class);
		}

		@Override
		public QuickFileButton create() {
			return new QuickFileButton(getIdentity());
		}
	}

	private boolean isOpen;

	public QuickFileButton(Object id) {
		super(id);
	}

	public boolean isOpen() {
		return isOpen;
	}

	@Override
	protected void doUpdate(ExElement.Interpreted<?> interpreted) {
		super.doUpdate(interpreted);
		Interpreted myInterpreted = (Interpreted) interpreted;
		isOpen = myInterpreted.getDefinition().isOpen();
	}
}
