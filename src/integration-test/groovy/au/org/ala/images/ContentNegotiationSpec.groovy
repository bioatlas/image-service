package au.org.ala.images

import grails.plugins.rest.client.RestBuilder
import grails.plugins.rest.client.RestResponse
import grails.testing.mixin.integration.Integration
import grails.transaction.Rollback
import groovy.json.JsonSlurper
import image.service.Application
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.servlet.DefaultServlet
import org.eclipse.jetty.servlet.ServletContextHandler
import org.eclipse.jetty.servlet.ServletHolder
import org.springframework.util.LinkedMultiValueMap
import org.springframework.util.MultiValueMap
import spock.lang.Shared
import spock.lang.Specification
import groovyx.net.http.HTTPBuilder
import groovyx.net.http.Method

import java.security.MessageDigest

/**
 * Content negotiation tests for /image/<UUID> URLs
 */
@Integration(applicationClass = Application.class)
@Rollback
class ContentNegotiationSpec extends Specification {


    @Shared RestBuilder rest = new RestBuilder()

    def imageId

    def grailsApplication
    Server server

    def setup() {

        // Create HTTP Server to emulate nginx
        server = new Server(8880)
        ServletContextHandler context = new ServletContextHandler()
        ServletHolder defaultServ = new ServletHolder("default", DefaultServlet.class)
        def imageStoreDir = new File(grailsApplication.config.imageservice.imagestore.root)
        defaultServ.setInitParameter("resourceBase",imageStoreDir.getParent())
        defaultServ.setInitParameter("dirAllowed","true")
        context.addServlet(defaultServ,"/")
        server.setHandler(context)

        // Start Server
        server.start()

        MultiValueMap<String, String> form = new LinkedMultiValueMap<String, String>()
        form.add("imageUrl", "https://upload.wikimedia.org/wikipedia/commons/e/ed/Puma_concolor_camera_trap_Arizona_2.jpg")

        RestResponse resp = rest.post("http://localhost:${serverPort}/ws/uploadImage", {
            contentType("application/x-www-form-urlencoded")
            body(form)
        })

        def jsonResponse = new JsonSlurper().parseText(resp.body)
        imageId = jsonResponse.imageId

        assert imageId != null
    }

    def cleanup() {
        // Start Server
        if (server != null) {
            server.stop()
        }
    }

    /**
     * Testing equivalent of
     * curl -X GET "https://images.ala.org.au/image/1a6dc180-96b1-45df-87da-7d0912dddd4f" -H "Accept: application/json"
     */
    void "Test accept: application/json"() {
        when:
        RestResponse resp = rest.get("http://localhost:${serverPort}/image/" + imageId){
            accept "application/json"
        }

        def jsonResponse = new JsonSlurper().parseText(resp.body)

        then:
        resp.status == 200
        jsonResponse.originalFilename != null
    }

    /**
     * Testing equivalent of
     * curl -X GET "https://images.ala.org.au/image/ABC" -H "Accept: application/json"
     */
    void "Test accept: application/json with expected 404"() {
        when:
        RestResponse resp = rest.get("http://localhost:${serverPort}/image/ABC"){
            accept "application/json"
        }

        def jsonResponse = new JsonSlurper().parseText(resp.body)

        then:
        resp.status == 404
        jsonResponse.success == false
    }

    /**
     * Testing equivalent of
     * curl -X GET "https://images.ala.org.au/ws/image/ABC" -H "Accept: application/json"
     */
    void "Test WS accept: application/json with expected 404"() {
        when:
        RestResponse resp = rest.get("http://localhost:${serverPort}/image/ABC"){
            accept "application/json"
        }

        def jsonResponse = new JsonSlurper().parseText(resp.body)

        then:
        resp.status == 404
        jsonResponse.success == false
    }

    /**
     * Testing equivalent of
     * curl -X GET "https://images.ala.org.au/image/1a6dc180-96b1-45df-87da-7d0912dddd4f" -H "Accept: image/jpeg"
     */
    void "Test accept: image/jpeg"() {
        when:

        def imageInBytes = new HTTPBuilder("http://localhost:${serverPort}/image/" + imageId).request(Method.GET, "image/jpeg") {
            requestContentType = "image/jpeg"
            response.success = { resp, binary ->
                return binary.bytes
            }
        }
        MessageDigest md = MessageDigest.getInstance("MD5")
        def md5Hash = md.digest(imageInBytes)

        //compare image with source
        def imageAsBytes = new URL("https://upload.wikimedia.org/wikipedia/commons/e/ed/Puma_concolor_camera_trap_Arizona_2.jpg").getBytes()

        def md5Hash2 =  md.digest(imageAsBytes)

        then:
        md5Hash == md5Hash2
    }

    /**
     * Testing equivalent of
     * curl -X GET "https://images.ala.org.au/image/1a6dc180-96b1-45df-87da-7d0912dddd4f" -H "Accept: image/jpeg"
     */
    void "Test accept: image/jpeg - 404"() {
        when:
        def failresp
        def imageInBytes = new HTTPBuilder("http://localhost:${serverPort}/image/ABC").request(Method.GET, "image/jpeg") {
            requestContentType = "image/jpeg"
            response.failure = { failresp_inner ->
                failresp = failresp_inner
            }
        }

        then:
        assert failresp.status == 404
    }
}