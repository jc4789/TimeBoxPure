---
name: local-build-jbr
description: Generate safe local Gradle build commands for Julia's Windows Android environment. Use only when writing terminal commands, Gradle build instructions, Android assemble/debug commands, or build troubleshooting steps.
---

# Local Build JBR

## Purpose

Prevent Gradle/Android builds from using the wrong system JDK.

This is a local environment rule, not an engine architecture law.

## Trigger When

Use this skill when the user asks for:

- build commands
- Gradle commands
- Android assemble/debug instructions
- terminal commands
- build troubleshooting
- CI/local command comparison

## Required Command Prefix

Always prefix Android Gradle build commands exactly:

```powershell
$env:JAVA_HOME="D:\Programes\Android Studio\jbr"; .\gradlew :app:assembleDebug
```

## Rules

1. Do not assume the system Java environment.
2. Do not omit the `JAVA_HOME` override for Android Gradle commands.
3. Keep the path spelling exactly as provided unless the user updates it.
4. Do not put this rule into core engine code.
5. Do not invent a Unix equivalent unless the user asks for non-Windows commands.

## Output Format

When giving a build command, include the exact command as a PowerShell command block.

```powershell
$env:JAVA_HOME="D:\Programes\Android Studio\jbr"; .\gradlew :app:assembleDebug
```
