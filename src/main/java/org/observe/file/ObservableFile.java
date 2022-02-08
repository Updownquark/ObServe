package org.observe.file;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.text.ParseException;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import org.observe.collect.DataControlledCollection;
import org.observe.collect.ObservableCollection;
import org.observe.collect.ObservableCollectionBuilder;
import org.observe.collect.ObservableCollectionBuilder.DataControlAutoRefresher;
import org.observe.collect.ObservableCollectionBuilder.DataControlledCollectionBuilderImpl;
import org.observe.util.TypeTokens;
import org.qommons.ThreadConstraint;
import org.qommons.collect.CollectionLockingStrategy;
import org.qommons.collect.StampedLockingStrategy;
import org.qommons.ex.ExConsumer;
import org.qommons.io.BetterFile;
import org.qommons.io.FileUtils;
import org.qommons.io.FileUtils.DirectorySyncResults;
import org.qommons.io.Format;

public class ObservableFile implements BetterFile {
	public static class ObservableFileSet {
		private final DataControlAutoRefresher theRefresher;
		private CollectionLockingStrategy theLocking;

		public ObservableFileSet(DataControlAutoRefresher refresher, CollectionLockingStrategy locking) {
			theRefresher = refresher;
		}

		public CollectionLockingStrategy getLocking() {
			return theLocking;
		}

		public DataControlAutoRefresher getRefresher() {
			return theRefresher;
		}
	}

	public static class FileFormat implements Format<ObservableFile> {
		private final BetterFile.FileDataSource theFileSource;
		private final ObservableFileSet theFileSet;
		private final ObservableFile theWorkingDir;
		private final boolean allowNull;

		public FileFormat(BetterFile.FileDataSource fileSource, ObservableFile workingDir, boolean allowNull) {
			this(fileSource, workingDir != null ? workingDir.getFileSet() : getDefaultFileSet(), workingDir, allowNull);
		}

		public FileFormat(BetterFile.FileDataSource fileSource, ObservableFileSet fileSet, ObservableFile workingDir, boolean allowNull) {
			theFileSource = fileSource;
			theFileSet = fileSet;
			theWorkingDir = workingDir;
			this.allowNull = allowNull;
		}

		public BetterFile getWorkingDir() {
			return theWorkingDir;
		}

		@Override
		public void append(StringBuilder text, ObservableFile value) {
			if (value != null)
				text.append(value);
		}

		@Override
		public ObservableFile parse(CharSequence text) throws ParseException {
			if (text.length() == 0) {
				if (allowNull)
					return null;
				else
					throw new ParseException("Empty content not allowed", 0);
			} else {
				try {
					return ObservableFile.observe(theFileSet, BetterFile.at(theFileSource, text.toString()));
				} catch (IllegalArgumentException e) {
					if (theWorkingDir != null)
						return theWorkingDir.at(text.toString());
					throw e;
				}
			}
		}
	}

	private static volatile ObservableFileSet DEFAULT_FILE_REFRESHER;

	public static ObservableFileSet getDefaultFileSet() {
		if (DEFAULT_FILE_REFRESHER == null) {
			synchronized (ObservableFile.class) {
				if (DEFAULT_FILE_REFRESHER == null) {
					DEFAULT_FILE_REFRESHER = new ObservableFileSet(//
						new ObservableCollectionBuilder.DefaultDataControlAutoRefresher(Duration.ofSeconds(1)), null);
					DEFAULT_FILE_REFRESHER.theLocking = new StampedLockingStrategy(DEFAULT_FILE_REFRESHER, ThreadConstraint.ANY);
				}
			}
		}
		return DEFAULT_FILE_REFRESHER;
	}

	public static ObservableFile observe(BetterFile file) {
		return observe(getDefaultFileSet(), file);
	}

	public static ObservableFile observe(ObservableFileSet fileSet, BetterFile file) {
		if (file instanceof ObservableFile)
			return (ObservableFile) file;
		return new ObservableFile(fileSet, null, file);
	}

	private final ObservableFileSet theFileSet;
	private ObservableFile theParent;
	private final BetterFile theFile;
	private WeakReference<DataControlledCollection<? extends ObservableFile, ?>> theContents;

	private volatile long theCachedLastModified;
	private volatile long theCachedSize;

	ObservableFile(ObservableFileSet fileSet, ObservableFile parent, BetterFile file) {
		theFileSet = fileSet;
		theParent = parent;
		theFile = file;
		theCachedLastModified = getLastModified();
		theCachedSize = length();
	}

	boolean checkChanged() {
		long lastMod = getLastModified();
		long size = length();
		if (lastMod != theCachedLastModified || size != theCachedSize) {
			theCachedLastModified = lastMod;
			theCachedSize = size;
			return true;
		} else
			return false;
	}

	public ObservableFileSet getFileSet() {
		return theFileSet;
	}

	@Override
	public FileDataSource getSource() {
		return theFile.getSource();
	}

	@Override
	public String getName() {
		return theFile.getName();
	}

	@Override
	public String getPath() {
		return theFile.getPath();
	}

	@Override
	public boolean exists() {
		return theFile.exists();
	}

	@Override
	public long getLastModified() {
		return theFile.getLastModified();
	}

	@Override
	public boolean get(FileBooleanAttribute attribute) {
		return theFile.get(attribute);
	}

