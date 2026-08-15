plugins {
    kotlin("multiplatform")
    id("com.android.library")
    kotlin("plugin.serialization") version "2.2.10"
}

kotlin {
    // 1. Android Target (The SurfaceView Consumer)
    androidTarget {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }
    
    // 2. iOS Targets (The Metal/AVAudioEngine Consumers)
    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "SharedEngine"
            isStatic = true
        }
    }
    
    // 3. Windows Bare-Metal Target (The 1MB EXE)
    mingwX64("win") {
        compilations.getByName("main") {
            cinterops {
                create("miniaudio") {
                    defFile(project.file("src/winMain/native/miniaudio.def"))
                    includeDirs(project.file("src/winMain/native"))
                }
            }
        }
        binaries.executable {
            entryPoint = "main"
            val miniaudioObject = project.layout.buildDirectory.get().asFile
                .resolve("native/win/timebox_miniaudio.o")
            linkerOpts(miniaudioObject.absolutePath)
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                // THE ONLY ALLOWED DEPENDENCIES: Pure Kotlin and Coroutines (for the Event Bus)
                implementation(kotlin("stdlib"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}

android {
    namespace = "com.timebox.engine.shared"
    compileSdk = 34
    defaultConfig {
        minSdk = 24
    }
}

val opnaAudit by tasks.registering(Exec::class) {
    group = "verification"
    description = "Runs the OPNA audio hot-path audit."
    workingDir = rootProject.projectDir
    if (org.gradle.internal.os.OperatingSystem.current().isWindows) {
        commandLine("python", "tools/math_oracles/opna_audit.py")
    } else {
        commandLine("python3", "tools/math_oracles/opna_audit.py")
    }
}

val compileMiniaudioWin by tasks.registering(Exec::class) {
    val gcc = file("${System.getProperty("user.home")}/.konan/dependencies/msys2-mingw-w64-x86_64-2/bin/gcc.exe")
    val source = file("src/winMain/native/timebox_miniaudio.c")
    val header = file("src/winMain/native/timebox_miniaudio.h")
    val miniaudioHeader = rootProject.file("miniaudio.h")
    val objectFile = layout.buildDirectory.file("native/win/timebox_miniaudio.o")
    inputs.files(source, header, miniaudioHeader)
    outputs.file(objectFile)
    doFirst {
        val out = objectFile.get().asFile
        out.parentFile.mkdirs()
        if (!gcc.isFile) {
            throw GradleException("Kotlin/Native gcc not found: ${gcc.absolutePath}")
        }
    }
    environment(
        "PATH",
        gcc.parentFile.absolutePath + ";" + (System.getenv("PATH") ?: "")
    )
    commandLine(
        gcc.absolutePath,
        "-c",
        "-O2",
        "-I", rootProject.projectDir.absolutePath,
        "-I", file("src/winMain/native").absolutePath,
        "-o", objectFile.get().asFile.absolutePath,
        source.absolutePath
    )
}

tasks.matching { it.name.startsWith("link") && it.name.contains("Win") }.configureEach {
    dependsOn(compileMiniaudioWin)
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompilationTask<*>>().configureEach {
    dependsOn(opnaAudit)
}