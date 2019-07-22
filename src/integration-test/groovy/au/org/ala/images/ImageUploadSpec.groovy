package au.org.ala.images

import grails.plugins.rest.client.RestBuilder
import grails.plugins.rest.client.RestResponse
import grails.testing.mixin.integration.Integration
import grails.transaction.*
import image.service.Application
import org.springframework.util.LinkedMultiValueMap
import org.springframework.util.MultiValueMap
import spock.lang.Shared
import spock.lang.Specification

@Integration(applicationClass = Application.class)
@Rollback
class ImageUploadSpec extends Specification {

    @Shared RestBuilder rest = new RestBuilder()

    def setup() {}

    def cleanup() {}

    void "test home page"() {
        when:
        RestResponse resp = rest.get("http://localhost:${serverPort}")
        then:
        resp.status == 200
    }

    void "test upload image - empty request - should result in 400"() {
        when:
        RestResponse resp = rest.post("http://localhost:${serverPort}/ws/uploadImagesFromUrls", [:])
        then:
        resp.status == 400
    }

    void "test upload image"() {
        when:
        MultiValueMap<String, String> form = new LinkedMultiValueMap<String, String>()
        form.add("imageUrl", "https://www.ala.org.au/wp-content/themes/ala-wordpress-theme/img/homepage-channel-image-rainbow-lorikeet.jpg")

        RestResponse resp = rest.post("http://localhost:${serverPort}/ws/uploadImage", {
            contentType("application/x-www-form-urlencoded")
            body(form)
        })
        then:
        resp.status == 200
        println(resp.text)
    }

    void "test multi upload image"() {
        when:
        RestResponse resp = rest.post("http://localhost:${serverPort}/ws/uploadImagesFromUrls",{
           json {
               [images:[[sourceURL:"https://www.ala.org.au/wp-content/themes/ala-wordpress-theme/img/homepage-channel-image-rainbow-lorikeet.jpg"],
                        [sourceURL:"https://www.ala.org.au/wp-content/themes/ala-wordpress-theme/img/homepage-channel-image-lionfish.jpg"]
               ]]
           }
        })
        then:
        resp.status == 200
        println(resp.text)
    }

    void 'test iNaturalist bug'(){
        when:

        MultiValueMap<String, String> form = new LinkedMultiValueMap<String, String>()
        form.add("imageUrl", "https://static.inaturalist.org/photos/35335345/original.jpeg?1555821308")
        RestResponse resp = rest.post("http://localhost:${serverPort}/ws/uploadImage", {
            contentType("application/x-www-form-urlencoded")
            body(form)
        })

        MultiValueMap<String, String> form2 = new LinkedMultiValueMap<String, String>()
        form.add("imageUrl", "https://static.inaturalist.org/photos/35335341/original.jpeg?1555821307")
        RestResponse resp2 = rest.post("http://localhost:${serverPort}/ws/uploadImage", {
            contentType("application/x-www-form-urlencoded")
            body(form2)
        })



        then:
        resp.status == 200
        println(resp.text)
    }

}
