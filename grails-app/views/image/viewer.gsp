<%@ page contentType="text/html;charset=UTF-8" %>
<!DOCTYPE html>
<html>
    <head>
        <title>Image ${imageInstance.originalFilename} | Image Service | ${grailsApplication.config.skin.orgNameLong}</title>
        <style>
        html, body {
            height:100%;
            padding: 0;
            margin:0;
        }

        #imageViewerContainer {
            height: 100%;
            padding: 0;
        }

        #imageViewer {
            width: 100%;
            height: 100%;
            margin: 0;
        }

        </style>
        <r:require module="image-viewer" />
        <r:layoutResources/>
        <r:script>
        $(document).ready(function() {
            var options = {
                auxDataUrl : "${auxDataUrl ? auxDataUrl : ''}",
                imageServiceBaseUrl : "${grailsApplication.config.serverName}${grailsApplication.config.contextPath}",
                imageClientBaseUrl : "${grailsApplication.config.serverName}${grailsApplication.config.contextPath}"
            };
            imgvwr.viewImage($("#imageViewer"), "${imageInstance.imageIdentifier}", "", "", options);
        });
        </r:script>
    </head>
    <body style="padding:0;">
        <div id="imageViewerContainer" class="container-fluid">
            <div id="imageViewer"> </div>
        </div>
        <r:layoutResources/>
    </body>
</html>
