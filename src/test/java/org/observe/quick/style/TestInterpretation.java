package org.observe.quick.style;

import static org.observe.expresso.qonfig.ExpressoBaseV0_1.creator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import org.observe.ObservableValue;
import org.observe.SettableValue;
import org.observe.expresso.CompiledExpressoEnv;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.InterpretedExpressoEnv;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ModelType;
import org.observe.expresso.ModelType.ModelInstanceType;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableModelSet.CompiledModelValue;
import org.observe.expresso.ObservableModelSet.InterpretedValueSynth;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.qonfig.CompiledExpression;
import org.observe.expresso.qonfig.ExElement;
import org.observe.expresso.qonfig.ExpressoQIS;
import org.observe.expresso.qonfig.ModelValueElement;
import org.qommons.QommonsUtils;
import org.qommons.Version;
import org.qommons.collect.BetterList;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretation;
import org.qommons.config.QonfigInterpretationException;
import org.qommons.config.QonfigInterpreterCore.Builder;
import org.qommons.config.QonfigToolkit;
import org.qommons.config.SpecialSession;

/** Interpretation for {@link QuickStyleTests} */
public class TestInterpretation implements QonfigInterpretation {
	/** The name of the test toolkit */
	public static final String TOOLKIT_NAME = "Quick-Style-Test";
	/** The version of the test toolkit */
	public static final Version VERSION = new Version(0, 1, 0);

	@Override
	public Set<Class<? extends SpecialSession<?>>> getExpectedAPIs() {
		return QommonsUtils.unmodifiableDistinctCopy(ExpressoQIS.class);
	}

	@Override
	public String getToolkitName() {
		return TOOLKIT_NAME;
	}

	@Override
	public Version getVersion() {
		return VERSION;
	}

	@Override
	public void init(QonfigToolkit toolkit) {
	}

	@Override
	public Builder configureInterpreter(Builder interpreter) {
		interpreter.createWith("a", ModelValueElement.CompiledSynth.class, creator(A.Def::new));
		interpreter.createWith("b", ModelValueElement.CompiledSynth.class, creator(B.Def::new));
		interpreter.createWith("c", ModelValueElement.CompiledSynth.class, creator(C.Def::new));
		interpreter.createWith("d", ModelValueElement.CompiledSynth.class, creator(D.Def::new));
		return interpreter;
	}

	static abstract class StyledTestElement<T> extends ModelValueElement.Default<SettableValue<?>, SettableValue<T>>
	implements QuickStyledElement {
		static abstract class Def<T> extends QuickStyledElement.Def.Abstract<StyledTestElement<T>>
		implements ModelValueElement.CompiledSynth<SettableValue<?>, StyledTestElement<T>> {
			private String theModelPath;

			protected Def(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
				super(parent, qonfigType);
			}

			@Override
			public String getModelPath() {
				return theModelPath;
			}

			@Override
			public ModelType<SettableValue<?>> getModelType(CompiledExpressoEnv env) {
				return ModelTypes.Value;
			}

			@Override
			public CompiledExpression getElementValue() {
				return null;
			}

			@Override
			public void prepareModelValue(ExpressoQIS session) throws QonfigInterpretationException {
			}
		}

		static abstract class Interpreted<T> extends QuickStyledElement.Interpreted.Abstract<StyledTestElement<T>>
		implements ModelValueElement.InterpretedSynth<SettableValue<?>, SettableValue<T>, StyledTestElement<T>> {
			private final Class<T> theType;

			protected Interpreted(Def<T> definition, Class<T> type) {
				super(definition, null);
				theType = type;
			}

			@Override
			public Def<T> getDefinition() {
				return (Def<T>) super.getDefinition();
			}

			protected Class<T> getTargetType() {
				return theType;
			}

			@Override
			public Interpreted<T> setParentElement(ExElement.Interpreted<?> parent) {
				super.setParentElement(parent);
				return this;
			}

			@Override
			public ModelInstanceType<SettableValue<?>, SettableValue<T>> getType() {
				return ModelTypes.Value.forType(theType);
			}

			@Override
			public InterpretedValueSynth<?, ?> getElementValue() {
				return null;
			}

			@Override
			public void updateValue(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
				update(env);
			}

			@Override
			public StyledTestElement<T> create(ExElement parent, ModelSetInstance models) throws ModelInstantiationException {
				return null;
			}
		}

		public StyledTestElement(Interpreted<T> interpreted, ExElement parent) {
			super(interpreted, parent);
			throw new IllegalStateException();
		}
	}

