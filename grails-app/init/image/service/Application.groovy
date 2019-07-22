package image.service

import grails.boot.GrailsApp
import grails.boot.config.GrailsAutoConfiguration
//import org.springframework.context.annotation.Bean
//import org.springframework.context.annotation.Import
//import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry
//import org.springframework.web.servlet.config.annotation.WebMvcConfigurerAdapter
//import springfox.documentation.spring.web.plugins.Docket
//import springfox.documentation.swagger2.annotations.EnableSwagger2
//import springfox.documentation.spi.*
//import static com.google.common.base.Predicates.not
//import static springfox.documentation.builders.PathSelectors.ant

//@EnableSwagger2
// 2. Import the springfox grails integration configuration
//@Import([springfox.documentation.grails.SpringfoxGrailsIntegrationConfiguration])
class Application extends GrailsAutoConfiguration {
    static void main(String[] args) {
        GrailsApp.run(Application, args)
    }
//
//    // 3. **Optionally** define a custom docket or omit this step to use the default
//    // For grails it is preferrable to use use the following settings.
//    @Bean
//    Docket api() {
//        new Docket(DocumentationType.SWAGGER_2)
//                .ignoredParameterTypes(MetaClass)
//                .select()
////                .paths(not(ant("/error")))
//                .build()
//    }
//
//    // 4. **Optionally** in the absense of asset pipeline configure the swagger-ui webjar to serve the scaffolded
//    //swagger UI
//    @Bean
//    static WebMvcConfigurerAdapter webConfigurer() {
//        new WebMvcConfigurerAdapter() {
//            @Override
//            void addResourceHandlers(ResourceHandlerRegistry registry) {
//                if (!registry.hasMappingForPattern("/webjars/**")) {
//                    registry
//                            .addResourceHandler("/webjars/**")
//                            .addResourceLocations("classpath:/META-INF/resources/webjars/")
//                }
//                if (!registry.hasMappingForPattern("/swagger-ui.html")) {
//                    registry
//                            .addResourceHandler("/swagger-ui.html")
//                            .addResourceLocations("classpath:/META-INF/resources/swagger-ui.html")
//                }
//            }
//        }
//    }
}