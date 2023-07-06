dependencies {
    api(libs.fabric.mappingio)

    compileOnly(libs.bundles.builtin.log4j)
    compileOnly(libs.builtin.commons.io)
    compileOnly(libs.builtin.commons.lang3)
    compileOnly(libs.builtin.google.gson)
    compileOnly(libs.builtin.google.guava)
    compileOnly(libs.builtin.fastutil) // only int -> object maps required
}
