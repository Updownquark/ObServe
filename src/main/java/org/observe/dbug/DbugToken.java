package org.observe.dbug;

import org.qommons.StringUtils;
import org.qommons.collect.BetterList;

public class DbugToken {
	private final BetterList<String> thePath;

	DbugToken(BetterList<String> path) {
		thePath = path;
	}

	public BetterList<String> getPath() {
		return thePath;
	}

	public DbugToken replaceLast(String last) {
		if (thePath.size() == 1)
			return new DbugToken(BetterList.of(last));
		String[] path = thePath.toArray(new String[thePath.size()]);
		path[path.length - 1] = last;
		return new DbugToken(BetterList.of(path));
	}

	@Override
	public int hashCode() {
		return thePath.hashCode();
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		else if (!(obj instanceof DbugToken))
			return false;
		return thePath.equals(((DbugToken) obj).thePath);
	}

	@Override
	public String toString() {
		return StringUtils.print(".", thePath, s -> s).toString();
	}

	public static DbugToken of(String... path) {
		return new DbugToken(BetterList.of(path));
	}
}
