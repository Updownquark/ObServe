package org.observe.quick.draw;

import java.awt.Color;

import org.observe.ObservableValue;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.InterpretedExpressoEnv;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.qonfig.ExElement;
import org.observe.expresso.qonfig.ExElementTraceable;
import org.observe.quick.QuickBorder;
import org.observe.quick.style.QuickCompiledStyle;
import org.observe.quick.style.QuickInterpretedStyle;
import org.observe.quick.style.QuickInterpretedStyleCache;
import org.observe.quick.style.QuickStyleAttribute;
import org.observe.quick.style.QuickStyleAttributeDef;
import org.observe.quick.style.QuickStyleSheet;
import org.observe.quick.style.QuickStyled;
import org.observe.quick.style.QuickStyled.QuickInstanceStyle;
import org.observe.quick.style.QuickTypeStyle;
import org.qommons.config.QonfigElementOrAddOn;

public interface QuickBorderedShape extends QuickWithStroke {
	public static final String BORDERED_SHAPE = "bordered-shape";

	@ExElementTraceable(toolkit = QuickDrawInterpretation.DRAW,
		qonfigType = BORDERED_SHAPE,
		interpretation = Interpreted.class,
		instance = QuickBorderedShape.class)
	public interface Def<E extends QuickShape> extends QuickWithStroke.Def<E> {
		@Override
		QuickBorderedShapeStyle.Def wrap(QuickInstanceStyle.Def parentStyle, QuickCompiledStyle style);

		@Override
		QuickBorderedShapeStyle.Def getStyle();

		@Override
		Interpreted<? extends E> interpret(ExElement.Interpreted<?> parent);

		public abstract class Abstract<E extends QuickBorderedShape> extends QuickShape.Def.Abstract<E> implements QuickBorderedShape.Def<E> {
			/**
			 * @param parent The parent container definition
			 * @param type The element type that this shape is interpreted from
			 */
			protected Abstract(ExElement.Def<?> parent, QonfigElementOrAddOn type) {
				super(parent, type);
			}

			@Override
			public QuickBorderedShapeStyle.Def getStyle() {
				return (QuickBorderedShapeStyle.Def) super.getStyle();
			}

			@Override
			public QuickBorderedShapeStyle.Def wrap(QuickInstanceStyle.Def parentStyle, QuickCompiledStyle style) {
				return new QuickBorderedShapeStyle.Def.Default(parentStyle, this, style);
			}
		}
	}

	public interface Interpreted<E extends QuickBorderedShape> extends QuickWithStroke.Interpreted<E> {
		@Override
		QuickBorderedShape.Def<? super E> getDefinition();

		@Override
		public QuickBorderedShapeStyle.Interpreted getStyle();

		@Override
		public abstract E create();

		public abstract class Abstract<E extends QuickBorderedShape> extends QuickWithStroke.Interpreted.Abstract<E>
		implements QuickBorderedShape.Interpreted<E> {
			protected Abstract(QuickBorderedShape.Def<? super E> definition, ExElement.Interpreted<?> parent) {
				super(definition, parent);
			}

			@Override
			public QuickBorderedShape.Def<? super E> getDefinition() {
				return (QuickBorderedShape.Def<? super E>) super.getDefinition();
			}

			@Override
			public QuickBorderedShapeStyle.Interpreted getStyle() {
				return (QuickBorderedShapeStyle.Interpreted) super.getStyle();
			}
		}
	}

	@Override
	public QuickBorderedShapeStyle getStyle();

	@Override
	public QuickBorderedShape copy(ExElement parent);

	public interface QuickBorderedShapeStyle extends QuickWithStroke.QuickStrokeStyle, QuickBorder.QuickBorderStyle {
		public static interface Def extends QuickWithStroke.QuickStrokeStyle.Def, QuickBorder.QuickBorderStyle.Def {
			public static class Default extends QuickWithStroke.QuickStrokeStyle.Def.Default implements Def {
				private final QuickStyleAttributeDef theBorderColor;
				private final QuickStyleAttributeDef theBorderThickness;

				protected Default(QuickStyled.QuickInstanceStyle.Def parent, ExElement.Def styledElement,
					QuickCompiledStyle wrapped) {
					super(parent, styledElement, wrapped);
					QuickTypeStyle typeStyle = QuickStyled.getTypeStyle(wrapped.getStyleTypes(), getElement(), QuickDrawInterpretation.NAME,
						QuickDrawInterpretation.VERSION, BORDERED_SHAPE);
					theBorderColor = addApplicableAttribute(typeStyle.getAttribute("border-color"));
					theBorderThickness = addApplicableAttribute(typeStyle.getAttribute("thickness"));
				}

