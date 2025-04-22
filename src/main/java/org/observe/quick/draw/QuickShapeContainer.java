package org.observe.quick.draw;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.observe.ObservableValue;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.InterpretedExpressoEnv;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.qonfig.ExElement;
import org.observe.expresso.qonfig.ExElementTraceable;
import org.observe.expresso.qonfig.ExpressoQIS;
import org.observe.quick.draw.QuickShape.QuickShapeStyle;
import org.observe.quick.style.QuickCompiledStyle;
import org.observe.quick.style.QuickInterpretedStyle;
import org.observe.quick.style.QuickInterpretedStyleCache;
import org.observe.quick.style.QuickInterpretedStyleCache.Applications;
import org.observe.quick.style.QuickStyleAttribute;
import org.observe.quick.style.QuickStyleAttributeDef;
import org.observe.quick.style.QuickStyleSheet;
import org.observe.quick.style.QuickStyled;
import org.observe.quick.style.QuickStyled.QuickInstanceStyle;
import org.observe.quick.style.QuickStyledElement;
import org.observe.quick.style.QuickTypeStyle;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretationException;

public interface QuickShapeContainer extends QuickStyledElement {
	public static final String SHAPE_CONTAINER = "shape-container";

	@ExElementTraceable(toolkit = QuickDrawInterpretation.DRAW,
		qonfigType = SHAPE_CONTAINER,
		interpretation = Interpreted.class,
		instance = QuickShapeContainer.class)
	public interface Def<E extends QuickShapeContainer> extends QuickStyledElement.Def<E> {
		List<ExElement.Def<?>> getShapes();

		@Override
		QuickShapeContainerStyle.Def getStyle();

		public static abstract class Abstract<E extends QuickShapeContainer> extends QuickStyledElement.Def.Abstract<E> implements Def<E> {
			private final List<ExElement.Def<?>> theShapes;

			public Abstract(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
				super(parent, qonfigType);
				theShapes = new ArrayList<>();
			}

			@Override
			public QuickShapeContainerStyle.Def wrap(QuickInstanceStyle.Def parentStyle, QuickCompiledStyle style) {
				return new QuickShapeContainerStyle.Def.Default(parentStyle, this, style);
			}

			@Override
			public List<ExElement.Def<?>> getShapes() {
				return Collections.unmodifiableList(theShapes);
			}

			@Override
			public QuickShapeContainerStyle.Def getStyle() {
				return (QuickShapeContainerStyle.Def) super.getStyle();
			}

			@Override
			protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
				super.doUpdate(session);

				syncChildren(ExElement.Def.class, theShapes, session.forChildren("shape"));
			}
		}
	}

	public interface Interpreted<E extends QuickShapeContainer> extends QuickStyledElement.Interpreted<E> {
		List<ExElement.Interpreted<?>> getShapes();

		@Override
		QuickShapeContainerStyle.Interpreted getStyle();
	}

	List<ExElement> getShapes();

	@Override
	QuickShapeContainerStyle getStyle();

	public interface QuickShapeContainerStyle extends QuickInstanceStyle {
		public static interface Def extends QuickInstanceStyle.Def {
			QuickStyleAttributeDef getContentOpacity();

			public static class Default extends QuickInstanceStyle.Def.Abstract implements Def {
				private final QuickStyleAttributeDef theContentOpacity;

				public Default(QuickStyled.QuickInstanceStyle.Def parent, QuickShapeContainer.Def<?> styledElement,
					QuickCompiledStyle wrapped) {
					super(parent, styledElement.getAddOn(QuickStyled.Def.class), wrapped);
					QuickTypeStyle typeStyle = QuickStyled.getTypeStyle(wrapped.getStyleTypes(), getElement(), QuickDrawInterpretation.NAME,
						QuickDrawInterpretation.VERSION, "shape-container");
					theContentOpacity = addApplicableAttribute(typeStyle.getAttribute("content-opacity"));
				}

				@Override
				public QuickStyleAttributeDef getContentOpacity() {
					return theContentOpacity;
				}

				@Override
				public QuickShapeContainerStyle.Interpreted interpret(ExElement.Interpreted<?> parentEl, QuickInterpretedStyle parent,
					InterpretedExpressoEnv env) throws ExpressoInterpretationException {
					return new QuickShapeContainerStyle.Interpreted.Default(this, (QuickShapeContainer.Interpreted<?>) parentEl,
						(QuickInstanceStyle.Interpreted) parent, getWrapped().interpret(parentEl, parent, env));
				}
			}
		}

		public static interface Interpreted extends QuickInstanceStyle.Interpreted {
			QuickElementStyleAttribute<Float> getContentOpacity();

			public static class Default extends QuickInstanceStyle.Interpreted.Abstract implements Interpreted {
				private QuickElementStyleAttribute<Float> theContentOpacity;

				public Default(QuickShapeContainerStyle.Def definition, QuickShapeContainer.Interpreted<?> styledElement,
					QuickStyled.QuickInstanceStyle.Interpreted parent, QuickInterpretedStyle wrapped) {
					super(definition, styledElement.getAddOn(QuickStyled.Interpreted.class), parent, wrapped);
				}

				@Override
				public QuickShapeContainerStyle.Def getDefinition() {
					return (QuickShapeContainerStyle.Def) super.getDefinition();
				}

				@Override
				public QuickElementStyleAttribute<Float> getContentOpacity() {
					return theContentOpacity;
				}

				@Override
				public void update(InterpretedExpressoEnv env, QuickStyleSheet.Interpreted styleSheet, Applications appCache)
					throws ExpressoInterpretationException {
					super.update(env, styleSheet, appCache);
					QuickInterpretedStyleCache cache = QuickInterpretedStyleCache.get(env);
					theContentOpacity = get(cache.getAttribute(getDefinition().getContentOpacity(), float.class, env));
				}

				@Override
				public QuickShapeStyle create(QuickStyled styled) {
					return new QuickShapeStyle.Default();
				}
			}
		}

		public ObservableValue<Float> getContentOpacity();

		public static class Default extends QuickInstanceStyle.Abstract implements QuickShapeContainerStyle {
			private QuickStyleAttribute<Float> theContentOpacityAttr;
			private ObservableValue<Float> theContentOpacity;

			@Override
			public ObservableValue<Float> getContentOpacity() {
				return theContentOpacity;
			}

			@Override
			public void update(QuickInstanceStyle.Interpreted interpreted, QuickStyled styled) throws ModelInstantiationException {
				super.update(interpreted, styled);

				QuickShapeStyle.Interpreted myInterpreted = (QuickShapeStyle.Interpreted) interpreted;

				theContentOpacityAttr = myInterpreted.getOpacity().getAttribute();

				theContentOpacity = getApplicableAttribute(theContentOpacityAttr);
			}

			@Override
			public QuickShapeContainerStyle.Default copy(QuickStyled styled) {
				QuickShapeContainerStyle.Default copy = (QuickShapeContainerStyle.Default) super.copy(styled);

				copy.theContentOpacity = copy.getApplicableAttribute(theContentOpacityAttr);

				return copy;
			}
		}
	}
}
