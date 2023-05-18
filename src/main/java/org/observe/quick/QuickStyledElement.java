package org.observe.quick;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.ExpressoQIS;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.quick.style.CompiledStyleApplication;
import org.observe.quick.style.InterpretedStyleApplication;
import org.observe.quick.style.QuickCompiledStyle;
import org.observe.quick.style.QuickInterpretedStyle;
import org.observe.quick.style.QuickTypeStyle;
import org.observe.quick.style.StyleQIS;
import org.qommons.Version;
import org.qommons.config.QonfigAddOn;
import org.qommons.config.QonfigElement;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretationException;
import org.qommons.config.QonfigToolkit;

/** A Quick element that has style */
public interface QuickStyledElement extends QuickElement {
	/**
	 * The definition of a styled element
	 *
	 * @param <S> The type of the styled element that this definition is for
	 */
	public interface Def<S extends QuickStyledElement> extends QuickElement.Def<S> {
		/** @return This element's style */
		QuickInstanceStyle.Def getStyle();

		/**
		 * An abstract {@link Def} implementation
		 *
		 * @param <S> The type of styled object that this definition is for
		 */
		public abstract class Abstract<S extends QuickStyledElement> extends QuickElement.Def.Abstract<S> implements Def<S> {
			private QuickInstanceStyle.Def theStyle;

			/**
			 * @param parent The parent container definition
			 * @param element The element that this widget is interpreted from
			 */
			protected Abstract(QuickElement.Def<?> parent, QonfigElement element) {
				super(parent, element);
			}

			@Override
			public QuickInstanceStyle.Def getStyle() {
				return theStyle;
			}

			@Override
			public Def.Abstract<S> update(ExpressoQIS session) throws QonfigInterpretationException {
				super.update(session);
				QuickElement.Def<?> parent = getParentElement();
				while (parent != null && !(parent instanceof QuickStyledElement.Def))
					parent = parent.getParentElement();
				theStyle = wrap(parent == null ? null : ((QuickStyledElement.Def<?>) parent).getStyle(),
					session.as(StyleQIS.class).getStyle());
				return this;
			}

			/**
			 * Provides the element an opportunity to wrap the standard style with one specific to this element
			 *
			 * @param style The style interpreted from the {@link #getStyle() compiled style}
			 * @return The style to use for this element
			 */
			protected abstract QuickInstanceStyle.Def wrap(QuickInstanceStyle.Def parentStyle, QuickCompiledStyle style);
		}
	}

	/** Needed to {@link QuickStyledElement.Interpreted#update(QuickInterpretationCache) update} an interpreted widget */
	class QuickInterpretationCache {
		/** A cache of interpreted style applications */
		public final Map<CompiledStyleApplication, InterpretedStyleApplication> applications = new HashMap<>();
	}

	/**
	 * An interpretation of a styled element
	 *
	 * @param <S> The type of styled element that this interpretation creates
	 */
	public interface Interpreted<S extends QuickStyledElement> extends QuickElement.Interpreted<S> {
		@Override
		Def<? super S> getDefinition();

		/** @return This element's interpreted style */
		QuickInstanceStyle.Interpreted getStyle();

		/**
		 * Populates and updates this interpretation. Must be called once after being produced by the {@link #getDefinition() definition}.
		 *
		 * @param cache The cache to use to interpret the widget
		 * @return This interpretation
		 * @throws ExpressoInterpretationException If any models could not be interpreted from their expressions in this widget or its
		 *         content
		 */
		Interpreted<S> update(QuickStyledElement.QuickInterpretationCache cache) throws ExpressoInterpretationException;

