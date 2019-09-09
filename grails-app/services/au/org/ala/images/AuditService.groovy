package au.org.ala.images

class AuditService {

    def log(Image image, String message, String userId) {
        def auditMessage = new AuditMessage(imageIdentifier: image.imageIdentifier, message: message, userId: userId)
        auditMessage.save()
    }

    def log(String imageIdentifier, String message, String userId) {
        def auditMessage = new AuditMessage(imageIdentifier: imageIdentifier, message: message, userId: userId)
        auditMessage.save()
    }

    def getMessagesForImage(String imageIdentifier) {
        return AuditMessage.findAllByImageIdentifier(imageIdentifier, [sort:'dateCreated', order:'asc'])
    }

}
