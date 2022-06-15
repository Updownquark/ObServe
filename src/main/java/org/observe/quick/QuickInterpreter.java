package org.observe.quick;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.observe.ObservableValue;
import org.observe.SettableValue;
import org.observe.expresso.ExpressoEnv;
import org.observe.expresso.ExpressoInterpreter;
import org.observe.expresso.ModelType.ModelInstanceType;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableModelSet;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.ObservableModelSet.ValueContainer;
import org.observe.quick.QuickCore.StyleValues;
import org.observe.quick.style.FontValueParser;
import org.observe.quick.style.QuickElementStyle;
import org.observe.quick.style.QuickModelValue;
import org.observe.quick.style.QuickModelValue.Satisfier;
import org.observe.quick.style.QuickStyleAttribute;
import org.observe.quick.style.QuickStyleSet;
import org.observe.quick.style.QuickStyleSheet;
import org.observe.quick.style.QuickStyleValue;
import org.observe.util.TypeTokens;
import org.qommons.ClassMap;
import org.qommons.StatusReportAccumulator;
import org.qommons.config.QonfigElement;
import org.qommons.config.QonfigElementDef;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretationException;
import org.qommons.config.QonfigToolkit;

public abstract class QuickInterpreter<QIS extends QuickInterpreter.QuickSession<?>> extends ExpressoInterpreter<QIS> {
	public static final String STYLE_ELEMENT = "quick-style-element";
	public static final QonfigElementDef WIDGET = QuickCore.CORE.get().getElement("widget");

	public static class QuickSession<QIS extends QuickSession<QIS>> extends ExpressoSession<QIS> {
		private final boolean isStyled;
		private final boolean isWidget;
		private ObservableModelSet.Wrapped theLocalModels;
		private QuickStyleSheet theStyleSheet;
		private QuickElementStyle theStyle;
		private final Set<QuickModelValue<?>> theModelValues;
		private final Map<QuickModelValue<?>, WidgetModelValueImpl<?>> theModelValueImpls;

		protected QuickSession(QIS parent, QonfigElement element, QonfigElementOrAddOn type, int childIndex)
			throws QonfigInterpretationException {
			super(parent, element, type, childIndex);
			theStyleSheet = ((QuickSession<?>) parent).theStyleSheet;
			if (parent.getElement() == getElement()) {
				QuickSession<?> qp = parent;
				isStyled = qp.isStyled;
				isWidget = qp.isWidget;
				theModelValues = qp.theModelValues;
				theModelValueImpls = qp.theModelValueImpls;
				theLocalModels = qp.theLocalModels;
			} else {
				isStyled = element.isInstance(QuickStyleSet.STYLED);
				if (isStyled) {
					isWidget = element.isInstance(WIDGET);
					theModelValues = new HashSet<>();
					theModelValueImpls = new HashMap<>();
					initStyleData();
				} else {
					isWidget = false;
					theModelValues = Collections.emptySet();
					theModelValueImpls = Collections.emptyMap();
				}
			}
		}

		protected QuickSession(QuickInterpreter<QIS> interpreter, QonfigElement root, ExpressoEnv env)
			throws QonfigInterpretationException {
			super(interpreter, root, env);
			isStyled = root.isInstance(QuickStyleSet.STYLED);
			isWidget = root.isInstance(WIDGET);
			theModelValues = new HashSet<>();
			theModelValueImpls = new HashMap<>();
			initStyleData();
		}

