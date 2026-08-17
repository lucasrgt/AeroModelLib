# Worldline Extension Manifests

Aero-owned Worldline extension manifests live under this directory:

```text
worldline/extensions/<id>/manifest.properties
```

Copy `TEMPLATE.properties` into an `<id>` directory. Do not put Worldline
overlay mixins or vanilla intercepts here; those stay in Worldline. This
folder is the Aero-owned binding to Worldline catalog roles.

Optimization records belong in `worldline/optimizations/catalog/`.
