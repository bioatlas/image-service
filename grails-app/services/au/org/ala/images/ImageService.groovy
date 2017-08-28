package au.org.ala.images

import au.org.ala.images.metadata.MetadataExtractor
import au.org.ala.images.thumb.ThumbnailingResult
import au.org.ala.images.tiling.TileFormat
import com.sun.imageio.plugins.common.SubImageInputStream
import grails.transaction.NotTransactional
import grails.transaction.Transactional
import org.apache.commons.codec.binary.Base64
import org.apache.commons.imaging.Imaging
import org.apache.commons.imaging.common.ImageMetadata
import org.apache.commons.imaging.formats.jpeg.JpegImageMetadata
import org.apache.commons.imaging.formats.tiff.TiffField
import org.apache.commons.imaging.formats.tiff.constants.TiffConstants
import org.apache.commons.imaging.formats.tiff.taginfos.TagInfo
import org.apache.commons.io.FileUtils
import org.apache.commons.io.FilenameUtils
import org.apache.commons.lang.StringUtils
import org.codehaus.groovy.grails.plugins.codecs.MD5Codec
import org.codehaus.groovy.grails.plugins.codecs.SHA1Codec
import org.hibernate.FlushMode
import org.springframework.web.multipart.MultipartFile

import java.text.SimpleDateFormat
import java.util.concurrent.ConcurrentLinkedQueue

@Transactional
class ImageService {

    def imageStoreService
    def tagService
    def grailsApplication
    def logService
    def auditService
    def sessionFactory
    def imageService
    def elasticSearchService

    private static Queue<BackgroundTask> _backgroundQueue = new ConcurrentLinkedQueue<BackgroundTask>()
    private static Queue<BackgroundTask> _tilingQueue = new ConcurrentLinkedQueue<BackgroundTask>()

    private static int BACKGROUND_TASKS_BATCH_SIZE = 100

    public Image storeImage(MultipartFile imageFile, String uploader, Map metadata = [:]) {

        if (imageFile) {
            // Store the image
            def originalFilename = imageFile.originalFilename
            def bytes = imageFile?.bytes
            def image = storeImageBytes(bytes, originalFilename, imageFile.size, imageFile.contentType, uploader, metadata)
            auditService.log(image,"Image stored from multipart file ${originalFilename}", uploader ?: "<unknown>")
            return image
        }
        return null
    }

    public Image storeImageFromUrl(String imageUrl, String uploader, Map metadata = [:]) {
        if (imageUrl) {
            try {
                def url = new URL(imageUrl)
                def bytes = url.bytes
                def contentType = detectMimeTypeFromBytes(bytes, imageUrl)
                def image = storeImageBytes(bytes, imageUrl, bytes.length, contentType, uploader, metadata)
                auditService.log(image, "Image downloaded from ${imageUrl}", uploader ?: "<unknown>")
                return image
            } catch (Exception ex) {
                log.error(ex.getMessage(), e)
            }
        }
        return null
    }

    @NotTransactional
    public Map batchUploadFromUrl(List<Map<String, String>> imageSources, String uploader) {
        def results = [:]
        Image.withNewTransaction {

            sessionFactory.currentSession.setFlushMode(FlushMode.MANUAL)
            try {
                imageSources.each { imageSource ->
                    def imageUrl = (imageSource.sourceUrl ?: imageSource.imageUrl) as String
                    if (imageUrl) {
                        def result = [success: false]
                        try {
                            def url = new URL(imageUrl)
                            def bytes = url.bytes
                            def contentType = detectMimeTypeFromBytes(bytes, imageUrl)
                            def image = storeImageBytes(bytes, imageUrl, bytes.length, contentType, uploader, imageSource)
                            result.imageId = image.imageIdentifier
                            result.image = image
                            result.success = true
                            auditService.log(image, "Image (batch) downloaded from ${imageUrl}", uploader ?: "<unknown>")
                        } catch (Exception ex) {
                            ex.printStackTrace()
                            result.message = ex.message
                        }
                        results[imageUrl] = result
                    }
                }
            } finally {
                sessionFactory.currentSession.flush()
                sessionFactory.currentSession.setFlushMode(FlushMode.AUTO)
            }
        }
        return results
    }

    public int getImageTaskQueueLength() {
        return _backgroundQueue.size()
    }

    public int getTilingTaskQueueLength() {
        return _tilingQueue.size()
    }

