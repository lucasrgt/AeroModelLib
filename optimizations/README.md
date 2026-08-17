# Aero Optimization Metadata

This directory is the source of truth for optimization metadata owned by
AeroModelLib. Each stable `aero.*` ID maps to one implementation decision in
`catalog/`; `TEMPLATE.properties` is the starting point for new records.

The files use the neutral `worldline.optimization.v1` schema. The schema does
not make Aero depend on the Worldline runtime, inject bytecode, or enable any
feature. Aero's source and feature flags remain authoritative for behavior.

Worldline and other experiment runners may validate these records and attach
evidence to their IDs. They must not copy the catalog and become a competing
source of truth. See `docs/OPTIMIZATION_CATALOG.md` for classification and
investigation guidance.
