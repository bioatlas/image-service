package au.org.ala.images

import au.org.ala.images.thumb.ImageThumbnailer
import au.org.ala.images.thumb.ThumbDefinition
import au.org.ala.images.thumb.ThumbnailingResult
import au.org.ala.images.tiling.ImageTiler
import au.org.ala.images.tiling.ImageTilerConfig
import au.org.ala.images.tiling.ImageTilerResults
import au.org.ala.images.tiling.TileFormat
import au.org.ala.images.util.ImageReaderUtils
import grails.transaction.Transactional
import org.apache.commons.io.FileUtils
import org.apache.commons.io.IOUtils
import org.springframework.web.multipart.MultipartFile
import javax.imageio.ImageIO
import javax.imageio.ImageReadParam
import java.awt.Color
import java.awt.Rectangle
import java.awt.image.BufferedImage
import grails.web.context.ServletContextHolder

class ImageStoreService {

    def grailsApplication
    def logService
    def auditService

    ImageDescriptor storeImage(byte[] imageBytes) {
        def uuid = UUID.randomUUID().toString()
        def imgDesc = new ImageDescriptor(imageIdentifier: uuid)
        String path = createOriginalPathFromUUID(uuid)

        File f = new File(path)
        f.parentFile.mkdirs()
        FileUtils.writeByteArrayToFile(f, imageBytes)
        def reader = ImageReaderUtils.findCompatibleImageReader(imageBytes)
        if (reader) {
            imgDesc.height = reader.getHeight(0)
            imgDesc.width = reader.getWidth(0)
            reader.dispose()
        }
        return imgDesc
    }

    File getImageDirectory(String uuid) {
        def l = [grailsApplication.config.imageservice.imagestore.root]
        computeAndAppendLocalDirectoryPath(uuid, l)
        return new File(l.join("/"))
    }

    String createOriginalPathFromUUID(String uuid) {
        def l = [grailsApplication.config.imageservice.imagestore.root]
        computeAndAppendLocalDirectoryPath(uuid, l)
        l << "original"
        return l.join('/')
    }

    String createTilesPathFromUUID(String uuid) {
        def l = [grailsApplication.config.imageservice.imagestore.root]
        computeAndAppendLocalDirectoryPath(uuid, l)
        l << "tms"
        return l.join('/')
    }

    File getOriginalImageFile(String imageIdentifier) {
        def path = createOriginalPathFromUUID(imageIdentifier)
        return new File(path)
    }

    private static void computeAndAppendLocalDirectoryPath(String uuid, List bits) {
        for (int i = 1; i <= 4; ++i) {
            bits << uuid.charAt(uuid.length() - i);
        }
        bits << uuid // each image gets it's own directory
    }

    byte[] retrieveImage(String imageIdentifier) {
        if (imageIdentifier) {
            def imageFile = getOriginalImageFile(imageIdentifier)
            return imageFile.getBytes()
        }
        return null
    }

    Map retrieveImageRectangle(String imageIdentifier, int x, int y, int width, int height) {

        def results = [bytes: null, contentType: ""]

        if (imageIdentifier) {
            def imageFile = getOriginalImageFile(imageIdentifier)
            def imageBytes = FileUtils.readFileToByteArray(imageFile);
            def reader = ImageReaderUtils.findCompatibleImageReader(imageBytes);
            if (reader) {
                try {
                    Rectangle stripRect = new Rectangle(x, y, width, height);
                    ImageReadParam params = reader.getDefaultReadParam();
                    params.setSourceRegion(stripRect);
                    params.setSourceSubsampling(1, 1, 0, 0);
                    // This may fail if there is not enough heap!
                    BufferedImage subimage = reader.read(0, params);
                    def bos = new ByteArrayOutputStream()
                    if (!ImageIO.write(subimage, "PNG", bos)) {
                        logService.debug("Could not create subimage in PNG format. Giving up")
                        return null
                    } else {
                        results.contentType = "image/png"
                    }
                    results.bytes = bos.toByteArray()
                    bos.close()
                } finally {
                    reader.dispose()
                }
            } else {
                throw new RuntimeException("No appropriate reader for image type!");
            }
        }

        return results
    }

    Map getAllUrls(String imageIdentifier) {
        def root = grailsApplication.config.imageservice.apache.root
        def results = [:]
        def path = []
        computeAndAppendLocalDirectoryPath(imageIdentifier, path)

        results.imageUrl = root + (path + "original").join("/")
        results.thumbUrl = root + (path + "thumbnail").join("/")
        results.largeThumbUrl = root + (path + "thumbnail_large").join("/")
        results.squareThumbUrl = root + (path + "thumbnail_square").join("/")
        results.tilesUrlPattern = root + (path + "tms").join("/") + "/{z}/{x}/{y}.png"

        return results
    }

