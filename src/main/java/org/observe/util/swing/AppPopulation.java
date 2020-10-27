package org.observe.util.swing;

import java.awt.Component;
import java.awt.Desktop;
import java.awt.EventQueue;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.io.Writer;
import java.lang.reflect.InvocationTargetException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedList;
import java.util.List;
import java.util.TimeZone;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JOptionPane;

import org.observe.Observable;
import org.observe.ObservableValue;
import org.observe.SettableValue;
import org.observe.SimpleObservable;
import org.observe.collect.ObservableCollection;
import org.observe.config.ObservableConfig;
import org.observe.config.ObservableConfig.ObservableConfigPersistence;
import org.observe.config.SyncValueSet;
import org.observe.util.TypeTokens;
import org.qommons.QommonsUtils;
import org.qommons.QommonsUtils.TimePrecision;
import org.qommons.collect.BetterSortedSet;
import org.qommons.collect.CircularArrayList;
import org.qommons.config.QommonsConfig;
import org.qommons.io.FileBackups;
import org.qommons.io.FileUtils;
import org.qommons.io.Format;
import org.qommons.threading.QommonsTimer;
import org.xml.sax.SAXException;

public class AppPopulation {

	public static class ObservableUiBuilder extends WindowPopulation.DefaultWindowBuilder<JFrame, ObservableUiBuilder> {
		private File theDefaultConfigLocation;
		private String theConfigName;
		private List<File> theOldConfigLocations;
		private List<String> theOldConfigNames;
		private URL theErrorReportLink;
		private BiConsumer<StringBuilder, Boolean> theErrorReportInstructions;
		private Consumer<ObservableConfig> theConfigInit;
		private boolean isCloseWithoutSaveEnabled;
		private volatile boolean isClosingWithoutSave;
		private Consumer<FileBackups.Builder> theBackups;
		private AboutMenuBuilder<?> theAboutMenu;

		public ObservableUiBuilder() {
			super(new JFrame(), Observable.empty(), true);
		}

		public ObservableUiBuilder withConfigAt(String configLocation) {
			return withConfigAt(new File(configLocation));
		}

		public ObservableUiBuilder withConfigAt(File configLocation) {
			theDefaultConfigLocation = configLocation;
			return this;
		}

		public ObservableUiBuilder withConfig(String configName) {
			theConfigName = configName;
			return this;
		}

		public ObservableUiBuilder withOldConfigAt(String configLocation) {
			return withOldConfigAt(new File(configLocation));
		}

		public ObservableUiBuilder withOldConfigAt(File configLocation) {
			if (theOldConfigLocations == null)
				theOldConfigLocations = new LinkedList<>();
			theOldConfigLocations.add(configLocation);
			return this;
		}

		public ObservableUiBuilder withOldConfig(String configName) {
			if (theOldConfigNames == null)
				theOldConfigNames = new LinkedList<>();
			theOldConfigNames.add(configName);
			return this;
		}

		public ObservableUiBuilder withBackups(Consumer<FileBackups.Builder> backups) {
			theBackups = backups;
			return this;
		}

		public ObservableUiBuilder withConfigInit(Consumer<ObservableConfig> configInit) {
			theConfigInit = configInit;
			return this;
		}

		/**
		 * @param configInit The resource path to an initial XML configuration to populate
		 * @return This builder
		 */
		public ObservableUiBuilder withConfigInit(Class<?> clazz, String configInit) {
			return withConfigInit(config -> {
				QommonsConfig initConfig;
				try {
					initConfig = QommonsConfig.fromXml(clazz.getResource(configInit));
				} catch (IOException e) {
					System.err.println("Could not find initial config");
					e.printStackTrace();
					return;
				}
				populate(config, initConfig);
			});
		}

		private void populate(ObservableConfig config, QommonsConfig initConfig) {
			config.setName(initConfig.getName());
			config.setValue(initConfig.getValue());
			SyncValueSet<? extends ObservableConfig> subConfigs = config.getAllContent();
			int configIdx = 0;
			for (QommonsConfig initSubConfig : initConfig.subConfigs()) {
				if (configIdx < subConfigs.getValues().size())
					populate(subConfigs.getValues().get(configIdx), initSubConfig);
				else
					populate(config.addChild(initSubConfig.getName()), initSubConfig);
				configIdx++;
			}
		}

