# Secrets of Death — GitHub Starter Project

A starter project for the **Secrets of Death and Destruction** Paper plugin.

## Target environment

- Minecraft / Paper: **1.21.11**
- Java toolchain: **21**
- Build system: **Gradle Kotlin DSL**

Paper's 1.21.11 compatibility guidance lists Java 21. Check your server's exact Paper build before deploying.

## What is included

- Gradle project files.
- Paper API dependency.
- Plugin entry point (`SecretsPlugin.java`).
- `/secrets` command with `help`, `version`, and admin-only `reload` subcommands.
- `plugin.yml` and starter `config.yml`.
- GitHub Actions workflow to build the plugin on each push and pull request.

This is a **project foundation**, not the completed plugin. Artifact gameplay, item definitions, and resource-pack wiring have not been added yet.

## Upload this starter to your GitHub repository

1. Download and extract this ZIP on your device.
2. Open your repository: `https://github.com/itzlazynox67-tech/secrets-of-death-plugin`.
3. Choose **Add file → Upload files**.
4. Upload the *contents* of the extracted `SecretsOfDeath-GitHub-Starter` folder to the repository root. Do not upload the outer folder as a nested directory.
5. Commit the upload to the `main` branch.

If GitHub's upload page does not accept folders on your device, create/upload the files individually while preserving the paths shown in this ZIP.

## Build on GitHub

After the files are committed:

1. Open the repository's **Actions** tab.
2. Open the **Build SecretsOfDeath** workflow.
3. Wait for the workflow to finish.
4. Open the completed workflow run.
5. Download the `SecretsOfDeath-plugin` artifact. It contains the compiled `.jar`.

The workflow uses GitHub-hosted runners; you do not need Gradle installed on your own device.

## Commands currently included

- `/secrets help`
- `/secrets version`
- `/secrets reload` — requires `secrets.admin` (default: server operators)

## Project layout

```text
SecretsOfDeath/
├── .github/workflows/build.yml
├── src/main/java/io/github/itzlazynox67/secrets/
│   ├── SecretsPlugin.java
│   └── commands/SecretsCommand.java
├── src/main/resources/
│   ├── plugin.yml
│   └── config.yml
├── build.gradle.kts
├── gradle.properties
└── settings.gradle.kts
```

## Notes

- Do not commit server files, world folders, private credentials, or access tokens.
- Keep the resource pack as a separately distributed pack; its public URL and SHA-1 can later be added to `config.yml` if needed.
- The plugin JAR belongs in the Paper server's `plugins/` directory.
