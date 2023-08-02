package org.observe.expresso.qonfig;

import java.util.Collections;
import java.util.Set;

import org.observe.expresso.ExpressoEvaluationException;
import org.observe.expresso.InterpretedExpressoEnv;
import org.observe.expresso.JavaExpressoParser;
import org.observe.expresso.ops.BinaryOperatorSet;
import org.observe.util.TypeTokens;
import org.qommons.Version;
import org.qommons.collect.MutableCollectionElement.StdMsg;
import org.qommons.config.QonfigInterpretationException;
import org.qommons.config.QonfigInterpreterCore;
import org.qommons.config.QonfigInterpreterCore.CoreSession;
import org.qommons.config.QonfigToolkit;
import org.qommons.config.SpecialSession;
import org.qommons.config.SpecialSessionImplementation;

import com.google.common.reflect.TypeToken;

/** Supports Qonfig evaluation using the {@link ExpressoQIS} session type */
public class ExpressoSessionImplV0_1 implements SpecialSessionImplementation<ExpressoQIS> {
	/** The name of the core toolkit */
	public static final String TOOLKIT_NAME = "Expresso-Core";
	/** The Expresso version supported by this class */
	public static final Version VERSION = new Version(0, 1, 0);
	/**
	 * An extra convenience operator that is an OR operation (||) for objects. This operator returns the first argument if it is not null,
	 * otherwise it returns the second.
	 */
	public BinaryOperatorSet.FirstArgDecisiveBinaryOp<Object, Object, Object> OBJECT_OR = new BinaryOperatorSet.FirstArgDecisiveBinaryOp<Object, Object, Object>() {
		@Override
		public Class<Object> getTargetSuperType() {
			return Object.class;
		}


		@Override
		public TypeToken<Object> getTargetType(TypeToken<? extends Object> leftOpType, TypeToken<? extends Object> rightOpType, int offset,
			int length) throws ExpressoEvaluationException {
			return TypeTokens.get().getCommonType(leftOpType, rightOpType);
		}

		@Override
		public Object apply(Object source, Object other) {
			if (source != null)
				return source;
			else
				return other;
		}

		@Override
		public String canReverse(Object currentSource, Object other, Object value) {
			return StdMsg.UNSUPPORTED_OPERATION;
		}

		@Override
		public Object reverse(Object currentSource, Object other, Object value) {
			throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
		}

		@Override
		public Object getFirstArgDecisiveValue(Object source) {
			return source;
		}

		@Override
		public String getDescription() {
			return "Object OR operation--first non-null value";
		}

		@Override
		public String toString() {
			return "||";
		}
	};

	private QonfigToolkit theToolkit;
	private ElementModelValue.Cache theDyamicValueCache;

	@Override
	public String getToolkitName() {
		return TOOLKIT_NAME;
	}

	@Override
	public Version getVersion() {
		return VERSION;
	}

	@Override
	public Class<ExpressoQIS> getProvidedAPI() {
		return ExpressoQIS.class;
	}

	@Override
	public Set<Class<? extends SpecialSession<?>>> getExpectedAPIs() {
		return Collections.emptySet(); // No dependencies
	}

	/**
	 * @param dmvCache The dynamic value cache for this session implementation to use, instead of creating a fresh one
	 * @return This session implementation
	 */
	public ExpressoSessionImplV0_1 withDynamicValueCache(ElementModelValue.Cache dmvCache) {
		theDyamicValueCache = dmvCache;
		return this;
	}

	@Override
	public void init(QonfigToolkit toolkit) {
		theToolkit = toolkit;
	}

	@Override
	public ExpressoQIS viewOfRoot(CoreSession coreSession, ExpressoQIS source) throws QonfigInterpretationException {
		if (coreSession.get(ExpressoQIS.DYNAMIC_VALUE_CACHE) == null) {
			if (theDyamicValueCache == null)
				theDyamicValueCache = new ElementModelValue.Cache();
			coreSession.put(ExpressoQIS.DYNAMIC_VALUE_CACHE, theDyamicValueCache);
		}
		ExpressoQIS qis = new ExpressoQIS(coreSession);
		qis.setExpressoParser(new JavaExpressoParser());
		InterpretedExpressoEnv.INTERPRETED_STANDARD_JAVA.reporting().ignoreClass(QonfigInterpreterCore.class.getName());
		InterpretedExpressoEnv.INTERPRETED_STANDARD_JAVA.reporting().ignoreClass(QonfigInterpreterCore.CoreSession.class.getName());
		qis.setExpressoEnv(InterpretedExpressoEnv.INTERPRETED_STANDARD_JAVA//
			.at(coreSession.getElement().getFilePosition())//
			.withOperators(null, BinaryOperatorSet.STANDARD_JAVA.copy()//
				.with("||", Object.class, Object.class, OBJECT_OR)//
				.build()));
		return qis;
	}

	@Override
	public ExpressoQIS viewOfChild(ExpressoQIS parent, CoreSession coreSession) throws QonfigInterpretationException {
		return new ExpressoQIS(coreSession);
	}

	@Override
	public ExpressoQIS parallelView(ExpressoQIS parallel, CoreSession coreSession) {
		ExpressoQIS qis = new ExpressoQIS(coreSession);
		qis.setExpressoParser(parallel.getExpressoParser());
		qis.setExpressoEnv(parallel.getExpressoEnv());
		return qis;
	}

	@Override
	public void postInitRoot(ExpressoQIS session, ExpressoQIS source) throws QonfigInterpretationException {
	}

	@Override
	public void postInitChild(ExpressoQIS session, ExpressoQIS parent) throws QonfigInterpretationException {
	}

	@Override
	public void postInitParallel(ExpressoQIS session, ExpressoQIS parallel) throws QonfigInterpretationException {
	}
}
