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
import org.qommons.Named;
import org.qommons.ClassMap;
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
	private final ClassMap<Object, DbugAnchorType<?>> theAnchors;
	private final ThreadLocal<Instantiation> theInstantiations;

	public Dbug(String name, Transactable lock) {
		this(name, __ -> lock);
	}

	public Dbug(String name, Function<? super Dbug, ? extends Transactable> lock) {
		theName = name;
		theLock = lock.apply(this);
		theAnchors = new ClassMap<>(Object.class);
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

	<A> void getTokens(DbugAnchor<A> anchor, Set<DbugToken> tokens) {
		theInstantiations.get()//
		.getTokens(anchor, tokens);
	}

	DbugAnchor.InstantiationTransaction instantiating(Set<DbugToken> tokens) {
		return theInstantiations.get().instantiatingFor(tokens);
	}

	static class Instantiation {
		private final List<List<DbugToken>> theCurrentTokens;
		private final ClassMap<Object, List<? extends DbugInstanceTokenizer<?>>> theTokenizers;

		Instantiation() {
			theCurrentTokens = new ArrayList<>();
			theTokenizers = new ClassMap<>(Object.class);
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
					if (tokenizer.applies(anchor, anchor.getInstance())) {
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
