package au.org.ala.images

import grails.transaction.Transactional
import grails.web.servlet.mvc.GrailsParameterMap
import org.hibernate.FlushMode

/**
 * Each user has an implicit hidden album called _userSelection which holds the users transient selected images...
 */
@Transactional
class SelectionService {

    public static final String SELECTION_ALBUM_NAME = "_userSelection"

    def sessionFactory
    def albumService
    
    def getOrCreateUserSelection(String userId) {
        def album = Album.findByUserIdAndName(userId, SELECTION_ALBUM_NAME)
        if (!album) {
            Album.withNewTransaction {
                album = new Album(userId: userId, name: SELECTION_ALBUM_NAME, externalIdentifier: UUID.randomUUID().toString(), description: "Implicit user selection album")
                album.save(flush: true, failOnError: true)
            }
        }
        return album
    }

    def getSelectedImageIdsAsMap(String userId) {

        if (!userId) {
            return [:]
        }

        def selectedImageMap = [:]
        
        def album = getOrCreateUserSelection(userId)
        if (album) {
            def idList = SelectedImage.executeQuery("select image.imageIdentifier from AlbumImage where album = :album", [album: album])
            // stick in map for quick retrieval
            idList?.each {
                selectedImageMap[it] = 1
            }
        }
        return selectedImageMap
    }

    def selectImages(String userId, imageIds) {
        try {
            sessionFactory.currentSession.flushMode = FlushMode.MANUAL
            def album = getOrCreateUserSelection(userId)
            imageIds.each { imageId ->
                def image = Image.get(imageId as Long)
                if (image) {
                    def existing = AlbumImage.findByAlbumAndImage(album, image)
                    if (!existing) {
                        def selected = new AlbumImage(album: album, image: image)
                        selected.save()
                    }
                }
            }
            sessionFactory.currentSession.flush()
        } finally {
            sessionFactory.currentSession.flushMode = FlushMode.AUTO
        }
    }

    def selectImage(String userId, Image image) {
        def album = getOrCreateUserSelection(userId)
        def existing = AlbumImage.findByAlbumAndImage(album, image)
        if (!existing) {
            def selected = new AlbumImage(album: album, image: image)
            selected.save()
            return true
        }
        return false
    }

    def deselectImage(String userId, Image image) {
        def album = getOrCreateUserSelection(userId)
        def existing = AlbumImage.findByAlbumAndImage(album, image)
        if (existing) {
            existing.delete();
            return true
        }
        return false;
    }

    def clearSelection(String userId) {
        def album = getOrCreateUserSelection(userId)
        def selectedImages = AlbumImage.findAllByAlbum(album)
        selectedImages?.each { selected ->
            selected.delete();
        }
    }

    def withSelectedImages(String userId, Closure closure) {
        def album = getOrCreateUserSelection(userId)
        albumService.withImageIds(album) { imageId ->
            def image = Image.get(imageId)
            if (image && closure) {
                closure(image)
            }
        }
    }

    def selectionSize(String userId) {
        def album = getOrCreateUserSelection(userId)
        return AlbumImage.countByAlbum(album)
    }

    def getSelectedImages(String userId, GrailsParameterMap params) {
        def album = getOrCreateUserSelection(userId)
        def c = AlbumImage.createCriteria()
        return c.list(params) {
            eq("album", album)
            projections {
                property("image")
            }
        }
    }

    def deleteSelectedImages(String userId) {
        def album = getOrCreateUserSelection(userId)
        def count = AlbumImage.countByAlbum(album)
        albumService.deleteAllImages(album, userId)
        return count
    }

}
