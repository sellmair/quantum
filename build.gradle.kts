@file:Suppress("UNUSED_VARIABLE")


buildscript {
    repositories {
        jcenter()
        maven("https://plugins.gradle.org/m2/")
        maven("https://dl.bintray.com/jetbrains/kotlin-native-dependencies")
        maven("http://dl.bintray.com/kotlin/kotlin-eap")
    }
}


allprojects {
    repositories {
        jcenter()
        mavenCentral()
        maven("http://dl.bintray.com/kotlin/kotlin-eap")
    }
}

plugins {
    id("maven-publish")
    kotlin("multiplatform")
}

/*
For publishing
 */
group = "io.sellmair"
version = "2.0.0-alpha.7"

kotlin {
    jvm()
    macosX64()
    iosArm64()
    iosX64()

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(kotlin("stdlib-common"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core-common:${Versions.coroutines}")
            }
        }

        val commonTest by getting {
            dependencies {
                implementation("org.jetbrains.kotlin:kotlin-test-common")
                implementation("org.jetbrains.kotlin:kotlin-test-annotations-common")
            }
        }

        val jvmMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:${Versions.coroutines}")
                implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
            }
        }

        val jvmTest by getting {
            dependencies {
                implementation("org.jetbrains.kotlin:kotlin-test")
                implementation("org.jetbrains.kotlin:kotlin-test-junit")
            }
        }

        val nativeMain by creating {
            this.dependsOn(commonMain)
            dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core-native:${Versions.coroutines}")
            }
        }

        val nativeTest by creating {
            this.dependsOn(commonTest)
        }

        val macosX64Main by getting {
            this.dependsOn(nativeMain)
        }

        val macosX64Test by getting {
            this.dependsOn(nativeTest)
        }

        val iosArm64Main by getting {
            this.dependsOn(nativeMain)
        }

        val iosArm64Test by getting {
            this.dependsOn(nativeTest)
        }

        val iosX64Main by getting {
            this.dependsOn(nativeMain)
        }

        val iosX64Test by getting {
            this.dependsOn(nativeTest)
        }
    }

    // Configure all compilations of all targets:
    targets.all {
        compilations.all {
            kotlinOptions {
                freeCompilerArgs = listOf("-Xuse-experimental=kotlin.Experimental")
            }
        }
    }
}