		private void initStyleData() throws QonfigInterpretationException {
			if (!isStyled)
				return;
			else if (getParent() != null && getElement() == getParent().getElement()) {
				theLocalModels = getParent().getLocalModels();
				theStyle = getParent().getStyle();
				return;
			}
			ObservableModelSet.WrappedBuilder builder = null;
			Collection<QuickModelValue<?>> modelValues = getInterpreter().getStyleSet().getModelValues(getElement(), this);
			if (!modelValues.isEmpty()) {
				builder = ObservableModelSet.wrap(getExpressoEnv().getModels());
				builder.withCustomValue(QuickModelValue.SATISFIER_PLACEHOLDER,
					new ValueContainer<SettableValue, SettableValue<QuickModelValue.Satisfier>>() {
						@Override
						public ModelInstanceType<SettableValue, SettableValue<Satisfier>> getType() {
							return ModelTypes.Value.forType(QuickModelValue.Satisfier.class);
						}

						@Override
						public SettableValue<Satisfier> get(ModelSetInstance models) {
							return SettableValue.of(QuickModelValue.Satisfier.class, new QuickModelValue.Satisfier() {
								@Override
								public <T> ObservableValue<T> satisfy(QuickModelValue<T> value) {
									WidgetModelValueImpl<T> impl = (WidgetModelValueImpl<T>) theModelValueImpls.get(value);
									if (impl == null) {
										System.err.println("Model value '" + value + "' not implemented for " + QuickSession.this);
										if (TypeTokens.get().unwrap(value.getValueType()).isPrimitive())
											return SettableValue.of(value.getValueType(),
												TypeTokens.get().getPrimitiveDefault(value.getValueType()), "Not reversible");
										else
											return SettableValue.of(value.getValueType(), null, "Not reversible");
								}
									return impl.createModelValue();
								}
							}, "Not settable");
						}
					});
				for (QuickModelValue<?> mv : modelValues) {
					builder.withCustomValue(mv.getName(), mv);
					theModelValues.add(mv);
				}
			}
			if (isWidget) {
				QIS modelSession = forChildren("model").peekFirst();
				if (modelSession != null) {
					if (builder == null)
						builder = ObservableModelSet.wrap(modelSession.getExpressoEnv().getModels());
					modelSession.put("local-variables", true);
					modelSession.setModels(builder, null);
					modelSession.interpret(ObservableModelSet.class);
				}
			}
			if (builder != null) {
				ObservableModelSet.Wrapped built = builder.build();
				setModels(built, null);
				theLocalModels = built;
			} else
				theLocalModels = null;

			// Parse style values, if any
			put(STYLE_ELEMENT, getElement());
			List<QuickStyleValue<?>> declared = null;
			for (StyleValues sv : interpretChildren("style", StyleValues.class)) {
				sv.init();
				if (declared == null)
					declared = new ArrayList<>();
				declared.addAll(sv);
			}
			if (declared == null)
				declared = Collections.emptyList();
			Collections.sort(declared);

			// Find parent
			QuickSession<?> parentSession = getParent();
			while (parentSession != null && parentSession.getStyle() == null)
				parentSession = parentSession.getParent();
			QuickElementStyle parent = parentSession == null ? null : parentSession.getStyle();
			// Create QuickElementStyle and put into session
			theStyle = new QuickElementStyle(getInterpreter().getStyleSet(), Collections.unmodifiableList(declared),
				parent, theStyleSheet, getElement(), this);
		}

		@Override
		public QuickInterpreter<QIS> getInterpreter() {
			return (QuickInterpreter<QIS>) super.getInterpreter();
		}

		public QuickStyleSheet getStyleSheet() {
			return theStyleSheet;
		}

		public QIS setStyleSheet(QuickStyleSheet styleSheet) {
			theStyleSheet = styleSheet;
			return (QIS) this;
		}

		public ObservableModelSet.Wrapped getLocalModels() {
			return theLocalModels;
		}

		public QuickElementStyle getStyle() {
			return theStyle;
		}

		public <T> QuickStyleAttribute<? extends T> getStyleAttribute(String element, String name, Class<T> type)
			throws QonfigInterpretationException {
			if (element != null) {
				QonfigElementOrAddOn el = getElement().getDocument().getDocToolkit().getElementOrAddOn(element);
				if (el == null)
					throw new QonfigInterpretationException("No such element or add-on '" + element + "'");
				else if (!getElement().isInstance(el))
					throw new QonfigInterpretationException("This element is not an instance of  '" + element + "'");
				return getInterpreter().getStyleSet().styled(el, this).getAttribute(name, type);
			}
			return getInterpreter().getStyleSet().styled(getType(), this).getAttribute(name, type);
		}