	/** Entity structure A for testing styles */
	public static class A {
		/** {@link CompiledModelValue} for producing instances of {@link D} */
		public static class Def extends StyledTestElement.Def<A> {
			CompiledExpression a;
			CompiledExpression b;
			CompiledExpression c;
			CompiledExpression d;

			/**
			 * @param parent The parent element
			 * @param qonfigType The Qonfig element type of this definition
			 */
			public Def(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
				super(parent, qonfigType);
			}

			@Override
			protected Style.Def wrap(QuickStyledElement.QuickInstanceStyle.Def parentStyle, QuickCompiledStyle style) {
				return new Style.Def(parentStyle, style);
			}

			@Override
			public Style.Def getStyle() {
				return (Style.Def) super.getStyle();
			}

			/** @return The "a" attribute value of this structure */
			public CompiledExpression getA() {
				return a;
			}

			/** @return The "b" attribute value of this structure */
			public CompiledExpression getB() {
				return b;
			}

			/** @return The "c" attribute value of this structure */
			public CompiledExpression getC() {
				return c;
			}

			/** @return The "d" attribute value of this structure */
			public CompiledExpression getD() {
				return d;
			}

			@Override
			protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
				super.doUpdate(session.asElement("styled"));

				a = session.getAttributeExpression("a");
				b = session.getAttributeExpression("b");
				c = session.getAttributeExpression("c");
				d = session.getAttributeExpression("d");
			}

			@Override
			public Interpreted interpret() {
				return new Interpreted(this);
			}
		}

		static class Interpreted extends StyledTestElement.Interpreted<A> {
			private InterpretedValueSynth<SettableValue<?>, SettableValue<Boolean>> a;
			private InterpretedValueSynth<SettableValue<?>, SettableValue<Boolean>> b;
			private InterpretedValueSynth<SettableValue<?>, SettableValue<Integer>> c;
			private InterpretedValueSynth<SettableValue<?>, SettableValue<Boolean>> d;

			Interpreted(Def definition) {
				super(definition, A.class);
			}

			@Override
			public Interpreted setParentElement(ExElement.Interpreted<?> parent) {
				super.setParentElement(parent);
				return this;
			}

			@Override
			public Def getDefinition() {
				return (Def) super.getDefinition();
			}

			@Override
			public Style.Interpreted getStyle() {
				return (Style.Interpreted) super.getStyle();
			}

			@Override
			protected void doUpdate(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
				super.doUpdate(env);

				a = getDefinition().getA().interpret(ModelTypes.Value.BOOLEAN, getExpressoEnv());
				b = getDefinition().getB().interpret(ModelTypes.Value.BOOLEAN, getExpressoEnv());
				c = getDefinition().getC().interpret(ModelTypes.Value.INT, getExpressoEnv());
				d = getDefinition().getD().interpret(ModelTypes.Value.BOOLEAN, getExpressoEnv());
			}

			@Override
			public List<? extends InterpretedValueSynth<?, ?>> getComponents() {
				return BetterList.of(a, b, c, d);
			}

			@Override
			public SettableValue<A> get(ModelSetInstance models) throws ModelInstantiationException, IllegalStateException {
				models = getExpressoEnv().wrapLocal(models);
				SettableValue<Boolean> aInst = a.get(models);
				SettableValue<Boolean> bInst = b.get(models);
				SettableValue<Integer> cInst = c.get(models);
				SettableValue<Boolean> dInst = d.get(models);

				ObservableValue<Boolean> s0 = getStyle().getS0().evaluate(models);
				ObservableValue<Integer> s1 = getStyle().getS1().evaluate(models);
				ObservableValue<Boolean> s2 = getStyle().getS2().evaluate(models);

				return SettableValue.of(A.class, new A(aInst, bInst, cInst, dInst, s0, s1, s2), "Unsettable");
			}

