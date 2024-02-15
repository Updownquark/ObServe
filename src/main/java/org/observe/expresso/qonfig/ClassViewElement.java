package org.observe.expresso.qonfig;

import java.util.ArrayList;
import java.util.List;

import org.observe.expresso.ClassView;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretationException;

/** An &lt;imports> element in an expresso &lt;head> section */
@ExElementTraceable(toolkit = ExpressoSessionImplV0_1.CORE, qonfigType = ClassViewElement.IMPORTS)
public class ClassViewElement extends ExElement.Def.Abstract<ExElement> {
	/** The XML name of this element */
	public static final String IMPORTS = "imports";
	/** The XML name of the &lt;import> element */
	public static final String IMPORT = "import";

	private final List<ImportElement> theImports;

	/**
	 * @param parent The parent of this element
	 * @param qonfigType The Qonfig type of this element
	 */
	public ClassViewElement(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
		super(parent, qonfigType);
		theImports = new ArrayList<>();
	}

	/**
	 * @param classView The class view builder to configure
	 * @return The configured builder
	 */
	public ClassView.Builder configureClassView(ClassView.Builder classView) {
		for (ImportElement element : theImports)
			element.addImport(classView);
		return classView;
	}

	/** @return The &lt;import> elements in this &lt;imports> element */
	@QonfigChildGetter("import")
	public List<ImportElement> getImports() {
		return theImports;
	}

	@Override
	protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
		super.doUpdate(session);
		syncChildren(ImportElement.class, theImports, session.forChildren(IMPORT));
	}

	/** An &lt;import> element under an &lt;imports> element in an expresso &lt;head> section */
	@ExElementTraceable(toolkit = ExpressoSessionImplV0_1.CORE, qonfigType = IMPORT)
	public static class ImportElement extends ExElement.Def.Abstract<ExElement> {
		private String theImport;
		private boolean isWildcard;

		/**
		 * @param parent The parent element of this element
		 * @param qonfigType The Qonfig type of this element
		 */
		public ImportElement(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType);
		}

		/** @return The content of the import (excluding the wildcard marker) */
		public String getImport() {
			return theImport;
		}

		/** @return Whether this import is a wildcard */
		public boolean isWildcard() {
			return isWildcard;
		}

		/** @return The content of the import (including the wildcard marker) */
		@QonfigAttributeGetter
		public String getImportText() {
			if (isWildcard)
				return theImport + ".*";
			else
				return theImport;
		}

		/**
		 * Configures a class view with this import
		 *
		 * @param classView The class view to configure
		 */
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
