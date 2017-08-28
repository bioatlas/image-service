package au.org.ala.images

import au.org.ala.cas.util.AuthenticationUtils
import au.org.ala.web.AlaSecured
import au.org.ala.web.CASRoles
import grails.converters.JSON
import grails.converters.XML
import org.apache.http.HttpResponse
import org.apache.http.HttpStatus
import org.grails.plugins.csv.CSVWriter
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.multipart.MultipartRequest

import javax.servlet.http.HttpServletRequest

class WebServiceController {

    static allowedMethods = [findImagesByMetadata: 'POST']

    def imageService
    def imageStoreService
    def tagService
    def searchService
    def logService
    def batchService
    def elasticSearchService
    def collectoryService
    def authService

    /**
     * This service is used directly in front end in an AJAX fashion.
     *
     * TODO produce an analogue to this service that will use the APIKey
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

            if(userId){
                //is user in ROLE_ADMIN or the original owner of the image
                def isAdmin = authService.userInRole(CASRoles.ROLE_ADMIN)
                def isImageOwner = image.uploader == userId
                if(isAdmin || isImageOwner){
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

    private long forEachImageId(closure) {
        def c = Image.createCriteria()
        def imageIdList = c {
            projections {
                property("id")
            }
        }

        long count = 0
        imageIdList.each { imageId ->
            if (closure) {
                closure(imageId)
            }
            count++
        }
        return count
    }

    def scheduleThumbnailGeneration() {
        def imageInstance = Image.findByImageIdentifier(params.id as String)
        def userId = getUserIdForRequest(request)
        def results = [success: true]

        if (params.id && !imageInstance) {
            results.success = false
            results.message = "Could not find image ${params.id}"
        } else {
            if (imageInstance) {
                imageService.scheduleThumbnailGeneration(imageInstance.id, userId)
                results.message = "Image thumbnail generation scheduled for image ${imageInstance.id}"
            } else {
                def count = forEachImageId { imageId ->
                    imageService.scheduleThumbnailGeneration(imageId, userId)
                }
                results.message = "Image thumbnail generation scheduled for ${count} images."
            }
        }

        renderResults(results)
    }

    def scheduleArtifactGeneration() {

        def imageInstance = Image.findByImageIdentifier(params.id as String)
        def userId = getUserIdForRequest(request)
        def results = [success: true]

        if (params.id && !imageInstance) {
            results.success = false
            results.message = "Could not find image ${params.id}"
        } else {
            if (imageInstance) {
                imageService.scheduleArtifactGeneration(imageInstance.id, userId)
                results.message = "Image artifact generation scheduled for image ${imageInstance.id}"
            } else {
                def count = forEachImageId { imageId ->
                    imageService.scheduleArtifactGeneration(imageId, userId)
                }
                results.message = "Image artifact generation scheduled for ${count} images."
            }
        }

        renderResults(results)
    }

    def scheduleKeywordRegeneration() {
        def imageInstance = Image.findByImageIdentifier(params.id as String)
        def userId = getUserIdForRequest(request)
        def results = [success:true]
        if (params.id && !imageInstance) {
            results.success = false
            results.message = "Could not find image ${params.id}"
        } else {
            if (imageInstance) {
                imageService.scheduleKeywordRebuild(imageInstance.id, userId)
                results.message = "Image keyword rebuild scheduled for image ${imageInstance.id}"
            } else {
                def imageList = Image.findAll()
                long count = 0
                imageList.each { image ->
                    imageService.scheduleKeywordRebuild(image.id, userId)
                    count++
                }
                results.message = "Image keyword rebuild scheduled for ${count} images."
            }
        }
        renderResults(results)
    }

    def scheduleInboxPoll() {
        def results = [success:true]
        def userId =  AuthenticationUtils.getUserId(request) ?: params.userId
        results.importBatchId = imageService.schedulePollInbox(userId)
        renderResults(results)
    }

    def getTagModel() {

        def newNode = { Tag tag, String label, boolean disabled = false ->
            [name: label, text: "${label}", children:[], 'icon': false, tagId: tag?.id, state:[disabled: disabled]]
        }

        def rootNode = newNode(null, "root")

        def tags
        if (params.q) {
            def query = params.q.toString().toLowerCase()
            def c = Tag.createCriteria()
            tags = c.list([order: 'asc', sort: 'path']) {
                like("path", "%${query}%")
            }

        } else {
            tags = Tag.list([order: 'asc', sort: 'path'])
        }

        tags.each { tag ->
            def path = tag.path
            if (path.startsWith(TagConstants.TAG_PATH_SEPARATOR)) {
                path = path.substring(TagConstants.TAG_PATH_SEPARATOR.length())
            }
            def bits = path.split(TagConstants.TAG_PATH_SEPARATOR)
            if (bits) {
                def parent = rootNode
                bits.eachWithIndex { pathElement, elementIndex ->
                    def child
                    child = parent.children?.find({ it.name == pathElement})
                    if (!child) {
                        boolean disabled = false
                        if (elementIndex < bits.size() - 1) {
                            disabled = true
                        }
                        child = newNode(tag, pathElement, disabled)
                        parent.children << child
                    }
                    parent = child
                }
            }
        }

        renderResults(rootNode.children)
    }

    def createTagByPath() {
        def success = false
        def tagPath = params.tagPath as String
        def tagId = 0
        if (tagPath) {

            def parent = Tag.get(params.int("parentTagId"))

            def tag = tagService.createTagByPath(tagPath, parent)
            success = tag != null
            tagId = tag.id
        }
        renderResults([success: success, tagId: tagId])
    }

    def moveTag() {
        def success = false

        def target = Tag.get(params.int("targetTagId"))
        def newParent = Tag.get(params.int("newParentTagId"))

        if (target) {

            tagService.moveTag(target, newParent)
        }

        renderResults([success: success])
    }

    def renameTag() {
        def success = false
        def tag = Tag.get(params.int("tagId"))
        if (tag && params.name) {
            tagService.renameTag(tag, params.name)
        }
        renderResults([success: success])
    }

    def deleteTag() {
        def success = false
        def tag = Tag.get(params.int("tagId"))
        if (tag) {
            tagService.deleteTag(tag)
        }
        renderResults([success: success])
    }

    def attachTagToImage() {
        def success = false
        def image = Image.findByImageIdentifier(params.id as String)
        def tag = Tag.get(params.int("tagId"))
        if (image && tag) {
            success = tagService.attachTagToImage(image, tag, AuthenticationUtils.getUserId(request))
        }
        renderResults([success: success])
    }

    def detachTagFromImage() {
        def success = false
        def image = Image.findByImageIdentifier(params.id as String)
        def tag = Tag.get(params.int("tagId"))
        if (image && tag) {
            success = tagService.detachTagFromImage(image, tag)
        }
        renderResults([success: success])
    }

    def getImageInfo() {
        def results = [success:false]
        def image = Image.findByImageIdentifier(params.id as String)
        if (image) {
            results.success = true
            addImageInfoToMap(image, results, params.boolean("includeTags"), params.boolean("includeMetadata"))
        }

        renderResults(results)
    }

    private addImageInfoToMap(Image image, Map results, Boolean includeTags, Boolean includeMetadata) {

        results.height = image.height
        results.width = image.width
        results.tileZoomLevels = image.zoomLevels ?: 0
        results.mimeType = image.mimeType
        results.originalFileName = image.originalFilename
        results.sizeInBytes = image.fileSize
        results.rights = image.rights ?: ''
        results.rightsHolder = image.rightsHolder ?: ''
        results.dateUploaded = formatDate(date: image.dateUploaded, format:"yyyy-MM-dd HH:mm:ss")
        results.dateTaken = formatDate(date: image.dateTaken, format:"yyyy-MM-dd HH:mm:ss")
        results.imageUrl = imageService.getImageUrl(image.imageIdentifier)
        results.tileUrlPattern = "${imageService.getImageTilesRootUrl(image.imageIdentifier)}/{z}/{x}/{y}.png"
        results.mmPerPixel = image.mmPerPixel ?: ''
        results.description = image.description ?: ''
        results.title = image.title ?: ''
        results.creator = image.creator ?: ''
        results.license = image.license ?: ''
        results.dataResourceUid = image.dataResourceUid ?: ''

        collectoryService.addMetadataForResource(results)

        if (includeTags) {
            results.tags = []
            def imageTags = ImageTag.findAllByImage(image)
            imageTags?.each { imageTag ->
                results.tags << imageTag.tag.path
            }
        }

        if (includeMetadata) {
            results.metadata = []
            def metaDataList = ImageMetaDataItem.findAllByImage(image)
            metaDataList?.each { md ->
                results.metadata << [key: md.name, value: md.value, source: md.source]
            }
        }

    }

    def imagePopupInfo() {
        def results = [success:false]

        def image = Image.findByImageIdentifier(params.id as String)
        if (image) {
            results.success = true
            results.data = [:]
            addImageInfoToMap(image, results.data, false, false)
            results.link = createLink(controller: "image", action:'details', id: image.id)
            results.linkText = "Image details..."
            results.title = "Image properties"
        }

        renderResults(results)
    }

    private renderResults(Object results, int responseCode = 200) {

        withFormat {
            json {
                def jsonStr = results as JSON
                if (params.callback) {
                    render("${params.callback}(${jsonStr})")
                } else {
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

    def getRepositoryStatistics() {
        def results = [:]
        results.imageCount = Image.count()
        results.sizeOnDisk =

        renderResults(results)
    }

    def getRepositorySizeOnDisk() {
        def results = [ repoSizeOnDisk : ImageUtils.formatFileSize(imageStoreService.getRepositorySizeOnDisk()) ]
        renderResults(results)
    }

    def getBackgroundQueueStats() {
        def results = [:]
        results.queueLength = imageService.getImageTaskQueueLength()
        results.tilingQueueLength = imageService.getTilingTaskQueueLength()
        renderResults(results)
    }

    def createSubimage() {
        def image = Image.findByImageIdentifier(params.id as String)
        if (!image) {
            renderResults([success:false, message:"Image not found: ${params.id}"])
            return
        }

        if (!params.x || !params.y || !params.height || !params.width) {
            renderResults([success:false, message:"Rectangle not correctly specified. Use x, y, height and width params"])
            return
        }

        def x = params.int('x')
        def y = params.int('y')
        def height = params.int('height')
        def width = params.int('width')
        def title = params.title
        def description = params.description

        if (height == 0 || width == 0) {
            renderResults([success:false, message:"Rectangle not correctly specified. Height and width cannot be zero"])
            return
        }

        def userId = getUserIdForRequest(request)
        if(!userId){
            renderResults([success:false, message:"User needs to be logged in to create subimage"])
            return
        }

        def subimage = imageService.createSubimage(image, x, y, width, height, userId, [title:title, description:description] )
        renderResults([success: subimage != null, subImageId: subimage?.imageIdentifier])
    }

    def getSubimageRectangles() {

        def image = Image.findByImageIdentifier(params.id as String)
        if (!image) {
            renderResults([success:false, message:"Image not found: ${params.id}"])
            return
        }

        def subimages = Subimage.findAllByParentImage(image)
        def results = [success: true, subimages: []]
        subimages.each { subImageRect ->
            def sub = subImageRect.subimage
            results.subimages << [imageId: sub.imageIdentifier, x: subImageRect.x, y: subImageRect.y, height: subImageRect.height, width: subImageRect.width]
        }
        renderResults(results)
    }

    def addUserMetadataToImage() {
        def image = Image.findByImageIdentifier(params.id as String)
        if (!image) {
            renderResults([success:false, message:"Image not found: ${params.id}"])
            return
        }

        def key = params.key
        if (!key) {
            renderResults([success:false, message:"Metadata key/name not supplied!"])
            return
        }
        def value = params.value
        if (!value) {
            renderResults([success:false, message:"Metadata value not supplied!"])
            return
        }

        def userId = getUserIdForRequest(request)

        def success = imageService.setMetaDataItem(image, MetaDataSourceType.UserDefined, key, value, userId)
        imageService.scheduleImageIndex(image.id)

        renderResults([success:success])
    }

    private getUserIdForRequest(HttpServletRequest request) {
        // First check the CAS filter cookie thing
        def userId = AuthenticationUtils.getUserId(request)
        // If not found (i.e. urls not mapped), look for standard ALA auth header
        if (!userId) {
            //TODO check the app is authorised
            userId = request.getHeader("X-ALA-userId")
        }

        // If still cannot be found look for it as a parameter
        if (!userId) {
            userId = params.userId
        }

        return userId
    }

    def bulkAddUserMetadataToImage(String id) {
        def image = Image.findByImageIdentifier(id)
        def userId = getUserIdForRequest(request)
        if (!image) {
            renderResults([success:false, message:"Image not found: ${params.id}"])
            return
        }

        def metadata = request.getJSON() as Map<String, String>
        def results = imageService.setMetadataItems(image, metadata, MetaDataSourceType.UserDefined, userId)
        imageService.scheduleImageIndex(image.id)

        renderResults([success:results != null])
    }

    def removeUserMetadataFromImage() {
        def image = Image.findByImageIdentifier(params.id as String)
        if (!image) {
            renderResults([success:false, message:"Image not found: ${params.id}"])
            return
        }

        def key = params.key
        if (!key) {
            renderResults([success:false, message:"Metadata key/name not supplied!"])
            return
        }
        def userId = getUserIdForRequest(request)
        def success = imageService.removeMetaDataItem(image, key, MetaDataSourceType.UserDefined, userId)

        renderResults([success: success])
    }

    def getMetadataKeys() {

        def source = params.source as MetaDataSourceType
        def results
        def c = ImageMetaDataItem.createCriteria()

        if (source) {
            results = c.list {
                eq("source", source)
                projections {
                    distinct("name")
                }
            }

        } else {
            results = c.list {
                projections {
                    distinct("name")
                }
            }
        }

        renderResults(results?.sort { it?.toLowerCase() })
    }

    def getImageLinksForMetaDataValues() {

        def key = params.key as String
        if (!key) {
            render([success:false, message:'No key parameter supplied'])
            return
        }

        def query = (params.q ?: params.value) as String

        if (!query) {
            render([success:false, message:'No q or value parameter supplied'])
            return
        }

        def images = searchService.findImagesByMetadata(key, [query], params)
        def results = [images:[], success: true, count: images.totalCount]

        def keyValues = imageService.getMetadataItemValuesForImages(images, key)

        images.each { image ->
            def info =  imageService.getImageInfoMap(image)
            info[key] = keyValues[image.id]
            results.images << info
        }

        renderResults(results)
    }

    def getImageInfoForIdList() {
        def query = request.JSON

        if (query) {

            List<String> imageIds = (query.imageIds as List)?.collect { it as String }

            if (!imageIds) {
                renderResults([success:false, message:'You must supply a list of image IDs (imageIds) to search for!'])
                return
            }

            def results =  [:]
            def errors = []

            imageIds.each { imageId ->

                def image = Image.findByImageIdentifier(imageId)

                if (image) {
                    def map = imageService.getImageInfoMap(image)
                    results[imageId] = map
                } else {
                    errors << imageId
                }

            }

            renderResults([success: true, results: results, invalidImageIds: errors])
            return
        }

        renderResults([success:false, message:'POST with content type "application/JSON" required.'])

    }

    def findImagesByOriginalFilename() {
        def query = request.JSON

        if (query) {

            def filenames = query.filenames as List<String>

            if (!filenames) {
                renderResults([success:false, message:'You must supply a list of filenames to search for!'])
                return
            }

            def results =  [:]

            filenames.each { filename ->

                def images = searchService.findImagesByOriginalFilename(filename, params)
                def list = []
                images?.each { image ->
                    def map = imageService.getImageInfoMap(image)
                    list << map
                }
                results[filename] = [count: list.size(), images: list]
            }

            renderResults([success: true, results: results])
            return
        }

        renderResults([success:false, message:'POST with content type "application/JSON" required.'])

    }

    def findImagesByMetadata() {
        def query = request.JSON

        if (query) {

            def key = query.key as String
            def values = query.values as List<String>

            if (!key) {
                renderResults([success:false, message:'You must supply a metadata key!'])
                return
            }

            if (!values) {
                renderResults([success:false, message:'You must supply a values list!'])
                return
            }

            def images = elasticSearchService.searchByMetadata(key, values, params)
            def results = [:]
            def keyValues = imageService.getMetadataItemValuesForImages(images.list, key)
            images?.list?.each { image ->
                def map = imageService.getImageInfoMap(image)
                collectoryService.addMetadataForResource(image)
                def keyValue = keyValues[image.id]
                def list = results[keyValue]
                if (!list) {
                    list = []
                    results[keyValue] = list
                }
                list << map
            }

            renderResults([success: true, images: results, count:images?.totalCount ?: 0])
            return
        }

        renderResults([success:false, message:'POST with content type "application/JSON" required.'])
    }

    def getNextTileJob() {
        def results = imageService.createNextTileJob()
        renderResults(results)
    }

    def cancelTileJob() {

        def userId = AuthenticationUtils.getUserId(request)
        def ticket = params.jobTicket ?: params.ticket
        if (!ticket) {
            renderResults([success:false, message:'No job ticket specified'])
            return
        }

        def job = OutsourcedJob.findByTicket(ticket)
        if (!job) {
            renderResults([success:false, message:'No such ticket or ticket expired.'])
            return
        }

        logService.log("Cancelling job (Ticket: ${job.ticket} for image ${job.image.imageIdentifier}")

        // Push the job back on the queue
        if (job.taskType == ImageTaskType.TMSTile) {
            imageService.scheduleTileGeneration(job.image.id, userId)
        }

        job.delete()
    }


    def postJobResults() {
        def ticket = params.jobTicket ?: params.ticket
        if (!ticket) {
            renderResults([success:false, message:'No job ticket specified'])
            return
        }

        def job = OutsourcedJob.findByTicket(ticket)
        if (!job) {
            renderResults([success:false, message:'No such ticket or ticket expired.'])
            return
        }

        def zoomLevels = params.int("zoomLevels")
        if (!zoomLevels) {
            renderResults([success:false, message:'No zoomLevels supplied.'])
            return
        }

        if (job.taskType == ImageTaskType.TMSTile) {
            // Expect a multipart file request
            MultipartFile file = request.getFile('tilesArchive')

            if (!file || file.size == 0) {
                renderResults([success:false, message:'tilesArchive param not present. Expected multipart file.'])
                return
            }

            if (imageStoreService.storeTilesArchiveForImage(job.image.imageIdentifier, file)) {
                job.image.zoomLevels = zoomLevels
                job.delete()
                renderResults([success: true])
                return
            } else {
                renderResults([success:false, message: "Error storing tiles for image!"])
                return
            }
        }
        renderResults([success: false, message:'Unhandled task type'])
    }

    /**
     * Update an image's metadata.
     *
     * @return
     */
    def updateMetadata(){

        Image image = Image.findByImageIdentifier(params.imageIdentifier)
        if(image){
            def userId = getUserIdForRequest(request)
            def metadata = {
                if(params.metadata){
                    JSON.parse(params.metadata as String) as Map
                } else {
                    [:]
                }
            }.call()

            //TODO - push down to service
            def imageUpdated = false
            metadata.each { kvp ->
                if(image.hasProperty(kvp.key) && kvp.value){
                    image[kvp.key] = kvp.value
                    imageUpdated = true
                }
            }
            if(imageUpdated){
                image.save()
            }

            //TODO - push down to service
            //store any other property
            metadata.each { kvp ->
                if(!image.hasProperty(kvp.key)){
                    imageService.setMetaDataItem(image, MetaDataSourceType.SystemDefined, kvp.key as String, kvp.value as String)
                }
            }

            //TODO - push down to service
            if (params.tags) {
                def tags = JSON.parse(params.tags as String) as List
                if (tags) {
                    tags.each { String tagPath ->
                        def tag = tagService.createTagByPath(tagPath)
                        tagService.attachTagToImage(image, tag, userId)
                    }
                }
            }
            response.setStatus(200)
            renderResults([success: true])
        } else {
            response.setStatus(404)
            renderResults([success: false])
        }
    }

