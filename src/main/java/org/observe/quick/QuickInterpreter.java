package org.observe.quick;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.observe.expresso.ExpressoEnv;
import org.observe.expresso.ExpressoInterpreter;
import org.observe.expresso.ObservableModelSet;
import org.observe.quick.QuickCore.StyleValues;
import org.observe.quick.style.FontValueParser;
import org.observe.quick.style.QuickElementStyle;
import org.observe.quick.style.QuickStyleAttribute;
import org.observe.quick.style.QuickStyleSet;
import org.observe.quick.style.QuickStyleSheet;
import org.observe.quick.style.QuickStyleValue;
import org.qommons.ClassMap;
import org.qommons.StatusReportAccumulator;
import org.qommons.config.QonfigAddOn;
import org.qommons.config.QonfigElement;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretationException;
import org.qommons.config.QonfigToolkit;

public abstract class QuickInterpreter<QIS extends QuickInterpreter.QuickSession<?>> extends ExpressoInterpreter<QIS> {
	public static final String STYLE_ELEMENT = "quick-style-element";
	public static final QonfigAddOn STYLED = QuickCore.CORE.get().getAddOn("styled");

	public static class QuickSession<QIS extends QuickSession<QIS>> extends ExpressoSession<QIS> {
		private final boolean isStyled;
		private boolean checkedLocalModels;
		private ObservableModelSet.Wrapped theLocalModels;
		private QuickStyleSheet theStyleSheet;
		private QuickElementStyle theStyle;

		protected QuickSession(QIS parent, QonfigElement element, QonfigElementOrAddOn type, int childIndex) {
			super(parent, element, type, childIndex);
			isStyled = parent.getElement() == getElement() ? ((QuickSession<?>) parent).isStyled : element.isInstance(STYLED);
			theStyleSheet = ((QuickSession<?>) parent).theStyleSheet;
		}

		protected QuickSession(QuickInterpreter<QIS> interpreter, QonfigElement root, ExpressoEnv env) {
			super(interpreter, root, env);
			isStyled = root.isInstance(STYLED);
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

		public ObservableModelSet.Wrapped getLocalModels() throws QonfigInterpretationException {
			if (!isStyled || checkedLocalModels)
				return theLocalModels;
			checkedLocalModels = true;
			if (getParent() != null && getElement() == getParent().getElement())
				theLocalModels = getParent().getLocalModels();
			else {
				ExpressoSession<?> modelSession = forChildren("model").peekFirst();
				if (modelSession == null)
					theLocalModels = null;
				else {
					modelSession.put("local-variables", true);
					ObservableModelSet.WrappedBuilder builder = ObservableModelSet.wrap(modelSession.getExpressoEnv().getModels());
					modelSession.setModels(builder, null);
					modelSession.interpret(ObservableModelSet.class);
					ObservableModelSet.Wrapped built = builder.build();
					setModels(built, null);
					theLocalModels = built;
				}
			}
			return theLocalModels;
		}

		public QuickElementStyle getStyle() throws QonfigInterpretationException {
			if (!isStyled)
				return null;
			else if (theStyle != null)
				return theStyle;
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
		QuickSessionDefault(QuickInterpreter<QuickSessionDefault> interpreter, QonfigElement root, ExpressoEnv expressoEnv) {
			super(interpreter, root, expressoEnv);
		}

		QuickSessionDefault(QuickSessionDefault parent, QonfigElement element, QonfigElementOrAddOn type, int childIndex) {
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
		public QuickSessionDefault interpret(QonfigElement element) {
			return new QuickSessionDefault(this, element, getExpressoEnv());
		}

		@Override
		protected QuickSessionDefault interpret(QuickSessionDefault parent, QonfigElement element, QonfigElementOrAddOn type,
			int childIndex) {
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
}
