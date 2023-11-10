package org.observe.expresso.qonfig;

import java.text.ParseException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.observe.Observable;
import org.observe.ObservableValue;
import org.observe.ObservableValueEvent;
import org.observe.Observer;
import org.observe.SettableValue;
import org.observe.Subscription;
import org.observe.assoc.ObservableMap;
import org.observe.config.ObservableConfig;
import org.observe.config.ObservableConfig.ObservableConfigMapBuilder;
import org.observe.config.ObservableConfig.ObservableConfigValueBuilder;
import org.observe.config.ObservableConfigFormat;
import org.observe.config.ObservableConfigFormatSet;
import org.observe.config.ObservableValueSet;
import org.observe.expresso.ExpressoInterpretationException;
import org.observe.expresso.InterpretedExpressoEnv;
import org.observe.expresso.ModelInstantiationException;
import org.observe.expresso.ModelType;
import org.observe.expresso.ModelType.ModelInstanceType;
import org.observe.expresso.ModelTypes;
import org.observe.expresso.ObservableModelSet.InterpretedValueSynth;
import org.observe.expresso.ObservableModelSet.ModelComponentId;
import org.observe.expresso.ObservableModelSet.ModelSetInstance;
import org.observe.expresso.ObservableModelSet.ModelValueInstantiator;
import org.observe.expresso.VariableType;
import org.observe.expresso.qonfig.ExAddOn.Void;
import org.observe.expresso.qonfig.ExElement.Def;
import org.observe.expresso.qonfig.ModelValueElement.InterpretedSynth;
import org.observe.util.EntityReflector;
import org.observe.util.TypeTokens;
import org.qommons.ArrayUtils;
import org.qommons.Causable;
import org.qommons.Identifiable;
import org.qommons.LambdaUtils;
import org.qommons.QommonsUtils;
import org.qommons.ThreadConstraint;
import org.qommons.TimeUtils;
import org.qommons.Transaction;
import org.qommons.Version;
import org.qommons.collect.BetterList;
import org.qommons.collect.MutableCollectionElement.StdMsg;
import org.qommons.config.QonfigAddOn;
import org.qommons.config.QonfigElementOrAddOn;
import org.qommons.config.QonfigInterpretation;
import org.qommons.config.QonfigInterpretationException;
import org.qommons.config.QonfigInterpreterCore;
import org.qommons.config.QonfigToolkit;
import org.qommons.config.SpecialSession;
import org.qommons.io.ArchiveEnabledFileSource;
import org.qommons.io.BetterFile;
import org.qommons.io.BetterFile.FileDataSource;
import org.qommons.io.ErrorReporting;
import org.qommons.io.Format;
import org.qommons.io.LocatedPositionedContent;
import org.qommons.io.NativeFileSource;
import org.qommons.io.SpinnerFormat;

import com.google.common.reflect.TypeToken;

/** Qonfig Interpretation for the ExpressoConfigV0_1 API */
public class ExpressoConfigV0_1 implements QonfigInterpretation {
	/** The name of the expresso config toolkit */
	public static final String NAME = "Expresso-Config";

	/** The version of this implementation of the expresso config toolkit */
	public static final Version VERSION = new Version(0, 1, 0);

	/** {@link #NAME} and {@link #VERSION} combined */
	public static final String CONFIG = "Expresso-Config v0.1";

	/** The name of the model value to store the {@link ObservableConfig} in the model */
	public static final String CONFIG_NAME = "$CONFIG$";

	public interface FormatValidation<T> {
		public interface Def<E extends Instantiator<?, ?>> extends ExElement.Def<E> {
			Interpreted<?, ? extends E> interpret(ExElement.Interpreted<?> parent);
		}

		public interface Interpreted<T, E extends Instantiator<T, ?>> extends ExElement.Interpreted<E> {
			void updateFormat(InterpretedExpressoEnv env, TypeToken<T> valueType) throws ExpressoInterpretationException;

			List<? extends InterpretedValueSynth<?, ?>> getComponents();

			Instantiator<T, ?> create();
		}

		public interface Instantiator<T, V extends FormatValidation<T>> extends ExElement, ModelValueInstantiator<V> {
			@Override
			default void instantiate() {
				instantiated();
			}
		}

		String test(T value, CharSequence text);
	}

	private QonfigToolkit theConfig;

	@Override
	public Set<Class<? extends SpecialSession<?>>> getExpectedAPIs() {
		return Collections.singleton(ExpressoQIS.class);
	}

	@Override
	public String getToolkitName() {
		return NAME;
	}

	@Override
	public Version getVersion() {
		return VERSION;
	}

	@Override
	public void init(QonfigToolkit toolkit) {
		theConfig = toolkit;
	}

	@Override
	public QonfigInterpreterCore.Builder configureInterpreter(QonfigInterpreterCore.Builder interpreter) {
		configureConfigModels(interpreter);
		configureFormats(interpreter);
		return interpreter;
	}

	void configureConfigModels(QonfigInterpreterCore.Builder interpreter) {
		interpreter.createWith("config", ObservableModelElement.ConfigModelElement.Def.class,
			ExElement.creator(ObservableModelElement.ConfigModelElement.Def::new));
		interpreter.createWith("value", ConfigModelValue.Def.class, ExElement.creator(ConfigValue::new));
		interpreter.createWith("value-set", ConfigModelValue.Def.class, ExElement.creator(ConfigValueSet::new));
		// TODO list, sorted-list, set, sorted-set
		interpreter.createWith(ConfigMap.CONFIG_MAP, ConfigMap.Def.class, ExAddOn.creator(ConfigMap.Def::new));
		interpreter.createWith("map", ConfigModelValue.Def.class, ExElement.creator(ExConfigMap::new));
		// TODO sorted-map, multi-map, sorted-multi-map
	}

	@ExElementTraceable(toolkit = CONFIG, qonfigType = "config-value", interpretation = ConfigValue.Interpreted.class)
	static class ConfigValue extends ConfigModelValue.Def.Abstract<SettableValue<?>> {
		private CompiledExpression theDefaultValue;

		public ConfigValue(ExElement.Def<?> parent, QonfigElementOrAddOn type) {
			super(parent, type, ModelTypes.Value);
		}

		@QonfigAttributeGetter("default")
		public CompiledExpression getDefaultValue() {
			return theDefaultValue;
		}

		@Override
		protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
			super.doUpdate(session.asElement(CONFIG, "config-model-value"));
			theDefaultValue = getAttributeExpression("default", session.asElement(CONFIG, "config-value"));
		}

		@Override
		public Interpreted<?> interpretValue(ExElement.Interpreted<?> parent) {
			return new Interpreted<>(this, parent);
		}

		static class Interpreted<T> extends ConfigModelValue.Interpreted.Abstract<T, SettableValue<?>, SettableValue<T>> {
			private InterpretedValueSynth<SettableValue<?>, SettableValue<T>> theDefaultValue;

			Interpreted(ConfigValue definition, ExElement.Interpreted<?> parent) {
				super(definition, parent);
			}

			@Override
			public ConfigValue getDefinition() {
				return (ConfigValue) super.getDefinition();
			}

			@Override
			protected ModelInstanceType<SettableValue<?>, SettableValue<T>> getTargetType() {
				return ModelTypes.Value.forType(getValueType());
			}

			public InterpretedValueSynth<SettableValue<?>, SettableValue<T>> getDefaultValue() {
				return theDefaultValue;
			}

			@Override
			protected void doUpdate(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
				super.doUpdate(env);
				theDefaultValue = interpret(getDefinition().getDefaultValue(), ModelTypes.Value.forType(getValueType()));
			}

			@Override
			public List<? extends InterpretedValueSynth<?, ?>> getComponents() {
				return getComponents(theDefaultValue);
			}

			@Override
			public ModelValueInstantiator<SettableValue<T>> instantiate() {
				return new Instantiator<>(this);
			}
		}

		static class Instantiator<T> extends ConfigModelValue.Instantiator<T, SettableValue<T>> {
			private final ModelValueInstantiator<SettableValue<T>> theDefaultValue;

			Instantiator(Interpreted<T> interpreted) {
				super(interpreted.getConfigValue().instantiate(), interpreted.getValueType(), interpreted.getConfigPath(),
					interpreted.getFormat() == null ? null : interpreted.getFormat().instantiate(), interpreted.getFormatSet());
				theDefaultValue = interpreted.getDefaultValue() == null ? null : interpreted.getDefaultValue().instantiate();
			}

			@Override
			public void instantiate() {
				super.instantiate();
				if (theDefaultValue != null)
					theDefaultValue.instantiate();
			}

