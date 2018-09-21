grails.servlet.version = "3.0" // Change depending on target container compliance (2.5 or 3.0)
grails.project.class.dir = "target/classes"
grails.project.test.class.dir = "target/test-classes"
grails.project.test.reports.dir = "target/test-reports"
grails.project.work.dir = "target/work"
grails.project.target.level = 1.6
grails.project.source.level = 1.6
grails.project.war.file = "target/${appName}.war"

//grails.plugin.location.'images-client-plugin' = "../images-client-plugin"
//grails.plugin.location.'ala-bootstrap2' = "../ala-bootstrap2"

grails.project.fork = [
    test: false,
    run: false,
    war: false,
    console: false
]

grails.project.dependency.resolver = "maven" // or ivy
grails.project.dependency.resolution = {
    // inherit Grails' default dependencies
    inherits("global") {
        // specify dependency exclusions here; for example, uncomment this to disable ehcache:
        // excludes 'ehcache'
    }
    log "error" // log level of Ivy resolver, either 'error', 'warn', 'info', 'debug' or 'verbose'
    checksums true // Whether to verify checksums on resolve
    legacyResolve false // whether to do a secondary resolve on plugin installation, not advised and here for backwards compatibility

    repositories {
        mavenLocal()
        mavenRepo ("https://nexus.ala.org.au/content/groups/public/") {
            updatePolicy 'always'
        }
    }

    dependencies {
        runtime 'org.postgresql:postgresql:9.4-1201-jdbc41'
        compile 'org.imgscalr:imgscalr-lib:4.2'
        runtime 'org.apache.commons:commons-imaging:1.0-SNAPSHOT'
        runtime 'org.apache.tika:tika-core:1.18'
        runtime 'com.github.jai-imageio:jai-imageio-core:1.4.0'
        runtime 'au.org.ala:image-utils:1.8.3'
        compile 'org.apache.ant:ant:1.7.1'
        compile 'org.apache.ant:ant-launcher:1.7.1'
        compile 'org.elasticsearch:elasticsearch:1.5.2'
        compile 'org.elasticsearch:elasticsearch:1.5.2'
        compile 'com.opencsv:opencsv:4.2'
    }

    plugins {
        build ":release:3.0.1"
        // plugins for the build system only
        build ":tomcat:7.0.70"

        // plugins for the compile step
        compile ":scaffolding:2.1.2"
        compile ':cache:1.1.8'
        compile ":quartz:1.0.1"
        compile ":csv:0.3.1"
        runtime ":jquery:1.11.1"

        // plugins needed at runtime but not for compilation
        runtime ":hibernate:3.6.10.16"
        runtime ":database-migration:1.4.0"
        runtime ":images-client-plugin:0.8"
        runtime ":ala-bootstrap2:2.4.5"
        runtime ":ala-auth:1.3.1"
        runtime ":cors:1.1.8"
    }
}
