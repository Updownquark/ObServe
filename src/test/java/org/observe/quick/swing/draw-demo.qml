<?xml version="1.0" encoding="UTF-8"?>

<quick xmlns:quick="Quick-X v0.1" xmlns:draw="Quick-Draw v0.1" with-extension="window"
	title="`Quick Draw Demo`" close-action="exit" x="app.x" y="app.y" width="app.w" height="app.h">
	<head>
		<imports>
			<import>org.observe.quick.swing.ColoredRectangle</import>
			<import>org.qommons.Colors</import>
		</imports>
		<models>
			<model name="app">
				<value name="x" type="int" />
				<value name="y" type="int" />
				<value name="w" init="1000" />
				<value name="h" init="800" />

				<value name="canvasWidth" type="int" />
				<value name="canvasHeight" type="int" />
				
				<value name="newRectX">Math.round(canvasWidth/4.0f)</value>
				<value name="newRectY">Math.round(canvasHeight/4.0f)</value>
				<value name="newRectWidth">Math.round(canvasWidth/2.0f)</value>
				<value name="newRectHeight">Math.round(canvasHeight/2.0f)</value>

				<list name="rectangles" type="ColoredRectangle">
					<element>new ColoredRectangle(100, 200, 300, 200).bg(`green`).border(2, `blue`)</element>
					<element>new ColoredRectangle(400, 400, 100, 250).bg(`red`).border(1, `lime`)</element>
				</list>
				<value name="selected" type="Colored Rectangle" />
				
				<value name="editBgColor" type="ColoredRectangle" />
				<value name="editBorderColor" type="ColoredRectangle" />
			</model>
		</models>
		<style-sheet>
			<!--<import-style-sheet name="searcher" ref="quick-testing.qss" />-->
		</style-sheet>
	</head>
	<box layout="inline-layout" orientation="horizontal" main-align="justify">
		<general-dialog visible="app.editBgColor!=null" title="`Edit Rectangle Color`" modal="false">
			<color-chooser value="app.editBgColor.background" />
		</general-dialog>
		<general-dialog visible="app.editBorderColor!=null" title="`Edit Rectangle Border Color`" modal="false">
			<color-chooser value="app.editBorderColor.borderColor" />
		</general-dialog>
		<canvas pref-width="600" pref-height="600" publish-width="app.canvasWidth" publish-height="app.canvasHeight">
			<shape-collection for-each="app.rectangles" active-shape-as="rect">
				<rectangle
					left="rect.getX(Leading)" h-center="rect.getX(Center)" right="rect.getX(Trailing)"
					top="rect.getY(Leading)" v-center="rect.getY(Center)" bottom="rect.getY(Trailing)"
					width="rect.width" height="rect.height" rotation="rect.rotation/360*(float) Math.PI">
					<model>
						<hook name="selectOnFocus" on="focused">focused ? (app.selected=rect) : null</hook>
					</model>
					<style attr="color">
						rect.background
						<style if="hovered">Colors.bleach(rect.background, 0.5f)</style>
					</style>
					<style attr="border-color">rect.borderColor</style>
					<style attr="thickness">
						rect.borderThickness
						<style if="pressed">rect.borderThickness*2</style>
					</style>
				</rectangle>
			</shape-collection>
		</canvas>
		<split orientation="vertical">
			<super-table rows="app.rectangles" active-value-name="rect" selection="app.selected" searchable="false">
				<titled-border title="`Rectangles`" />
				<with-row-dragging />
				<column name="`X`" value="rect.x" width="40">
					<column-edit column-edit-value-name="newV" commit="rect.x=newV" row-update="true">
						<text-field />
					</column-edit>
				</column>
				<column name="`Y`" value="rect.y" width="40">
					<column-edit column-edit-value-name="newV" commit="rect.y=newV" row-update="true">
						<text-field />
					</column-edit>
				</column>
				<column name="`Width`" value="rect.width" width="40">
					<column-edit column-edit-value-name="newV" commit="rect.width=newV" row-update="true">
						<text-field />
					</column-edit>
				</column>
				<column name="`Height`" value="rect.height" width="45">
					<column-edit column-edit-value-name="newV" commit="rect.height=newV" row-update="true">
						<text-field />
					</column-edit>
				</column>
				<column name="`Rotation`" value="(int) rect.rotation" width="60">
					<column-edit column-edit-value-name="newV" commit="rect.rotation=newV" row-update="true">
						<text-field />
					</column-edit>
				</column>
				<column name="`Color`" value="rect.background" width="50">
					<label>
						<style attr="color">rect.background</style>
						<on-click>app.editBgColor=rect</on-click>
					</label>
				</column>
				<column name="`Border`" value="rect.background" width="50">
					<label>
						<style attr="color">rect.borderColor</style>
						<on-click>app.editBorderColor=rect</on-click>
					</label>
				</column>
				<column name="`Border Thickness`" value="rect.borderThickness" width="100">
					<column-edit column-edit-value-name="newV" commit="rect.borderThickness=newV" row-update="true">
						<text-field />
					</column-edit>
				</column>
				<multi-value-action icon="`/icons/add.png$16x16`" allow-for-empty="true">
					app.rectangles.add(new ColoredRectangle(app.newRectX, app.newRectY, app.newRectWidth, app.newRectHeight))
				</multi-value-action>
				<multi-value-action icon="`/icons/remove.png$16x16`" values-name="rects">
					app.rectangles.removeAll(rects)
				</multi-value-action>
			</super-table>
			<box layout="inline-layout" orientation="vertical" main-align="justify" cross-align="justify">
				<field-panel visible="app.selected!=null">
					<model>
						<value name="bound">Math.max(app.canvasWidth, app.canvasHeight)</value>
						<field-value name="x" source="app.selected.x"
							target-as="newValue" save="app.selected.x=newValue">
							<action>app.rectangles.update(app.selected, true)</action>
						</field-value>
						<field-value name="y" source="app.selected.y"
							target-as="newValue" save="app.selected.y=newValue">
							<action>app.rectangles.update(app.selected, true)</action>
						</field-value>
						<field-value name="width" source="app.selected.width"
							target-as="newValue" save="app.selected.width=newValue">
							<action>app.rectangles.update(app.selected, true)</action>
						</field-value>
						<field-value name="height" source="app.selected.height"
							target-as="newValue" save="app.selected.height=newValue">
							<action>app.rectangles.update(app.selected, true)</action>
						</field-value>
						<field-value name="xAnchor" source="app.selected.xAnchor"
							target-as="newValue" save="app.selected.xAnchor=newValue">
							<action>app.rectangles.update(app.selected, true)</action>
						</field-value>
						<field-value name="yAnchor" source="app.selected.yAnchor"
							target-as="newValue" save="app.selected.yAnchor=newValue">
							<action>app.rectangles.update(app.selected, true)</action>
						</field-value>
						<field-value name="rotation" source="app.selected.rotation"
							target-as="newValue" save="app.selected.rotation=newValue">
							<action>app.rectangles.update(app.selected, true)</action>
						</field-value>
					</model>
					<box field-label="`Horizontal:`" fill="true" layout="inline-layout" orientation="horizontal" main-align="justify">
						<combo value="xAnchor" values="ColoredRectangle.AnchorEnd.values()" />
						<multi-slider values="{x, width}" min="0" max="bound"
							 enforce-order="false" />
					</box>
					<box field-label="`Vertical:`" fill="true" layout="inline-layout" orientation="horizontal" main-align="justify">
						<combo value="yAnchor" values="ColoredRectangle.AnchorEnd.values()" />
						<multi-slider values="{y, height}" min="0" max="bound"
							 enforce-order="false" />
					</box>
					<slider field-label="`Rotation:`" fill="true" value="rotation" min="0" max="360" />
				</field-panel>
			</box>
		</split>
	</box>
</quick>
