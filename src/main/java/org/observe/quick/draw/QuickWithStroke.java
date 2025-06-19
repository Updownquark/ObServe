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

public interface QuickWithStroke extends QuickShape {
	public static final String WITH_STROKE = "with-stroke";

	@ExElementTraceable(toolkit = QuickDrawInterpretation.DRAW,
		qonfigType = WITH_STROKE,
		interpretation = Interpreted.class,
		instance = QuickWithStroke.class)
	public interface Def<E extends QuickShape> extends QuickShape.Def<E> {
		@Override
		QuickShapeStyle.Def wrap(QuickInstanceStyle.Def parentStyle, QuickCompiledStyle style);

		@Override
		QuickStrokeStyle.Def getStyle();

		@Override
		Interpreted<? extends E> interpret(ExElement.Interpreted<?> parent);

		public abstract class Abstract<E extends QuickWithStroke> extends QuickShape.Def.Abstract<E> implements QuickWithStroke.Def<E> {
			/**
			 * @param parent The parent container definition
			 * @param type The element type that this shape is interpreted from
			 */
			protected Abstract(ExElement.Def<?> parent, QonfigElementOrAddOn type) {
				super(parent, type);
			}

			@Override
			public QuickStrokeStyle.Def getStyle() {
				return (QuickStrokeStyle.Def) super.getStyle();
			}

			@Override
			public QuickStrokeStyle.Def wrap(QuickInstanceStyle.Def parentStyle, QuickCompiledStyle style) {
				return new QuickStrokeStyle.Def.Default(parentStyle, this, style);
			}
		}
	}

	public interface Interpreted<E extends QuickWithStroke> extends QuickShape.Interpreted<E> {
		@Override
		QuickWithStroke.Def<? super E> getDefinition();

		@Override
		public QuickStrokeStyle.Interpreted getStyle();

		@Override
		public abstract E create();

		public abstract class Abstract<E extends QuickWithStroke> extends QuickShape.Interpreted.Abstract<E>
		implements QuickWithStroke.Interpreted<E> {
			protected Abstract(QuickWithStroke.Def<? super E> definition, ExElement.Interpreted<?> parent) {
				super(definition, parent);
			}

			@Override
			public QuickWithStroke.Def<? super E> getDefinition() {
				return (QuickWithStroke.Def<? super E>) super.getDefinition();
			}

			@Override
			public QuickStrokeStyle.Interpreted getStyle() {
				return (QuickStrokeStyle.Interpreted) super.getStyle();
			}
		}
	}

	@Override
	public QuickStrokeStyle getStyle();

	@Override
	public QuickWithStroke copy(ExElement parent);

	public interface QuickStrokeStyle extends QuickShape.QuickShapeStyle {
		public static interface Def extends QuickShape.QuickShapeStyle.Def {
			QuickStyleAttributeDef getStrokeDash();

			public static class Default extends QuickShape.QuickShapeStyle.Def.Default implements Def {
				private final QuickStyleAttributeDef theStrokeDash;

				protected Default(QuickStyled.QuickInstanceStyle.Def parent, ExElement.Def styledElement, QuickCompiledStyle wrapped) {
					super(parent, styledElement, wrapped);
					QuickTypeStyle typeStyle = QuickStyled.getTypeStyle(wrapped.getStyleTypes(), getElement(), QuickDrawInterpretation.NAME,
						QuickDrawInterpretation.VERSION, WITH_STROKE);
					theStrokeDash = addApplicableAttribute(typeStyle.getAttribute("stroke-dash"));
				}

				@Override
				public QuickStyleAttributeDef getStrokeDash() {
					return theStrokeDash;
				}

				@Override
				public Interpreted interpret(ExElement.Interpreted<?> parentEl, QuickInterpretedStyle parent)
					throws ExpressoInterpretationException {
					return new QuickStrokeStyle.Interpreted.Default(this, parentEl, (QuickInstanceStyle.Interpreted) parent,
						getWrapped().interpret(parentEl, parent));
				}
			}
		}

		public static interface Interpreted extends QuickShape.QuickShapeStyle.Interpreted {
			QuickElementStyleAttribute<StrokeDashing> getStrokeDash();

			public static class Default extends QuickShape.QuickShapeStyle.Interpreted.Default implements Interpreted {
				private QuickElementStyleAttribute<StrokeDashing> theStrokeDash;

				protected Default(QuickStrokeStyle.Def definition, ExElement.Interpreted<?> styledElement,
					QuickStyled.QuickInstanceStyle.Interpreted parent, QuickInterpretedStyle wrapped) {
					super(definition, styledElement, parent, wrapped);
				}

				@Override
				public QuickStrokeStyle.Def getDefinition() {
					return (QuickStrokeStyle.Def) super.getDefinition();
				}

				@Override
				public QuickElementStyleAttribute<StrokeDashing> getStrokeDash() {
					return theStrokeDash;
				}

				@Override
				public void update(ExElement.Interpreted<?> element, QuickStyleSheet.Interpreted styleSheet)
					throws ExpressoInterpretationException {
					super.update(element, styleSheet);
					InterpretedExpressoEnv env = element.getDefaultEnv();
					QuickInterpretedStyleCache cache = QuickInterpretedStyleCache.get(env);
					theStrokeDash = get(cache.getAttribute(getDefinition().getStrokeDash(), StrokeDashing.class, env));
				}

				@Override
				public QuickStrokeStyle create(QuickStyled styled) {
					return new QuickStrokeStyle.Default();
				}
			}
		}

		public ObservableValue<StrokeDashing> getStrokeDash();

		public static class Default extends QuickShape.QuickShapeStyle.Default implements QuickStrokeStyle {
			private QuickStyleAttribute<StrokeDashing> theStrokeDashAttr;
			private ObservableValue<StrokeDashing> theStrokeDash;

			protected Default() {
				super();
			}

			@Override
			public ObservableValue<StrokeDashing> getStrokeDash() {
				return theStrokeDash;
			}

			@Override
			public void update(QuickInstanceStyle.Interpreted interpreted, QuickStyled styled) throws ModelInstantiationException {
				super.update(interpreted, styled);

				QuickStrokeStyle.Interpreted myInterpreted = (QuickStrokeStyle.Interpreted) interpreted;

				theStrokeDashAttr = myInterpreted.getStrokeDash().getAttribute();
				theStrokeDash = getApplicableAttribute(theStrokeDashAttr);
			}

			@Override
			public QuickStrokeStyle.Default copy(QuickStyled styled) {
				QuickStrokeStyle.Default copy = (QuickStrokeStyle.Default) super.copy(styled);

				copy.theStrokeDash = copy.getApplicableAttribute(theStrokeDashAttr);

				return copy;
			}
		}
	}

}
