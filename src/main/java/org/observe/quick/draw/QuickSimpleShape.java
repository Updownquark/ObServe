package org.observe.quick.draw;

import org.observe.SettableValue;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableModelSet.InterpretedValueSynth;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.ObservableModelSet.ModelValueInstantiator;
import org.observe.expresso.qonfig.CompiledExpression;
import org.observe.expresso.qonfig.ExElement;
import org.observe.expresso.qonfig.ExElementTraceable;
import org.observe.expresso.qonfig.ExpressoQIS;
import org.observe.expresso.qonfig.QonfigAttributeGetter;
import org.observe.quick.Positionable;
import org.observe.quick.QuickSize;
import org.qommons.StringUtils;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretationException;

public interface QuickSimpleShape extends QuickBorderedShape {
	public static final String SIMPLE_SHAPE = "simple-shape";

	@ExElementTraceable(toolkit = QuickDrawInterpretation.DRAW,
		qonfigType = SIMPLE_SHAPE,
		interpretation = Interpreted.class,
		instance = QuickSimpleShape.class)
	public interface Def<E extends QuickSimpleShape> extends QuickBorderedShape.Def<E> {
		@QonfigAttributeGetter("width")
		CompiledExpression getWidth();

		@QonfigAttributeGetter("height")
		CompiledExpression getHeight();

		@Override
		Interpreted<? extends E> interpret(ExElement.Interpreted<?> parent);

		public abstract class Abstract<E extends QuickSimpleShape> extends QuickBorderedShape.Def.Abstract<E>
		implements QuickSimpleShape.Def<E> {
			private CompiledExpression theWidth;
			private CompiledExpression theHeight;
			/**
			 * @param parent The parent container definition
			 * @param type The element type that this shape is interpreted from
			 */
			protected Abstract(ExElement.Def<?> parent, QonfigElementOrAddOn type) {
				super(parent, type);
			}

			@Override
			public CompiledExpression getWidth() {
				return theWidth;
			}

			@Override
			public CompiledExpression getHeight() {
				return theHeight;
			}

			@Override
			protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
				super.doUpdate(session);
				theWidth = getAttributeExpression("width", session);
				theHeight = getAttributeExpression("height", session);

				checkDimensionSpecification(getAddOn(Positionable.Def.Horizontal.class), theWidth, "horizontal", "width", session);
				checkDimensionSpecification(getAddOn(Positionable.Def.Vertical.class), theHeight, "vertical", "height", session);
			}

			private void checkDimensionSpecification(Positionable.Def<?> pos, CompiledExpression size, String dimension, String sizeName,
				ExpressoQIS session) {
				int posAttrs = countPosAttrs(pos);
				switch (posAttrs) {
				case 0:
					session.reporting().warn("No " + dimension + " positioning specified");
					break;
				case 1:
					if (size == null)
						session.reporting().error("No " + sizeName + " specified");
					break;
				case 2:
					if (size != null)
						session.reporting()
						.warn(StringUtils.capitalize(sizeName) + " may be constrained by multiple horizontal positioning values");
					break;
				case 3:
					session.reporting().warn("All 3 " + dimension + " positioning attributes specified--these may conflict");
					break;
				}
			}

			private static int countPosAttrs(Positionable.Def<?> pos) {
				return (pos.getLeading() == null ? 0 : 1)//
					+ (pos.getCenter() == null ? 0 : 1)//
					+ (pos.getTrailing() == null ? 0 : 1);
			}

			@Override
			public abstract Interpreted<? extends E> interpret(ExElement.Interpreted<?> parent);
		}
	}

	public interface Interpreted<E extends QuickSimpleShape> extends QuickBorderedShape.Interpreted<E> {
		@Override
		QuickSimpleShape.Def<? super E> getDefinition();

		InterpretedValueSynth<SettableValue<?>, SettableValue<QuickSize>> getWidth();

		InterpretedValueSynth<SettableValue<?>, SettableValue<QuickSize>> getHeight();

		@Override
		public abstract E create();

		public abstract class Abstract<E extends QuickSimpleShape> extends QuickBorderedShape.Interpreted.Abstract<E>
		implements QuickSimpleShape.Interpreted<E> {
			private InterpretedValueSynth<SettableValue<?>, SettableValue<QuickSize>> theWidth;
			private InterpretedValueSynth<SettableValue<?>, SettableValue<QuickSize>> theHeight;

			protected Abstract(QuickSimpleShape.Def<? super E> definition, ExElement.Interpreted<?> parent) {
				super(definition, parent);
			}

			@Override
			public QuickSimpleShape.Def<? super E> getDefinition() {
				return (QuickSimpleShape.Def<? super E>) super.getDefinition();
			}

			@Override
			public InterpretedValueSynth<SettableValue<?>, SettableValue<QuickSize>> getWidth() {
				return theWidth;
			}

			@Override
			public InterpretedValueSynth<SettableValue<?>, SettableValue<QuickSize>> getHeight() {
				return theHeight;
			}

			@Override
			protected void doUpdate() throws ExpressoInterpretationException {
				super.doUpdate();
				theWidth = interpret(getDefinition().getWidth(), ModelTypes.Value.forType(QuickSize.class));
				theHeight = interpret(getDefinition().getHeight(), ModelTypes.Value.forType(QuickSize.class));
			}
		}
	}

	SettableValue<QuickSize> getWidth();

	SettableValue<QuickSize> getHeight();

	@Override
	public QuickSimpleShape copy(ExElement parent);

	public abstract class Abstract extends QuickShape.Abstract implements QuickSimpleShape {
		private ModelValueInstantiator<SettableValue<QuickSize>> theWidthInstantiator;
		private ModelValueInstantiator<SettableValue<QuickSize>> theHeightInstantiator;

		private SettableValue<SettableValue<QuickSize>> theWidth;
		private SettableValue<SettableValue<QuickSize>> theHeight;

		protected Abstract(Object id) {
			super(id);
			theWidth = SettableValue.create();
			theHeight = SettableValue.create();
		}

		@Override
		public SettableValue<QuickSize> getWidth() {
			return SettableValue.flatten(theWidth);
		}

		@Override
		public SettableValue<QuickSize> getHeight() {
			return SettableValue.flatten(theHeight);
		}

		@Override
		public QuickBorderedShapeStyle getStyle() {
			return (QuickBorderedShapeStyle) super.getStyle();
		}

		@Override
		protected void doUpdate(ExElement.Interpreted<?> interpreted) throws ModelInstantiationException {
			super.doUpdate(interpreted);

			QuickSimpleShape.Interpreted<?> myInterpreted = (QuickSimpleShape.Interpreted<?>) interpreted;
			theWidthInstantiator = ExElement.instantiate(myInterpreted.getWidth());
			theHeightInstantiator = ExElement.instantiate(myInterpreted.getHeight());
		}

		@Override
		protected ModelSetInstance doInstantiate(ModelSetInstance myModels) throws ModelInstantiationException {
			myModels = super.doInstantiate(myModels);

			theWidth.set(ExElement.get(theWidthInstantiator, myModels));
			theHeight.set(ExElement.get(theHeightInstantiator, myModels));

			return myModels;
		}

		@Override
		public QuickSimpleShape.Abstract copy(ExElement parent) {
			QuickSimpleShape.Abstract copy = (QuickSimpleShape.Abstract) super.copy(parent);

			copy.theWidth = SettableValue.create();
			copy.theHeight = SettableValue.create();

			return copy;
		}
	}
}