			@Override
			public SettableValue<T> create(ObservableConfigValueBuilder<T> config, ModelSetInstance msi)
				throws ModelInstantiationException {
				SettableValue<T> value = config.buildValue(null);
				if (theDefaultValue != null && config.getConfig().getChild(config.getPath(), false, null) == null)
					value.set(theDefaultValue.get(msi).get(), null);
				return value;
			}
		}
	}

	static class ConfigValueSet extends ConfigModelValue.Def.Abstract<ObservableValueSet<?>> {
		public ConfigValueSet(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType, ModelTypes.ValueSet);
		}

		@Override
		public Interpreted<?> interpretValue(ExElement.Interpreted<?> parent) {
			return new Interpreted<>(this, parent);
		}

		static class Interpreted<T> extends ConfigModelValue.Interpreted.Abstract<T, ObservableValueSet<?>, ObservableValueSet<T>> {
			public Interpreted(ConfigValueSet definition, ExElement.Interpreted<?> parent) {
				super(definition, parent);
			}

			@Override
			public List<? extends InterpretedValueSynth<?, ?>> getComponents() {
				return Collections.emptyList();
			}

			@Override
			protected ModelInstanceType<ObservableValueSet<?>, ObservableValueSet<T>> getTargetType() {
				return ModelTypes.ValueSet.forType(getValueType());
			}

			@Override
			public ModelValueInstantiator<ObservableValueSet<T>> instantiate() {
				return new Instantiator<>(this);
			}
		}

		static class Instantiator<T> extends ConfigModelValue.Instantiator<T, ObservableValueSet<T>> {
			public Instantiator(Interpreted<T> interpreted) {
				super(interpreted.getConfigValue().instantiate(), interpreted.getValueType(), interpreted.getConfigPath(),
					interpreted.getFormat() == null ? null : interpreted.getFormat().instantiate(), interpreted.getFormatSet());
			}

			@Override
			public ObservableValueSet<T> create(ObservableConfigValueBuilder<T> config, ModelSetInstance msi)
				throws ModelInstantiationException {
				return config.buildEntitySet(null);
			}
		}
	}

	public static class ConfigMap<K> extends ExMapModelValue<K> {
		public static final String CONFIG_MAP = "config-map";

		@ExElementTraceable(toolkit = CONFIG, qonfigType = CONFIG_MAP, interpretation = Interpreted.class, instance = ConfigMap.class)
		public static class Def extends ExMapModelValue.Def<ConfigMap<?>> {
			private CompiledExpression theKeyFormat;

			public Def(QonfigAddOn type, ExElement.Def<? extends ExElement> element) {
				super(type, element);
			}

			@QonfigAttributeGetter("key-format")
			public CompiledExpression getKeyFormat() {
				return theKeyFormat;
			}

			@Override
			public void update(ExpressoQIS session, ExElement.Def<? extends ExElement> element) throws QonfigInterpretationException {
				super.update(session, element);

				theKeyFormat = element.getAttributeExpression("key-format", session);
			}

			@Override
			public Interpreted<?> interpret(ExElement.Interpreted<? extends ExElement> element) {
				return new Interpreted<>(this, element);
			}
		}

		public static class Interpreted<K> extends ExMapModelValue.Interpreted<K, ConfigMap<K>> {
			private InterpretedValueSynth<SettableValue<?>, SettableValue<ObservableConfigFormat<K>>> theKeyFormat;

			Interpreted(Def definition, ExElement.Interpreted<?> element) {
				super(definition, element);
			}

			@Override
			public Def getDefinition() {
				return (Def) super.getDefinition();
			}

			public InterpretedValueSynth<SettableValue<?>, SettableValue<ObservableConfigFormat<K>>> getKeyFormat() {
				return theKeyFormat;
			}

			@Override
			public Class<ConfigMap<K>> getInstanceType() {
				return (Class<ConfigMap<K>>) (Class<?>) ConfigMap.class;
			}

			@Override
			public void update(ExElement.Interpreted<?> element) throws ExpressoInterpretationException {
				super.update(element);

				theKeyFormat = getElement().interpret(getDefinition().getKeyFormat(), ModelTypes.Value.forType(
					TypeTokens.get().keyFor(ObservableConfigFormat.class).<ObservableConfigFormat<K>> parameterized(getKeyType())));
			}

			@Override
			public ConfigMap<K> create(ExElement element) {
				return new ConfigMap<>(element);
			}
		}

		private ModelValueInstantiator<SettableValue<ObservableConfigFormat<K>>> theKeyFormat;

		public ConfigMap(ExElement element) {
			super(element);
		}

		public ModelValueInstantiator<SettableValue<ObservableConfigFormat<K>>> getKeyFormat() {
			return theKeyFormat;
		}

		@Override
		public void update(ExAddOn.Interpreted<?, ?> interpreted, ExElement element) {
			super.update(interpreted, element);

			Interpreted<K> myInterpreted = (Interpreted<K>) interpreted;
			theKeyFormat = myInterpreted.getKeyFormat() == null ? null : myInterpreted.getKeyFormat().instantiate();
		}

		@Override
		public Class<Interpreted<?>> getInterpretationType() {
			return (Class<Interpreted<?>>) (Class<?>) Interpreted.class;
		}
	}

	static abstract class AbstractConfigMap<M> extends ConfigModelValue.Def.Abstract<M> {
		private VariableType theKeyType;
		private CompiledExpression theKeyFormat;

		protected AbstractConfigMap(Def<?> parent, QonfigElementOrAddOn qonfigType, ModelType.DoubleTyped<M> modelType) {
			super(parent, qonfigType, modelType);
		}

		public VariableType getKeyType() {
			return theKeyType;
		}

		public CompiledExpression getKeyFormat() {
			return theKeyFormat;
		}

		@Override
		protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
			super.doUpdate(session);

			ConfigMap.Def mapAddOn = getAddOn(ConfigMap.Def.class);
			theKeyType = mapAddOn.getKeyType();
			theKeyFormat = mapAddOn.getKeyFormat();
		}

		public static abstract class Interpreted<K, V, M, MV extends M> extends ConfigModelValue.Interpreted.Abstract<V, M, MV> {
			private TypeToken<K> theKeyType;
			private InterpretedValueSynth<SettableValue<?>, SettableValue<ObservableConfigFormat<K>>> theKeyFormat;

			protected Interpreted(AbstractConfigMap<M> definition, ExElement.Interpreted<?> parent) {
				super(definition, parent);
			}

			@Override
			public AbstractConfigMap<M> getDefinition() {
				return (AbstractConfigMap<M>) super.getDefinition();
			}

			public TypeToken<K> getKeyType() {
				return theKeyType;
			}

			public InterpretedValueSynth<SettableValue<?>, SettableValue<ObservableConfigFormat<K>>> getKeyFormat() {
				return theKeyFormat;
			}

			@Override
			protected void doUpdate(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
				super.doUpdate(env);

				ConfigMap.Interpreted<K> mapAddOn = getAddOn(ConfigMap.Interpreted.class);
				theKeyType = mapAddOn.getKeyType();
				theKeyFormat = mapAddOn.getKeyFormat();
			}
		}
	}

	static class ExConfigMap extends AbstractConfigMap<ObservableMap<?, ?>> {
		public ExConfigMap(Def<?> parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType, ModelTypes.Map);
		}

		@Override
		public Interpreted<?, ?> interpretValue(ExElement.Interpreted<?> parent) {
			return new Interpreted<>(this, parent);
		}

		static class Interpreted<K, V> extends AbstractConfigMap.Interpreted<K, V, ObservableMap<?, ?>, ObservableMap<K, V>> {
			public Interpreted(ExConfigMap definition, ExElement.Interpreted<?> parent) {
				super(definition, parent);
			}

			@Override
			public List<? extends InterpretedValueSynth<?, ?>> getComponents() {
				return Collections.emptyList();
			}

			@Override
			protected ModelInstanceType<ObservableMap<?, ?>, ObservableMap<K, V>> getTargetType() {
				return ModelTypes.Map.forType(getKeyType(), getValueType());
			}

			@Override
			public ModelValueInstantiator<ObservableMap<K, V>> instantiate() {
				return new Instantiator<>(this);
			}
		}

		static class Instantiator<K, V> extends ConfigModelValue.Instantiator<V, ObservableMap<K, V>> {
			private TypeToken<K> theKeyType;
			private ModelValueInstantiator<SettableValue<ObservableConfigFormat<K>>> theKeyFormat;

			public Instantiator(Interpreted<K, V> interpreted) {
				super(interpreted.getConfigValue().instantiate(), interpreted.getValueType(), interpreted.getConfigPath(),
					interpreted.getFormat() == null ? null : interpreted.getFormat().instantiate(), interpreted.getFormatSet());
				theKeyType = interpreted.getKeyType();
				theKeyFormat = interpreted.getKeyFormat() == null ? null : interpreted.getKeyFormat().instantiate();
			}

			@Override
			public ObservableMap<K, V> create(ObservableConfigValueBuilder<V> config, ModelSetInstance msi)
				throws ModelInstantiationException {
				ObservableConfigMapBuilder<K, V> mapBuilder = config.asMap(theKeyType);
				if (theKeyFormat != null) {
					ObservableConfigFormat<K> keyFormat = theKeyFormat.get(msi).get();
					if (keyFormat != null)
						mapBuilder.withKeyFormat(keyFormat);
				}
				return mapBuilder.buildMap(null);
			}
		}
	}

	private void configureFormats(QonfigInterpreterCore.Builder interpreter) {
		// Text formats
		interpreter.createWith(StandardTextFormat.STANDARD_TEXT_FORMAT, ModelValueElement.CompiledSynth.class,
			ExElement.creator(StandardTextFormat::new));
		// TODO custom-text-format, int-format, long-format
		interpreter.createWith(DoubleFormat.DOUBLE_FORMAT, ModelValueElement.CompiledSynth.class, ExElement.creator(DoubleFormat::new));
		interpreter.createWith(FileFormat.FILE_FORMAT, ModelValueElement.CompiledSynth.class, ExElement.creator(FileFormat::new));
		interpreter.createWith(DateFormat.INSTANT_FORMAT, ModelValueElement.CompiledSynth.class, ExElement.creator(DateFormat::new));
		interpreter.createWith(RegexStringFormat.REGEX_FORMAT_STRING, ModelValueElement.CompiledSynth.class,
			ExElement.creator(RegexStringFormat::new));
		// TODO regex-format

		// Text format validation
		// TODO regex-validation
		interpreter.createWith(FilterValidation.FILTER_VALIDATION, FormatValidation.Def.class,
			ExElement.creator(FilterValidation.Def::new));

		// File sources
		// TODO native-file-source, sftp-file-source
		interpreter.createWith(ExArchiveEnabledFileSource.ARCHIVE_ENABLED_FILE_SOURCE, ModelValueElement.CompiledSynth.class,
			ExElement.creator(ExArchiveEnabledFileSource::new));
		interpreter.createWith(ZipCompression.ZIP_ARCHIVAL, ModelValueElement.CompiledSynth.class, ExElement.creator(ZipCompression::new));
		interpreter.createWith(GZCompression.GZ_ARCHIVAL, ModelValueElement.CompiledSynth.class, ExElement.creator(GZCompression::new));
		interpreter.createWith(TarArchival.TAR_ARCHIVAL, ModelValueElement.CompiledSynth.class, ExElement.creator(TarArchival::new));

		// ObservableConfig formats
		interpreter.createWith(TextConfigFormat.TEXT_CONFIG_FORMAT, ModelValueElement.CompiledSynth.class,
			ExElement.creator(TextConfigFormat::new));
		interpreter.createWith(EntityConfigFormat.ENTITY_CONFIG_FORMAT, ModelValueElement.CompiledSynth.class,
			ExElement.creator(EntityConfigFormat::new));
		interpreter.createWith(EntityConfigField.ENTITY_CONFIG_FIELD, EntityConfigField.class, ExAddOn.creator(EntityConfigField::new));
	}

	@ExElementTraceable(toolkit = CONFIG,
		qonfigType = ExArchiveEnabledFileSource.ARCHIVE_ENABLED_FILE_SOURCE,
		interpretation = ExArchiveEnabledFileSource.Interpreted.class)
	static class ExArchiveEnabledFileSource extends
	ModelValueElement.Def.SingleTyped<SettableValue<?>, ModelValueElement<SettableValue<?>, SettableValue<ArchiveEnabledFileSource>>>
	implements
	ModelValueElement.CompiledSynth<SettableValue<?>, ModelValueElement<SettableValue<?>, SettableValue<ArchiveEnabledFileSource>>> {
		public static final String ARCHIVE_ENABLED_FILE_SOURCE = "archive-enabled-file-source";

		private CompiledExpression theWrapped;
		private CompiledExpression theMaxArchiveDepth;
		private final List<ModelValueElement.CompiledSynth<SettableValue<?>, ModelValueElement<SettableValue<?>, SettableValue<ArchiveEnabledFileSource.FileArchival>>>> theArchiveMethods;

		public ExArchiveEnabledFileSource(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType, ModelTypes.Value);
			theArchiveMethods = new ArrayList<>();
		}

		@QonfigAttributeGetter("wrapped")
		public CompiledExpression getWrapped() {
			return theWrapped;
		}

		@QonfigAttributeGetter("max-archive-depth")
		public CompiledExpression getMaxArchiveDepth() {
			return theMaxArchiveDepth;
		}

		@QonfigChildGetter("archive-method")
		public List<ModelValueElement.CompiledSynth<SettableValue<?>, ModelValueElement<SettableValue<?>, SettableValue<ArchiveEnabledFileSource.FileArchival>>>> getArchiveMethods() {
			return Collections.unmodifiableList(theArchiveMethods);
		}

		@Override
		protected void doPrepare(ExpressoQIS session) throws QonfigInterpretationException {
			theWrapped = getAttributeExpression("wrapped", session);
			theMaxArchiveDepth = getAttributeExpression("max-archive-depth", session);
			syncChildren(ModelValueElement.CompiledSynth.class, theArchiveMethods, session.forChildren("archive-method"));
		}

		@Override
		public InterpretedSynth<SettableValue<?>, ?, ? extends ModelValueElement<SettableValue<?>, SettableValue<ArchiveEnabledFileSource>>> interpretValue(
			ExElement.Interpreted<?> parent) {
			return new Interpreted(this, parent);
		}

		static class Interpreted extends
		ModelValueElement.Def.SingleTyped.Interpreted<SettableValue<?>, SettableValue<ArchiveEnabledFileSource>, ModelValueElement<SettableValue<?>, SettableValue<ArchiveEnabledFileSource>>>
		implements
		ModelValueElement.InterpretedSynth<SettableValue<?>, SettableValue<ArchiveEnabledFileSource>, ModelValueElement<SettableValue<?>, SettableValue<ArchiveEnabledFileSource>>> {
			private InterpretedValueSynth<SettableValue<?>, SettableValue<FileDataSource>> theWrapped;
			private InterpretedValueSynth<SettableValue<?>, SettableValue<Integer>> theMaxArchiveDepth;
			private final List<ModelValueElement.InterpretedSynth<SettableValue<?>, SettableValue<ArchiveEnabledFileSource.FileArchival>, ?>> theArchiveMethods;

			Interpreted(ExArchiveEnabledFileSource definition, ExElement.Interpreted<?> parent) {
				super(definition, parent);
				theArchiveMethods = new ArrayList<>();
			}

			@Override
			public ExArchiveEnabledFileSource getDefinition() {
				return (ExArchiveEnabledFileSource) super.getDefinition();
			}

			@Override
			protected ModelInstanceType<SettableValue<?>, SettableValue<ArchiveEnabledFileSource>> getTargetType() {
				return ModelTypes.Value.forType(ArchiveEnabledFileSource.class);
			}

			public InterpretedValueSynth<SettableValue<?>, SettableValue<FileDataSource>> getWrapped() {
				return theWrapped;
			}

			public InterpretedValueSynth<SettableValue<?>, SettableValue<Integer>> getMaxArchiveDepth() {
				return theMaxArchiveDepth;
			}

			public List<ModelValueElement.InterpretedSynth<SettableValue<?>, SettableValue<ArchiveEnabledFileSource.FileArchival>, ?>> getArchiveMethods() {
				return Collections.unmodifiableList(theArchiveMethods);
			}

			@Override
			protected void doUpdate(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
				super.doUpdate(env);
				theWrapped = interpret(getDefinition().getWrapped(), ModelTypes.Value.forType(FileDataSource.class));
				theMaxArchiveDepth = interpret(getDefinition().getMaxArchiveDepth(), ModelTypes.Value.INT);
				syncChildren(getDefinition().getArchiveMethods(), theArchiveMethods, (def,
					vEnv) -> (ModelValueElement.InterpretedSynth<SettableValue<?>, SettableValue<ArchiveEnabledFileSource.FileArchival>, ?>) def
					.interpret(vEnv),
					(i, vEnv) -> i.updateValue(vEnv));
			}

			@Override
			public List<? extends InterpretedValueSynth<?, ?>> getComponents() {
				List<InterpretedValueSynth<?, ?>> components = new ArrayList<>();
				if (theWrapped != null)
					components.add(theWrapped);
				components.add(theMaxArchiveDepth);
				components.addAll(theArchiveMethods);
				return components;
			}

			@Override
			public ModelValueInstantiator<SettableValue<ArchiveEnabledFileSource>> instantiate() {
				return new Instantiator(this);
			}
		}

		static class Instantiator implements ModelValueInstantiator<SettableValue<ArchiveEnabledFileSource>> {
			private final ModelValueInstantiator<SettableValue<FileDataSource>> theWrapped;
			private final ModelValueInstantiator<SettableValue<Integer>> theMaxArchiveDepth;
			private final List<ModelValueInstantiator<SettableValue<ArchiveEnabledFileSource.FileArchival>>> theArchiveMethods;

			Instantiator(Interpreted interpreted) {
				theWrapped = interpreted.getWrapped() == null ? null : interpreted.getWrapped().instantiate();
				theMaxArchiveDepth = interpreted.getMaxArchiveDepth().instantiate();
				theArchiveMethods = QommonsUtils.filterMap(interpreted.getArchiveMethods(), null, am -> am.instantiate());
			}

			@Override
			public void instantiate() {
				if (theWrapped != null)
					theWrapped.instantiate();
				theMaxArchiveDepth.instantiate();
				for (ModelValueInstantiator<?> am : theArchiveMethods)
					am.instantiate();
			}

			@Override
			public SettableValue<ArchiveEnabledFileSource> get(ModelSetInstance models)
				throws ModelInstantiationException, IllegalStateException {
				SettableValue<FileDataSource> wrapped = theWrapped == null
					? SettableValue.of(FileDataSource.class, new NativeFileSource(), "Unmodifiable") : theWrapped.get(models);
				SettableValue<Integer> maxArchiveDepth = theMaxArchiveDepth.get(models);
				List<ArchiveEnabledFileSource.FileArchival> archiveMethods = new ArrayList<>(theArchiveMethods.size());
				for (ModelValueInstantiator<SettableValue<ArchiveEnabledFileSource.FileArchival>> am : theArchiveMethods)
					archiveMethods.add(am.get(models).get());
				return SettableValue.asSettable(wrapped.transform(ArchiveEnabledFileSource.class, tx -> tx.map(w -> {
					ArchiveEnabledFileSource aefs = new ArchiveEnabledFileSource(w)//
						.withArchival(archiveMethods);
					maxArchiveDepth.changes().takeUntil(wrapped.noInitChanges()).act(evt -> aefs.setMaxArchiveDepth(evt.getNewValue()));
					return aefs;
				})), __ -> "Unmodifiable");
			}

			@Override
			public SettableValue<ArchiveEnabledFileSource> forModelCopy(SettableValue<ArchiveEnabledFileSource> value,
				ModelSetInstance sourceModels, ModelSetInstance newModels) throws ModelInstantiationException {
				SettableValue<FileDataSource> srcWrapped = theWrapped == null
					? SettableValue.of(FileDataSource.class, new NativeFileSource(), "Unmodifiable") : theWrapped.get(sourceModels);
				SettableValue<FileDataSource> newWrapped = theWrapped == null ? srcWrapped
					: theWrapped.forModelCopy(srcWrapped, sourceModels, newModels);
				SettableValue<Integer> srcMAD = theMaxArchiveDepth.get(sourceModels);
				SettableValue<Integer> newMAD = theMaxArchiveDepth.forModelCopy(srcMAD, sourceModels, newModels);
				List<ArchiveEnabledFileSource.FileArchival> archiveMethods = new ArrayList<>(theArchiveMethods.size());
				boolean diff = srcWrapped != newWrapped || srcMAD != newMAD;
				for (ModelValueInstantiator<SettableValue<ArchiveEnabledFileSource.FileArchival>> am : theArchiveMethods) {
					SettableValue<ArchiveEnabledFileSource.FileArchival> srcAM = am.get(sourceModels);
					SettableValue<ArchiveEnabledFileSource.FileArchival> newAM = am.forModelCopy(srcAM, sourceModels, newModels);
					diff |= srcAM != newAM;
					archiveMethods.add(newAM.get());
				}
				if (!diff)
					return value;
				return SettableValue.asSettable(newWrapped.transform(ArchiveEnabledFileSource.class, tx -> tx.map(w -> {
					ArchiveEnabledFileSource aefs = new ArchiveEnabledFileSource(w)//
						.withArchival(archiveMethods);
					newMAD.changes().takeUntil(newWrapped.noInitChanges()).act(evt -> aefs.setMaxArchiveDepth(evt.getNewValue()));
					return aefs;
				})), __ -> "Unmodifiable");
			}
		}
	}

	static class ZipCompression extends
	ModelValueElement.Def.SingleTyped<SettableValue<?>, ModelValueElement<SettableValue<?>, SettableValue<ArchiveEnabledFileSource.ZipCompression>>>
	implements
	ModelValueElement.CompiledSynth<SettableValue<?>, ModelValueElement<SettableValue<?>, SettableValue<ArchiveEnabledFileSource.ZipCompression>>> {
		public static final String ZIP_ARCHIVAL = "zip-archival";

		public ZipCompression(Def<?> parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType, ModelTypes.Value);
		}

		@Override
		protected void doPrepare(ExpressoQIS session) throws QonfigInterpretationException {
		}

		@Override
		public Interpreted interpretValue(ExElement.Interpreted<?> parent) {
			return new Interpreted(this, parent);
		}

		static class Interpreted extends
		ModelValueElement.Def.SingleTyped.Interpreted<SettableValue<?>, SettableValue<ArchiveEnabledFileSource.ZipCompression>, ModelValueElement<SettableValue<?>, SettableValue<ArchiveEnabledFileSource.ZipCompression>>>
		implements
		ModelValueElement.InterpretedSynth<SettableValue<?>, SettableValue<ArchiveEnabledFileSource.ZipCompression>, ModelValueElement<SettableValue<?>, SettableValue<ArchiveEnabledFileSource.ZipCompression>>> {

			Interpreted(ZipCompression definition, ExElement.Interpreted<?> parent) {
				super(definition, parent);
			}

			@Override
			public List<? extends InterpretedValueSynth<?, ?>> getComponents() {
				return Collections.emptyList();
			}

			@Override
			public ModelValueInstantiator<SettableValue<ArchiveEnabledFileSource.ZipCompression>> instantiate() {
				return new Instantiator();
			}
		}

		static class Instantiator implements ModelValueInstantiator<SettableValue<ArchiveEnabledFileSource.ZipCompression>> {
			@Override
			public void instantiate() {

			}

			@Override
			public SettableValue<ArchiveEnabledFileSource.ZipCompression> get(ModelSetInstance models)
				throws ModelInstantiationException, IllegalStateException {
				return SettableValue.of(ArchiveEnabledFileSource.ZipCompression.class, new ArchiveEnabledFileSource.ZipCompression(),
					"Unmodifiable");
			}

			@Override
			public SettableValue<ArchiveEnabledFileSource.ZipCompression> forModelCopy(
				SettableValue<ArchiveEnabledFileSource.ZipCompression> value, ModelSetInstance sourceModels, ModelSetInstance newModels)
					throws ModelInstantiationException {
				return value;
			}
		}
	}

	static class GZCompression extends
	ModelValueElement.Def.SingleTyped<SettableValue<?>, ModelValueElement<SettableValue<?>, SettableValue<ArchiveEnabledFileSource.GZipCompression>>>
	implements
	ModelValueElement.CompiledSynth<SettableValue<?>, ModelValueElement<SettableValue<?>, SettableValue<ArchiveEnabledFileSource.GZipCompression>>> {
		public static final String GZ_ARCHIVAL = "gz-archival";

		public GZCompression(Def<?> parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType, ModelTypes.Value);
		}

		@Override
		protected void doPrepare(ExpressoQIS session) throws QonfigInterpretationException {
		}

		@Override
		public Interpreted interpretValue(ExElement.Interpreted<?> parent) {
			return new Interpreted(this, parent);
		}

		static class Interpreted extends
		ModelValueElement.Def.SingleTyped.Interpreted<SettableValue<?>, SettableValue<ArchiveEnabledFileSource.GZipCompression>, ModelValueElement<SettableValue<?>, SettableValue<ArchiveEnabledFileSource.GZipCompression>>>
		implements
		ModelValueElement.InterpretedSynth<SettableValue<?>, SettableValue<ArchiveEnabledFileSource.GZipCompression>, ModelValueElement<SettableValue<?>, SettableValue<ArchiveEnabledFileSource.GZipCompression>>> {

			Interpreted(GZCompression definition, ExElement.Interpreted<?> parent) {
				super(definition, parent);
			}

			@Override
			public List<? extends InterpretedValueSynth<?, ?>> getComponents() {
				return Collections.emptyList();
			}

			@Override
			public ModelValueInstantiator<SettableValue<ArchiveEnabledFileSource.GZipCompression>> instantiate() {
				return new Instantiator();
			}
		}

		static class Instantiator implements ModelValueInstantiator<SettableValue<ArchiveEnabledFileSource.GZipCompression>> {
			@Override
			public void instantiate() {

			}

			@Override
			public SettableValue<ArchiveEnabledFileSource.GZipCompression> get(ModelSetInstance models)
				throws ModelInstantiationException, IllegalStateException {
				return SettableValue.of(ArchiveEnabledFileSource.GZipCompression.class, new ArchiveEnabledFileSource.GZipCompression(),
					"Unmodifiable");
			}

			@Override
			public SettableValue<ArchiveEnabledFileSource.GZipCompression> forModelCopy(
				SettableValue<ArchiveEnabledFileSource.GZipCompression> value, ModelSetInstance sourceModels, ModelSetInstance newModels)
					throws ModelInstantiationException {
				return value;
			}
		}
	}

	static class TarArchival extends
	ModelValueElement.Def.SingleTyped<SettableValue<?>, ModelValueElement<SettableValue<?>, SettableValue<ArchiveEnabledFileSource.TarArchival>>>
	implements
	ModelValueElement.CompiledSynth<SettableValue<?>, ModelValueElement<SettableValue<?>, SettableValue<ArchiveEnabledFileSource.TarArchival>>> {
		public static final String TAR_ARCHIVAL = "tar-archival";

		public TarArchival(Def<?> parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType, ModelTypes.Value);
		}

		@Override
		protected void doPrepare(ExpressoQIS session) throws QonfigInterpretationException {
		}

		@Override
		public Interpreted interpretValue(ExElement.Interpreted<?> parent) {
			return new Interpreted(this, parent);
		}

		static class Interpreted extends
		ModelValueElement.Def.SingleTyped.Interpreted<SettableValue<?>, SettableValue<ArchiveEnabledFileSource.TarArchival>, ModelValueElement<SettableValue<?>, SettableValue<ArchiveEnabledFileSource.TarArchival>>>
		implements
		ModelValueElement.InterpretedSynth<SettableValue<?>, SettableValue<ArchiveEnabledFileSource.TarArchival>, ModelValueElement<SettableValue<?>, SettableValue<ArchiveEnabledFileSource.TarArchival>>> {

			Interpreted(TarArchival definition, ExElement.Interpreted<?> parent) {
				super(definition, parent);
			}

			@Override
			public List<? extends InterpretedValueSynth<?, ?>> getComponents() {
				return Collections.emptyList();
			}

			@Override
			public ModelValueInstantiator<SettableValue<ArchiveEnabledFileSource.TarArchival>> instantiate() {
				return new Instantiator();
			}
		}

		static class Instantiator implements ModelValueInstantiator<SettableValue<ArchiveEnabledFileSource.TarArchival>> {
			@Override
			public void instantiate() {

			}

			@Override
			public SettableValue<ArchiveEnabledFileSource.TarArchival> get(ModelSetInstance models)
				throws ModelInstantiationException, IllegalStateException {
				return SettableValue.of(ArchiveEnabledFileSource.TarArchival.class, new ArchiveEnabledFileSource.TarArchival(),
					"Unmodifiable");
			}

			@Override
			public SettableValue<ArchiveEnabledFileSource.TarArchival> forModelCopy(
				SettableValue<ArchiveEnabledFileSource.TarArchival> value, ModelSetInstance sourceModels, ModelSetInstance newModels)
					throws ModelInstantiationException {
				return value;
			}
		}
	}

	@ExElementTraceable(toolkit = CONFIG, qonfigType = AbstractFormat.FORMAT, interpretation = AbstractFormat.Interpreted.class)
	static abstract class AbstractFormat<T>
	extends ModelValueElement.Def.SingleTyped<SettableValue<?>, ModelValueElement<SettableValue<?>, SettableValue<Format<T>>>>
	implements ModelValueElement.CompiledSynth<SettableValue<?>, ModelValueElement<SettableValue<?>, SettableValue<Format<T>>>> {
		public static final String FORMAT = "format";

		private final List<FormatValidation.Def<?>> theValidation;

		protected AbstractFormat(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType, ModelTypes.Value);
			theValidation = new ArrayList<>();
		}

		@QonfigChildGetter("validate")
		public List<FormatValidation.Def<?>> getValidation() {
			return Collections.unmodifiableList(theValidation);
		}

		@Override
		protected void doPrepare(ExpressoQIS session) throws QonfigInterpretationException {
			syncChildren(FormatValidation.Def.class, theValidation, session.forChildren("validate"));
		}

		@Override
		public abstract Interpreted<T> interpretValue(ExElement.Interpreted<?> parent);

		public static abstract class Interpreted<T> extends
		ModelValueElement.Def.SingleTyped.Interpreted<SettableValue<?>, SettableValue<Format<T>>, ModelValueElement<SettableValue<?>, SettableValue<Format<T>>>>
		implements
		ModelValueElement.InterpretedSynth<SettableValue<?>, SettableValue<Format<T>>, ModelValueElement<SettableValue<?>, SettableValue<Format<T>>>> {
			private final List<FormatValidation.Interpreted<T, ?>> theValidation;

			protected Interpreted(AbstractFormat<T> definition, ExElement.Interpreted<?> parent) {
				super(definition, parent);
				theValidation = new ArrayList<>();
			}

			@Override
			public AbstractFormat<T> getDefinition() {
				return (AbstractFormat<T>) super.getDefinition();
			}

			@Override
			protected ModelInstanceType<SettableValue<?>, SettableValue<Format<T>>> getTargetType() {
				return ModelTypes.Value.forType(TypeTokens.get().keyFor(Format.class).<Format<T>> parameterized(getValueType()));
			}

			public List<FormatValidation.Interpreted<T, ?>> getValidation() {
				return Collections.unmodifiableList(theValidation);
			}

			protected abstract TypeToken<T> getValueType();

			@Override
			protected void doUpdate(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
				super.doUpdate(env);

				if (!theValidation.isEmpty()) {
					TypeToken<T> valueType = getValueType();
					syncChildren(getDefinition().getValidation(), theValidation,
						def -> (FormatValidation.Interpreted<T, ?>) def.interpret(this), (i, vEnv) -> i.updateFormat(vEnv, valueType));
				}
			}

			@Override
			public List<? extends InterpretedValueSynth<?, ?>> getComponents() {
				return BetterList.of(theValidation.stream().flatMap(v -> v.getComponents().stream()));
			}

			@Override
			public abstract Instantiator<T> instantiate();
		}

		public static abstract class Instantiator<T> implements ModelValueInstantiator<SettableValue<Format<T>>> {
			private final TypeToken<Format<T>> theFormatType;
			private final List<FormatValidation.Instantiator<T, ?>> theValidation;

			protected Instantiator(Interpreted<T> interpreted) {
				theFormatType = TypeTokens.get().keyFor(Format.class).<Format<T>> parameterized(interpreted.getValueType());
				theValidation = new ArrayList<>(interpreted.getValidation().size());
				for (FormatValidation.Interpreted<T, ?> validation : interpreted.getValidation()) {
					FormatValidation.Instantiator<T, ?> v = validation.create();
					v.update(validation, null);
					theValidation.add(v);
				}
			}

			protected TypeToken<Format<T>> getFormatType() {
				return theFormatType;
			}

			@Override
			public void instantiate() {
				for (FormatValidation.Instantiator<T, ?> validation : theValidation)
					validation.instantiated();
			}

			@Override
			public SettableValue<Format<T>> get(ModelSetInstance models) throws ModelInstantiationException, IllegalStateException {
				List<FormatValidation<T>> validation = new ArrayList<>(theValidation.size());
				for (FormatValidation.Instantiator<T, ?> v : theValidation)
					validation.add(v.get(models));
				SettableValue<Format<T>> sourceFormat = createFormat(models);
				if (validation.isEmpty())
					return sourceFormat;
				return new ValidatedFormatValue<>(theFormatType, sourceFormat, validation);
			}

			protected abstract SettableValue<Format<T>> createFormat(ModelSetInstance models)
				throws ModelInstantiationException, IllegalStateException;

			@Override
			public SettableValue<Format<T>> forModelCopy(SettableValue<Format<T>> value, ModelSetInstance sourceModels,
				ModelSetInstance newModels) throws ModelInstantiationException {
				if (!(value instanceof ValidatedFormatValue))
					return copyFormat(value, sourceModels, newModels);
				ValidatedFormatValue<T> validatedValue = (ValidatedFormatValue<T>) value;
				SettableValue<Format<T>> newSource = copyFormat(validatedValue.getSourceFormat(), sourceModels, newModels);
				List<FormatValidation<T>> newValidation = null;
				for (int i = 0; i < theValidation.size(); i++) {
					FormatValidation<T> validation = ((FormatValidation.Instantiator<T, FormatValidation<T>>) theValidation.get(i))
						.forModelCopy(validatedValue.getValidation().get(i), sourceModels, newModels);
					if (newValidation == null && validation != validatedValue.getValidation().get(i)) {
						newValidation = new ArrayList<>(theValidation.size());
						for (int j = 0; j < i; j++)
							newValidation.add(validatedValue.getValidation().get(j));
					}
					if (newValidation != null)
						newValidation.add(validatedValue.getValidation().get(i));
				}
				if (newSource != validatedValue.getSourceFormat() && newValidation == null)
					newValidation = validatedValue.getValidation();
				if (newValidation != null)
					return new ValidatedFormatValue<>(theFormatType, newSource, newValidation);
				return value;
			}

			protected abstract SettableValue<Format<T>> copyFormat(SettableValue<Format<T>> format, ModelSetInstance sourceModels,
				ModelSetInstance newModels) throws ModelInstantiationException;
		}

		private static class ValidatedFormatValue<T> implements SettableValue<Format<T>> {
			private final TypeToken<Format<T>> theType;
			private final SettableValue<Format<T>> theSourceFormat;
			private final List<FormatValidation<T>> theValidation;

			private long theStamp;
			private ValidatedFormat<T> thePreviousFormat;
			private ValidatedFormat<T> theValidatedFormat;

			ValidatedFormatValue(TypeToken<Format<T>> type, SettableValue<Format<T>> sourceFormat, List<FormatValidation<T>> validation) {
				theType = type;
				theSourceFormat = sourceFormat;
				theValidation = validation;
				theStamp = -1;
			}

			SettableValue<Format<T>> getSourceFormat() {
				return theSourceFormat;
			}

			List<FormatValidation<T>> getValidation() {
				return theValidation;
			}

			@Override
			public ValidatedFormat<T> get() {
				if (theStamp != theSourceFormat.getStamp()) {
					thePreviousFormat = theValidatedFormat;
					theStamp = theSourceFormat.getStamp();
					Format<T> f = theSourceFormat.get();
					theValidatedFormat = f == null ? null : new ValidatedFormat<>(f, theValidation);
				}
				return theValidatedFormat;
			}

			@Override
			public Observable<ObservableValueEvent<Format<T>>> noInitChanges() {
				Observable<ObservableValueEvent<Format<T>>> sourceChanges = theSourceFormat.noInitChanges();
				return new Observable<ObservableValueEvent<Format<T>>>() {
					@Override
					public CoreId getCoreId() {
						return theSourceFormat.getCoreId();
					}

					@Override
					public Object getIdentity() {
						return Identifiable.wrap(sourceChanges.getIdentity(), "validated", theValidation.toArray());
					}

					@Override
					public ThreadConstraint getThreadConstraint() {
						return sourceChanges.getThreadConstraint();
					}

					@Override
					public boolean isEventing() {
						return sourceChanges.isEventing();
					}

					@Override
					public Subscription subscribe(Observer<? super ObservableValueEvent<Format<T>>> observer) {
						return sourceChanges.subscribe(new Observer<ObservableValueEvent<Format<T>>>() {
							@Override
							public <V extends ObservableValueEvent<Format<T>>> void onNext(V value) {
								ValidatedFormat<T> newFormat = get();
								ValidatedFormat<T> old = value.isInitial() ? newFormat : thePreviousFormat;
								ObservableValueEvent<Format<T>> evt;
								if (value.isInitial())
									evt = createInitialEvent(newFormat, value);
								else
									evt = createChangeEvent(old, old, value);
								try (Transaction t = evt.use()) {
									observer.onNext(evt);
								}
							}

							@Override
							public void onCompleted(Causable cause) {
								observer.onCompleted(cause);
							}
						});
					}

					@Override
					public boolean isSafe() {
						return sourceChanges.isSafe();
					}

					@Override
					public Transaction lock() {
						return sourceChanges.lock();
					}

					@Override
					public Transaction tryLock() {
						return sourceChanges.tryLock();
					}
				};
			}

			@Override
			public TypeToken<Format<T>> getType() {
				return theType;
			}

			@Override
			public Object getIdentity() {
				return Identifiable.wrap(theSourceFormat.getIdentity(), "validated", theValidation.toArray());
			}

			@Override
			public Transaction lock(boolean write, Object cause) {
				return theSourceFormat.lock(write, cause);
			}

			@Override
			public Transaction tryLock(boolean write, Object cause) {
				return theSourceFormat.tryLock(write, cause);
			}

			@Override
			public long getStamp() {
				return theSourceFormat.getStamp();
			}

			@Override
			public boolean isLockSupported() {
				return theSourceFormat.isLockSupported();
			}

			@Override
			public <V extends Format<T>> Format<T> set(V value, Object cause)
				throws IllegalArgumentException, UnsupportedOperationException {
				throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
			}

			@Override
			public <V extends Format<T>> String isAcceptable(V value) {
				return StdMsg.UNSUPPORTED_OPERATION;
			}

			@Override
			public ObservableValue<String> isEnabled() {
				return SettableValue.ALWAYS_DISABLED;
			}
		}

		private static class ValidatedFormat<T> implements Format<T> {
			private final Format<T> theSourceFormat;
			private final List<FormatValidation<T>> theValidation;

			ValidatedFormat(Format<T> sourceFormat, List<FormatValidation<T>> validation) {
				theSourceFormat = sourceFormat;
				theValidation = validation;
			}

			@Override
			public void append(StringBuilder text, T value) {
				theSourceFormat.append(text, value);
			}

			@Override
			public T parse(CharSequence text) throws ParseException {
				T parsed = theSourceFormat.parse(text);
				for (FormatValidation<T> validation : theValidation) {
					String error = validation.test(parsed, text);
					if (error != null)
						throw new ParseException(error, 0);
				}
				return parsed;
			}
		}
	}

	@ExElementTraceable(toolkit = CONFIG, qonfigType = FileFormat.FILE_FORMAT, interpretation = FileFormat.Interpreted.class)
	static class FileFormat extends AbstractFormat<BetterFile> {
		public static final String FILE_FORMAT = "file-format";

		private CompiledExpression theFileSource;
		private CompiledExpression theWorkingDir;
		private boolean isAllowEmpty;

		public FileFormat(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType);
		}

		@QonfigAttributeGetter("file-source")
		public CompiledExpression getFileSource() {
			return theFileSource;
		}

		@QonfigAttributeGetter("working-dir")
		public CompiledExpression getWorkingDir() {
			return theWorkingDir;
		}

		@QonfigAttributeGetter("allow-empty")
		public boolean isAllowEmpty() {
			return isAllowEmpty;
		}

		@Override
		protected void doPrepare(ExpressoQIS session) throws QonfigInterpretationException {
			super.doPrepare(session);
			theFileSource = getAttributeExpression("file-source", session);
			theWorkingDir = getAttributeExpression("working-dir", session);
		}

		@Override
		public Interpreted interpretValue(ExElement.Interpreted<?> parent) {
			return new Interpreted(this, parent);
		}

		static class Interpreted extends AbstractFormat.Interpreted<BetterFile> {
			private InterpretedValueSynth<SettableValue<?>, SettableValue<FileDataSource>> theFileSource;
			private InterpretedValueSynth<SettableValue<?>, SettableValue<BetterFile>> theWorkingDir;

			Interpreted(FileFormat definition, ExElement.Interpreted<?> parent) {
				super(definition, parent);
			}

			@Override
			public FileFormat getDefinition() {
				return (FileFormat) super.getDefinition();
			}

			public InterpretedValueSynth<SettableValue<?>, SettableValue<FileDataSource>> getFileSource() {
				return theFileSource;
			}

			public InterpretedValueSynth<SettableValue<?>, SettableValue<BetterFile>> getWorkingDir() {
				return theWorkingDir;
			}

			@Override
			protected TypeToken<BetterFile> getValueType() {
				return TypeTokens.get().of(BetterFile.class);
			}

			@Override
			protected void doUpdate(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
				super.doUpdate(env);
				theFileSource = interpret(getDefinition().getFileSource(), ModelTypes.Value.forType(FileDataSource.class));
				theWorkingDir = interpret(getDefinition().getWorkingDir(), ModelTypes.Value.forType(BetterFile.class));

			}

			@Override
			public List<? extends InterpretedValueSynth<?, ?>> getComponents() {
				List<InterpretedValueSynth<?, ?>> components = new ArrayList<>();
				if (theFileSource != null)
					components.add(theFileSource);
				components.add(theWorkingDir);
				return components;
			}

			@Override
			public Instantiator instantiate() {
				return new Instantiator(this);
			}
		}

		static class Instantiator extends AbstractFormat.Instantiator<BetterFile> {
			private final ModelValueInstantiator<SettableValue<FileDataSource>> theFileSource;
			private final ModelValueInstantiator<SettableValue<BetterFile>> theWorkingDir;
			private final boolean isAllowEmpty;

			Instantiator(Interpreted interpreted) {
				super(interpreted);
				theFileSource = interpreted.getFileSource() == null ? null : interpreted.getFileSource().instantiate();
				theWorkingDir = interpreted.getWorkingDir() == null ? null : interpreted.getWorkingDir().instantiate();
				isAllowEmpty = interpreted.getDefinition().isAllowEmpty();
			}

			@Override
			public void instantiate() {
				super.instantiate();
				if (theFileSource != null)
					theFileSource.instantiate();
				if (theWorkingDir != null)
					theWorkingDir.instantiate();
			}

			@Override
			protected SettableValue<Format<BetterFile>> createFormat(ModelSetInstance models)
				throws ModelInstantiationException, IllegalStateException {
				SettableValue<FileDataSource> fileSource = theFileSource == null
					? SettableValue.of(FileDataSource.class, new NativeFileSource(), "Unmodifiable") : theFileSource.get(models);
				SettableValue<BetterFile> workingDir;
				if (theWorkingDir != null)
					workingDir = theWorkingDir.get(models);
				else
					workingDir = SettableValue.asSettable(
						fileSource.map(BetterFile.class, fs -> BetterFile.at(fs, System.getProperty("user.dir"))), __ -> "Unmodifiable");

				return SettableValue.asSettable(
					fileSource.transform(TypeTokens.get().keyFor(Format.class).<Format<BetterFile>> parameterized(BetterFile.class),
						tx -> tx.combineWith(workingDir)//
						.combine((fs, wd) -> new BetterFile.FileFormat(fs, wd, isAllowEmpty))),
					__ -> "Unmodifiable");
			}

			@Override
			protected SettableValue<Format<BetterFile>> copyFormat(SettableValue<Format<BetterFile>> format, ModelSetInstance sourceModels,
				ModelSetInstance newModels) throws ModelInstantiationException {
				SettableValue<FileDataSource> srcFS = theFileSource == null
					? SettableValue.of(FileDataSource.class, new NativeFileSource(), "Unmodifiable") : theFileSource.get(sourceModels);
				SettableValue<FileDataSource> newFS = theFileSource == null ? srcFS
					: theFileSource.forModelCopy(srcFS, sourceModels, newModels);
				SettableValue<BetterFile> srcWD, newWD;
				if (theWorkingDir != null) {
					srcWD = theWorkingDir.get(sourceModels);
					newWD = theWorkingDir.forModelCopy(srcWD, sourceModels, newModels);
				} else {
					srcWD = SettableValue.asSettable(srcFS.map(BetterFile.class, fs -> BetterFile.at(fs, System.getProperty("user.dir"))),
						__ -> "Unmodifiable");
					if (newFS == srcFS)
						newWD = srcWD;
					else
						newWD = SettableValue.asSettable(
							newFS.map(BetterFile.class, fs -> BetterFile.at(fs, System.getProperty("user.dir"))), __ -> "Unmodifiable");
				}
				if (srcFS == newFS && srcWD == newWD)
					return format;

				return SettableValue.asSettable(newFS.transform(
					TypeTokens.get().keyFor(Format.class).<Format<BetterFile>> parameterized(BetterFile.class), tx -> tx.combineWith(newWD)//
					.combine((fs, wd) -> new BetterFile.FileFormat(fs, wd, isAllowEmpty))),
					__ -> "Unmodifiable");
			}
		}
	}

	@ExElementTraceable(toolkit = CONFIG, qonfigType = DoubleFormat.DOUBLE_FORMAT, interpretation = DoubleFormat.Interpreted.class)
	static class DoubleFormat extends AbstractFormat<Double> {
		public static final String DOUBLE_FORMAT = "double-format";

		private CompiledExpression theSignificantDigits;
		private String theUnit;
		private boolean isUnitRequired;
		private boolean isMetricPrefixed;
		private boolean isMetricPrefixedP2;
		private final List<Prefix> thePrefixes;
		private final Map<String, Double> thePrefixMults;

		public DoubleFormat(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType);
			thePrefixes = new ArrayList<>();
			thePrefixMults = new LinkedHashMap<>();
		}

		@QonfigAttributeGetter("sig-digs")
		public CompiledExpression getSignificantDigits() {
			return theSignificantDigits;
		}

		@QonfigAttributeGetter("unit")
		public String getUnit() {
			return theUnit;
		}

		@QonfigAttributeGetter("unit-required")
		public boolean isUnitRequired() {
			return isUnitRequired;
		}

		@QonfigAttributeGetter("metric-prefixes")
		public boolean isMetricPrefixed() {
			return isMetricPrefixed;
		}

		@QonfigAttributeGetter("metric-prefixes-p2")
		public boolean isMetricPrefixedP2() {
			return isMetricPrefixedP2;
		}

		@QonfigChildGetter("prefix")
		public List<Prefix> getPrefixes() {
			return Collections.unmodifiableList(thePrefixes);
		}

		public Map<String, Double> getPrefixMults() {
			return Collections.unmodifiableMap(thePrefixMults);
		}

		@Override
		protected void doPrepare(ExpressoQIS session) throws QonfigInterpretationException {
			super.doPrepare(session);
			theSignificantDigits = getAttributeExpression("sig-digs", session);
			theUnit = session.getAttributeText("unit");
			isUnitRequired = session.getAttribute("unit-required", boolean.class);
			isMetricPrefixed = session.getAttribute("metric-prefixes", boolean.class);
			isMetricPrefixedP2 = session.getAttribute("metric-prefixes-p2", boolean.class);
			if (isMetricPrefixed && isMetricPrefixedP2)
				throw new QonfigInterpretationException("Only one of 'metrix-prefixes' and 'metric-prefixes-p2' may be specified",
					session.attributes().get("metric-prefixes-p2").getLocatedContent());
			syncChildren(Prefix.class, thePrefixes, session.forChildren("prefix"));
			thePrefixMults.clear();
			for (Prefix prefix : thePrefixes) {
				if (thePrefixMults.containsKey(prefix.getName()))
					prefix.reporting().error("Multiple prefix elements named '" + prefix.getName() + "'");
				else if (prefix.getExponent() != null)
					thePrefixMults.put(prefix.getName(), Math.pow(10, prefix.getExponent()));
				else
					thePrefixMults.put(prefix.getName(), prefix.getMultiplier());
			}
		}

		@Override
		public Interpreted interpretValue(ExElement.Interpreted<?> parent) {
			return new Interpreted(this, parent);
		}

		static class Interpreted extends AbstractFormat.Interpreted<Double> {
			private InterpretedValueSynth<SettableValue<?>, SettableValue<Integer>> theSignificantDigits;

			Interpreted(DoubleFormat definition, ExElement.Interpreted<?> parent) {
				super(definition, parent);
			}

			@Override
			public DoubleFormat getDefinition() {
				return (DoubleFormat) super.getDefinition();
			}

			public InterpretedValueSynth<SettableValue<?>, SettableValue<Integer>> getSignificantDigits() {
				return theSignificantDigits;
			}

			@Override
			protected TypeToken<Double> getValueType() {
				return TypeTokens.get().DOUBLE;
			}

			@Override
			protected void doUpdate(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
				super.doUpdate(env);
				theSignificantDigits = interpret(getDefinition().getSignificantDigits(), ModelTypes.Value.INT);
			}

			@Override
			public List<? extends InterpretedValueSynth<?, ?>> getComponents() {
				return Collections.emptyList();
			}

			@Override
			public Instantiator instantiate() {
				return new Instantiator(this);
			}
		}

		static class Instantiator extends AbstractFormat.Instantiator<Double> {
			private ModelValueInstantiator<SettableValue<Integer>> theSignificantDigits;
			private String theUnit;
			private boolean isUnitRequired;
			private boolean isMetricPrefixed;
			private boolean isMetricPrefixedP2;
			private Map<String, Double> thePrefixMults;

			public Instantiator(Interpreted interpreted) {
				super(interpreted);
				theSignificantDigits = interpreted.getSignificantDigits().instantiate();
				theUnit = interpreted.getDefinition().getUnit();
				isUnitRequired = interpreted.getDefinition().isUnitRequired();
				isMetricPrefixed = interpreted.getDefinition().isMetricPrefixed();
				isMetricPrefixedP2 = interpreted.getDefinition().isMetricPrefixedP2();
				thePrefixMults = QommonsUtils.unmodifiableCopy(interpreted.getDefinition().getPrefixMults());
			}

			@Override
			public void instantiate() {
				super.instantiate();
				theSignificantDigits.instantiate();
			}

			@Override
			protected SettableValue<Format<Double>> createFormat(ModelSetInstance models)
				throws ModelInstantiationException, IllegalStateException {
				SettableValue<Integer> sigDigs = theSignificantDigits.get(models);
				return createFormat(sigDigs);
			}

			private SettableValue<Format<Double>> createFormat(SettableValue<Integer> sigDigs) {
				return SettableValue
					.asSettable(sigDigs.map(TypeTokens.get().keyFor(Format.class).<Format<Double>> parameterized(double.class), sd -> {
						Format.SuperDoubleFormatBuilder builder = Format.doubleFormat(sd);
						builder.withUnit(theUnit, isUnitRequired);
						if (isMetricPrefixed)
							builder.withMetricPrefixes();
						else if (isMetricPrefixedP2)
							builder.withMetricPrefixesPower2();
						for (Map.Entry<String, Double> prefix : thePrefixMults.entrySet())
							builder.withPrefix(prefix.getKey(), prefix.getValue());
						return builder.build();
					}), __ -> "Unmodifiable");
			}

			@Override
			protected SettableValue<Format<Double>> copyFormat(SettableValue<Format<Double>> format, ModelSetInstance sourceModels,
				ModelSetInstance newModels) throws ModelInstantiationException {
				SettableValue<Integer> srcSigDigs = theSignificantDigits.get(sourceModels);
				SettableValue<Integer> newSigDigs = theSignificantDigits.forModelCopy(srcSigDigs, sourceModels, newModels);
				if (newSigDigs == srcSigDigs)
					return format;
				else
					return createFormat(newSigDigs);
			}
		}
	}

	@ExElementTraceable(toolkit = CONFIG, qonfigType = Prefix.PREFIX)
	static class Prefix extends ExElement.Def.Abstract<ExElement.Void> {
		public static final String PREFIX = "prefix";

		private String theName;
		private Integer theExponent;
		private Double theMultiplier;

		public Prefix(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType);
		}

		public String getName() {
			return theName;
		}

		public Integer getExponent() {
			return theExponent;
		}

		public Double getMultiplier() {
			return theMultiplier;
		}

		@Override
		protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
			super.doUpdate(session);
			theName = session.getAttributeText("name");
			String str = session.getAttributeText("exp");
			if (str != null) {
				LocatedPositionedContent mult = session.attributes().get("multiplier").getLocatedContent();
				if (mult != null)
					throw new QonfigInterpretationException("Only one of 'exp', 'multiplier' may be specified", mult.getPosition(0),
						mult.length());
				theExponent = Integer.parseInt(str);
				theMultiplier = null;
			} else {
				str = session.getAttributeText("multiplier");
				if (str == null)
					throw new QonfigInterpretationException("One of 'exp', 'multiplier' may be specified",
						session.getElement().getPositionInFile(), 0);
				theMultiplier = Double.parseDouble(str);
				theExponent = null;
			}
		}
	}

	@ExElementTraceable(toolkit = CONFIG, qonfigType = DateFormat.INSTANT_FORMAT, interpretation = DateFormat.Interpreted.class)
	static class DateFormat extends AbstractFormat<Instant> {
		public static final String INSTANT_FORMAT = "instant-format";

		private String theDayFormat;
		private TimeZone theTimeZone;
		private TimeUtils.DateElementType theMaxResolution;
		private boolean isFormat24H;
		private TimeUtils.RelativeInstantEvaluation theRelativeEvaluation;
		private CompiledExpression theRelativeTo;

		public DateFormat(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType);
		}

		@QonfigAttributeGetter("day-format")
		public String getDayFormat() {
			return theDayFormat;
		}

		@QonfigAttributeGetter("time-zone")
		public TimeZone getTimeZone() {
			return theTimeZone;
		}

		@QonfigAttributeGetter("max-resolution")
		public TimeUtils.DateElementType getMaxResolution() {
			return theMaxResolution;
		}

		@QonfigAttributeGetter("format-24h")
		public boolean isFormat24H() {
			return isFormat24H;
		}

		@QonfigAttributeGetter("relative-evaluation")
		public TimeUtils.RelativeInstantEvaluation getRelativeEvaluation() {
			return theRelativeEvaluation;
		}

		@QonfigAttributeGetter("relative-to")
		public CompiledExpression getRelativeTo() {
			return theRelativeTo;
		}

		@Override
		protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
			super.doUpdate(session);

			theDayFormat = session.getAttributeText("day-format");
			String tz = session.getAttributeText("time-zone");
			theTimeZone = tz == null ? TimeZone.getDefault() : TimeZone.getTimeZone(tz);
			// Above method returns UTC if the string isn't recognized. But we want to tell the user.
			if (tz != null && theTimeZone.getRawOffset() == 0 && !"GMT".equalsIgnoreCase(tz) && !"UTC".equalsIgnoreCase(tz)
				&& !ArrayUtils.contains(TimeZone.getAvailableIDs(), tz)) {
				reporting().error("Unrecognized time zone: " + tz);
				theTimeZone = TimeZone.getDefault();
			}
			try {
				theMaxResolution = TimeUtils.DateElementType.parse(session.getAttributeText("max-resolution"));
				switch (theMaxResolution) {
				case AmPm:
				case TimeZone:
					reporting().error("Invalid max-resolution: " + theMaxResolution);
					theMaxResolution = TimeUtils.DateElementType.Second;
					break;
				case Weekday:
					theMaxResolution = TimeUtils.DateElementType.Day;
					break;
				default:
					// It's fine
				}
			} catch (IllegalArgumentException e) {
				reporting().error("Unrecognized max-resolution: " + session.getAttributeText("max-resolution"), e);
				theMaxResolution = TimeUtils.DateElementType.Second;
			}

			isFormat24H = session.getAttribute("format-24h", boolean.class);
			try {
				theRelativeEvaluation = TimeUtils.RelativeInstantEvaluation.parse(session.getAttributeText("relative-evaluation"));
			} catch (IllegalArgumentException e) {
				reporting().error("Unrecognized max-resolution: " + session.getAttributeText("relative-evaluation"), e);
				theRelativeEvaluation = TimeUtils.RelativeInstantEvaluation.Closest;
			}

			theRelativeTo = getAttributeExpression("relative-to", session);
		}

		@Override
		public Interpreted interpretValue(ExElement.Interpreted<?> parent) {
			return new Interpreted(this, parent);
		}

		static class Interpreted extends AbstractFormat.Interpreted<Instant> {
			private InterpretedValueSynth<SettableValue<?>, SettableValue<Instant>> theRelativeTo;

			Interpreted(DateFormat definition, ExElement.Interpreted<?> parent) {
				super(definition, parent);
			}

			@Override
			public DateFormat getDefinition() {
				return (DateFormat) super.getDefinition();
			}

			public InterpretedValueSynth<SettableValue<?>, SettableValue<Instant>> getRelativeTo() {
				return theRelativeTo;
			}

			@Override
			protected TypeToken<Instant> getValueType() {
				return TypeTokens.get().of(Instant.class);
			}

			@Override
			protected void doUpdate(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
				super.doUpdate(env);
				theRelativeTo = interpret(getDefinition().getRelativeTo(), ModelTypes.Value.forType(Instant.class));
			}

			@Override
			public List<? extends InterpretedValueSynth<?, ?>> getComponents() {
				return theRelativeTo == null ? Collections.emptyList() : Collections.singletonList(theRelativeTo);
			}

			@Override
			public Instantiator instantiate() {
				return new Instantiator(this);
			}
		}

		static class Instantiator extends AbstractFormat.Instantiator<Instant> {
			private final String theDayFormat;
			private final TimeZone theTimeZone;
			private final TimeUtils.DateElementType theMaxResolution;
			private final boolean isFormat24H;
			private final TimeUtils.RelativeInstantEvaluation theRelativeEvaluation;
			private final ModelValueInstantiator<SettableValue<Instant>> theRelativeTo;

			Instantiator(Interpreted interpreted) {
				super(interpreted);
				theDayFormat = interpreted.getDefinition().getDayFormat();
				theTimeZone = interpreted.getDefinition().getTimeZone();
				theMaxResolution = interpreted.getDefinition().getMaxResolution();
				isFormat24H = interpreted.getDefinition().isFormat24H();
				theRelativeEvaluation = interpreted.getDefinition().getRelativeEvaluation();
				theRelativeTo = interpreted.getRelativeTo() == null ? null : interpreted.getRelativeTo().instantiate();
			}

			@Override
			public void instantiate() {
				super.instantiate();
				if (theRelativeTo != null)
					theRelativeTo.instantiate();
			}

			@Override
			protected SettableValue<Format<Instant>> createFormat(ModelSetInstance models)
				throws ModelInstantiationException, IllegalStateException {
				Supplier<Instant> relativeTo = theRelativeTo == null ? LambdaUtils.printableSupplier(Instant::now, () -> "now", null)
					: theRelativeTo.get(models);
				return SettableValue.of(TypeTokens.get().keyFor(Format.class).<Format<Instant>> parameterized(Instant.class), //
					SpinnerFormat.flexDate(relativeTo, theDayFormat, teo -> teo//
						.withTimeZone(theTimeZone)//
						.withMaxResolution(theMaxResolution)//
						.with24HourFormat(isFormat24H)//
						.withEvaluationType(theRelativeEvaluation)),
					"Unsettable");
			}

			@Override
			protected SettableValue<Format<Instant>> copyFormat(SettableValue<Format<Instant>> format, ModelSetInstance sourceModels,
				ModelSetInstance newModels) throws ModelInstantiationException {
				if (theRelativeTo == null)
					return format;
				SettableValue<Instant> sourceRT = theRelativeTo.get(sourceModels);
				SettableValue<Instant> newRT = theRelativeTo.forModelCopy(sourceRT, sourceModels, newModels);
				if (sourceRT == newRT)
					return format;
				return SettableValue.of(TypeTokens.get().keyFor(Format.class).<Format<Instant>> parameterized(Instant.class), //
					SpinnerFormat.flexDate(newRT, theDayFormat, teo -> teo//
						.withTimeZone(theTimeZone)//
						.withMaxResolution(theMaxResolution)//
						.with24HourFormat(isFormat24H)//
						.withEvaluationType(theRelativeEvaluation)),
					"Unsettable");
			}
		}
	}

	@ExElementTraceable(toolkit = CONFIG,
		qonfigType = RegexStringFormat.REGEX_FORMAT_STRING,
		interpretation = RegexStringFormat.Interpreted.class)
	static class RegexStringFormat extends AbstractFormat<String> {
		public static final String REGEX_FORMAT_STRING = "regex-format-string";

		public static final Format<String> INSTANCE = new Format<String>() {
			@Override
			public void append(StringBuilder text, String value) {
				if (value != null)
					text.append(value);
			}

			@Override
			public String parse(CharSequence text) throws ParseException {
				String str = text.toString();
				try {
					Pattern.compile(str);
				} catch (PatternSyntaxException e) {
					throw new ParseException(e.getMessage(), e.getIndex());
				}
				return str;
			}

			@Override
			public String toString() {
				return REGEX_FORMAT_STRING;
			}
		};

		public RegexStringFormat(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType);
		}

		@Override
		public Interpreted interpretValue(ExElement.Interpreted<?> parent) {
			return new Interpreted(this, parent);
		}

		static class Interpreted extends AbstractFormat.Interpreted<String> {
			Interpreted(RegexStringFormat definition, ExElement.Interpreted<?> parent) {
				super(definition, parent);
			}

			@Override
			public RegexStringFormat getDefinition() {
				return (RegexStringFormat) super.getDefinition();
			}

			@Override
			public List<? extends InterpretedValueSynth<?, ?>> getComponents() {
				return Collections.emptyList();
			}

			@Override
			protected TypeToken<String> getValueType() {
				return TypeTokens.get().STRING;
			}

			@Override
			public Instantiator instantiate() {
				return new Instantiator(this);
			}
		}

		static class Instantiator extends AbstractFormat.Instantiator<String> {
			public Instantiator(Interpreted interpreted) {
				super(interpreted);
			}

			@Override
			protected SettableValue<Format<String>> createFormat(ModelSetInstance models)
				throws ModelInstantiationException, IllegalStateException {
				return SettableValue.of(TypeTokens.get().keyFor(Format.class).<Format<String>> parameterized(String.class), INSTANCE,
					"Unmodifiable");
			}

			@Override
			protected SettableValue<Format<String>> copyFormat(SettableValue<Format<String>> format, ModelSetInstance sourceModels,
				ModelSetInstance newModels) throws ModelInstantiationException {
				return format;
			}
		}
	}

	static class StandardTextFormat<T> extends AbstractFormat<T> {
		public static final String STANDARD_TEXT_FORMAT = "standard-text-format";

		public StandardTextFormat(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType);
		}

		@Override
		public Interpreted<T> interpretValue(ExElement.Interpreted<?> parent) {
			return new Interpreted<>(this, parent);
		}

		static class Interpreted<T> extends AbstractFormat.Interpreted<T> {
			private TypeToken<T> theType;
			private Format<T> theFormat;

			Interpreted(StandardTextFormat<T> definition, ExElement.Interpreted<?> parent) {
				super(definition, parent);
			}

			@Override
			protected TypeToken<T> getValueType() {
				return theType;
			}

			public Format<T> getFormat() {
				return theFormat;
			}

			@Override
			protected void doUpdate(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
				super.doUpdate(env);

				theType = getAddOn(ExTyped.Interpreted.class).getValueType();
				Format<?> f;
				Class<?> type = TypeTokens.get().unwrap(TypeTokens.getRawType(theType));
				if (type == String.class)
					f = SpinnerFormat.NUMERICAL_TEXT;
				else if (type == int.class)
					f = SpinnerFormat.INT;
				else if (type == long.class)
					f = SpinnerFormat.LONG;
				else if (type == double.class)
					f = Format.doubleFormat(4).build();
				else if (type == float.class)
					f = Format.doubleFormat(4).buildFloat();
				else if (type == boolean.class)
					f = Format.BOOLEAN;
				else if (Enum.class.isAssignableFrom(type))
					f = Format.enumFormat((Class<Enum<?>>) type);
				else if (type == Instant.class)
					f = SpinnerFormat.flexDate(Instant::now, "EEE MMM dd, yyyy", null);
				else if (type == Duration.class)
					f = SpinnerFormat.flexDuration(false);
				else if (type == java.io.File.class)
					f = new Format.FileFormat(true);
				else
					throw new ExpressoInterpretationException("No standard format available for type " + theType, reporting().getPosition(),
						0);
				theFormat = (Format<T>) f;
			}

			@Override
			public Instantiator<T> instantiate() {
				return new Instantiator<>(this);
			}
		}

		static class Instantiator<T> extends AbstractFormat.Instantiator<T> {
			private Format<T> theFormat;

			Instantiator(Interpreted<T> interpreted) {
				super(interpreted);
				theFormat = interpreted.getFormat();
			}

			@Override
			protected SettableValue<Format<T>> createFormat(ModelSetInstance models)
				throws ModelInstantiationException, IllegalStateException {
				return SettableValue.of(getFormatType(), theFormat, StdMsg.UNSUPPORTED_OPERATION);
			}

			@Override
			protected SettableValue<Format<T>> copyFormat(SettableValue<Format<T>> format, ModelSetInstance sourceModels,
				ModelSetInstance newModels) throws ModelInstantiationException {
				return format;
			}
		}
	}

	static class FilterValidation<T> implements FormatValidation<T> {
		public static final String FILTER_VALIDATION = "filter-validation";

		@ExElementTraceable(toolkit = CONFIG,
			qonfigType = FILTER_VALIDATION,
			interpretation = Interpreted.class,
			instance = Instantiator.class)
		static class Def extends ExElement.Def.Abstract<Instantiator<?>> implements FormatValidation.Def<Instantiator<?>> {
			private ModelComponentId theFilterValueVariable;
			private CompiledExpression theTest;

			public Def(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
				super(parent, qonfigType);
			}

			@QonfigAttributeGetter("filter-value-name")
			public ModelComponentId getFilterValueVariable() {
				return theFilterValueVariable;
			}

			@QonfigAttributeGetter("test")
			public CompiledExpression getTest() {
				return theTest;
			}

			@Override
			protected void doUpdate(ExpressoQIS session) throws QonfigInterpretationException {
				super.doUpdate(session);

				ExWithElementModel.Def elModels = getAddOn(ExWithElementModel.Def.class);
				theFilterValueVariable = elModels.getElementValueModelId(session.getAttributeText("filter-value-name"));
				elModels.satisfyElementValueType(theFilterValueVariable, ModelTypes.Value,
					(interp, env) -> ModelTypes.Value.forType(((Interpreted<?>) interp).getValueType()));
				theTest = getAttributeExpression("test", session);
			}

			@Override
			public Interpreted<?> interpret(ExElement.Interpreted<?> parent) {
				return new Interpreted<>(this, parent);
			}
		}

		static class Interpreted<T> extends ExElement.Interpreted.Abstract<Instantiator<T>>
		implements FormatValidation.Interpreted<T, Instantiator<T>> {
			private TypeToken<T> theValueType;
			private InterpretedValueSynth<SettableValue<?>, SettableValue<String>> theTest;

			Interpreted(Def definition, ExElement.Interpreted<?> parent) {
				super(definition, parent);
			}

			@Override
			public Def getDefinition() {
				return (Def) super.getDefinition();
			}

			public TypeToken<T> getValueType() {
				return theValueType;
			}

			public InterpretedValueSynth<SettableValue<?>, SettableValue<String>> getTest() {
				return theTest;
			}

			@Override
			public void updateFormat(InterpretedExpressoEnv env, TypeToken<T> valueType) throws ExpressoInterpretationException {
				theValueType = valueType;
				update(env);
			}

			@Override
			protected void doUpdate(InterpretedExpressoEnv expressoEnv) throws ExpressoInterpretationException {
				super.doUpdate(expressoEnv);
				theTest = ExpressoTransformations.parseFilter(getDefinition().getTest(), this, true);
			}

			@Override
			public List<? extends InterpretedValueSynth<?, ?>> getComponents() {
				return Arrays.asList(theTest);
			}

			@Override
			public Instantiator<T> create() {
				return new Instantiator<>(getIdentity());
			}
		}

		static class Instantiator<T> extends ExElement.Abstract implements FormatValidation.Instantiator<T, FilterValidation<T>> {
			private TypeToken<T> theValueType;
			private ModelComponentId theFilterValueVariable;
			private ModelValueInstantiator<SettableValue<String>> theTest;

			Instantiator(Object id) {
				super(id);
			}

			@Override
			protected void doUpdate(ExElement.Interpreted<?> interpreted) {
				super.doUpdate(interpreted);

				FilterValidation.Interpreted<T> myInterpreted = (FilterValidation.Interpreted<T>) interpreted;
				theValueType = myInterpreted.getValueType();
				theFilterValueVariable = myInterpreted.getDefinition().getFilterValueVariable();
				theTest = myInterpreted.getTest().instantiate();
			}

			@Override
			public void instantiated() {
				super.instantiated();
				theTest.instantiate();
			}

			@Override
			protected void doInstantiate(ModelSetInstance myModels) throws ModelInstantiationException {
				super.doInstantiate(myModels);
			}

			@Override
			public FilterValidation<T> get(ModelSetInstance models) throws ModelInstantiationException, IllegalStateException {
				SettableValue<T> filterValue = SettableValue.build(theValueType).build();
				ExFlexibleElementModelAddOn.satisfyElementValue(theFilterValueVariable, models, filterValue);
				SettableValue<String> test = theTest.get(models);
				return new FilterValidation<>(filterValue, test);
			}

			@Override
			public FilterValidation<T> forModelCopy(FilterValidation<T> value, ModelSetInstance sourceModels, ModelSetInstance newModels)
				throws ModelInstantiationException {
				SettableValue<String> newTest = theTest.forModelCopy(value.getTest(), sourceModels, newModels);
				if (newTest != value.getTest()) {
					SettableValue<T> filterValue = SettableValue.build(theValueType).build();
					ExFlexibleElementModelAddOn.satisfyElementValue(theFilterValueVariable, newModels, filterValue);
					return new FilterValidation<>(filterValue, newTest);
				} else
					return value;
			}
		}

		private final SettableValue<T> theValue;
		private final SettableValue<String> theTest;

		FilterValidation(SettableValue<T> value, SettableValue<String> test) {
			theValue = value;
			theTest = test;
		}

		SettableValue<T> getValue() {
			return theValue;
		}

		SettableValue<String> getTest() {
			return theTest;
		}

		@Override
		public String test(T value, CharSequence text) {
			theValue.set(value, null);
			return theTest.get();
		}
	}

	@ExElementTraceable(toolkit = CONFIG,
		qonfigType = TextConfigFormat.TEXT_CONFIG_FORMAT,
		interpretation = TextConfigFormat.Interpreted.class)
	static class TextConfigFormat extends
	ModelValueElement.Def.SingleTyped<SettableValue<?>, ModelValueElement<SettableValue<?>, ? extends SettableValue<? extends ObservableConfigFormat<?>>>>
	implements
	ModelValueElement.CompiledSynth<SettableValue<?>, ModelValueElement<SettableValue<?>, ? extends SettableValue<? extends ObservableConfigFormat<?>>>> {
		public static final String TEXT_CONFIG_FORMAT = "text-config-format";
		public static final String INTERPRETED_TYPE_MANAGED = "Expresso.Config.Text.Format.Type.Managed";

		private CompiledExpression theTextFormat;
		private CompiledExpression theDefaultValue;
		private String theDefaultText;

		public TextConfigFormat(Def<?> parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType, ModelTypes.Value);
		}

		@QonfigAttributeGetter("text-format")
		public CompiledExpression getTextFormat() {
			return theTextFormat;
		}

		@QonfigAttributeGetter("default")
		public CompiledExpression getDefaultValue() {
			return theDefaultValue;
		}

		@QonfigAttributeGetter("default-text")
		public String getDefaultText() {
			return theDefaultText;
		}

		@Override
		protected void doPrepare(ExpressoQIS session) throws QonfigInterpretationException {
			VariableType type = getAddOn(ExTyped.Def.class).getValueType();
			theTextFormat = getAttributeExpression("text-format", session);
			if (type == null && theTextFormat == null) {
				if (!Boolean.TRUE.equals(session.get(INTERPRETED_TYPE_MANAGED)))
					throw new QonfigInterpretationException("Either 'type' or 'text-format' must be specified",
						session.getElement().getPositionInFile(), 0);
			} else if (type != null && theTextFormat != null)
				throw new QonfigInterpretationException("Only one of 'type' or 'text-format' may be specified",
					session.getElement().getPositionInFile(), 0);
			theDefaultValue = getAttributeExpression("default", session);
			theDefaultText = session.getAttributeText("default-text");
			if (theDefaultValue != null && theDefaultText != null)
				throw new QonfigInterpretationException("Only one of 'default' or 'default-text' may be specified",
					session.getElement().getPositionInFile(), 0);
		}

		@Override
		public Interpreted<?> interpretValue(ExElement.Interpreted<?> parent) {
			return new Interpreted<>(this, parent);
		}

		static class Interpreted<T> extends
		ModelValueElement.Def.SingleTyped.Interpreted<SettableValue<?>, SettableValue<ObservableConfigFormat<T>>, ModelValueElement<SettableValue<?>, SettableValue<ObservableConfigFormat<T>>>>
		implements
		ModelValueElement.InterpretedSynth<SettableValue<?>, SettableValue<ObservableConfigFormat<T>>, ModelValueElement<SettableValue<?>, SettableValue<ObservableConfigFormat<T>>>> {
			private InterpretedValueSynth<SettableValue<?>, SettableValue<Format<T>>> theTextFormat;
			private Format<T> theDefaultFormat;
			private InterpretedValueSynth<SettableValue<?>, SettableValue<T>> theDefaultValue;

			Interpreted(TextConfigFormat definition, ExElement.Interpreted<?> parent) {
				super(definition, parent);
			}

			@Override
			public TextConfigFormat getDefinition() {
				return (TextConfigFormat) super.getDefinition();
			}

			protected TypeToken<T> getValueType() {
				TypeToken<T> type = getAddOn(ExTyped.Interpreted.class).getValueType();
				return type != null ? type
					: (TypeToken<T>) theTextFormat.getType().getType(0).resolveType(Format.class.getTypeParameters()[0]);
			}

			@Override
			protected ModelInstanceType<SettableValue<?>, SettableValue<ObservableConfigFormat<T>>> getTargetType() {
				return ModelTypes.Value.forType(
					TypeTokens.get().keyFor(ObservableConfigFormat.class).<ObservableConfigFormat<T>> parameterized(getValueType()));
			}

			public InterpretedValueSynth<SettableValue<?>, SettableValue<Format<T>>> getTextFormat() {
				return theTextFormat;
			}

			public Format<T> getDefaultFormat() {
				return theDefaultFormat;
			}

			public InterpretedValueSynth<SettableValue<?>, SettableValue<T>> getDefaultValue() {
				return theDefaultValue;
			}

			@Override
			protected void doUpdate(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
				super.doUpdate(env);
				TypeToken<T> type = getAddOn(ExTyped.Interpreted.class).getValueType();
				ModelInstanceType<SettableValue<?>, SettableValue<Format<T>>> formatType;
				if (type != null)
					formatType = ModelTypes.Value.forType(TypeTokens.get().keyFor(Format.class).<Format<T>> parameterized(type));
				else
					formatType = ModelTypes.Value.anyAsV();
				theTextFormat = interpret(getDefinition().getTextFormat(), formatType);

				if (theTextFormat == null) {
					if (type == null)
						throw new ExpressoInterpretationException(
							"If no text-format is specified, a type must be specified to determine a default text format",
							reporting().getPosition(), 0);
					try {
						ObservableConfigFormat<T> defaultFormat = new ObservableConfigFormatSet().getConfigFormat(type, null);
						if (!(defaultFormat instanceof ObservableConfigFormat.Impl.SimpleConfigFormat))
							throw new IllegalArgumentException();
						theDefaultFormat = ((ObservableConfigFormat.Impl.SimpleConfigFormat<T>) defaultFormat).format;
					} catch (IllegalArgumentException e) {
						throw new ExpressoInterpretationException(
							"No default text format available for type " + type + ". 'text-format' must be specified.",
							reporting().getFileLocation(), e);
					}
				}

				theDefaultValue = interpret(getDefinition().getDefaultValue(), ModelTypes.Value.forType(getValueType()));
			}

			@Override
			public List<? extends InterpretedValueSynth<?, ?>> getComponents() {
				List<InterpretedValueSynth<?, ?>> components = new ArrayList<>();
				if (theTextFormat != null)
					components.add(theTextFormat);
				if (theDefaultValue != null)
					components.add(theDefaultValue);
				return components;
			}

			@Override
			public Instantiator<T> instantiate() {
				return new Instantiator<>(this);
			}
		}

		static class Instantiator<T> implements ModelValueInstantiator<SettableValue<ObservableConfigFormat<T>>> {
			private final TypeToken<T> theType;
			private final ModelValueInstantiator<SettableValue<Format<T>>> theTextFormat;
			private final Format<T> theDefaultFormat;
			private final ModelValueInstantiator<SettableValue<T>> theDefaultValue;
			private final String theDefaultText;
			private final ErrorReporting theReporting;

			Instantiator(Interpreted<T> interpreted) {
				theType = interpreted.getValueType();
				theTextFormat = interpreted.getTextFormat() == null ? null : interpreted.getTextFormat().instantiate();
				theDefaultFormat = interpreted.getDefaultFormat();
				theDefaultValue = interpreted.getDefaultValue() == null ? null : interpreted.getDefaultValue().instantiate();
				theDefaultText = interpreted.getDefinition().getDefaultText();
				theReporting = interpreted.reporting();
			}

			@Override
			public void instantiate() {
				if (theTextFormat != null)
					theTextFormat.instantiate();
				if (theDefaultValue != null)
					theDefaultValue.instantiate();
			}

			@Override
			public SettableValue<ObservableConfigFormat<T>> get(ModelSetInstance models)
				throws ModelInstantiationException, IllegalStateException {
				SettableValue<Format<T>> textFormat;
				if (theTextFormat != null)
					textFormat = theTextFormat.get(models);
				else
					textFormat = SettableValue.of(TypeTokens.get().of((Class<Format<T>>) (Class<?>) Format.class), theDefaultFormat,
						"Unmodifiable");
				ObservableValue<T> defaultValue;
				if (theDefaultValue != null)
					defaultValue = theDefaultValue.get(models);
				else if (theDefaultText != null) {
					defaultValue = textFormat.map(theType, tf -> {
						try {
							return tf.parse(theDefaultText);
						} catch (ParseException e) {
							theReporting.error("Could not parse default value", e);
							return TypeTokens.get().getDefaultValue(theType);
						}
					});
				} else
					defaultValue = ObservableValue.of(theType, TypeTokens.get().getDefaultValue(theType));
				return SettableValue.asSettable(
					textFormat.map(TypeTokens.get().of((Class<ObservableConfigFormat<T>>) (Class<?>) ObservableConfigFormat.class),
						tf -> ObservableConfigFormat.ofQommonFormat(tf, defaultValue)),
					__ -> "Unmodifiable");
			}

			@Override
			public SettableValue<ObservableConfigFormat<T>> forModelCopy(SettableValue<ObservableConfigFormat<T>> value,
				ModelSetInstance sourceModels, ModelSetInstance newModels) throws ModelInstantiationException {
				SettableValue<Format<T>> srcTextFormat, newTextFormat;
				if (theTextFormat != null) {
					srcTextFormat = theTextFormat.get(sourceModels);
					newTextFormat = theTextFormat.forModelCopy(srcTextFormat, sourceModels, newModels);
				} else
					srcTextFormat = newTextFormat = SettableValue.of(TypeTokens.get().of((Class<Format<T>>) (Class<?>) Format.class),
						theDefaultFormat, "Unmodifiable");

				ObservableValue<T> srcDefaultValue, newDefaultValue;
				if (theDefaultValue != null) {
					srcDefaultValue = theDefaultValue.get(sourceModels);
					newDefaultValue = theDefaultValue.forModelCopy((SettableValue<T>) srcDefaultValue, sourceModels, newModels);
				} else if (theDefaultText != null) {
					srcDefaultValue = newDefaultValue = newTextFormat.map(theType, tf -> {
						try {
							return tf.parse(theDefaultText);
						} catch (ParseException e) {
							theReporting.error("Could not parse default value", e);
							return TypeTokens.get().getDefaultValue(theType);
						}
					});
				} else
					srcDefaultValue = newDefaultValue = ObservableValue.of(theType, TypeTokens.get().getDefaultValue(theType));
				if (srcTextFormat == newTextFormat && srcDefaultValue == newDefaultValue)
					return value;
				return SettableValue.asSettable(
					newTextFormat.map(TypeTokens.get().of((Class<ObservableConfigFormat<T>>) (Class<?>) ObservableConfigFormat.class),
						tf -> ObservableConfigFormat.ofQommonFormat(tf, newDefaultValue)),
					__ -> "Unmodifiable");
			}
		}
	}

	@ExElementTraceable(toolkit = CONFIG,
		qonfigType = EntityConfigFormat.ENTITY_CONFIG_FORMAT,
		interpretation = EntityConfigFormat.Interpreted.class)
	static class EntityConfigFormat extends
	ModelValueElement.Def.SingleTyped<SettableValue<?>, ModelValueElement<SettableValue<?>, ? extends SettableValue<? extends ObservableConfigFormat<?>>>>
	implements
	ModelValueElement.CompiledSynth<SettableValue<?>, ModelValueElement<SettableValue<?>, ? extends SettableValue<? extends ObservableConfigFormat<?>>>> {
		public static final String ENTITY_CONFIG_FORMAT = "entity-config-format";

		private CompiledExpression theFormatSet;
		private final Map<String, ModelValueElement.CompiledSynth<SettableValue<?>, ModelValueElement<SettableValue<?>, ? extends SettableValue<ObservableConfigFormat<?>>>>> theFields;

		public EntityConfigFormat(ExElement.Def<?> parent, QonfigElementOrAddOn qonfigType) {
			super(parent, qonfigType, ModelTypes.Value);
			theFields = new LinkedHashMap<>();
		}

		@QonfigAttributeGetter("format-set")
		public CompiledExpression getFormatSet() {
			return theFormatSet;
		}

		@QonfigChildGetter("field")
		public Map<String, ModelValueElement.CompiledSynth<SettableValue<?>, ModelValueElement<SettableValue<?>, ? extends SettableValue<ObservableConfigFormat<?>>>>> getFields() {
			return Collections.unmodifiableMap(theFields);
		}

		@Override
		protected void doPrepare(ExpressoQIS session) throws QonfigInterpretationException {
			theFormatSet = getAttributeExpression("format-set", session);
			List<ModelValueElement.CompiledSynth<SettableValue<?>, ModelValueElement<SettableValue<?>, ? extends SettableValue<ObservableConfigFormat<?>>>>> fields;
			fields = new ArrayList<>(theFields.values());
			syncChildren(ModelValueElement.CompiledSynth.class, fields, session.forChildren("field"), (f, s) -> {
				s = s.put(ExTyped.VALUE_TYPE_KEY, null).putLocal(TextConfigFormat.INTERPRETED_TYPE_MANAGED, true);
				f.update(s);
				f.prepareModelValue(s);
			});
			theFields.clear();
			for (ModelValueElement.CompiledSynth<SettableValue<?>, ModelValueElement<SettableValue<?>, ? extends SettableValue<ObservableConfigFormat<?>>>> field : fields) {
				String fieldName = field.getAddOn(EntityConfigField.class).getFieldName();
				if (theFields.containsKey(fieldName))
					reporting().warn("Multiple fields specified named '" + fieldName + "': using the first specification");
				else
					theFields.put(fieldName, field);
			}
		}

		@Override
		public Interpreted<?> interpretValue(ExElement.Interpreted<?> parent) {
			return new Interpreted<>(this, parent);
		}

		static class Interpreted<E> extends
		ModelValueElement.Def.SingleTyped.Interpreted<SettableValue<?>, SettableValue<ObservableConfigFormat<E>>, ModelValueElement<SettableValue<?>, SettableValue<ObservableConfigFormat<E>>>>
		implements
		ModelValueElement.InterpretedSynth<SettableValue<?>, SettableValue<ObservableConfigFormat<E>>, ModelValueElement<SettableValue<?>, SettableValue<ObservableConfigFormat<E>>>> {
			private InterpretedValueSynth<SettableValue<?>, SettableValue<ObservableConfigFormatSet>> theFormatSet;
			private final Map<String, ModelValueElement.InterpretedSynth<SettableValue<?>, SettableValue<ObservableConfigFormat<E>>, ModelValueElement<SettableValue<?>, SettableValue<ObservableConfigFormat<E>>>>> theFields;

			Interpreted(EntityConfigFormat definition, ExElement.Interpreted<?> parent) {
				super(definition, parent);
				theFields = new LinkedHashMap<>();
			}

			@Override
			public EntityConfigFormat getDefinition() {
				return (EntityConfigFormat) super.getDefinition();
			}

			public InterpretedValueSynth<SettableValue<?>, SettableValue<ObservableConfigFormatSet>> getFormatSet() {
				return theFormatSet;
			}

			public Map<String, ModelValueElement.InterpretedSynth<SettableValue<?>, SettableValue<ObservableConfigFormat<E>>, ModelValueElement<SettableValue<?>, SettableValue<ObservableConfigFormat<E>>>>> getFields() {
				return Collections.unmodifiableMap(theFields);
			}

			protected TypeToken<ObservableConfigFormat<E>> getValueType() {
				TypeToken<E> type = getAddOn(ExTyped.Interpreted.class).getValueType();
				return TypeTokens.get().keyFor(ObservableConfigFormat.class).<ObservableConfigFormat<E>> parameterized(type);
			}

			@Override
			protected ModelInstanceType<SettableValue<?>, SettableValue<ObservableConfigFormat<E>>> getTargetType() {
				return ModelTypes.Value.forType(getValueType());
			}

			@Override
			public List<? extends InterpretedValueSynth<?, ?>> getComponents() {
				return new ArrayList<InterpretedValueSynth<?, ?>>(theFields.values());
			}

			@Override
			protected void doUpdate(InterpretedExpressoEnv env) throws ExpressoInterpretationException {
				super.doUpdate(env);
				theFormatSet = interpret(getDefinition().getFormatSet(), ModelTypes.Value.forType(ObservableConfigFormatSet.class));
				Set<String> fieldNames = new LinkedHashSet<>(getDefinition().getFields().keySet());
				TypeToken<E> entityType = getAddOn(ExTyped.Interpreted.class).getValueType();
				EntityReflector<E> reflector = EntityReflector.build(entityType, true).build();
				List<ModelValueElement.CompiledSynth<SettableValue<?>, ModelValueElement<SettableValue<?>, ? extends SettableValue<ObservableConfigFormat<?>>>>> defFields;
				defFields = new ArrayList<>();
				for (int f = 0; f < reflector.getFields().keySize(); f++) {
					ModelValueElement.CompiledSynth<SettableValue<?>, ModelValueElement<SettableValue<?>, ? extends SettableValue<ObservableConfigFormat<?>>>> defField;
					String fieldName = reflector.getFields().keySet().get(f);
					defField = getDefinition().getFields().get(fieldName);
					if (defField == null)
						continue;
					fieldNames.remove(fieldName);
					defFields.add(defField);
				}
				if (!fieldNames.isEmpty()) {
					String msg = "No such field" + (fieldNames.size() == 1 ? "" : "s") + ": " + entityType + ".";
					if (fieldNames.size() == 1)
						msg += fieldNames.iterator().next();
					else
						msg += fieldNames;
					msg += "\nAvailable fields are " + reflector.getFields().keySet();
					reporting().warn(msg);
				}
				List<ModelValueElement.InterpretedSynth<SettableValue<?>, SettableValue<ObservableConfigFormat<E>>, ModelValueElement<SettableValue<?>, SettableValue<ObservableConfigFormat<E>>>>> fields;
				fields = new ArrayList<>(theFields.values());
				syncChildren(defFields, fields,
					f -> (ModelValueElement.InterpretedSynth<SettableValue<?>, SettableValue<ObservableConfigFormat<E>>, ModelValueElement<SettableValue<?>, SettableValue<ObservableConfigFormat<E>>>>) f
						.interpretValue(this),
					(i, mEnv) -> {
						String fieldName = i.getDefinition().getAddOn(EntityConfigField.class).getFieldName();
						mEnv.putLocal(ExTyped.VALUE_TYPE_KEY, reflector.getFields().get(fieldName).getType());
						i.updateValue(mEnv);
					});
				theFields.clear();
				for (ModelValueElement.InterpretedSynth<SettableValue<?>, SettableValue<ObservableConfigFormat<E>>, ModelValueElement<SettableValue<?>, SettableValue<ObservableConfigFormat<E>>>> field : fields)
					theFields.put(field.getDefinition().getAddOn(EntityConfigField.class).getFieldName(), field);
			}

			@Override
			public ModelValueInstantiator<SettableValue<ObservableConfigFormat<E>>> instantiate() {
				return new Instantiator<>(this);
			}
		}

		static class Instantiator<E> implements ModelValueInstantiator<SettableValue<ObservableConfigFormat<E>>> {
			private final TypeToken<E> theEntityType;
			private final ModelValueInstantiator<SettableValue<ObservableConfigFormatSet>> theFormatSet;
			private final Map<String, String> theFieldConfigNames;
			private final Map<String, ModelValueInstantiator<? extends SettableValue<? extends ObservableConfigFormat<?>>>> theFields;

			Instantiator(Interpreted<E> interpreted) {
				theEntityType = interpreted.getAddOn(ExTyped.Interpreted.class).getValueType();
				theFormatSet = interpreted.getFormatSet() == null ? null : interpreted.getFormatSet().instantiate();
				theFieldConfigNames = new LinkedHashMap<>();
				theFields = new LinkedHashMap<>();
				for (Map.Entry<String, InterpretedSynth<SettableValue<?>, SettableValue<ObservableConfigFormat<E>>, ModelValueElement<SettableValue<?>, SettableValue<ObservableConfigFormat<E>>>>> field : interpreted
					.getFields().entrySet()) {
					String configName = field.getValue().getDefinition().getAddOn(EntityConfigField.class).getConfigName();
					if (configName != null)
						theFieldConfigNames.put(field.getKey(), configName);
					theFields.put(field.getKey(), field.getValue().instantiate());
				}
			}

			@Override
			public void instantiate() {
				if (theFormatSet != null)
					theFormatSet.instantiate();
				for (ModelValueInstantiator<? extends SettableValue<? extends ObservableConfigFormat<?>>> field : theFields.values())
					field.instantiate();
			}

			@Override
			public SettableValue<ObservableConfigFormat<E>> get(ModelSetInstance models)
				throws ModelInstantiationException, IllegalStateException {
				ObservableConfigFormatSet formatSet;
				if (theFormatSet != null)
					formatSet = theFormatSet.get(models).get();
				else
					formatSet = new ObservableConfigFormatSet();
				ObservableConfigFormat.EntityFormatBuilder<E> builder = ObservableConfigFormat.buildEntities(theEntityType, formatSet);
				for (Map.Entry<String, ModelValueInstantiator<? extends SettableValue<? extends ObservableConfigFormat<?>>>> field : theFields
					.entrySet()) {
					String configName = theFieldConfigNames.get(field.getKey());
					if (configName != null)
						builder.withFieldChildName(field.getKey(), configName);
					builder.withFieldFormat(field.getKey(), field.getValue().get(models).get());
				}
				return SettableValue.of(
					TypeTokens.get().keyFor(ObservableConfigFormat.class).<ObservableConfigFormat<E>> parameterized(theEntityType),
					builder.build(), "Unmodifiable");
			}

			@Override
			public SettableValue<ObservableConfigFormat<E>> forModelCopy(SettableValue<ObservableConfigFormat<E>> value,
				ModelSetInstance sourceModels, ModelSetInstance newModels) throws ModelInstantiationException {
				// Meh
				return get(newModels);
			}
		}
	}

	@ExElementTraceable(toolkit = CONFIG, qonfigType = EntityConfigField.ENTITY_CONFIG_FIELD)
	static class EntityConfigField extends ExAddOn.Def.Abstract<ExElement, ExAddOn.Void<ExElement>> {
		public static final String ENTITY_CONFIG_FIELD = "entity-config-field";

		private String theFieldName;
		private String theConfigName;

		public EntityConfigField(QonfigAddOn type, ExElement.Def<?> element) {
			super(type, element);
		}

		@QonfigAttributeGetter("field-name")
		public String getFieldName() {
			return theFieldName;
		}

		@QonfigAttributeGetter("config-name")
		public String getConfigName() {
			return theConfigName;
		}

		@Override
		public void update(ExpressoQIS session, ExElement.Def<? extends ExElement> element) throws QonfigInterpretationException {
			super.update(session, element);
			theFieldName = session.getAttributeText("field-name");
			theConfigName = session.getAttributeText("config-name");
		}

		@Override
		public ExAddOn.Interpreted<? extends ExElement, ? extends Void<ExElement>> interpret(ExElement.Interpreted<?> element) {
			return null;
		}
	}
}
