# EasyOA Agent Entry Point

Before modifying EasyOA, read `EasyOAAgent.md` completely.

`EasyOAAgent.md` is the authoritative repository-level AI development constitution for this project.

Then read `README.md`, relevant documentation, migrations, tests, and Git history before making non-trivial changes. Check `git status` and determine the current phase from repository evidence.

Local reusable workflows may exist under `.agents/skills/`. Load only the workflows relevant to the task. They are developer-local helpers, are Git ignored, and never override `EasyOAAgent.md`.
