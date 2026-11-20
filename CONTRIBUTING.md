# Contributing to SDHelper

Thanks for your interest. The project is in its design phase; code arrives in later phases (see `docs/ROADMAP.md`).

## Ground rules
- Code, comments, commits and docs are in English. App UI strings are Turkish and English.
- Read `docs/ARCHITECTURE.md` first. Respect the module dependency rules; CI enforces them.
- Feature code must not import Material 3 or Miuix directly. Use `ui:design-api`.
- Conventional Commits (`feat:`, `fix:`, `docs:`, `refactor:`, `test:`, `chore:`).
- Add tests for core, protocol and action changes. Mark anything you could not test (especially Windows-only code).
- Never commit secrets (`local.properties`, keys, tokens).

## Plugins
Third-party actions belong in [`SDHelper-Plugins`](https://github.com/RatmuX/SDHelper-Plugins), not here.

## Security
Report vulnerabilities privately; see `docs/SECURITY.md`.
