package org.observe.quick.style;

import static org.observe.expresso.qonfig.ExpressoBaseV0_1.addOnCreator;
import static org.observe.expresso.qonfig.ExpressoBaseV0_1.creator;

import java.util.Set;

import org.observe.expresso.qonfig.ExAddOn;
import org.observe.expresso.qonfig.ExpressoQIS;
import org.observe.expresso.qonfig.ExtModelValueElement;
import org.qommons.QommonsUtils;
import org.qommons.Version;
import org.qommons.config.QonfigAddOn;
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

	@Override
	public String getToolkitName() {
		return NAME;
	}

	@Override
	public Version getVersion() {
		return VERSION;
	}

	@Override
	public void init(QonfigToolkit toolkit) {
	}

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
				exS.setExpressoEnv(exS.getExpressoEnv().copy().withNonStructuredParser(double.class, new FontStyleParser()));
				return null;
			}

			@Override
			public Object modifyValue(Object value, CoreSession session, Object prepared) throws QonfigInterpretationException {
				return value;
			}
		});
		interpreter.createWith("with-style-sheet", ExAddOn.Def.class, session -> new ExWithStyleSheet((QonfigAddOn) session.getFocusType(),
			session.as(ExpressoQIS.class).getElementRepresentation()));
		interpreter.createWith("style", QuickStyleElement.Def.class, creator(QuickStyleElement.Def::new));
		interpreter.createWith("style-sheet", QuickStyleSheet.class, creator(QuickStyleSheet::new));
		interpreter.createWith("style-set", QuickStyleSet.class, session -> {
			if (!(session.getElementRepresentation() instanceof QuickStyleSheet))
				throw new QonfigInterpretationException("This interpretation is only valid as a child of a style-sheet",
					session.reporting().getFileLocation().getPosition(0), 0);
			return new QuickStyleSet((QuickStyleSheet) session.getElementRepresentation(), session.getFocusType());
		});
		interpreter.createWith("import-style-sheet", QuickStyleSheet.StyleSheetRef.class,
			creator(QuickStyleSheet.class, QuickStyleSheet.StyleSheetRef::new));
		interpreter.createWith("style-model-value", ExStyleModelValue.Def.class,
			addOnCreator(ExtModelValueElement.Def.class, ExStyleModelValue.Def::new));
		return interpreter;
	}
}
