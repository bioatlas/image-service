<!doctype html>
<html>
<head>
    <meta name="layout" content="main"/>
    <meta name="section" content="home"/>
    <title>Selected Images | ${grailsApplication.config.skin.orgNameLong}</title>
</head>

<body class="content">

    <img:headerContent title="Selected images">
        <%
            pageScope.crumbs = [
            ]
        %>
    </img:headerContent>

    <div class="well well-small">

        <div class="btn-group">
            <a class="btn dropdown-toggle" data-toggle="dropdown" href="#">
                Actions
                <span class="caret"></span>
            </a>
            <ul class="dropdown-menu">
                <li>
                    <a href="#" id="btnAddToAlbum">Add to album...</a>
                </li>

                <li>
                    <a href="#">Add meta data</a>
                </li>
                <li>
                    <a href="#">Add tags</a>
                </li>
            </ul>
        </div>

        <auth:ifAnyGranted roles="${au.org.ala.web.CASRoles.ROLE_ADMIN}">
        <div class="btn-group">
            <a class="btn dropdown-toggle btn-warning" data-toggle="dropdown" href="#">
                <i class="icon-cog icon-white"></i>&nbsp;Admin functions
                <span class="caret"></span>
            </a>
            <ul class="dropdown-menu">
                <li>
                    <a href="#" id="btnRegenerateThumbs"><i class="icon-picture"></i>&nbsp;Regenerate thumbnails</a>
                </li>
                <li>
                    <a href="#" id="btnRegenerateTiles"><i class="icon-refresh"></i>&nbsp;Regenerate tiles</a>
                </li>
                <li class="divider"></li>
                <li>
                    <a href="#" id="btnDeleteImages"><i class="icon-trash"></i>&nbsp;Delete Images</a>
                </li>
            </ul>
        </div>
        </auth:ifAnyGranted>

        <button class="btn" id="btnClearSelection">Clear selection</button>

        %{--<a href="" class="btn btn-danger pull-right"><i class="icon-remove icon-white"></i>&nbsp;Delete all selected</a>--}%
    </div>

    <table class="table table-condensed table-bordered table-striped">
        <thead>
            <tr>
                <th>#</th>
                <th width="110">Thumb</th>
                <th>Name</th>
                <th>Size (bytes)</th>
                <th>Size (pixels)</th>
                <th>Content type</th>
                <th></th>
            </tr>
        </thead>
        <tbody>
            <g:each in="${selectedImages}" var="image" status="i">
                <tr imageId="${image.id}">
                    <td>${i.toInteger() + 1 + params.offset.toInteger()}</td>
                    <td>
                        <a href="${createLink(controller:'image', action:'details', id: image.id)}">
                            <img src="<img:imageSquareThumbUrl imageId='${image.imageIdentifier}'/>" width="100" />
                        </a>
                    </td>
                    <td><a href="${createLink(controller:'image', action:'details', id: image.id)}">${image.originalFilename ?: image.imageIdentifier}</a></td>
                    <td><img:sizeInBytes size="${image.fileSize}" /></td>
                    <td>${image.width}&nbsp;x&nbsp;${image.height}</td>
                    <td>${image.mimeType}</td>
                    <td><button class="btn btn-small btnRemoveFromSelection"><i class="icon-remove"></i></button></td>
                </tr>
            </g:each>
        </tbody>
    </table>

    <div class="pagination">
        <g:paginate total="${total}" prev="" next="" />
    </div>

    <script>
        $(document).ready(function() {

            $(".btnRemoveFromSelection").click(function(e) {
                e.preventDefault();
                var imageId = $(this).closest("[imageId]").attr("imageId");
                if (imageId) {
                    $.ajax("${createLink(controller: 'selection', action: 'ajaxDeselectImage')}/" + imageId).done(function() {
                        window.location = "${createLink(controller: 'selection', action: "list")}";
                    });
                }
            });

            $("#btnAddToAlbum").click(function(e) {
                e.preventDefault();
                imgvwr.selectAlbum(function(albumId) {
                    window.location = "${createLink(controller:'selection', action:'addSelectionToAlbum')}?albumId=" + albumId;
                });
            });

            $("#btnClearSelection").click(function(e) {
                e.preventDefault();
                $.ajax("${createLink(controller: 'selection', action: 'clearSelection')}").done(function() {
                    window.location = "${createLink(controller: 'selection', action: "list")}";
                });
            });

            $("#btnRegenerateThumbs").click(function(e) {
                e.preventDefault();
                window.location = "${createLink(controller: 'selection', action: 'generateThumbnails')}";
            });

            $("#btnRegenerateTiles").click(function(e) {
                e.preventDefault();
                window.location = "${createLink(controller: 'selection', action: 'generateTMSTiles')}";
            });

            $("#btnDeleteImages").click(function(e) {
                e.preventDefault();
                var options = {
                    message: "Warning! This operation cannot be undone. Are you sure you wish to permanently delete these images?",
                    affirmativeAction: function() {
                        window.location = "${createLink(controller: 'selection', action: 'deleteSelected')}";
                    }
                }
                imgvwr.areYouSure(options);
            });

        });

    </script>

</body>
</html>