		public ObservableUiBuilder withErrorReporting(String link, BiConsumer<StringBuilder, Boolean> instructions) {
			try {
				return withErrorReporting(new URL(link), instructions);
			} catch (MalformedURLException e) {
				e.printStackTrace();
				throw new IllegalArgumentException("Bad URL: " + link);
			}
		}

		public ObservableUiBuilder withErrorReporting(URL link, BiConsumer<StringBuilder, Boolean> instructions) {
			theErrorReportLink = link;
			theErrorReportInstructions = instructions;
			withMenuBar(bar -> bar.withMenu("File", fileMenu -> {
				fileMenu.withAction("Report Error or Request Feature", __ -> {
					if (theErrorReportInstructions != null) {
						WindowPopulation.populateDialog(null, null, true)//
						.withTitle("Report Error/Request Feature")//
						.withVContent(content -> {
							String gotoText = new StringBuilder().append("<html>Go to <a href=\"").append(theErrorReportLink)
								.append("\">").append(theErrorReportLink).append("</a><br>").toString();
							content.addLabel(null, ObservableValue.of(gotoText), Format.TEXT, label -> {
								label.onClick(evt -> {
									try {
										Desktop.getDesktop().browse(theErrorReportLink.toURI());
									} catch (IOException | URISyntaxException | RuntimeException e) {
										JOptionPane.showMessageDialog(getWindow(),
											"Unable to open browser. Go to " + theErrorReportLink, "Unable To Open Browser",
											JOptionPane.ERROR_MESSAGE);
										e.printStackTrace();
									}
								});
							});
							if (theErrorReportInstructions != null) {
								StringBuilder msg = new StringBuilder("<html>");
								theErrorReportInstructions.accept(msg, false);
								content.addLabel(null, ObservableValue.of(msg.toString()), Format.TEXT, null);
							}
						}).run(getWindow());
					} else {
						try {
							Desktop.getDesktop().browse(theErrorReportLink.toURI());
						} catch (IOException | URISyntaxException | RuntimeException e) {
							JOptionPane.showMessageDialog(getWindow(), "Unable to open browser. Go to " + theErrorReportLink,
								"Unable To Open Browser", JOptionPane.ERROR_MESSAGE);
							e.printStackTrace();
						}
					}
				}, null);
			}));
			return this;
		}