    @NotTransactional
    Image storeImageBytes(byte[] bytes, String originalFilename, long filesize, String contentType,
                          String uploaderId, Map metadata = [:]) {

        CodeTimer ct = new CodeTimer("Store Image ${originalFilename}")

        def md5Hash = MD5Codec.encode(bytes)
        def sha1Hash = SHA1Codec.encode(bytes)

        def extension = FilenameUtils.getExtension(originalFilename) ?: 'jpg'
        def imgDesc = imageStoreService.storeImage(bytes)

        // Create the image record, and set the various attributes
        Image image = new Image(
                imageIdentifier: imgDesc.imageIdentifier,
                contentMD5Hash: md5Hash,
                contentSHA1Hash: sha1Hash,
                uploader: uploaderId)

        //add core metadata properties for this image
        metadata.each { kvp ->
            if(image.hasProperty(kvp.key) && kvp.value){
                if(!(kvp.key in ["dateTaken", "dateUploaded"])){
                    image[kvp.key] = kvp.value
                }
            }
        }

        image.fileSize = filesize
        image.mimeType = contentType
        image.dateUploaded = new Date()
        image.originalFilename = originalFilename
        image.extension = extension
        image.dateTaken = getImageTakenDate(bytes) ?: image.dateUploaded
        image.height = imgDesc.height
        image.width = imgDesc.width

        image.save(failOnError: true)

        def md = getImageMetadataFromBytes(bytes, originalFilename)
        setMetadataItems(image, md, MetaDataSourceType.Embedded, uploaderId)

        ct.stop(true)
        return image
    }

    public Map getMetadataItemValuesForImages(List<Image> images, String key, MetaDataSourceType source = MetaDataSourceType.SystemDefined) {
        if (!images || !key) {
            return [:]
        }
        def results = ImageMetaDataItem.executeQuery("select md.value, md.image.id from ImageMetaDataItem md where md.image in (:images) and lower(name) = :key and source=:source", [images: images, key: key.toLowerCase(), source: source])
        def fr = [:]
        results.each {
            fr[it[1]] = it[0]
        }
        return fr
    }

    public Map getAllUrls(String imageIdentifier) {
        return imageStoreService.getAllUrls(imageIdentifier)
    }

    public String getImageUrl(String imageIdentifier) {
        return imageStoreService.getImageUrl(imageIdentifier)
    }

    public String getImageThumbUrl(String imageIdentifier) {
        return imageStoreService.getImageThumbUrl(imageIdentifier)
    }

    public String getImageThumbLargeUrl(String imageIdentifier) {
        return imageStoreService.getImageThumbLargeUrl(imageIdentifier)
    }

    public String getImageSquareThumbUrl(String imageIdentifier, String backgroundColor = null) {
        return imageStoreService.getImageSquareThumbUrl(imageIdentifier, backgroundColor)
    }

    public List<String> getAllThumbnailUrls(String imageIdentifier) {
        def results = []

        def image = Image.findByImageIdentifier(imageIdentifier)
        if (image) {
            def thumbs = ImageThumbnail.findAllByImage(image)
            thumbs?.each { thumb ->
                results << imageStoreService.getThumbUrlByName(imageIdentifier, thumb.name)
            }
        }

        return results
    }

    public String getImageTilesRootUrl(String imageIdentifier) {
        return imageStoreService.getImageTilesRootUrl(imageIdentifier)
    }

    private static Date getImageTakenDate(byte[] bytes) {
        try {
            ImageMetadata metadata = Imaging.getMetadata(bytes)
            if (metadata && metadata instanceof JpegImageMetadata) {
                JpegImageMetadata jpegMetadata = metadata

                def date = getImageTagValue(jpegMetadata,TiffConstants.EXIF_TAG_DATE_TIME_ORIGINAL)
                if (date) {
                    def sdf = new SimpleDateFormat("yyyy:MM:dd hh:mm:ss")
                    return sdf.parse(date.toString())
                }
            }
        } catch (Exception ex) {
            return null
        }
    }

    private static Object getImageTagValue(JpegImageMetadata jpegMetadata, TagInfo tagInfo) {
        TiffField field = jpegMetadata.findEXIFValue(tagInfo);
        if (field) {
            return field.value
        }
    }

    private static Map<String, Object> getImageMetadataFromBytes(byte[] bytes, String filename) {
        def extractor = new MetadataExtractor()
        return extractor.readMetadata(bytes, filename)
    }

