// Feature modules see only the design *contract*, never a concrete design library.
// Enforced by `verifyArchitecture` (see sdhelper.root).
plugins {
    id("sdhelper.android-compose")
}

dependencies {
    "implementation"(project(":ui:design-api"))
    "implementation"(project(":core:model"))
}