			@Override
			public SettableValue<A> forModelCopy(SettableValue<A> value, ModelSetInstance sourceModels, ModelSetInstance newModels)
				throws ModelInstantiationException {
				SettableValue<Boolean> aInst = a.forModelCopy(value.get().a, sourceModels, newModels);
				SettableValue<Boolean> bInst = b.forModelCopy(value.get().b, sourceModels, newModels);
				SettableValue<Integer> cInst = c.forModelCopy(value.get().c, sourceModels, newModels);
				SettableValue<Boolean> dInst = d.forModelCopy(value.get().d, sourceModels, newModels);

				if (aInst == value.get().a && bInst == value.get().b && cInst == value.get().c && dInst == value.get().d)
					return value;

				ObservableValue<Boolean> s0 = getStyle().getS0().evaluate(newModels);
				ObservableValue<Integer> s1 = getStyle().getS1().evaluate(newModels);
				ObservableValue<Boolean> s2 = getStyle().getS2().evaluate(newModels);

				return SettableValue.of(A.class, new A(aInst, bInst, cInst, dInst, s0, s1, s2), "Unsettable");
			}
		}

		static class Style extends QuickStyledElement.QuickInstanceStyle.Abstract {
			static class Def extends QuickCompiledStyle.Wrapper implements QuickStyledElement.QuickInstanceStyle.Def {
				private final Object theId;
				private final QuickStyleAttributeDef s0;
				private final QuickStyleAttributeDef s1;
				private final QuickStyleAttributeDef s2;

				public Def(QuickCompiledStyle parent, QuickCompiledStyle wrapped) {
					super(parent, wrapped);
					theId = new Object();
					QuickTypeStyle typeStyle = QuickStyledElement.getTypeStyle(wrapped.getStyleTypes(), wrapped.getElement(), TOOLKIT_NAME,
						VERSION, "a");
					s0 = typeStyle.getAttribute("s0");
					s1 = typeStyle.getAttribute("s1");
					s2 = typeStyle.getAttribute("s2");
				}

				@Override
				public Object getId() {
					return theId;
				}

				public QuickStyleAttributeDef getS0() {
					return s0;
				}

				public QuickStyleAttributeDef getS1() {
					return s1;
				}

				public QuickStyleAttributeDef getS2() {
					return s2;
				}

				@Override
				public Interpreted interpret(ExElement.Interpreted<?> parentEl, QuickInterpretedStyle parent, InterpretedExpressoEnv env)
					throws ExpressoInterpretationException {
					return new Interpreted(this, parent, getWrapped().interpret(parentEl, parent, env));
				}
			}

			static class Interpreted extends QuickInterpretedStyle.Wrapper implements QuickStyledElement.QuickInstanceStyle.Interpreted {
				private final Def theDefinition;
				private QuickElementStyleAttribute<Boolean> s0;
				private QuickElementStyleAttribute<Integer> s1;
				private QuickElementStyleAttribute<Boolean> s2;

				public Interpreted(Def definition, QuickInterpretedStyle parent, QuickInterpretedStyle wrapped) {
					super(parent, wrapped);
					theDefinition = definition;
				}

				@Override
				public Def getDefinition() {
					return theDefinition;
				}

				@Override
				public Object getId() {
					return theDefinition.getId();
				}

				public QuickElementStyleAttribute<Boolean> getS0() {
					return s0;
				}

				public QuickElementStyleAttribute<Integer> getS1() {
					return s1;
				}

				public QuickElementStyleAttribute<Boolean> getS2() {
					return s2;
				}

