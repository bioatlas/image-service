package au.org.ala.images

import au.com.bytecode.opencsv.CSVReader
import com.opencsv.CSVWriter
import org.apache.log4j.Logger

class DeleteOrphanedImagesBackgroundTask  extends BackgroundTask {

    private ImageService _imageService
    String _exportDirectory
    private Logger log = Logger.getLogger(DeleteOrphanedImagesBackgroundTask.class)

    DeleteOrphanedImagesBackgroundTask(ImageService imageService, String exportDirectory) {
        _imageService = imageService
        _exportDirectory = exportDirectory
    }

    @Override
    void execute() {

        //read an export of occurrence UUIDs from Biocache into a HashSet
        def reader = new CSVReader(new FileReader(new File(_exportDirectory + "/occurrence-uuid.csv")))
        def line = reader.readNext()
        def cache = new HashMap<String, String>()
        while (line){
            if(line[1]) {
                cache.put(line[0], line[1])
            }
            line = reader.readNext()
        }

        def writer = new CSVWriter(new FileWriter(new File(_exportDirectory + "/images-to-delete.csv")))
        writer.writeNext((String[]) ["imageIdentifier"].toArray())
        def c = Image.createCriteria()
        def imageIds = c.list {
            projections {
                property("imageIdentifier"  )
            }
        }
        def counter = 0
        imageIds.each { imageId ->

            def imageDirectory = _imageStoreService.getImageDirectory(imageId)
            if (imageDirectory.exists()) {
                //check for original
                def originalFile = new File(imageDirectory, "original")
                if (!originalFile.exists()) {
                    writer.writeNext((String[]) [imageId, imageDirectory.getAbsolutePath(), "original-missing"].toArray())
                }
            } else {
                writer.writeNext((String[]) [imageId, imageDirectory.getAbsolutePath(), "directory-missing"].toArray())
            }
            counter += 1
        }
        if (counter % 1000 == 0) {
            log.info("Missing image check: " + counter)
        }

        writer.flush()
        writer.close()
        log.info("Missing images check complete. Total checked: " + counter)
    }
}