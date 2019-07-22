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
            /*#imageTabs .nav { margin-top:5px; }*/
            div#main { padding-top: 0px; }
            /*.leaflet-container { background: #E7E7E7;}*/
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
                                <table class="table table-bordered table-condensed table-striped">
                                    <g:if test="${imageInstance.dataResourceUid}">
                                        <tr>
                                            <td class="property-name">Data resource</td>
                                            <td class="property-value">
                                                <a href="${grailsApplication.config.collectory.baseURL}/public/show/${imageInstance.dataResourceUid}">
                                                    ${resourceLevel.name}
                                                </a>
                                            </td>
                                        </tr>
                                    </g:if>
                                    <tr>
                                        <td class="property-name">Image Identifier</td>
                                        <td class="property-value">${imageInstance.imageIdentifier}</td>
                                    </tr>
                                    <tr>
                                        <td class="property-name">Title</td>
                                        <td class="property-value">${imageInstance.title}</td>
                                    </tr>
                                    <tr>
                                        <td class="property-name">Creator</td>
                                        <td class="property-value"><img:imageMetadata image="${imageInstance}" resource="${resourceLevel}" field="creator"/></td>
                                    </tr>
                                    <tr>
                                        <td class="property-name">Description</td>
                                        <td class="property-value">${imageInstance.description}</td>
                                    </tr>

                                    <g:if test="${imageInstance.parent}">
                                        <tr>
                                            <td>Parent image</td>
                                            <td imageId="${imageInstance.parent.id}">
                                                <g:link controller="image" action="details" id="${imageInstance.parent.imageIdentifier}">${imageInstance.parent.originalFilename ?: imageInstance.parent.imageIdentifier}</g:link>
                                                <i class="icon-info-sign image-info-button"></i>
                                            </td>
                                        </tr>
                                    </g:if>
                                    <g:if test="${isImage}">
                                        <tr>
                                            <td class="property-name">Zoom levels</td>
                                            <td class="property-value">${imageInstance.zoomLevels}</td>
                                        </tr>
                                        <tr>
                                            <td class="property-name">Linear scale</td>
                                            <td class="property-value">
                                                <g:if test="${imageInstance.mmPerPixel}">
                                                    ${imageInstance.mmPerPixel} mm per pixel
                                                    <button id="btnResetLinearScale" type="button" class="btn btn-sm btn-default pull-right" title="Reset calibation">
                                                        <i class="glyphicon glyphicon-remove"></i></button>
                                                </g:if>
                                                <g:else>
                                                    &lt;not calibrated&gt;
                                                </g:else>
                                            </td>
                                        </tr>
                                    </g:if>
                                    <tr>
                                        <td class="property-name">Date uploaded</td>
                                        <td class="property-value"><img:formatDateTime date="${imageInstance.dateUploaded}" /></td>
                                    </tr>
                                    <tr>
                                        <td class="property-name">Uploaded by</td>
                                        <td class="property-value"><img:userDisplayName userId="${imageInstance.uploader}" /></td>
                                    </tr>
                                    <g:if test="${imageInstance.dateTaken}">
                                        <tr>
                                            <td class="property-name">Date taken/created</td>
                                            <td class="property-value"><img:formatDateTime date="${imageInstance.dateTaken}" /></td>
                                        </tr>
                                    </g:if>
                                    <tr>
                                        <td class="property-name">Rights</td>
                                        <td class="property-value"><img:imageMetadata image="${imageInstance}" resource="${resourceLevel}" field="rights"/></td>
                                    </tr>
                                    <tr>
                                        <td class="property-name">Rights holder</td>
                                        <td class="property-value"><img:imageMetadata image="${imageInstance}" resource="${resourceLevel}" field="rightsHolder"/></td>
                                    </tr>
                                    <tr>
                                        <td class="property-name">Licence</td>
                                        <td class="property-value">
                                            ${imageInstance.license}
                                            <g:if test="${imageInstance.recognisedLicense}">
                                               <a href="${imageInstance.recognisedLicense.url}" title="${imageInstance.recognisedLicense.name +  ' (' + imageInstance.recognisedLicense.acronym + ')'}">
                                                <img src="${imageInstance.recognisedLicense.imageUrl}">
                                               </a>
                                            </g:if>
                                            <g:else>
                                                <img:imageMetadata image="${imageInstance}" resource="${resourceLevel}" field="license"/>
                                            </g:else>
                                        </td>
                                    </tr>
                                    <g:if test="${subimages}">
                                        <tr>
                                            <td>Sub-images</td>
                                            <td>
                                                <ul>
                                                    <g:each in="${subimages}" var="subimage">
                                                        <li imageId="${subimage.id}">
                                                            <g:link controller="image" action="details" id="${subimage.imageIdentifier}">${subimage.originalFilename ?: subimage.imageIdentifier}</g:link>
                                                            <i class="icon-info-sign image-info-button"></i>
                                                        </li>
                                                    </g:each>
                                                </ul>
                                            </td>
                                        </tr>
                                    </g:if>

