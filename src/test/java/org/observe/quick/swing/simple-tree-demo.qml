<?xml version="1.0" encoding="UTF-8"?>

<quick xmlns:base="Quick-Base v0.1" xmlns:expresso="Expresso-Base v0.1" with-extension="window"
	title="`Simple Quick Tree Demo`" close-action="exit" x="app.x" y="app.y" width="app.w" height="app.h">
	<head>
		<imports>
			<import>org.observe.file.ObservableFile</import>
		</imports>
		<models>
			<model name="app">
				<value name="x" type="int" />
				<value name="y" type="int" />
				<value name="w" init="200" />
				<value name="h" init="500" />

				<value name="root">ObservableFile.observe(
					org.qommons.io.FileUtils.better(
						new java.io.File(
							System.getProperty("user.dir")
						)
					)
				)</value>
			</model>
		</models>
	</head>
	<scroll>
		<box role="content" layout="inline-layout" orientation="vertical" cross-align="justify">
			<tree active-node-name="node">
				<tree-node value="&quot;Root&quot;">
					<tree-node value="&quot;Numbers&quot;">
						<tree-node value="1" />
						<tree-node value="2" />
						<tree-node value="3" />
					</tree-node>
					<dynamic-tree-model value="app.root" children="node.listFiles()" leaf="!node.isDirectory()" />
				</tree-node>
				<column>
					<label value="columnValue instanceof ObservableFile ? ((ObservableFile) columnValue).getName() : columnValue.toString()"
						icon="columnValue instanceof ObservableFile ? (&quot;/icons/icons8-&quot;+ ( ((ObservableFile) columnValue).isFile() ? &quot;file-50&quot; : &quot;folder-16&quot;)+&quot;.png&quot;) : null" />
				</column>
			</tree>
		</box>
	</scroll>
</quick>
