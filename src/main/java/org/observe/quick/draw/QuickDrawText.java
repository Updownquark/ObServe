package org.observe.quick.draw;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.observe.ObservableValue;
import org.observe.SettableValue;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.InterpretedExpressoEnv;
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
import org.observe.expresso.qonfig.QonfigChildGetter;
import org.observe.quick.QuickCoreInterpretation;
import org.observe.quick.QuickTextElement;
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
import org.qommons.config.QonfigInterpretationException;

public class QuickDrawText extends QuickShape.Abstract implements QuickTextElement {
	public static final String TEXT = "text";

	@ExElementTraceable(toolkit = QuickDrawInterpretation.DRAW,
		qonfigType = TEXT,
		interpretation = Interpreted.class,
		instance = QuickDrawText.class)
	public static class Def extends QuickShape.Def.Abstract<QuickDrawText> implements QuickTextElement.Def<QuickDrawText> {
		private CompiledExpression theValue;
		private final List<Def> theSubTexts;

		public Def(ExElement.Def<?> parent, QonfigElementOrAddOn type) {
			super(parent, type);
			theSubTexts = new ArrayList<>();
		}

		@QonfigAttributeGetter
		public CompiledExpression getValue() {
			return theValue;
		}

		@QonfigChildGetter("sub-text")
		public List<Def> getSubTexts() {
			return Collections.unmodifiableList(theSubTexts);
		}

		@Override
		public QuickDrawTextStyle.Def getStyle() {
			return (QuickDrawTextStyle.Def) super.getStyle();
		}

		@Override
		public QuickDrawTextStyle.Def wrap(QuickStyled.QuickInstanceStyle.Def parentStyle, QuickCompiledStyle style) {
			return new QuickDrawTextStyle.Def.Default(parentStyle, this, style);
		}

		@Override
		protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
			super.doUpdate(session);

			theValue = getValueExpression(session);
			syncChildren(Def.class, theSubTexts, session.forChildren("sub-text"));
		}

