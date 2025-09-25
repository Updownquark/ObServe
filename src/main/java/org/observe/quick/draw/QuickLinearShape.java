package org.observe.quick.draw;

import org.observe.ObservableValue;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.InterpretedExpressoEnv;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.qonfig.ExElement;
import org.observe.expresso.qonfig.ExElementTraceable;
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

public interface QuickLinearShape extends QuickWithStroke {
	public static final String LINEAR_SHAPE = "linear-shape";

	@ExElementTraceable(toolkit = QuickDrawInterpretation.DRAW,
		qonfigType = LINEAR_SHAPE,
		interpretation = Interpreted.class,
		instance = QuickLinearShape.class)
	public interface Def<E extends QuickShape> extends QuickWithStroke.Def<E> {
		@Override
		QuickLineShapeStyle.Def wrap(QuickInstanceStyle.Def parentStyle, QuickCompiledStyle style);

		@Override
		QuickLineShapeStyle.Def getStyle();

		@Override
		Interpreted<? extends E> interpret(ExElement.Interpreted<?> parent);

		public abstract class Abstract<E extends QuickLinearShape> extends QuickShape.Def.Abstract<E> implements QuickLinearShape.Def<E> {
			/**
			 * @param parent The parent container definition
			 * @param type The element type that this shape is interpreted from
			 */
			protected Abstract(ExElement.Def<?> parent, QonfigElementOrAddOn type) {
				super(parent, type);
			}

			@Override
			public QuickLineShapeStyle.Def getStyle() {
				return (QuickLineShapeStyle.Def) super.getStyle();
			}

			@Override
			public QuickLineShapeStyle.Def wrap(QuickInstanceStyle.Def parentStyle, QuickCompiledStyle style) {
				return new QuickLineShapeStyle.Def.Default(parentStyle, this, style);
			}
		}
	}

	public interface Interpreted<E extends QuickLinearShape> extends QuickWithStroke.Interpreted<E> {
		@Override
		QuickLinearShape.Def<? super E> getDefinition();

		@Override
		public QuickLineShapeStyle.Interpreted getStyle();

		@Override
		public abstract E create();

		public abstract class Abstract<E extends QuickLinearShape> extends QuickWithStroke.Interpreted.Abstract<E>
		implements QuickLinearShape.Interpreted<E> {
			protected Abstract(QuickLinearShape.Def<? super E> definition, ExElement.Interpreted<?> parent) {
				super(definition, parent);
			}

			@Override
			public QuickLinearShape.Def<? super E> getDefinition() {
				return (QuickLinearShape.Def<? super E>) super.getDefinition();
			}

			@Override
			public QuickLineShapeStyle.Interpreted getStyle() {
				return (QuickLineShapeStyle.Interpreted) super.getStyle();
			}
		}
	}

	@Override
	public QuickLineShapeStyle getStyle();

	@Override
	public QuickLinearShape copy(ExElement parent);

	public interface QuickLineShapeStyle extends QuickWithStroke.QuickStrokeStyle {
		public static interface Def extends QuickWithStroke.QuickStrokeStyle.Def {
			QuickStyleAttributeDef getThickness();

			public static class Default extends QuickWithStroke.QuickStrokeStyle.Def.Default implements Def {
				private final QuickStyleAttributeDef theThickness;

				protected Default(QuickStyled.QuickInstanceStyle.Def parent, QuickLinearShape.Def styledElement,
					QuickCompiledStyle wrapped) {
					super(parent, styledElement, wrapped);
					QuickTypeStyle typeStyle = QuickStyled.getTypeStyle(wrapped.getStyleTypes(), getElement(), QuickDrawInterpretation.NAME,
						QuickDrawInterpretation.VERSION, LINEAR_SHAPE);
					theThickness = addApplicableAttribute(typeStyle.getAttribute("thickness"));
				}

				@Override
				public QuickStyleAttributeDef getThickness() {
					return theThickness;
				}

				@Override
				public Interpreted interpret(ExElement.Interpreted<?> parentEl, QuickInterpretedStyle parent)
					throws ExpressoInterpretationException {
					return new QuickLineShapeStyle.Interpreted.Default(this, (QuickLinearShape.Interpreted<?>) parentEl,
						(QuickInstanceStyle.Interpreted) parent, getWrapped().interpret(parentEl, parent));
				}
			}
		}

		public static interface Interpreted extends QuickWithStroke.QuickStrokeStyle.Interpreted {
			QuickElementStyleAttribute<Double> getThickness();

			public static class Default extends QuickWithStroke.QuickStrokeStyle.Interpreted.Default implements Interpreted {
				private QuickElementStyleAttribute<Double> theThickness;

				protected Default(QuickLineShapeStyle.Def definition, QuickLinearShape.Interpreted<?> styledElement,
					QuickStyled.QuickInstanceStyle.Interpreted parent, QuickInterpretedStyle wrapped) {
					super(definition, styledElement, parent, wrapped);
				}

				@Override
				public QuickLineShapeStyle.Def getDefinition() {
					return (QuickLineShapeStyle.Def) super.getDefinition();
				}

				@Override
				public QuickElementStyleAttribute<Double> getThickness() {
					return theThickness;
				}

				@Override
				public void update(ExElement.Interpreted<?> element, QuickStyleSheet.Interpreted styleSheet)
					throws ExpressoInterpretationException {
					super.update(element, styleSheet);
					InterpretedExpressoEnv env = element.getDefaultEnv();
					QuickInterpretedStyleCache cache = QuickInterpretedStyleCache.get(env);
					theThickness = get(cache.getAttribute(getDefinition().getThickness(), Double.class, env));
				}

				@Override
				public QuickLineShapeStyle create(QuickStyled styled) {
					return new QuickLineShapeStyle.Default();
				}
			}
		}

		public ObservableValue<Double> getThickness();

		public static class Default extends QuickWithStroke.QuickStrokeStyle.Default implements QuickLineShapeStyle {
			private QuickStyleAttribute<Double> theThicknessAttr;
			private ObservableValue<Double> theThickness;

			protected Default() {
				super();
			}

			@Override
			public ObservableValue<Double> getThickness() {
				return theThickness;
			}

			@Override
			public void update(QuickInstanceStyle.Interpreted interpreted, QuickStyled styled) throws ModelInstantiationException {
				super.update(interpreted, styled);

				QuickLineShapeStyle.Interpreted myInterpreted = (QuickLineShapeStyle.Interpreted) interpreted;

				theThicknessAttr = myInterpreted.getThickness().getAttribute();
				theThickness = getApplicableAttribute(theThicknessAttr);
			}

			@Override
			public QuickLineShapeStyle.Default copy(QuickStyled styled) {
				QuickLineShapeStyle.Default copy = (QuickLineShapeStyle.Default) super.copy(styled);

				copy.theThickness = copy.getApplicableAttribute(theThicknessAttr);

				return copy;
			}
		}
	}

}
