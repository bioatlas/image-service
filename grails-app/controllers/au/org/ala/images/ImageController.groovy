package au.org.ala.images

import au.org.ala.cas.util.AuthenticationUtils
import au.org.ala.web.AlaSecured
import au.org.ala.web.CASRoles
import grails.converters.JSON
import grails.converters.XML
import io.swagger.annotations.Api
import io.swagger.annotations.ApiImplicitParam
import io.swagger.annotations.ApiImplicitParams
import io.swagger.annotations.ApiOperation
import io.swagger.annotations.ApiResponse
import io.swagger.annotations.ApiResponses
import org.apache.commons.io.IOUtils
import org.springframework.http.MediaType
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.multipart.MultipartHttpServletRequest

import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
import groovyx.net.http.HTTPBuilder
import static groovyx.net.http.Method.POST

@Api(value = "/image", tags = ["Access to image derivatives (e.g. thumbnails, tiles and originals)"], description = "Image Web Services")
class ImageController {

    def imageService
    def imageStoreService
    def logService
    def imageStagingService
    def batchService
    def collectoryService
    def authService

    def index() { }

    def list(){
        redirect(controller: 'search', action:'list')
    }

    @ApiOperation(
            value = "Get original image",
            nickname = "{id}/original",
            produces = "image/jpeg",
            httpMethod = "GET"
    )
    @ApiResponses([
            @ApiResponse(code = 200, message = "OK"),
            @ApiResponse(code = 404, message = "Image Not Found")]
    )
    @ApiImplicitParams([
            @ApiImplicitParam(name = "id", paramType = "path", required = true, value = "Image Id", dataType = "string")
    ])
    def proxyImage() {
        def imageInstance = imageService.getImageFromParams(params)
        if (imageInstance) {
            def imageUrl = imageService.getImageUrl(imageInstance.imageIdentifier)
            boolean contentDisposition = params.boolean("contentDisposition")
            proxyImageRequest(response, imageInstance, imageUrl, (int) imageInstance.fileSize ?: 0, contentDisposition)
            sendAnalytics(imageInstance, 'imageview')
        }
    }

    @ApiOperation(
            value = "Get image thumbnail",
            nickname = "{id}/thumbnail",
            produces = "image/jpeg",
            httpMethod = "GET"
    )
    @ApiResponses([
            @ApiResponse(code = 200, message = "OK"),
            @ApiResponse(code = 404, message = "Image Not Found")]
    )
    @ApiImplicitParams([
            @ApiImplicitParam(name = "id", paramType = "path", required = true, value = "Image Id", dataType = "string")
    ])
    def proxyImageThumbnail() {
        def imageInstance = imageService.getImageFromParams(params)
        if (imageInstance) {
            if(imageInstance.mimeType.startsWith('image')) {
                def imageUrl = imageService.getImageThumbUrl(imageInstance.imageIdentifier)
                proxyImageRequest(response, imageInstance, imageUrl, 0)
                sendAnalytics(imageInstance, 'imagethumbview')
            } else if(imageInstance.mimeType.startsWith('audio')){
                proxyImageRequest(response, imageInstance, grailsApplication.config.placeholder.sound.thumbnail, 0)
            } else {
                proxyImageRequest(response, imageInstance, grailsApplication.config.placeholder.document.thumbnail, 0)
            }
        }
    }

    @ApiOperation(
            value = "Get image large version",
            nickname = "{id}/large",
            produces = "image/jpeg",
            httpMethod = "GET"
    )
    @ApiResponses([
            @ApiResponse(code = 200, message = "OK"),
            @ApiResponse(code = 404, message = "Image Not Found")]
    )
    @ApiImplicitParams([
            @ApiImplicitParam(name = "id", paramType = "path", required = true, value = "Image Id", dataType = "string")
    ])
    def proxyImageThumbnailLarge() {
        def imageInstance = imageService.getImageFromParams(params)
        if (imageInstance) {
            def imageUrl = imageService.getImageThumbLargeUrl(imageInstance.imageIdentifier)
            proxyImageRequest(response, imageInstance, imageUrl, 0)
            sendAnalytics(imageInstance, 'imagelargeview')
        }
    }

