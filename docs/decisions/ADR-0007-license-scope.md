# ADR-0007: License and first-release scope

**Decision.** Apache-2.0 (compatible with Material 3 and Miuix, patent grant, permissive). First release targets Android plus Windows 11; Linux, keyboard (v2) and touchpad (v3) follow. Distribution through GitHub Releases and F-Droid; Google Play is not planned.

**Consequences.** The Windows agent cannot be tested on the Linux development machine, so Windows code is marked "not tested" until the owner verifies it on Windows 11 and CI covers builds and unit tests on `windows-latest`.