    /**
     * Main web service for image upload.
     *
     * @return
     */
    def uploadImage() {
        // Expect a multipart file request

        Image image = null

        def userId = getUserIdForRequest(request)
        def url = params.imageUrl ?: params.url
        def metadata = {
            if(params.metadata){
                JSON.parse(params.metadata as String) as Map
            } else {
                [:]
            }
        }.call()


        if (url) {
            // Image is located at an endpoint, and we need to download it first.
            image = imageService.storeImageFromUrl(url, userId, metadata)
            if (!image) {
                renderResults([success: false, message: "Unable to retrieve image from ${url}"])
            }
        } else {
            // it should contain a file parameter
            MultipartRequest req = request as MultipartRequest
            if (req) {
                MultipartFile file = req.getFile('image')
                if (!file ) {
                    renderResults([success: false, message: 'image parameter not found. Please supply an image file.'])
                    return
                }

                if (file.size == 0) {
                    renderResults([success: false, message: 'the supplied image was empty. Please supply an image file.'])
                    return
                }

                image = imageService.storeImage(file, userId, metadata)
            } else {
                renderResults([success: false, message: "No url parameter, therefore expected multipart request!"])
            }
        }

        if (image) {

            //store any other property
            metadata.each { kvp ->
                if(!image.hasProperty(kvp.key)){
                    imageService.setMetaDataItem(image, MetaDataSourceType.SystemDefined, kvp.key as String, kvp.value as String)
                }
            }

            if (params.tags) {
                def tags = JSON.parse(params.tags as String) as List
                if (tags) {
                    tags.each { String tagPath ->
                        def tag = tagService.createTagByPath(tagPath)
                        tagService.attachTagToImage(image, tag, userId)
                    }
                }
            }

            // Callers have the option to generate thumbs immediately (although it will block).
            // And they will be regenerated later as part of general artifact generation
            // This is useful, though, if the uploader needs to link to the thumbnail straight away
            if (params.synchronousThumbnail) {
                imageService.generateImageThumbnails(image)
            }

            imageService.scheduleArtifactGeneration(image.id, userId)
            imageService.scheduleImageIndex(image.id)
            renderResults([success: true, imageId: image?.imageIdentifier])
        } else {
            renderResults([success: false, message: "Failed to store image!"])
        }
    }