    @ApiOperation(
            value = "Get image tile - for use with tile mapping service clients such as LeafletJS or Openlayers",
            nickname = "{id}/tms/{z}/{x}/{y}.png",
            produces = "image/jpeg",
            httpMethod = "GET"
    )
    @ApiResponses([
            @ApiResponse(code = 200, message = "OK"),
            @ApiResponse(code = 404, message = "Image Not Found")]
    )
    @ApiImplicitParams([
            @ApiImplicitParam(name = "id", paramType = "path", required = true, value = "Image Id", dataType = "string"),
            @ApiImplicitParam(name = "x", paramType = "path", required = true, value = "Tile mapping service X value", dataType = "string"),
            @ApiImplicitParam(name = "y", paramType = "path", required = true, value = "Tile mapping service Y value", dataType = "string"),
            @ApiImplicitParam(name = "z", paramType = "path", required = true, value = "Tile mapping service Z value", dataType = "string")
    ])
    def proxyImageTile() {
        def imageIdentifier = params.id
        def url = imageService.getImageTilesRootUrl(imageIdentifier)
        url += "/${params.z}/${params.x}/${params.y}.png"
        proxyUrl(new URL(url), response)
    }

    private void proxyImageRequest(HttpServletResponse response, Image imageInstance, String imageUrl, int contentLength, boolean addContentDisposition = false) {

        def u = new URL(imageUrl)
        response.setContentType(imageInstance.mimeType ?: "image/jpeg")
        if (addContentDisposition) {
            response.setHeader("Content-disposition", "attachment;filename=${imageInstance.imageIdentifier}.${imageInstance.extension ?: "jpg"}")
        }

        if (contentLength) {
            response.setContentLength(contentLength)
        }

        proxyUrl(u, response)
    }

    private void proxyUrl(URL u, HttpServletResponse response) {

        //async call to google analytics....
        InputStream is = null
        try {
            is = u.openStream()
        } catch (Exception ex) {
            logService.error("Failed it proxy URL", ex)
        }

        if (is) {
            try {
                IOUtils.copy(u.openStream(), response.outputStream)
            } finally {
                is.close()
                response.flushBuffer()
            }
        }
    }

    /**
     * POST event data to google analytics.
     *
     * @param imageInstance
     * @param eventCategory
     * @return
     */

    def sendAnalytics(Image imageInstance, String eventCategory){
        if (imageInstance && grailsApplication.config.analytics.ID){
            def queryURL =  grailsApplication.config.analytics.URL
            def requestBody = [
                           'v': 1,
                           'tid': grailsApplication.config.analytics.ID,
                           'cid': UUID.randomUUID().toString(),  //anonymous client ID
                           't': 'event',
                           'ec': eventCategory, // event category
                           'ea': imageInstance.dataResourceUid, //event value
                           'ua' : request.getHeader("User-Agent")
            ]

            def http = new HTTPBuilder(queryURL)
            http.request( POST ) {
                uri.path = '/collect'
                requestContentType = groovyx.net.http.ContentType.URLENC
                body =  requestBody

                response.success = { resp ->
                    println "POST response status: ${resp.statusLine}"
                }

                response.failure = { resp ->
                    println 'request failed = ' + resp.status
                }
            }
        }
    }

    /**
     * This service is used directly in front end in an AJAX fashion.
     * method to authenticate client applications.
     *
     * @return
     */
    def deleteImage() {

        def success = false

        def message = ""

        def image = Image.findByImageIdentifier(params.id as String)

        if (image) {
            def userId = getUserIdForRequest(request)

            if (userId){
                //is user in ROLE_ADMIN or the original owner of the image
                def isAdmin = authService.userInRole(CASRoles.ROLE_ADMIN)
                def isImageOwner = image.uploader == userId
                if (isAdmin || isImageOwner){
                    success = imageService.scheduleImageDeletion(image.id, userId)
                    message = "Image scheduled for deletion."
                } else {
                    message = "Logged in user is not authorised."
                }
            } else {
                message = "Unable to obtain user details."
            }
        } else {
            message = "Invalid image identifier."
        }
        renderResults(["success": success, message: message])
    }

    @AlaSecured(value = [CASRoles.ROLE_ADMIN], anyRole = true, redirectUri = "/")
    def scheduleArtifactGeneration() {

        def imageInstance = imageService.getImageFromParams(params)
        def userId = AuthenticationUtils.getUserId(request)
        def results = [success: true]

        if (imageInstance) {
            imageService.scheduleArtifactGeneration(imageInstance.id, userId)
            results.message = "Image artifact generation scheduled for image ${imageInstance.id}"
        } else {
            def imageList = Image.findAll()
            long count = 0
            imageList.each { image ->
                imageService.scheduleArtifactGeneration(image.id, userId)
                count++
            }
            results.message = "Image artifact generation scheduled for ${count} images."
        }

        renderResults(results)
    }