		public <T> QuickModelValue<T> getStyleModelValue(String element, String name, Class<T> type)
			throws QonfigInterpretationException {
			if (element != null) {
				QonfigElementOrAddOn el = getElement().getDocument().getDocToolkit().getElementOrAddOn(element);
				if (el == null)
					throw new QonfigInterpretationException("No such element or add-on '" + element + "'");
				else if (!getElement().isInstance(el))
					throw new QonfigInterpretationException("This element is not an instance of  '" + element + "'");
				return getInterpreter().getStyleSet().styled(el, this).getModelValue(name, type);
			}
			return getInterpreter().getStyleSet().styled(getType(), this).getModelValue(name, type);
		}

		public <T> QIS support(QuickModelValue<T> modelValue, WidgetModelValueImpl<T> value) {
			if (!theModelValues.contains(modelValue))
				throw new IllegalArgumentException("Style model value " + modelValue + " does not apply to this element");
			theModelValueImpls.put(modelValue, value);
			return (QIS) this;
		}
	}

	private final QuickStyleSet theStyleSet;

	protected QuickInterpreter(Class<?> callingClass, Map<QonfigElementOrAddOn, ClassMap<QonfigCreatorHolder<QIS, ?>>> creators,
		Map<QonfigElementOrAddOn, ClassMap<QonfigModifierHolder<QIS, ?>>> modifiers, ExpressoEnv expressoEnv,
		QuickStyleSet styleSet) {
		super(callingClass, creators, modifiers, expressoEnv);
		theStyleSet = styleSet;
	}

	public QuickStyleSet getStyleSet() {
		return theStyleSet;
	}

	public static Builder<?, ?> build(Class<?> callingClass, QonfigToolkit... toolkits) {
		return new DefaultBuilder(callingClass, null, null, toolkits);
	}

	public static abstract class Builder<QIS extends QuickSession<?>, B extends Builder<QIS, B>>
	extends ExpressoInterpreter.Builder<QIS, B> {
		private QuickStyleSet theStyleSet;

		protected Builder(Class<?> callingClass, ExpressoEnv expressoEnv, QuickStyleSet styleSet, QonfigToolkit... toolkits) {
			super(callingClass, expressoEnv, toolkits);
			theStyleSet = styleSet;
		}

		protected Builder(Class<?> callingClass, Set<QonfigToolkit> toolkits, QonfigToolkit toolkit,
			StatusReportAccumulator<QonfigElementOrAddOn> status, Map<QonfigElementOrAddOn, ClassMap<QonfigCreatorHolder<QIS, ?>>> creators,
			Map<QonfigElementOrAddOn, ClassMap<QonfigModifierHolder<QIS, ?>>> modifiers, ExpressoEnv expressoEnv,
			QuickStyleSet styleSet) {
			super(callingClass, toolkits, toolkit, status, creators, modifiers, expressoEnv);
			theStyleSet = styleSet;
		}

		public QuickStyleSet getStyleSet() {
			return theStyleSet;
		}

		protected QuickStyleSet getOrCreateStyleSet() {
			return theStyleSet == null ? new QuickStyleSet() : null;
		}

		public B withStyleSet(QuickStyleSet styleSet) {
			theStyleSet = styleSet;
			return (B) this;
		}

		@Override
		protected B builderFor(Class<?> callingClass, Set<QonfigToolkit> toolkits, QonfigToolkit toolkit,
			StatusReportAccumulator<QonfigElementOrAddOn> status, Map<QonfigElementOrAddOn, ClassMap<QonfigCreatorHolder<QIS, ?>>> creators,
			Map<QonfigElementOrAddOn, ClassMap<QonfigModifierHolder<QIS, ?>>> modifiers, ExpressoEnv expressoEnv) {
			return builderFor(callingClass, toolkits, toolkit, status, creators, modifiers, expressoEnv, theStyleSet);
		}

		protected abstract B builderFor(Class<?> callingClass, Set<QonfigToolkit> toolkits, QonfigToolkit toolkit,
			StatusReportAccumulator<QonfigElementOrAddOn> status, Map<QonfigElementOrAddOn, ClassMap<QonfigCreatorHolder<QIS, ?>>> creators,
			Map<QonfigElementOrAddOn, ClassMap<QonfigModifierHolder<QIS, ?>>> modifiers, ExpressoEnv expressoEnv,
			QuickStyleSet styleSet);

		@Override
		public abstract QuickInterpreter<QIS> create();

		@Override
		public QuickInterpreter<QIS> build() {
			return (QuickInterpreter<QIS>) super.build();
		}
	}

