package org.observe.util;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.qommons.Named;
import org.qommons.collect.QuickSet.QuickMap;

/** A CSV entity set that is synchronized with a version control system */
public abstract class VersionedEntities extends CsvEntitySet {
	/** The author of a commit */
	public interface Committer extends Named {
	}

	/** Represents a change to one or more entities in the set */
	public interface Commit {
		/** @return The author of the commit */
		Committer getCommitter();

		/** @return The time of the commit */
		Instant getCommitTime();

		/** @return The commit message */
		String getMessage();

		/** @return Whether the commit was made locally */
		boolean isLocalOnly();

		/** @return The changes in the commit */
		List<EntityUpdate> getChanges();
	}

	/** A change to an entity */
	public interface EntityUpdate {
		/** @return The commit in which the entity was changed */
		Commit getCommit();

		/** @return The type of the entity that was changed */
		EntityFormat getEntityType();

		/** @return The entity before the change */
		QuickMap<String, Object> getOldValues();

		/** @return The entity after the change */
		QuickMap<String, Object> getNewValues();
	}

	/** Listens for changes to the entity set */
	public interface ChangeListener {
		/** @param commit The change */
		void changeOccurred(Commit commit);
	}

	/** Represents a conflict, changes from both a remote source as well as local, which must be resolved */
	public interface EntityConflict {
		/** @return The entity before any changes, local or remote */
		QuickMap<String, Object> getOriginal();

		/** @return The entity after local changes */
		QuickMap<String, Object> getMine();

		/** @return The entity after remote changes */
		QuickMap<String, Object> getTheirs();

		/**
		 * @param fieldIndex The index of the field
		 * @return The latest local commit where the field was modified
		 */
		Commit getMyChange(int fieldIndex);

		/**
		 * @param fieldIndex The index of the field
		 * @return The latest remote commit where the field was modified
		 */
		Commit getTheirChange(int fieldIndex);
	}

	/** Resolves conflicts against a single entity from multiple sources */
	public interface ConflictResolver {
		/**
		 * @param conflict The conflict to resolve
		 * @return The resolved entity
		 */
		QuickMap<String, Object> resolveConflict(EntityConflict conflict);
	}

	/** Represents a VCS branch that is a particular schema version of a logical branch of an entity set */
	public static class BranchInfo {
		/** Pattern used to parse branches from VCS branch names */
		public static final Pattern BRANCH_PATTERN = Pattern.compile("(?<name>.+)\\-(?<major>\\d+)\\.(?<minor>\\d+)\\.(?<patch>\\d+)");

		private final String theBranchName;
		private final int theMajorVersion;
		private final int theMinorVersion;
		private final int thePatchVersion;

		/**
		 * @param branchName The base (version-less) name for the branch
		 * @param majorVersion The major version number for the branch
		 * @param minorVersion The minor version number for the branch
		 * @param patchVersion The patch version number for the branch
		 */
		public BranchInfo(String branchName, int majorVersion, int minorVersion, int patchVersion) {
			theBranchName = branchName;
			theMajorVersion = majorVersion;
			theMinorVersion = minorVersion;
			thePatchVersion = patchVersion;
		}

		/** @return The base (version-less) name of the branch */
		public String getBranchName() {
			return theBranchName;
		}

		/** @return The major version number of the branch */
		public int getMajorVersion() {
			return theMajorVersion;
		}

		/** @return The minor version number of the branch */
		public int getMinorVersion() {
			return theMinorVersion;
		}

		/** @return The patch version number of the branch */
		public int getPatchVersion() {
			return thePatchVersion;
		}

		@Override
		public int hashCode() {
			return Objects.hash(theBranchName, theMajorVersion, theMinorVersion, thePatchVersion);
		}

		@Override
		public boolean equals(Object obj) {
			return obj instanceof BranchInfo//
				&& theBranchName.equals(((BranchInfo) obj).theBranchName)//
				&& theMajorVersion == ((BranchInfo) obj).theMajorVersion//
				&& theMinorVersion == ((BranchInfo) obj).theMinorVersion//
				&& thePatchVersion == ((BranchInfo) obj).thePatchVersion;
		}

		@Override
		public String toString() {
			return new StringBuilder(theBranchName).append('-')//
				.append(theMajorVersion).append('.').append(theMinorVersion).append('.').append(thePatchVersion)//
				.toString();
		}

		/**
		 * @param vcsBranchName The branch name from the version control system
		 * @return The parsed branch info
		 * @throws IllegalArgumentException If the branch is not a well-formatted branch info string
		 */
		public static BranchInfo parseBranch(String vcsBranchName) throws IllegalArgumentException {
			Matcher match = BRANCH_PATTERN.matcher(vcsBranchName);
			if (!match.matches())
				throw new IllegalArgumentException("Branch is not well-formatted: " + vcsBranchName);
			return new BranchInfo(match.group("name"), //
				Integer.parseInt(match.group("major")), //
				Integer.parseInt(match.group("minor")), //
				Integer.parseInt(match.group("patch")));
		}
	}

	/** @see CsvEntitySet#CsvEntitySet(File, File) */
	public VersionedEntities(File entityDirectory, File indexDirectory) throws IOException {
		super(entityDirectory, indexDirectory);
	}

	/**
	 * @param listener The listener to be notified of changes to the current {@link #getBranch() branch} of entities
	 * @param remoteOnly Whether the listener is only to be called for changes from the remote
	 * @return A Runnable to {@link Runnable#run()} to remove the listener from this entity set
	 */
	public abstract Runnable addListener(ChangeListener listener, boolean remoteOnly);

	/**
	 * <ol>
	 * <li>Checks the remote for changes (and pulls them down, if any)</li>
	 * <li>Merges changes to the current branch with any locally {@link #commit(String) committed} changes</li>
	 * <li>Pushes locally {@link #commit(String) committed} changes to the remote</li>
	 * </ol>
	 *
	 * @param onConflict Resolves any conflicts
	 * @return This entity set
	 * @throws IOException If an error occurs communicating with the remote or manipulating versions
	 * @throws IllegalStateException If there are uncommitted changes
	 */
	public abstract VersionedEntities checkAndPush(ConflictResolver onConflict) throws IOException, IllegalStateException;

	/**
	 * Commits all changes locally. Use {@link #checkAndPush(ConflictResolver)} to push the changes to the remote.
	 *
	 * @param message A description of the changes in the commit. If null is given, a message may be auto-generated.
	 * @return This entity set
	 * @throws IOException If an error occurs committing
	 */
	public abstract VersionedEntities commit(String message) throws IOException;

	/**
	 * @return The current entity branch
	 * @throws IOException If an error occurs retrieving the information
	 */
	public abstract BranchInfo getBranch() throws IOException;

	/**
	 * Creates a new branch without committing
	 *
	 * @param newBranchName The new {@link BranchInfo#getBranchName() name} for the branch, or null to keep the current branch name
	 * @param changeLevel 0 for no change--used for just changing the branch name, 1 for a major revision, 2 for a minor revision, 3 for a
	 *        patch revision
	 * @return This entity set
	 * @throws IOException If an error occurs creating the branch
	 */
	public abstract VersionedEntities branch(String newBranchName, int changeLevel) throws IOException;
}
