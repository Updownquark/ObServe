package org.observe.config;

import java.util.function.Consumer;

import org.observe.Observable;
import org.qommons.QommonsUtils;
import org.qommons.Transaction;
import org.qommons.collect.ElementId;
import org.qommons.collect.MutableCollectionElement.StdMsg;

public class UnmodifiableObservableConfig extends AbstractObservableConfig {
	private static final ObservableConfigParseSession SESSION = new ObservableConfigParseSession();

	private final ObservableConfig theWrapped;

	private UnmodifiableObservableConfig(ObservableConfig wrapped) {
		theWrapped = wrapped;
	}

	@Override
	public String getName() {
		return theWrapped.getName();
	}

	@Override
	public Transaction lock(boolean write, Object cause) {
		return theWrapped.lock(false, cause);
	}

	@Override
	public Transaction tryLock(boolean write, Object cause) {
		return theWrapped.tryLock(false, cause);
	}

	@Override
	public long getStamp() {
		return theWrapped.getStamp();
	}

	@Override
	public String getValue() {
		return theWrapped.getValue();
	}

	@Override
	public ElementId getParentChildRef() {
		return theWrapped.getParentChildRef();
	}

	@Override
	public Observable<ObservableConfigEvent> watch(ObservableConfigPath path) {
		return theWrapped.watch(path).map(evt -> {
			return new ObservableConfig.ObservableConfigEvent(evt.changeType, evt.isMove, evt.eventTarget.unmodifiable(), evt.oldName,
				evt.oldValue, //
				QommonsUtils.map2(evt.relativePath, ObservableConfig::unmodifiable), evt);
		});
	}

	@Override
	public String canAddChild(ObservableConfig after, ObservableConfig before) {
		return StdMsg.UNSUPPORTED_OPERATION;
	}

	@Override
	public String canMoveChild(ObservableConfig child, ObservableConfig after, ObservableConfig before) {
		return StdMsg.UNSUPPORTED_OPERATION;
	}

	@Override
	public String canRemove() {
		return StdMsg.UNSUPPORTED_OPERATION;
	}

	@Override
	public ObservableConfig setName(String name) {
		throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
	}

	@Override
	public ObservableConfig setValue(String value) {
		throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
	}

	@Override
	public ObservableConfig addChild(ObservableConfig after, ObservableConfig before, boolean first, String name,
		Consumer<ObservableConfig> preAddMod) {
		throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
	}

	@Override
	public ObservableConfig moveChild(ObservableConfig child, ObservableConfig after, ObservableConfig before, boolean first,
		Runnable afterRemove) {
		throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
	}

	@Override
	public void remove() {
		throw new UnsupportedOperationException(StdMsg.UNSUPPORTED_OPERATION);
	}

	@Override
	protected AbstractObservableConfig createChild(String name) {
		throw new IllegalStateException("Should not be here");
	}

	@Override
	protected void addChild(AbstractObservableConfig child, ObservableConfig after, ObservableConfig before, boolean first, boolean move) {
		throw new IllegalStateException("Should not be here");
	}

	@Override
	protected void doRemove(boolean move) {
		throw new IllegalStateException("Should not be here");
	}

	@Override
	protected void _postRemove() {
		throw new IllegalStateException("Should not be here");
	}

	@Override
	public ObservableConfig unmodifiable() {
		return this;
	}

	static UnmodifiableObservableConfig unmodifiable(ObservableConfig config) {
		UnmodifiableObservableConfig unmodifiable = (UnmodifiableObservableConfig) config.getParsedItem(SESSION);
		if (unmodifiable == null) {
			unmodifiable = new UnmodifiableObservableConfig(config);
			config.withParsedItem(SESSION, unmodifiable);
		}
		return unmodifiable;
	}
}
