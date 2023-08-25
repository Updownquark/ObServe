package org.observe.expresso.qonfig;

import java.util.ArrayList;
import java.util.List;

import org.observe.expresso.ClassView;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretationException;

@ExElementTraceable(toolkit = ExpressoSessionImplV0_1.CORE, qonfigType = "imports")
public class ClassViewElement extends ExElement.Def.Abstract<ExElement> {
	private final List<ImportElement> theImports;

	public ClassViewElement(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
		super(parent, qonfigType);
		theImports = new ArrayList<>();
	}

	public ClassView.Builder configureClassView(ClassView.Builder classView) {
		for (ImportElement element : theImports)
			element.addImport(classView);
		return classView;
	}

	@QonfigChildGetter("import")
	public List<ImportElement> getImports() {
		return theImports;
	}

	@Override
	protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
		super.doUpdate(session);
		ExElement.syncDefs(ImportElement.class, theImports, session.forChildren("import"));
	}

	@ExElementTraceable(toolkit = ExpressoSessionImplV0_1.CORE, qonfigType = "import")
	public static class ImportElement extends ExElement.Def.Abstract<ExElement> {
		private String theImport;
		private boolean isWildcard;

		public ImportElement(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType);
		}

		public String getImport() {
			return theImport;
		}

		public boolean isWildcard() {
			return isWildcard;
		}

		@QonfigAttributeGetter
		public String getImportText() {
			if (isWildcard)
				return theImport + ".*";
			else
				return theImport;
		}

		public void addImport(ClassView.Builder classView) {
			if (isWildcard)
				classView.withWildcardImport(theImport);
			else
				classView.withImport(theImport, reporting());
		}

		@Override
		protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
			super.doUpdate(session);
			String text = session.getValueText();
			isWildcard = text.endsWith(".*");
			if (isWildcard)
				theImport = text.substring(0, text.length() - 2);
			else
				theImport = text;
		}
	}
}