    def scheduleArtifactGeneration(long imageId, String userId) {
        scheduleBackgroundTask(new ImageBackgroundTask(imageId, this, ImageTaskType.Thumbnail, userId))
        _tilingQueue.add(new ImageBackgroundTask(imageId, this, ImageTaskType.TMSTile, userId))
    }

    def scheduleThumbnailGeneration(long imageId, String userId) {
        scheduleBackgroundTask(new ImageBackgroundTask(imageId, this, ImageTaskType.Thumbnail, userId))
    }

    def scheduleTileGeneration(long imageId, String userId) {
        _tilingQueue.add(new ImageBackgroundTask(imageId, this, ImageTaskType.TMSTile, userId))
    }

    def scheduleKeywordRebuild(long imageId, String userId) {
        scheduleBackgroundTask(new ImageBackgroundTask(imageId, this, ImageTaskType.KeywordRebuild, userId))
    }

    def scheduleImageDeletion(long imageId, String userId) {
        scheduleBackgroundTask(new ImageBackgroundTask(imageId, this, ImageTaskType.Delete, userId))
    }

    def scheduleImageIndex(long imageId) {
        scheduleBackgroundTask(new IndexImageBackgroundTask(imageId, elasticSearchService))
    }

    def scheduleBackgroundTask(BackgroundTask task) {
        _backgroundQueue.add(task)
    }

    def schedulePollInbox(String userId) {
        def task = new PollInboxBackgroundTask(this, userId)
        scheduleBackgroundTask(task)
        return task.batchId
    }

    public void processBackgroundTasks() {
        int taskCount = 0
        BackgroundTask task = null

        while (taskCount < BACKGROUND_TASKS_BATCH_SIZE && (task = _backgroundQueue.poll()) != null) {
            if (task) {
                task.execute()
                taskCount++
            }
        }
    }

    public void processTileBackgroundTasks() {
        int taskCount = 0
        BackgroundTask task = null
        while (taskCount < BACKGROUND_TASKS_BATCH_SIZE && (task = _tilingQueue.poll()) != null) {
            if (task) {
                task.execute()
                taskCount++
            }
        }
    }

    public boolean isImageType(Image image) {
        return image.mimeType?.toLowerCase()?.startsWith("image/");
    }

    public boolean isAudioType(Image image) {
        return image.mimeType?.toLowerCase()?.startsWith("audio/");
    }

    public List<ThumbnailingResult> generateImageThumbnails(Image image) {
        List<ThumbnailingResult> results
        if (isAudioType(image)) {
            results = imageStoreService.generateAudioThumbnails(image.imageIdentifier)
        } else {
            results = imageStoreService.generateImageThumbnails(image.imageIdentifier)
        }

        // These are deprecated, but we'll update them anyway...
        if (results) {
            def defThumb = results.find { it.thumbnailName.equalsIgnoreCase("thumbnail")}
            image.thumbWidth = defThumb?.width ?: 0
            image.thumbHeight = defThumb?.height ?: 0
            image.squareThumbSize = results.find({ it.thumbnailName.equalsIgnoreCase("thumbnail_square")})?.width ?: 0
        }
        results?.each { th ->
            def imageThumb = ImageThumbnail.findByImageAndName(image, th.thumbnailName)
            if (imageThumb) {
                imageThumb.height = th.height
                imageThumb.width = th.width
                imageThumb.isSquare = th.square
            } else {
                imageThumb = new ImageThumbnail(image: image, name: th.thumbnailName, height: th.height, width: th.width, isSquare: th.square)
                imageThumb.save()
            }
        }
    }

    void generateTMSTiles(String imageIdentifier) {
        imageStoreService.generateTMSTiles(imageIdentifier)
    }

