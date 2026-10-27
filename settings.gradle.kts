pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Pleos Connect SDK를 쓰게 되면 Pleos 문서에 나온 저장소 주소를 여기에 추가
    }
}
rootProject.name = "SafeBeat"
include(":app")
