package org.observe.quick.style;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.observe.SettableValue;
import org.observe.expresso.CompiledExpressoEnv;
import org.observe.expresso.ExpressoCompilationException;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.InterpretedExpressoEnv;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ModelType;
import org.observe.expresso.ModelType.ModelInstanceType;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableModelSet;
import org.observe.expresso.ObservableModelSet.CompiledModelValue;
import org.observe.expresso.ObservableModelSet.InterpretedModelSet;
import org.observe.expresso.ObservableModelSet.InterpretedValueSynth;
import org.observe.expresso.ObservableModelSet.ModelComponentId;
import org.observe.expresso.ObservableModelSet.ModelComponentNode;
import org.observe.expresso.ObservableModelSet.ModelInstantiator;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.ObservableModelSet.ModelSetInstanceBuilder;
import org.observe.expresso.ObservableModelSet.ModelValueInstantiator;
import org.observe.expresso.qonfig.ExElement;
import org.observe.expresso.qonfig.ExElementTraceable;
import org.observe.expresso.qonfig.ExModelAugmentation;
import org.observe.expresso.qonfig.ExWithRequiredModels;
import org.observe.expresso.qonfig.ExpressoQIS;
import org.observe.expresso.qonfig.QonfigAttributeGetter;
import org.observe.expresso.qonfig.QonfigChildGetter;
import org.qommons.collect.CollectionUtils;
import org.qommons.config.DefaultQonfigParser;
import org.qommons.config.QommonsConfig;
import org.qommons.config.QonfigDocument;
import org.qommons.config.QonfigElement;
import org.qommons.config.QonfigElement.QonfigValue;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretationException;
import org.qommons.config.QonfigParseException;
import org.qommons.config.QonfigToolkit;
import org.qommons.io.LocatedFilePosition;
import org.qommons.io.SimpleXMLParser;

/** A structure containing many style values that may apply to all &lt;styled> elements in a document */
@ExElementTraceable(toolkit = QuickStyleInterpretation.STYLE,
qonfigType = QuickStyleSheet.STYLE_SHEET,
interpretation = QuickStyleSheet.Interpreted.class)
public class QuickStyleSheet extends ExElement.Def.Abstract<ExElement.Void> {
	/** The XML name for this type */
	public static final String STYLE_SHEET = "style-sheet";
	/** The XML name for {@link StyleSheetRef} */
	public static final String IMPORT_STYLE_SHEET = "import-style-sheet";
	private static final String SUB_SHEET_MODEL_NAME = "$MODELINSTANCE";

	/** An imported style sheet */
	@ExElementTraceable(toolkit = QuickStyleInterpretation.STYLE, qonfigType = IMPORT_STYLE_SHEET)
	public static class StyleSheetRef extends ExElement.Def.Abstract<ExElement.Void> {
		private String theName;
		private QuickStyleSheet theTarget;
		private URL theReference;

		/**
		 * @param parent The parent style sheet importing this style sheet
		 * @param type The Qonfig type of this import reference
		 */
		public StyleSheetRef(QuickStyleSheet parent, QonfigElementOrAddOn type) {
			super(parent, type);
		}

		/** @return The name of the imported style sheet in the parent */
		@QonfigAttributeGetter("name")
		public String getName() {
			return theName;
		}

		/** @return The location of the imported style sheet */
		@QonfigAttributeGetter("ref")
		public URL getReference() {
			return theReference;
		}

		/** @return The imported style sheet */
		public QuickStyleSheet getTarget() {
			return theTarget;
		}

		@Override
		protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
			super.doUpdate(session);
			theName = session.getAttributeText("name");

			importStyleSheet(session);
		}

