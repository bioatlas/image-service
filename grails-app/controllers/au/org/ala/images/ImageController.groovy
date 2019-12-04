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

    /**
     * @deprecated use getOriginalFile instead.
     */
    @Deprecated
    def proxyImage() {
        def imageIdentifier = imageService.getImageGUIDFromParams(params)
        if (imageIdentifier) {
            def imageUrl = imageService.getImageUrl(imageIdentifier)
            boolean contentDisposition = params.boolean("contentDisposition")
            proxyImageRequest(response, imageUrl, imageIdentifier, "", "", 0, contentDisposition)
            if (grailsApplication.config.analytics.trackThumbnails.toBoolean()) {
               def imageInstance = Image.findByImageIdentifier(imageIdentifier)
               sendAnalytics(imageInstance, 'imageview')
            }
        } else {
            response.sendError(404, "Image not found")
        }
    }

    @ApiOperation(
            value = "Get original image, sound or video file.",
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
    def getOriginalFile() {
        def imageIdentifier = imageService.getImageGUIDFromParams(params)
        if (imageIdentifier) {
            def imageInstance = Image.findByImageIdentifier(imageIdentifier)
            def imageUrl = imageService.getImageUrl(imageIdentifier)
            boolean contentDisposition = params.boolean("contentDisposition")
            proxyImageRequest(response, imageUrl, imageIdentifier, imageInstance.extension, imageInstance.mimeType, imageInstance.fileSize.toInteger(), contentDisposition)
            if (grailsApplication.config.analytics.trackThumbnails.toBoolean()) {
                sendAnalytics(imageInstance, 'imageview')
            }
        } else {
            response.sendError(404, "Image not found")
        }
    }

    /**
     * This method serves the image from the file system where possible for better performance.
     * proxyImageThumbnail is used heavily by applications rendering search results (biocache, BIE).
     */
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
        def imageIdentifier = imageService.getImageGUIDFromParams(params)
        boolean addContentDisposition = params.boolean("contentDisposition")
        if (imageIdentifier) {
            def imageFilePath = imageStoreService.getImageThumbFile(imageIdentifier)
            def imageFile = new File(imageFilePath)
            if (imageFile.exists()){
                serveImageFile(response, imageFile, imageIdentifier, "image/jpeg", "jpg", addContentDisposition)
                if (grailsApplication.config.analytics.trackThumbnails.toBoolean()) {
                    def imageInstance = Image.findByImageIdentifier(imageIdentifier)
                    sendAnalytics(imageInstance, 'imageview')
                }
            } else {
                //may be sound file or document
                def imageInstance = Image.findByImageIdentifier(imageIdentifier)
                if (imageInstance) {
                    if (imageInstance.mimeType.startsWith('audio')) {
                        proxyImageRequest(response, grailsApplication.config.placeholder.sound.thumbnail as String, imageIdentifier, imageInstance.extension, imageInstance.mimeType, 0, addContentDisposition)
                    } else {
                        proxyImageRequest(response, grailsApplication.config.placeholder.document.thumbnail as String, imageIdentifier, imageInstance.extension, imageInstance.mimeType, 0, addContentDisposition)
                    }
                } else {
                    response.sendError(404, "Resource not found")
                }
            }
        } else {
            response.sendError(404, "Image not found")
        }
    }

    /**
     * Serve the image file from the file system.
     *
     * @param response
     * @param filePath
     * @param imageIdentifier
     * @param contentType
     * @param extension
     * @param addContentDisposition
     * @return
     */
    private def serveImageFile(response, String filePath, String imageIdentifier, String contentType, String extension, boolean addContentDisposition){
        def file = new File(filePath)
        serveImageFile(response, file, imageIdentifier, contentType, extension, addContentDisposition)
    }

    /**
     * Serve image from file system.
     *
     * @param response
     * @param file
     * @param imageIdentifier
     * @param contentType
     * @param extension
     * @param addContentDisposition
     * @return
     */
    private def serveImageFile(response, File file, String imageIdentifier, String contentType, String extension, boolean addContentDisposition){
        response.setContentLength(file.size() as int)
        response.setContentType(contentType)
        if (addContentDisposition) {
            response.setHeader("Content-disposition", "attachment;filename=${imageIdentifier}.${extension ?: "jpg"}")
        }
        response.status = 200
        file.withInputStream { stream ->
            IOUtils.copy(stream, response.outputStream)
            response.flushBuffer()
        }
    }

    @ApiOperation(
            value = "Get image thumbnail large version",
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
        def imageIdentifier = imageService.getImageGUIDFromParams(params)
        boolean addContentDisposition = params.boolean("contentDisposition")
        if (imageIdentifier) {
            def imageFilePath = imageStoreService.getImageThumbLargeFile(imageIdentifier)
            def imageFile = new File(imageFilePath)
            if (imageFile.exists()){
                serveImageFile(response, imageFile, imageIdentifier, "image/jpeg", "jpg", addContentDisposition)
                if (grailsApplication.config.analytics.trackThumbnails.toBoolean()) {
                    def imageInstance = Image.findByImageIdentifier(imageIdentifier)
                    sendAnalytics(imageInstance, 'imageview')
                }

            } else {
                //may be sound file or document
                def imageInstance = Image.findByImageIdentifier(imageIdentifier)
                if (imageInstance) {
                    if (imageInstance.mimeType.startsWith('audio')) {
                        proxyImageRequest(response, grailsApplication.config.placeholder.sound.thumbnail as String, imageIdentifier, "jpg", "image/jpeg", 0, addContentDisposition)
                    } else {
                        proxyImageRequest(response, grailsApplication.config.placeholder.document.thumbnail as String, imageIdentifier, "jpg", "image/jpeg", 0, addContentDisposition)
                    }
                } else {
                    response.sendError(404, "Resource not found")
                }
            }
        } else {
            response.sendError(404, "Image ID not recognised.")
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
        if (new File(url).exists()) {
            proxyUrl(new URL(url), response)
        } else {
            response.sendError(404, "Image tile not found")
        }
    }

    private void proxyImageRequest(HttpServletResponse response, String imageUrl, String imageIdentifier, String extension, String mimeType, int contentLength, boolean addContentDisposition = false) {

        def u = new URL(imageUrl)
        response.setContentType(mimeType ?: "image/jpeg")

        // this is specifically for iNaturalist data, where extensions are currently parsed to strings like m4a?12312312
        if (extension && extension.contains("?")){
            def cleanedExtension = extension.substring(0, extension.indexOf("?"))
            if (cleanedExtension && cleanedExtension.length() > 0){
                extension  = cleanedExtension
            }
        }

        if (imageIdentifier && addContentDisposition) {
            response.setHeader("Content-disposition", "attachment;filename=${imageIdentifier}.${extension ?: "jpg"}")
        }

        if (contentLength) {
            response.setContentLength(contentLength)
        }

        proxyUrl(u, response)
    }

    private void proxyUrl(URL u, HttpServletResponse response) {

        InputStream is = null
        try {
            is = u.openStream()
        } catch (Exception ex) {
            logService.error("Failed it proxy URL:" + u, ex)
            response.sendError(404, "Image unavailable or inaccessible.")
        }

        if (is) {
            try {
                IOUtils.copy(is, response.outputStream)
            } catch (Exception ex) {
                logService.error("Unable to proxy image:" + u, ex)
                response.sendError(404, "Unable to proxy image")
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
            notes = "To get an image, supply an 'Accept' header with a value of 'image/jpeg'",
            produces = "image/jpeg,application/json,text/html",
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

        if (request.getHeader('accept')) {

            boolean imageRequest = request.getHeader('accept').indexOf(MediaType.IMAGE_JPEG.toString()) > -1
            boolean htmlRequest = request.getHeader('accept').indexOf("text/html") > -1

            def imageInstance = imageService.getImageFromParams(params)
            if (htmlRequest){
                if (imageInstance) {
                    getImageModel(imageInstance)
                } else {
                    flash.errorMessage = "Could not find image with id ${params.int("id") ?: params.imageId }!"
                    redirect(action:'list', controller: 'search')
                }
            } else if(imageRequest){
                if (imageInstance) {
                    def imageUrl = imageService.getImageUrl(imageInstance.imageIdentifier)
                    boolean contentDisposition = params.boolean("contentDisposition")
                    proxyImageRequest(response, imageUrl, imageInstance.imageIdentifier, imageInstance.extension, imageInstance.mimeType, (int) imageInstance.fileSize ?: 0, contentDisposition)
                    if (grailsApplication.config.analytics.trackDetailedView.toBoolean()) {
                        sendAnalytics(imageInstance, 'imageview')
                    }
                } else {
                    response.setStatus(404)
                    proxyImageRequest(response, grailsApplication.config.placeholder.missing.thumbnail as String, null, null, "image/png", 0, false)
                }
            } else {
                renderImageInstance(imageInstance)
            }
        } else {
            //default to html view
            def image = imageService.getImageFromParams(params)
            if (image) {
                getImageModel(image)
            } else {
                flash.errorMessage = "Could not find image with id ${params.int("id") ?: params.imageId }!"
                redirect(action:'list', controller: 'search')
            }
        }
    }

    private renderImageInstance(Object imageInstance) {
        response.addHeader("Access-Control-Allow-Origin", "")
        withFormat {
            json {

                if(imageInstance) {
                    def jsonStr = imageInstance as JSON
                    if (params.callback) {
                        response.setContentType("text/javascript")
                        render("${params.callback}(${jsonStr})")
                    } else {
                        response.setContentType("application/json")
                        render(jsonStr)
                    }
                } else {
                    response.status = 404
                    render([success:false] as JSON)
                }
            }
            xml {
                if(imageInstance) {
                    render(imageInstance as XML)
                } else {
                    response.status = 404
                    render([success:false] as XML)
                }
            }
        }
    }

    private def getImageModel(Image image){
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

        if (!userId){
            userId =  authService.getUserId()
        }

        def thumbUrls = imageService.getAllThumbnailUrls(image.imageIdentifier)

        boolean isImage = imageService.isImageType(image)

        //add additional metadata
        def resourceLevel = collectoryService.getResourceLevelMetadata(image.dataResourceUid)

        if (grailsApplication.config.analytics.trackDetailedView.toBoolean()) {
            sendAnalytics(image, 'imagedetailedview')
        }

        [imageInstance: image, subimages: subimages,
         parentImage: image.parent,
         sizeOnDisk: sizeOnDisk,
         squareThumbs: thumbUrls, isImage: isImage, resourceLevel: resourceLevel, isAdmin:isAdmin, userId:userId]
    }

    def view() {
        def image = imageService.getImageFromParams(params)
        if (!image) {
            flash.errorMessage = "Could not find image with id ${params.int("id")}!"
        }
        def subimages = Subimage.findAllByParentImage(image)*.subimage

        if (grailsApplication.config.analytics.trackLargeViewer.toBoolean()) {
            sendAnalytics(image, 'imagelargeviewer')
        }

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
        def source = null
        if (imageInstance) {
            if (params.source) {
                source = MetaDataSourceType.valueOf(params.source)
                if (source){
                    metaData = imageInstance.metadata?.findAll { it.source == source }
                } else {
                    metaData = imageInstance.metadata
                }
            } else {
                metaData = imageInstance.metadata
            }
        }

        [imageInstance: imageInstance, metaData: metaData?.sort { it.name }, source: source]
    }

    def coreImageMetadataTableFragment() {
        def imageInstance = imageService.getImageFromParams(params)

        render(view: '_coreImageMetadataFragment', model: getImageModel(imageInstance))
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
        if (grailsApplication.config.analytics.trackLargeViewer.toBoolean()) {
            sendAnalytics(imageInstance, 'imagelargeviewer')
        }
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

