# Aero Optimization Catalog Records

Each UTF-8 `.properties` file is named after one stable `aero.*` optimization
ID. Records describe implementation-owned facts: status, source default,
behavioral delta, risks, rollback, source symbols, and supporting evidence.

The current records use `tracking=symbol`, so no annotation dependency enters
Aero source or artifacts. `source.revision` records the last source snapshot
audited for that entry. A changed implementation, default, risk, or rollback
path must update its record in the same repository change.

When the same Java type exists in multiple platform roots and only one
implementation owns the decision, optional `source.paths` constrains the
symbol to the audited platform file. `tools/optimization-catalog/Audit.java`
fails if that path or symbol drifts.
