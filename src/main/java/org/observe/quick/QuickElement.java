package org.observe.quick;

import java.util.Collection;
import java.util.function.Function;

import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.ExpressoQIS;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ObservableModelSet.InterpretedModelSet;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.quick.style.StyleQIS;
import org.qommons.ClassMap;
import org.qommons.config.AbstractQIS;
import org.qommons.config.QonfigAddOn;
import org.qommons.config.QonfigElement;
import org.qommons.config.QonfigInterpretationException;

public interface QuickElement {
	public static final String SESSION_QUICK_ELEMENT = "quick.element";

	public interface Def<E extends QuickElement> {
		Def<?> getParentElement();

		QonfigElement getElement();

		ExpressoQIS getExpressoSession();

		StyleQIS getStyleSession();

		<AO extends QuickAddOn.Def<? super E, ?>> AO getAddOn(Class<AO> addOn);

		Collection<QuickAddOn.Def<? super E, ?>> getAddOns();

		default <AO extends QuickAddOn.Def<? super E, ?>, T> T getAddOnValue(Class<AO> addOn, Function<? super AO, ? extends T> fn) {
			AO ao = getAddOn(addOn);
			return ao == null ? null : fn.apply(ao);
		}

		Def<E> update(AbstractQIS<?> session) throws QonfigInterpretationException;

		public abstract class Abstract<E extends QuickElement> implements Def<E> {
			private final QuickElement.Def<?> theParent;
			private QonfigElement theElement;
			private ExpressoQIS theExpressoSession;
			private StyleQIS theStyleSession;
			private final ClassMap<QuickAddOn.Def<? super E, ?>> theAddOns;

			public Abstract(QuickElement.Def<?> parent, QonfigElement element) {
				theParent = parent;
				theElement = element;
				theAddOns = new ClassMap<>();
			}

			@Override
			public QuickElement.Def<?> getParentElement() {
				return theParent;
			}

			@Override
			public QonfigElement getElement() {
				return theElement;
			}

			@Override
			public ExpressoQIS getExpressoSession() {
				return theExpressoSession;
			}

			@Override
			public StyleQIS getStyleSession() {
				return theStyleSession;
			}

			public void setStyleSession(StyleQIS styleSession) {
				theStyleSession = styleSession;
			}

			@Override
			public <AO extends QuickAddOn.Def<? super E, ?>> AO getAddOn(Class<AO> addOn) {
				return (AO) theAddOns.get(addOn, ClassMap.TypeMatch.SUB_TYPE);
			}

			@Override
			public Collection<QuickAddOn.Def<? super E, ?>> getAddOns() {
				return theAddOns.getAllValues();
			}

			@Override
			public Abstract<E> update(AbstractQIS<?> session) throws QonfigInterpretationException {
				session.put(SESSION_QUICK_ELEMENT, this);
				theElement = session.getElement();
				theExpressoSession = session.as(ExpressoQIS.class);
				theStyleSession = session.as(StyleQIS.class);

				if (theAddOns.isEmpty()) {
					// Add-ons can't change, because if they do, the Quick definition should be re-interpreted from the session
					for (QonfigAddOn addOn : session.getElement().getInheritance().values())
						addAddOn(session.asElementOnly(addOn));

					for (QuickAddOn.Def<? super E, ?> addOn : theAddOns.getAllValues())
						addOn.update(theExpressoSession.asElement(addOn.getType()));
				}
				return this;
			}

			private void addAddOn(AbstractQIS<?> session) throws QonfigInterpretationException {
				if (session.supportsInterpretation(QuickAddOn.Def.class)) {
					QuickAddOn.Def<? super E, ?> addOn = session.interpret(QuickAddOn.Def.class);
					theAddOns.put(addOn.getClass(), addOn);
				} else {
					for (QonfigAddOn inh : session.getFocusType().getInheritance())
						addAddOn(session.asElementOnly(inh));
				}
			}

			@Override
			public String toString() {
				return theElement.toString();
			}
		}
	}

	public interface Interpreted<E extends QuickElement> {
		Def<? super E> getDefinition();

		Interpreted<?> getParentElement();

		<AO extends QuickAddOn.Interpreted<? super E, ?>> AO getAddOn(Class<AO> addOn);

		Collection<QuickAddOn.Interpreted<? super E, ?>> getAddOns();

		default <AO extends QuickAddOn.Interpreted<? super E, ?>, T> T getAddOnValue(Class<AO> addOn,
			Function<? super AO, ? extends T> fn) {
			AO ao = getAddOn(addOn);
			return ao == null ? null : fn.apply(ao);
		}

