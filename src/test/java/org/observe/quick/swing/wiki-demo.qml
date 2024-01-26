<?xml version="1.0" encoding="UTF-8"?>

<!--
    Root XML element.
    Declare all Quick toolkits to be used with 'xmlns:<name>="Toolkit-Name vM.m"'.
    Toolkits extended by declared toolkits will be available as well.
    'with-extension="window"' brings in the 'title' attribute (and 'width' and others) so we can set the title on the window.
    Set the 'width' attribute just so the window is big enough to see the title
-->
<quick xmlns:base="Quick-Base v0.1" with-extension="window" title="`My App`" width="240">
  <!-- The head section of the document, where we define model data and other non-widget information -->
  <head>
    <models>
      <!-- Define a model named "app" -->
      <model name="app">
        <!--
            Define a value named "text".
            The initial value here will set the type of the value (String), so it doesn't need to be specified explicitly.
        --> 
        <value name="text" init="&quot;This is some text&quot;" />
      </model>
    </models>
  </head>
  <!--
      The root widget of the application, a box (simple container with a customizable layout)
      with an inline-layout (lays out widgets one after another in a single dimension, in this case vertically)
  -->
  <box layout="inline-layout" orientation="vertical">
    <!-- This label has constant text specified in the value of the element -->
    <label>The value you enter in this text field...</label>
    <!-- This text field allows the user to edit the 'app.text' model value -->
    <text-field value="app.text" />
    <label>...will be reproduced in the label below.</label>
    <!-- This label's text is the contents of the 'app.text' model value, updating when the model value is changed -->
    <label value="app.text" />
  </box>
</quick>
