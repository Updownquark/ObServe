package org.observe.dbug;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;

import org.observe.dbug.DbugAnchor.DbugInstanceTokenizer;
import org.observe.dbug.DbugAnchor.InstantiationTransaction;
import org.qommons.ClassMap;
import org.qommons.Named;
import org.qommons.ThreadConstraint;
import org.qommons.Transactable;
import org.qommons.collect.BetterList;
import org.qommons.collect.StampedLockingStrategy;
import org.qommons.tree.BetterTreeList;

/**
 * <p>
 * Dbug is a runtime debugging model intended to allow developers to punch holes in code to access data and events from code that is not
 * normally accessible. Dbug is enabled for a type by writing code in that class which calls Dbug for each field or event. Then listeners to
 * these values and events can be implemented. Dbug also contains means to retrieve values which may be fields of fields, inaccessible to
 * the caller normally.
 * </p>
 * <p>
 * The most important part of Dbug is that its default state is disabled, and Dbug calls consume almost no CPU or memory resources when
 * disabled. This allows the custom code written for Dbug to be left in dbugged classes to be left for potential later re-use.
 * </p>
 * <p>
 * To enable dbugging on a class:
 * </p>
 * <ol>
 * <li>{@link #anchor(Class, Consumer) Create an anchor type} for it. If you have control over the class's source code, you can do this as a
 * static constant on the class. No need to delete it when you're done dbugging, as it won't consume any resources when it is
 * inactive.<br />
 * On the builder passed to the Consumer, configure any
 * {@link DbugAnchorType.Builder#withField(String, boolean, boolean, com.google.common.reflect.TypeToken) fields} and/or
 * {@link DbugAnchorType.Builder#withEvent(String, Consumer) events} that instances of the type will be able to maintain/fire and be
 * listenable to for debugging.</li>
 * <li>Add code to {@link DbugAnchorType#instance(Object, Consumer) create an anchor instance} for the instance you want to dbug. If you
 * control the source, this can be done as a final field in the class initialized in the constructor. This will, obviously, cause instances
 * of the class to consume one memory location's size more space, but no more when dbugging for the instance is inactive.<br />
 * The instance itself may maintain its {@link DbugAnchor#setField(String, Object, Object) fields} and fire its
 * {@link DbugAnchor#event(String, Object, Consumer) events}, or this may be done by some observer of the instance. The code that does this
 * need not be removed after dbugging, since these calls do nothing when the instance is not active.</li>
 * <li>Call {@link #watch()}.{@link InstantiationTransaction#watchFor(DbugAnchorType, String, Consumer) watchFor()} to watch for new
 * instances of the given type. On the {@link DbugInstanceTokenizer} passed to the Consumer:
 * <ol>
 * <li>Optionally specify a {@link DbugInstanceTokenizer#filter(java.util.function.Predicate) filter} or
 * {@link DbugInstanceTokenizer#skip(int) skip} to pass over some instances</li>
 * <li>Optionally specify a {@link DbugInstanceTokenizer#applyTo(int) number} of instances to apply to</li>
 * </ol>
 * </li>
 * <li>If applicable, {@link InstantiationTransaction#close() Close} the transaction at a spot in code past where a target instance may be
 * created.</li>
 * <li>When an instance of the type is created and Dbug determines that it applies to your tokenizer, the tokenizer's
 * {@link DbugInstanceTokenizer#getTokenName() token} will be applied to it, it will be activated (unless prevented with
 * {@link DbugInstanceTokenizer#activate(boolean) activate(false)}) and presented to the tokenizer's
 * {@link DbugInstanceTokenizer#thenDo(Consumer) action}.</li>
 * <li>The action may observe any of the anchor's {@link DbugAnchor#getField(String) fields}, listen to any of the anchor's
 * {@link DbugAnchor#listenFor(String, Consumer) events}.
 * </ul>
 */
