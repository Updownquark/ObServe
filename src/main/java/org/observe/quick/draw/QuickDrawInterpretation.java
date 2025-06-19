package org.observe.quick.draw;

import java.util.Set;

import org.observe.expresso.qonfig.ExAddOn;
import org.observe.expresso.qonfig.ExElement;
import org.observe.expresso.qonfig.ExpressoQIS;
import org.qommons.QommonsUtils;
import org.qommons.Version;
import org.qommons.config.QonfigInterpretation;
import org.qommons.config.QonfigInterpreterCore;
import org.qommons.config.QonfigInterpreterCore.Builder;
import org.qommons.config.QonfigToolkit;
import org.qommons.config.SpecialSession;

/** Qonfig interpretation for the Quick-Draw v0.1 toolkit */
public class QuickDrawInterpretation implements QonfigInterpretation {
	/** The name of the Quick-Draw toolkit */
	public static final String NAME = "Quick-Draw";
	/** The supported version of the Quick-Core toolkit */
	public static final Version VERSION = new Version(0, 1, 0);

	/** {@link #NAME} and {@link #VERSION} combined */
	public static final String DRAW = "Quick-Draw v0.1";

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
	public Builder configureInterpreter(QonfigInterpreterCore.Builder interpreter) {
		interpreter.createWith(QuickCanvas.CANVAS, QuickCanvas.Def.class, ExElement.creator(QuickCanvas.Def::new));
		interpreter.createWith(QuickRectangle.RECTANGLE, QuickRectangle.Def.class, ExElement.creator(QuickRectangle.Def::new));
		interpreter.createWith(QuickEllipse.ELLIPSE, QuickEllipse.Def.class, ExElement.creator(QuickEllipse.Def::new));
		interpreter.createWith(QuickShapeContainer.SHAPE_CONTAINER, QuickShapeContainer.Def.class,
			ExAddOn.creator(QuickShapeContainer.Def::new));
		interpreter.createWith(QuickShapeCollection.SHAPE_COLLECTION, QuickShapeCollection.Def.class,
			ExElement.creator(QuickShapeCollection.Def::new));
		return interpreter;
	}
}
