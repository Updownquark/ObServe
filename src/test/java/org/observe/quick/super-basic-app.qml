<?xml version="1.0" encoding="UTF-8"?>

<qonfig-app uses:app="Qonfig-App v0.1" app-file="super-basic.qml">
	<toolkit def="/org/observe/expresso/expresso.qtd">
		<value-type>org.observe.expresso.qonfig.ExpressionValueType</value-type>
	</toolkit>
	<toolkit def="/org/observe/quick/quick-core.qtd" />
	<toolkit def="/org/observe/quick/quick-base.qtd" />
	<toolkit def="/org/observe/quick/quick-swing.qtd" />
	<special-session>org.observe.expresso.qonfig.ExpressoSessionImplV0_1</special-session>
	<special-session>org.observe.quick.QuickSessionImplV0_1</special-session>
	<interpretation>org.observe.expresso.qonfig.ExpressoBaseV0_1</interpretation>
	<interpretation>org.observe.quick.QuickCore</interpretation>
	<interpretation>org.observe.quick.QuickBase</interpretation>
	<interpretation>org.observe.quick.QuickSwing</interpretation>
</qonfig-app>
