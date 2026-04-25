tasks.register("createWindowsAppImage") {
    group = "distribution"
    description = "Builds the Windows app-image in the app module."
    dependsOn(":app:createWindowsAppImage")
}

tasks.register("packageWindowsReleaseZip") {
    group = "distribution"
    description = "Builds the portable Windows release zip in the app module."
    dependsOn(":app:packageWindowsReleaseZip")
}