    def deleteImage(Image image, String userId) {

        if (image) {

            // need to delete it from user selections
            def selected = SelectedImage.findAllByImage(image)
            selected.each { selectedImage ->
                selectedImage.delete()
            }

            // Need to delete tags
            def tags = ImageTag.findAllByImage(image)
            tags.each { tag ->
                tag.delete()
            }

            // Delete keywords
            def keywords = ImageKeyword.findAllByImage(image)
            keywords.each { keyword ->
                keyword.delete()
            }

            // If this image is a subimage, also need to delete any subimage rectangle records
            def subimagesRef = Subimage.findAllBySubimage(image)
            subimagesRef.each { subimage ->
                subimage.delete()
            }

            // This image may also be a parent image
            def subimages = Subimage.findAllByParentImage(image)
            subimages.each { subimage ->
                // need to detach this image from the child images, but we do not actually delete the sub images. They
                // will live on as root images in their own right
                subimage.subimage.parent = null
                subimage.delete()
            }

            // and delete album images
            def albumImages = AlbumImage.findAllByImage(image)
            albumImages.each { albumImage ->
                albumImage.delete()
            }

            // thumbnail records...
            def thumbs = ImageThumbnail.findAllByImage(image)
            thumbs.each { thumb ->
                thumb.delete()
            }

            // Delete from the index...
            elasticSearchService.deleteImage(image)

            // and delete domain object
            image.delete(flush: true, failonerror: true)

            // Finally need to delete images on disk.
            // This might fail (if the file is held open somewhere), but that's ok, we can clean up later.
            imageStoreService.deleteImage(image?.imageIdentifier)

            auditService.log(image?.imageIdentifier, "Image deleted", userId)

            return true
        }

        return false
    }

    List<File> listStagedImages() {
        def files = []
        def inboxLocation = grailsApplication.config.imageservice.imagestore.inbox as String
        def inboxDirectory = new File(inboxLocation)
        inboxDirectory.eachFile { File file ->
            files << file
        }
        return files
    }

    Image importFileFromInbox(File file, String batchId, String userId) {

        CodeTimer ct = new CodeTimer("Import file ${file?.absolutePath}")

        if (!file || !file.exists()) {
            throw new RuntimeException("Could not read file ${file?.absolutePath} - Does not exist")
        }

        Image image = null

        def fieldDefinitions = ImportFieldDefinition.list()

        Image.withNewTransaction {

            // Create the image domain object
            def bytes = file.getBytes()
            def mimeType = detectMimeTypeFromBytes(bytes, file.name)
            image = storeImageBytes(bytes, file.name, file.length(),mimeType, userId)

            auditService.log(image, "Imported from ${file.absolutePath}", userId)

            if (image && batchId) {
                setMetaDataItem(image, MetaDataSourceType.SystemDefined,  "importBatchId", batchId)
            }

            // Is there any extra data to be applied to this image?
            if (fieldDefinitions) {
                fieldDefinitions.each { fieldDef ->
                    setMetaDataItem(image, MetaDataSourceType.SystemDefined, fieldDef.fieldName, ImportFieldValueExtractor.extractValue(fieldDef, file))
                }
            }
            generateImageThumbnails(image)

            image.save(flush: true, failOnError: true)
        }

        // If we get here, and the image is not null, it means it has been committed to the database and we can remove the file from the inbox
        if (image) {
            if (!FileUtils.deleteQuietly(file)) {
                file.deleteOnExit()
            }
            // also we should do the thumb generation (we'll defer tiles until after the load, as it will slow everything down)
            scheduleTileGeneration(image.id, userId)
        }
        return image
    }

    def pollInbox(String batchId, String userId) {
        def inboxLocation = grailsApplication.config.imageservice.imagestore.inbox as String
        def inboxDirectory = new File(inboxLocation)

        inboxDirectory.eachFile { File file ->
            _backgroundQueue.add(new ImportFileBackgroundTask(file, this, batchId, userId))
        }

    }

    private static String sanitizeString(Object value) {
        if (value) {
            value = value.toString()
        } else {
            return ""
        }

        def bytes = value?.getBytes("utf8")

        def hasZeros = bytes.contains(0)
        if (hasZeros) {
            return Base64.encodeBase64String(bytes)
        }
        return value
    }

    def setMetaDataItem(Image image, MetaDataSourceType source, String key, String value, String userId = "<unknown") {

        value = sanitizeString(value)

        if (image && StringUtils.isNotEmpty(key?.trim()) && StringUtils.isNotEmpty(value?.trim())) {

            if (value.length() > 8000) {
                auditService.log(image, "Cannot set metdata item '${key}' because value is too big! First 25 bytes=${value.take(25)}", userId)
                return false
            }
            
            // See if we already have an existing item...
            def existing = ImageMetaDataItem.findByImageAndNameAndSource(image, key, source)
            if (existing) {
                existing.value = value
            } else {
                def md = new ImageMetaDataItem(image: image, name: key, value: value, source: source)
                md.save()
                image.addToMetadata(md)
            }

            auditService.log(image, "Metadata item ${key} set to '${value?.take(25)}' (truncated) (${source})", userId)
            image.save()
            return true
        } else {
            logService.debug("Not Setting metadata item! Image ${image?.id} key: ${key} value: ${value}")
        }

        return false
    }