				@Override
				public void update(InterpretedExpressoEnv env, QuickInterpretedStyleCache.Applications appCache)
					throws ExpressoInterpretationException {
					super.update(env, appCache);
					QuickInterpretedStyleCache cache = QuickInterpretedStyleCache.get(env);
					s0 = get(cache.getAttribute(theDefinition.getS0(), boolean.class, env));
					s1 = get(cache.getAttribute(theDefinition.getS1(), int.class, env));
					s2 = get(cache.getAttribute(theDefinition.getS2(), boolean.class, env));
				}

				@Override
				public Style create(QuickStyledElement parent) {
					return new Style(getId(), parent);
				}
			}

			private ObservableValue<Boolean> s0;
			private ObservableValue<Integer> s1;
			private ObservableValue<Boolean> s2;

			public Style(Object interpretedId, QuickStyledElement styledElement) {
				super(interpretedId, styledElement);
			}

			public ObservableValue<Boolean> getS0() {
				return s0;
			}

			public ObservableValue<Integer> getS1() {
				return s1;
			}

			public ObservableValue<Boolean> getS2() {
				return s2;
			}

			@Override
			public void update(QuickStyledElement.QuickInstanceStyle.Interpreted interpreted, ModelSetInstance models)
				throws ModelInstantiationException {
				super.update(interpreted, models);
				Interpreted myInterpreted = (Interpreted) interpreted;
				s0 = myInterpreted.getS0().evaluate(models);
				s1 = myInterpreted.getS1().evaluate(models);
				s2 = myInterpreted.getS2().evaluate(models);
			}
		}

		/** Field a */
		public final SettableValue<Boolean> a;
		/** Field b */
		public final SettableValue<Boolean> b;
		/** Field c */
		public final SettableValue<Integer> c;
		/** Field d */
		public final SettableValue<Boolean> d;
		/** Style value s0 */
		public final ObservableValue<Boolean> s0;
		/** Style value s1 */
		public final ObservableValue<Integer> s1;
		/** Style value s2 */
		public final ObservableValue<Boolean> s2;

