package org.observe.quick.style;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.observe.ObservableValue;
import org.observe.SettableValue;
import org.observe.expresso.Expresso;
import org.observe.expresso.ExpressoQIS;
import org.observe.expresso.ExpressoTesting.ExpressoTest;
import org.observe.expresso.ModelType.ModelInstanceType;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.ObservableModelSet.ValueContainer;
import org.observe.expresso.ObservableModelSet.ValueCreator;
import org.observe.quick.style.QuickElementStyle.QuickElementStyleAttribute;
import org.qommons.QommonsUtils;
import org.qommons.Version;
import org.qommons.collect.BetterList;
import org.qommons.config.QonfigInterpretation;
import org.qommons.config.QonfigInterpretationException;
import org.qommons.config.QonfigInterpreterCore.Builder;
import org.qommons.config.QonfigToolkit;
import org.qommons.config.SpecialSession;

public class TestInterpretation implements QonfigInterpretation {
	/** The name of the test toolkit */
	public static final String TOOLKIT_NAME = "Quick-Style-Test";
	/** The version of the test toolkit */
	public static final Version VERSION = new Version(0, 1, 0);

	private QonfigToolkit theToolkit;

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
		theToolkit = toolkit;
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

	public static class A {
		public static class Def implements ValueContainer<SettableValue<?>, SettableValue<A>> {
			private final ExpressoQIS expressoSession;
			private final ValueContainer<SettableValue<?>, SettableValue<Boolean>> a;
			private final ValueContainer<SettableValue<?>, SettableValue<Boolean>> b;
			private final ValueContainer<SettableValue<?>, SettableValue<Integer>> c;
			private final ValueContainer<SettableValue<?>, SettableValue<Boolean>> d;
			private final QuickElementStyleAttribute<Boolean> s0;
			private final QuickElementStyleAttribute<Integer> s1;
			private final QuickElementStyleAttribute<Boolean> s2;