    def setMetadataItemsByImageId(Long imageId, Map<String, String> metadata, MetaDataSourceType source, String userId) {
        def image = Image.get(imageId)
        if (image) {
            return setMetadataItems(image, metadata, source, userId)
        }
        return false
    }

    def setMetadataItems(Image image, Map<String, Object> metadata, MetaDataSourceType source, String userId) {
        if (!userId) {
            userId = "<unknown>"
        }
        metadata.each { kvp ->
            def value = sanitizeString(kvp.value?.toString())
            def key = kvp.key
            if (image && StringUtils.isNotEmpty(key?.trim()) && StringUtils.isNotEmpty(value?.trim())) {

                if (value.length() > 8000) {
                    auditService.log(image, "Cannot set metdata item '${key}' because value is too big! First 25 bytes=${value.take(25)}", userId)
                    return false
                }

                // See if we already have an existing item...
                def existing = ImageMetaDataItem.findByImageAndNameAndSource(image, key, source)
                if (existing) {
                    existing.value = value
                } else {
                    def md = new ImageMetaDataItem(image: image, name: key, value: value, source: source)
                    md.save()
                    image.addToMetadata(md)
                }

                auditService.log(image, "Metadata item ${key} set to '${value?.take(25)}' (truncated) (${source})", userId)
            } else {
                logService.debug("Not Setting metadata item! Image ${image?.id} key: ${key} value: ${value}")
            }
        }
        image.save()
        scheduleImageIndex(image.id)
        return true
    }

    def removeMetaDataItem(Image image, String key, MetaDataSourceType source, String userId="<unknown>") {
        def count = 0
        def items = ImageMetaDataItem.findAllByImageAndNameAndSource(image, key, source)
        if (items) {
            items.each { md ->
                count++
                md.delete()
            }
            scheduleImageIndex(image.id)
        }
        auditService.log(image, "Delete metadata item ${key} (${count} items)", userId)
        return count > 0
    }

    static String detectMimeTypeFromBytes(byte[] bytes, String filename) {
        return new MetadataExtractor().detectContentType(bytes, filename);
    }

    public Image createSubimage(Image parentImage, int x, int y, int width, int height, String userId, Map metadata = [:]) {

        if (x < 0) {
            x = 0;
        }
        if (y < 0) {
            y = 0;
        }

        def results = imageStoreService.retrieveImageRectangle(parentImage.imageIdentifier, x, y, width, height)
        if (results.bytes) {
            int subimageIndex = Subimage.countByParentImage(parentImage) + 1
            def filename = "${parentImage.originalFilename}_subimage_${subimageIndex}"
            def subimage = storeImageBytes(results.bytes,filename, results.bytes.length, results.contentType, userId, metadata)

            def subimageRect = new Subimage(parentImage: parentImage, subimage: subimage, x: x, y: y, height: height, width: width)
            subimageRect.save()
            subimage.parent = parentImage

            auditService.log(parentImage, "Subimage created ${subimage.imageIdentifier}", userId)
            auditService.log(subimage, "Subimage created from parent image ${parentImage.imageIdentifier}", userId)

            scheduleArtifactGeneration(subimage.id, userId)
            scheduleImageIndex(subimage.id)

            return subimage
        }
    }

    def Map getImageInfoMap(Image image) {
        def map = [
                imageId: image.imageIdentifier,
                height: image.height,
                width: image.width,
                tileZoomLevels: image.zoomLevels,
                thumbHeight: image.thumbHeight,
                thumbWidth: image.thumbWidth,
                filesize: image.fileSize,
                mimetype: image.mimeType,
                creator: image.creator,
                title: image.title,
                description: image.description,
                rights: image.rights,
                rightsHolder: image.rightsHolder,
                license: image.license
        ]
        def urls = getAllUrls(image.imageIdentifier)
        urls.each { kvp ->
            map[kvp.key] = kvp.value
        }
        return map
    }