		/**
		 * An abstract {@link Interpreted} implementation
		 *
		 * @param <S> The type of widget that this interpretation is for
		 */
		public abstract class Abstract<S extends QuickStyledElement> extends QuickElement.Interpreted.Abstract<S>
		implements Interpreted<S> {
			private QuickInstanceStyle.Interpreted theStyle;

			/**
			 * @param definition The definition producing this interpretation
			 * @param parent The interpreted parent
			 */
			protected Abstract(Def<? super S> definition, QuickElement.Interpreted<?> parent) {
				super(definition, parent);
			}

			@Override
			public Def<? super S> getDefinition() {
				return (Def<? super S>) super.getDefinition();
			}

			@Override
			public QuickInstanceStyle.Interpreted getStyle() {
				return theStyle;
			}

			@Override
			public Interpreted.Abstract<S> update(QuickStyledElement.QuickInterpretationCache cache)
				throws ExpressoInterpretationException {
				super.update();
				if (theStyle == null || theStyle.getCompiled() != getDefinition().getStyle()) {
					QuickElement.Interpreted<?> parent = getParentElement();
					while (parent != null && !(parent instanceof QuickStyledElement.Interpreted))
						parent = parent.getParentElement();
					theStyle = getDefinition().getStyle()
						.interpret(parent == null ? null : ((QuickStyledElement.Interpreted<?>) parent).getStyle(), cache.applications);
				}
				return this;
			}
		}
	}

	@Override
	Interpreted<?> getInterpreted();

	QuickInstanceStyle getStyle();

	/** An abstract {@link QuickStyledElement} implementation */
	public abstract class Abstract extends QuickElement.Abstract implements QuickStyledElement {
		private QuickInstanceStyle theStyle;

		/**
		 * @param interpreted The interpretation that is creating this element
		 * @param parent The parent element
		 */
		protected Abstract(QuickStyledElement.Interpreted<?> interpreted, QuickElement parent) {
			super(interpreted, parent);
		}

		@Override
		public QuickStyledElement.Interpreted<?> getInterpreted() {
			return (QuickStyledElement.Interpreted<?>) super.getInterpreted();
		}

		@Override
		public QuickInstanceStyle getStyle() {
			return theStyle;
		}

		@Override
		public QuickStyledElement.Abstract update(ModelSetInstance models) throws ModelInstantiationException {
			super.update(models);
			QuickElement parent = getParentElement();
			StyleQIS.installParentModels(getModels(), parent == null ? null : parent.getModels());
			if (theStyle == null || theStyle.getInterpreted() != getInterpreted().getStyle())
				theStyle = getInterpreted().getStyle().create();
			theStyle.update(getModels());
			return this;
		}
	}

	public interface QuickInstanceStyle {
		public interface Def extends QuickCompiledStyle {
			@Override
			Interpreted interpret(QuickInterpretedStyle parent, Map<CompiledStyleApplication, InterpretedStyleApplication> applications)
				throws ExpressoInterpretationException;
		}

		public interface Interpreted extends QuickInterpretedStyle {
			@Override
			Def getCompiled();

			QuickInstanceStyle create();
		}

		Interpreted getInterpreted();

		void update(ModelSetInstance models) throws ModelInstantiationException;
	}

	static QuickTypeStyle getTypeStyle(QonfigElement element, String toolkitName, Version toolkitVersion, String elementName) {
		QonfigToolkit.ToolkitDefVersion tdv = new QonfigToolkit.ToolkitDefVersion(toolkitVersion.major, toolkitVersion.minor);
		QonfigToolkit toolkit;
		if (element.getType().getDeclarer().getName().equals(toolkitName)//
			&& element.getType().getDeclarer().getMajorVersion() == tdv.majorVersion
			&& element.getType().getDeclarer().getMinorVersion() == tdv.minorVersion)
			toolkit = element.getType().getDeclarer();
		else
			toolkit = element.getType().getDeclarer().getDependenciesByDefinition()
			.getOrDefault(toolkitName, Collections.emptyNavigableMap()).get(tdv);
		QonfigElementOrAddOn type;
		if (toolkit == null) {
			for (QonfigAddOn inh : element.getInheritance().values()) {
				toolkit = inh.getDeclarer().getDependenciesByDefinition().getOrDefault(toolkitName, Collections.emptyNavigableMap())
					.get(tdv);
				if (toolkit != null)
					break;
			}
		}
		if (toolkit == null)
			throw new IllegalArgumentException(
				"No such toolkit " + toolkitName + " " + toolkitVersion + " found in type information of element " + element);
		type = toolkit.getElementOrAddOn(elementName);
		return QuickTypeStyle.get(type); // Should be available
	}
}
