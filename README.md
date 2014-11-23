Table-Driven Data Layer
====
I'm starting this repository to keep track of the ideas I'm having for a backend data layer that I believe would revolutionize how applications are designed and implemented.

The basic idea is sort of a mix of spreadsheets and database tables.  One would define a schema for an application consisting of various writable tables (input) that may have any number of rows of data, each row having an entry in each of the table's columns.  Columns would have a name and a type.  All this is identical to database tables.

In addition to these writable tables into which an application would pump data, there would be other, read-only, tables that are permutations of the data in these traditional tables.  This is similar to the database concept of a view.  Whenever data is added to, removed from, or changed in an input, the data in the views would change accordingly.

Each of these tables would be represented to the application as an observable collection with each element in the collection representing a row.  This functionality is theoretically available via trigger mechanisms in some DBMS's, but not in the observable format, which is much easier to write applications against.

These are all the basics of the system.  When designing an application, one would design all the input tables, create views that filter, collate, operate on, and otherwise manipulate this input data into sets of data that are represented directly in the GUI with minimal or no application logic outside of the data definition.

As an example, consider a very imple calendar application.  We'll have an event table.  It would have a date and a name and perhaps some other information.  Then an alert table.  This table would have a reference to event that the alert is for, the time (or maybe time offset) of the alert, maybe the mechanism of the alert (SMS, email, notification, etc.), and a flag specifying whether the alert has been disseminated.  We would create a view that filters the events to be only the current month using a view operation that would be available from a schema creation GUI.  Then we have a current alerts view that filters alerts whose alert time is in the past but whose dissemination flag is not set.

The application would have 2 sets of logic.  One is to display a layout of the days for the current month, query the current-month-events view, and for each event, place it in the proper day.  When a new event is created or the month rolls over, the view's data will change automatically and the application logic would be re-called automatically as a feature of the observable collection.  The other piece of logic is to monitor the current-alerts view.  For each alert, perform the appropriate dissemination action and set the dissemination flag on the alert.

Adding features to this calendar would be easy.  For example, to add the ability to change the month view of the calendar, add an input with a single entry of the selected month.  The current-month-events view would be change to filter events whose time is within the value of the selected month.  The application needs the user widget to select the month.  Done.

I believe defining the application logic in this format would make almost any application very simple to understand.  It also lends itself well to displaying application logic in a graphical format. It also lends itself very well to automated tools.  In the future, I will develop a MUIS sub-project containing widgets or models that interact directly with TDDL.  The end goal is to allow developers to build any application, no matter how massive the scope, just by defining tables and writing some MUIS files.

My priority at the moment is MUIS.  I want to have a well-running minimal set of GUI functionality before I write any code for this project.  Ideally I'd like MUIS and TDDL to release 1.0.0 at the same time, maybe along with another repository containing a large, useful application that uses both libs and has a minimum of java code in it.  Maybe a calendar/task/lists app or a development environment.