    @ApiOperation(
            value = "Get original image",
            nickname = "{id}",
            notes = "To get an image, supply an Accept-Content header with a value of 'image/jpeg'",
            produces = "image/jpeg",
            httpMethod = "GET"
    )
    @ApiResponses([
            @ApiResponse(code = 200, message = "OK"),
            @ApiResponse(code = 404, message = "Image Not Found")]
    )
    @ApiImplicitParams([
            @ApiImplicitParam(name = "id", paramType = "path", required = true, value = "Image Id", dataType = "string")
    ])
    def details() {
        if (request.getHeader('accept') && request.getHeader('accept').indexOf(MediaType.IMAGE_JPEG.toString()) > -1) {
            def imageInstance = imageService.getImageFromParams(params)
            if (imageInstance) {
                def imageUrl = imageService.getImageUrl(imageInstance.imageIdentifier)
                boolean contentDisposition = params.boolean("contentDisposition")
                proxyImageRequest(response, imageInstance, imageUrl, (int) imageInstance.fileSize ?: 0, contentDisposition)
                sendAnalytics(imageInstance, 'imageview')
            }
        } else {
            def image = imageService.getImageFromParams(params)
            if (!image) {
                flash.errorMessage = "Could not find image with id ${params.int("id") ?: params.imageId }!"
                redirect(action:'list', controller: 'search')
            } else {
                def subimages = Subimage.findAllByParentImage(image)*.subimage
                def sizeOnDisk = imageStoreService.getConsumedSpaceOnDisk(image.imageIdentifier)

                //accessible from cookie
                def userEmail = AuthenticationUtils.getEmailAddress(request)
                def userDetails = authService.getUserForEmailAddress(userEmail, true)
                def userId = userDetails ? userDetails.id : ""

                def isAdmin = false
                if (userDetails){
                    if (userDetails.getRoles().contains("ROLE_ADMIN"))
                        isAdmin = true
                }

                def albums = []

                def thumbUrls = imageService.getAllThumbnailUrls(image.imageIdentifier)

                boolean isImage = imageService.isImageType(image)

                //add additional metadata
                def resourceLevel = collectoryService.getResourceLevelMetadata(image.dataResourceUid)

                sendAnalytics(image, 'imagedetailedview')

                [imageInstance: image, subimages: subimages, sizeOnDisk: sizeOnDisk, albums: albums,
                 squareThumbs: thumbUrls, isImage: isImage, resourceLevel: resourceLevel, isAdmin:isAdmin, userId:userId]
            }
        }
    }

    def view() {
        def image = imageService.getImageFromParams(params)
        if (!image) {
            flash.errorMessage = "Could not find image with id ${params.int("id")}!"
        }
        def subimages = Subimage.findAllByParentImage(image)*.subimage
        sendAnalytics(image, 'imagelargeviewer')
        render (view: 'viewer', model: [imageInstance: image, subimages: subimages])
    }

    def tagsFragment() {
        def imageInstance = imageService.getImageFromParams(params)
        def imageTags = ImageTag.findAllByImage(imageInstance)
        def tags = imageTags?.collect { it.tag }
        def leafTags = TagUtils.getLeafTags(tags)

        [imageInstance: imageInstance, tags: leafTags]
    }

    @AlaSecured(value = [CASRoles.ROLE_ADMIN])
    def imageAuditTrailFragment() {
        def imageInstance = Image.get(params.int("id"))
        def messages = []
        if (imageInstance) {
            messages = AuditMessage.findAllByImageIdentifier(imageInstance.imageIdentifier, [order:'asc', sort:'dateCreated'])
        }
        [messages: messages]
    }

    def imageMetadataTableFragment() {

        def imageInstance = imageService.getImageFromParams(params)
        def metaData = []
        def source = params.source as MetaDataSourceType
        if (imageInstance) {
            if (source) {
                metaData = imageInstance.metadata?.findAll { it.source == source }
            } else {
                metaData = imageInstance.metadata
            }
        }

        [imageInstance: imageInstance, metaData: metaData?.sort { it.name }, source: source]
    }

    def imageTooltipFragment() {
        def imageInstance = imageService.getImageFromParams(params)
        [imageInstance: imageInstance]
    }

