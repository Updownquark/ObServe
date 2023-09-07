<?xml version="1.0" encoding="UTF-8"?>

<quick xmlns:base="Quick-Base v0.1" xmlns:expresso="Expresso-Base v0.1" with-extension="window"
	title="`Simple Quick Tree Demo`" close-action="exit" x="app.x" y="app.y" width="app.w" height="app.h">
	<head>
		<models>
			<model name="app">
				<value name="x" type="int" />
				<value name="y" type="int" />
				<value name="w" init="200" />
				<value name="h" init="500" />

				<value name="root">org.observe.file.ObservableFile.observe(
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
			<tree root="app.root" active-node-name="node" children="node.listFiles()" leaf="!node.isDirectory()">
				<column>
					<label value="columnValue.getName()" />
				</column>
			</tree>
		</box>
	</scroll>
</quick>
