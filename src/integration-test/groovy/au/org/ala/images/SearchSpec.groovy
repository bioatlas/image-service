package au.org.ala.images

import grails.plugins.rest.client.RestBuilder
import grails.plugins.rest.client.RestResponse
import grails.testing.mixin.integration.Integration
import grails.transaction.Rollback
import groovy.json.JsonSlurper
import image.service.Application
import org.springframework.util.LinkedMultiValueMap
import org.springframework.util.MultiValueMap
import spock.lang.Ignore
import spock.lang.Shared
import spock.lang.Specification

@Integration(applicationClass = Application.class)
@Rollback
class SearchSpec extends Specification {

    @Shared RestBuilder rest = new RestBuilder()

    def setup() {}

    def cleanup() {}

    void 'test upload'() {

        when:
        def occurrenceID = "f4c13adc-2926-44c8-b2cd-fb2d62378a1a"

        //first upload an image
        def testUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/f/f1/Red_kangaroo_-_melbourne_zoo.jpg/800px-Red_kangaroo_-_melbourne_zoo.jpg"

        RestResponse uploadResponse = rest.post("http://localhost:${serverPort}/ws/uploadImagesFromUrls", {
            json {
                [images: [[
                          sourceUrl   : testUrl,
                          occurrenceID: occurrenceID
                  ]]]
            }
        })
        def jsonUploadResponse = new JsonSlurper().parseText(uploadResponse.body)

        then:
        uploadResponse.status == 200
        jsonUploadResponse.success == true
    }


    void 'test search for previous upload'(){

        when:

        boolean hasBacklog = true
        int counter = 0
        int MAX_CHECKS = 10


        while (hasBacklog && counter < MAX_CHECKS){
            RestResponse statsResp = rest.get("http://localhost:${serverPort}/ws/backgroundQueueStats")
            def json = new JsonSlurper().parseText(statsResp.body)
            if(json.queueLength > 0){
                Thread.sleep(5000)
            } else {
                hasBacklog = false
            }
            counter +=1
        }

        //search by occurrence ID
        RestResponse resp = rest.post("http://localhost:${serverPort}/ws/findImagesByMetadata",{
            json {
                [
                    "key": "occurrenceid",
                    "values": ["f4c13adc-2926-44c8-b2cd-fb2d62378a1a"]
                ]
            }
        })
        def jsonResponse = new JsonSlurper().parseText(resp.body)

        then:
        resp.status == 200
        jsonResponse.count > 0
    }
}
