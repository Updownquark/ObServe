<?xml version="1.0" encoding="UTF-8"?>

<quick xmlns:quick="Quick-X v0.1" with-extension="window" title="`Crossed Collection Demo`">
	<head>
		<imports>
			<import>org.qommons.BiTuple</import>
			<import>java.lang.Math.*</import>
		</imports>
		<models>
			<model name="app">
				<list name="a" type="int">
					<element>0</element>
					<element>1</element>
					<element>2</element>
					<element>3</element>
					<element>4</element>
				</list>
				<value name="b" init="10" />
				<list name="c" type="int">
					<element>10</element>
					<element>20</element>
					<element>30</element>
					<element>40</element>
					<element>50</element>
				</list>
				<transform name="ac" source="a">
					<cross with="c" source-as="av" crossed-as="cv">new BiTuple&lt;>(av, cv)</cross>
				</transform>
				<value name="targetD" init="0" />
				<transform name="esByD" source="ac">
					<refresh on="targetD" />
					<filter source-as="v" test="v.getValue1()+b == targetD" />
					<map-to source-as="v">
						<map-with>v.getValue1()*b/(v.getValue2()==0 ? 1 : v.getValue2())</map-with>
					</map-to>
				</transform>
				<transform name="totalE" source="esByD">
					<reduce seed="0" temp-as="temp" source-as="v">temp+v</reduce>
				</transform>
				<transform name="countE" source="esByD">
					<size />
				</transform>
				<!-- Prints all the Es for the selected D each time they change -->
				<hook name="debug" on="esByD">System.out.println("es="+esByD)</hook>
			</model>
		</models>
	</head>
	<box layout="inline-layout" orientation="vertical">
		<field-panel>
			<label fill="true">This demo crosses the "A" and "C" collections and combines them with the "B" value in various ways:</label>
			<label fill="true">D = A+B</label>
			<label fill="true">E = A*B/C (or just A*B if C==0)</label>
			<label fill="true">F = 2^E  If E is negative, the absolute value will be used for the exponent and the result will be negative</label>
			<label fill="true">G = A*F-C</label>
			<box field-label="`A:`" fill="true" layout="inline-layout" orientation="horizontal" cross-align="center">
				<line-border />
				<tiled-pane
					layout="grid-flow-layout" max-row-count="10"
					values="app.a" active-value-name="v">
					<box layout="simple-layout">
						<text-field right="`7xp`" value="v" columns="4" />
						<button right="`0xp`" width="6" height="6"
							icon="`/icons/redX.png`" action="app.a.remove(rowIndex)" />
					</box>
				</tiled-pane>
				<button icon="`/icons/add.png`" action="app.a.add(app.a.size())" />
			</box>
			<text-field field-label="`B:`" value="app.b" columns="5" />
			<box field-label="`C:`" fill="true" layout="inline-layout" orientation="horizontal" cross-align="center">
				<line-border />
				<tiled-pane
					layout="grid-flow-layout" max-row-count="10"
					values="app.c" active-value-name="v">
					<box layout="simple-layout">
						<text-field right="`7xp`" value="v" columns="4" />
						<button right="`0xp`" width="6" height="6"
							icon="`/icons/redX.png`" action="app.c.remove(rowIndex)" />
					</box>
				</tiled-pane>
				<button icon="`/icons/add.png`" action="app.c.add(app.c.size())" />
			</box>
			<table fill="true" rows="app.ac" active-value-name="v">
				<model>
					<value name="a">v==null ? 0 : v.getValue1()</value>
					<value name="b">app.b</value>
					<value name="c">v==null ? 0 : v.getValue2()</value>
					<value name="d">a+b</value>
					<value name="e">a*b/(c==0 ? 1 : c)</value>
					<value name="f">(e&lt;0 ? -1 : 1)*(int) pow(2, abs(e))</value>
					<value name="g">a*f-c</value>
				</model>
				<column name="`#`" pref-width="30" value="rowIndex+1" />
				<column name="`A`" pref-width="30" value="a" />
				<column name="`B`" pref-width="30" value="b" />
				<column name="`C`" pref-width="30" value="c" />
				<column name="`D`" pref-width="30" value="d" />
				<column name="`E`" pref-width="50" value="e" />
				<column name="`F`" pref-width="50" value="f" />
				<column name="`G`" pref-width="50" value="g" />
			</table>
			<label>For the following selected D value, the average value of E will be reported for all rows with the given D value</label>
			<text-field field-label="`D:`" value="app.targetD" columns="5" />
			<label field-label="`Avg E:`" value="app.totalE*1.0/app.countE" />
		</field-panel>
	</box>
</quick>
