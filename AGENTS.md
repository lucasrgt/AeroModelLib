<!-- csm:instructions:start -->
## Codebase Semantic Memory

This repository uses CSM. Run `csm context --task "<goal>" --path <path>` before changing code and `csm check --task "<goal>" --base HEAD` before finishing. Durable tool state lives under `.csm/`; use `csm sync` to install the versions pinned by CSM. The standalone tools remain authoritative for their own records and must be invoked through `csm nya|rtw|wtw|nwc ...` in this repository.
<!-- csm:instructions:end -->
