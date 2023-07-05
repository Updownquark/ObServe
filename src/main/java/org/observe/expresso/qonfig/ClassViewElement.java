package org.observe.expresso.qonfig;

import java.util.ArrayList;
import java.util.List;

import org.observe.expresso.ClassView;
import org.qommons.config.QonfigElement;
import org.qommons.config.QonfigInterpretationException;

public class ClassViewElement extends ExElement.Def.Abstract<ExElement> {
	private static final ElementTypeTraceability<ExElement, ExElement.Interpreted<ExElement>, ClassViewElement> TRACEABILITY = ElementTypeTraceability
		.<ExElement, ExElement.Interpreted<ExElement>, ClassViewElement> build(ExpressoSessionImplV0_1.TOOLKIT_NAME,
			ExpressoSessionImplV0_1.VERSION, "imports")//
		.reflectMethods(ClassViewElement.class, null, null)//
		.build();

	private final List<ImportElement> theImports;

	public ClassViewElement(ExElement.Def<?> parent, QonfigElement element) {
		super(parent, element);
		theImports = new ArrayList<>();
	}

	public void configureClassView(ClassView.Builder classView) {
		for (ImportElement element : theImports)
			element.addImport(classView);
	}

	@QonfigChildGetter("import")
	public List<ImportElement> getImports() {
		return theImports;
	}

	@Override
	public void update(ExpressoQIS session) throws QonfigInterpretationException {
		withTraceability(TRACEABILITY.validate(session.getFocusType(), session.reporting()));
		super.update(session);
		ExElement.syncDefs(ImportElement.class, theImports, session.forChildren("import"));
	}

	public static class ImportElement extends ExElement.Def.Abstract<ExElement> {
		private static final ElementTypeTraceability<ExElement, ExElement.Interpreted<?>, ImportElement> TRACEABILITY = ElementTypeTraceability.<ExElement, ExElement.Interpreted<?>, ImportElement> build(
			ExpressoSessionImplV0_1.TOOLKIT_NAME, ExpressoSessionImplV0_1.VERSION, "import")//
			.reflectMethods(ImportElement.class, null, null)//
			.build();

		private String theImport;
		private boolean isWildcard;

		public ImportElement(ExElement.Def<?> parent, QonfigElement element) {
			super(parent, element);
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
		public void update(ExpressoQIS session) throws QonfigInterpretationException {
			withTraceability(TRACEABILITY.validate(session.getFocusType(), session.reporting()));
			super.update(session);
			String text = session.getValueText();
			isWildcard = text.endsWith(".*");
			if (isWildcard)
				theImport = text.substring(0, text.length() - 2);
			else
				theImport = text;
		}
	}
}