public class Dbug implements Named {
	private static Dbug COMMON;
	private static ConcurrentHashMap<String, Dbug> NAMED = new ConcurrentHashMap<>();

	/** @return The "common" instance of Dbug. Custom Dbug instances can be created, but this is the one typically used. */
	public static Dbug common() {
		if (COMMON != null)
			return COMMON;
		synchronized (Dbug.class) {
			if (COMMON != null)
				return COMMON;
			COMMON = new Dbug("COMMON", dbug -> new StampedLockingStrategy(dbug, ThreadConstraint.ANY));
		}
		return COMMON;
	}

	/**
	 * Retrieves or creates a Dbug instance by name
	 *
	 * @param name The name for the Dbug instance
	 * @return The Dbug instance with the given name
	 */
	public static Dbug dbug(String name) {
		return dbug(name, dbug -> new StampedLockingStrategy(dbug, ThreadConstraint.ANY));
	}

	/**
	 * Retrieves or creates a Dbug instance by name
	 *
	 * @param name The name for the Dbug instance
	 * @param lock Produces locking for the Dbug
	 * @return The Dbug instance with the given name
	 */
	public static Dbug dbug(String name, Function<? super Dbug, ? extends Transactable> lock) {
		return NAMED.computeIfAbsent(name, n -> new Dbug(name, lock));
	}

	private final String theName;
	private final Transactable theLock;
	private final ClassMap<DbugAnchorType<?>> theAnchors;
	private final ThreadLocal<Instantiation> theInstantiations;

	public Dbug(String name, Transactable lock) {
		this(name, __ -> lock);
	}

	public Dbug(String name, Function<? super Dbug, ? extends Transactable> lock) {
		theName = name;
		theLock = lock.apply(this);
		theAnchors = new ClassMap<>();
		theInstantiations = ThreadLocal.withInitial(Instantiation::new);
	}

	public Transactable getLock() {
		return theLock;
	}

	/**
	 * Declares an anchor type in this Dbug
	 *
	 * @param <A> The type to debug
	 * @param type The type to debug
	 * @param builder Configures debugging capabilities for this type
	 * @return The anchor type
	 */
	public synchronized <A> DbugAnchorType<A> anchor(Class<A> type, Consumer<DbugAnchorType.Builder<A>> builder) {
		DbugAnchorType<?> existing = theAnchors.get(type, ClassMap.TypeMatch.EXACT);
		if (existing != null)
			throw new IllegalArgumentException(
				"An anchor has already been configured for type " + type.getName() + " in this Dbug");
		DbugAnchorType.Builder<A> anchorBuilder = new DbugAnchorType.Builder<>(this, type);
		DbugAnchorType<A> anchor = anchorBuilder.getAnchor();
		theAnchors.with(type, anchor);
		if (builder != null)
			builder.accept(anchorBuilder);
		anchorBuilder.built();
		return anchor;
	}

	/**
	 * @param <A> The anchor type to debug
	 * @param type The anchor type to debug
	 * @param throwIfNotFound Whether to throw an exception if no such anchor type has been declared
	 * @return The Dbug anchor type for the given java type, if it has been declared
	 */
	public synchronized <A> DbugAnchorType<? super A> getAnchor(Class<A> type, boolean throwIfNotFound) {
		DbugAnchorType<? super A> anchor = (DbugAnchorType<? super A>) theAnchors.get(type, ClassMap.TypeMatch.SUPER_TYPE);
		if (anchor == null && throwIfNotFound)
			throw new IllegalArgumentException("No anchor for type " + type.getName());
		return anchor;
	}

	@Override
	public String getName() {
		return theName;
	}

	public InstantiationTransaction watch() {
		return instantiating(Collections.emptySet());
	}

	<A> void getTokens(DbugAnchor<A> anchor, Set<DbugToken> tokens) {
		theInstantiations.get()//
		.getTokens(anchor, tokens);
	}