    String getImageUrl(String imageIdentifier) {
        def path = []
        computeAndAppendLocalDirectoryPath(imageIdentifier, path)
        path << "original"
        return grailsApplication.config.imageservice.apache.root + path.join("/")
    }

    String getImageThumbUrl(String imageIdentifier) {
        def path = []
        computeAndAppendLocalDirectoryPath(imageIdentifier, path)
        path << "thumbnail"
        return grailsApplication.config.imageservice.apache.root + path.join("/")
    }

    String getImageThumbFile(String imageIdentifier) {
        def path = []
        computeAndAppendLocalDirectoryPath(imageIdentifier, path)
        path << "thumbnail"
        return grailsApplication.config.imageservice.imagestore.root + "/" + path.join("/")
    }

    String getImageThumbLargeFile(String imageIdentifier) {
        def path = []
        computeAndAppendLocalDirectoryPath(imageIdentifier, path)
        path << "thumbnail_large"
        return grailsApplication.config.imageservice.imagestore.root + "/" + path.join("/")
    }

    String getImageOriginalFile(String imageIdentifier) {
        def path = []
        computeAndAppendLocalDirectoryPath(imageIdentifier, path)
        path << "original"
        return grailsApplication.config.imageservice.imagestore.root + "/" + path.join("/")
    }

    String getImageThumbUrl(String imageIdentifier,  Integer idx) {
        def path = []
        computeAndAppendLocalDirectoryPath(imageIdentifier, path)
        path << "thumbnail"
        def roots = grailsApplication.config.imageservice.apache.multiple_roots.split(' ')
        def root = roots[idx % (roots.length)]
        return root + path.join("/")
    }

    String getImageThumbLargeUrl(String imageIdentifier) {
        def path = []
        computeAndAppendLocalDirectoryPath(imageIdentifier, path)
        path << "thumbnail_large"
        return grailsApplication.config.imageservice.apache.root + path.join("/")
    }

    String getThumbUrlByName(String imageIdentifier, String name) {
        def path = []
        computeAndAppendLocalDirectoryPath(imageIdentifier, path)
        path << name
        return grailsApplication.config.imageservice.apache.root + path.join("/")
    }

    String getImageSquareThumbUrl(String imageIdentifier, String backgroundColor) {
        def path = []
        computeAndAppendLocalDirectoryPath(imageIdentifier, path)
        if (backgroundColor) {
            path << "thumbnail_square_${backgroundColor}"
        } else {
            path << "thumbnail_square"
        }
        return grailsApplication.config.imageservice.apache.root + path.join("/")
    }

    String getImageTilesRootUrl(String imageIdentifier) {
        def path = []
        computeAndAppendLocalDirectoryPath(imageIdentifier, path)
        path << "tms"
        return grailsApplication.config.imageservice.apache.root + path.join("/")
    }

    List<ThumbnailingResult> generateAudioThumbnails(String imageIdentifier) {
        URL u = new URL(grailsApplication.config.placeholder.sound.thumbnail)
        def imageBytes = u.getBytes()
        if (imageBytes) {
            return generateThumbnailsImpl(imageBytes, imageIdentifier)
        }
        return null
    }

    List<ThumbnailingResult> generateDocumentThumbnails(String imageIdentifier) {
        URL u = new URL(grailsApplication.config.placeholder.document.thumbnail)
        def imageBytes = u.getBytes()
        if (imageBytes) {
            return generateThumbnailsImpl(imageBytes, imageIdentifier)
        }
        return null
    }

    /**
     * Create a number of thumbnail artifacts for an image, one that preserves the aspect ratio of the original image, another drawing a scale image on a transparent
     * square with a constrained maximum dimension of config item "imageservice.thumbnail.size", and a series of square jpeg thumbs with different coloured backgrounds
     * (jpeg thumbs are much smaller, and load much faster than PNG).
     *
     * The first thumbnail (preserved aspect ratio) is of type JPG to conserve disk space, whilst the square thumb is PNG as JPG does not support alpha transparency
     * @param imageIdentifier The id of the image to thumb
     */
    List<ThumbnailingResult> generateImageThumbnails(String imageIdentifier) {
        def imageFile = getOriginalImageFile(imageIdentifier)
        def imageBytes = FileUtils.readFileToByteArray(imageFile)
        return generateThumbnailsImpl(imageBytes, imageIdentifier)
    }

