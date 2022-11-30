import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import org.ajoberstar.grgit.Grgit

plugins {
    // Javaプラグインを適用
    java
    // Kotlinを使用するためのプラグイン
    kotlin("jvm") version "1.7.10"
    // ShadowJar(依存関係埋め込み)を使用するためのプラグイン
    id("com.github.johnrengelman.shadow") version "6.0.0"
    // Gitに応じた自動バージョニングを行うためのプラグイン
    id("org.ajoberstar.grgit") version "4.1.1"
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
    // ProtocolLibの依存リポジトリ (Spigot NMSの依存リポジトリ)
    maven("https://repo.dmulloy2.net/repository/public/")
}

dependencies {
    // PaperAPI
    compileOnly("com.destroystokyo.paper:paper-api:1.16.5-R0.1-SNAPSHOT")
    // ProtocolLib
    compileOnly("com.comphenix.protocol:ProtocolLib:4.6.0")
    // Spigot NMS
    compileOnly("org.spigotmc:spigot:1.16.5-R0.1-SNAPSHOT")
}

tasks {
    jar {
        // 依存関係を埋め込んでいないjarは末尾に-originalを付与する
        archiveClassifier.set("original")
    }

    // 依存関係をcom.yourgroup.lib以下に埋め込むためにリロケートする
    val relocateShadow by registering(com.github.jengelman.gradle.plugins.shadow.tasks.ConfigureShadowRelocation::class) {
        target = shadowJar.get()
        prefix = "${project.group}.${project.name.toLowerCase()}.lib"
    }

    // fatJarを生成する
    shadowJar {
        // リロケートする
        dependsOn(relocateShadow)
        // 依存関係を埋め込んだjarは末尾なし
        archiveClassifier.set("")
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