	@Override
	public long length() {
		return theFile.length();
	}

	@Override
	public ObservableFile getRoot() {
		ObservableFile f = this;
		BetterFile p = theFile.getParent();
		while (p != null) {
			ObservableFile op = f.theParent;
			if (op == null)
				f.theParent = op = new ObservableFile(theFileSet, null, p);
			f = op;
			p = p.getParent();
		}
		return f;
	}

	@Override
	public ObservableFile getParent() {
		return theParent;
	}

	@Override
	public ObservableFile at(String path) {
		String[] pathArray = FileUtils.splitPath(path);
		ObservableFile parent = this;
		for (int i = 0; i < pathArray.length; i++) {
			parent = new ObservableFile(theFileSet, parent, theFile.at(pathArray[i]));
		}
		return parent;
	}

	@Override
	public List<? extends BetterFile> discoverContents(Consumer<? super BetterFile> onDiscovered, BooleanSupplier canceled) {
		return theFile.discoverContents(onDiscovered, canceled);
	}

	@Override
	public DataControlledCollection<? extends ObservableFile, ?> listFiles() {
		if (!isDirectory()) {
			return DataControlledCollection.empty(TypeTokens.get().of(ObservableFile.class));
		}
		DataControlledCollection<? extends ObservableFile, ?> contents = theContents == null ? null : theContents.get();
		if (contents == null) {
			synchronized (this) {
				contents = theContents == null ? null : theContents.get();
				if (contents == null) {
					ObservableCollectionBuilder<ObservableFile, ?> builder = ObservableCollection.build(ObservableFile.class)
						.withLocking(theFileSet.getLocking()).withDescription("Directory content of " + getPath());
					DataControlledCollectionBuilderImpl<ObservableFile, ? extends BetterFile, ?> dataBuilder;
					dataBuilder = (DataControlledCollectionBuilderImpl<ObservableFile, ? extends BetterFile, ?>) builder
						.withData(() -> theFile.listFiles());
					dataBuilder.refreshOnAccess(false).autoRefreshWith(theFileSet.getRefresher());
					dataBuilder.withEquals((f1, f2) -> f1.getName().equals(f2.getName())).withMaxRefreshFrequency(5);
					contents = dataBuilder.build(f -> new ObservableFile(theFileSet, this, f), //
						adjustment -> adjustment.commonUsesLeft((of, f) -> of.checkChanged()));
					theContents = new WeakReference<>(contents);
				}
			}
		}
		return contents;
	}

	@Override
	public InputStream read(long startFrom, BooleanSupplier canceled) throws IOException {
		return theFile.read(startFrom, canceled);
	}

	@Override
	public void delete(DirectorySyncResults results) throws IOException {
		theFile.delete(results);
	}

	@Override
	public ObservableFile create(boolean directory) throws IOException {
		theFile.create(directory);
		return this;
	}

	@Override
	public OutputStream write() throws IOException {
		return theFile.write();
	}

	@Override
	public boolean set(FileBooleanAttribute attribute, boolean value, boolean ownerOnly) {
		return theFile.set(attribute, value, ownerOnly);
	}

	@Override
	public boolean setLastModified(long lastModified) {
		return theFile.setLastModified(lastModified);
	}

	@Override
	public ObservableFile move(BetterFile newFile) throws IOException {
		BetterFile moved = theFile.move(newFile);
		if (theParent == null || Objects.equals(theFile.getParent(), newFile.getParent()))
			return new ObservableFile(theFileSet, theParent, moved);
		else
			return observe(moved);
	}

	@Override
	public void visitAll(ExConsumer<? super BetterFile, IOException> forEach, BooleanSupplier canceled) throws IOException {
		theFile.visitAll(forEach, canceled);
	}

	@Override
	public StringBuilder toUrl(StringBuilder str) {
		return theFile.toUrl(str);
	}

	@Override
	public int hashCode() {
		return theFile.hashCode();
	}

	@Override
	public boolean equals(Object obj) {
		return theFile.equals(obj);
	}

	@Override
	public String toString() {
		return theFile.toString();
	}

	public static DataControlledCollection<ObservableFile, ?> getRoots(FileDataSource dataSource) {
		ObservableCollectionBuilder.SortedBuilder<ObservableFile, ?> builder = ObservableCollection.build(ObservableFile.class)
			.sortBy(BetterFile.DISTINCT_NUMBER_TOLERANT).withLocking(ObservableFile.getDefaultFileSet().getLocking());
		DataControlledCollectionBuilderImpl<ObservableFile, ? extends BetterFile, ?> dataBuilder;
		dataBuilder = (DataControlledCollectionBuilderImpl<ObservableFile, ? extends BetterFile, ?>) builder
			.withData(() -> BetterFile.getRoots(dataSource));
		dataBuilder = dataBuilder.autoRefreshWith(ObservableFile.getDefaultFileSet().getRefresher()).refreshOnAccess(false);
		dataBuilder = dataBuilder.withEquals((f1, f2) -> f1.getName().equals(f2.getName())).withMaxRefreshFrequency(5);
		return dataBuilder.build(b -> ObservableFile.observe(b), adjustment -> adjustment.commonUsesLeft((of, f) -> of.checkChanged()));
	}
}