		@Override
		public Interpreted interpret(ExElement.Interpreted<?> parent) {
			return new Interpreted(this, parent);
		}
	}

	public static class Interpreted extends QuickShape.Interpreted.Abstract<QuickDrawText>
	implements QuickTextElement.Interpreted<QuickDrawText> {
		private InterpretedValueSynth<SettableValue<?>, SettableValue<String>> theValue;
		private final List<Interpreted> theSubTexts;

		Interpreted(Def definition, ExElement.Interpreted<?> parent) {
			super(definition, parent);
			theSubTexts = new ArrayList<>();
		}

		@Override
		public Def getDefinition() {
			return (Def) super.getDefinition();
		}

		public InterpretedValueSynth<SettableValue<?>, SettableValue<String>> getValue() {
			return theValue;
		}

		public List<Interpreted> getSubTexts() {
			return Collections.unmodifiableList(theSubTexts);
		}

		@Override
		public QuickDrawTextStyle.Interpreted getStyle() {
			return (QuickDrawTextStyle.Interpreted) super.getStyle();
		}

		@Override
		protected void doUpdate() throws ExpressoInterpretationException {
			super.doUpdate();

			theValue = interpret(getDefinition().getValue(), ModelTypes.Value.STRING);
			syncChildren(getDefinition().getSubTexts(), theSubTexts, def -> def.interpret(this), Interpreted::updateElement);
		}

		@Override
		public QuickDrawText create() {
			return new QuickDrawText(getIdentity());
		}
	}

	private ModelValueInstantiator<SettableValue<String>> theValueInstantiator;
	private List<QuickDrawText> theSubTexts;

	private SettableValue<SettableValue<String>> theValue;

	QuickDrawText(Object id) {
		super(id);
		theSubTexts = new ArrayList<>();
		theValue = SettableValue.create();
	}

	public SettableValue<String> getValue() {
		return SettableValue.flatten(theValue);
	}

	public List<QuickDrawText> getSubTexts() {
		return Collections.unmodifiableList(theSubTexts);
	}

	@Override
	public QuickDrawTextStyle getStyle() {
		return (QuickDrawTextStyle) super.getStyle();
	}

	@Override
	protected void doUpdate(ExElement.Interpreted<?> interpreted) throws ModelInstantiationException {
		super.doUpdate(interpreted);

		Interpreted myInterpreted = (Interpreted) interpreted;
		theValueInstantiator = ExElement.instantiate(myInterpreted.getValue());
		syncChildren(myInterpreted.getSubTexts(), theSubTexts, Interpreted::create, QuickDrawText::update);
	}

	@Override
	public void instantiated() throws ModelInstantiationException {
		super.instantiated();
		if (theValueInstantiator != null)
			theValueInstantiator.instantiate();
		for (QuickDrawText subText : theSubTexts)
			subText.instantiated();
	}

	@Override
	protected ModelSetInstance doInstantiate(ModelSetInstance myModels) throws ModelInstantiationException {
		myModels = super.doInstantiate(myModels);

		theValue.set(ExElement.get(theValueInstantiator, myModels));
		for (QuickDrawText subText : theSubTexts)
			subText.instantiate(myModels);

		return myModels;
	}

	@Override
	public QuickDrawText copy(ExElement parent) {
		QuickDrawText copy = (QuickDrawText) super.copy(parent);

		copy.theValue = SettableValue.create();
		copy.theSubTexts = new ArrayList<>();
		for (QuickDrawText subText : theSubTexts)
			copy.theSubTexts.add(subText.copy(this));

		return copy;
	}

	public interface QuickDrawTextStyle extends QuickShape.QuickShapeStyle, QuickTextElement.QuickTextStyle {
		public interface Def extends QuickShape.QuickShapeStyle.Def, QuickTextElement.QuickTextStyle.Def {
			public static class Default extends QuickShape.QuickShapeStyle.Def.Default implements Def {
				private final QuickStyleAttributeDef theFontColor;
				private final QuickStyleAttributeDef theFontSize;
				private final QuickStyleAttributeDef theFontWeight;
				private final QuickStyleAttributeDef theFontSlant;
				private final QuickStyleAttributeDef isUnderline;
				private final QuickStyleAttributeDef isStrikeThrough;
				private final QuickStyleAttributeDef isSuperScript;
				private final QuickStyleAttributeDef isSubScript;

				public Default(QuickStyled.QuickInstanceStyle.Def parent, ExElement.Def styledElement, QuickCompiledStyle wrapped) {
					super(parent, styledElement, wrapped);
					QuickTypeStyle typeStyle = QuickStyled.getTypeStyle(wrapped.getStyleTypes(), wrapped.getElement(),
						QuickDrawInterpretation.NAME, QuickCoreInterpretation.VERSION, TEXT);
					theFontColor = addApplicableAttribute(typeStyle.getAttribute("font-color"));
					theFontSize = addApplicableAttribute(typeStyle.getAttribute("font-size"));
					theFontWeight = addApplicableAttribute(typeStyle.getAttribute("font-weight"));
					theFontSlant = addApplicableAttribute(typeStyle.getAttribute("font-slant"));
					isUnderline = addApplicableAttribute(typeStyle.getAttribute("underline"));
					isStrikeThrough = addApplicableAttribute(typeStyle.getAttribute("strike-through"));
					isSuperScript = addApplicableAttribute(typeStyle.getAttribute("super-script"));
					isSubScript = addApplicableAttribute(typeStyle.getAttribute("sub-script"));
				}

				@Override
				public QuickStyleAttributeDef getFontColor() {
					return theFontColor;
				}

				@Override
				public QuickStyleAttributeDef getFontSize() {
					return theFontSize;
				}

				@Override
				public QuickStyleAttributeDef getFontWeight() {
					return theFontWeight;
				}

				@Override
				public QuickStyleAttributeDef getFontSlant() {
					return theFontSlant;
				}

				@Override
				public QuickStyleAttributeDef isUnderline() {
					return isUnderline;
				}

				@Override
				public QuickStyleAttributeDef isStrikeThrough() {
					return isStrikeThrough;
				}

				@Override
				public QuickStyleAttributeDef isSuperScript() {
					return isSuperScript;
				}

				@Override
				public QuickStyleAttributeDef isSubScript() {
					return isSubScript;
				}

				@Override
				public Interpreted interpret(ExElement.Interpreted<?> parentEl, QuickInterpretedStyle parent)
					throws ExpressoInterpretationException {
					return new Interpreted.Default(this, parentEl, (QuickInstanceStyle.Interpreted) parent,
						getWrapped().interpret(parentEl, parent));
				}
			}
		}

		public interface Interpreted extends QuickShape.QuickShapeStyle.Interpreted, QuickTextElement.QuickTextStyle.Interpreted {
			@Override
			QuickDrawTextStyle create(QuickStyled styled);

			public static class Default extends QuickShape.QuickShapeStyle.Interpreted.Default implements Interpreted {
				private QuickElementStyleAttribute<Color> theFontColor;
				private QuickElementStyleAttribute<Double> theFontSize;
				private QuickElementStyleAttribute<Double> theFontWeight;
				private QuickElementStyleAttribute<Double> theFontSlant;
				private QuickElementStyleAttribute<Boolean> isUnderline;
				private QuickElementStyleAttribute<Boolean> isStrikeThrough;
				private QuickElementStyleAttribute<Boolean> isSuperScript;
				private QuickElementStyleAttribute<Boolean> isSubScript;

				Default(QuickShape.QuickShapeStyle.Def definition, ExElement.Interpreted<?> styledElement,
					QuickStyled.QuickInstanceStyle.Interpreted parent, QuickInterpretedStyle wrapped) {
					super(definition, styledElement, parent, wrapped);
				}

				@Override
				public QuickDrawTextStyle.Def getDefinition() {
					return (QuickDrawTextStyle.Def) super.getDefinition();
				}

				@Override
				public QuickElementStyleAttribute<Color> getFontColor() {
					return theFontColor;
				}

				@Override
				public QuickElementStyleAttribute<Double> getFontSize() {
					return theFontSize;
				}

				@Override
				public QuickElementStyleAttribute<Double> getFontWeight() {
					return theFontWeight;
				}

				@Override
				public QuickElementStyleAttribute<Double> getFontSlant() {
					return theFontSlant;
				}

				@Override
				public QuickElementStyleAttribute<Boolean> isUnderline() {
					return isUnderline;
				}

				@Override
				public QuickElementStyleAttribute<Boolean> isStrikeThrough() {
					return isStrikeThrough;
				}

				@Override
				public QuickElementStyleAttribute<Boolean> isSuperScript() {
					return isSuperScript;
				}

				@Override
				public QuickElementStyleAttribute<Boolean> isSubScript() {
					return isSubScript;
				}

				@Override
				public void update(org.observe.expresso.qonfig.ExElement.Interpreted<?> element, QuickStyleSheet.Interpreted styleSheet)
					throws ExpressoInterpretationException {
					super.update(element, styleSheet);
					InterpretedExpressoEnv env = element.getDefaultEnv();
					QuickInterpretedStyleCache cache = QuickInterpretedStyleCache.get(env);
					theFontColor = get(cache.getAttribute(getDefinition().getFontColor(), Color.class, env));
					theFontSize = get(cache.getAttribute(getDefinition().getFontSize(), Double.class, env));
					theFontWeight = get(cache.getAttribute(getDefinition().getFontWeight(), Double.class, env));
					theFontSlant = get(cache.getAttribute(getDefinition().getFontSlant(), Double.class, env));
					isUnderline = get(cache.getAttribute(getDefinition().isUnderline(), Boolean.class, env));
					isStrikeThrough = get(cache.getAttribute(getDefinition().isStrikeThrough(), Boolean.class, env));
					isSuperScript = get(cache.getAttribute(getDefinition().isSuperScript(), Boolean.class, env));
					isSubScript = get(cache.getAttribute(getDefinition().isSubScript(), Boolean.class, env));
				}

				@Override
				public QuickDrawTextStyle create(QuickStyled styled) {
					return new QuickDrawTextStyle.Default();
				}
			}
		}

		public static class Default extends QuickShape.QuickShapeStyle.Default implements QuickDrawTextStyle {
			private QuickStyleAttribute<Color> theFontColorAttr;
			private ObservableValue<Color> theFontColor;
			private QuickStyleAttribute<Double> theFontSizeAttr;
			private ObservableValue<Double> theFontSize;
			private QuickStyleAttribute<Double> theFontWeightAttr;
			private ObservableValue<Double> theFontWeight;
			private QuickStyleAttribute<Double> theFontSlantAttr;
			private ObservableValue<Double> theFontSlant;
			private QuickStyleAttribute<Boolean> theUnderlineAttr;
			private ObservableValue<Boolean> isUnderline;
			private QuickStyleAttribute<Boolean> theStrikeThroughAttr;
			private ObservableValue<Boolean> isStrikeThrough;
			private QuickStyleAttribute<Boolean> theSuperScriptAttr;
			private ObservableValue<Boolean> isSuperScript;
			private QuickStyleAttribute<Boolean> theSubScriptAttr;
			private ObservableValue<Boolean> isSubScript;

			@Override
			public ObservableValue<Color> getFontColor() {
				return theFontColor;
			}

			@Override
			public ObservableValue<Double> getFontSize() {
				return theFontSize;
			}

			@Override
			public ObservableValue<Double> getFontWeight() {
				return theFontWeight;
			}

			@Override
			public ObservableValue<Double> getFontSlant() {
				return theFontSlant;
			}

			@Override
			public ObservableValue<Boolean> isUnderline() {
				return isUnderline;
			}

			@Override
			public ObservableValue<Boolean> isStrikeThrough() {
				return isStrikeThrough;
			}

			@Override
			public ObservableValue<Boolean> isSuperScript() {
				return isSuperScript;
			}

			@Override
			public ObservableValue<Boolean> isSubScript() {
				return isSubScript;
			}

			@Override
			public void update(QuickInstanceStyle.Interpreted interpreted, QuickStyled styledElement) throws ModelInstantiationException {
				super.update(interpreted, styledElement);

				QuickTextStyle.Interpreted myInterpreted = (QuickTextStyle.Interpreted) interpreted;
				theFontColorAttr = myInterpreted.getFontColor().getAttribute();
				theFontSizeAttr = myInterpreted.getFontSize().getAttribute();
				theFontWeightAttr = myInterpreted.getFontWeight().getAttribute();
				theFontSlantAttr = myInterpreted.getFontSlant().getAttribute();
				theUnderlineAttr = myInterpreted.isUnderline().getAttribute();
				theStrikeThroughAttr = myInterpreted.isStrikeThrough().getAttribute();
				theSuperScriptAttr = myInterpreted.isSuperScript().getAttribute();
				theSubScriptAttr = myInterpreted.isSubScript().getAttribute();

				theFontColor = getApplicableAttribute(theFontColorAttr);
				theFontSize = getApplicableAttribute(theFontSizeAttr);
				theFontWeight = getApplicableAttribute(theFontWeightAttr);
				theFontSlant = getApplicableAttribute(theFontSlantAttr);
				isUnderline = getApplicableAttribute(theUnderlineAttr);
				isStrikeThrough = getApplicableAttribute(theStrikeThroughAttr);
				isSuperScript = getApplicableAttribute(theSuperScriptAttr);
				isSubScript = getApplicableAttribute(theSubScriptAttr);
			}

			@Override
			public QuickDrawTextStyle.Default copy(QuickStyled styled) {
				QuickDrawTextStyle.Default copy = (QuickDrawTextStyle.Default) super.copy(styled);

				copy.theFontColor = copy.getApplicableAttribute(theFontColorAttr);
				copy.theFontSize = copy.getApplicableAttribute(theFontSizeAttr);
				copy.theFontWeight = copy.getApplicableAttribute(theFontWeightAttr);
				copy.theFontSlant = copy.getApplicableAttribute(theFontSlantAttr);
				copy.isUnderline = copy.getApplicableAttribute(theUnderlineAttr);
				copy.isStrikeThrough = copy.getApplicableAttribute(theStrikeThroughAttr);
				copy.isSuperScript = copy.getApplicableAttribute(theSuperScriptAttr);
				copy.isSubScript = copy.getApplicableAttribute(theSubScriptAttr);

				return copy;
			}
		}
	}
}