    def uploadImagesFromUrls() {

        def userId = getUserIdForRequest(request)
        def body = request.JSON

        if (body) {

            List<Map<String, String>> imageList = body.images

            if (!imageList) {
                renderResults(
                        [success:false,
                         message:'You must supply a list of objects called "images", each of which' +
                                 ' must contain a "sourceUrl" key, along with optional meta data items!'],
                        HttpStatus.SC_BAD_REQUEST
                )
                return
            }

            // first create the images
            def results = imageService.batchUploadFromUrl(imageList, userId)

            imageList.each { srcImage ->
                def newImage = results[srcImage.sourceUrl ?: srcImage.imageUrl]
                if (newImage && newImage.success) {
                    imageService.setMetadataItems(newImage.image, srcImage, MetaDataSourceType.SystemDefined, userId)
                    imageService.scheduleArtifactGeneration(newImage.image.id, userId)
                    imageService.scheduleImageIndex(newImage.image.id)
                    newImage.image = null
                }
            }

            renderResults([success: true, results: results])
            return
        }

        renderResults([success:false, message:'POST with content type "application/JSON" required.'])
    }

    def calibrateImageScale() {

        def userId = getUserIdForRequest(request)
        def image = Image.findByImageIdentifier(params.imageId)
        def units = params.units ?: "mm"
        def pixelLength = params.double("pixelLength") ?: 0
        def actualLength = params.double("actualLength") ?: 0
        if (image && units && pixelLength && actualLength) {
            def pixelsPerMM = imageService.calibrateImageScale(image, pixelLength, actualLength, units, userId)
            renderResults([success: true, pixelsPerMM:pixelsPerMM, message:"Image is scaled at ${pixelsPerMM} pixels per mm"])
            return
        }
        renderResults([success:false, message:'Missing one or more required parameters: imageId, pixelLength, actualLength, units'])
    }