    private List<ThumbnailingResult> generateThumbnailsImpl(byte[] imageBytes, String imageIdentifier) {
        def t = new ImageThumbnailer()
        def destinationDirectory = getImageDirectory(imageIdentifier)
        int size = grailsApplication.config.imageservice.thumbnail.size as Integer
        def thumbDefs = [
            new ThumbDefinition(size, false, null, "thumbnail"),
            new ThumbDefinition(size, true, null, "thumbnail_square"),
            new ThumbDefinition(size, true, Color.black, "thumbnail_square_black"),
            new ThumbDefinition(size, true, Color.white, "thumbnail_square_white"),
            new ThumbDefinition(size, true, Color.darkGray, "thumbnail_square_darkGray"),
            new ThumbDefinition(650, false, null, "thumbnail_large"),
        ]
        def results = t.generateThumbnails(imageBytes, destinationDirectory, thumbDefs as List<ThumbDefinition>)
        auditService.log(imageIdentifier, "Thumbnails created", "N/A")
        return results
    }

    void generateTMSTiles(String imageIdentifier) {
        logService.log("Generating TMS compatible tiles for image ${imageIdentifier}")
        def ct = new CodeTimer("Tiling image ${imageIdentifier}")
        def imageFile = getOriginalImageFile(imageIdentifier)

        // Calculate where the tiles will live on the file system
        def bits = [grailsApplication.config.imageservice.imagestore.root]
        computeAndAppendLocalDirectoryPath(imageIdentifier, bits)
        bits << "tms"
        def tileRootPath = bits.join("/")

        def rootFile = new File(tileRootPath)

        def results = tileImage(imageFile, rootFile)
        if (results.success) {
            def image = Image.findByImageIdentifier(imageIdentifier)
            if (image) {
                image.zoomLevels = results.zoomLevels
                image.save(flush: true, failOnError: true)
            } else {
                logService.log("Image not found in database! ${imageIdentifier}")
            }
        } else {
            logService.log("Image tiling failed! ${results}");
        }
        auditService.log(imageIdentifier, "TMS tiles generated", "N/A")
        ct.stop(true)
    }

    private static ImageTilerResults tileImage(File imageFile, File destination) {
        def config = new ImageTilerConfig(2,2,256, 6, TileFormat.JPEG)
        config.setTileBackgroundColor(new Color(221, 221, 221))
        def tiler = new ImageTiler(config)
        return tiler.tileImage(imageFile, destination)
    }

    private BufferedImage loadImage(String imageIdentifier) {
        String path = createOriginalPathFromUUID(imageIdentifier)
        File f = new File(path)
        if (f.exists()) {
            def image = ImageIO.read(f)
            return image
        } else {
            throw new RuntimeException("File not found! ${path}")
        }
    }

    boolean deleteImage(String imageIdentifier) {
        if (imageIdentifier) {
            File f = getOriginalImageFile(imageIdentifier)
            if (f && f.exists()) {
                FileUtils.deleteQuietly(f.parentFile)
                auditService.log(imageIdentifier, "Image deleted from store", "N/A")
                return true
            }
        }
        return false
    }

    boolean storeTilesArchiveForImage(String imageIdentifier, MultipartFile zipFile) {

        def original = getOriginalImageFile(imageIdentifier)
        if (original && original.exists()) {
            def parent = original.parentFile
            def stagingFile = new File(parent.absolutePath + "/tiles.zip")
            if (stagingFile.exists()) {
                stagingFile.delete()
            }

            // copy the zip file to the staging area
            stagingFile.newOutputStream() << zipFile.inputStream
            def tilesRoot = createTilesPathFromUUID(imageIdentifier)

            def ant = new groovy.util.AntBuilder()
            ant.unzip(
                    src: stagingFile.absolutePath,
                    dest: tilesRoot,
                    overwrite: true
            )
            // TODO: validate the extracted contents
            auditService.log(imageIdentifier, "Image tiles stored from zip file (outsourced job?)", "N/A")

            // Now clean up!
            FileUtils.deleteQuietly(stagingFile)
            return true
        }
        return false
    }

    long getConsumedSpaceOnDisk(String imageId) {
        def original = getOriginalImageFile(imageId)
        if (original && original.exists()) {
            return FileUtils.sizeOfDirectory(original.parentFile)
        }
        return 0
    }

    long getRepositorySizeOnDisk() {
        def dir = new File(grailsApplication.config.imageservice.imagestore.root)
        if (dir && dir.exists()) {
            return FileUtils.sizeOfDirectory(dir)
        }
        return 0
    }

}
