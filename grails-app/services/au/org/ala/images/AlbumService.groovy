package au.org.ala.images

import grails.transaction.Transactional
import grails.web.servlet.mvc.GrailsParameterMap

@Transactional
class AlbumService {

    def selectionService
    def imageService
    def tagService

    def deleteAlbum(Album album) {
        if (!album) {
            return false
        }

        // first delete album images
        def albumImages = AlbumImage.findAllByAlbum(album)
        albumImages.each { albumImage ->
            albumImage.delete()
        }

        // then delete the album
        album.delete()
    }

    def addImageToAlbum(Album album, Image image) {
        if (!album || !image) {
            return
        }

        // Does this image already exist in this album
        def albumImage = AlbumImage.findByAlbumAndImage(album, image)
        if (!albumImage) {
            albumImage = new AlbumImage(album: album, image: image)
            albumImage.save()
        }
        return albumImage
    }

    def removeImageFromAlbum(Album album, Image image) {
        if (!album || !image) {
            return
        }

        def albumImage = AlbumImage.findByAlbumAndImage(album, image)
        if (albumImage) {
            albumImage.delete()
        }
    }

    def withImageIds(Album album, Closure closure) {
        if (!album) {
            return
        }

        // Get a list of just the images ids...
        def c = AlbumImage.createCriteria()
        def imageIds = c.list {
            eq('album', album)
            projections {
                image {
                    property("id")
                }
            }
        }

        if (imageIds && closure) {
            imageIds.each { imageId ->
                closure(imageId)
            }
        }
    }

    def scheduleTileRegeneration(Album album, String userId) {
        if (album) {
            withImageIds(album) { imageId ->
                imageService.scheduleTileGeneration(imageId, userId)
            }
        }
    }

    def scheduleThumbnailRegeneration(Album album, String userId) {
        if (album) {
            withImageIds(album) { imageId ->
                imageService.scheduleThumbnailGeneration(imageId, userId)
            }
        }
    }

    def deleteAllImages(Album album, String userId) {
        if (album) {
            withImageIds(album) { imageId ->
                def image = Image.get(imageId)
                if (image) {
                    removeImageFromAlbum(album, image)
                    imageService.scheduleImageDeletion(image.id, userId)
                }
            }
        }
    }

    def tagImages(Album album, Tag tag, String userId) {
        if (album && tag) {
            withImageIds(album) { imageId ->
                def image = Image.get(imageId)
                if (image) {
                    tagService.attachTagToImage(image, tag, userId)
                }
            }
        }
    }

    def attachMetadata(Album album, String key, String value, MetaDataSourceType source) {
        if (album && key && value) {
            source = source ?: MetaDataSourceType.UserDefined
            withImageIds(album) { long imageId ->
                def image = Image.get(imageId)
                if (image) {
                    imageService.setMetaDataItem(image, source, key, value)
                    imageService.scheduleImageIndex(imageId)
                }
            }
        }
    }

    def getUserAlbums(String userId, GrailsParameterMap params) {
        params.sort = params.sort ?: 'name'
        def albums = Album.findAllByUserIdAndNameNotEqual(userId, selectionService.SELECTION_ALBUM_NAME, params)
        return albums
    }

    QueryResults<Image> getAlbumImages(Album album, Map params) {
        def c = AlbumImage.createCriteria()
        def results = new QueryResults<Image>()
        results.list = c.list(params) {
            eq("album", album)
            projections {
                property("image")
            }
        }

        results.totalCount = AlbumImage.countByAlbum(album)
        return results
    }

    public List<Map> getAlbumTabularData(Album album, List<CSVColumnDefintion> columnDefinitions, int maxRows = -1, int offset = 0) {
        def tabularData = []
        if (!album) {
            return tabularData
        }

        def params = [:]
        if (maxRows > 0) {
            params.max = maxRows
        }

        if (offset > 0) {
            params.offset = offset
        }

        def albumImagesResults = getAlbumImages(album, params)
        def metadataColumns = columnDefinitions.findAll({ it.columnType == 'metadata'})*.columnName

        def metaDataRows = []
        if (metadataColumns) {
            def c = ImageMetaDataItem.createCriteria()
            // retrieve just the relevant metadata rows
            metaDataRows = c.list {
                inList("image", albumImagesResults.list)
                inList("name", metadataColumns)
            }
        }

        def metaDataMappedbyImage = metaDataRows.groupBy {
            it.image.id
        }

        albumImagesResults.list.each { image ->
            def map =  ['imageUrl': imageService.getImageUrl(image.imageIdentifier)]
            def imageMetadata = metaDataMappedbyImage[image.id]
            columnDefinitions.each { colDef ->
                if (colDef.columnType == 'property') {
                    map[colDef.columnName] = image[colDef.columnName]
                } else {
                    map[colDef.columnName] = imageMetadata?.find({ it.name == colDef.columnName })?.value
                }
            }
            tabularData << map
        }

        return tabularData
    }

}