				@Override
				public QuickStyleAttributeDef getBorderColor() {
					return theBorderColor;
				}

				@Override
				public QuickStyleAttributeDef getBorderThickness() {
					return theBorderThickness;
				}

				@Override
				public Interpreted interpret(ExElement.Interpreted<?> parentEl, QuickInterpretedStyle parent)
					throws ExpressoInterpretationException {
					return new QuickBorderedShapeStyle.Interpreted.Default(this, parentEl,
						(QuickInstanceStyle.Interpreted) parent, getWrapped().interpret(parentEl, parent));
				}
			}
		}

		public static interface Interpreted extends QuickWithStroke.QuickStrokeStyle.Interpreted, QuickBorder.QuickBorderStyle.Interpreted {
			@Override
			Def getDefinition();

			@Override
			QuickBorderedShapeStyle create(QuickStyled styled);

			public static class Default extends QuickWithStroke.QuickStrokeStyle.Interpreted.Default implements Interpreted {
				private QuickElementStyleAttribute<Color> theBorderColor;
				private QuickElementStyleAttribute<Integer> theBorderThickness;

				protected Default(QuickBorderedShapeStyle.Def definition, ExElement.Interpreted<?> styledElement,
					QuickStyled.QuickInstanceStyle.Interpreted parent, QuickInterpretedStyle wrapped) {
					super(definition, styledElement, parent, wrapped);
				}

				@Override
				public QuickBorderedShapeStyle.Def getDefinition() {
					return (QuickBorderedShapeStyle.Def) super.getDefinition();
				}

				@Override
				public QuickElementStyleAttribute<Color> getBorderColor() {
					return theBorderColor;
				}

				@Override
				public QuickElementStyleAttribute<Integer> getBorderThickness() {
					return theBorderThickness;
				}

				@Override
				public void update(ExElement.Interpreted<?> element, QuickStyleSheet.Interpreted styleSheet)
					throws ExpressoInterpretationException {
					super.update(element, styleSheet);
					InterpretedExpressoEnv env = element.getDefaultEnv();
					QuickInterpretedStyleCache cache = QuickInterpretedStyleCache.get(env);
					theBorderColor = get(cache.getAttribute(getDefinition().getBorderColor(), Color.class, env));
					theBorderThickness = get(cache.getAttribute(getDefinition().getBorderThickness(), Integer.class, env));
				}

				@Override
				public QuickBorderedShapeStyle create(QuickStyled styled) {
					return new QuickBorderedShapeStyle.Default();
				}
			}
		}

		public static class Default extends QuickWithStroke.QuickStrokeStyle.Default implements QuickBorderedShapeStyle {
			private QuickStyleAttribute<Color> theBorderColorAttr;
			private QuickStyleAttribute<Integer> theBorderThicknessAttr;

			private ObservableValue<Color> theBorderColor;
			private ObservableValue<Integer> theBorderThickness;

			protected Default() {
				super();
			}

			@Override
			public ObservableValue<Color> getBorderColor() {
				return theBorderColor;
			}

			@Override
			public ObservableValue<Integer> getBorderThickness() {
				return theBorderThickness;
			}

			@Override
			public void update(QuickInstanceStyle.Interpreted interpreted, QuickStyled styled) throws ModelInstantiationException {
				super.update(interpreted, styled);

				QuickBorderedShapeStyle.Interpreted myInterpreted = (QuickBorderedShapeStyle.Interpreted) interpreted;

				theBorderColorAttr = myInterpreted.getBorderColor().getAttribute();
				theBorderThicknessAttr = myInterpreted.getBorderThickness().getAttribute();
				theBorderColor = getApplicableAttribute(theBorderColorAttr);
				theBorderThickness = getApplicableAttribute(theBorderThicknessAttr);
			}

			@Override
			public QuickBorderedShapeStyle.Default copy(QuickStyled styled) {
				QuickBorderedShapeStyle.Default copy = (QuickBorderedShapeStyle.Default) super.copy(styled);

				copy.theBorderThickness = copy.getApplicableAttribute(theBorderThicknessAttr);

				return copy;
			}
		}
	}

}