    def imageTagsTooltipFragment() {
        def imageInstance = imageService.getImageFromParams(params)
        def imageTags = ImageTag.findAllByImage(imageInstance)
        def tags = imageTags?.collect { it.tag }
        def leafTags = TagUtils.getLeafTags(tags)
        [imageInstance: imageInstance, tags: leafTags]
    }

    def createSubimageFragment() {
        def imageInstance = imageService.getImageFromParams(params)
        def metadata = ImageMetaDataItem.findAllByImage(imageInstance)
        [imageInstance: imageInstance, x: params.x, y: params.y, width: params.width, height: params.height, metadata: metadata]
    }

    def viewer() {
        def imageInstance = imageService.getImageFromParams(params)
        sendAnalytics(imageInstance, 'imagelargeviewer')
        [imageInstance: imageInstance, auxDataUrl: params.infoUrl]
    }

    @AlaSecured(value = [CASRoles.ROLE_USER, CASRoles.ROLE_ADMIN], anyRole = true, redirectUri = "/")
    def stagedImages() {
        def userId = AuthenticationUtils.getUserId(request)
        if (!userId) {
            throw new Exception("Must be logged in to use this service")
        }

        def stagedFiles = imageStagingService.buildStagedImageData(userId, params)
        def columns = StagingColumnDefinition.findAllByUserId(userId, [sort:'id', order:'asc'])

        [stagedFiles: stagedFiles, userId: userId, hasDataFile: imageStagingService.hasDataFileUploaded(userId),
         dataFileUrl: imageStagingService.getDataFileUrl(userId), dataFileColumns: columns]
    }

    @AlaSecured(value = [CASRoles.ROLE_USER, CASRoles.ROLE_ADMIN], anyRole = true, redirectUri = "/")
    def stageImages() {
        def userId = AuthenticationUtils.getUserId(request)
        if (!userId) {
            throw new Exception("Must be logged in to use this service")
        }
        if(request instanceof MultipartHttpServletRequest) {
            def filelist = []
            def errors = []

            ((MultipartHttpServletRequest) request).getMultiFileMap().imageFile.each { f ->
                if (f != null) {
                    try {
                        def stagedFile = imageStagingService.stageFile(userId, f)
                        if (stagedFile) {
                            filelist << stagedFile
                        } else {
                            errors << f
                        }
                    } catch (Exception ex) {
                        flash.message = "Failed to upload image file: " + ex.message;
                    }
                }

            }
        }
        redirect(action:'stagedImages')
    }

    @AlaSecured(value = [CASRoles.ROLE_USER, CASRoles.ROLE_ADMIN], anyRole = true, redirectUri = "/")
    def deleteStagedImage(int stagedImageId) {
        def userId = AuthenticationUtils.getUserId(request)
        if (!userId) {
            throw new Exception("Must be logged in to use this service")
        }
        def stagedFile = StagedFile.get(stagedImageId)
        if (stagedFile) {
            imageStagingService.deleteStagedFile(stagedFile)
        }
        redirect(action:'stagedImages')
    }

    @AlaSecured(value = [CASRoles.ROLE_USER, CASRoles.ROLE_ADMIN], anyRole = true, redirectUri = "/")
    def uploadStagingDataFile() {

        def userId = AuthenticationUtils.getUserId(request)
        if (!userId) {
            throw new Exception("Must be logged in to use this service")
        }

        if(request instanceof MultipartHttpServletRequest) {
            MultipartFile f = ((MultipartHttpServletRequest) request).getFile('dataFile')
            if (f != null) {
                def allowedMimeTypes = ['text/plain','text/csv', 'application/octet-stream', 'application/vnd.ms-excel']
                if (!allowedMimeTypes.contains(f.getContentType())) {
                    flash.message = "The data file must be one of: ${allowedMimeTypes}, recieved '${f.getContentType()}'}"
                    redirect(action:'stagedImages')
                    return
                }

                if (f.size == 0 || !f.originalFilename) {
                    flash.message = "You must select a file to upload"
                    redirect(action:'stagedImages')
                    return
                }
                imageStagingService.uploadDataFile(userId, f)
            }
        }
        redirect(action:'stagedImages')
    }

    @AlaSecured(value = [CASRoles.ROLE_USER, CASRoles.ROLE_ADMIN], anyRole = true, redirectUri = "/")
    def clearStagingDataFile() {
        def userId = AuthenticationUtils.getUserId(request)
        if (!userId) {
            throw new Exception("Must be logged in to use this service")
        }
        imageStagingService.deleteDataFile(userId)
        redirect(action:'stagedImages')
    }

