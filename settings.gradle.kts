pluginManagement {
 repositories {
  google { content { includeGroupByRegex("com.android.*"); includeGroupByRegex("androidx.*"); includeGroupByRegex("com.google.testing.*") } }
  mavenCentral()
  gradlePluginPortal()
 }
}
dependencyResolutionManagement { repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS); repositories { google(); mavenCentral() } }
rootProject.name="Attention"
include(":app")