		public ObservableUiBuilder enableCloseWithoutSave() {
			isCloseWithoutSaveEnabled = true;
			withMenuBar(bar -> bar.withMenu("File", fileMenu -> {
				fileMenu.withAction("Close Without Save", __ -> {
					if (JOptionPane.showConfirmDialog(getWindow(),
						"<html>This will cause all changes since the app was opened to be discarded.<br>Close the app?",
						"Exit Without Saving?", JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION)
						return;
					isClosingWithoutSave = true;
					System.exit(0);
				}, null);
			}));
			return this;
		}

		private static final Pattern VERSION_PATTERN = Pattern.compile("v?([0-9]+)\\.([0-9]+)\\.([0-9]+)");

		public ObservableUiBuilder withAbout(Class<?> appClass, Consumer<AboutMenuBuilder<?>> about) {
			if (theAboutMenu == null) {
				theAboutMenu = new AboutMenuBuilder<>(appClass, new JDialog(), Observable.empty(), false)//
					.withTitle("About " + (getTitle() == null ? "" : getTitle().get()))//
					.modal(false);
			}
			about.accept(theAboutMenu);
			withMenuBar(bar -> bar.withMenu("Help", helpMenu -> helpMenu.withAction("About", __ -> {
				theAboutMenu.run(getWindow());
			}, null)));
			return this;
		}

		public ObservableUiBuilder systemLandF() {
			ObservableSwingUtils.systemLandF();
			return this;
		}

		public JFrame build(Function<ObservableConfig, Component> app) {
			build((config, onBuilt) -> onBuilt.accept(app.apply(config)));
			return getWindow();
		}

		public void build(BiConsumer<ObservableConfig, Consumer<Component>> app) {
			String configName = theConfigName;
			if (configName == null) {
				if (theDefaultConfigLocation == null)
					throw new IllegalStateException("No configuration set to initialize configuration");
				else if (!theDefaultConfigLocation.exists())
					throw new IllegalStateException("Config file does not exist");
				else if (theDefaultConfigLocation.isDirectory())
					throw new IllegalStateException("Config file is not a file");
			}
			String configFileLoc = null;
			if (configName != null)
				configFileLoc = System.getProperty(configName + ".config");
			if (configFileLoc == null && theDefaultConfigLocation != null) {
				configFileLoc = theDefaultConfigLocation.getPath();
			}
			if (configFileLoc == null)
				configFileLoc = "./" + configName + ".config";
			if (!new File(configFileLoc).exists() && (theOldConfigNames != null || theOldConfigLocations != null)) {
				if (theOldConfigNames != null) {
					configFileLoc = System.getProperty(configName + ".config");
					if ((configFileLoc == null || !new File(configFileLoc).canWrite()) && theDefaultConfigLocation != null)
						configFileLoc = theDefaultConfigLocation.getPath();
					if (configFileLoc == null)
						configFileLoc = "./" + configName + ".config";
					boolean found = false;
					for (String oldConfigName : theOldConfigNames) {
						String oldConfigLoc = System.getProperty(oldConfigName + ".config");
						if (oldConfigLoc != null && new File(oldConfigLoc).exists()) {
							new File(oldConfigLoc).renameTo(new File(configFileLoc));
							found = true;
							break;
						}
					}
					if (!found) {
						for (File oldConfigLoc : theOldConfigLocations) {
							if (oldConfigLoc.exists()) {
								oldConfigLoc.renameTo(new File(configFileLoc));
								break;
							}
						}
					}
				}
			}
			ObservableConfig config = ObservableConfig.createRoot("config");
			ObservableConfig.XmlEncoding encoding = ObservableConfig.XmlEncoding.DEFAULT;
			File configFile = new File(configFileLoc);
			if (theErrorReportLink != null || theErrorReportInstructions != null) {
				String errorFileName;
				int lastDot = configFile.getName().lastIndexOf('.');
				if (lastDot >= 0)
					errorFileName = configFile.getName().substring(0, lastDot) + ".errors.txt";
				else
					errorFileName = configFile.getName() + ".errors.txt";
				File errorFile = new File(configFile.getParentFile(), errorFileName);
				new SystemOutputHandler(configFile, errorFile);
			}
			FileBackups backups;
			if (theBackups != null) {
				FileBackups.Builder backupBuilder = FileBackups.build(FileUtils.better(configFile));
				theBackups.accept(backupBuilder);
				backups = backupBuilder.build();
			} else
				backups = null;
			if (configFile.exists()) {
				try {
					try (InputStream configStream = new BufferedInputStream(new FileInputStream(configFile))) {
						ObservableConfig.readXml(config, configStream, encoding);
					}
				} catch (IOException | SAXException e) {
					if (backups == null) {
						System.err.println("Could not read config file " + configFileLoc);
						e.printStackTrace();
					} else {
						System.out.println("Could not read config file " + configFileLoc);
						e.printStackTrace(System.out);
						restoreBackup(config, backups, () -> build2(config, configFile, backups, app), null);
					}
				}
				if (configName != null)
					config.setName(configName);
				build2(config, configFile, backups, app);
			} else {
				restoreBackup(config, backups, () -> build2(config, configFile, backups, app), () -> {
					if (configName != null)
						config.setName(configName);
					if (theConfigInit != null)
						theConfigInit.accept(config);
					build2(config, configFile, backups, app);
				});
			}
		}

		private void build2(ObservableConfig config, File configFile, FileBackups backups,
			BiConsumer<ObservableConfig, Consumer<Component>> app) {
			ObservableConfigPersistence<IOException> actuallyPersist = ObservableConfig.toFile(configFile,
				ObservableConfig.XmlEncoding.DEFAULT);
			config.persistOnShutdown(new ObservableConfig.ObservableConfigPersistence<IOException>() {
				@Override
				public void persist(ObservableConfig config2) throws IOException {
					if (isClosingWithoutSave)
						return;
					if (backups != null)
						backups.saveLatest(true);
					actuallyPersist.persist(config2);
				}
			}, ex -> {
				System.err.println("Could not persist UI config");
				ex.printStackTrace();
			});
			Runnable buildApp = () -> {
				app.accept(config, ui -> {
					if (EventQueue.isDispatchThread())
						build3(config, ui);
					else {
						try {
							EventQueue.invokeAndWait(() -> {
								build3(config, ui);
							});
						} catch (InvocationTargetException | InterruptedException e) {
							throw new IllegalStateException(e);
						}
					}
				});
			};
			if (EventQueue.isDispatchThread())
				buildApp.run();
			else
				EventQueue.invokeLater(buildApp);
		}

		private void restoreBackup(ObservableConfig config, FileBackups backups, Runnable onBackup, Runnable onNoBackup) {
			BetterSortedSet<Instant> backupTimes = backups == null ? null : backups.getBackups();
			if (backupTimes == null || backupTimes.isEmpty()) {
				if (onNoBackup != null)
					onNoBackup.run();
				return;
			}
			SettableValue<Instant> selectedBackup = SettableValue.build(Instant.class).safe(false).build();
			Format<Instant> dateFormat = Format.flexibleDate("MMM dd yyyy", TimeZone.getDefault());
			JFrame[] frame = new JFrame[1];
			boolean[] backedUp = new boolean[1];
			frame[0] = WindowPopulation.populateWindow(null, null, true, false)//
				.withTitle(getTitle().get() == null ? "Backup" : getTitle().get() + " Backup").withIcon(getIcon())//
				.withVContent(content -> {
					content.addLabel(null, "Your configuration is missing or has been corrupted", null)//
					.addLabel(null, "Please choose a backup to restore", null)//
					.addTable(ObservableCollection.of(TypeTokens.get().of(Instant.class), backupTimes.reverse()), table -> {
						table.fill().withColumn("Date", String.class, t -> dateFormat.format(t), col -> col.withWidths(100, 250, 500))//
						.withSelection(selectedBackup, true);
					}).addButton("Backup", __ -> {
						try {
							populate(config,
								QommonsConfig.fromXml(QommonsConfig.getRootElement(backups.getBackup(selectedBackup.get()).read())));
							backedUp[0] = true;
						} catch (IOException e) {
							e.printStackTrace();
						}
						frame[0].setVisible(false);
					}, btn -> btn.disableWith(selectedBackup.map(t -> t == null ? "Select a Backup" : null)));
				}).run(null).getWindow();
			frame[0].addComponentListener(new ComponentAdapter() {
				@Override
				public void componentHidden(ComponentEvent e) {
					if (backedUp[0]) {
						if (onBackup != null)
							onBackup.run();
					} else {
						if (onNoBackup != null)
							onNoBackup.run();
					}
				}
			});
		}

		public static class AboutMenuBuilder<A extends AboutMenuBuilder<A>> extends WindowPopulation.DefaultDialogBuilder<JDialog, A> {
			private final Class<?> theAppClass;
			private final SettableValue<String> theCurrentVersion;
			private Supplier<String> theLatestReleaseGetter;
			private String theLatestRelease;
			private final ObservableValue<String> theLatestVersionValue;
			private final SettableValue<Consumer<String>> theUpgrader;

			AboutMenuBuilder(Class<?> appClass, JDialog dialog, Observable<?> until, boolean disposeOnClose) {
				super(dialog, until, disposeOnClose);
				theAppClass = appClass;
				theCurrentVersion = SettableValue.build(String.class).safe(false).build();
				theUpgrader = SettableValue.build(TypeTokens.get().keyFor(Consumer.class).<Consumer<String>> parameterized(String.class))
					.safe(false).build();
				SimpleObservable<Object> shown = SimpleObservable.build().safe(false).build();
				shown.act(__ -> {
					if (theLatestReleaseGetter != null)
						theLatestRelease = theLatestReleaseGetter.get();
					else
						theLatestRelease = null;
				});
				theLatestVersionValue = ObservableValue.of(TypeTokens.get().STRING, () -> theLatestRelease, () -> 1, // Hopefully nobody
																														// asks
					shown);
				getWindow().addComponentListener(new ComponentAdapter() {
					@Override
					public void componentShown(ComponentEvent e) {
						shown.onNext(e);
					}
				});

				withVContent(content -> {
					content.addLabel(null, theCurrentVersion.map(String.class, v -> "Version: " + (v == null ? "Unknown" : v)), Format.TEXT,
						label -> label.visibleWhen(theCurrentVersion.map(v -> v != null)));
					content.addLabel(null, theLatestVersionValue, Format.TEXT,
						label -> label.visibleWhen(theLatestVersionValue.map(v -> v != null)));
					content.addButton("Upgrade", __ -> {
						theUpgrader.get().accept(theLatestRelease);
					}, btn -> btn.visibleWhen(theUpgrader
						.transform(tx -> tx.combineWith(theCurrentVersion).combineWith(theLatestVersionValue).combine((u, cv, lv) -> {
							return u != null && cv != null && lv != null;
						}))));
				});
			}

			public A withCurrentVersionFromManifest() {
				Package pkg = theAppClass.getPackage();
				String version = null;
				while (pkg != null && (version = pkg.getImplementationVersion()) == null) {
					int dotIdx = pkg.getName().lastIndexOf('.');
					if (dotIdx < 0)
						break;
					pkg = Package.getPackage(pkg.getName().substring(0, dotIdx));
				}
				withCurrentVersion(version == null ? "Unknown" : version);
				return (A) this;
			}

			public A withCurrentVersion(String currentVersion) {
				theCurrentVersion.set(currentVersion, null);
				return (A) this;
			}

			public A withLatestVersion(Supplier<String> latestVersion) {
				theLatestReleaseGetter = latestVersion;
				return (A) this;
			}

			public A withUpgrade(Consumer<String> upgrade) {
				theUpgrader.set(upgrade, null);
				return (A) this;
			}
		}

		static class SystemOutput {
			final byte[] content;
			final Instant time;
			final boolean error;

			SystemOutput(byte[] content, Instant time, boolean error) {
				this.content = content;
				this.time = time;
				this.error = error;
			}
		}

		class SystemOutputHandler {
			private final File theConfigFile;
			private final File theErrorFile;
			private final ByteArrayOutputStream theBytes;
			private final Duration theAccumulationTime;
			private final Duration theErrorCascadeTolerance;
			private final CircularArrayList<SystemOutput> theOutput;
			private volatile Instant theLastWriteTime;
			private volatile boolean theLastWriteError;
			private volatile boolean isWritingError;

			SystemOutputHandler(File configFile, File errorFile) {
				theConfigFile = configFile;
				theErrorFile = errorFile;
				theBytes = new ByteArrayOutputStream(64 * 1028);
				theAccumulationTime = Duration.ofSeconds(2);
				theErrorCascadeTolerance = Duration.ofMillis(100);
				theOutput = CircularArrayList.build().build();

				System.setOut(new HandlerPrintStream(System.out, false));
				System.setErr(new HandlerPrintStream(System.err, true));
			}

			synchronized void report(int b, boolean error) throws IOException {
				startReport(error);
				theBytes.write(b);
			}

			synchronized void report(byte[] b, int off, int len, boolean error) throws IOException {
				startReport(error);
				theBytes.write(b, off, len);
			}

			private void startReport(boolean error) {
				Instant now = Instant.now();
				boolean timeDiff = !now.equals(theLastWriteTime);
				if (timeDiff) {
					if (!isWritingError) {
						SystemOutput output = theOutput.peekFirst();
						while (output != null && output.time.plus(theAccumulationTime).compareTo(now) < 0) {
							theOutput.removeFirst();
							output = theOutput.peekFirst();
						}
					}
					theLastWriteTime = now;
				}
				if (theBytes.size() == 0) {//
				} else if (error != theLastWriteError || timeDiff) {
					byte[] output = theBytes.toByteArray();
					theBytes.reset();
					theOutput.add(new SystemOutput(output, now, error));
				}
				if (error) {
					isWritingError = true;
					QommonsTimer.getCommonInstance().doAfterInactivity(this, this::alertUser, theErrorCascadeTolerance);
				}
			}

			private void alertUser() {
				if (!EventQueue.isDispatchThread()) {
					try {
						EventQueue.invokeAndWait(this::alertUser);
					} catch (InvocationTargetException | InterruptedException e) {
						e.printStackTrace(System.out);
					}
					return;
				}
				isWritingError = false;
				try (OutputStream out = new BufferedOutputStream(new FileOutputStream(theErrorFile));
					Writer writer = new OutputStreamWriter(out)) {
					boolean lastLineEnd = true;
					boolean lastError = false;
					Instant lastTime = Instant.ofEpochMilli(0);
					SystemOutput output = theOutput.pollFirst();
					while (output != null) {
						boolean timeDiff = !lastTime.equals(output.time);
						if (lastError != output.error || (lastLineEnd && timeDiff)) {
							writer.append('[').append(output.error ? "ERR" : "OUT").append(' ');
							writer.append(QommonsUtils.printRelativeTime(output.time.toEpochMilli(), lastTime.toEpochMilli(),
								TimePrecision.MILLIS, TimeZone.getDefault(), 0, null));
							writer.append("] ");
							writer.flush();
						}
						lastError = output.error;
						lastTime = output.time;
						out.write(output.content);
						byte lastChar = output.content[output.content.length - 1];
						lastLineEnd = lastChar == '\n' || lastChar == '\r';
						output = theOutput.pollFirst();
					}
					WindowPopulation.populateDialog(null, null, true)//
					.withTitle("Unhandled Application Error")//
					.withVContent(content -> {
						String title = getTitle() == null ? null : getTitle().get();
						if (title != null)
							content.addLabel(null, ObservableValue.of(title + " has encountered an error."), Format.TEXT, null);
						else
							content.addLabel(null, ObservableValue.of("An error has been encountered."), Format.TEXT, null);
						if (theErrorReportLink != null) {
							String gotoText = new StringBuilder().append("<html>Go to <a href=\"").append(theErrorReportLink)
								.append("\">").append(theErrorReportLink).append("</a><br>").toString();
							content.addLabel(null, ObservableValue.of(gotoText), Format.TEXT, label -> {
								label.onClick(evt -> {
									try {
										Desktop.getDesktop().browse(theErrorReportLink.toURI());
									} catch (IOException | URISyntaxException | RuntimeException e) {
										JOptionPane.showMessageDialog(getWindow(),
											"Unable to open browser. Go to " + theErrorReportLink, "Unable To Open Browser",
											JOptionPane.ERROR_MESSAGE);
										e.printStackTrace();
									}
								});
							});
						} else
							content.addLabel(null, ObservableValue.of("Please report it."), Format.TEXT, null);
						content.addLabel(null,
							ObservableValue.of("<html>Please attach the captured error output file: <a href=\"thisDoesntMatter.html\">"
								+ theErrorFile.getPath() + "</a>"),
							Format.TEXT, label -> {
								label.onClick(evt -> {
									try {
										/* We don't have Java 9, but this hack seems to work
										 * For some reason, I was encountering theErrorFile.getParentFile()==null
										 * So I had to work around it
										 * getPath() was also not returning parents
										 * String path = theErrorFile.toString();
										 * JOptionPane.showMessageDialog(null, path);
										 * int lastSlash = path.lastIndexOf('/');
										 * if (lastSlash < 0 || lastSlash < path.lastIndexOf('\\'))
										 * lastSlash = path.lastIndexOf('\\');
										 * if (lastSlash < 0)
										 * return; // ??
										 * String parentPath = path.substring(0, lastSlash);
										 */
										// Desktop.getDesktop().browse(new File(parentPath).toURI());
										Desktop.getDesktop().browse(theErrorFile.getAbsoluteFile().getParentFile().toURI());
									} catch (IOException | RuntimeException e) {
										e.printStackTrace();
									}
								});
							});
						if (theErrorReportInstructions != null) {
							StringBuilder msg = new StringBuilder("<html>");
							theErrorReportInstructions.accept(msg, true);
							content.addLabel(null, ObservableValue.of(msg.toString()), Format.TEXT, null);
						}
						String msg = "<html>You may want to consider backing up your data file (" + theConfigFile.getPath() + ")";
						if (isCloseWithoutSaveEnabled)
							msg += "<br>" + " or closing the app without saving your changes (File->Close Without Save)";
						content.addLabel(null, ObservableValue.of(msg), Format.TEXT, null);
					}).run(getWindow());
				} catch (IOException e) {
					System.err.println("Could not write error output");
					e.printStackTrace();
				}
			}

			class HandlerPrintStream extends PrintStream {
				HandlerPrintStream(PrintStream systemStream, boolean error) {
					super(new OutputStream() {
						@Override
						public void write(int b) throws IOException {
							systemStream.write(b);
							report(b, error);
						}

						@Override
						public void write(byte[] b) throws IOException {
							systemStream.write(b);
							report(b, 0, b.length, error);
						}

						@Override
						public void write(byte[] b, int off, int len) throws IOException {
							systemStream.write(b, off, len);
							report(b, off, len, error);
						}
					});
				}
			}
		}

		private JFrame build3(ObservableConfig config, Component ui) {
			return withBounds(config).withContent(ui).run(null).getWindow();
		}
	}

}