    def resetImageCalibration() {
        def image = Image.findByImageIdentifier(params.imageId)
        if (image) {
            imageService.resetImageLinearScale(image)
            renderResults([success: true, message:"Image linear scale has been reset"])
            return
        }
        renderResults([success:false, message:'Missing one or more required parameters: imageId, pixelLength, actualLength, units'])
    }

    def setHarvestable() {
        def userId = getUserIdForRequest(request)
        def image = Image.findByImageIdentifier(params.imageId)
        if (image) {
            imageService.setHarvestable(image, (params.value ?: params.harvest ?: "").toBoolean(), userId)
            renderResults([success: true, message:"Image harvestable now set to ${image.harvestable}", harvestable: image.harvestable])
        } else {
            renderResults([success:false, message:'Missing one or more required parameters: imageId, value'])
        }
    }

    def scheduleUploadFromUrls() {

        def userId = getUserIdForRequest(request)
        def body = request.JSON

        if (body) {
            List<Map<String, String>> imageList = body.images
            if (!imageList) {
                renderResults([success:false, message:'You must supply a list of objects called "images", each of which must contain a "sourceUrl" key, along with optional meta data items!'], HttpStatus.SC_BAD_REQUEST)
                return
            }

            def batchId = batchService.createNewBatch()
            int imageCount = 0
            imageList.each { srcImage ->
                if (!srcImage.containsKey("importBatchId")) {
                    srcImage["importBatchId"] = batchId
                }
                batchService.addTaskToBatch(batchId, new UploadFromUrlTask(srcImage, imageService, userId))
                imageCount++
            }

            renderResults([success: true, message: "${imageCount} urls scheduled for upload (batch id ${batchId}).", batchId: batchId])
            return
        }

        renderResults([success:false, message:'POST with content type "application/JSON" required.'])
    }

