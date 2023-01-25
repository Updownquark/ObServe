package org.observe.quick.style;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.observe.ObservableValue;
import org.observe.SettableValue;
import org.observe.expresso.Expresso;
import org.observe.expresso.ExpressoQIS;
import org.observe.expresso.ExpressoTesting.ExpressoTest;
import org.observe.expresso.ModelException;
import org.observe.expresso.ModelType.ModelInstanceType;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.ObservableModelSet.ValueContainer;
import org.observe.expresso.ObservableModelSet.ValueCreator;
import org.observe.expresso.TypeConversionException;
import org.observe.quick.style.QuickElementStyle.QuickElementStyleAttribute;
import org.qommons.QommonsUtils;
import org.qommons.Version;
import org.qommons.collect.BetterList;
import org.qommons.config.QonfigEvaluationException;
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
		return interpreter//
			.createWith("a", ValueCreator.class, session -> ValueCreator.constant(new A.Def(session.as(StyleQIS.class))))//
			.createWith("b", ValueCreator.class, session -> ValueCreator.constant(B.Def.create(session.as(StyleQIS.class))))//
			.createWith("c", ValueCreator.class, session -> ValueCreator.constant(new C.Def(session.as(StyleQIS.class))))//
			.createWith("d", ValueCreator.class, session -> ValueCreator.constant(new D.Def(session.as(StyleQIS.class))))//
			.modifyWith("styled-test", ExpressoTest.class, new Expresso.ElementModelAugmentation<ExpressoTest>() {
				@Override
				public void augmentElementModel(ExpressoQIS session, org.observe.expresso.ObservableModelSet.Builder builder)
					throws QonfigInterpretationException {
					for (ExpressoQIS value : session.forChildren("styled-value"))
						builder.withMaker(value.getAttributeText("name"), value.interpret(ValueCreator.class));
				}
			})//
			;
	}

	/** Entity structure A for testing styles */
	public static class A {
		/** ValueContainer for producing instances of {@link A} */
		public static class Def implements ValueContainer<SettableValue<?>, SettableValue<A>> {
			private final ExpressoQIS expressoSession;
			private final ValueContainer<SettableValue<?>, SettableValue<Boolean>> a;
			private final ValueContainer<SettableValue<?>, SettableValue<Boolean>> b;
			private final ValueContainer<SettableValue<?>, SettableValue<Integer>> c;
			private final ValueContainer<SettableValue<?>, SettableValue<Boolean>> d;
			private final QuickElementStyleAttribute<Boolean> s0;
			private final QuickElementStyleAttribute<Integer> s1;
			private final QuickElementStyleAttribute<Boolean> s2;

			/**
			 * @param session The session from which to get the data defining the {@link A} instance
			 * @throws QonfigInterpretationException If the {@link A} instance could not be parsed
			 */
			public Def(StyleQIS session) throws QonfigInterpretationException {
				expressoSession = session.as(ExpressoQIS.class);
				try {
					a = expressoSession.getExpressoEnv().getModels().getValue("a", ModelTypes.Value.BOOLEAN);
				} catch (ModelException | TypeConversionException x) {
					throw new QonfigInterpretationException("Could not interpret a", x, session.getElement().getPositionInFile(), 0);
				} catch (QonfigEvaluationException x) {
					throw new QonfigInterpretationException("Could not interpret a", x, x.getPosition(), x.getErrorLength());
				}
				try {
					b = expressoSession.getExpressoEnv().getModels().getValue("b", ModelTypes.Value.BOOLEAN);
				} catch (ModelException | TypeConversionException x) {
					throw new QonfigInterpretationException("Could not interpret b", x, session.getElement().getPositionInFile(), 0);
				} catch (QonfigEvaluationException x) {
					throw new QonfigInterpretationException("Could not interpret b", x, x.getPosition(), x.getErrorLength());
				}
				try {
					c = expressoSession.getExpressoEnv().getModels().getValue("c", ModelTypes.Value.INT);
				} catch (ModelException | TypeConversionException x) {
					throw new QonfigInterpretationException("Could not interpret c", x, session.getElement().getPositionInFile(), 0);
				} catch (QonfigEvaluationException x) {
					throw new QonfigInterpretationException("Could not interpret c", x, x.getPosition(), x.getErrorLength());
				}
				try {
					d = expressoSession.getExpressoEnv().getModels().getValue("d", ModelTypes.Value.BOOLEAN);
				} catch (ModelException | TypeConversionException x) {
					throw new QonfigInterpretationException("Could not interpret d", x, session.getElement().getPositionInFile(), 0);
				} catch (QonfigEvaluationException x) {
					throw new QonfigInterpretationException("Could not interpret d", x, x.getPosition(), x.getErrorLength());
				}

				Map<String, List<QuickStyleAttribute<?>>> attrs = session.getStyle().getAttributes().stream()
					.collect(Collectors.groupingBy(QuickStyleAttribute::getName));
				s0 = session.getStyle().get((QuickStyleAttribute<Boolean>) attrs.get("s0").get(0));
				s1 = session.getStyle().get((QuickStyleAttribute<Integer>) attrs.get("s1").get(0));
				s2 = session.getStyle().get((QuickStyleAttribute<Boolean>) attrs.get("s2").get(0));
			}

			@Override
			public ModelInstanceType<SettableValue<?>, SettableValue<A>> getType() {
				return ModelTypes.Value.forType(A.class);
			}

			@Override
			public SettableValue<A> get(ModelSetInstance models) throws QonfigEvaluationException {
				return SettableValue.of(A.class, new A(this, expressoSession.wrapLocal(models)), "Immutable");
			}

			@Override
			public SettableValue<A> forModelCopy(SettableValue<A> value, ModelSetInstance sourceModels, ModelSetInstance newModels) {
				return value;
			}

			@Override
			public BetterList<ValueContainer<?, ?>> getCores() {
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

		A(Def def, ModelSetInstance msi) throws QonfigEvaluationException {
			this.a = def.a.get(msi);
			this.b = def.b.get(msi);
			this.c = def.c.get(msi);
			this.d = def.d.get(msi);
			this.s0 = def.s0.evaluate(msi);
			this.s1 = def.s1.evaluate(msi);
			this.s2 = def.s2.evaluate(msi);
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
		 * ValueContainer for producing instances of {@link B}
		 *
		 * @param <T> The {@link B} sub-type of this definition
		 */
		public static class Def<T extends B> implements ValueContainer<SettableValue<?>, SettableValue<T>> {
			final Class<T> clazz;
			final ExpressoQIS expressoSession;
			final ValueContainer<SettableValue<?>, SettableValue<Boolean>> e;
			final ValueContainer<SettableValue<?>, SettableValue<Integer>> f;
			final QuickElementStyleAttribute<Integer> s3;
			final QuickElementStyleAttribute<Integer> s4;
			final List<ValueContainer<SettableValue<?>, SettableValue<A>>> children;

			/**
			 * @param session The session from which to get the data defining the {@link B} instance
			 * @throws QonfigInterpretationException If the {@link B} instance could not be parsed
			 * @return The {@link ValueContainer} to produce the instance
			 */
			public static Def<B> create(StyleQIS session) throws QonfigInterpretationException {
				return new Def<>(session, B.class);
			}

			Def(StyleQIS session, Class<T> clazz) throws QonfigInterpretationException {
				this.clazz = clazz;
				expressoSession = session.as(ExpressoQIS.class);
				try {
					e = expressoSession.getExpressoEnv().getModels().getValue("e", ModelTypes.Value.BOOLEAN);
				} catch (ModelException | TypeConversionException x) {
					throw new QonfigInterpretationException("Could not interpret e", x, session.getElement().getPositionInFile(), 0);
				} catch (QonfigEvaluationException x) {
					throw new QonfigInterpretationException("Could not interpret e", x, x.getPosition(), x.getErrorLength());
				}
				try {
					f = expressoSession.getExpressoEnv().getModels().getValue("f", ModelTypes.Value.INT);
				} catch (ModelException | TypeConversionException x) {
					throw new QonfigInterpretationException("Could not interpret f", x, session.getElement().getPositionInFile(), 0);
				} catch (QonfigEvaluationException x) {
					throw new QonfigInterpretationException("Could not interpret f", x, x.getPosition(), x.getErrorLength());
				}

				Map<String, List<QuickStyleAttribute<?>>> attrs = session.getStyle().getAttributes().stream()
					.collect(Collectors.groupingBy(QuickStyleAttribute::getName));
				s3 = session.getStyle().get((QuickStyleAttribute<Integer>) attrs.get("s3").get(0));
				s4 = session.getStyle().get((QuickStyleAttribute<Integer>) attrs.get("s4").get(0));
				children = new ArrayList<>();
				try {
					for (ValueCreator<SettableValue<?>, SettableValue<A>> child : session.interpretChildren("a", ValueCreator.class))
						children.add(child.createContainer());
				} catch (QonfigEvaluationException x) {
					throw new QonfigInterpretationException("Could not evaluate children", x, x.getPosition(), x.getErrorLength());
				}
			}

			@Override
			public ModelInstanceType<SettableValue<?>, SettableValue<T>> getType() {
				return ModelTypes.Value.forType(clazz);
			}

			@Override
			public SettableValue<T> get(ModelSetInstance models) throws QonfigEvaluationException {
				ModelSetInstance localModels = expressoSession.wrapLocal(models);
				List<A> childrenInstances = new ArrayList<>();
				for (ValueContainer<SettableValue<?>, SettableValue<A>> child : children)
					childrenInstances.add(child.get(localModels).get());
				return SettableValue.of(clazz, create(localModels, childrenInstances), "Immutable");
			}

			T create(ModelSetInstance models, List<A> aChildren) throws QonfigEvaluationException {
				return (T) new B(this, models, aChildren);
			}

			@Override
			public SettableValue<T> forModelCopy(SettableValue<T> value, ModelSetInstance sourceModels, ModelSetInstance newModels) {
				return value;
			}

			@Override
			public BetterList<ValueContainer<?, ?>> getCores() {
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

		B(Def<?> def, ModelSetInstance msi, List<A> children) throws QonfigEvaluationException {
			this.e = def.e.get(msi);
			this.f = def.f.get(msi);
			this.s3 = def.s3.evaluate(msi);
			this.s4 = def.s4.evaluate(msi);
			this.a = children;
		}
	}

	/** Entity structure C for testing styles */
	public static class C extends B {
		/** ValueContainer for producing instances of {@link C} */
		public static class Def extends B.Def<C> {
			final ValueContainer<SettableValue<?>, SettableValue<Boolean>> g;
			final QuickElementStyleAttribute<Boolean> s5;

			/**
			 * @param session The session from which to get the data defining the {@link C} instance
			 * @throws QonfigInterpretationException If the {@link C} instance could not be parsed
			 */
			public Def(StyleQIS session) throws QonfigInterpretationException {
				super(session, C.class);
				try {
					g = expressoSession.getExpressoEnv().getModels().getValue("g", ModelTypes.Value.BOOLEAN);
				} catch (ModelException | TypeConversionException x) {
					throw new QonfigInterpretationException("Could not interpret g", x, session.getElement().getPositionInFile(), 0);
				} catch (QonfigEvaluationException x) {
					throw new QonfigInterpretationException("Could not interpret g", x, x.getPosition(), x.getErrorLength());
				}
				Map<String, List<QuickStyleAttribute<?>>> attrs = session.getStyle().getAttributes().stream()
					.collect(Collectors.groupingBy(QuickStyleAttribute::getName));
				s5 = session.getStyle().get((QuickStyleAttribute<Boolean>) attrs.get("s5").get(0));
			}

			@Override
			C create(ModelSetInstance models, List<A> aChildren) throws QonfigEvaluationException {
				return new C(this, models, aChildren);
			}
		}

		/** Field g */
		public final SettableValue<Boolean> g;
		/** Style value s5 */
		public final ObservableValue<Boolean> s5;

		C(Def def, ModelSetInstance msi, List<A> children) throws QonfigEvaluationException {
			super(def, msi, children);
			g = def.g.get(msi);
			s5 = def.s5.evaluate(msi);
		}
	}

	/** Entity structure D for testing styles */
	public static class D extends B {
		/** ValueContainer for producing instances of {@link D} */
		public static class Def extends B.Def<D> {
			final ValueContainer<SettableValue<?>, SettableValue<Integer>> h;
			final QuickElementStyleAttribute<Integer> s6;

			/**
			 * @param session The session from which to get the data defining the {@link D} instance
			 * @throws QonfigInterpretationException If the {@link D} instance could not be parsed
			 */
			public Def(StyleQIS session) throws QonfigInterpretationException {
				super(session, D.class);
				try {
					h = expressoSession.getExpressoEnv().getModels().getValue("h", ModelTypes.Value.INT);
				} catch (ModelException | TypeConversionException x) {
					throw new QonfigInterpretationException("Could not interpret h", x, session.getElement().getPositionInFile(), 0);
				} catch (QonfigEvaluationException x) {
					throw new QonfigInterpretationException("Could not interpret h", x, x.getPosition(), x.getErrorLength());
				}
				Map<String, List<QuickStyleAttribute<?>>> attrs = session.getStyle().getAttributes().stream()
					.collect(Collectors.groupingBy(QuickStyleAttribute::getName));
				s6 = session.getStyle().get((QuickStyleAttribute<Integer>) attrs.get("s6").get(0));
			}

			@Override
			D create(ModelSetInstance models, List<A> aChildren) throws QonfigEvaluationException {
				return new D(this, models, aChildren);
			}
		}

		/** Field h */
		public final SettableValue<Integer> h;
		/** Style value s6 */
		public final ObservableValue<Integer> s6;

		D(Def def, ModelSetInstance msi, List<A> children) throws QonfigEvaluationException {
			super(def, msi, children);
			h = def.h.get(msi);
			s6 = def.s6.evaluate(msi);
		}
	}
}