		public abstract class Abstract<E extends QuickElement> implements Interpreted<E> {
			private final Def<? super E> theDefinition;
			private final Interpreted<?> theParent;
			private final ClassMap<QuickAddOn.Interpreted<? super E, ?>> theAddOns;

			public Abstract(Def<? super E> definition, Interpreted<?> parent) {
				theDefinition = definition;
				theParent = parent;
				theAddOns = new ClassMap<>();
				for (QuickAddOn.Def<? super E, ?> addOn : definition.getAddOns()) {
					QuickAddOn.Interpreted<? super E, ?> interp = (QuickAddOn.Interpreted<? super E, ?>) addOn.interpret(this);
					theAddOns.put(interp.getClass(), interp);
				}
			}

			@Override
			public Def<? super E> getDefinition() {
				return theDefinition;
			}

			@Override
			public Interpreted<?> getParentElement() {
				return theParent;
			}

			@Override
			public <AO extends QuickAddOn.Interpreted<? super E, ?>> AO getAddOn(Class<AO> addOn) {
				return (AO) theAddOns.get(addOn, ClassMap.TypeMatch.SUB_TYPE);
			}

			@Override
			public Collection<QuickAddOn.Interpreted<? super E, ?>> getAddOns() {
				return theAddOns.getAllValues();
			}

			protected Interpreted<E> update(InterpretedModelSet models) throws ExpressoInterpretationException {
				// Do I need this?
				// theDefinition.getExpressoSession().setExpressoEnv(theDefinition.getExpressoSession().getExpressoEnv().with(models,
				// null));
				theDefinition.getExpressoSession().interpretLocalModel();
				for (QuickAddOn.Interpreted<?, ?> addOn : theAddOns.getAllValues())
					addOn.update(models);
				return this;
			}
		}
	}

	Interpreted<?> getInterpreted();

	QuickElement getParentElement();

	ModelSetInstance getModels();

	<AO extends QuickAddOn<?>> AO getAddOn(Class<AO> addOn);

	Collection<QuickAddOn<?>> getAddOns();

	default <AO extends QuickAddOn<?>, T> T getAddOnValue(Class<AO> addOn, Function<? super AO, ? extends T> fn) {
		AO ao = getAddOn(addOn);
		return ao == null ? null : fn.apply(ao);
	}

	public abstract class Abstract implements QuickElement {
		private final Interpreted<?> theInterpreted;
		private final QuickElement theParent;
		private final ClassMap<QuickAddOn<?>> theAddOns;
		private ModelSetInstance theModels;

		public Abstract(Interpreted<?> interpreted, QuickElement parent) {
			theInterpreted = interpreted;
			theParent = parent;
			theAddOns = new ClassMap<>();
			for (QuickAddOn.Interpreted<?, ?> addOn : theInterpreted.getAddOns()) {
				QuickAddOn<?> inst = ((QuickAddOn.Interpreted<QuickElement, ?>) addOn).create(this);
				theAddOns.put(inst.getClass(), inst);
			}
		}

		@Override
		public Interpreted<?> getInterpreted() {
			return theInterpreted;
		}

		@Override
		public QuickElement getParentElement() {
			return theParent;
		}

		@Override
		public ModelSetInstance getModels() {
			return theModels;
		}

		@Override
		public <AO extends QuickAddOn<?>> AO getAddOn(Class<AO> addOn) {
			return (AO) theAddOns.get(addOn, ClassMap.TypeMatch.SUB_TYPE);
		}

		@Override
		public Collection<QuickAddOn<?>> getAddOns() {
			return theAddOns.getAllValues();
		}

		protected Abstract update(ModelSetInstance models) throws ModelInstantiationException {
			theModels = theInterpreted.getDefinition().getExpressoSession().wrapLocal(models);
			return this;
		}
	}

	public static boolean typesEqual(QonfigElement element1, QonfigElement element2) {
		return element1.getType() == element2.getType() && element1.getInheritance().equals(element2.getInheritance());
	}

	public static <E extends QuickElement, D extends QuickElement.Def<E>> D useOrReplace(Class<? extends D> type, D def,
		AbstractQIS<?> session, String childName) throws QonfigInterpretationException, IllegalArgumentException {
		QonfigElement element = session.getChildren(childName).peekFirst();
		if (element == null)
			return null;
		else if (def != null && typesEqual(def.getElement(), element))
			return def;
		AbstractQIS<?> childSession = session.forChildren(childName).getFirst();
		def = childSession.interpret(type);
		def.update(childSession);
		return def;
	}
}
