# Worldline TestKit consumer

AeroModelLib consumes the packaged Java 8 TestKit API. The specs import
AeroModelLib product classes from a separate classpath and do not compile any
Worldline repository sources.

Build the experimental TestKit distribution, point `WORLDLINE_TESTKIT_HOME`
at its generated directory, and run:

```text
java tools/testkit/Run.java
```

The Java 21 tool compiles the Java 8 core and specs into separate directories,
then executes the packaged TestKit runner in host-only mode.