    def createNextTileJob() {
        def task = _tilingQueue.poll() as ImageBackgroundTask
        if (task == null) {
            return [success:false, message:"No tiling jobs available at this time."]
        } else {
            if (task) {
                def image = Image.get(task.imageId)
                // Create a new pending job
                def ticket = UUID.randomUUID().toString()
                def job = new OutsourcedJob(image: image, taskType: ImageTaskType.TMSTile, expectedDurationInMinutes: 15, ticket: ticket)
                job.save()
                return [success: true, imageId: image.imageIdentifier, jobTicket: ticket, tileFormat: TileFormat.JPEG]
            } else {
                return [success:false, message: "Internal error!"]
            }
        }
    }

    def createNextThumbnailJob() {

        ImageBackgroundTask task = _backgroundQueue.find { bgt ->
            def imageTask = bgt as ImageBackgroundTask
            if (imageTask != null) {
                if (imageTask.operation == ImageTaskType.Thumbnail) {
                    if (_backgroundQueue.remove(imageTask)) {
                        return true
                    }
                }
            }
            return false
        }

        if (task == null) {
            return [success: false, message:'No thumbnail job available at this time.']
        } else {
            if (task) {
                def image = Image.get(task.imageId)
                // Create a new pending job
                def ticket = UUID.randomUUID().toString()
                def job = new OutsourcedJob(image: image, taskType: ImageTaskType.Thumbnail, expectedDurationInMinutes: 15, ticket: ticket)
                job.save()
                return [success: true, imageId: image.imageIdentifier, jobTicket: ticket]
            } else {
                return [success:false, message: "Internal error!"]
            }
        }
    }

    def resetImageLinearScale(Image image) {
        image.mmPerPixel = null;
        image.save()
        scheduleImageIndex(image.id)
    }

    def calibrateImageScale(Image image, double pixelLength, double actualLength, String units, String userId) {

        double scale = 1.0
        switch (units) {
            case "inches":
                scale = 25.4
                break;
            case "metres":
                scale = 1000
                break;
            case "feet":
                scale = 304.8
                break;
            default: // unrecognized units, or mm
                break;
        }

        def mmPerPixel = (actualLength * scale) / pixelLength

        image.mmPerPixel = mmPerPixel
        image.save()
        scheduleImageIndex(image.id)

        return mmPerPixel
    }

    def setHarvestable(Image image, Boolean harvestable, String userId) {
        image.setHarvestable(harvestable)
        image.save()
        scheduleImageIndex(image.id)
        auditService.log(image, "Harvestable set to ${harvestable}", userId)
    }

    /**
     *
     * @param maxRows
     * @param offset
     * @return a map with two keys - 'data' a list of maps containing the harvestable data, and 'columnHeadings', a list of strings with the distinct list of columns
     */
    def getHarvestTabularData(int maxRows = -1, int offset = 0) {

        def params = [:]
        if (maxRows > 0) {
            params.max = maxRows
        }

        if (offset > 0) {
            params.offset = offset
        }

        def images = Image.findAllByHarvestable(true)
        if (!images) {
            return [columnHeaders: ["imageUrl", "occurrenceId"], data: []]
        }

        def c = ImageMetaDataItem.createCriteria()
        // retrieve just the relevant metadata rows
        def metaDataRows = c.list {
            inList("image", images)
            or {
                eq("source", MetaDataSourceType.SystemDefined)
                eq("source", MetaDataSourceType.UserDefined)
            }
        }

        def metaDataMappedbyImage = metaDataRows.groupBy {
            it.image.id
        }

        def columnHeaders = ['imageUrl', 'occurrenceId']

        def tabularData = []

        images.each { image ->
            def map =  [occurrenceId: image.imageIdentifier, 'imageUrl': imageService.getImageUrl(image.imageIdentifier)]
            def imageMetadata = metaDataMappedbyImage[image.id]
            imageMetadata.each { md ->
                if (md.value) {
                    map[md.name] = md.value
                    if (!columnHeaders.contains(md.name)) {
                        columnHeaders << md.name
                    }
                }
            }
            tabularData << map
        }

        return [data: tabularData, columnHeaders: columnHeaders]
    }

    def deleteIndex() {
        elasticSearchService.reinitialiseIndex()
    }

    /**
     * Retrieve image via numeric ID or guid.
     * @param params
     * @return
     */
    def getImageFromParams(params) {
        def image = Image.findById(params.int("id"))
        if (!image) {
            String guid = params.id // maybe the id is a guid?
            if (!guid) {
                guid = params.imageId
            }

            image = Image.findByImageIdentifier(guid)
        }
        return image
    }
}