    def getBatchStatus() {
        def status = batchService.getBatchStatus(params.batchId)
        if (status) {
            renderResults([success:true, taskCount: status.taskCount, tasksCompleted: status.tasksCompleted, batchId: status.batchId, timeStarted: status.timeStarted.getTime(), timeFinished: status.timeFinished?.getTime() ?: 0])
            return
        }

        renderResults([success:false, message:'Missing or invalid batchId'])
    }

    def darwinCoreTerms() {
        def terms = []

        def filter = params.q ? { DarwinCoreField dwc -> dwc.name().toLowerCase().contains(params.q.toLowerCase()) } : { DarwinCoreField dwc -> true}

        DarwinCoreField.values().each {
            if (filter(it)) {
                terms.add([name: it.name(), label: it.label])
            }
        }
        renderResults(terms)
    }

    def harvest() {

        def harvestResults = imageService.getHarvestTabularData()

        if (harvestResults) {
            response.setHeader("Content-disposition", "attachment;filename=images-harvest.csv")
            response.contentType = "text/csv"

            def bos = new OutputStreamWriter(response.outputStream)

            def writer = new CSVWriter(bos, {
                for (int i = 0; i < harvestResults.columnHeaders.size(); ++i) {
                    def col = harvestResults.columnHeaders[i]
                    "${col}" {
                        it[col] ?: ""
                    }
                }
            })

            harvestResults.data.each {
                writer << it
            }

            bos.flush()
            bos.close()
        } else {
            renderResults([success:"false", message:'No harvestable images found'])
        }
    }
}