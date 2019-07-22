package image.service

class BootStrap {

    def elasticSearchService


    def init = { servletContext ->


        elasticSearchService.initialize()


    }
    def destroy = {
    }
}
