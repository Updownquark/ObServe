package org.observe.quick.style;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

import org.observe.ObservableValue;
import org.observe.SettableValue;
import org.observe.expresso.Expresso;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.ExpressoQIS;
import org.observe.expresso.ExpressoTesting.ExpressoTest;
import org.observe.expresso.ModelException;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ModelType;
import org.observe.expresso.ModelType.ModelInstanceType;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableModelSet.CompiledModelValue;
import org.observe.expresso.ObservableModelSet.ModelComponentNode;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.ObservableModelSet.ModelValueSynth;
import org.observe.expresso.TypeConversionException;
import org.observe.quick.style.QuickInterpretedStyle.QuickElementStyleAttribute;
import org.qommons.QommonsUtils;
import org.qommons.Version;
import org.qommons.collect.BetterList;
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
		return QommonsUtils.unmodifiableDistinctCopy(ExpressoQIS.class, StyleQIS.class);
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
		interpreter.createWith("a", CompiledModelValue.class, session -> new A.Def(session.as(StyleQIS.class)));
		interpreter.createWith("b", CompiledModelValue.class, session -> B.Def.create(session.as(StyleQIS.class)));
		interpreter.createWith("c", CompiledModelValue.class, session -> new C.Def(session.as(StyleQIS.class)));
		interpreter.createWith("d", CompiledModelValue.class, session -> new D.Def(session.as(StyleQIS.class)));
		interpreter.modifyWith("styled-test", ExpressoTest.class, new Expresso.ElementModelAugmentation<ExpressoTest>() {
			@Override
			public void augmentElementModel(ExpressoQIS session, org.observe.expresso.ObservableModelSet.Builder builder)
				throws QonfigInterpretationException {
				for (ExpressoQIS value : session.forChildren("styled-value"))
					builder.withMaker(value.getAttributeText("name"), value.interpret(CompiledModelValue.class));
			}
		});
		return interpreter;
	}

	/** Entity structure A for testing styles */
	public static class A {
		/** {@link CompiledModelValue} for producing instances of {@link D} */
		public static class Def implements CompiledModelValue<SettableValue<?>, SettableValue<A>> {
			private final ExpressoQIS expressoSession;
			private final ModelComponentNode<?, ?> a;
			private final ModelComponentNode<?, ?> b;
			private final ModelComponentNode<?, ?> c;
			private final ModelComponentNode<?, ?> d;
			private final QuickCompiledStyle theStyle;

			Def(StyleQIS session) throws QonfigInterpretationException {
				expressoSession = session.as(ExpressoQIS.class);
				try {
					a = expressoSession.getExpressoEnv().getModels().getComponent("a");
				} catch (ModelException x) {
					throw new QonfigInterpretationException("Could not interpret a", session.getElement().getPositionInFile(), 0, x);
				}
				try {
					b = expressoSession.getExpressoEnv().getModels().getComponent("b");
				} catch (ModelException x) {
					throw new QonfigInterpretationException("Could not interpret b", session.getElement().getPositionInFile(), 0, x);
				}
				try {
					c = expressoSession.getExpressoEnv().getModels().getComponent("c");
				} catch (ModelException x) {
					throw new QonfigInterpretationException("Could not interpret c", session.getElement().getPositionInFile(), 0, x);
				}
				try {
					d = expressoSession.getExpressoEnv().getModels().getComponent("d");
				} catch (ModelException x) {
					throw new QonfigInterpretationException("Could not interpret d", session.getElement().getPositionInFile(), 0, x);
				}

				theStyle = session.getStyle();
			}

			@Override
			public ModelType<SettableValue<?>> getModelType() {
				return ModelTypes.Value;
			}

			@Override
			public ModelValueSynth<SettableValue<?>, SettableValue<A>> createSynthesizer() throws ExpressoInterpretationException {
				return new Synth(expressoSession, a, b, c, d, theStyle.interpret(null, new HashMap<>()));
			}
		}

		static class Synth implements ModelValueSynth<SettableValue<?>, SettableValue<A>> {
			private final ExpressoQIS expressoSession;
			private final ModelValueSynth<SettableValue<?>, SettableValue<Boolean>> a;
			private final ModelValueSynth<SettableValue<?>, SettableValue<Boolean>> b;
			private final ModelValueSynth<SettableValue<?>, SettableValue<Integer>> c;
			private final ModelValueSynth<SettableValue<?>, SettableValue<Boolean>> d;
			private final QuickElementStyleAttribute<Boolean> s0;
			private final QuickElementStyleAttribute<Integer> s1;
			private final QuickElementStyleAttribute<Boolean> s2;

			Synth(ExpressoQIS session, ModelComponentNode<?, ?> a, ModelComponentNode<?, ?> b, ModelComponentNode<?, ?> c,
				ModelComponentNode<?, ?> d, QuickInterpretedStyle style) throws ExpressoInterpretationException {
				expressoSession = session;
				expressoSession.interpretLocalModel();
				try {
					this.a = a.interpret().as(ModelTypes.Value.BOOLEAN);
				} catch (TypeConversionException x) {
					throw new ExpressoInterpretationException("Could not interpret a", session.getElement().getPositionInFile(), 0, x);
				}
				try {
					this.b = b.interpret().as(ModelTypes.Value.BOOLEAN);
				} catch (TypeConversionException x) {
					throw new ExpressoInterpretationException("Could not interpret b", session.getElement().getPositionInFile(), 0, x);
				}
				try {
					this.c = c.interpret().as(ModelTypes.Value.INT);
				} catch (TypeConversionException x) {
					throw new ExpressoInterpretationException("Could not interpret c", session.getElement().getPositionInFile(), 0, x);
				}
				try {
					this.d = d.interpret().as(ModelTypes.Value.BOOLEAN);
				} catch (TypeConversionException x) {
					throw new ExpressoInterpretationException("Could not interpret d", session.getElement().getPositionInFile(), 0, x);
				}

				s0 = style.get("s0", boolean.class);
				s1 = style.get("s1", int.class);
				s2 = style.get("s2", boolean.class);
			}

			@Override
			public ModelType<SettableValue<?>> getModelType() {
				return ModelTypes.Value;
			}

			@Override
			public ModelInstanceType<SettableValue<?>, SettableValue<A>> getType() {
				return ModelTypes.Value.forType(A.class);
			}

			@Override
			public SettableValue<A> get(ModelSetInstance models) throws ModelInstantiationException {
				return SettableValue.of(A.class, new A(this, expressoSession.wrapLocal(models)), "Immutable");
			}

			@Override
			public SettableValue<A> forModelCopy(SettableValue<A> value, ModelSetInstance sourceModels, ModelSetInstance newModels) {
				return value;
			}

			@Override
			public BetterList<ModelValueSynth<?, ?>> getCores() {
				return BetterList.of(this);
			}

			@Override
			public String toString() {
				return expressoSession.getElement().toString();
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

		A(Synth synth, ModelSetInstance msi) throws ModelInstantiationException {
			this.a = synth.a.get(msi);
			this.b = synth.b.get(msi);
			this.c = synth.c.get(msi);
			this.d = synth.d.get(msi);
			this.s0 = synth.s0.evaluate(msi);
			this.s1 = synth.s1.evaluate(msi);
			this.s2 = synth.s2.evaluate(msi);
		}

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
		public static class Def<T extends B> implements CompiledModelValue<SettableValue<?>, SettableValue<T>> {
			final Class<T> clazz;
			final ExpressoQIS expressoSession;
			final ModelComponentNode<?, ?> e;
			final ModelComponentNode<?, ?> f;
			final List<CompiledModelValue<?, ?>> children;
			final QuickCompiledStyle style;

			Def(StyleQIS session, Class<T> clazz) throws QonfigInterpretationException {
				this.clazz = clazz;
				expressoSession = session.as(ExpressoQIS.class);
				try {
					e = expressoSession.getExpressoEnv().getModels().getComponent("e");
				} catch (ModelException x) {
					throw new QonfigInterpretationException("Could not interpret e", session.getElement().getPositionInFile(), 0, x);
				}
				try {
					f = expressoSession.getExpressoEnv().getModels().getComponent("f");
				} catch (ModelException x) {
					throw new QonfigInterpretationException("Could not interpret f", session.getElement().getPositionInFile(), 0, x);
				}

				children = new ArrayList<>();
				try {
					children.addAll((List<CompiledModelValue<?, ?>>) (List<?>) session.interpretChildren("a", CompiledModelValue.class));
				} catch (QonfigInterpretationException x) {
					throw new QonfigInterpretationException("Could not evaluate children", x.getPosition(), x.getErrorLength(), x);
				}
				style = session.getStyle();
			}

			@Override
			public ModelType<SettableValue<?>> getModelType() {
				return ModelTypes.Value;
			}

			@Override
			public ModelValueSynth<SettableValue<?>, SettableValue<T>> createSynthesizer() throws ExpressoInterpretationException {
				return new Synth<>(this);
			}

			/**
			 * @param session The session from which to get the data defining the {@link B} instance
			 * @throws QonfigInterpretationException If the {@link B} instance could not be parsed
			 * @return The {@link ModelValueSynth} to produce the instance
			 */
			public static Def<B> create(StyleQIS session) throws QonfigInterpretationException {
				return new Def<>(session, B.class);
			}
		}

		static class Synth<T extends B> implements ModelValueSynth<SettableValue<?>, SettableValue<T>> {
			final Class<T> clazz;
			final ExpressoQIS expressoSession;
			final QuickInterpretedStyle style;
			final ModelValueSynth<SettableValue<?>, SettableValue<Boolean>> e;
			final ModelValueSynth<SettableValue<?>, SettableValue<Integer>> f;
			final QuickElementStyleAttribute<Integer> s3;
			final QuickElementStyleAttribute<Integer> s4;
			final List<ModelValueSynth<SettableValue<?>, SettableValue<A>>> children;

			Synth(Def<T> def) throws ExpressoInterpretationException {
				this.clazz = def.clazz;
				expressoSession = def.expressoSession;
				expressoSession.interpretLocalModel();
				style = def.style.interpret(null, new HashMap<>());
				try {
					this.e = def.e.interpret().as(ModelTypes.Value.BOOLEAN);
				} catch (TypeConversionException x) {
					throw new ExpressoInterpretationException("Could not interpret e", expressoSession.getElement().getPositionInFile(), 0,
						x);
				}
				try {
					this.f = def.f.interpret().as(ModelTypes.Value.INT);
				} catch (TypeConversionException x) {
					throw new ExpressoInterpretationException("Could not interpret f", expressoSession.getElement().getPositionInFile(), 0,
						x);
				}

				s3 = style.get("s3", int.class);
				s4 = style.get("s4", int.class);
				this.children = new ArrayList<>();
				try {
					for (CompiledModelValue<?, ?> child : def.children)
						this.children.add(child.createSynthesizer().as(ModelTypes.Value.forType(A.class)));
				} catch (TypeConversionException x) {
					throw new ExpressoInterpretationException("Could not evaluate children",
						expressoSession.getElement().getPositionInFile(), 0, x);
				}
			}

			@Override
			public ModelType<SettableValue<?>> getModelType() {
				return ModelTypes.Value;
			}

			@Override
			public ModelInstanceType<SettableValue<?>, SettableValue<T>> getType() {
				return ModelTypes.Value.forType(clazz);
			}

			@Override
			public SettableValue<T> get(ModelSetInstance models) throws ModelInstantiationException {
				ModelSetInstance localModels = expressoSession.wrapLocal(models);
				List<A> childrenInstances = new ArrayList<>();
				for (ModelValueSynth<SettableValue<?>, SettableValue<A>> child : children)
					childrenInstances.add(child.get(localModels).get());
				return SettableValue.of(clazz, create(localModels, childrenInstances), "Immutable");
			}

			T create(ModelSetInstance models, List<A> aChildren) throws ModelInstantiationException {
				return (T) new B(this, models, aChildren);
			}

			@Override
			public SettableValue<T> forModelCopy(SettableValue<T> value, ModelSetInstance sourceModels, ModelSetInstance newModels) {
				return value;
			}

			@Override
			public BetterList<ModelValueSynth<?, ?>> getCores() {
				return BetterList.of(this);
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

		B(Synth<?> synth, ModelSetInstance msi, List<A> children) throws ModelInstantiationException {
			this.e = synth.e.get(msi);
			this.f = synth.f.get(msi);
			this.s3 = synth.s3.evaluate(msi);
			this.s4 = synth.s4.evaluate(msi);
			this.a = children;
		}
	}

	/** Entity structure C for testing styles */
	public static class C extends B {
		/** {@link CompiledModelValue} for producing instances of {@link C} */
		public static class Def extends B.Def<C> {
			final ModelComponentNode<?, ?> g;

			Def(StyleQIS session) throws QonfigInterpretationException {
				super(session, C.class);
				try {
					g = expressoSession.getExpressoEnv().getModels().getComponent("g");
				} catch (ModelException x) {
					throw new QonfigInterpretationException("Could not interpret g", session.getElement().getPositionInFile(), 0, x);
				}
			}

			@Override
			public ModelValueSynth<SettableValue<?>, SettableValue<C>> createSynthesizer() throws ExpressoInterpretationException {
				return new Synth(this);
			}
		}

		static class Synth extends B.Synth<C> {
			final ModelValueSynth<SettableValue<?>, SettableValue<Boolean>> g;
			final QuickElementStyleAttribute<Boolean> s5;

			Synth(C.Def def) throws ExpressoInterpretationException {
				super(def);
				try {
					this.g = def.g.interpret().as(ModelTypes.Value.BOOLEAN);
				} catch (TypeConversionException x) {
					throw new ExpressoInterpretationException("Could not interpret g", expressoSession.getElement().getPositionInFile(), 0,
						x);
				}
				this.s5 = style.get("s5", boolean.class);
			}

			@Override
			C create(ModelSetInstance models, List<A> aChildren) throws ModelInstantiationException {
				return new C(this, models, aChildren);
			}
		}

		/** Field g */
		public final SettableValue<Boolean> g;
		/** Style value s5 */
		public final ObservableValue<Boolean> s5;

		C(Synth synth, ModelSetInstance msi, List<A> children) throws ModelInstantiationException {
			super(synth, msi, children);
			g = synth.g.get(msi);
			s5 = synth.s5.evaluate(msi);
		}
	}

	/** Entity structure D for testing styles */
	public static class D extends B {
		/** {@link CompiledModelValue} for producing instances of {@link D} */
		public static class Def extends B.Def<D> {
			final ModelComponentNode<?, ?> h;

			Def(StyleQIS session) throws QonfigInterpretationException {
				super(session, D.class);
				try {
					h = expressoSession.getExpressoEnv().getModels().getComponent("h");
				} catch (ModelException x) {
					throw new QonfigInterpretationException("No such model value h", session.getElement().getPositionInFile(), 0, x);
				}
			}

			@Override
			public ModelValueSynth<SettableValue<?>, SettableValue<D>> createSynthesizer() throws ExpressoInterpretationException {
				return new Synth(this);
			}
		}

		static class Synth extends B.Synth<D> {
			final ModelValueSynth<SettableValue<?>, SettableValue<Integer>> h;
			final QuickElementStyleAttribute<Integer> s6;

			Synth(D.Def def) throws ExpressoInterpretationException {
				super(def);
				try {
					this.h = def.h.interpret().as(ModelTypes.Value.INT);
				} catch (TypeConversionException x) {
					throw new ExpressoInterpretationException("Could not interpret h", expressoSession.getElement().getPositionInFile(), 0,
						x);
				}
				this.s6 = style.get("s6", int.class);
			}

			@Override
			D create(ModelSetInstance models, List<A> aChildren) throws ModelInstantiationException {
				return new D(this, models, aChildren);
			}
		}

		/** Field h */
		public final SettableValue<Integer> h;
		/** Style value s6 */
		public final ObservableValue<Integer> s6;

		D(Synth synth, ModelSetInstance msi, List<A> children) throws ModelInstantiationException {
			super(synth, msi, children);
			h = synth.h.get(msi);
			s6 = synth.s6.evaluate(msi);
		}
	}
}