%{--                                    <tr>--}%
%{--                                        <td class="property-name">Harvested as occurrence record?</td>--}%
%{--                                        <auth:ifAnyGranted roles="${CASRoles.ROLE_ADMIN}">--}%
%{--                                            <td>--}%
%{--                                                <g:checkBox name="chkIsHarvestable" data-size="small" data-on-text="Yes" data-off-text="No" checked="${imageInstance.harvestable}" />--}%
%{--                                            </td>--}%
%{--                                        </auth:ifAnyGranted>--}%
%{--                                        <auth:ifNotGranted roles="${CASRoles.ROLE_ADMIN}">--}%
%{--                                            <td class="property-value">${imageInstance.harvestable ? "Yes" : "No"}</td>--}%
%{--                                        </auth:ifNotGranted>--}%
%{--                                    </tr>--}%

                                    <tr>
                                        <td colspan="2">
                                            <g:link controller="webService" action="getImageInfo" params="[id:imageInstance.imageIdentifier]" title="View JSON metadata" class="btn btn-default">
                                                <i class="glyphicon glyphicon-wrench"> </i>
                                            </g:link>

                                            <g:if test="${isImage}">
                                                <button class="btn btn-default" id="btnViewImage" title="View zoomable image"><span class="glyphicon glyphicon-eye-open"> </span></button>
                                            </g:if>
                                            <a class="btn btn-default" href="${grailsApplication.config.grails.serverURL}${createLink(controller:'image', action:'proxyImage', id:imageInstance.id, params:[contentDisposition: 'true'])}" title="Download full image" target="imageWindow"><i class="glyphicon glyphicon-download-alt"></i></a>

                                            <g:if test="${isAdminView}">
                                                <button class="btn btn-default" id="btnRegen" title="Regenerate artifacts"><i class="glyphicon glyphicon-refresh"></i></button>
                                                <button class="btn btn-danger" id="btnDeleteImage" title="Delete image (admin)"><i class="glyphicon glyphicon-remove  glyphicon-white"></i></button>
                                            </g:if>
                                            <g:elseif test="${isAdmin}">
                                                <g:link class="btn btn-danger" controller="admin" action="image" params="[imageId: imageInstance.imageIdentifier]">Admin view</g:link>
                                            </g:elseif>

                                        </td>
                                    </tr>
                                </table>
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
                imageServiceBaseUrl : "${grailsApplication.config.grails.serverURL}${grailsApplication.config.contextPath}",
                imageClientBaseUrl : "${grailsApplication.config.grails.serverURL}${grailsApplication.config.contextPath}",
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

            $("#btnResetLinearScale").click(function(e) {
                e.preventDefault();
                imgvwr.areYouSure({
                    title:"Reset calibration for this image?",
                    message:"Are you sure you wish to reset the linear scale for this image?",
                    affirmativeAction: function() {
                        var url = "${grailsApplication.config.grails.serverURL}${createLink(controller:'webService', action:'resetImageCalibration')}?imageId=${imageInstance.imageIdentifier}";
                        $.ajax(url).done(function(result) {
                            window.location.reload(true);
                        });
                    }
                });
            });

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
                    content: "Warning! This operation cannot be undone. Are you sure you wish to permanently delete this image?",
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

    </script>
    </body>
</html>

