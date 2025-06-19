package org.observe.quick.draw;

import org.observe.SettableValue;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableModelSet.InterpretedValueSynth;
import org.observe.expresso.qonfig.CompiledExpression;
import org.observe.expresso.qonfig.ExElement;
import org.observe.expresso.qonfig.ExElementTraceable;
import org.observe.expresso.qonfig.ExpressoQIS;
import org.observe.expresso.qonfig.QonfigAttributeGetter;
import org.observe.quick.QuickWithBackground;
import org.observe.quick.style.QuickStyledElement;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretationException;

public interface QuickRotated extends QuickStyledElement {
	public static final String ROTATED = "rotated";

	/**
	 * The definition of a {@link QuickWithBackground}
	 *
	 * @param <E> The sub-type of the element to create
	 */
	@ExElementTraceable(toolkit = QuickDrawInterpretation.DRAW,
		qonfigType = ROTATED,
		interpretation = Interpreted.class,
		instance = QuickRotated.class)
	public interface Def<E extends QuickRotated> extends QuickStyledElement.Def<E> {
		@QonfigAttributeGetter("rotation")
		CompiledExpression getRotation();

		/**
		 * Abstract {@link QuickWithBackground} definition implementation
		 *
		 * @param <E> The sub-type of element to create
		 */
		public static abstract class Abstract<E extends QuickRotated> extends QuickStyledElement.Def.Abstract<E> implements Def<E> {
			private CompiledExpression theRotation;

			/**
			 * @param parent The parent element for this element
			 * @param type The Qonfig type of this element
			 */
			protected Abstract(ExElement.Def<?> parent, QonfigElementOrAddOn type) {
				super(parent, type);
			}

			@Override
			public CompiledExpression getRotation() {
				return theRotation;
			}

			@Override
			protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
				super.doUpdate(session);
				theRotation = getAttributeExpression("rotation", session);
			}
		}
	}

	/**
	 * The interpretation of a {@link QuickWithBackground}
	 *
	 * @param <E> The sub-type of the element to create
	 */
	public interface Interpreted<E extends QuickRotated> extends QuickStyledElement.Interpreted<E> {
		@Override
		Def<? super E> getDefinition();

		InterpretedValueSynth<SettableValue<?>, SettableValue<Float>> getRotation();

		/**
		 * Abstract {@link QuickRotated} interpretation implementation
		 *
		 * @param <E> The sub-type of element to create
		 */
		public static class Abstract<E extends QuickRotated> extends QuickStyledElement.Interpreted.Abstract<E> implements Interpreted<E> {
			private InterpretedValueSynth<SettableValue<?>, SettableValue<Float>> theRotation;

			/**
			 * @param definition The definition producing this interpretation
			 * @param parent The interpreted parent
			 */
			protected Abstract(Def<? super E> definition, ExElement.Interpreted<?> parent) {
				super(definition, parent);
			}

			@Override
			public Def<? super E> getDefinition() {
				return (Def<? super E>) super.getDefinition();
			}

			@Override
			public InterpretedValueSynth<SettableValue<?>, SettableValue<Float>> getRotation() {
				return theRotation;
			}

			@Override
			protected void doUpdate() throws ExpressoInterpretationException {
				super.doUpdate();
				theRotation = interpret(getDefinition().getRotation(), ModelTypes.Value.forType(Float.class));
			}
		}
	}

	SettableValue<Float> getRotation();
}
