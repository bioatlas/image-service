package au.org.ala.images

import au.org.ala.cas.util.AuthenticationUtils
import au.org.ala.web.AlaSecured
import au.org.ala.web.CASRoles
import grails.converters.JSON
import grails.web.servlet.mvc.GrailsParameterMap

class SelectionController {

    def selectionService
    def imageService
    def albumService

    def index() {
        redirect(action:'list')
    }

    def ajaxSelectImage() {
        def image = getImageFromParams(params)
        def userId = AuthenticationUtils.getUserId(request)

        if (!userId) {
            render([success:false, message:"Not logged in (or url filters are wrong!)"] as JSON)
            return
        }

        if (image) {
            def result = selectionService.selectImage(userId, image)
            render([success:result] as JSON)
        } else {
            render([success:false, message:"No image id specified!"] as JSON)
        }
    }

    def ajaxSelectImages() {

        def idList = params."imageList[]"
        def userId = AuthenticationUtils.getUserId(request)
        if (idList && userId) {
            selectionService.selectImages(userId, idList)
            render([success:true] as JSON)
            return
        }
        render([success:false] as JSON)
    }

    def ajaxDeselectImages() {

        def idList = params."imageList[]"
        def userId = AuthenticationUtils.getUserId(request)
        if (idList && userId) {
            idList.each { String imageId ->
                def image = Image.get(imageId.toLong())
                if (image) {
                    selectionService.deselectImage(userId, image)
                }
            }
            render([success:true] as JSON)
            return
        }
        render([success:false] as JSON)
    }

    def ajaxDeselectImage() {
        def image = getImageFromParams(params)
        def userId = AuthenticationUtils.getUserId(request)

        if (!userId) {
            render([success:false, message:"Not logged in (or url filters are wrong!)"] as JSON)
            return
        }

        if (image) {
            def result = selectionService.deselectImage(userId, image)
            render([success:result] as JSON)
        } else {
            render([success:false, message:"No image id specified!"] as JSON)
        }
    }

    def clearSelection() {
        def userId = AuthenticationUtils.getUserId(request)

        if (!userId) {
            render([success:false, message:"Not logged in (or url filters are wrong!)"] as JSON)
            return
        }

        selectionService.clearSelection(userId)
        render([success:true] as JSON)
    }

    def ajaxGetSelectedImages() {
        def userId = AuthenticationUtils.getUserId(request)
        if (!userId) {
            render([success:false, message:"Not logged in (or url filters are wrong!)"] as JSON)
            return
        }

        def results = [success: true, images:[]]
        selectionService.withSelectedImages(userId) { image ->
            results.images << imageService.getImageInfoMap(image)
        }
        render(results as JSON)
    }

    def userContextFragment() {
        def userId = AuthenticationUtils.getUserId(request)
        def selectionCount = 0
        if (userId) {
            selectionCount = selectionService.selectionSize(userId)
        }
        [userId: userId, selectionCount: selectionCount]
    }

    private static Image getImageFromParams(GrailsParameterMap params) {
        def image = Image.get(params.int("id"))
        if (!image) {
            image = Image.findByImageIdentifier(params.imageId as String)
        }
        return image
    }

    def list() {
        def userId = AuthenticationUtils.getUserId(request)
        def selected = []
        def total = 0
        if (userId) {

            params.max = params.max ?: 20
            params.offset = params.offset ?: 0

            def ct = new CodeTimer("List Selected Images")
            selected = selectionService.getSelectedImages(userId, params)
            total = selectionService.selectionSize(userId)

            ct.stop(true)
        }

        [selectedImages: selected, total: total]
    }

    @AlaSecured(value = [CASRoles.ROLE_ADMIN], message = "You do not have sufficient privileges to perform this action", redirectAction = "list")
    def deleteSelected() {

        def userId = AuthenticationUtils.getUserId(request)
        def selected = []

        def count = 0
        if (userId) {
            count = selectionService.deleteSelectedImages(userId)
        }

        flash.message = "${count} images scheduled for deletion"
        redirect(action:'list')
    }

    @AlaSecured(value = [CASRoles.ROLE_ADMIN], message = "You do not have sufficient privileges to perform this action", redirectAction = "list")
    def generateThumbnails() {

        def userId = AuthenticationUtils.getUserId(request)
        def selected = []
        if (userId) {
            def album = selectionService.getOrCreateUserSelection(userId)
            albumService.scheduleThumbnailRegeneration(album, userId)
        }

        flash.message = "Scheduled thumbnail generation for selected images."

        redirect(action:'list')
    }

    @AlaSecured(value = [CASRoles.ROLE_ADMIN], message = "You do not have sufficient privileges to perform this action", redirectAction = "list")
    def generateTMSTiles() {

        def userId = AuthenticationUtils.getUserId(request)
        def selected = []
        if (userId) {
            def album = selectionService.getOrCreateUserSelection(userId)
            albumService.scheduleTileRegeneration(album, userId)
        }

        flash.message = "Scheduled tile generation for selected images."

        redirect(action:'list')

    }

    @AlaSecured(value=[CASRoles.ROLE_USER], anyRole = true)
    def addSelectionToAlbum() {

        def userId = AuthenticationUtils.getUserId(request)
        def album = Album.get(params.int('albumId'))
        if (album) {
            selectionService.withSelectedImages(userId) { Image image ->
                albumService.addImageToAlbum(album, image)
            }
        } else {
            flash.errorMessage = "Album not found, or no album specified!"
        }

        redirect(action:'list')
    }

}
