pluginManagement {
	repositories {
		maven("https://maven.fabricmc.net/") { name = "Fabric" }
		maven("https://maven.architectury.dev/")
		maven("https://files.minecraftforge.net/maven/")
		mavenCentral()
		gradlePluginPortal()
	}
}
gradle.extra.apply {
	set("mod_version", "2.23")
	set("maven_group", "xaeroplus")
	set("archives_base_name", "XaeroPlus")
	set("minecraft_version", "1.20.2")
	set("parchment_version", "2023.12.10")
	set("fabric_loader_version", "0.15.11")
	set("fabric_api_version", "0.91.6+1.20.2")
	set("forge_loader_version", "48.1.0")
	set("neoforge_version", "20.2.88")
    set("worldmap_version_fabric", "1.39.0")
	set("minimap_version_fabric", "24.4.0")
	set("worldmap_version_forge", "1.39.0")
	set("minimap_version_forge", "24.4.0")
	set("worldmap_version_neo", "1.39.0")
	set("minimap_version_neo", "24.4.0")
}

dependencyResolutionManagement {
	versionCatalogs {
		create("libs") {
			library("minecraft", "com.mojang:minecraft:${gradle.extra.get("minecraft_version")}")
			library("fabric-loader", "net.fabricmc:fabric-loader:${gradle.extra.get("fabric_loader_version")}")
			library("forge", "net.minecraftforge:forge:${gradle.extra.get("minecraft_version")}-${gradle.extra.get("forge_loader_version")}")
			library("fabric-api", "net.fabricmc.fabric-api:fabric-api:${gradle.extra.get("fabric_api_version")}")
			library("parchment", "org.parchmentmc.data:parchment-${gradle.extra.get("minecraft_version")}:${gradle.extra.get("parchment_version")}")
			library("neoforge", "net.neoforged:neoforge:${gradle.extra.get("neoforge_version")}")
			library("worldmap-fabric", "maven.modrinth:xaeros-world-map:${gradle.extra.get("worldmap_version_fabric")}_Fabric_${gradle.extra.get("minecraft_version")}")
			library("worldmap-forge", "maven.modrinth:xaeros-world-map:${gradle.extra.get("worldmap_version_forge")}_Forge_${gradle.extra.get("minecraft_version")}")
			library("worldmap-neo", "maven.modrinth:xaeros-world-map:${gradle.extra.get("worldmap_version_neo")}_NeoForge_${gradle.extra.get("minecraft_version")}")
			library("minimap-fabric", "maven.modrinth:xaeros-minimap:${gradle.extra.get("minimap_version_fabric")}_Fabric_${gradle.extra.get("minecraft_version")}")
			library("minimap-forge", "maven.modrinth:xaeros-minimap:${gradle.extra.get("minimap_version_forge")}_Forge_${gradle.extra.get("minecraft_version")}")
			library("minimap-neo", "maven.modrinth:xaeros-minimap:${gradle.extra.get("minimap_version_neo")}_NeoForge_${gradle.extra.get("minecraft_version")}")
            library("mixinextras-common", "io.github.llamalad7:mixinextras-common:0.4.0")
            library("mixinextras-forge", "io.github.llamalad7:mixinextras-forge:0.4.0")
			library("caffeine", "com.github.ben-manes.caffeine:caffeine:3.1.8")
			library("lambdaEvents", "net.lenni0451:LambdaEvents:2.4.2")
			library("waystones-fabric", "maven.modrinth:waystones:15.2.0+fabric-1.20.2")
			library("waystones-forge", "maven.modrinth:waystones:15.2.0+forge-1.20.2")
			library("waystones-neoforge", "maven.modrinth:waystones:15.2.0+neoforge-1.20.2")
			library("balm-fabric", "maven.modrinth:balm:8.0.5+fabric-1.20.2")
			library("balm-forge", "maven.modrinth:balm:8.0.5+forge-1.20.2")
			library("balm-neoforge", "maven.modrinth:balm:8.0.5+neoforge-1.20.2")
			library("fabric-waystones", "maven.modrinth:fwaystones:3.3.2+mc1.20.2")
			library("worldtools", "maven.modrinth:worldtools:1.2.4+1.20.2")
            library("sqlite", "org.rfresh.xerial:sqlite-jdbc:3.46.1.0") // relocated xerial sqlite to avoid conflicts with other mods
			library("immediatelyfast", "maven.modrinth:immediatelyfast:1.2.18+1.20.4-fabric")
			library("modmenu", "maven.modrinth:modmenu:8.0.1")
			library("sodium", "maven.modrinth:sodium:mc1.20.2-0.5.5")
            library("embeddium", "maven.modrinth:embeddium:0.2.12+mc1.20.2")
            library("fpsdisplay", "maven.modrinth:fpsdisplay:3.1.0+1.20.x")
			library("cloth-config-fabric", "me.shedaniel.cloth:cloth-config-fabric:12.0.111")
            library("oldbiomes", "com.github.rfresh2:OldBiomes:1.0")
        }
	}
}



include("common")
include("fabric")
include("forge")
include("neo")

rootProject.name = "XaeroPlus"
