package org.observe.dbug.qonfig;

import java.util.Collections;
import java.util.Set;

import org.observe.expresso.qonfig.ExAddOn;
import org.observe.expresso.qonfig.ExElement;
import org.observe.expresso.qonfig.ExpressoQIS;
import org.qommons.Version;
import org.qommons.config.QonfigInterpretation;
import org.qommons.config.QonfigInterpreterCore.Builder;
import org.qommons.config.QonfigToolkit;
import org.qommons.config.SpecialSession;

/** Implementation of the Expresso-Debug toolkit */
public class ExpressoDbugV0_1 implements QonfigInterpretation {
	/** The name of the toolkit */
	public static final String NAME = "Dbug";
	/** The version of the toolkit */
	public static final Version VERSION = new Version(0, 1, 0);

	/** {@link #NAME} and {@link #VERSION} combined */
	public static final String DBUG = "Dbug v0.1";

	@Override
	public String getToolkitName() {
		return NAME;
	}

	@Override
	public Version getVersion() {
		return VERSION;
	}

	@Override
	public Set<Class<? extends SpecialSession<?>>> getExpectedAPIs() {
		return Collections.singleton(ExpressoQIS.class);
	}

	@Override
	public void init(QonfigToolkit toolkit) {
	}

	@Override
	public Builder configureInterpreter(Builder interpreter) {
		interpreter.createWith(DbugAnchorWatch.DBUG_ANCHOR, DbugAnchorWatch.Def.class, ExElement.creator(DbugAnchorWatch.Def::new));
		interpreter.createWith(DbugEventWatch.DBUG_EVENT, DbugEventWatch.Def.class, ExElement.creator(DbugEventWatch.Def::new));
		interpreter.createWith(DbugTag.DBUG_TAG, DbugTag.Def.class, ExElement.creator(DbugTag.Def::new));
		interpreter.createWith(DbugModelValue.DBUG_VALUE, DbugModelValue.Def.class, ExAddOn.creator(DbugModelValue.Def::new));
		interpreter.createWith(SubAnchorWatch.SUB_ANCHOR_WATCH, SubAnchorWatch.Def.class,
			ExAddOn.creator(DbugAnchorWatch.Def.class, SubAnchorWatch.Def::new));
		interpreter.createWith(DbugBreak.BREAK, DbugBreak.Def.class, ExElement.creator(DbugBreak.Def::new));
		interpreter.createWith(DbugDo.DO, DbugDo.Def.class, ExElement.creator(DbugDo.Def::new));

		return interpreter;
	}
}