		/**
		 * @param a Field a
		 * @param b Field b
		 * @param c Field c
		 * @param d Field d
		 * @param s0 Style value s0
		 * @param s1 Style value s1
		 * @param s2 Style value s2
		 */
		public A(SettableValue<Boolean> a, SettableValue<Boolean> b, SettableValue<Integer> c, SettableValue<Boolean> d,
			ObservableValue<Boolean> s0, ObservableValue<Integer> s1, ObservableValue<Boolean> s2) {
			this.a = a;
			this.b = b;
			this.c = c;
			this.d = d;
			this.s0 = s0;
			this.s1 = s1;
			this.s2 = s2;
		}
	}

	/** Entity structure B for testing styles */
	public static class B {
		/**
		 * {@link CompiledModelValue} for producing instances of {@link B}
		 *
		 * @param <T> The {@link B} sub-type of this definition
		 */
		public static class Def<T extends B> extends StyledTestElement.Def<T> {
			CompiledExpression e;
			CompiledExpression f;
			final List<ModelValueElement.CompiledSynth<SettableValue<?>, ?>> children;

			/**
			 * @param parent The parent element
			 * @param qonfigType The Qonfig element type of this definition
			 */
			public Def(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
				super(parent, qonfigType);
				children = new ArrayList<>();
			}

			@Override
			protected Style.Def wrap(QuickStyledElement.QuickInstanceStyle.Def parentStyle, QuickCompiledStyle style) {
				return new Style.Def(parentStyle, style);
			}

			/** @return The "e" attribute value of this structure */
			public CompiledExpression getE() {
				return e;
			}

			/** @return The "f" attribute value of this structure */
			public CompiledExpression getF() {
				return f;
			}

			/** @return The a-typed children of this structure */
			public List<ModelValueElement.CompiledSynth<SettableValue<?>, ?>> getChildren() {
				return Collections.unmodifiableList(children);
			}

			@Override
			protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
				super.doUpdate(session.asElement("styled"));

				e = session.getAttributeExpression("e");
				f = session.getAttributeExpression("f");

				ExElement.syncDefs(ModelValueElement.CompiledSynth.class, children, session.forChildren("a"));
			}

			@Override
			public Interpreted<T> interpret() {
				return (Interpreted<T>) new Interpreted<>((Def<B>) this, B.class);
			}
		}

		static class Interpreted<T extends B> extends StyledTestElement.Interpreted<T> {
			private InterpretedValueSynth<SettableValue<?>, SettableValue<Boolean>> e;
			private InterpretedValueSynth<SettableValue<?>, SettableValue<Integer>> f;
			private final List<ModelValueElement.InterpretedSynth<SettableValue<?>, SettableValue<A>, ?>> theChildren;

			Interpreted(Def<T> definition, Class<T> type) {
				super(definition, type);
				theChildren = new ArrayList<>();
			}

			@Override
			public Def<T> getDefinition() {
				return (Def<T>) super.getDefinition();
			}

			@Override
			public Style.Interpreted getStyle() {
				return (Style.Interpreted) super.getStyle();
			}

			@Override
			protected void doUpdate(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
				super.doUpdate(env);

				e = getDefinition().getE().interpret(ModelTypes.Value.BOOLEAN, getExpressoEnv());
				f = getDefinition().getF().interpret(ModelTypes.Value.INT, getExpressoEnv());

				theChildren.clear();
				for (ModelValueElement.CompiledSynth<SettableValue<?>, ?> synth : getDefinition().getChildren()) {
					ModelValueElement.InterpretedSynth<SettableValue<?>, SettableValue<A>, ?> child;
					child = (ModelValueElement.InterpretedSynth<SettableValue<?>, SettableValue<A>, ?>) synth.interpret();
					child.setParentElement(this);
					child.updateValue(getExpressoEnv());
					theChildren.add(child);
				}
			}

			@Override
			public List<? extends InterpretedValueSynth<?, ?>> getComponents() {
				return BetterList.of(Stream.concat(Stream.of(e, f), theChildren.stream()));
			}

			@Override
			public SettableValue<T> get(ModelSetInstance models) throws ModelInstantiationException, IllegalStateException {
				models = getExpressoEnv().wrapLocal(models);
				SettableValue<Boolean> eInst = e.get(models);
				SettableValue<Integer> fInst = f.get(models);

				ObservableValue<Integer> s3 = getStyle().getS3().evaluate(models);
				ObservableValue<Integer> s4 = getStyle().getS4().evaluate(models);

				List<A> childrenInst = new ArrayList<>(theChildren.size());
				for (InterpretedValueSynth<SettableValue<?>, ? extends SettableValue<? extends A>> child : theChildren)
					childrenInst.add(child.get(models).get());

				return create(eInst, fInst, s3, s4, childrenInst, models);
			}

			protected SettableValue<T> create(SettableValue<Boolean> eInst, SettableValue<Integer> fInst, ObservableValue<Integer> s3,
				ObservableValue<Integer> s4, List<A> children, ModelSetInstance models) throws ModelInstantiationException {
				return (SettableValue<T>) SettableValue.of(B.class, new B(eInst, fInst, s3, s4, children), "Not settable");
			}

			@Override
			public SettableValue<T> forModelCopy(SettableValue<T> value, ModelSetInstance sourceModels, ModelSetInstance newModels)
				throws ModelInstantiationException {
				return get(newModels);
			}
		}

		static class Style extends QuickStyledElement.QuickInstanceStyle.Abstract {
			static class Def extends QuickCompiledStyle.Wrapper implements QuickStyledElement.QuickInstanceStyle.Def {
				private final Object theId;
				private final QuickStyleAttributeDef s3;
				private final QuickStyleAttributeDef s4;

				public Def(QuickCompiledStyle parent, QuickCompiledStyle wrapped) {
					super(parent, wrapped);
					theId = new Object();
					QuickTypeStyle typeStyle = QuickStyledElement.getTypeStyle(wrapped.getStyleTypes(), wrapped.getElement(), TOOLKIT_NAME,
						VERSION, "b");
					s3 = typeStyle.getAttribute("s3");
					s4 = typeStyle.getAttribute("s4");
				}

				@Override
				public Object getId() {
					return theId;
				}

				public QuickStyleAttributeDef getS3() {
					return s3;
				}

				public QuickStyleAttributeDef getS4() {
					return s4;
				}

				@Override
				public Interpreted interpret(ExElement.Interpreted<?> parentEl, QuickInterpretedStyle parent, InterpretedExpressoEnv env)
					throws ExpressoInterpretationException {
					return new Interpreted(this, parent, getWrapped().interpret(parentEl, parent, env));
				}
			}

			static class Interpreted extends QuickInterpretedStyle.Wrapper implements QuickStyledElement.QuickInstanceStyle.Interpreted {
				private final Def theDefinition;
				private QuickElementStyleAttribute<Integer> s3;
				private QuickElementStyleAttribute<Integer> s4;

				public Interpreted(Def definition, QuickInterpretedStyle parent, QuickInterpretedStyle wrapped) {
					super(parent, wrapped);
					theDefinition = definition;
				}

				@Override
				public Def getDefinition() {
					return theDefinition;
				}

				@Override
				public Object getId() {
					return theDefinition.getId();
				}

				public QuickElementStyleAttribute<Integer> getS3() {
					return s3;
				}

				public QuickElementStyleAttribute<Integer> getS4() {
					return s4;
				}

				@Override
				public void update(InterpretedExpressoEnv env, QuickInterpretedStyleCache.Applications appCache)
					throws ExpressoInterpretationException {
					super.update(env, appCache);
					QuickInterpretedStyleCache cache = QuickInterpretedStyleCache.get(env);
					s3 = get(cache.getAttribute(theDefinition.getS3(), int.class, env));
					s4 = get(cache.getAttribute(theDefinition.getS4(), int.class, env));
				}

				@Override
				public Style create(QuickStyledElement parent) {
					return new Style(getId(), parent);
				}
			}

			private ObservableValue<Integer> s3;
			private ObservableValue<Integer> s4;

			public Style(Object interpretedId, QuickStyledElement styledElement) {
				super(interpretedId, styledElement);
			}

			public ObservableValue<Integer> getS3() {
				return s3;
			}

			public ObservableValue<Integer> getS4() {
				return s4;
			}

			@Override
			public void update(QuickStyledElement.QuickInstanceStyle.Interpreted interpreted, ModelSetInstance models)
				throws ModelInstantiationException {
				super.update(interpreted, models);
				Interpreted myInterpreted = (Interpreted) interpreted;
				s3 = myInterpreted.getS3().evaluate(models);
				s4 = myInterpreted.getS4().evaluate(models);
			}
		}

		/** Field e */
		public final SettableValue<Boolean> e;
		/** Field f */
		public final SettableValue<Integer> f;
		/** Style value s3 */
		public final ObservableValue<Integer> s3;
		/** Style value s4 */
		public final ObservableValue<Integer> s4;
		/** This B entity's {@link A}-typed children */
		public final List<A> a;

		B(SettableValue<Boolean> e, SettableValue<Integer> f, ObservableValue<Integer> s3, ObservableValue<Integer> s4, List<A> a) {
			this.e = e;
			this.f = f;
			this.s3 = s3;
			this.s4 = s4;
			this.a = a;
		}
	}

	/** Entity structure C for testing styles */
	public static class C extends B {
		/** {@link CompiledModelValue} for producing instances of {@link C} */
		public static class Def extends B.Def<C> {
			CompiledExpression g;

			/**
			 * @param parent The parent element
			 * @param qonfigType The Qonfig element type of this definition
			 */
			public Def(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
				super(parent, qonfigType);
			}

			/** @return The "g" attribute value of this structure */
			public CompiledExpression getG() {
				return g;
			}

			@Override
			protected Style.Def wrap(QuickStyledElement.QuickInstanceStyle.Def parentStyle, QuickCompiledStyle style) {
				return new Style.Def(parentStyle, style);
			}

			@Override
			protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
				super.doUpdate(session);

				g = session.getAttributeExpression("g");
			}

			@Override
			public Interpreted interpret() {
				return new Interpreted(this);
			}
		}

		static class Interpreted extends B.Interpreted<C> {
			private InterpretedValueSynth<SettableValue<?>, SettableValue<Boolean>> g;

			public Interpreted(Def definition) {
				super(definition, C.class);
			}

			@Override
			public Def getDefinition() {
				return (Def) super.getDefinition();
			}

			@Override
			public Style.Interpreted getStyle() {
				return (Style.Interpreted) super.getStyle();
			}

			@Override
			protected void doUpdate(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
				super.doUpdate(env);

				g = getDefinition().getG().interpret(ModelTypes.Value.BOOLEAN, getExpressoEnv());
			}

			@Override
			protected SettableValue<C> create(SettableValue<Boolean> eInst, SettableValue<Integer> fInst, ObservableValue<Integer> s3,
				ObservableValue<Integer> s4, List<A> children, ModelSetInstance models) throws ModelInstantiationException {
				SettableValue<Boolean> gInst = g.get(models);
				ObservableValue<Boolean> s5 = getStyle().getS5().evaluate(models);
				return SettableValue.of(C.class, new C(eInst, fInst, gInst, s3, s4, s5, children), "Not settable");
			}
		}

		static class Style extends B.Style {
			static class Def extends B.Style.Def {
				private final QuickStyleAttributeDef s5;

				public Def(QuickCompiledStyle parent, QuickCompiledStyle wrapped) {
					super(parent, wrapped);
					QuickTypeStyle typeStyle = QuickStyledElement.getTypeStyle(wrapped.getStyleTypes(), wrapped.getElement(), TOOLKIT_NAME,
						VERSION, "c");
					s5 = typeStyle.getAttribute("s5");
				}

				public QuickStyleAttributeDef getS5() {
					return s5;
				}

				@Override
				public Interpreted interpret(ExElement.Interpreted<?> parentEl, QuickInterpretedStyle parent, InterpretedExpressoEnv env)
					throws ExpressoInterpretationException {
					return new Interpreted(this, parent, getWrapped().interpret(parentEl, parent, env));
				}
			}

			static class Interpreted extends B.Style.Interpreted {
				private QuickElementStyleAttribute<Boolean> s5;

				public Interpreted(Def definition, QuickInterpretedStyle parent, QuickInterpretedStyle wrapped) {
					super(definition, parent, wrapped);
				}

				@Override
				public Def getDefinition() {
					return (Def) super.getDefinition();
				}

				public QuickElementStyleAttribute<Boolean> getS5() {
					return s5;
				}

				@Override
				public void update(InterpretedExpressoEnv env, QuickInterpretedStyleCache.Applications appCache)
					throws ExpressoInterpretationException {
					super.update(env, appCache);
					QuickInterpretedStyleCache cache = QuickInterpretedStyleCache.get(env);
					s5 = get(cache.getAttribute(getDefinition().getS5(), boolean.class, env));
				}
			}

			public Style(Object interpretedId, QuickStyledElement styledElement) {
				super(interpretedId, styledElement);
			}
		}

		/** Field g */
		public final SettableValue<Boolean> g;
		/** Style value s5 */
		public final ObservableValue<Boolean> s5;

		C(SettableValue<Boolean> e, SettableValue<Integer> f, SettableValue<Boolean> g, ObservableValue<Integer> s3,
			ObservableValue<Integer> s4, ObservableValue<Boolean> s5, List<A> a) {
			super(e, f, s3, s4, a);
			this.g = g;
			this.s5 = s5;
		}
	}

	/** Entity structure D for testing styles */
	public static class D extends B {
		/** {@link CompiledModelValue} for producing instances of {@link D} */
		public static class Def extends B.Def<D> {
			CompiledExpression h;

			/**
			 * @param parent The parent element
			 * @param qonfigType The Qonfig element type of this definition
			 */
			public Def(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
				super(parent, qonfigType);
			}

			/** @return The "h" attribute value of this structure */
			public CompiledExpression getH() {
				return h;
			}

			@Override
			protected Style.Def wrap(QuickStyledElement.QuickInstanceStyle.Def parentStyle, QuickCompiledStyle style) {
				return new Style.Def(parentStyle, style);
			}

			@Override
			protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
				super.doUpdate(session);

				h = session.getAttributeExpression("h");
			}

			@Override
			public Interpreted interpret() {
				return new Interpreted(this);
			}
		}

		static class Interpreted extends B.Interpreted<D> {
			private InterpretedValueSynth<SettableValue<?>, SettableValue<Integer>> h;

			public Interpreted(Def definition) {
				super(definition, D.class);
			}

			@Override
			public Def getDefinition() {
				return (Def) super.getDefinition();
			}

			@Override
			public Style.Interpreted getStyle() {
				return (Style.Interpreted) super.getStyle();
			}

			@Override
			protected void doUpdate(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
				super.doUpdate(env);

				h = getDefinition().getH().interpret(ModelTypes.Value.INT, getExpressoEnv());
			}

			@Override
			protected SettableValue<D> create(SettableValue<Boolean> eInst, SettableValue<Integer> fInst, ObservableValue<Integer> s3,
				ObservableValue<Integer> s4, List<A> children, ModelSetInstance models) throws ModelInstantiationException {
				SettableValue<Integer> hInst = h.get(models);
				ObservableValue<Integer> s6 = getStyle().getS6().evaluate(models);

				return SettableValue.of(D.class, new D(eInst, fInst, hInst, s3, s4, s6, children), "Not settable");
			}
		}

		static class Style extends B.Style {
			static class Def extends B.Style.Def {
				private final QuickStyleAttributeDef s6;

				public Def(QuickCompiledStyle parent, QuickCompiledStyle wrapped) {
					super(parent, wrapped);
					QuickTypeStyle typeStyle = QuickStyledElement.getTypeStyle(wrapped.getStyleTypes(), wrapped.getElement(), TOOLKIT_NAME,
						VERSION, "d");
					s6 = typeStyle.getAttribute("s6");
				}

				public QuickStyleAttributeDef getS6() {
					return s6;
				}

				@Override
				public Interpreted interpret(ExElement.Interpreted<?> parentEl, QuickInterpretedStyle parent, InterpretedExpressoEnv env)
					throws ExpressoInterpretationException {
					return new Interpreted(this, parent, getWrapped().interpret(parentEl, parent, env));
				}
			}

			static class Interpreted extends B.Style.Interpreted {
				private QuickElementStyleAttribute<Integer> s6;

				public Interpreted(Def definition, QuickInterpretedStyle parent, QuickInterpretedStyle wrapped) {
					super(definition, parent, wrapped);
				}

				@Override
				public Def getDefinition() {
					return (Def) super.getDefinition();
				}

				public QuickElementStyleAttribute<Integer> getS6() {
					return s6;
				}

				@Override
				public void update(InterpretedExpressoEnv env, QuickInterpretedStyleCache.Applications appCache)
					throws ExpressoInterpretationException {
					super.update(env, appCache);
					QuickInterpretedStyleCache cache = QuickInterpretedStyleCache.get(env);
					s6 = get(cache.getAttribute(getDefinition().getS6(), int.class, env));
				}
			}

			public Style(Object interpretedId, QuickStyledElement styledElement) {
				super(interpretedId, styledElement);
			}
		}

		/** Field h */
		public final SettableValue<Integer> h;
		/** Style value s6 */
		public final ObservableValue<Integer> s6;

		D(SettableValue<Boolean> e, SettableValue<Integer> f, SettableValue<Integer> h, ObservableValue<Integer> s3,
			ObservableValue<Integer> s4, ObservableValue<Integer> s6, List<A> a) {
			super(e, f, s3, s4, a);
			this.h = h;
			this.s6 = s6;
		}
	}
}