		private void importStyleSheet(ExpressoQIS session) throws QonfigInterpretationException {
			DefaultQonfigParser parser = new DefaultQonfigParser();
			for (QonfigToolkit tk : session.getElement().getDocument().getDocToolkit().getDependencies().values())
				parser.withToolkit(tk);
			QonfigValue address = session.attributes().get("ref").get();
			URL ref;
			try {
				String urlStr = QommonsConfig.resolve(address.text, session.getElement().getDocument().getLocation());
				ref = new URL(urlStr);
			} catch (IOException e) {
				throw new QonfigInterpretationException("Bad style-sheet reference: " + session.getAttributeText("ref"),
					address.position == null ? null : new LocatedFilePosition(address.fileLocation, address.position.getPosition(0)), //
						address.text.length(), e);
			}
			theReference = ref;
			QonfigDocument ssDoc;
			try (InputStream in = new BufferedInputStream(ref.openStream())) {
				ssDoc = parser.parseDocument(false, ref.toString(), in);
			} catch (IOException e) {
				throw new QonfigInterpretationException("Could not access style-sheet reference " + ref,
					address.position == null ? null : new LocatedFilePosition(address.fileLocation, address.position.getPosition(0)), //
						address.text.length(), e);
			} catch (SimpleXMLParser.XmlParseException e) {
				throw new QonfigInterpretationException("Could not parse style-sheet reference " + ref,
					address.position == null ? null : new LocatedFilePosition(address.fileLocation, address.position.getPosition(0)), //
						address.text.length(), e);
			} catch (QonfigParseException e) {
				throw new QonfigInterpretationException("Malformed style-sheet reference " + ref,
					address.position == null ? null : new LocatedFilePosition(address.fileLocation, address.position.getPosition(0)), //
						address.text.length(), e);
			}
			if (!session.getFocusType().getDeclarer().getElement("style-sheet").isAssignableFrom(ssDoc.getRoot().getType()))
				throw new QonfigInterpretationException(
					"Style-sheet reference does not parse to a style-sheet (" + ssDoc.getRoot().getType() + "): " + ref, //
					address.position == null ? null : new LocatedFilePosition(address.fileLocation, address.position.getPosition(0)),
						address.text.length());
			ExpressoQIS importSession = session.interpretRoot(ssDoc.getRoot());
			QuickTypeStyle.TypeStyleSet styleTypeSet = session.get(QuickStyleElement.STYLE_TYPE_SET, QuickTypeStyle.TypeStyleSet.class);
			if (styleTypeSet == null) {
				styleTypeSet = new QuickTypeStyle.TypeStyleSet();
				session.putGlobal(QuickStyleElement.STYLE_TYPE_SET, styleTypeSet);
			}
			importSession.as(ExpressoQIS.class)//
			.setExpressoEnv(importSession.getExpressoEnv()
				.with(ObservableModelSet.build(address.text, session.getExpressoEnv().getModels().getNameChecker())))//
			.put(QuickStyleElement.STYLE_TYPE_SET, styleTypeSet);
			if (theTarget == null)
				theTarget = importSession.interpret(QuickStyleSheet.class);
			theTarget.update(importSession);
		}
	}

	private final List<QuickStyleElement.Def> theStyleElements;
	private final List<StyleSheetRef> theStyleSheetRefs;
	private final Map<String, QuickStyleSheet> theImportedStyleSheets;
	private final List<QuickStyleSet> theStyleSetList;
	private final Map<String, QuickStyleSet> theStyleSets;

	/**
	 * @param parent The parent element of this style sheet
	 * @param qonfigType The Qonfig type of this style sheet
	 */
	public QuickStyleSheet(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
		super(parent, qonfigType);
		theStyleSetList = new ArrayList<>();
		theStyleSets = new HashMap<>();
		theStyleSheetRefs = new ArrayList<>();
		theImportedStyleSheets = new LinkedHashMap<>();
		theStyleElements = new ArrayList<>();
	}

	/** @return This style sheet's style sets, whose values can be inherited en-masse by name */
	public Map<String, QuickStyleSet> getStyleSets() {
		return Collections.unmodifiableMap(theStyleSets);
	}

	/** @return All style sets in this style sheet */
	@QonfigChildGetter("style-set")
	public List<QuickStyleSet> getStyleSetList() {
		return theStyleSetList;
	}

	/** @return All style elements in this style sheet */
	@QonfigChildGetter("style")
	public List<QuickStyleElement.Def> getStyleElements() {
		return Collections.unmodifiableList(theStyleElements);
	}

	/** @return All style sheets referred to by this style sheet, by name */
	public Map<String, QuickStyleSheet> getImportedStyleSheets() {
		return Collections.unmodifiableMap(theImportedStyleSheets);
	}

	/** @return All stylesheets imported into this style sheet */
	@QonfigChildGetter("style-sheet-ref")
	public List<StyleSheetRef> getImportedStyleSheetRefs() {
		return Collections.unmodifiableList(theStyleSheetRefs);
	}

	/**
	 * @param name The name of the style-set. May refer to a style set in an {@link #getImportedStyleSheets() imported} style sheet by using
	 *        the style sheet's name, dot (.), the style set name.
	 * @return All style values declared for the given style set
	 * @throws IllegalArgumentException If no such style-set was found
	 */
	public QuickStyleSet getStyleSet(String name) throws IllegalArgumentException {
		int dot = name.indexOf('.');
		if (dot >= 0) {
			QuickStyleSheet ss = theImportedStyleSheets.get(name.substring(0, dot));
			if (ss == null)
				throw new IllegalArgumentException("No such style-sheet found: '" + name.substring(0, dot) + "'");
			return ss.getStyleSet(name.substring(dot + 1));
		} else {
			QuickStyleSet styleSet = theStyleSets.get(name);
			if (styleSet == null)
				throw new IllegalArgumentException("No such style-set found: '" + name + "'");
			return styleSet;
		}
	}

	/**
	 * @param styleValues Collection into which to add style values in this style sheet that
	 *        {@link StyleApplicationDef#applies(QonfigElement) apply} to the given element
	 * @param element The element to get style values for
	 * @param env The compiled environment to validate against
	 * @throws QonfigInterpretationException If this style sheet's styles cannot be applied in the given environment
	 */
	public final void getStyleValues(Collection<QuickStyleValue> styleValues, QonfigElement element, CompiledExpressoEnv env)
		throws QonfigInterpretationException {
		ExWithRequiredModels.RequiredModelContext styleSheetModelContext = getAddOn(ExWithRequiredModels.Def.class).getContext(env);
		for (QuickStyleElement.Def style : theStyleElements)
			style.getStyleValues(styleValues, StyleApplicationDef.ALL, element, env, styleSheetModelContext);
		for (QuickStyleSheet imported : theImportedStyleSheets.values())
			imported.getStyleValues(styleValues, element, env);
	}

	@Override
	protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
		if (getParentElement() == null && session.getExpressoEnv().getModels() instanceof ObservableModelSet.Builder) {
			// Label the models as ours
			((ObservableModelSet.Builder) session.getExpressoEnv().getModels()).withTagValue(ExModelAugmentation.ELEMENT_MODEL_TAG,
				getIdentity());
		}

		super.doUpdate(session);

		session.put(ExWithStyleSheet.QUICK_STYLE_SHEET, this);

		syncChildren(StyleSheetRef.class, theStyleSheetRefs, session.forChildren("style-sheet-ref"));
		theImportedStyleSheets.clear();
		for (StyleSheetRef ref : theStyleSheetRefs) {
			if (theImportedStyleSheets.put(ref.getName(), ref.getTarget()) != null)
				throw new QonfigInterpretationException("Multiple imported style sheets named '" + ref.getName() + "'",
					ref.reporting().getPosition(), 0);
			if (!ref.getTarget().getExpressoEnv().getModels().getComponentNames().isEmpty()) {
				// Local style-sheet model. Expose to this style-sheet.
				ObservableModelSet.Builder builder = ExModelAugmentation.augmentElementModel(getExpressoEnv().getModels(), this);
				setExpressoEnv(getExpressoEnv().with(builder));
				session.setExpressoEnv(getExpressoEnv());
				ObservableModelSet.Builder subSheetModel = builder.createSubModel(ref.getName(), ref.reporting().getPosition());
				subSheetModel.with(SUB_SHEET_MODEL_NAME, ModelTypes.Value.forType(ModelSetInstance.class), ModelValueInstantiator
					.of(msi -> SettableValue.<ModelSetInstance> build().withDescription(SUB_SHEET_MODEL_NAME).build()), null);
				ModelComponentId subSheetModelId = subSheetModel.getLocalComponent(SUB_SHEET_MODEL_NAME).getIdentity();
				try {
					addComponents(subSheetModel, ref.getTarget().getExpressoEnv(), subSheetModelId);
				} catch (ExpressoCompilationException e) {
					reporting().error(e.getMessage(), e);
				}
			}
		}

		// Parse style-sheets and style-sets first so they can be referred to
		syncChildren(QuickStyleSet.class, theStyleSetList, session.forChildren("style-set"));
		theStyleSets.clear();
		for (QuickStyleSet styleSet : theStyleSetList) {
			if (theStyleSets.put(styleSet.getName(), styleSet) != null)
				throw new QonfigInterpretationException("Multiple style sets named '" + styleSet.getName() + "'",
					styleSet.reporting().getPosition(), 0);
		}

		syncChildren(QuickStyleElement.Def.class, theStyleElements, session.forChildren("style"));
	}

	private static void addComponents(ObservableModelSet.Builder myModel, CompiledExpressoEnv subSheetEnv, ModelComponentId subSheetModelId)
		throws ExpressoCompilationException {
		for (String name : subSheetEnv.getModels().getComponentNames()) {
			ModelComponentNode<?> node = subSheetEnv.getModels().getComponentIfExists(name);
			if (node.getModel() != null)
				addComponents(myModel.createSubModel(name, node.getSourceLocation()), subSheetEnv.with(node.getModel()), subSheetModelId);
			else
				myModel.withMaker(name, new CompiledSubSheetValue<>(node, subSheetEnv, subSheetModelId), node.getSourceLocation());
		}
	}

	static class CompiledSubSheetValue<M> implements CompiledModelValue<M> {
		private final CompiledModelValue<M> theSubSheetValue;
		private final ModelType<M> theType;
		private final ModelComponentId theSubSheetModelId;

		CompiledSubSheetValue(CompiledModelValue<M> subSheetValue, CompiledExpressoEnv subSheetEnv, ModelComponentId subSheetModelId)
			throws ExpressoCompilationException {
			theSubSheetValue = subSheetValue;
			theType = subSheetValue.getModelType(subSheetEnv);
			theSubSheetModelId = subSheetModelId;
		}

		@Override
		public ModelType<M> getModelType(CompiledExpressoEnv env) throws ExpressoCompilationException {
			return theType;
		}

		@Override
		public InterpretedValueSynth<M, ?> interpret(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
			InterpretedExpressoEnv subSheetModel = env.get(theSubSheetModelId.getPath(), InterpretedExpressoEnv.class);
			if (subSheetModel == null)
				throw new IllegalStateException("No " + theSubSheetModelId.getPath() + " property set in environment");
			InterpretedValueSynth<M, M> subSheetModelValue = (InterpretedValueSynth<M, M>) theSubSheetValue.interpret(subSheetModel);
			return new InterpretedValueSynth<M, M>() {
				@Override
				public ModelInstanceType<M, M> getType() {
					return subSheetModelValue.getType();
				}

				@Override
				public List<? extends InterpretedValueSynth<?, ?>> getComponents() {
					return Collections.singletonList(subSheetModelValue);
				}

				@Override
				public ModelValueInstantiator<M> instantiate() throws ModelInstantiationException {
					ModelValueInstantiator<M> ssmvInstantiated = subSheetModelValue.instantiate();
					return new SubSheetValueInstantiator<>(ssmvInstantiated, theSubSheetModelId);
				}
			};
		}
	}

	static class SubSheetValueInstantiator<M> implements ModelValueInstantiator<M> {
		private final ModelValueInstantiator<M> theSubSheetModelValue;
		private final ModelComponentId theSubSheetModelId;

		SubSheetValueInstantiator(ModelValueInstantiator<M> subSheetModelValue, ModelComponentId subSheetModelId) {
			theSubSheetModelValue = subSheetModelValue;
			theSubSheetModelId = subSheetModelId;
		}

		@Override
		public void instantiate() throws ModelInstantiationException {
			theSubSheetModelValue.instantiate();
		}

		@Override
		public M get(ModelSetInstance models) throws ModelInstantiationException, IllegalStateException {
			ModelSetInstance subSheetModelInstance = ((SettableValue<ModelSetInstance>) models.get(theSubSheetModelId)).get();
			return theSubSheetModelValue.get(subSheetModelInstance);
		}

		@Override
		public M forModelCopy(M value, ModelSetInstance sourceModels, ModelSetInstance newModels) throws ModelInstantiationException {
			ModelSetInstance subSheetSourceModel = ((SettableValue<ModelSetInstance>) sourceModels.get(theSubSheetModelId)).get();
			ModelSetInstance subSheetNewModel = ((SettableValue<ModelSetInstance>) newModels.get(theSubSheetModelId)).get();
			return theSubSheetModelValue.forModelCopy(value, subSheetSourceModel, subSheetNewModel);
		}
	}

	/**
	 * @param parent The parent element for the interpreted style sheet
	 * @return The interpreted style sheet
	 */
	public Interpreted interpret(ExElement.Interpreted<?> parent) {
		return new Interpreted(this, parent);
	}

	/**
	 * Prints a representation of this style sheet to a StringBuilder
	 *
	 * @param str The string builder to append to. Null to create a new one.
	 * @param indent The amount by which to indent this style sheet in the string
	 * @return The string builder
	 */
	public StringBuilder print(StringBuilder str, int indent) {
		if (str == null)
			str = new StringBuilder(super.toString());
		str.append("{");
		for (QuickStyleElement.Def style : theStyleElements) {
			indent(str, indent);
			str.append(style);
		}
		if (!theImportedStyleSheets.isEmpty() || !theStyleElements.isEmpty())
			str.append('\n');
		for (Map.Entry<String, QuickStyleSheet> imp : theImportedStyleSheets.entrySet()) {
			indent(str, indent);
			str.append(imp.getKey()).append("<-");
			imp.getValue().print(str, indent + 1).append('\n');
		}
		// TODO Style sets
		return str.append("}");
	}

	private void indent(StringBuilder str, int indent) {
		for (int i = 0; i < indent; i++)
			str.append('\t');
	}

	/** @return A string representation of this style sheet's style content */
	public String printContent() {
		return print(null, 0).toString();
	}

	/** Interpretation of a {@link QuickStyleSheet} */
	public static class Interpreted extends ExElement.Interpreted.Abstract<ExElement.Void> {
		private final List<QuickStyleElement.Interpreted<?>> theStyleElements;
		private final Map<String, QuickStyleSheet.Interpreted> theImportedStyleSheets;
		private final Map<String, QuickStyleSet.Interpreted> theStyleSets;

		Interpreted(QuickStyleSheet definition, ExElement.Interpreted<?> parent) {
			super(definition, parent);
			theStyleElements = new ArrayList<>();
			theImportedStyleSheets = new LinkedHashMap<>();
			theStyleSets = new LinkedHashMap<>();
		}

		@Override
		public QuickStyleSheet getDefinition() {
			return (QuickStyleSheet) super.getDefinition();
		}

		/** @return All style elements in this style sheet */
		public List<QuickStyleElement.Interpreted<?>> getStyleElements() {
			return Collections.unmodifiableList(theStyleElements);
		}

		/** @return All style sheets referred to by this style sheet, by name */
		public Map<String, QuickStyleSheet.Interpreted> getImportedStyleSheets() {
			return Collections.unmodifiableMap(theImportedStyleSheets);
		}

		/** @return This style sheet's style sets, whose values can be inherited en-masse by name */
		public Map<String, QuickStyleSet.Interpreted> getStyleSets() {
			return Collections.unmodifiableMap(theStyleSets);
		}

		/**
		 * @param styleSheet The style sheet to find the interpretation of
		 * @return The interpretation of this style sheet or one of its imported style sheets whose definition is given, or null if this
		 *         style sheet is not and does not use the given style sheet
		 */
		public Interpreted findInterpretation(QuickStyleSheet styleSheet) {
			if (styleSheet.getIdentity() == getIdentity())
				return this;
			for (Interpreted imported : theImportedStyleSheets.values()) {
				Interpreted found = imported.findInterpretation(styleSheet);
				if (found != null)
					return found;
			}
			return null;
		}

		/**
		 * Initializes or updates this style sheet
		 *
		 * @param expressoEnv The expresso environment to use to interpret expressions
		 * @throws ExpressoInterpretationException If this style sheet could not be interpreted
		 */
		public void updateStyleSheet(InterpretedExpressoEnv expressoEnv) throws ExpressoInterpretationException {
			update(expressoEnv);
		}

		@Override
		protected void doUpdate(InterpretedExpressoEnv expressoEnv) throws ExpressoInterpretationException {
			if (getParentElement() == null) {
				// The root model for imported style sheets with their own model isn't interpreted anywhere else
				for (InterpretedModelSet model : getModels().getInheritance().values())
					model.interpret(expressoEnv);
			}

			// Interpret the imported style sheets
			List<QuickStyleSheet.Interpreted> importedStyleSheets = new ArrayList<>(theImportedStyleSheets.values());
			theImportedStyleSheets.clear();
			CollectionUtils
			.synchronize(importedStyleSheets, new ArrayList<>(getDefinition().getImportedStyleSheets().entrySet()),
				(interp, def) -> interp.getIdentity() == def.getValue().getIdentity())//
			.<ExpressoInterpretationException> simpleX(def -> def.getValue().interpret(null))//
			.onLeftX(el -> el.getLeftValue().destroy())//
			.onRightX(el -> {
				el.getLeftValue().updateStyleSheet(expressoEnv);
				// Populate the sub-sheet environment for imported models
				if (expressoEnv.getModels().getLocalComponent(el.getRightValue().getKey()) != null)
					expressoEnv.put(el.getRightValue().getKey() + "." + SUB_SHEET_MODEL_NAME, el.getLeftValue().getExpressoEnv());
				theImportedStyleSheets.put(el.getRightValue().getKey(), el.getLeftValue());
			})//
			.onCommonX(el -> {
				el.getLeftValue().updateStyleSheet(expressoEnv);
				theImportedStyleSheets.put(el.getRightValue().getKey(), el.getLeftValue());
			})//
			.rightOrder()//
			.adjust();

			super.doUpdate(expressoEnv);

			syncChildren(getDefinition().getStyleElements(), theStyleElements, def -> def.interpret(this),
				(i, sEnv) -> i.updateStyle(sEnv));

			List<QuickStyleSet.Interpreted> styleSets = new ArrayList<>(theStyleSets.values());
			theStyleSets.clear();
			CollectionUtils
			.synchronize(styleSets, new ArrayList<>(getDefinition().getStyleSets().entrySet()),
				(interp, def) -> interp.getIdentity() == def.getValue().getIdentity())//
			.<ExpressoInterpretationException> simpleX(def -> def.getValue().interpret(this))//
			.onLeftX(el -> el.getLeftValue().destroy())//
			.onRightX(el -> {
				el.getLeftValue().updateStyleSet(expressoEnv);
				theStyleSets.put(el.getRightValue().getKey(), el.getLeftValue());
			})//
			.onCommonX(el -> {
				el.getLeftValue().updateStyleSet(expressoEnv);
				theStyleSets.put(el.getRightValue().getKey(), el.getLeftValue());
			})//
			.rightOrder()//
			.adjust();
		}

		/** @return Style sheet models to use to populate element models with values needed by this style sheet */
		public StyleSheetModels instantiateModels() {
			ArrayList<ModelInstantiator> styleSetModels = new ArrayList<>(theStyleSets.size());
			for (QuickStyleSet.Interpreted styleSet : theStyleSets.values()) {
				if (styleSet.getModels().getIdentity() != getModels().getIdentity())
					styleSetModels.add(styleSet.getModels().instantiate());
			}
			styleSetModels.trimToSize();
			Map<ModelComponentId, StyleSheetModels> subModels = new LinkedHashMap<>();
			StyleSheetModels models = new StyleSheetModels(getModels().instantiate(), styleSetModels, subModels);
			for (Map.Entry<String, QuickStyleSheet.Interpreted> subSheet : theImportedStyleSheets.entrySet()) {
				ModelComponentNode<?> component = getModels().getLocalComponent(subSheet.getKey());
				if (component != null)
					subModels.put(component.getModel().getLocalComponent(SUB_SHEET_MODEL_NAME).getIdentity(),
						subSheet.getValue().instantiateModels());
			}
			return models;
		}
	}

	/** Populates element models with values needed by a style sheet */
	public static class StyleSheetModels {
		private final ModelInstantiator theStyleSheetModels;
		private final List<ModelInstantiator> theStyleSetModels;
		private final Map<ModelComponentId, StyleSheetModels> theSubModels;

		StyleSheetModels(ModelInstantiator styleSheetModels, List<ModelInstantiator> styleSetModels,
			Map<ModelComponentId, StyleSheetModels> subModels) {
			theStyleSheetModels = styleSheetModels;
			theStyleSetModels = styleSetModels;
			theSubModels = subModels;
		}

		/**
		 * Instantiates this model's values. Must be called once after creation.
		 *
		 * @throws ModelInstantiationException If any model values fail to initialize
		 */
		public void instantiate() throws ModelInstantiationException {
			theStyleSheetModels.instantiate();
			for (ModelInstantiator styleSetModel : theStyleSetModels)
				styleSetModel.instantiate();
			for (StyleSheetModels subSheet : theSubModels.values())
				subSheet.instantiate();
		}

		/**
		 * @param into The model instance builder to populate the style sheet models into
		 * @return The style sheet models
		 * @throws ModelInstantiationException If the style sheet models could not be instantiated
		 */
		public ModelSetInstance populate(ModelSetInstanceBuilder into) throws ModelInstantiationException {
			ModelSetInstanceBuilder builder = theStyleSheetModels.createInstance(into.getUntil())//
				.withAll(into);
			// Populate the models for each imported style sheet
			for (Map.Entry<ModelComponentId, StyleSheetModels> subModel : theSubModels.entrySet()) {
				SettableValue<ModelSetInstance> subModelHolder = (SettableValue<ModelSetInstance>) builder.get(subModel.getKey());
				subModelHolder.set(subModel.getValue().populate(into), null);
			}
			ModelSetInstance built = builder.build();
			for (ModelInstantiator styleSetModel : theStyleSetModels) {
				into.withAll(//
					styleSetModel.createInstance(into.getUntil())//
					.withAll(built)//
					.build());
			}
			into.withAll(built);
			return built;
		}
	}
}