    @AlaSecured(value = [CASRoles.ROLE_USER, CASRoles.ROLE_ADMIN], anyRole = true, redirectUri = "/")
    def saveStagingColumnDefinition() {
        def userId = AuthenticationUtils.getUserId(request)
        if (!userId) {
            throw new Exception("Must be logged in to use this service")
        }

        def fieldName = params.fieldName
        if (fieldName) {

            def fieldType = (params.fieldType as StagingColumnType) ?: StagingColumnType.Literal
            def format = params.definition ?: ""
            def fieldDefinition = StagingColumnDefinition.get(params.int("columnDefinitionId"))
            if (fieldDefinition) {
                // this is a 'save', not a 'create'
                fieldDefinition.fieldName = fieldName
                fieldDefinition.fieldDefinitionType = fieldType
                fieldDefinition.format = format
            } else {
                new StagingColumnDefinition(userId: userId, fieldDefinitionType: fieldType, format: format,
                        fieldName: fieldName).save(failOnError: true)
            }

        }
        redirect(action: 'stagedImages')
    }

    @AlaSecured(value = [CASRoles.ROLE_USER, CASRoles.ROLE_ADMIN], anyRole = true, redirectUri = "/")
    def editStagingColumnFragment() {
        def fieldDefinition = StagingColumnDefinition.get(params.int("columnDefinitionId"))
        def userId = AuthenticationUtils.getUserId(request)
        if (!userId) {
            throw new Exception("Must be logged in to use this service")
        }

        def hasDataFile = imageStagingService.hasDataFileUploaded(userId)

        def dataFileColumns = []
        if (hasDataFile) {
            dataFileColumns = ['']
            dataFileColumns.addAll(imageStagingService.getDataFileColumns(userId))
        }

        [fieldDefinition: fieldDefinition, hasDataFile: hasDataFile, dataFileColumns: dataFileColumns]
    }

    @AlaSecured(value = [CASRoles.ROLE_USER, CASRoles.ROLE_ADMIN], anyRole = true, redirectUri = "/")
    def deleteStagingColumnDefinition() {
        def userId = AuthenticationUtils.getUserId(request)
        if (!userId) {
            throw new Exception("Must be logged in to use this service")
        }
        def fieldDefinition = StagingColumnDefinition.get(params.int("columnDefinitionId"))
        if (fieldDefinition) {
            fieldDefinition.delete()
        }
        redirect(action:"stagedImages")
    }

    @AlaSecured(value = [CASRoles.ROLE_USER, CASRoles.ROLE_ADMIN], anyRole = true, redirectUri = "/")
    def uploadStagedImages() {
        def userId = AuthenticationUtils.getUserId(request)
        if (!userId) {
            throw new Exception("Must be logged in to use this service")
        }

        def harvestable = params.boolean("harvestable")

        def stagedFiles = imageStagingService.buildStagedImageData(userId, [:])

        def batchId = batchService.createNewBatch()
        int imageCount = 0
        stagedFiles.each { stagedFileMap ->
            def stagedFile = StagedFile.get(stagedFileMap.id)
            batchService.addTaskToBatch(batchId, new UploadFromStagedFileTask(stagedFile, stagedFileMap,
                    imageStagingService, batchId, harvestable))
            imageCount++
        }
        redirect(action:"stagedImages")
    }

    private renderResults(Object results, int responseCode = 200) {

        withFormat {
            json {
                def jsonStr = results as JSON
                if (params.callback) {
                    response.setContentType("text/javascript")
                    render("${params.callback}(${jsonStr})")
                } else {
                    response.setContentType("application/json")
                    render(jsonStr)
                }
            }
            xml {
                render(results as XML)
            }
        }
        response.addHeader("Access-Control-Allow-Origin", "")
        response.status = responseCode
    }

    @AlaSecured(value = [CASRoles.ROLE_USER, CASRoles.ROLE_ADMIN], anyRole = true, redirectUri = "/")
    def resetImageCalibration() {
        def image = Image.findByImageIdentifier(params.imageId)
        if (image) {
            imageService.resetImageLinearScale(image)
            renderResults([success: true, message:"Image linear scale has been reset"])
            return
        }
        renderResults([success:false, message:'Missing one or more required parameters: imageId, pixelLength, actualLength, units'])
    }

    private getUserIdForRequest(HttpServletRequest request) {
        if (grailsApplication.config.security.cas.disableCAS.toBoolean()){
            return "-1"
        }
        AuthenticationUtils.getUserId(request)
    }
}

