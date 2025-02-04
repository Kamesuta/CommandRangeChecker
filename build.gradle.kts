import org.ajoberstar.grgit.Grgit

plugins {
    // Javaプラグインを適用
    java
    // Kotlinを使用するためのプラグイン
    kotlin("jvm") version "1.7.10"
    // ShadowJar(依存関係埋め込み)を使用するためのプラグイン
    id("com.github.johnrengelman.shadow") version "8.1.1"
    // Gitに応じた自動バージョニングを行うためのプラグイン
    id("org.ajoberstar.grgit") version "4.1.1"
}

// Java17を使用する
java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(17))
}

// グループ定義
group = "net.kunmc"
// バージョン定義
version = run {
    // Gitに応じた自動バージョニングを行うための設定
    val grgit = runCatching { Grgit.open(mapOf("currentDir" to project.rootDir)) }.getOrNull()
        ?: return@run "unknown" // .gitがない
    // HEADがバージョンを示すタグを指している場合はそのタグをバージョンとする
    val versionStr = grgit.describe {
        longDescr = false
        tags = true
        match = listOf("v[0-9]*")
    } ?: "0.0.0" // バージョンを示すタグがない
    // GitHub Actionsでビルドする場合は環境変数からビルド番号を取得する
    val buildNumber = System.getenv("GITHUB_RUN_NUMBER") ?: "git"
    // コミットされていない変更がある場合は+dirtyを付与する
    val dirty = if (grgit.status().isClean) "" else "+dirty"
    // リリースバージョン以外は-SNAPSHOTを付与する
    val snapshot = if (versionStr.matches(Regex(".*-[0-9]+-g[0-9a-f]{7}"))) "-SNAPSHOT" else ""
    // バージョンを組み立てる
    "${versionStr}.${buildNumber}${snapshot}${dirty}"
}

repositories {
    mavenCentral()
    // Paperの依存リポジトリ
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://oss.sonatype.org/content/groups/public/")
    // ProtocolLibの依存リポジトリ
    maven("https://repo.dmulloy2.net/repository/public/")
    // Spigot NMSの依存リポジトリ
    mavenLocal()
}

dependencies {
    // PaperAPI
    compileOnly("io.papermc.paper:paper-api:1.20.1-R0.1-SNAPSHOT")
    // ProtocolLib
    compileOnly("com.comphenix.protocol:ProtocolLib:4.6.0")
    // Spigot NMS
    compileOnly("org.spigotmc:spigot:1.20.1-R0.1-SNAPSHOT")
}

tasks {
    jar {
        // 依存関係を埋め込んでいないjarは末尾に-originalを付与する
        archiveClassifier.set("original")
    }

    // fatJarを生成する
    shadowJar {
        // 依存関係を埋め込んだjarは末尾なし
        archiveClassifier.set("")
        // 依存関係をcom.yourgroup.lib以下に埋め込むためにリロケートする
        arrayOf("kotlin", "org.intellij", "org.jetbrains").forEach {
            relocate(it, "${project.group}.${project.name.lowercase()}.lib.${it}")
        }
    }

    // ソースjarを生成する
    val sourcesJar by registering(Jar::class) {
        archiveClassifier.set("sources")
        from(sourceSets["main"].allSource)
    }

    // アーティファクトを登録する
    artifacts {
        // 依存関係を埋め込んだjarをビルドする
        add("archives", shadowJar)
        // ソースjarを生成する (-sources)
        add("archives", sourcesJar)
    }

    // plugin.ymlの中にバージョンを埋め込む
    @Suppress("UnstableApiUsage")
    withType<ProcessResources> {
        val props = mapOf("version" to version)
        inputs.properties(props)
        filteringCharset = "UTF-8"
        filesMatching("plugin.yml") {
            expand(props)
        }
    }
}
