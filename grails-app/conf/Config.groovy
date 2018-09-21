/******************************************************************************\
 *  CONFIG MANAGEMENT
\******************************************************************************/
def appName = 'images'
def ENV_NAME = "${appName.toUpperCase()}_CONFIG"
default_config = "/data/${appName}/config/${appName}-config.properties"
if(!grails.config.locations || !(grails.config.locations instanceof List)) {
    grails.config.locations = []
}
if(System.getenv(ENV_NAME) && new File(System.getenv(ENV_NAME)).exists()) {
    println "[${appName}] Including configuration file specified in environment: " + System.getenv(ENV_NAME);
    grails.config.locations.add "file:" + System.getenv(ENV_NAME)
} else if(System.getProperty(ENV_NAME) && new File(System.getProperty(ENV_NAME)).exists()) {
    println "[${appName}] Including configuration file specified on command line: " + System.getProperty(ENV_NAME);
    grails.config.locations.add "file:" + System.getProperty(ENV_NAME)
} else if(new File(default_config).exists()) {
    println "[${appName}] Including default configuration file: " + default_config;
    grails.config.locations.add "file:" + default_config
} else {
    println "[${appName}] No external configuration file defined."
}

println "[${appName}] (*) grails.config.locations = ${grails.config.locations}"

skin.layout = "images"
//headerAndFooter.baseURL = 'https://www2.ala.org.au/commonui'
ala.baseURL = "https://www.ala.org.au"
bie.baseURL = "https://bie.ala.org.au"
bie.searchPath = "/search"
collectory.baseURL = "https://collections.ala.org.au"
biocache.baseURL = "https://biocache.ala.org.au"
bie.baseURL = "https://bie.ala.org.au"

grails.project.groupId = "au.org.ala"
grails.cache.enabled = true

skin.layout = "main"
skin.orgNameLong = "Atlas of Living Australia"

//grails.project.groupId = appName // change this to alter the default package name and Maven publishing destination

// The ACCEPT header will not be used for content negotiation for user agents containing the following strings (defaults to the 4 major rendering engines)
grails.mime.disable.accept.header.userAgents = ['Gecko', 'WebKit', 'Presto', 'Trident']
grails.mime.types = [ // the first one is the default format
    all:           '*/*', // 'all' maps to '*' or the first available format in withFormat
    atom:          'application/atom+xml',
    css:           'text/css',
    csv:           'text/csv',
    form:          'application/x-www-form-urlencoded',
    html:          ['text/html','application/xhtml+xml'],
    js:            'text/javascript',
    json:          ['application/json', 'text/json'],
    multipartForm: 'multipart/form-data',
    rss:           'application/rss+xml',
    text:          'text/plain',
    hal:           ['application/hal+json','application/hal+xml'],
    xml:           ['text/xml', 'application/xml']
]

// URL Mapping Cache Max Size, defaults to 5000
//grails.urlmapping.cache.maxsize = 1000

// What URL patterns should be processed by the resources plugin
grails.resources.adhoc.patterns = ['/images/*', '/css/*', '/js/*', '/plugins/*']

// Legacy setting for codec used to encode data with ${}
grails.views.default.codec = "html"

// The default scope for controllers. May be prototype, session or singleton.
// If unspecified, controllers are prototype scoped.
grails.controllers.defaultScope = 'singleton'

// GSP settings
grails {
    views {
        gsp {
            encoding = 'UTF-8'
            htmlcodec = 'xml' // use xml escaping instead of HTML4 escaping
            codecs {
                expression = 'html' // escapes values inside ${}
                scriptlet = 'html' // escapes output from scriptlets in GSPs
                taglib = 'none' // escapes output from taglibs
                staticparts = 'none' // escapes output from static template parts
            }
        }
        // escapes all not-encoded output at final stage of outputting
        filteringCodecForContentType {
            //'text/html' = 'html'
        }
    }
}
 
grails.converters.encoding = "UTF-8"
// scaffolding templates configuration
grails.scaffolding.templates.domainSuffix = 'Instance'

// Set to false to use the new Grails 1.2 JSONBuilder in the render method
grails.json.legacy.builder = false
// enabled native2ascii conversion of i18n properties files
grails.enable.native2ascii = true
// packages to include in Spring bean scanning
grails.spring.bean.packages = []
// whether to disable processing of multi part requests
grails.web.disable.multipart=false

// request parameters to mask when logging exceptions
grails.exceptionresolver.params.exclude = ['password']

// configure auto-caching of queries by default (if false you can cache individual queries with 'cache: true')
grails.hibernate.cache.queries = false

elasticsearch.location="/data/images/elasticsearch"

imageservice.imagestore.root = "/data/images/store"
imageservice.imagestore.inbox = "/data/images/incoming"
imageservice.thumbnail.size = 300
imageservice.pathToImgCnv = "/data/images/bin/imgcnv"

imageservice.apache.root = "http://dev.ala.org.au/data/images/store/"
grails.serverURL = "http://dev.ala.org.au:8080/ala-images"

environments {
    development {
        grails.logging.jul.usebridge = true
    }
    test {
        grails.serverURL = "http://images-dev.ala.org.au"
        imageservice.apache.root = "http://images-dev.ala.org.au/data/images/store/"
    }
    production {
        grails.logging.jul.usebridge = true
        grails.serverURL = "http://images.ala.org.au"
        imageservice.apache.root = "http://images.ala.org.au/images/store/"
    }
}

// log4j configuration
def loggingDir = (System.getProperty('catalina.base') ? System.getProperty('catalina.base') + '/logs' : './logs')
// work around for travis and local builds
if(!(new File(loggingDir).exists())){
    loggingDir = "/tmp/"
}

// log4j configuration
log4j = {
    // Example of changing the log pattern for the default console appender:
    appenders {
        environments {
            production {
                rollingFile name: "tomcatLog", maxFileSize: 102400000, file: loggingDir + "/${appName}.log", threshold: org.apache.log4j.Level.INFO, layout: pattern(conversionPattern: "[%d] %m%n")
                'null' name: "stacktrace"
            }
            development {
                console name: "stdout", layout: pattern(conversionPattern: "[%d] %-5p [%c{1}]  %m%n"), threshold: org.apache.log4j.Level.DEBUG
            }
            test {
                console name: "stdout", layout: pattern(conversionPattern: "[%d] %-5p [%c{1}]  %m%n"), threshold: org.apache.log4j.Level.INFO
            }
        }
    }

    root {
        info 'stdout'
    }

    error   'org.codehaus.groovy.grails.web.servlet',        // controllers
            'org.codehaus.groovy.grails.web.pages',          // GSP
            'org.codehaus.groovy.grails.web.sitemesh',       // layouts
            'org.codehaus.groovy.grails.web.mapping.filter', // URL mapping
            'org.codehaus.groovy.grails.web.mapping',        // URL mapping
            'org.codehaus.groovy.grails.commons',            // core / classloading
            'org.codehaus.groovy.grails.plugins',            // plugins
            'org.codehaus.groovy.grails.orm.hibernate',      // hibernate integration
            'org.springframework',
            'org.hibernate',
            'net.sf.ehcache.hibernate'
    info    'grails.app',
            'au.org.ala.image'
    debug   'grails.app.controllers',
            'grails.app.services',
            //'grails.app.taglib',
            'grails.web.pages',
            //'grails.app',
//            'au.org.ala.cas',
            'au.org.ala.image'
}
