package au.org.ala.images

class DeletedImagesPurgeBackgroundTask extends BackgroundTask {

    ImageService imageService

    DeletedImagesPurgeBackgroundTask(ImageService imageService) {
        this.imageService = imageService
    }

    @Override
    void execute() {
        def images = Image.findAllByDateDeletedIsNotNull()
        images.each {
            imageService.deleteImagePurge(it)
        }
    }
}
