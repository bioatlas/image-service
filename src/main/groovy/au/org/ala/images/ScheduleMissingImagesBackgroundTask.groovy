package au.org.ala.images

import com.opencsv.CSVWriter
import org.apache.log4j.Logger
import org.apache.tomcat.util.http.fileupload.FileUtils

/**
 * Cycles through the database outputting details of images that are referenced in the database
 * but are missing on the file system
 */
class ScheduleMissingImagesBackgroundTask extends BackgroundTask {

    private ImageStoreService _imageStoreService
    String _exportDirectory
    private Logger log = Logger.getLogger(ScheduleMissingImagesBackgroundTask.class)

    ScheduleMissingImagesBackgroundTask(ImageStoreService imageStoreService, String exportDirectory) {
        _imageStoreService = imageStoreService
        _exportDirectory = exportDirectory
    }

    @Override
    void execute() {


        def exportDir = new File(_exportDirectory)
        if (!exportDir.exists()) {
            FileUtils.forceMkdir(new File(_exportDirectory))
        }

        def writer = new CSVWriter(new FileWriter(new File(exportDir, "/missing-images.csv")))
        writer.writeNext((String[])["imageIdentifier", "directory", "status"].toArray())
        def c = Image.createCriteria()
        def imageIds = c.list {
            projections {
                property("imageIdentifier")
            }
        }
        def counter = 0
        imageIds.each { imageId ->
            def imageDirectory = _imageStoreService.getImageDirectory(imageId)
            if (imageDirectory.exists()){
                //check for original
                def originalFile = new File(imageDirectory, "original")
                if(!originalFile.exists()){
                    writer.writeNext((String[])[imageId, imageDirectory.getAbsolutePath(), "original-missing"].toArray())
                }
            } else {
                writer.writeNext((String[])[imageId, imageDirectory.getAbsolutePath(), "directory-missing"].toArray())
            }
            counter += 1
        }
        if (counter % 1000 == 0){
            log.info("Missing image check: " + counter)
        }

        writer.flush()
        writer.close()
        log.info("Missing images check complete. Total checked: " + counter)
    }
}