	public static class QuickSessionDefault extends QuickSession<QuickSessionDefault> {
		QuickSessionDefault(QuickInterpreter<QuickSessionDefault> interpreter, QonfigElement root, ExpressoEnv expressoEnv)
			throws QonfigInterpretationException {
			super(interpreter, root, expressoEnv);
		}

		QuickSessionDefault(QuickSessionDefault parent, QonfigElement element, QonfigElementOrAddOn type, int childIndex)
			throws QonfigInterpretationException {
			super(parent, element, type, childIndex);
		}
	}

	public static class Default extends QuickInterpreter<QuickSessionDefault> {
		Default(Class<?> callingClass, Map<QonfigElementOrAddOn, ClassMap<QonfigCreatorHolder<QuickSessionDefault, ?>>> creators,
			Map<QonfigElementOrAddOn, ClassMap<QonfigModifierHolder<QuickSessionDefault, ?>>> modifiers, ExpressoEnv expressoEnv,
			QuickStyleSet styleSet) {
			super(callingClass, creators, modifiers, expressoEnv, styleSet);
		}

		@Override
		public QuickSessionDefault interpret(QonfigElement element) throws QonfigInterpretationException {
			return new QuickSessionDefault(this, element, getExpressoEnv());
		}

		@Override
		protected QuickSessionDefault interpret(QuickSessionDefault parent, QonfigElement element, QonfigElementOrAddOn type,
			int childIndex) throws QonfigInterpretationException {
			return new QuickSessionDefault(parent, element, type, childIndex);
		}
	}

	public static class DefaultBuilder extends Builder<QuickSessionDefault, DefaultBuilder> {
		DefaultBuilder(Class<?> callingClass, ExpressoEnv expressoEnv, QuickStyleSet styleSet, QonfigToolkit... toolkits) {
			super(callingClass, expressoEnv, styleSet, toolkits);
		}

		DefaultBuilder(Class<?> callingClass, Set<QonfigToolkit> toolkits, QonfigToolkit toolkit,
			StatusReportAccumulator<QonfigElementOrAddOn> status,
			Map<QonfigElementOrAddOn, ClassMap<QonfigCreatorHolder<QuickSessionDefault, ?>>> creators,
			Map<QonfigElementOrAddOn, ClassMap<QonfigModifierHolder<QuickSessionDefault, ?>>> modifiers, ExpressoEnv expressoEnv,
			QuickStyleSet styleSet) {
			super(callingClass, toolkits, toolkit, status, creators, modifiers, expressoEnv, styleSet);
		}

		@Override
		protected ExpressoEnv createExpressoEnv() {
			return super.createExpressoEnv().withNonStructuredParser(double.class, new FontValueParser());
		}

		@Override
		protected DefaultBuilder builderFor(Class<?> callingClass, Set<QonfigToolkit> toolkits, QonfigToolkit toolkit,
			StatusReportAccumulator<QonfigElementOrAddOn> status,
			Map<QonfigElementOrAddOn, ClassMap<QonfigCreatorHolder<QuickSessionDefault, ?>>> creators,
			Map<QonfigElementOrAddOn, ClassMap<QonfigModifierHolder<QuickSessionDefault, ?>>> modifiers, ExpressoEnv expressoEnv,
			QuickStyleSet styleSet) {
			return new DefaultBuilder(callingClass, toolkits, toolkit, status, creators, modifiers, expressoEnv, styleSet);
		}

		@Override
		public QuickInterpreter<QuickSessionDefault> create() {
			return new Default(getCallingClass(), getCreators(), getModifiers(), getOrCreateExpressoEnv(), getOrCreateStyleSet());
		}
	}

	public interface WidgetModelValueImpl<T> {
		ObservableValue<T> createModelValue();
	}
}