	DbugAnchor.InstantiationTransaction instantiating(Set<DbugToken> tokens) {
		return theInstantiations.get().instantiatingFor(tokens);
	}

	static class Instantiation {
		private final List<List<DbugToken>> theCurrentTokens;
		private final ClassMap<List<? extends DbugInstanceTokenizer<?>>> theTokenizers;

		Instantiation() {
			theCurrentTokens = new ArrayList<>();
			theTokenizers = new ClassMap<>();
		}

		InstantiationStackItem instantiatingFor(Set<DbugToken> tokens) {
			boolean newTokens = !tokens.isEmpty();
			if (newTokens) {
				List<DbugToken> current = theCurrentTokens.isEmpty() ? Collections.emptyList()
					: theCurrentTokens.get(theCurrentTokens.size() - 1);
				List<DbugToken> stackTokens = new ArrayList<>(current.size() + tokens.size());
				stackTokens.addAll(current);
				stackTokens.addAll(tokens);
				theCurrentTokens.add(stackTokens);
			}
			return new InstantiationStackItem(newTokens);
		}


		<A> void getTokens(DbugAnchor<A> anchor, Set<DbugToken> tokens) {
			List<DbugInstanceTokenizer<A>> tokenizers = (List<DbugInstanceTokenizer<A>>) theTokenizers.get(anchor.getType().getType(),
				ClassMap.TypeMatch.SUPER_TYPE);
			if (tokenizers != null && !tokenizers.isEmpty()) {
				for (DbugInstanceTokenizer<A> tokenizer : tokenizers) {
					if (tokenizer.applies(anchor)) {
						if (tokenizer.getTokenName() != null) {
							tokens.add(new DbugToken(BetterList.of(tokenizer.getTokenName())));
							if (!theCurrentTokens.isEmpty()) {
								for (DbugToken parent : theCurrentTokens.get(theCurrentTokens.size() - 1)) {
									String[] path = new String[parent.getPath().size() + 1];
									parent.getPath().toArray(path);
									path[path.length - 1] = tokenizer.getTokenName();
									tokens.add(new DbugToken(BetterList.of(path)));
								}
							}
						}
						if (tokenizer.isExhausted())
							tokenizers.remove(tokenizer);
					}
				}
			}
		}

		class InstantiationStackItem implements DbugAnchor.InstantiationTransaction {
			private final boolean hasNewTokens;
			private List<DbugInstanceTokenizer<?>> theStackItemTokenizers;

			InstantiationStackItem(boolean newTokens) {
				hasNewTokens = newTokens;
			}

			@Override
			public <A> InstantiationTransaction watchFor(DbugAnchorType<A> targetAnchor, String tokenName,
				Consumer<DbugInstanceTokenizer<A>> configure) {
				targetAnchor.setActive(true);
				DbugInstanceTokenizer<A> tokenizer = new DbugInstanceTokenizer<>(targetAnchor, tokenName);
				if (configure != null)
					configure.accept(tokenizer);
				if (theStackItemTokenizers == null)
					theStackItemTokenizers = new ArrayList<>(3);
				theStackItemTokenizers.add(tokenizer);
				((List<DbugInstanceTokenizer<A>>) theTokenizers.computeIfAbsent(targetAnchor.getType(),
					() -> BetterTreeList.<DbugInstanceTokenizer<A>> build().build())).add(0, tokenizer); // Inner listeners go first
				return this;
			}

			@Override
			public void close() {
				if (hasNewTokens)
					theCurrentTokens.remove(theCurrentTokens.size() - 1);
				if (theStackItemTokenizers != null) {
					for (DbugInstanceTokenizer<?> tokenizer : theStackItemTokenizers) {
						tokenizer.getAnchorType().setActive(false);
						theTokenizers.compute(tokenizer.getAnchorType().getType(), list -> {
							if (list == null)
								return list;
							list.remove(tokenizer);
							return list.isEmpty() ? null : list;
						});
					}
				}
			}
		}
	}
}