			public Def(StyleQIS session) throws QonfigInterpretationException {
				expressoSession = session.as(ExpressoQIS.class);
				a = expressoSession.getExpressoEnv().getModels().getValue("a", ModelTypes.Value.BOOLEAN);
				b = expressoSession.getExpressoEnv().getModels().getValue("b", ModelTypes.Value.BOOLEAN);
				c = expressoSession.getExpressoEnv().getModels().getValue("c", ModelTypes.Value.INT);
				d = expressoSession.getExpressoEnv().getModels().getValue("d", ModelTypes.Value.BOOLEAN);

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
			public SettableValue<A> get(ModelSetInstance models) {
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

		public final SettableValue<Boolean> a;
		public final SettableValue<Boolean> b;
		public final SettableValue<Integer> c;
		public final SettableValue<Boolean> d;
		public final ObservableValue<Boolean> s0;
		public final ObservableValue<Integer> s1;
		public final ObservableValue<Boolean> s2;

		A(Def def, ModelSetInstance msi) {
			this.a = def.a.get(msi);
			this.b = def.b.get(msi);
			this.c = def.c.get(msi);
			this.d = def.d.get(msi);
			this.s0 = def.s0.evaluate(msi);
			this.s1 = def.s1.evaluate(msi);
			this.s2 = def.s2.evaluate(msi);
		}

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

	public static class B {
		public static class Def<T extends B> implements ValueContainer<SettableValue<?>, SettableValue<T>> {
			final Class<T> clazz;
			final ExpressoQIS expressoSession;
			final ValueContainer<SettableValue<?>, SettableValue<Boolean>> e;
			final ValueContainer<SettableValue<?>, SettableValue<Integer>> f;
			final QuickElementStyleAttribute<Integer> s3;
			final QuickElementStyleAttribute<Integer> s4;
			final List<ValueContainer<SettableValue<?>, SettableValue<A>>> children;

			public static Def<B> create(StyleQIS session) throws QonfigInterpretationException {
				return new Def<>(session, B.class);
			}

			Def(StyleQIS session, Class<T> clazz) throws QonfigInterpretationException {
				this.clazz = clazz;
				expressoSession = session.as(ExpressoQIS.class);
				e = expressoSession.getExpressoEnv().getModels().getValue("e", ModelTypes.Value.BOOLEAN);
				f = expressoSession.getExpressoEnv().getModels().getValue("f", ModelTypes.Value.INT);

				Map<String, List<QuickStyleAttribute<?>>> attrs = session.getStyle().getAttributes().stream()
					.collect(Collectors.groupingBy(QuickStyleAttribute::getName));
				s3 = session.getStyle().get((QuickStyleAttribute<Integer>) attrs.get("s3").get(0));
				s4 = session.getStyle().get((QuickStyleAttribute<Integer>) attrs.get("s4").get(0));
				children = session.<ValueCreator<SettableValue<?>, SettableValue<A>>> interpretChildren("a", ValueCreator.class)//
					.stream().map(ValueCreator::createValue).collect(Collectors.toList());
			}

			@Override
			public ModelInstanceType<SettableValue<?>, SettableValue<T>> getType() {
				return ModelTypes.Value.forType(clazz);
			}

			@Override
			public SettableValue<T> get(ModelSetInstance models) {
				ModelSetInstance localModels = expressoSession.wrapLocal(models);
				return SettableValue.of(clazz, create(localModels, //
					children.stream().map(def -> def.get(localModels).get()).collect(Collectors.toList())), "Immutable");
			}

			T create(ModelSetInstance models, List<A> aChildren) {
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

		public final SettableValue<Boolean> e;
		public final SettableValue<Integer> f;
		public final ObservableValue<Integer> s3;
		public final ObservableValue<Integer> s4;
		public final List<A> a;

		B(Def<?> def, ModelSetInstance msi, List<A> children) {
			this.e = def.e.get(msi);
			this.f = def.f.get(msi);
			this.s3 = def.s3.evaluate(msi);
			this.s4 = def.s4.evaluate(msi);
			this.a = children;
		}
	}

	public static class C extends B {
		public static class Def extends B.Def<C> {
			final ValueContainer<SettableValue<?>, SettableValue<Boolean>> g;
			final QuickElementStyleAttribute<Boolean> s5;

			public Def(StyleQIS session) throws QonfigInterpretationException {
				super(session, C.class);
				g = expressoSession.getExpressoEnv().getModels().getValue("g", ModelTypes.Value.BOOLEAN);
				Map<String, List<QuickStyleAttribute<?>>> attrs = session.getStyle().getAttributes().stream()
					.collect(Collectors.groupingBy(QuickStyleAttribute::getName));
				s5 = session.getStyle().get((QuickStyleAttribute<Boolean>) attrs.get("s5").get(0));
			}

			@Override
			C create(ModelSetInstance models, List<A> aChildren) {
				return new C(this, models, aChildren);
			}
		}

		public final SettableValue<Boolean> g;
		public final ObservableValue<Boolean> s5;

		C(Def def, ModelSetInstance msi, List<A> children) {
			super(def, msi, children);
			g = def.g.get(msi);
			s5 = def.s5.evaluate(msi);
		}
	}

	public static class D extends B {
		public static class Def extends B.Def<D> {
			final ValueContainer<SettableValue<?>, SettableValue<Integer>> h;
			final QuickElementStyleAttribute<Integer> s6;

			public Def(StyleQIS session) throws QonfigInterpretationException {
				super(session, D.class);
				h = expressoSession.getExpressoEnv().getModels().getValue("h", ModelTypes.Value.INT);
				Map<String, List<QuickStyleAttribute<?>>> attrs = session.getStyle().getAttributes().stream()
					.collect(Collectors.groupingBy(QuickStyleAttribute::getName));
				s6 = session.getStyle().get((QuickStyleAttribute<Integer>) attrs.get("s6").get(0));
			}

			@Override
			D create(ModelSetInstance models, List<A> aChildren) {
				return new D(this, models, aChildren);
			}
		}

		public final SettableValue<Integer> h;
		public final ObservableValue<Integer> s6;

		D(Def def, ModelSetInstance msi, List<A> children) {
			super(def, msi, children);
			h = def.h.get(msi);
			s6 = def.s6.evaluate(msi);
		}
	}
}
