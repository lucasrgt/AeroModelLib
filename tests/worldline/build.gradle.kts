plugins { id("dev.worldline.test") version "0.2.0" }

val product = layout.buildDirectory.dir("aero-product")
val prepareAeroProduct by tasks.registering(JavaCompile::class) {
    source(fileTree("../../core") { include("**/*.java") })
    source("../../modloader/tests/aero/modellib/Aero_AnimationState.java")
    destinationDirectory.set(product)
    classpath = files()
    options.release.set(8)
    options.compilerArgs.add("-Xlint:none")
}

worldline {
    runtime.set("b1.7.3")
    oracleProfile.set("b173-local")
    noRuntime.set(true)
    productClasspath.from(product)
}

tasks.named("compileWorldlineTestJava") { dependsOn(prepareAeroProduct) }
