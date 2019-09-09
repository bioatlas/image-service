<%@ page import="au.org.ala.web.CASRoles" %>
<g:set var="mediaTitle" value="${isImage ? 'Image' : 'Media'}" />
<!doctype html>
<html>
    <head>
        <meta name="layout" content="main"/>
        <title>${mediaTitle} - ${imageInstance.title ? imageInstance.title : imageInstance.imageIdentifier}</title>
        <meta name="breadcrumbs" content="${g.createLink( controller: 'search', action: 'list')}, Images"/>
        <asset:stylesheet src="application.css" />
        <asset:stylesheet src="ala/images-client.css" />
        <style>
            td.property-value { font-weight: bold !important; }
            .audiojs { width: 100%; }
            .tab-pane { padding-top: 20px !important; }
            .tabbable { font-size: 9pt; margin-top:10px; }
            div#main { padding-top: 0px; }
            .subimages_thumbs { max-height:100px; }
        </style>
    </head>
    <body>
        <img:headerContent title="${mediaTitle} details ${imageInstance?.id}">
            <% pageScope.crumbs = []  %>
        </img:headerContent>
        <div class="container-fluid" style="padding-left:1px; padding-top:0px;">
            <div class="row">
                <div id="viewerContainerId" class="col-md-9">
                    <g:if test="${!imageInstance.mimeType.startsWith('image')}">
                    <div class="col-md-3"></div>
                    <div class="col-md-6">
                        <div class="document-icon" style="height: 500px; margin-bottom: 30px;"></div>
                        <g:if test="${imageInstance.mimeType.startsWith('audio')}">
                            <audio src="${createLink(controller: 'image', action:'proxyImage', params: [imageId: imageInstance.imageIdentifier])}" preload="auto" />
                        </g:if>
                    </div>
                    <div class="col-md-3"></div>
                    </g:if>
                </div>
                <div id="imageTabs" class="col-md-3">
                    <div class="tabbable" >
                        <ul class="nav nav-tabs">
                            <li class="active">
                                <a href="#tabProperties" data-toggle="tab">${mediaTitle}</a>
                            </li>
                            <li>
                                <a href="#tabExif" data-toggle="tab">Embedded</a>
                            </li>
                            <li>
                                <a href="#tabSystem" data-toggle="tab">System</a>
                            </li>
                            <g:if test="${isImage}">
                                <li>
                                    <a href="#tabThumbnails" data-toggle="tab">Thumbnails</a>
                                </li>
                            </g:if>
                            <auth:ifAnyGranted roles="${CASRoles.ROLE_ADMIN}">
                                <li>
                                    <a href="#tabAuditMessages" data-toggle="tab">Audit</a>
                                </li>
                            </auth:ifAnyGranted>
                        </ul>

                        <div class="tab-content">

                            <div class="tab-pane active" id="tabProperties">
                                <div class="coreMetadataContainer">
                                    <g:render template="/image/coreImageMetadataFragment" />
                                </div>
                                <div id="tagsSection"></div>
                            </div>
                            <div class="tab-pane" id="tabExif" metadataSource="${au.org.ala.images.MetaDataSourceType.Embedded}">
                                <div class="metadataSource-container"></div>
                            </div>
                            <div class="tab-pane" id="tabSystem" metadataSource="${au.org.ala.images.MetaDataSourceType.SystemDefined}" >
                                <table class="table table-bordered table-condensed table-striped">
                                    <tr>
                                        <td class="property-name">Data resource UID</td>
                                        <td class="property-value">${imageInstance.dataResourceUid}</td>
                                    </tr>
                                    <g:if test="${isImage}">
                                        <tr>
                                            <td class="property-name">Dimensions (w x h)</td>
                                            <td class="property-value">${imageInstance.width} x ${imageInstance.height}</td>
                                        </tr>
                                    </g:if>
                                    <tr>
                                        <td class="property-name">File size</td>
                                        <td class="property-value"><img:sizeInBytes size="${imageInstance.fileSize}" /></td>
                                    </tr>
                                    <tr>
                                        <td class="property-name">Mime type</td>
                                        <td class="property-value">${imageInstance.mimeType}</td>
                                    </tr>

                                    <tr>
                                        <td class="property-name">${mediaTitle} URL</td>
                                        <td class="property-value">
                                            <a href="${img.imageUrl([imageId: imageInstance.imageIdentifier])}">
                                                <img:imageUrl imageId="${imageInstance.imageIdentifier}" />
                                            </a>
                                        </td>
                                    </tr>
                                    <tr>
                                        <td class="property-name">MD5 Hash</td>
                                        <td class="property-value">${imageInstance.contentMD5Hash}</td>
                                    </tr>
                                    <tr>
                                        <td class="property-name">SHA1 Hash</td>
                                        <td class="property-value">${imageInstance.contentSHA1Hash}</td>
                                    </tr>
                                    <tr>
                                        <td class="property-name">Size on disk (including all artifacts)</td>
                                        <td class="property-value"><img:sizeInBytes size="${sizeOnDisk}" /></td>
                                    </tr>
                                </table>

                                <div class="metadataSource-container"></div>
                            </div>
                            <div class="tab-pane" id="tabThumbnails">
                                <ul class="list-unstyled list-inline">
                                    <g:each in="${squareThumbs}" var="thumbUrl">
                                        <li>
                                            <a href="${thumbUrl}" target="thumbnail">
                                                <img class="thumbnail" src="${thumbUrl}" style="width:100px;" title="${thumbUrl}" style="margin: 5px"/>
                                            </a>
                                        </li>
                                    </g:each>
                                </ul>
                            </div>
                            <auth:ifAnyGranted roles="${CASRoles.ROLE_ADMIN}">
                                <div class="tab-pane" id="tabAuditMessages">
                                    <div class="metadataSource-container"></div>
                                </div>
                            </auth:ifAnyGranted>
                        </div>
                    </div>
                </div>
            </div>
        </div>

        <asset:javascript src="ala/images-client.js"/>
        <asset:javascript src="audiojs/audio.min.js"/>

    <script>

        function refreshMetadata(tabDiv) {
            var dest = $(tabDiv);
            dest.find('.metadataSource-container').html("Loading...");
            var source = dest.attr("metadataSource");
            $.ajax("${grailsApplication.config.grails.serverURL}${createLink(controller:'image', action:'imageMetadataTableFragment', id: imageInstance.id)}?source=" + source).done(function(content) {
                dest.find('.metadataSource-container').html(content);
            });
        }

        function refreshCoreMetadata() {
            $.ajax("${grailsApplication.config.grails.serverURL}${createLink(controller:'image', action:'coreImageMetadataTableFragment', id: imageInstance.id)}").done(function(content) {
                $('#imageTabs').find('.coreMetadataContainer').html(content);
            });
        }

        <auth:ifAnyGranted roles="${CASRoles.ROLE_ADMIN}">

        function refreshAuditTrail() {
            $.ajax("${grailsApplication.config.grails.serverURL}${createLink(controller: 'image', action:'imageAuditTrailFragment', id: imageInstance.id)}").done(function(content) {
                $("#tabAuditMessages").html(content);
            });
        }

        </auth:ifAnyGranted>

        $(document).ready(function() {

            var options = {
                auxDataUrl : "${auxDataUrl ? auxDataUrl : ''}",
                imageServiceBaseUrl : "${grailsApplication.config.grails.serverURL}${grailsApplication.config.server.contextPath}",
                imageClientBaseUrl : "${grailsApplication.config.grails.serverURL}${grailsApplication.config.server.contextPath}",
                zoomFudgeFactor: 0.65
            };

            var screenHeight = $(window).height();
            $('#viewerContainerId').css('height', (screenHeight - 172) + 'px');


            <g:if test="${imageInstance.mimeType.startsWith('image')}">
            imgvwr.viewImage($("#viewerContainerId"), '${imageInstance.imageIdentifier}', "", "", options);
            </g:if>
            <g:elseif test="${imageInstance.mimeType.startsWith('audio')}">
            $('#viewerContainerId .document-icon').css('background-image', 'url("${grailsApplication.config.placeholder.sound.large}")');
            $('#viewerContainerId .document-icon').css('background-repeat', 'no-repeat');
            $('#viewerContainerId').css('background-position', 'center');
            audiojs.createAll();
            </g:elseif>
            <g:else>
            $('#viewerContainerId .document-icon').css('background-image', 'url("${grailsApplication.config.placeholder.document.large}")');
            $('#viewerContainerId .document-icon').css('background-repeat', 'no-repeat');
            $('#viewerContainerId .document-icon').css('background-position', 'center');
            </g:else>

            $('a[data-toggle="tab"]').on('click', function (e) {

                var dest = $($(this).attr("href"));
                if (dest.attr("metadataSource")) {
                    refreshMetadata(dest);
                } else {
                    if (dest.attr("id") == "tabAuditMessages") {
                        refreshAuditTrail();
                    }
                }
            });

            $("#btnViewImage").click(function(e) {
                e.preventDefault();
                window.location = "${grailsApplication.config.grails.serverURL}${createLink(controller:'image', action:'view', id: imageInstance.imageIdentifier)}";
            });

            $("#btnRegen").click(function(e) {
                e.preventDefault();

                $.ajax("${grailsApplication.config.grails.serverURL}${createLink(controller:'image', action:'scheduleArtifactGeneration', id: imageInstance.imageIdentifier)}").done(function(data) {
                    console.log(data);
                    alert('Regeneration scheduled - ' + data.message);
                }).fail(function(){
                    alert("Problem scheduling regeneration");
                });
            });

            $("#btnDeleteImage").click(function(e) {
                e.preventDefault();
                var options = {
                    message: "Warning! This operation cannot be undone. Are you sure you wish to permanently delete this image?",
                    title: "Delete this image",
                    affirmativeAction: function() {
                        $.ajax("${grailsApplication.config.grails.serverURL}${createLink(controller:'image', action:'deleteImage', id: imageInstance.imageIdentifier)}").done(function() {
                            window.location = "${grailsApplication.config.grails.serverURL}${createLink(controller:'search', action:'list')}";
                        });
                    }
                };
                imgvwr.areYouSure(options);
            });

            $(".image-info-button").each(function() {
                var imageId = $(this).closest("[imageId]").attr("imageId");
                if (imageId) {
                    $(this).qtip({
                        content: {
                            text: function(event, api) {
                                $.ajax("${grailsApplication.config.grails.serverURL}${createLink(controller:'image', action:"imageTooltipFragment")}/" + imageId).then(function(content) {
                                    api.set("content.text", content);
                                },
                                function(xhr, status, error) {
                                    api.set("content.text", status + ": " + error);
                                });
                            }
                        }
                    });
                }
            });

            loadTags();
        });

        function loadTags() {
            $.ajax("${grailsApplication.config.grails.serverURL}${createLink(controller: 'image', action:'tagsFragment', id:imageInstance.id)}").done(function(html) {
                $("#tagsSection").html(html);
            });
        }

        function calibrationCallback(data){
            refreshCoreMetadata();
        }

        function createSubImageCallback(){
            refreshCoreMetadata();
        }
    </script>
    </body>
</html>

