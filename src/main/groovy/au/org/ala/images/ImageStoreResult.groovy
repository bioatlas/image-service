package au.org.ala.images;

class ImageStoreResult {

    Image image = null
    boolean alreadyStored = false

    ImageStoreResult(Image image, boolean alreadyStored){
        this.alreadyStored = alreadyStored
        this.image = image
    }
}