package org.observe.quick.style;

import java.util.Set;

import org.observe.expresso.qonfig.ExAddOn;
import org.observe.expresso.qonfig.ExElement;
import org.observe.expresso.qonfig.ExpressoQIS;
import org.observe.expresso.qonfig.ExtModelValueElement;
import org.qommons.QommonsUtils;
import org.qommons.Version;
import org.qommons.config.QonfigInterpretation;
import org.qommons.config.QonfigInterpretationException;
import org.qommons.config.QonfigInterpreterCore;
import org.qommons.config.QonfigInterpreterCore.Builder;
import org.qommons.config.QonfigInterpreterCore.CoreSession;
import org.qommons.config.QonfigToolkit;
import org.qommons.config.SpecialSession;

/** Interpretation for the Quick-Style toolkit */
public class QuickStyleInterpretation implements QonfigInterpretation {
	/** The name of the Quick-Style toolkit */
	public static final String NAME = "Quick-Style";
	/** The supported version of the Quick-Style toolkit */
	public static final Version VERSION = new Version(0, 1, 0);

	/** {@link #NAME} and {@link #VERSION} combined */
	public static final String STYLE = "Quick-Style v0.1";

	@Override
	public String getToolkitName() {
		return NAME;
	}

	@Override
	public Version getVersion() {
		return VERSION;
	}

	@Override
	public void init(QonfigToolkit toolkit) {}

	@Override
	public Set<Class<? extends SpecialSession<?>>> getExpectedAPIs() {
		return QommonsUtils.unmodifiableDistinctCopy(ExpressoQIS.class);
	}

	@Override
	public Builder configureInterpreter(Builder interpreter) {
		interpreter.modifyWith("styled", Object.class, new QonfigInterpreterCore.QonfigValueModifier<Object>() {
			@Override
			public Object prepareSession(CoreSession session) throws QonfigInterpretationException {
				ExpressoQIS exS = session.as(ExpressoQIS.class);
				exS.setExpressoEnv(exS.getExpressoEnv().copy().withNonStructuredParser(double.class, FontStyleParser.INSTANCE));
				return null;
			}

			@Override
			public Object modifyValue(Object value, CoreSession session, Object prepared) throws QonfigInterpretationException {
				return value;
			}
		});
		interpreter.createWith("with-style-sheet", ExWithStyleSheet.Def.class, ExAddOn.creator(ExWithStyleSheet.Def::new));
		interpreter.modifyWith("with-style-sheet", Object.class, new QonfigInterpreterCore.QonfigValueModifier<Object>() {
			@Override
			public Object prepareSession(CoreSession session) throws QonfigInterpretationException {
				ExpressoQIS exS = session.as(ExpressoQIS.class);
				exS.setExpressoEnv(exS.getExpressoEnv().copy().withNonStructuredParser(double.class, FontStyleParser.INSTANCE));
				return null;
			}

			@Override
			public Object modifyValue(Object value, CoreSession session, Object prepared) throws QonfigInterpretationException {
				return value;
			}
		});
		interpreter.createWith("style", QuickStyleElement.Def.class, ExElement.creator(QuickStyleElement.Def::new));
		interpreter.createWith("style-sheet", QuickStyleSheet.class, ExElement.creator(QuickStyleSheet::new));
		interpreter.createWith("style-set", QuickStyleSet.class, session -> {
			if (!(session.getElementRepresentation() instanceof QuickStyleSheet))
				throw new QonfigInterpretationException("This interpretation is only valid as a child of a style-sheet",
					session.reporting().getPosition(), 0);
			return new QuickStyleSet((QuickStyleSheet) session.getElementRepresentation(), session.getFocusType());
		});
		interpreter.createWith("import-style-sheet", QuickStyleSheet.StyleSheetRef.class,
			ExElement.creator(QuickStyleSheet.class, QuickStyleSheet.StyleSheetRef::new));
		interpreter.createWith("style-model-value", ExStyleModelValue.Def.class,
			ExAddOn.creator(ExtModelValueElement.Def.class, ExStyleModelValue.Def::new));
		return interpreter;
	}
}
