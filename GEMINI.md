# Project Instructions

## Workflows
- **Build Verification:** Before confirming the completion of any task, ALWAYS run `./gradlew assembleDebug` to ensure the project compiles correctly and no regressions were introduced in the build process.

## AI Guidance & Custom Skills
This project uses specialized Gemini CLI skills to automate complex tasks. When you encounter the following scenarios, you SHOULD activate and follow the corresponding skill:

- **Implementing New Features:** Use the `cook` skill. It provides a structured "kitchen" workflow (Research -> Recipe -> Cook -> Taste Test).
  - Location: `.gemini/skills/cook/SKILL.md`
- **Releasing New Versions:** Use the `upgrade-app` skill. It automates version bumping, changelog generation, tagging, and GitHub releases.
  - Location: `.gemini/skills/upgrade-app/SKILL.md`
- **Translating CSV Files:** Use the `csv-translator` skill. It handles splitting large files, translation, and auto-importing strings into Android resources.
  - Location: `.gemini/skills/csv-translator/SKILL.md`

Always refer to the project-specific memory in `.gemini/tmp/universal-installer/memory/MEMORY.md` for private local context.
