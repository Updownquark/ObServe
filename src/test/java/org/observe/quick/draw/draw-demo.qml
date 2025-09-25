<?xml version="1.0" encoding="UTF-8"?>

<quick xmlns:quick="Quick-X v0.1" xmlns:draw="Quick-Draw-Demo v0.1" xmlns:config="Expresso-Config v0.1" with-extension="window"
	title="`Quick Draw Demo`" close-action="exit" x="config.windowX" y="config.windowY" width="config.windowW" height="config.windowH">
	<head>
		<imports>
			<import>org.observe.quick.draw.*</import>
			<import>org.qommons.Colors</import>
		</imports>
		<models>
			<model name="formats">
				<entity-config-format name="shapeFormat" type="ColoredShape">
					<entity-config-format role="sub-format" type="ColoredRectangle" sub-type-name="rectangle" />
					<entity-config-format role="sub-format" type="ColoredEllipse" sub-type-name="ellipse" />
					<entity-config-format role="sub-format" type="ColoredPolygon" sub-type-name="polygon" />
					<entity-config-format role="sub-format" type="ColoredText" sub-type-name="text" />
				</entity-config-format>
			</model>
			<config name="config" config-name="quick-draw-demo">
				<value name="windowX" type="int" config-path="window/x" />
				<value name="windowY" type="int" config-path="window/y" />
				<value name="windowW" type="int" config-path="window/width" default="1000" />
				<value name="windowH" type="int" config-path="window/height" default="800"/>

				<value name="transformX" type="int" config-path="transform/x" />
				<value name="transformY" type="int" config-path="transform/y" />
				<value name="transformW" type="int" config-path="transform/width" />
				<value name="transformH" type="int" config-path="transform/height" />

				<value name="translateX" type="int" config-path="translate/x" />
				<value name="translateY" type="int" config-path="translate/y" />
				<value name="scaleX" type="float" config-path="scale/x" default="1" />
				<value name="scaleY" type="float" config-path="scale/y" default="1" />
				<value name="scaleTogether" type="boolean" config-path="scale/together" default="true" />
				<value name="rotateAnchorX" type="int" config-path="rotate/anchor/x" />
				<value name="rotateAnchorY" type="int" config-path="rotate/anchor/y" />
				<value name="rotateAmount" type="float" config-path="rotate" />
				
				<value-set name="shapes" type="ColoredShape" format="formats.shapeFormat"/>
			</config>
			<model name="app">
				<list name="shapes" type="ColoredShape">config.shapes.getValues()</list>

				<value name="canvasWidth" type="int" />
				<value name="canvasHeight" type="int" />
				
				<value name="newRectX">Math.round(canvasWidth/4.0f)</value>
				<value name="newRectY">Math.round(canvasHeight/4.0f)</value>
				<value name="newRectWidth">Math.round(canvasWidth/2.0f)</value>
				<value name="newRectHeight">Math.round(canvasHeight/2.0f)</value>

				<value name="selected" type="ColoredShape" />
				<list name="toRemove" type="ColoredShape" />

				<value name="globalTransformOpen" init="false" />			
					
				<value name="editBgColor" type="ColoredShape" />
				<value name="editBorderColor" type="BorderedShape" />
			</model>
		</models>
		<style-sheet>
			<!--<import-style-sheet name="searcher" ref="quick-testing.qss" />-->
		</style-sheet>
	</head>
	<menu-bar>
		<menu>Settings
			<menu-item action="app.globalTransformOpen=true">Global Transform</menu-item>
		</menu>
	</menu-bar>
	<box layout="inline-layout" orientation="horizontal" main-align="justify">
		<general-dialog visible="app.globalTransformOpen" title="`Global Tranform`" modal="false"
			x="config.transformX" y="config.transformY" width="config.transformW" height="config.transformH" >
			<field-panel>
				<box field-label="`Translate:`" layout="inline-layout" orientation="horizontal">
					<spinner value="config.translateX" columns="5" />
					<spinner value="config.translateY" columns="5" />
				</box>
				<box field-label="`Scale:`" layout="inline-layout" orientation="horizontal">
					<model>
						<transform name="scaleX" source="config.scaleX">
							<filter-accept source-as="v" test="v&lt;=0.001 ? `Scale is too small` : null" />
							<filter-accept source-as="v" test="v&lt;=0 ? `Scale must be positive` : null" />
						</transform>
						<transform name="scaleY" source="config.scaleY">
							<filter-accept source-as="v" test="v&lt;=0.001 ? `Scale is too small` : null" />
							<filter-accept source-as="v" test="v&lt;=0 ? `Scale must be positive` : null" />
						</transform>
					</model>
					<check-box value="config.scaleTogether">`Together:`</check-box>
					<spinner value="scaleX" columns="7" value-name="v" next="v*1.25f" previous="v/1.25f" />
					<spinner value="scaleY" visible="!config.scaleTogether" columns="7" value-name="v" next="v*1.25f" previous="v/1.25f" />
				</box>
				<box field-label="`Rotate:`" layout="inline-layout" orientation="horizontal">
					<spinner value="config.rotateAmount" columns="7" value-name="v" next="v+15" previous="v-15" />
					<label>&#x00b0; Around</label>
					<spinner value="config.rotateAnchorX" columns="5" />
					<spinner value="config.rotateAnchorY" columns="5" />
				</box>
				<box layout="inline-layout" orientation="horizontal" main-align="center">
					<button action="{
						config.translateX=0,
						config.translateY=0,
						config.scaleX=1,
						config.scaleY=1,
						config.rotateAmount=0}">`Reset`</button>
				</box>
			</field-panel>
		</general-dialog>
		<general-dialog visible="app.editBgColor!=null" title="`Edit `+app.editBgColor.getName()+` Color`" modal="false">
			<model>
				<field-value name="color" source="app.editBgColor.getColor()"
					target-as="newValue" save="app.editBgColor.setColor(newValue)" />
			</model>
			<color-chooser value="color" />
		</general-dialog>
		<general-dialog visible="app.editBorderColor!=null" title="`Edit `+app.editBorderColor.getName()+` Border Color`" modal="false">
			<model>
				<field-value name="color" source="app.editBorderColor.getBorderColor()"
					target-as="newValue" save="app.editBorderColor.setBorderColor(newValue)" />
			</model>
			<color-chooser value="color" />
		</general-dialog>
		<confirm visible="!app.toRemove.isEmpty()" title="`Remove `+itemText+`?`"
			on-confirm="{app.shapes.removeAll(app.toRemove), app.toRemove.clear()}" on-cancel="app.toRemove.clear()">
			<model>
				<value name="single">app.toRemove.size()==1 ? app.toRemove.peekFirst() : null</value>
				<value name="itemText">single!=null ? (single.getType()+` `+single.getName()) : (app.toRemove.size()+` Shapes`)</value>
			</model>
			<label value="`Permanently delete `+itemText+`?`" />
		</confirm>
		<split orientation="vertical">
			<super-table rows="app.shapes" active-value-name="shape" selection="app.selected" searchable="false">
				<model>
					<value name="trash" type="ColoredShape" />
				</model>
				<titled-border title="`Shapes`" />
				<with-row-dragging />
				<column name="`Type`" value="shape.getType()" width="80"/>
				<column name="``" value="shape" width="16">
					<model>
						<value name="bounds">shape.getBounds()</value>
						<value name="scale">16.0f/Math.max(1, bounds.maxDimension)</value>
					</model>
					<canvas width="16" height="16">
						<shape-view>
							<translate x="-bounds.centerX+bounds.maxDimension/2" y="-bounds.centerY+bounds.maxDimension/2" />
							<scale x="scale" y="scale" />
							<demo-shape-collection shapes="{shape}" selected="trash" always-visible="true" />
						</shape-view>
					</canvas>
				</column>
				<column name="``" value="shape.isVisible()" width="20" column-value-name="visible">
					<check-box value="visible" />
					<column-edit column-edit-value-name="newV" commit="shape.setVisible(newV)" row-update="true">
						<check-box />
					</column-edit>
				</column>
				<column name="`Name`" value="shape.getName()" pref-width="200">
					<column-edit column-edit-value-name="newV" commit="shape.setName(newV)" row-update="true">
						<text-field />
					</column-edit>
				</column>
				<column name="`X`" value="shape instanceof PositionedShape ? Integer.valueOf(((PositionedShape) shape).getX()) : (Integer) null" width="40">
					<column-edit column-edit-value-name="newV" commit="((PositionedShape) shape).setX(newV)" row-update="true">
						<text-field />
					</column-edit>
				</column>
				<column name="`Y`" value="shape instanceof PositionedShape ? Integer.valueOf(((PositionedShape) shape).getY()) : (Integer) null" width="40">
					<column-edit column-edit-value-name="newV" commit="((PositionedShape) shape).setY(newV)" row-update="true">
						<text-field />
					</column-edit>
				</column>
				<column name="`Width`" value="shape instanceof PositionedShape ? Integer.valueOf(((PositionedShape) shape).getWidth()) : (Integer) null" width="40">
					<column-edit column-edit-value-name="newV" commit="((PositionedShape) shape).setWidth(newV)" row-update="true">
						<text-field />
					</column-edit>
				</column>
				<column name="`Height`" value="shape instanceof PositionedShape ? Integer.valueOf(((PositionedShape) shape).getHeight()) : (Integer) null" width="45">
					<column-edit column-edit-value-name="newV" commit="((PositionedShape) shape).setHeight(newV)" row-update="true">
						<text-field />
					</column-edit>
				</column>
				<column name="`Rotation`" value="shape instanceof PositionedShape ? Float.valueOf(((PositionedShape) shape).getRotation()) : (Float) null" width="60">
					<column-edit column-edit-value-name="newV" commit="((PositionedShape) shape).setRotation(newV)" row-update="true">
						<text-field />
					</column-edit>
				</column>
				<column name="`Color`" value="shape.getColor()" width="50">
					<label>
						<style attr="color">shape.getColor()</style>
						<on-click>app.editBgColor=shape</on-click>
					</label>
				</column>
				<column name="`Border`" value="shape instanceof BorderedShape ? ((BorderedShape) shape).getBorderColor() : null" width="50">
					<label>
						<style attr="color">shape instanceof BorderedShape ? ((BorderedShape) shape).getBorderColor() : `white`</style>
						<on-click>
							<event-filter>shape instanceof BorderedShape</event-filter>
							app.editBorderColor=(BorderedShape) shape
						</on-click>
					</label>
				</column>
				<combo-button values="ShapeType.values()" active-value-name="type" action="create" icon="`/icons/add.png$16x16`">
					<model>
						<action name="create" always-enabled="true">
							app.selected=type.initShape(config.shapes.create(type.shapeType),
								app.shapes.size(), app.canvasWidth, app.canvasHeight).create().get()
						</action>
					</model>
				</combo-button>
				<multi-value-action icon="`/icons/remove.png$16x16`" values-name="shapes">
					<model>
						<action name="remove" on-thread="ANY">app.toRemove=shapes</action>
					</model>
					remove
				</multi-value-action>
			</super-table>
			<box layout="inline-layout" orientation="vertical" main-align="justify" cross-align="justify">
				<field-panel visible="app.selected!=null">
					<model>
						<value name="bound">Math.max(app.canvasWidth, app.canvasHeight)</value>
						<value name="bordered">app.selected instanceof BorderedShape ? (BorderedShape) app.selected : null</value>
						<value name="positioned">app.selected instanceof PositionedShape ? (PositionedShape) app.selected : null</value>
						<value name="textShape">app.selected instanceof ColoredText ? (ColoredText) app.selected : null</value>
						<field-value name="name" source="positioned.getName()"
							target-as="newValue" save="positioned.setName(newValue)" />
						<field-value name="borderThick" source="bordered.getBorderThickness()"
							target-as="newValue" save="bordered.setBorderThickness(newValue)" />
						<field-value name="x" source="positioned.getX()"
							target-as="newValue" save="positioned.setX(newValue)" />
						<field-value name="y" source="positioned.getY()"
							target-as="newValue" save="positioned.setY(newValue)" />
						<field-value name="xAnchor" source="positioned.getXAnchor()"
							target-as="newValue" save="positioned.setXAnchor(newValue)" />
						<field-value name="yAnchor" source="positioned.getYAnchor()"
							target-as="newValue" save="positioned.setYAnchor(newValue)" />
						<field-value name="w" source="positioned.getWidth()"
							target-as="newValue" save="positioned.setWidth(newValue)" />
						<field-value name="h" source="positioned.getHeight()"
							target-as="newValue" save="positioned.setHeight(newValue)" />
						<field-value name="r" source="positioned.getRotation()"
							target-as="newValue" save="positioned.setRotation(newValue)" />
						<field-value name="text" source="textShape.getText()"
							target-as="newValue" save="textShape.setText(newValue)" />
						<field-value name="fontSize" source="textShape.getFontSize()"
							target-as="newValue" save="textShape.setFontSize(newValue)" />
					</model>
					<text-field field-label="`Name:`" fill="true" value="name" />
					<text-field field-label="`Text:`" visible="app.selected instanceof ColoredText" fill="true" value="text" />
					<spinner field-label="`Font Size:`" visible="textShape!=null" value="fontSize" />
					<box field-label="`Horizontal:`" fill="true" visible="positioned!=null"
						layout="inline-layout" orientation="horizontal" main-align="justify">
						<combo value="xAnchor" values="AnchorEnd.values()" />
						<multi-range-slider values="{x+w}" min="0" max="bound" range-min="x" range-max="w+x"
							requires-source-modification="false" enforce-order="false" />
					</box>
					<box field-label="`Vertical:`" fill="true" visible="positioned!=null"
						layout="inline-layout" orientation="horizontal" main-align="justify">
						<combo value="yAnchor" values="AnchorEnd.values()" />
						<multi-range-slider values="{y+h}" min="0" max="bound" range-min="y" range-max="h+y"
							requires-source-modification="false" enforce-order="false" />
					</box>
					<slider field-label="`Rotation:`" fill="true" visible="positioned!=null" value="r" min="0" max="360" />
					<table fill="true" visible="app.selected instanceof ColoredPolygon"
						rows="app.selected instanceof ColoredPolygon ? ((ColoredPolygon) app.selected).getVertices().getValues() : null"
						active-value-name="vertex">
						<titled-border title="`Vertices`" />
						<column name="`X`" value="vertex.getX()">
							<column-edit column-edit-value-name="newV" commit="vertex.setX(newV)">
								<text-field />
							</column-edit>
						</column>
						<column name="`Y`" value="vertex.getY()">
							<column-edit column-edit-value-name="newV" commit="vertex.setY(newV)">
								<text-field />
							</column-edit>
						</column>
						<multi-value-action icon="`/icons/add.png$16x16`" allow-for-empty="true">
							((ColoredPolygon) app.selected).getVertices().create().create()
						</multi-value-action>
						<multi-value-action icon="`/icons/remove.png$16x16`" values-name="vertices">
							((ColoredPolygon) app.selected).getVertices().getValues().removeAll(vertices)
						</multi-value-action>
					</table>
					<box field-label="`Coloring:`" fill="true" layout="inline-layout" orientation="horizontal">
						<box layout="simple-layout" width="30">
							<titled-border title="`Background`" />
							<box width="16" height="16" layout="layer-layout">
								<style attr="color">app.selected.getColor()</style>
								<on-click>app.editBgColor=app.selected</on-click>
							</box>
						</box>
						<box visible="bordered!=null" layout="inline-layout" orientation="horizontal">
							<titled-border title="`Border`" />
							<box layout="simple-layout">
								<box width="16" height="16" layout="layer-layout">
									<style attr="color">bordered.getColor()</style>
									<on-click>app.editBorderColor=bordered</on-click>
								</box>
							</box>
							<spacer width="3" />
							<label>Thickness:</label>
							<spinner value="borderThick" />
						</box>
					</box>
				</field-panel>
			</box>
		</split>
		<canvas pref-width="600" pref-height="600" publish-width="app.canvasWidth" publish-height="app.canvasHeight">
			<shape-view>
				<translate x="config.translateX" y="config.translateY" />
				<rotate radians="config.rotateAmount/180*(float) Math.PI"
					 anchor-x="config.rotateAnchorX-config.translateX" anchor-y="config.rotateAnchorY-config.translateY" />
				<scale x="config.scaleX" y="config.scaleTogether ? config.scaleX : config.scaleY" />

				<demo-shape-collection shapes="app.shapes" selected="app.selected" />
			</shape-view>
		</canvas>
	</box>
</quick>
